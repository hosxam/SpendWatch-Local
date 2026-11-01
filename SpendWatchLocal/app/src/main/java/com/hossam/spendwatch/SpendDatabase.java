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
    private static final int DATABASE_VERSION = 7;

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
    private static final String TABLE_SAVINGS_GOALS = "savings_goals";
    private static final String TABLE_RESET_HISTORY = "reset_history";
    // Explicit user corrections only, separate from automatically guessed categories.
    private static final String TABLE_MERCHANT_RULES = "merchant_category_rules";

    private static SpendDatabase instance;
    private final Context applicationContext;

    public static synchronized SpendDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new SpendDatabase(context.getApplicationContext());
        }
        return instance;
    }

    private SpendDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        applicationContext = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTransactionsTable(db);
        createV2Tables(db);
        createMerchantRulesTable(db);
        createSavingsGoalsTable(db);
        createResetHistoryTable(db);
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
                + "is_sinking INTEGER NOT NULL DEFAULT 0, "
                + "sort_order INTEGER NOT NULL DEFAULT 0"
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
        // Only adds new structures; existing users retain transactions and budgets.
        createTransactionsTable(db);
        createV2Tables(db);
        createMerchantRulesTable(db);
        createSavingsGoalsTable(db);
        createResetHistoryTable(db);
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE " + TABLE_BUDGETS
                    + " ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE " + TABLE_BUDGETS + " SET sort_order=id");
        }
        seedDefaultBudgets(db);
    }

    private void createResetHistoryTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_RESET_HISTORY + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "reset_time INTEGER NOT NULL, "
                + "reset_kind TEXT NOT NULL, "
                + "item_name TEXT NOT NULL, "
                + "previous_amount REAL NOT NULL, "
                + "target_amount REAL NOT NULL"
                + ");");
    }

    private void signalLedgerChange() {
        // Async, coalesced refresh; never blocks the notification listener.
        LocalLedgerExporter.queueRefresh(applicationContext);
    }

    public synchronized int getBudgetStartDay() {
        return Math.max(1, Math.min(31, (int) getSettingDouble("budget_start_day", 1)));
    }

    public synchronized void setBudgetStartDay(int day) {
        if (day < 1 || day > 31) throw new IllegalArgumentException("Start day must be 1-31");
        // Keep existing carry balances; only the period boundary changes.
        setSetting("budget_start_day", String.valueOf(day));
        setSetting("last_rollover_cycle_start", String.valueOf(getCycleStart(System.currentTimeMillis())));
        signalLedgerChange();
    }

    public synchronized long getCycleStart(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        int day = getBudgetStartDay();
        cal.set(Calendar.DAY_OF_MONTH, Math.min(day, cal.getActualMaximum(Calendar.DAY_OF_MONTH)));
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (timestamp < cal.getTimeInMillis()) {
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.add(Calendar.MONTH, -1);
            cal.set(Calendar.DAY_OF_MONTH, Math.min(day, cal.getActualMaximum(Calendar.DAY_OF_MONTH)));
        }
        return cal.getTimeInMillis();
    }

    public synchronized long getCycleEnd(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(getCycleStart(timestamp));
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.add(Calendar.MONTH, 1);
        cal.set(Calendar.DAY_OF_MONTH, Math.min(getBudgetStartDay(),
                cal.getActualMaximum(Calendar.DAY_OF_MONTH)));
        return cal.getTimeInMillis();
    }

    public synchronized long getCurrentCycleStart() {
        return getCycleStart(System.currentTimeMillis());
    }

    public synchronized long getCurrentCycleEnd() {
        return getCycleEnd(System.currentTimeMillis());
    }

    public synchronized double getEstimatedBalanceRemaining() {
        return getMonthlyIncome() - getTotalSpentAedThisMonth();
    }

    public synchronized double getTotalAllocatedBudget() {
        double total = 0;
        for (BudgetEnvelope b : getBudgets()) total += Math.max(0, b.getEffectiveAmount());
        return total;
    }

    public synchronized double getTotalBudgetRemaining() {
        double total = 0;
        for (BudgetEnvelope b : getBudgets()) {
            if (b.getEffectiveAmount() > 0) {
                total += b.getEffectiveAmount() - getSpentForCategoryThisMonth(b.getName());
            }
        }
        return total;
    }

    public synchronized List<String[]> getResetHistoryRows() {
        ArrayList<String[]> out = new ArrayList<>();
        Cursor cur = getReadableDatabase().query(TABLE_RESET_HISTORY, null,
                null, null, null, null, "reset_time DESC, id DESC");
        try {
            while (cur.moveToNext()) {
                out.add(new String[]{
                        String.valueOf(cur.getLong(cur.getColumnIndexOrThrow("reset_time"))),
                        cur.getString(cur.getColumnIndexOrThrow("reset_kind")),
                        cur.getString(cur.getColumnIndexOrThrow("item_name")),
                        String.valueOf(cur.getDouble(cur.getColumnIndexOrThrow("previous_amount"))),
                        String.valueOf(cur.getDouble(cur.getColumnIndexOrThrow("target_amount")))
                });
            }
        } finally { cur.close(); }
        return out;
    }

    private void recordReset(SQLiteDatabase db, long timestamp, String kind, String name,
                             double before, double target) {
        ContentValues v = new ContentValues();
        v.put("reset_time", timestamp);
        v.put("reset_kind", kind);
        v.put("item_name", name);
        v.put("previous_amount", before);
        v.put("target_amount", target);
        db.insertOrThrow(TABLE_RESET_HISTORY, null, v);
    }

    public synchronized void resetBudgetsPreservingHistory() {
        ensureMonthlyRollover();
        long now = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (BudgetEnvelope b : getBudgetsRaw()) {
                double remaining = b.getEffectiveAmount() - getSpentForCategoryThisMonth(b.getName());
                recordReset(db, now, "ENVELOPE", b.getName(), remaining, b.getEffectiveAmount());
            }
            ContentValues values = new ContentValues();
            values.put("carry_amount", 0d);
            db.update(TABLE_BUDGETS, values, null, null);
            ContentValues setting = new ContentValues();
            setting.put("key", "budget_reset_at");
            setting.put("value", String.valueOf(now));
            db.insertWithOnConflict(TABLE_SETTINGS, null, setting, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        signalLedgerChange();
    }

    public synchronized void resetGoalsPreservingHistory() {
        long now = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (SavingsGoal g : getSavingsGoals()) {
                recordReset(db, now, "GOAL", g.getName(), g.getSavedAmount(), g.getTargetAmount());
            }
            ContentValues values = new ContentValues();
            values.put("saved_amount", 0d);
            db.update(TABLE_SAVINGS_GOALS, values, null, null);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        signalLedgerChange();
    }

    public synchronized boolean moveBudget(long id, int direction) {
        List<BudgetEnvelope> ordered = getBudgetsRaw();
        int index = -1;
        for (int i = 0; i < ordered.size(); i++) if (ordered.get(i).getId() == id) index = i;
        int target = index + direction;
        if (index < 0 || target < 0 || target >= ordered.size()) return false;
        BudgetEnvelope temp = ordered.get(index);
        ordered.set(index, ordered.get(target));
        ordered.set(target, temp);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < ordered.size(); i++) {
                ContentValues v = new ContentValues();
                v.put("sort_order", i);
                db.update(TABLE_BUDGETS, v, "id=?", new String[]{String.valueOf(ordered.get(i).getId())});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        signalLedgerChange();
        return true;
    }

    private void createSavingsGoalsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_SAVINGS_GOALS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, "
                + "target_amount REAL NOT NULL, "
                + "saved_amount REAL NOT NULL DEFAULT 0"
                + ");");
    }

    /**
     * Dedicated goal savings are an allocation tracker, not a bank transfer.
     * Goals never insert spending transactions or silently alter bank balance.
     */
    public synchronized List<SavingsGoal> getSavingsGoals() {
        List<SavingsGoal> goals = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query(TABLE_SAVINGS_GOALS,
                new String[]{"id", "name", "target_amount", "saved_amount"},
                null, null, null, null, "id DESC");
        try {
            while (cursor.moveToNext()) {
                goals.add(new SavingsGoal(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getDouble(2),
                        cursor.getDouble(3)));
            }
        } finally {
            cursor.close();
        }
        return goals;
    }

    public synchronized long saveSavingsGoal(long id, String name, double target) {
        if (name == null || name.trim().isEmpty()
                || !Double.isFinite(target) || target <= 0) return -1;
        String cleanName = name.trim();
        ContentValues values = new ContentValues();
        values.put("name", cleanName);
        values.put("target_amount", target);
        SQLiteDatabase db = getWritableDatabase();
        if (id > 0) {
            int updated = db.update(TABLE_SAVINGS_GOALS, values, "id=?",
                    new String[]{Long.toString(id)});
            if (updated > 0) signalLedgerChange();
            return updated > 0 ? id : -1;
        }
        values.put("saved_amount", 0d);
        long inserted = db.insert(TABLE_SAVINGS_GOALS, null, values);
        if (inserted > 0) signalLedgerChange();
        return inserted;
    }

    public synchronized boolean changeSavingsGoalBalance(long id, double difference) {
        if (id <= 0 || !Double.isFinite(difference) || difference == 0) return false;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Cursor cursor = db.query(TABLE_SAVINGS_GOALS,
                    new String[]{"saved_amount"}, "id=?",
                    new String[]{Long.toString(id)}, null, null, null);
            double previous;
            try {
                if (!cursor.moveToFirst()) return false;
                previous = cursor.getDouble(0);
            } finally {
                cursor.close();
            }
            double next = previous + difference;
            if (!Double.isFinite(next) || next < -0.00001) return false;
            ContentValues values = new ContentValues();
            values.put("saved_amount", Math.max(0d, next));
            if (db.update(TABLE_SAVINGS_GOALS, values, "id=?",
                    new String[]{Long.toString(id)}) != 1) return false;
            db.setTransactionSuccessful();
            signalLedgerChange();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    public synchronized boolean deleteSavingsGoal(long id) {
        boolean deleted = getWritableDatabase().delete(TABLE_SAVINGS_GOALS, "id=?",
                new String[]{Long.toString(id)}) == 1;
        if (deleted) signalLedgerChange();
        return deleted;
    }

    public synchronized double getTotalSavedForGoals() {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(saved_amount), 0) FROM " + TABLE_SAVINGS_GOALS,
                null);
        try { return cursor.moveToFirst() ? cursor.getDouble(0) : 0d; }
        finally { cursor.close(); }
    }

    private void createMerchantRulesTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_MERCHANT_RULES + " ("
                + "bank_key TEXT NOT NULL, "
                + "merchant_key TEXT NOT NULL, "
                + "category TEXT NOT NULL, "
                + "PRIMARY KEY (bank_key, merchant_key)"
                + ");");
    }

    private static String normalizeKey(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim().replaceAll("\\s+", " ");
    }

    private static boolean isSpecificMerchant(String merchant) {
        String key = normalizeKey(merchant);
        return key.length() >= 3 && !key.equals("unknown merchant")
                && !key.equals("manual entry")
                && !key.equals("transfer")
                && !key.equals("incoming funds")
                && !key.equals("refund")
                && !key.equals("atm cash withdrawal")
                && !key.equals("merchant");
    }

    private static boolean isSpendingType(String type) {
        return "card_purchase".equals(type) || "pos".equals(type)
                || "online".equals(type) || "atm".equals(type)
                || "direct_debit".equals(type) || "fee".equals(type)
                || "other_expense".equals(type);
    }

    private static boolean canLearnCategory(String category) {
        return category != null && !category.trim().isEmpty()
                && !category.equalsIgnoreCase("Income")
                && !category.equalsIgnoreCase("Refunds")
                && !category.equalsIgnoreCase("Transfers");
    }

    public synchronized String getLearnedCategory(String bank, String merchant) {
        if (!isSpecificMerchant(merchant)) return null;
        String bankKey = normalizeKey(bank);
        if (bankKey.isEmpty()) return null;
        Cursor cursor = getReadableDatabase().query(
                TABLE_MERCHANT_RULES, new String[]{"category"},
                "bank_key=? AND merchant_key=?",
                new String[]{bankKey, normalizeKey(merchant)},
                null, null, null);
        try {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        } finally {
            cursor.close();
        }
    }

    public synchronized int getLearnedMerchantCount() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_MERCHANT_RULES, null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; }
        finally { c.close(); }
    }

    public synchronized void forgetLearnedMerchants() {
        getWritableDatabase().delete(TABLE_MERCHANT_RULES, null, null);
    }

    private void rememberMerchant(String bank, String merchant, String category) {
        if (!isSpecificMerchant(merchant) || !canLearnCategory(category)) return;
        String bankKey = normalizeKey(bank);
        if (bankKey.isEmpty()) return;
        ContentValues values = new ContentValues();
        values.put("bank_key", bankKey);
        values.put("merchant_key", normalizeKey(merchant));
        values.put("category", category);
        getWritableDatabase().insertWithOnConflict(
                TABLE_MERCHANT_RULES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Learns from deliberate edits, not from automatically guessed categories.
     * Existing transactions are left unchanged. Future captures at the same
     * normalized merchant and bank use the correction, regardless of amount.
     */
    public synchronized boolean updateTransactionAndLearn(
            long id, String category, String type, boolean ignored, boolean remember) {
        Cursor cursor = getReadableDatabase().query(
                TABLE_TRANSACTIONS, new String[]{COLUMN_BANK_SOURCE, COLUMN_MERCHANT},
                COLUMN_ID + "=?", new String[]{String.valueOf(id)},
                null, null, null);
        String bank;
        String merchant;
        try {
            if (!cursor.moveToFirst()) return false;
            bank = cursor.getString(0);
            merchant = cursor.getString(1);
        } finally {
            cursor.close();
        }

        if (!updateTransaction(id, category, type, ignored)) return false;
        if (remember && !ignored && isSpendingType(type)) {
            rememberMerchant(bank, merchant, category);
        }
        return true;
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
            cv.put("sort_order", -1);
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

        // User-taught merchant rules take precedence over the built-in
        // keyword-based category guesses, but never change income/transfers/refunds.
        if (isSpendingType(transaction.getTransactionType())) {
            String learnedCategory = getLearnedCategory(
                    transaction.getBankSource(), transaction.getMerchant());
            if (learnedCategory != null) transaction.setCategory(learnedCategory);
        }

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
        if (id > 0) signalLedgerChange();
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

    private long startOfMonth() { return getCurrentCycleStart(); }

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
        boolean changed = getWritableDatabase().update(
                TABLE_TRANSACTIONS, cv, COLUMN_ID + "=?", new String[]{String.valueOf(id)}
        ) > 0;
        if (changed) signalLedgerChange();
        return changed;
    }

    public synchronized boolean deleteTransaction(long id) {
        boolean changed = getWritableDatabase().delete(
                TABLE_TRANSACTIONS, COLUMN_ID + "=?", new String[]{String.valueOf(id)}
        ) > 0;
        if (changed) signalLedgerChange();
        return changed;
    }

    public synchronized void clearAll() {
        getWritableDatabase().delete(TABLE_TRANSACTIONS, null, null);
        signalLedgerChange();
    }

    private List<BudgetEnvelope> getBudgetsRaw() {
        List<BudgetEnvelope> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query(
                TABLE_BUDGETS, null, null, null, null, null, "sort_order ASC, id ASC"
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
            signalLedgerChange();
            return b.getId();
        }
        Cursor max = getReadableDatabase().rawQuery(
                "SELECT COALESCE(MAX(sort_order),0)+1 FROM " + TABLE_BUDGETS, null);
        try { if (max.moveToFirst()) cv.put("sort_order", max.getInt(0)); }
        finally { max.close(); }
        long id = getWritableDatabase().insertWithOnConflict(
                TABLE_BUDGETS, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        if (id > 0) signalLedgerChange();
        return id;
    }

    public synchronized boolean deleteBudget(long id) {
        boolean deleted = getWritableDatabase().delete(TABLE_BUDGETS, "id=?", new String[]{String.valueOf(id)}) > 0;
        if (deleted) signalLedgerChange();
        return deleted;
    }

    public synchronized double getSpentForCategoryThisMonth(String category) {
        long from = Math.max(getCurrentCycleStart(),
                (long) getSettingDouble("budget_reset_at", 0));
        return getSpentForCategoryBetween(category, from, System.currentTimeMillis());
    }

    private void ensureMonthlyRollover() {
        long current = getCurrentCycleStart();
        long last = (long) getSettingDouble("last_rollover_cycle_start", 0);
        if (last == 0) {
            setSetting("last_rollover_cycle_start", String.valueOf(current));
            return;
        }
        if (last >= current) return;

        long cursor = last;
        int safety = 0;
        while (cursor < current && ++safety < 1200) {
            long periodStart = cursor;
            long periodEnd = getCycleEnd(periodStart);
            if (periodEnd <= periodStart) break;
            cursor = periodEnd;
            for (BudgetEnvelope b : getBudgetsRaw()) {
                double carry = 0d;
                if (b.isRolloverEnabled() || b.isSinkingFund()) {
                    long resetAt = (long) getSettingDouble("budget_reset_at", 0);
                    long from = Math.max(periodStart, resetAt);
                    double spent = from >= periodEnd ? 0 :
                            getSpentForCategoryBetween(b.getName(), from, periodEnd - 1);
                    carry = Math.max(0d, b.getMonthlyAmount() + b.getCarryAmount() - spent);
                }
                ContentValues values = new ContentValues();
                values.put("carry_amount", carry);
                getWritableDatabase().update(TABLE_BUDGETS, values, "id=?",
                        new String[]{String.valueOf(b.getId())});
            }
            setSetting("last_rollover_cycle_start", String.valueOf(periodEnd));
        }
        long resetAt = (long) getSettingDouble("budget_reset_at", 0);
        if (resetAt > 0 && resetAt < current) setSetting("budget_reset_at", "0");
        signalLedgerChange();
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
            signalLedgerChange();
            return bill.getId();
        }
        long id = getWritableDatabase().insert(TABLE_RECURRING, null, cv);
        if (id > 0) signalLedgerChange();
        return id;
    }

    public synchronized boolean deleteRecurringBill(long id) {
        boolean changed = getWritableDatabase().delete(TABLE_RECURRING, "id=?", new String[]{String.valueOf(id)}) > 0;
        if (changed) signalLedgerChange();
        return changed;
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
        signalLedgerChange();
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
        long start = getCurrentCycleStart();
        long end = getCurrentCycleEnd();
        int day = (int) Math.max(1, (System.currentTimeMillis() - start) / 86400000L + 1);
        int daysInMonth = (int) Math.max(1, (end - start) / 86400000L);
        double spent = getTotalSpentAedThisMonth();
        double averageDaily = spent / day;
        int remainingDays = Math.max(0, daysInMonth - day);
        return spent + (averageDaily * remainingDays) + getUpcomingRecurringAedThisMonth();
    }

    public synchronized String getTopBudgetPaceInsight() {
        List<BudgetEnvelope> budgets = getBudgets();
        Calendar now = Calendar.getInstance();
        double monthProgress = Math.min(1d, (double) (System.currentTimeMillis() - getCurrentCycleStart())
                / Math.max(1d, (getCurrentCycleEnd() - getCurrentCycleStart())));
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
