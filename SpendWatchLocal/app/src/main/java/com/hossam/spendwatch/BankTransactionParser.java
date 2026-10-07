package com.hossam.spendwatch;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BankTransactionParser {

    public static final List<String> RUYA_SENDER_ALIASES = Arrays.asList(
            "ruya", "ruyabank", "ruya-bank", "ruyadubai", "ae.ruya", "ae.ruyabank"
    );

    private static final String[] HARD_REJECT = {
            "otp", "one-time password", "verification code", "security code", "passcode",
            "declined", "failed", "unsuccessful", "not authorized", "insufficient funds",
            "statement ready", "available balance", "current balance", "balance enquiry"
    };

    private static final String[] OUTGOING_KEYWORDS = {
            "spent", "purchase", "purchased", "pos", "debit", "debited", "charged",
            "paid", "payment of", "withdrawn", "withdrawal", "outward transfer",
            "transfer sent", "transferred to", "sent to", "direct debit", "card used",
            "fee debited", "service fee", "apple pay", "google pay"
    };

    private static final Pattern PATTERN_CURRENCY_FIRST = Pattern.compile(
            "(?:^|\\s)(AED|DHS|DH|DIRHAMS?|USD|EUR|GBP|SAR|QAR|KWD|OMR|BHD|INR|CAD|AUD)\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_AMOUNT_FIRST = Pattern.compile(
            "(?:^|\\s)([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)\\s*(AED|DHS|DH|DIRHAMS?|USD|EUR|GBP|SAR|QAR|KWD|OMR|BHD|INR|CAD|AUD)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_MERCHANT = Pattern.compile(
            "(?:at|@|to|towards|for|merchant|payee|from)\\s+([A-Za-z0-9&'._\\-\\s/]{2,40}?)(?:\\s+(?:on|with|using|via|ending|ref|avail|bal|available|card|account|approx|from|dt|date|AED|USD|EUR|GBP|SAR|\\.|$)|\\n|\\.|$)",
            Pattern.CASE_INSENSITIVE
    );

    public static Transaction parseNotification(String title, String body, String packageName) {
        String combined = ((title == null ? "" : title) + " " + (body == null ? "" : body)).trim();
        if (combined.isEmpty()) return null;

        String bank = detectUaeBank(title, packageName, combined);
        if (bank == null) return null;

        String lower = combined.toLowerCase(Locale.ROOT);
        for (String bad : HARD_REJECT) {
            if (lower.contains(bad)) return null;
        }

        String type;
        String category;

        if (containsAny(lower, "refund", "refunded", "reversal", "reversed", "cashback")) {
            type = "refund";
            category = "Refunds";
        } else if (containsAny(lower, "salary", "payroll", "salary transfer")) {
            type = "income";
            category = "Income";
        } else if (containsAny(lower, "inward transfer", "transfer received", "received from",
                "credited", "deposited", "cash deposit")) {
            type = "income";
            category = "Income";
        } else if (containsAny(lower, "outward transfer", "transfer sent", "transferred to", "sent to")) {
            type = "transfer_sent";
            category = "Transfers";
        } else {
            boolean outgoing = false;
            for (String kw : OUTGOING_KEYWORDS) {
                if (lower.contains(kw)) {
                    outgoing = true;
                    break;
                }
            }
            if (!outgoing) return null;
            type = detectExpenseType(lower);
            category = null;
        }

        double amount = 0.0;
        String currency = "AED";
        Matcher mCurr = PATTERN_CURRENCY_FIRST.matcher(combined);
        if (mCurr.find()) {
            currency = normalizeCurrency(mCurr.group(1));
            amount = parseAmount(mCurr.group(2));
        } else {
            Matcher mAmt = PATTERN_AMOUNT_FIRST.matcher(combined);
            if (mAmt.find()) {
                amount = parseAmount(mAmt.group(1));
                currency = normalizeCurrency(mAmt.group(2));
            }
        }
        if (amount <= 0.0) return null;

        String merchant = extractMerchant(combined, lower, type);
        if (category == null) category = categorizeMerchant(merchant, combined);

        Transaction t = new Transaction();
        t.setAmount(amount);
        t.setCurrency(currency);
        t.setMerchant(merchant);
        t.setBankSource(bank);
        t.setRawMessage(combined);
        t.setTimestamp(System.currentTimeMillis());
        t.setCategory(category);
        t.setTransactionType(type);
        return t;
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private static String extractMerchant(String combined, String lower, String type) {
        Matcher m = PATTERN_MERCHANT.matcher(combined);
        if (m.find()) return m.group(1).trim().replaceAll("[,.;:]+$", "");
        if ("atm".equals(type)) return "ATM Cash Withdrawal";
        if ("income".equals(type)) return "Incoming Funds";
        if ("refund".equals(type)) return "Refund";
        if ("transfer_sent".equals(type)) return "Transfer";
        return "Unknown Merchant";
    }

    private static String detectUaeBank(String title, String pkg, String text) {
        String combined = ((title == null ? "" : title) + " "
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
        if (lower.contains("online") || lower.contains("amazon") || lower.contains("noon")) return "online";
        if (lower.contains("pos")) return "pos";
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
        if (containsAny(c, "carrefour", "spinneys", "lulu", "choithrams", "waitrose")) return "Groceries";
        if (containsAny(c, "starbucks", "tim hortons", "talabat", "deliveroo", "cafe", "restaurant", "subway")) return "Food & Dining";
        if (containsAny(c, "enoc", "adnoc", "emarat", "fuel", "petrol", "salik", "careem", "uber")) return "Transport & Fuel";
        if (containsAny(c, "dewa", "sewa", "etisalat", "e&", "du telecom")) return "Utilities & Telecom";
        if (containsAny(c, "amazon", "noon", "virgin", "ikea", "zara", "centrepoint")) return "Shopping";
        if (containsAny(c, "vox", "cinema", "reel", "netflix", "spotify")) return "Entertainment";
        if (containsAny(c, "aster", "boots", "pharmacy", "hospital", "clinic")) return "Health & Pharmacy";
        if (containsAny(c, "gym", "fitness", "wellfit")) return "Fitness";
        if (containsAny(c, "atm", "withdrawal")) return "Cash / ATM";
        if (c.contains("fee")) return "Bank Fees & Charges";
        return "General";
    }
}
