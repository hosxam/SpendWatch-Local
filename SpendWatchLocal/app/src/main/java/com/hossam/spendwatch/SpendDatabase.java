package com.hossam.spendwatch;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SpendDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "spendwatch_uae.db";
    private static final int DATABASE_VERSION = 4;

    public static final String TABLE_TRANSACTIONS = "transactions";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_AMOUNT = "amount";
    public static final String COLUMN_CURRENCY = "currency";
    public static final String COLUMN_MERCHANT = "merchant";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_BANK_SOURCE = "bank_source";
    public static final String COLUMN_RAW_MESSAGE = "raw_message";
    public static final String COLUMN_CATEGORY = "category";
    public static final String COLUMN_TYPE = "transaction_type";
    public static final String COLUMN_IS_IGNORED = "is_ignored";

    private static final String TABLE_BUDGETS = "budgets";
    private static final String TABLE_RECURRING = "recurring_bills";
    private static final String TABLE_SETTINGS = "settings";

    private static SpendDatabase instance;

    public static synchronized SpendDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new SpendDatabase(context.getApplicationContext());
        }
        return instance;
    }

    private SpendDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTransactionsTable(db);
        createV2Tables(db);
        seedDefaultBudgets(db);
    }

    private void createTransactionsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_TRANSACTIONS + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_AMOUNT + " REAL NOT NULL, "
                + COLUMN_CURRENCY + " TEXT NOT NULL, "
                + COLUMN_MERCHANT + " TEXT NOT NULL, "
                + COLUMN_TIMESTAMP + " INTEGER NOT NULL, "
                + COLUMN_BANK_SOURCE + " TEXT, "
                + COLUMN_RAW_MESSAGE + " TEXT, "
                + COLUMN_CATEGORY + " TEXT, "
                + COLUMN_TYPE + " TEXT, "
                + COLUMN_IS_IGNORED + " INTEGER DEFAULT 0"
                + ");");
    }

    private void createV2Tables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_BUDGETS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL UNIQUE, "
                + "monthly_amount REAL NOT NULL DEFAULT 0, "
                + "rollover_enabled INTEGER NOT NULL DEFAULT 0, "
                + "carry_amount REAL NOT NULL DEFAULT 0, "
                + "is_sinking INTEGER NOT NULL DEFAULT 0"
                + ");");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_RECURRING + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, "
                + "amount REAL NOT NULL, "
                + "currency TEXT NOT NULL DEFAULT 'AED', "
                + "due_day INTEGER NOT NULL, "
                + "category TEXT NOT NULL DEFAULT 'General', "
                + "active INTEGER NOT NULL DEFAULT 1"
                + ");");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_SETTINGS + " ("
                + "key TEXT PRIMARY KEY, "
                + "value TEXT"
                + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        createTransactionsTable(db);
        createV2Tables(db);
        seedDefaultBudgets(db);
    }

    private void seedDefaultBudgets(SQLiteDatabase db) {
        String[] names = new String[]{
                "Groceries", "Food & Dining", "Transport & Fuel", "Shopping",
                "Entertainment", "Health & Pharmacy", "Utilities & Telecom",
                "Fitness", "Cash / ATM", "Bank Fees & Charges", "General"
        };
        for (String name : names) {
            ContentValues cv = new ContentValues();
            cv.put("name", name);
            cv.put("monthly_amount", 0.0);
            cv.put("rollover_enabled", 0);
            cv.put("carry_amount", 0.0);
            cv.put("is_sinking", 0);
            db.insertWithOnConflict(TABLE_BUDGETS, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    public synchronized boolean isDuplicate(Transaction t) {
        SQLiteDatabase db = getReadableDatabase();
        long window = 10 * 60 * 1000L;
        long start = t.getTimestamp() - window;
        long end = t.getTimestamp() + window;
        String query = "SELECT " + COLUMN_ID + " FROM " + TABLE_TRANSACTIONS
                + " WHERE LOWER(" + COLUMN_BANK_SOURCE + ") = LOWER(?)"
                + " AND " + COLUMN_AMOUNT + " = ?"
                + " AND " + COLUMN_CURRENCY + " = ?"
                + " AND LOWER(" + COLUMN_MERCHANT + ") = LOWER(?)"
                + " AND " + COLUMN_TIMESTAMP + " BETWEEN ? AND ? LIMIT 1";
        Cursor cursor = db.rawQuery(query, new String[]{
                t.getBankSource(), String.valueOf(t.getAmount()), t.getCurrency(),
                t.getMerchant(), String.valueOf(start), String.valueOf(end)
        });
        boolean duplicate = cursor.moveToFirst();
        cursor.close();
        return duplicate;
    }

    public synchronized long insertTransaction(Transaction transaction) {
        if (isDuplicate(transaction)) return -1L;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_AMOUNT, transaction.getAmount());
        values.put(COLUMN_CURRENCY, transaction.getCurrency());
        values.put(COLUMN_MERCHANT, transaction.getMerchant());
        values.put(COLUMN_TIMESTAMP, transaction.getTimestamp());
        values.put(COLUMN_BANK_SOURCE, transaction.getBankSource());
        values.put(COLUMN_RAW_MESSAGE, transaction.getRawMessage());
        values.put(COLUMN_CATEGORY, transaction.getCategory());
        values.put(COLUMN_TYPE, transaction.getTransactionType());
        values.put(COLUMN_IS_IGNORED, transaction.isIgnored() ? 1 : 0);
        long id = db.insert(TABLE_TRANSACTIONS, null, values);
        transaction.setId(id);
        return id;
    }

    public synchronized List<Transaction> getAllTransactions() {
        List<Transaction> list = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query(
                TABLE_TRANSACTIONS, null, null, null, null, null,
                COLUMN_TIMESTAMP + " DESC"
        );
        try {
            int idIdx = cursor.getColumnIndexOrThrow(COLUMN_ID);
            int amountIdx = cursor.getColumnIndexOrThrow(COLUMN_AMOUNT);
            int currIdx = cursor.getColumnIndexOrThrow(COLUMN_CURRENCY);
            int merchIdx = cursor.getColumnIndexOrThrow(COLUMN_MERCHANT);
            int timeIdx = cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP);
            int bankIdx = cursor.getColumnIndexOrThrow(COLUMN_BANK_SOURCE);
            int rawIdx = cursor.getColumnIndexOrThrow(COLUMN_RAW_MESSAGE);
            int catIdx = cursor.getColumnIndexOrThrow(COLUMN_CATEGORY);
            int typeIdx = cursor.getColumnIndexOrThrow(COLUMN_TYPE);
            int ignIdx = cursor.getColumnIndexOrThrow(COLUMN_IS_IGNORED);
            while (cursor.moveToNext()) {
                list.add(new Transaction(
                        cursor.getLong(idIdx),
                        cursor.getDouble(amountIdx),
                        cursor.getString(currIdx),
                        cursor.getString(merchIdx),
                        cursor.getLong(timeIdx),
                        cursor.getString(bankIdx),
                        cursor.getString(rawIdx),
                        cursor.getString(catIdx),
                        cursor.getString(typeIdx),
                        cursor.getInt(ignIdx) == 1
                ));
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    private String spendingTypesSql() {
        return "('" + "card_purchase" + "','" + "pos" + "','" + "online" + "','"
                + "atm" + "','" + "direct_debit" + "','" + "fee" + "','" + "other_expense" + "')";
    }

    public synchronized double getTotalSpentAedBetween(long startTimestamp, long endTimestamp) {
        String query = "SELECT COALESCE(SUM(" + COLUMN_AMOUNT + "),0) FROM " + TABLE_TRANSACTIONS
                + " WHERE " + COLUMN_CURRENCY + "='AED'"
                + " AND " + COLUMN_IS_IGNORED + "=0"
                + " AND " + COLUMN_TYPE + " IN " + spendingTypesSql()
                + " AND " + COLUMN_TIMESTAMP + " BETWEEN ? AND ?";
        Cursor c = getReadableDatabase().rawQuery(query, new String[]{
                String.valueOf(startTimestamp), String.valueOf(endTimestamp)
        });
        double result = 0.0;
        if (c.moveToFirst()) result = c.getDouble(0);
        c.close();
        return result;
    }

    public synchronized double getSpentForCategoryBetween(String category, long start, long end) {
        String query = "SELECT COALESCE(SUM(" + COLUMN_AMOUNT + "),0) FROM " + TABLE_TRANSACTIONS
                + " WHERE " + COLUMN_CURRENCY + "='AED'"
                + " AND " + COLUMN_IS_IGNORED + "=0"
                + " AND " + COLUMN_TYPE + " IN " + spendingTypesSql()
                + " AND " + COLUMN_CATEGORY + "=?"
                + " AND " + COLUMN_TIMESTAMP + " BETWEEN ? AND ?";
        Cursor c = getReadableDatabase().rawQuery(query, new String[]{
                category, String.valueOf(start), String.valueOf(end)
        });
        double result = 0.0;
        if (c.moveToFirst()) result = c.getDouble(0);
        c.close();
        return result;
    }

    private long startOfToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private long startOfMonth() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    public synchronized double getTotalSpentAedToday() {
        return getTotalSpentAedBetween(startOfToday(), System.currentTimeMillis());
    }

    public synchronized double getTotalSpentAedThisWeek() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return getTotalSpentAedBetween(cal.getTimeInMillis(), System.currentTimeMillis());
    }

    public synchronized double getTotalSpentAedThisMonth() {
        return getTotalSpentAedBetween(startOfMonth(), System.currentTimeMillis());
    }

    public synchronized double getTotalSpentAedAllTime() {
        return getTotalSpentAedBetween(0L, System.currentTimeMillis());
    }

    public synchronized boolean updateTransaction(long id, String category, String type, boolean ignored) {
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_CATEGORY, category);
        cv.put(COLUMN_TYPE, type);
        cv.put(COLUMN_IS_IGNORED, ignored ? 1 : 0);
        return getWritableDatabase().update(
                TABLE_TRANSACTIONS, cv, COLUMN_ID + "=?", new String[]{String.valueOf(id)}
        ) > 0;
    }

    public synchronized boolean deleteTransaction(long id) {
        return getWritableDatabase().delete(
                TABLE_TRANSACTIONS, COLUMN_ID + "=?", new String[]{String.valueOf(id)}
        ) > 0;
    }

    public synchronized void clearAll() {
        getWritableDatabase().delete(TABLE_TRANSACTIONS, null, null);
    }

    private List<BudgetEnvelope> getBudgetsRaw() {
        List<BudgetEnvelope> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query(
                TABLE_BUDGETS, null, null, null, null, null, "is_sinking ASC, name ASC"
        );
        try {
            while (c.moveToNext()) {
                list.add(new BudgetEnvelope(
                        c.getLong(c.getColumnIndexOrThrow("id")),
                        c.getString(c.getColumnIndexOrThrow("name")),
                        c.getDouble(c.getColumnIndexOrThrow("monthly_amount")),
                        c.getInt(c.getColumnIndexOrThrow("rollover_enabled")) == 1,
                        c.getDouble(c.getColumnIndexOrThrow("carry_amount")),
                        c.getInt(c.getColumnIndexOrThrow("is_sinking")) == 1
                ));
            }
        } finally {
            c.close();
        }
        return list;
    }

    public synchronized List<BudgetEnvelope> getBudgets() {
        ensureMonthlyRollover();
        return getBudgetsRaw();
    }

    public synchronized long saveBudget(BudgetEnvelope b) {
        ContentValues cv = new ContentValues();
        cv.put("name", b.getName().trim());
        cv.put("monthly_amount", Math.max(0.0, b.getMonthlyAmount()));
        cv.put("rollover_enabled", (b.isRolloverEnabled() || b.isSinkingFund()) ? 1 : 0);
        cv.put("carry_amount", Math.max(0.0, b.getCarryAmount()));
        cv.put("is_sinking", b.isSinkingFund() ? 1 : 0);
        if (b.getId() > 0) {
            getWritableDatabase().update(TABLE_BUDGETS, cv, "id=?", new String[]{String.valueOf(b.getId())});
            return b.getId();
        }
        return getWritableDatabase().insertWithOnConflict(TABLE_BUDGETS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized boolean deleteBudget(long id) {
        return getWritableDatabase().delete(TABLE_BUDGETS, "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public synchronized double getSpentForCategoryThisMonth(String category) {
        return getSpentForCategoryBetween(category, startOfMonth(), System.currentTimeMillis());
    }

    private void ensureMonthlyRollover() {
        Calendar now = Calendar.getInstance();
        int currentYm = now.get(Calendar.YEAR) * 100 + (now.get(Calendar.MONTH) + 1);
        int lastYm = (int) getSettingDouble("last_rollover_month", 0);
        if (lastYm == 0) {
            setSetting("last_rollover_month", String.valueOf(currentYm));
            return;
        }
        if (lastYm == currentYm) return;

        int year = lastYm / 100;
        int month = lastYm % 100;
        Calendar cursor = Calendar.getInstance();
        cursor.set(year, month - 1, 1, 0, 0, 0);
        cursor.set(Calendar.MILLISECOND, 0);
        cursor.add(Calendar.MONTH, 1);

        Calendar currentStart = Calendar.getInstance();
        currentStart.set(Calendar.DAY_OF_MONTH, 1);
        currentStart.set(Calendar.HOUR_OF_DAY, 0);
        currentStart.set(Calendar.MINUTE, 0);
        currentStart.set(Calendar.SECOND, 0);
        currentStart.set(Calendar.MILLISECOND, 0);

        while (!cursor.after(currentStart)) {
            Calendar prevStart = (Calendar) cursor.clone();
            prevStart.add(Calendar.MONTH, -1);
            Calendar prevEnd = (Calendar) cursor.clone();
            prevEnd.add(Calendar.MILLISECOND, -1);

            List<BudgetEnvelope> budgets = getBudgetsRaw();
            for (BudgetEnvelope b : budgets) {
                double newCarry = 0.0;
                if (b.isRolloverEnabled() || b.isSinkingFund()) {
                    double spent = getSpentForCategoryBetween(
                            b.getName(), prevStart.getTimeInMillis(), prevEnd.getTimeInMillis()
                    );
                    newCarry = Math.max(0.0, b.getMonthlyAmount() + b.getCarryAmount() - spent);
                }
                ContentValues cv = new ContentValues();
                cv.put("carry_amount", newCarry);
                getWritableDatabase().update(TABLE_BUDGETS, cv, "id=?",
                        new String[]{String.valueOf(b.getId())});
            }

            int processedYm = cursor.get(Calendar.YEAR) * 100 + (cursor.get(Calendar.MONTH) + 1);
            setSetting("last_rollover_month", String.valueOf(processedYm));
            if (processedYm == currentYm) break;
            cursor.add(Calendar.MONTH, 1);
        }
    }

    public synchronized double getTotalSinkingMonthlyAllocation() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(monthly_amount),0) FROM " + TABLE_BUDGETS + " WHERE is_sinking=1",
                null
        );
        double result = 0.0;
        if (c.moveToFirst()) result = c.getDouble(0);
        c.close();
        return result;
    }

    public synchronized List<RecurringBill> getRecurringBills() {
        List<RecurringBill> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query(
                TABLE_RECURRING, null, null, null, null, null, "due_day ASC, name ASC"
        );
        try {
            while (c.moveToNext()) {
                list.add(new RecurringBill(
                        c.getLong(c.getColumnIndexOrThrow("id")),
                        c.getString(c.getColumnIndexOrThrow("name")),
                        c.getDouble(c.getColumnIndexOrThrow("amount")),
                        c.getString(c.getColumnIndexOrThrow("currency")),
                        c.getInt(c.getColumnIndexOrThrow("due_day")),
                        c.getString(c.getColumnIndexOrThrow("category")),
                        c.getInt(c.getColumnIndexOrThrow("active")) == 1
                ));
            }
        } finally {
            c.close();
        }
        return list;
    }

    public synchronized long saveRecurringBill(RecurringBill bill) {
        ContentValues cv = new ContentValues();
        cv.put("name", bill.getName().trim());
        cv.put("amount", Math.max(0.0, bill.getAmount()));
        cv.put("currency", bill.getCurrency() == null ? "AED" : bill.getCurrency());
        cv.put("due_day", Math.max(1, Math.min(31, bill.getDueDay())));
        cv.put("category", bill.getCategory() == null ? "General" : bill.getCategory());
        cv.put("active", bill.isActive() ? 1 : 0);
        if (bill.getId() > 0) {
            getWritableDatabase().update(TABLE_RECURRING, cv, "id=?", new String[]{String.valueOf(bill.getId())});
            return bill.getId();
        }
        return getWritableDatabase().insert(TABLE_RECURRING, null, cv);
    }

    public synchronized boolean deleteRecurringBill(long id) {
        return getWritableDatabase().delete(TABLE_RECURRING, "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public synchronized double getUpcomingRecurringAedThisMonth() {
        int today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount),0) FROM " + TABLE_RECURRING
                        + " WHERE active=1 AND currency='AED' AND due_day>=?",
                new String[]{String.valueOf(today)}
        );
        double result = 0.0;
        if (c.moveToFirst()) result = c.getDouble(0);
        c.close();
        return result;
    }

    public synchronized void setSetting(String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put("key", key);
        cv.put("value", value);
        getWritableDatabase().insertWithOnConflict(TABLE_SETTINGS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized String getSetting(String key, String defaultValue) {
        Cursor c = getReadableDatabase().query(
                TABLE_SETTINGS, new String[]{"value"}, "key=?", new String[]{key},
                null, null, null
        );
        String result = defaultValue;
        if (c.moveToFirst()) result = c.getString(0);
        c.close();
        return result == null ? defaultValue : result;
    }

    public synchronized double getSettingDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(getSetting(key, String.valueOf(defaultValue)));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public synchronized double getMonthlyIncome() {
        return getSettingDouble("monthly_income", 0.0);
    }

    public synchronized void setMonthlyIncome(double amount) {
        setSetting("monthly_income", String.format(Locale.US, "%.2f", Math.max(0.0, amount)));
    }

    public synchronized double getSafeToSpendRestOfMonth() {
        double income = getMonthlyIncome();
        if (income <= 0.0) return 0.0;
        double spent = getTotalSpentAedThisMonth();
        double upcomingBills = getUpcomingRecurringAedThisMonth();
        double sinking = getTotalSinkingMonthlyAllocation();
        return Math.max(0.0, income - spent - upcomingBills - sinking);
    }

    public synchronized double getProjectedMonthEndSpend() {
        Calendar now = Calendar.getInstance();
        int day = Math.max(1, now.get(Calendar.DAY_OF_MONTH));
        int daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH);
        double spent = getTotalSpentAedThisMonth();
        double averageDaily = spent / day;
        int remainingDays = Math.max(0, daysInMonth - day);
        return spent + (averageDaily * remainingDays) + getUpcomingRecurringAedThisMonth();
    }

    public synchronized String getTopBudgetPaceInsight() {
        List<BudgetEnvelope> budgets = getBudgets();
        Calendar now = Calendar.getInstance();
        double monthProgress = (double) now.get(Calendar.DAY_OF_MONTH)
                / (double) now.getActualMaximum(Calendar.DAY_OF_MONTH);
        BudgetEnvelope worst = null;
        double worstGap = 0.0;
        double worstRatio = 0.0;
        for (BudgetEnvelope b : budgets) {
            double effective = b.getEffectiveAmount();
            if (effective <= 0.0) continue;
            double spent = getSpentForCategoryThisMonth(b.getName());
            double ratio = spent / effective;
            double gap = ratio - monthProgress;
            if (gap > worstGap) {
                worstGap = gap;
                worst = b;
                worstRatio = ratio;
            }
        }
        if (worst != null && worstGap >= 0.15) {
            return worst.getName() + " is running ahead of pace at "
                    + Math.round(worstRatio * 100) + "% of its monthly envelope.";
        }
        return "Your funded envelopes are broadly on pace this month.";
    }
}
