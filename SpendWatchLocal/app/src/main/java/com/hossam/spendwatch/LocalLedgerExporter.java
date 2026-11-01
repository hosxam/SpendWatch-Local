package com.hossam.spendwatch;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates a genuine offline .xlsx workbook (OOXML), no cloud or network SDK. */
public final class LocalLedgerExporter {
    private static final String PREFS = "spendwatch_ledger_export";
    private static final String URI_KEY = "linked_spreadsheet_uri";
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean DIRTY = new AtomicBoolean(false);
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static final String[] SHEET_NAMES = {
            "Transactions", "Monthly Summary", "Envelopes", "Savings Goals", "Reset History"
    };
    private LocalLedgerExporter() {}

    public static void linkSpreadsheet(Context context, Uri uri) {
        Context app = context.getApplicationContext();
        app.getContentResolver().takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(URI_KEY, uri.toString()).apply();
        queueRefresh(app);
    }

    public static boolean hasLinkedSpreadsheet(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .contains(URI_KEY);
    }

    public static void unlinkSpreadsheet(Context context) {
        String linked = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(URI_KEY, null);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(URI_KEY).apply();
        if (linked != null) {
            try {
                context.getContentResolver().releasePersistableUriPermission(
                        Uri.parse(linked),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception ignored) {}
        }
    }

    public static void queueRefresh(Context context) {
        Context app = context.getApplicationContext();
        if (!hasLinkedSpreadsheet(app)) return;
        DIRTY.set(true);
        if (!BUSY.compareAndSet(false, true)) return;
        WORKER.execute(() -> {
            try {
                while (DIRTY.getAndSet(false)) {
                    String text = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .getString(URI_KEY, null);
                    if (text == null) break;
                    try {
                        exportToUri(app, Uri.parse(text));
                        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                .remove("last_error").apply();
                    } catch (Exception ex) {
                        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                .putString("last_error", ex.getMessage()).apply();
                    }
                }
            } finally {
                BUSY.set(false);
                if (DIRTY.get() && hasLinkedSpreadsheet(app)) queueRefresh(app);
            }
        });
    }

    public static String getLastError(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("last_error", null);
    }

    public static void exportToUri(Context context, Uri uri) throws Exception {
        File temp = File.createTempFile("spendwatch-ledger-", ".xlsx", context.getCacheDir());
        try {
            try (OutputStream output = new FileOutputStream(temp)) {
                writeWorkbook(context, output);
            }
            try (InputStream input = new FileInputStream(temp);
                 OutputStream output = context.getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new java.io.IOException("Cannot write selected file");
                byte[] buffer = new byte[32768];
                int n;
                while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
                output.flush();
            }
        } finally {
            temp.delete();
        }
    }

    public static void writeWorkbook(Context context, OutputStream output) throws Exception {
        SpendDatabase db = SpendDatabase.getInstance(context);
        List<Transaction> transactions = db.getAllTransactions();
        List<BudgetEnvelope> budgets = db.getBudgets();
        List<SavingsGoal> goals = db.getSavingsGoals();
        List<String[]> resets = db.getResetHistoryRows();
        if (transactions.size() > 1048575)
            throw new IllegalArgumentException("More than Excel's limit of 1,048,575 transactions");

        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            entry(zip, "[Content_Types].xml", contentTypes());
            entry(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");
            entry(zip, "xl/workbook.xml", workbookXml());
            entry(zip, "xl/_rels/workbook.xml.rels", workbookRels());
            entry(zip, "xl/styles.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                    + "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                    + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill>"
                    + "<fill><patternFill patternType=\"gray125\"/></fill></fills>"
                    + "<borders count=\"1\"><border/></borders>"
                    + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                    + "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                    + "<xf numFmtId=\"2\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/></cellXfs>"
                    + "</styleSheet>");

            sheetStart(zip, 1);
            String[] txColumns = {
                    "Transaction ID", "Timestamp", "Merchant", "Amount", "Currency",
                    "Category", "Type", "Bank", "Ignored", "Raw Notification"
            };
            row(zip, 1, txColumns);
            int row = 2;
            // Preserve oldest-first ledger order for easy long-term review in Excel.
            java.util.Collections.reverse(transactions);
            for (Transaction t : transactions) {
                rowMixed(zip, row++, new Object[]{
                        t.getId(), isoTime(t.getTimestamp()), t.getMerchant(), t.getAmount(),
                        t.getCurrency(), t.getCategory(), t.getTransactionType(),
                        t.getBankSource(), t.isIgnored() ? "Yes" : "No", t.getRawMessage()
                });
            }
            sheetEnd(zip);

            sheetStart(zip, 2);
            row(zip, 1, new String[]{
                    "Budget cycle starting", "Expenses AED", "Refunds AED",
                    "Net expenses AED", "Income AED", "Transfers AED", "Transaction count"
            });
            Map<Long, double[]> aggregates = new java.util.TreeMap<>();
            for (Transaction t : transactions) {
                if (!"AED".equalsIgnoreCase(t.getCurrency()) || t.isIgnored()) continue;
                long start = db.getCycleStart(t.getTimestamp());
                double[] acc = aggregates.get(start);
                if (acc == null) { acc = new double[5]; aggregates.put(start, acc); }
                String type = t.getTransactionType();
                if (expenseType(type)) acc[0] += t.getAmount();
                else if ("refund".equals(type)) acc[1] += t.getAmount();
                else if ("income".equals(type)) acc[2] += t.getAmount();
                else if ("transfer_sent".equals(type)) acc[3] += t.getAmount();
                acc[4]++;
            }
            row = 2;
            for (Map.Entry<Long,double[]> item : aggregates.entrySet()) {
                double[] a = item.getValue();
                rowMixed(zip, row++, new Object[]{
                        dateOnly(item.getKey()), a[0], a[1],
                        a[0] - a[1], a[2], a[3], a[4]
                });
            }
            sheetEnd(zip);

            sheetStart(zip, 3);
            row(zip, 1, new String[]{
                    "Envelope", "Monthly Allocation AED", "Carry AED",
                    "Effective Budget AED", "Spent Since Reset AED", "Remaining AED",
                    "Rollover", "Sinking Fund", "Order"
            });
            row = 2;
            for (int i = 0; i < budgets.size(); i++) {
                BudgetEnvelope b = budgets.get(i);
                double spent = db.getSpentForCategoryThisMonth(b.getName());
                rowMixed(zip, row++, new Object[]{
                        b.getName(), b.getMonthlyAmount(), b.getCarryAmount(),
                        b.getEffectiveAmount(), spent, b.getEffectiveAmount() - spent,
                        b.isRolloverEnabled() ? "Yes" : "No",
                        b.isSinkingFund() ? "Yes" : "No", i + 1
                });
            }
            sheetEnd(zip);

            sheetStart(zip, 4);
            row(zip, 1, new String[]{
                    "Savings Goal", "Target AED", "Saved AED", "Remaining AED", "Progress %"
            });
            row = 2;
            for (SavingsGoal g : goals) {
                double progress = g.getTargetAmount() > 0
                        ? Math.min(100, g.getSavedAmount() / g.getTargetAmount() * 100) : 0;
                rowMixed(zip, row++, new Object[]{
                        g.getName(), g.getTargetAmount(), g.getSavedAmount(),
                        g.getRemaining(), progress
                });
            }
            sheetEnd(zip);

            sheetStart(zip, 5);
            row(zip, 1, new String[]{
                    "Reset Timestamp", "Reset Type", "Item", "Balance Before AED", "Target AED"
            });
            row = 2;
            for (String[] r : resets) {
                rowMixed(zip, row++, new Object[]{
                        isoTime(Long.parseLong(r[0])), r[1], r[2],
                        Double.parseDouble(r[3]), Double.parseDouble(r[4])
                });
            }
            sheetEnd(zip);
        }
    }

    static boolean expenseType(String type) {
        return "card_purchase".equals(type) || "pos".equals(type) ||
               "online".equals(type) || "atm".equals(type) ||
               "direct_debit".equals(type) || "fee".equals(type) ||
               "other_expense".equals(type);
    }

    private static String isoTime(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(millis));
    }
    private static String dateOnly(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(millis));
    }

    private static String contentTypes() {
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
                .append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 1; i <= SHEET_NAMES.length; i++) {
            b.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return b.append("</Types>").toString();
    }

    private static String workbookXml() {
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
                .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
                .append("<sheets>");
        for (int i = 0; i < SHEET_NAMES.length; i++) {
            b.append("<sheet name=\"").append(SHEET_NAMES[i])
                    .append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return b.append("</sheets></workbook>").toString();
    }
    private static String workbookRels() {
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 1; i <= SHEET_NAMES.length; i++) {
            b.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i).append(".xml\"/>");
        }
        b.append("<Relationship Id=\"rId").append(SHEET_NAMES.length + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return b.append("</Relationships>").toString();
    }

    private static void entry(ZipOutputStream zip, String filename, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(filename));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
    private static void sheetStart(ZipOutputStream zip, int i) throws Exception {
        zip.putNextEntry(new ZipEntry("xl/worksheets/sheet" + i + ".xml"));
        zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
                .getBytes(StandardCharsets.UTF_8));
    }
    private static void sheetEnd(ZipOutputStream zip) throws Exception {
        zip.write("</sheetData></worksheet>".getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
    private static void row(ZipOutputStream zip, int row, String[] data) throws Exception {
        Object[] values = new Object[data.length];
        System.arraycopy(data, 0, values, 0, data.length);
        rowMixed(zip, row, values);
    }
    private static void rowMixed(ZipOutputStream zip, int number, Object[] values) throws Exception {
        StringBuilder b = new StringBuilder(512);
        b.append("<row r=\"").append(number).append("\">");
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            String address = column(i) + number;
            if (value instanceof Number) {
                double n = ((Number)value).doubleValue();
                if (Double.isFinite(n)) {
                    b.append("<c r=\"").append(address).append("\" s=\"1\"><v>")
                            .append(String.format(Locale.US, "%.6f", n))
                            .append("</v></c>");
                }
            } else {
                b.append("<c r=\"").append(address).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(xml(value == null ? "" : value.toString()))
                        .append("</t></is></c>");
            }
        }
        b.append("</row>");
        zip.write(b.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static String column(int i) {
        StringBuilder b = new StringBuilder();
        do { b.insert(0, (char)('A' + i % 26)); i = i / 26 - 1; } while (i >= 0);
        return b.toString();
    }
    private static String xml(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                case '\'': b.append("&apos;"); break;
                default:
                    if (ch == 9 || ch == 10 || ch == 13 || ch >= 32) b.append(ch);
            }
        }
        return b.toString();
    }
}
