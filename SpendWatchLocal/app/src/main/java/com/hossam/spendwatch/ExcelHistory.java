package com.hossam.spendwatch;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Real Excel XLSX export, fully offline. No third-party library or Internet
 * permission. The attached Android document is rewritten from all stored
 * history whenever a transaction arrives, not appended as raw CSV.
 */
public final class ExcelHistory {
    private static final String TAG = "SpendWatchExcel";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final String SETTING = "continuous_excel_uri";
    private ExcelHistory() {}

    public static void setContinuousDestination(Context context, Uri uri) {
        SpendDatabase.getInstance(context).setSetting(SETTING, uri == null ? "" : uri.toString());
        if (uri != null) schedule(context);
    }

    public static boolean isEnabled(Context context) {
        return !SpendDatabase.getInstance(context).getSetting(SETTING, "").isEmpty();
    }

    public static void schedule(Context context) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            String str = SpendDatabase.getInstance(app).getSetting(SETTING, "");
            if (str.isEmpty()) return;
            try {
                export(app, Uri.parse(str));
                SpendDatabase.getInstance(app).setSetting("excel_last_sync_at",
                        Long.toString(System.currentTimeMillis()));
                SpendDatabase.getInstance(app).setSetting("excel_last_error", "");
            } catch (Exception e) {
                SpendDatabase.getInstance(app).setSetting("excel_last_error",
                        e.getClass().getSimpleName() + ": " +
                                (e.getMessage() == null ? "unknown error" : e.getMessage()));
                Log.e(TAG, "Excel synchronization failed; history is still in SQLite", e);
            }
        });
    }

    public static void export(Context context, Uri destination) throws Exception {
        SpendDatabase database = SpendDatabase.getInstance(context);
        synchronized (database) {
            try (OutputStream out = context.getContentResolver().openOutputStream(destination, "wt")) {
                if (out == null) throw new IOException("Cannot write Excel workbook to selected file");
                try (ZipOutputStream zip = new ZipOutputStream(out)) {
                    entry(zip, "[Content_Types].xml",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                        + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                        + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                        + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                        + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                        + "<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                        + "<Override PartName=\"/xl/worksheets/sheet3.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                        + "<Override PartName=\"/xl/worksheets/sheet4.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                        + "<Override PartName=\"/xl/worksheets/sheet5.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                        + "<Override PartName=\"/xl/worksheets/sheet6.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                        + "<Override PartName=\"/xl/worksheets/sheet7.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                        + "</Types>");
                    entry(zip, "_rels/.rels",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                        + "</Relationships>");
                    entry(zip, "xl/workbook.xml",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                        + "<sheets><sheet name=\"Transactions\" sheetId=\"1\" r:id=\"rId1\"/>"
                        + "<sheet name=\"Monthly Summary\" sheetId=\"2\" r:id=\"rId2\"/>"
                        + "<sheet name=\"Envelopes\" sheetId=\"3\" r:id=\"rId3\"/>"
                        + "<sheet name=\"Savings Goals\" sheetId=\"4\" r:id=\"rId4\"/>"
                        + "<sheet name=\"Archive\" sheetId=\"5\" r:id=\"rId5\"/>"
                        + "<sheet name=\"Daily Summary\" sheetId=\"6\" r:id=\"rId6\"/>"
                        + "<sheet name=\"Planned Payments\" sheetId=\"7\" r:id=\"rId7\"/>"
                        + "</sheets></workbook>");
                    StringBuilder relationships = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
                    for (int i = 1; i <= 7; i++) relationships.append("<Relationship Id=\"rId")
                        .append(i).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                        .append(i).append(".xml\"/>");
                    relationships.append("<Relationship Id=\"rId8\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
                    entry(zip, "xl/_rels/workbook.xml.rels", relationships.toString());
                    entry(zip, "xl/styles.xml",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                        + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                        + "<font><b/><color rgb=\"FFFFFFFF\"/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                        + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill>"
                        + "<fill><patternFill patternType=\"gray125\"/></fill>"
                        + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF253B65\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
                        + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                        + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                        + "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                        + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFill=\"1\" applyFont=\"1\"/></cellXfs>"
                        + "</styleSheet>");
                    writeTransactions(zip, database);
                    writeMonthly(zip, database);
                    writeEnvelopes(zip, database);
                    writeGoals(zip, database);
                    writeArchive(zip, database);
                    writeDaily(zip, database);
                    writeOneTimeCommitments(zip, database);
                }
            }
        }
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
    private static void begin(ZipOutputStream zip, int index, String... columns) throws IOException {
        zip.putNextEntry(new ZipEntry("xl/worksheets/sheet" + index + ".xml"));
        put(zip, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<sheetViews><sheetView workbookViewId=\"0\"/></sheetViews>"
            + "<sheetFormatPr defaultRowHeight=\"18\"/>"
            + "<sheetData><row r=\"1\">");
        for (int i = 0; i < columns.length; i++) cell(zip, i, 1, columns[i], true);
        put(zip, "</row>");
    }
    private static void end(ZipOutputStream zip) throws IOException {
        put(zip, "</sheetData></worksheet>");
        zip.closeEntry();
    }
    private static void row(ZipOutputStream zip, int number, Object... values) throws IOException {
        put(zip, "<row r=\"" + number + "\">");
        for (int i = 0; i < values.length; i++) {
            Object v = values[i];
            if (v instanceof Number && Double.isFinite(((Number)v).doubleValue())) {
                put(zip, "<c r=\"" + ref(i,number) + "\"><v>" + v + "</v></c>");
            } else cell(zip,i,number, v == null ? "" : v.toString(),false);
        }
        put(zip, "</row>");
    }
    private static String ref(int index, int row) {
        StringBuilder col = new StringBuilder();
        int n = index + 1;
        while (n > 0) { n--; col.insert(0,(char)('A' + n % 26)); n /= 26; }
        return col.toString() + row;
    }
    private static void cell(ZipOutputStream zip, int index, int row, String content,
            boolean heading) throws IOException {
        // Inline strings cannot execute spreadsheet formula injection.
        put(zip, "<c r=\"" + ref(index,row) + "\" t=\"inlineStr\""
                + (heading ? " s=\"1\"" : "") + "><is><t xml:space=\"preserve\">"
                + escape(content) + "</t></is></c>");
    }
    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i=0;i<value.length();i++){
            char ch=value.charAt(i);
            if (ch=='&') out.append("&amp;");
            else if(ch=='<') out.append("&lt;");
            else if(ch=='>') out.append("&gt;");
            else if(ch=='\"') out.append("&quot;");
            else if(ch=='\'') out.append("&apos;");
            else if(ch>=0x20 || ch=='\n' || ch=='\r' || ch=='\t') out.append(ch);
        }
        return out.toString();
    }
    private static void put(ZipOutputStream zip, String value) throws IOException {
        zip.write(value.getBytes(StandardCharsets.UTF_8));
    }
    private static String date(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(millis));
    }

    private static void writeTransactions(ZipOutputStream zip, SpendDatabase db) throws IOException {
        begin(zip,1,"ID","Date & Time","Merchant","Amount","Currency","Category",
                "Transaction Type","Bank","Ignored","Original Notification");
        Cursor c=db.getReadableDatabase().rawQuery(
                "SELECT id,timestamp,merchant,amount,currency,category,transaction_type,"
                + "bank_source,is_ignored,raw_message FROM transactions ORDER BY timestamp ASC,id ASC",null);
        try {
            int row=2;
            while(c.moveToNext()){
                row(zip,row++,c.getLong(0),date(c.getLong(1)),c.getString(2),
                    c.getDouble(3),c.getString(4),c.getString(5),c.getString(6),
                    c.getString(7),c.getInt(8),c.getString(9));
            }
        }finally{c.close();}
        end(zip);
    }

    private static void writeMonthly(ZipOutputStream zip, SpendDatabase db) throws IOException {
        begin(zip,2,"Month (calendar)","Total AED Spending","Transactions",
                "Income AED","Refunds AED","Transfers AED");
        Cursor c=db.getReadableDatabase().rawQuery(
                "SELECT strftime('%Y-%m',timestamp/1000,'unixepoch','localtime') AS month,"
                + " SUM(CASE WHEN is_ignored=0 AND currency='AED' AND transaction_type IN "
                + "('card_purchase','pos','online','atm','direct_debit','fee','other_expense') THEN amount ELSE 0 END),"
                + " COUNT(*),"
                + " SUM(CASE WHEN is_ignored=0 AND currency='AED' AND transaction_type='income' THEN amount ELSE 0 END),"
                + " SUM(CASE WHEN is_ignored=0 AND currency='AED' AND transaction_type='refund' THEN amount ELSE 0 END),"
                + " SUM(CASE WHEN is_ignored=0 AND currency='AED' AND transaction_type='transfer_sent' THEN amount ELSE 0 END)"
                + " FROM transactions GROUP BY month ORDER BY month ASC",null);
        try{int i=2;while(c.moveToNext())row(zip,i++,c.getString(0),c.getDouble(1),
                c.getInt(2),c.getDouble(3),c.getDouble(4),c.getDouble(5));
        }finally{c.close();}
        end(zip);
    }
    private static void writeEnvelopes(ZipOutputStream zip, SpendDatabase db) throws IOException {
        begin(zip,3,"Envelope","Monthly AED","Carried AED","Available AED",
                "Spent this cycle AED","Remaining AED","Rollover","Sinking Fund");
        int i=2; for(BudgetEnvelope b:db.getBudgets()){
            double spent=db.getSpentForCategoryThisMonth(b.getName());
            row(zip,i++,b.getName(),b.getMonthlyAmount(),b.getCarryAmount(),
                    b.getEffectiveAmount(),spent,b.getEffectiveAmount()-spent,
                    b.isRolloverEnabled()?"Yes":"No",b.isSinkingFund()?"Yes":"No");
        } end(zip);
    }
    private static void writeGoals(ZipOutputStream zip, SpendDatabase db) throws IOException {
        begin(zip,4,"Savings Goal","Target AED","Saved AED","Remaining AED","Progress %");
        int i=2;for(SavingsGoal g:db.getSavingsGoals())row(zip,i++,g.getName(),
                g.getTargetAmount(),g.getSavedAmount(),g.getRemaining(),
                g.getTargetAmount()>0 ? 100*g.getSavedAmount()/g.getTargetAmount():0);
        end(zip);
    }
    private static void writeArchive(ZipOutputStream zip, SpendDatabase db) throws IOException {
        begin(zip,5,"ID","Saved At","Type","Name","Period Start",
                "Period End","Allocated/Target AED","Spent/Saved AED","Remaining AED","Notes");
        Cursor c=db.getReadableDatabase().rawQuery("SELECT id,created_at,kind,name,"
                + "period_start,period_end,allocated,spent_or_saved,remaining,details "
                + "FROM cycle_archive ORDER BY created_at ASC,id ASC",null);
        try{int i=2;while(c.moveToNext())row(zip,i++,c.getLong(0),date(c.getLong(1)),
                c.getString(2),c.getString(3),
                c.getLong(4)==0?"":date(c.getLong(4)),date(c.getLong(5)),
                c.getDouble(6),c.getDouble(7),c.getDouble(8),c.getString(9));
        }finally{c.close();}
        end(zip);
    }
    private static void writeDaily(ZipOutputStream zip, SpendDatabase db)
            throws IOException {
        begin(zip, 6, "Calendar Date", "Total AED Spending", "Transactions",
                "Income AED", "Refunds AED");
        String sql = "SELECT strftime('%Y-%m-%d', timestamp/1000, 'unixepoch','localtime') day,"
                + " SUM(CASE WHEN is_ignored=0 AND currency='AED' AND transaction_type IN "
                + "('card_purchase','pos','online','atm','direct_debit','fee','other_expense') THEN amount ELSE 0 END),"
                + " COUNT(*),"
                + " SUM(CASE WHEN is_ignored=0 AND currency='AED' AND transaction_type='income' THEN amount ELSE 0 END),"
                + " SUM(CASE WHEN is_ignored=0 AND currency='AED' AND transaction_type='refund' THEN amount ELSE 0 END)"
                + " FROM transactions GROUP BY day ORDER BY day ASC";
        try (Cursor c = db.getReadableDatabase().rawQuery(sql, null)) {
            int index = 2;
            while (c.moveToNext()) row(zip, index++, c.getString(0), c.getDouble(1),
                    c.getInt(2), c.getDouble(3), c.getDouble(4));
        }
        end(zip);
    }

    private static void writeOneTimeCommitments(ZipOutputStream zip, SpendDatabase db)
            throws IOException {
        begin(zip, 7, "ID", "Title", "Expected AED", "Due Date",
                "Reminder", "Status");
        int index = 2;
        for (OneTimeCommitment item : db.getOneTimeCommitments()) {
            String reminder = item.getRemindDays() < 0
                    ? -item.getRemindDays() + " month(s) before"
                    : item.getRemindDays() + " day(s) before";
            row(zip, index++, item.getId(), item.getTitle(), item.getAmount(),
                    date(item.getDueAt()), reminder, item.getStatus());
        }
        end(zip);
    }

}
