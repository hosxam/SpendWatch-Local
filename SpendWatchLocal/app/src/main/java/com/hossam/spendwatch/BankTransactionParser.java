package com.hossam.spendwatch;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BankTransactionParser {

    public static final List<String> RUYA_SENDER_ALIASES = Arrays.asList(
            "ruya", "ruyabank", "ruya-bank", "ruya dubai", "ruya bank",
            "ae.ruya", "ae.ruyabank", "ruya digital"
    );

    private static final String[] HARD_REJECT = {
            "otp", "one-time password", "one time password", "verification code",
            "security code", "passcode", "declined", "failed", "unsuccessful",
            "not authorized", "not authorised", "insufficient funds", "statement ready",
            "available balance", "current balance", "balance enquiry", "balance inquiry",
            "login", "signed in", "device registered"
    };

    private static final String[] OUTGOING_KEYWORDS = {
            "spent", "purchase", "purchased", "pos", "debit", "debited", "charged",
            "paid", "payment", "payment of", "withdrawn", "withdrawal", "outward transfer",
            "transfer sent", "transferred to", "sent to", "direct debit", "card used",
            "was used", "used for", "used at", "card ending", "card ending in",
            "transaction of", "transaction for", "transaction amount", "successful transaction",
            "successfully processed", "fee debited", "service fee", "apple pay", "google pay",
            "tap to pay", "contactless"
    };

    private static final Pattern PATTERN_CURRENCY_FIRST = Pattern.compile(
            "\\b(AED|DHS|DH|DIRHAMS?|USD|EUR|GBP|SAR|QAR|KWD|OMR|BHD|INR|CAD|AUD)\\s*:?\\s*([0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_AMOUNT_FIRST = Pattern.compile(
            "\\b([0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)\\s*(AED|DHS|DH|DIRHAMS?|USD|EUR|GBP|SAR|QAR|KWD|OMR|BHD|INR|CAD|AUD)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_MERCHANT = Pattern.compile(
            "(?:at|@|merchant|payee|to)\\s*:?\\s*([A-Za-z0-9&'._\\-\\s/]{2,45}?)(?=\\s+(?:on|with|using|via|ending|ref|reference|avail|bal|available|card|account|approx|from|date|time|AED|USD|EUR|GBP|SAR)\\b|[,.]|$)",
            Pattern.CASE_INSENSITIVE
    );

    public static Transaction parseNotification(String title, String body, String packageName) {
        String combined = clean((title == null ? "" : title) + " " + (body == null ? "" : body));
        if (combined.isEmpty()) return null;

        String bank = detectUaeBank(title, packageName, combined);
        if (bank == null) return null;

        String lower = combined.toLowerCase(Locale.ROOT);
        for (String bad : HARD_REJECT) {
            if (lower.contains(bad)) return null;
        }

        AmountResult amountResult = extractAmount(combined);
        if (amountResult == null || amountResult.amount <= 0.0) return null;

        String type;
        String category;

        if (containsAny(lower, "refund", "refunded", "reversal", "reversed", "cashback")) {
            type = "refund";
            category = "Refunds";
        } else if (containsAny(lower, "salary", "payroll", "salary transfer")) {
            type = "income";
            category = "Income";
        } else if (containsAny(lower, "inward transfer", "transfer received", "received from",
                "credited to your", "has been credited", "deposited", "cash deposit")) {
            type = "income";
            category = "Income";
        } else if (containsAny(lower, "outward transfer", "transfer sent", "transferred to", "sent to")) {
            type = "transfer_sent";
            category = "Transfers";
        } else {
            boolean outgoing = containsOutgoingSignal(lower);
            if (!outgoing && "Ruya".equals(bank)) {
                outgoing = looksLikeRuyaCardSpend(lower);
            }
            if (!outgoing) return null;
            type = detectExpenseType(lower);
            category = null;
        }

        String merchant = extractMerchant(combined, type);
        if (category == null) category = categorizeMerchant(merchant, combined);

        Transaction t = new Transaction();
        t.setAmount(amountResult.amount);
        t.setCurrency(amountResult.currency);
        t.setMerchant(merchant);
        t.setBankSource(bank);
        t.setRawMessage(combined);
        t.setTimestamp(System.currentTimeMillis());
        t.setCategory(category);
        t.setTransactionType(type);
        return t;
    }

    private static boolean containsOutgoingSignal(String lower) {
        for (String kw : OUTGOING_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private static boolean looksLikeRuyaCardSpend(String lower) {
        boolean cardSignal = containsAny(lower, "card", "visa", "mastercard", "merchant", "transaction");
        boolean successSignal = containsAny(lower, "approved", "successful", "successfully", "completed", "processed", "used");
        boolean spendSignal = containsAny(lower, " at ", "merchant", "purchase", "payment");
        return cardSignal && (successSignal || spendSignal);
    }

    private static AmountResult extractAmount(String combined) {
        Matcher mCurr = PATTERN_CURRENCY_FIRST.matcher(combined);
        if (mCurr.find()) {
            double amount = parseAmount(mCurr.group(2));
            return amount > 0 ? new AmountResult(normalizeCurrency(mCurr.group(1)), amount) : null;
        }
        Matcher mAmt = PATTERN_AMOUNT_FIRST.matcher(combined);
        if (mAmt.find()) {
            double amount = parseAmount(mAmt.group(1));
            return amount > 0 ? new AmountResult(normalizeCurrency(mAmt.group(2)), amount) : null;
        }
        return null;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private static String extractMerchant(String combined, String type) {
        Matcher m = PATTERN_MERCHANT.matcher(combined);
        if (m.find()) {
            String value = m.group(1).trim().replaceAll("[,.;:]+$", "");
            if (!value.isEmpty()) return value;
        }
        if ("atm".equals(type)) return "ATM Cash Withdrawal";
        if ("income".equals(type)) return "Incoming Funds";
        if ("refund".equals(type)) return "Refund";
        if ("transfer_sent".equals(type)) return "Transfer";
        return "Unknown Merchant";
    }

    private static String detectUaeBank(String title, String pkg, String text) {
        String combined = clean((title == null ? "" : title) + " "
                + (pkg == null ? "" : pkg) + " " + (text == null ? "" : text))
                .toLowerCase(Locale.ROOT);
        for (String alias : RUYA_SENDER_ALIASES) {
            if (combined.contains(alias)) return "Ruya";
        }
        if (combined.contains("emirates nbd") || combined.contains("enbd")) return "Emirates NBD";
        if (combined.contains("adcb")) return "ADCB";
        if (combined.contains("first abu dhabi") || combined.contains("fab")) return "FAB";
        if (combined.contains("mashreq")) return "Mashreq";
        if (combined.contains("rakbank")) return "RAKBANK";
        if (combined.contains("commercial bank of dubai") || combined.contains("cbd")) return "CBD";
        if (combined.contains("adib")) return "ADIB";
        if (combined.contains("dubai islamic") || combined.contains(" dib ") || combined.startsWith("dib ")) return "Dubai Islamic Bank";
        if (combined.contains("hsbc")) return "HSBC UAE";
        if (combined.contains("stanchart") || combined.contains("standard chartered")) return "Standard Chartered UAE";
        return null;
    }

    private static String detectExpenseType(String lower) {
        if (lower.contains("atm") || lower.contains("cash withdrawal") || lower.contains("withdrawn")) return "atm";
        if (lower.contains("online") || lower.contains("e-commerce") || lower.contains("ecommerce")) return "online";
        if (lower.contains("pos") || lower.contains("contactless") || lower.contains("tap to pay")) return "pos";
        if (lower.contains("direct debit")) return "direct_debit";
        if (lower.contains("fee")) return "fee";
        return "card_purchase";
    }

    private static String normalizeCurrency(String raw) {
        if (raw == null) return "AED";
        String u = raw.trim().toUpperCase(Locale.ROOT);
        if (u.equals("DHS") || u.equals("DH") || u.startsWith("DIRHAM")) return "AED";
        return u;
    }

    private static double parseAmount(String str) {
        try {
            return Double.parseDouble(str.replace(",", "").trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static String categorizeMerchant(String merchant, String message) {
        String c = (merchant + " " + message).toLowerCase(Locale.ROOT);
        if (containsAny(c, "carrefour", "spinneys", "lulu", "choithrams", "waitrose", "union coop")) return "Groceries";
        if (containsAny(c, "starbucks", "tim hortons", "talabat", "deliveroo", "cafe", "restaurant", "subway", "mcdonald")) return "Food & Dining";
        if (containsAny(c, "enoc", "adnoc", "emarat", "fuel", "petrol", "salik", "careem", "uber", "rta")) return "Transport & Fuel";
        if (containsAny(c, "dewa", "sewa", "etisalat", "e&", "du telecom")) return "Utilities & Telecom";
        if (containsAny(c, "amazon", "noon", "virgin", "ikea", "zara", "centrepoint")) return "Shopping";
        if (containsAny(c, "vox", "cinema", "reel", "netflix", "spotify")) return "Entertainment";
        if (containsAny(c, "aster", "boots", "pharmacy", "hospital", "clinic")) return "Health & Pharmacy";
        if (containsAny(c, "gym", "fitness", "wellfit")) return "Fitness";
        if (containsAny(c, "atm", "withdrawal")) return "Cash / ATM";
        if (c.contains("fee")) return "Bank Fees & Charges";
        return "General";
    }

    private static class AmountResult {
        final String currency;
        final double amount;
        AmountResult(String currency, double amount) {
            this.currency = currency;
            this.amount = amount;
        }
    }
}
