package com.hossam.spendwatch;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BankTransactionParser {

    // Primary Bank: Ruya (configurable sender aliases)
    public static final List<String> RUYA_SENDER_ALIASES = Arrays.asList(
            "ruya", "ruyabank", "ruya-bank", "ruyadubai", "ae.ruya", "ae.ruyabank"
    );

    // Rejection keywords - Incoming, OTP, Declined, Reversals, Refunds
    private static final String[] REJECTION_KEYWORDS = {
            "declined", "failed", "unsuccessful", "not authorized", "insufficient funds",
            "otp", "one-time password", "verification code", "security code", "passcode",
            "salary", "payroll", "inward transfer", "transfer received", "credited",
            "deposited", "refund", "refunded", "reversed", "reversal", "cashback",
            "statement ready"
    };

    // Outgoing expense indicators
    private static final String[] OUTGOING_KEYWORDS = {
            "spent", "purchase", "purchased", "pos", "debit", "debited", "charged",
            "paid", "payment of", "withdrawn", "withdrawal", "outward transfer",
            "transfer sent", "transferred to", "sent to", "direct debit", "card used",
            "fee debited", "service fee", "apple pay"
    };

    // Regex for UAE AED (Dhs, DH, Dirhams) and Foreign Currencies
    private static final Pattern PATTERN_CURRENCY_FIRST = Pattern.compile(
            "(?:^|\\s)(AED|DHS|DH|DIRHAMS?|USD|EUR|GBP|SAR|QAR|KWD|OMR|BHD|INR|CAD|AUD)\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_AMOUNT_FIRST = Pattern.compile(
            "(?:^|\\s)([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)\\s*(AED|DHS|DH|DIRHAMS?|USD|EUR|GBP|SAR|QAR|KWD|OMR|BHD|INR|CAD|AUD)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_MERCHANT = Pattern.compile(
            "(?:at|@|to|towards|for|merchant|payee)\\s+([A-Za-z0-9&'._\\-\\s/]{2,35}?)(?:\\s+(?:on|with|using|via|ending|ref|avail|bal|available|card|account|approx|on card|from|dt|date|AED|\\.|$)|\\n|\\.|$)",
            Pattern.CASE_INSENSITIVE
    );

    public static Transaction parseNotification(String title, String body, String packageName) {
        String combined = (title != null ? title + " " : "") + (body != null ? body : "");
        if (combined.trim().isEmpty()) return null;

        String lower = combined.toLowerCase(Locale.ROOT);

        // 1. Strict rejection checks
        for (String rej : REJECTION_KEYWORDS) {
            if (lower.contains(rej)) {
                if (!lower.contains("debited") && !lower.contains("spent") && !lower.contains("purchase")) {
                    return null;
                }
            }
        }

        // 2. Check for outgoing keywords
        boolean hasOutgoing = false;
        for (String kw : OUTGOING_KEYWORDS) {
            if (lower.contains(kw)) {
                hasOutgoing = true;
                break;
            }
        }
        if (!hasOutgoing) return null;

        // 3. Extract Amount & Currency (Default primary: AED)
        double amount = 0.0;
        String currency = "AED";

        Matcher mCurr = PATTERN_CURRENCY_FIRST.matcher(combined);
        if (mCurr.find()) {
            currency = normalizeUaeCurrency(mCurr.group(1));
            amount = parseAmount(mCurr.group(2));
        } else {
            Matcher mAmt = PATTERN_AMOUNT_FIRST.matcher(combined);
            if (mAmt.find()) {
                amount = parseAmount(mAmt.group(1));
                currency = normalizeUaeCurrency(mAmt.group(2));
            }
        }
        if (amount <= 0.0) return null;

        // 4. Extract Merchant
        String merchant = "Unknown Merchant";
        Matcher mMerch = PATTERN_MERCHANT.matcher(combined);
        if (mMerch.find()) {
            merchant = mMerch.group(1).trim().replaceAll("[,.;:]+$", "");
        } else if (lower.contains("atm") || lower.contains("withdrawal")) {
            merchant = "ATM Cash Withdrawal";
        }

        // 5. Detect Bank (Ruya prioritized)
        String bank = detectUaeBank(title, packageName, combined);

        // 6. Transaction Type & Category
        String type = detectType(combined);
        String category = categorizeMerchant(merchant, combined);

        Transaction t = new Transaction();
        t.setAmount(amount);
        t.setCurrency(currency);
        t.setMerchant(merchant);
        t.setBankSource(bank);
        t.setRawMessage(combined.trim());
        t.setTimestamp(System.currentTimeMillis());
        t.setCategory(category);
        t.setTransactionType(type);
        return t;
    }

    private static String detectUaeBank(String title, String pkg, String text) {
        String combined = (title + " " + pkg + " " + text).toLowerCase(Locale.ROOT);
        // Prioritize Ruya
        for (String alias : RUYA_SENDER_ALIASES) {
            if (combined.contains(alias)) return "Ruya";
        }
        // Secondary UAE banks
        if (combined.contains("enbd") || combined.contains("emirates nbd")) return "Emirates NBD";
        if (combined.contains("adcb")) return "ADCB";
        if (combined.contains("fab") || combined.contains("first abu dhabi")) return "FAB";
        if (combined.contains("mashreq")) return "Mashreq";
        if (combined.contains("rakbank")) return "RAKBANK";
        if (combined.contains("cbd")) return "CBD";
        if (combined.contains("adib")) return "ADIB";
        if (combined.contains("dib") || combined.contains("dubai islamic")) return "Dubai Islamic Bank";
        if (combined.contains("hsbc")) return "HSBC UAE";
        if (combined.contains("stanchart") || combined.contains("standard chartered")) return "Standard Chartered UAE";
        return (title != null && !title.isEmpty()) ? title : "UAE Bank Alert";
    }

    private static String detectType(String text) {
        String l = text.toLowerCase(Locale.ROOT);
        if (l.contains("atm") || l.contains("cash withdrawal")) return "atm";
        if (l.contains("online") || l.contains("amazon") || l.contains("noon")) return "online";
        if (l.contains("pos")) return "pos";
        if (l.contains("outward transfer") || l.contains("transfer sent")) return "transfer_sent";
        if (l.contains("direct debit")) return "direct_debit";
        if (l.contains("fee")) return "fee";
        return "card_purchase";
    }

    private static String normalizeUaeCurrency(String raw) {
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
        if (c.contains("carrefour") || c.contains("spinneys") || c.contains("lulu") || c.contains("choithrams") || c.contains("waitrose")) return "Groceries";
        if (c.contains("starbucks") || c.contains("tim hortons") || c.contains("talabat") || c.contains("cafe") || c.contains("restaurant")) return "Food & Dining";
        if (c.contains("enoc") || c.contains("adnoc") || c.contains("emarat") || c.contains("fuel") || c.contains("petrol") || c.contains("salik") || c.contains("careem")) return "Transport & Fuel";
        if (c.contains("dewa") || c.contains("sewa") || c.contains("etisalat") || c.contains("du")) return "Utilities & Telecom";
        if (c.contains("amazon") || c.contains("noon") || c.contains("virgin") || c.contains("ikea") || c.contains("zara")) return "Shopping";
        if (c.contains("vox") || c.contains("cinema") || c.contains("reel")) return "Entertainment";
        if (c.contains("aster") || c.contains("boots") || c.contains("pharmacy")) return "Health & Pharmacy";
        if (c.contains("atm") || c.contains("withdrawal")) return "Cash / ATM";
        if (c.contains("fee")) return "Bank Fees & Charges";
        return "General";
    }
}
