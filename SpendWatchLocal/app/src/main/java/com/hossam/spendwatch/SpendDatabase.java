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
    private static final int DATABASE_VERSION = 8;

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
    private static final String TABLE_CYCLE_ARCHIVE = "cycle_archive";
    private static final String TABLE_COMMITMENTS = "one_time_commitments";
    // Explicit user corrections only, separate from automatically guessed categories.
    private static final String TABLE_MERCHANT_RULES = "merchant_category_rules";

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
        createMerchantRulesTable(db);
        createSavingsGoalsTable(db);
        createHistoryTables(db);
        createCommitmentsTable(db);
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
        // Only adds new structures; existing users retain transactions and budgets.
        createTransactionsTable(db);
        createV2Tables(db);
        createMerchantRulesTable(db);
        createSavingsGoalsTable(db);
        createHistoryTables(db);
        createCommitmentsTable(db);
        seedDefaultBudgets(db);
    }

    private void createHistoryTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_CYCLE_ARCHIVE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "created_at INTEGER NOT NULL, "
                + "kind TEXT NOT NULL, "
                + "name TEXT NOT NULL, "
                + "period_start INTEGER NOT NULL, "
                + "period_end INTEGER NOT NULL, "
                + "allocated REAL NOT NULL, "
                + "spent_or_saved REAL NOT NULL, "
                + "remaining REAL NOT NULL, "
                + "details TEXT"
                + ")");
        try {
            db.execSQL("ALTER TABLE budgets ADD COLUMN progress_mode INTEGER NOT NULL DEFAULT 0");
        } catch (android.database.SQLException alreadyExists) {
            // Existing field.
        }
        try {
            db.execSQL("ALTER TABLE budgets ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 9999");
        } catch (android.database.SQLException alreadyExists) {
            // Column already exists in an updated installation.
        }
    }

    private void createCommitmentsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_COMMITMENTS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "title TEXT NOT NULL, "
                + "amount REAL NOT NULL, "
                + "due_at INTEGER NOT NULL, "
                + "remind_days INTEGER NOT NULL DEFAULT 0, "
                + "status TEXT NOT NULL DEFAULT 'pending', "
                + "created_at INTEGER NOT NULL"
                + ")");
    }

    public synchronized long saveOneTimeCommitment(long id, String title,
            double amount, long dueAt, int remindDays) {
        if (title == null || title.trim().isEmpty() || !Double.isFinite(amount)
                || amount <= 0 || dueAt <= 0 || remindDays < -3 || remindDays > 365)
            return -1;
        android.content.ContentValues values = new android.content.ContentValues();
        values.put("title", title.trim());
        values.put("amount", amount);
        values.put("due_at", dueAt);
        values.put("remind_days", remindDays);
        SQLiteDatabase writable = getWritableDatabase();
        if (id > 0) {
            return writable.update(TABLE_COMMITMENTS, values,
                    "id=? AND status='pending'", new String[]{Long.toString(id)}) == 1 ? id : -1;
        }
        values.put("status", "pending");
        values.put("created_at", System.currentTimeMillis());
        return writable.insert(TABLE_COMMITMENTS, null, values);
    }

    public synchronized boolean setOneTimeCommitmentStatus(long id, String status) {
        if (!"paid".equals(status) && !"pending".equals(status)
                && !"cancelled".equals(status)) return false;
        android.content.ContentValues values = new android.content.ContentValues();
        values.put("status", status);
        return getWritableDatabase().update(TABLE_COMMITMENTS, values, "id=?",
                new String[]{Long.toString(id)}) == 1;
    }

    public synchronized boolean deleteOneTimeCommitment(long id) {
        return getWritableDatabase().delete(TABLE_COMMITMENTS, "id=?",
                new String[]{Long.toString(id)}) == 1;
    }

    public synchronized List<OneTimeCommitment> getOneTimeCommitments() {
        List<OneTimeCommitment> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,title,amount,due_at,remind_days,status "
                + "FROM " + TABLE_COMMITMENTS
                + " ORDER BY CASE status WHEN 'pending' THEN 0 ELSE 1 END,"
                + " due_at ASC, id ASC", null)) {
            while (c.moveToNext()) {
                result.add(new OneTimeCommitment(c.getLong(0), c.getString(1),
                        c.getDouble(2), c.getLong(3), c.getInt(4), c.getString(5)));
            }
        }
        return result;
    }

    public synchronized OneTimeCommitment getOneTimeCommitment(long id) {
        for (OneTimeCommitment item : getOneTimeCommitments())
            if (item.getId() == id) return item;
        return null;
    }

    /** AED reservations for upcoming one-off bills; not actual bank balance. */
    public synchronized double getUpcomingOneTimeCommitmentsTotal() {
        double total = 0;
        for (OneTimeCommitment c : getOneTimeCommitments()) {
            if ("pending".equals(c.getStatus())) total += c.getAmount();
        }
        return total;
    }

    public synchronized List<String> getHistoricalTotals(boolean daily, int limit) {
        List<String> totals = new ArrayList<>();
        String date = daily ? "%Y-%m-%d" : "%Y-%m";
        String sql = "SELECT strftime('" + date + "',timestamp/1000,'unixepoch','localtime') d,"
                + " COUNT(*), COALESCE(SUM(amount),0)"
                + " FROM " + TABLE_TRANSACTIONS
                + " WHERE currency='AED' AND is_ignored=0 AND transaction_type IN "
                + spendingTypesSql() + " GROUP BY d ORDER BY d DESC LIMIT ?";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql,
                new String[]{Integer.toString(Math.max(1, Math.min(3660, limit)))})) {
            while (cursor.moveToNext()) {
                totals.add(String.format(Locale.US,"%s  •  AED %,.2f  •  %d transactions",
                        cursor.getString(0), cursor.getDouble(2), cursor.getInt(1)));
            }
        }
        return totals;
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
            return updated > 0 ? id : -1;
        }
        values.put("saved_amount", 0d);
        return db.insert(TABLE_SAVINGS_GOALS, null, values);
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
            return true;
        } finally {
            db.endTransaction();
        }
    }

    public synchronized boolean deleteSavingsGoal(long id) {
        return getWritableDatabase().delete(TABLE_SAVINGS_GOALS, "id=?",
                new String[]{Long.toString(id)}) == 1;
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
        return getTotalSpentAedBetween(getEffectiveBudgetStartTimestamp(), System.currentTimeMillis());
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
                        c.getInt(c.getColumnIndexOrThrow("is_sinking")) == 1,
                        c.getInt(c.getColumnIndexOrThrow("progress_mode")) == 1
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
        cv.put("progress_mode", b.isShowSpentProgress() ? 1 : 0);
        if (b.getId() > 0) {
            getWritableDatabase().update(TABLE_BUDGETS, cv, "id=?", new String[]{String.valueOf(b.getId())});
            return b.getId();
        }
        return getWritableDatabase().insertWithOnConflict(TABLE_BUDGETS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized boolean moveBudget(long id, int offset) {
        List<BudgetEnvelope> ordered = getBudgetsRaw();
        int from = -1;
        for (int i = 0; i < ordered.size(); i++) if (ordered.get(i).getId() == id) from = i;
        if (from < 0 || from + offset < 0 || from + offset >= ordered.size()) return false;
        java.util.Collections.swap(ordered, from, from + offset);
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            for (int i = 0; i < ordered.size(); i++) {
                ContentValues v = new ContentValues();
                v.put("sort_order", i);
                database.update(TABLE_BUDGETS, v, "id=?",
                        new String[]{Long.toString(ordered.get(i).getId())});
            }
            database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
        return true;
    }

    public synchronized double getTotalEnvelopeAvailable() {
        double amount = 0;
        for (BudgetEnvelope budget : getBudgets()) {
            amount += budget.getEffectiveAmount()
                    - getSpentForCategoryThisMonth(budget.getName());
        }
        return amount;
    }

    public synchronized double getTotalEnvelopeSpent() {
        double spent = 0;
        for (BudgetEnvelope b : getBudgets()) {
            spent += getSpentForCategoryThisMonth(b.getName());
        }
        return spent;
    }

    /** 1-31; shorter months clamp to their last calendar day. */
    public synchronized int getCycleStartDay() {
        return Math.max(1, Math.min(31, (int) getSettingDouble("cycle_start_day", 1)));
    }

    public synchronized void setCycleStartDay(int day) {
        if (day < 1 || day > 31) throw new IllegalArgumentException("Day must be 1-31");
        if (day == getCycleStartDay()) return;
        // Keep old rollovers from being reapplied under the new cycle definition.
        setSetting("cycle_start_day", Integer.toString(day));
        setSetting("last_rollover_cycle", currentCycleId());
    }

    private Calendar cycleStartFor(Calendar now, int day) {
        Calendar c = (Calendar) now.clone();
        c.set(Calendar.DAY_OF_MONTH, Math.min(day, c.getActualMaximum(Calendar.DAY_OF_MONTH)));
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        if (c.after(now)) {
            c.add(Calendar.MONTH, -1);
            c.set(Calendar.DAY_OF_MONTH, Math.min(day, c.getActualMaximum(Calendar.DAY_OF_MONTH)));
        }
        return c;
    }

    public synchronized long getCycleStartTimestamp() {
        return cycleStartFor(Calendar.getInstance(), getCycleStartDay()).getTimeInMillis();
    }

    public synchronized long getEffectiveBudgetStartTimestamp() {
        return Math.max(getCycleStartTimestamp(), (long) getSettingDouble("budget_reset_at", 0d));
    }

    private synchronized String currentCycleId() {
        Calendar c = cycleStartFor(Calendar.getInstance(), getCycleStartDay());
        return String.format(Locale.US, "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
    }

    /** Snapshot the current envelope balances; retain all historical transactions. */
    public synchronized void softResetEnvelopes() {
        long now = System.currentTimeMillis();
        long start = getEffectiveBudgetStartTimestamp();
        List<BudgetEnvelope> budgets = getBudgets();
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            for (BudgetEnvelope b : budgets) {
                double spent = getSpentForCategoryBetween(b.getName(), start, now);
                double allocated = b.getEffectiveAmount();
                insertArchive(database, now, "envelope", b.getName(), start, now,
                        allocated, spent, allocated - spent, b.isSinkingFund() ? "sinking" : "budget");
                ContentValues change = new ContentValues();
                change.put("carry_amount", 0d);
                database.update(TABLE_BUDGETS, change, "id=?",
                        new String[]{Long.toString(b.getId())});
            }
            ContentValues reset = new ContentValues();
            reset.put("key", "budget_reset_at");
            reset.put("value", Long.toString(now));
            database.insertWithOnConflict(TABLE_SETTINGS, null, reset, SQLiteDatabase.CONFLICT_REPLACE);
            database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
    }

    /** Snapshot achieved goal progress without deleting goals or transactions. */
    public synchronized void softResetGoals() {
        long now = System.currentTimeMillis();
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            for (SavingsGoal goal : getSavingsGoals()) {
                insertArchive(database, now, "goal", goal.getName(), 0, now,
                        goal.getTargetAmount(), goal.getSavedAmount(),
                        goal.getRemaining(), "soft reset");
                ContentValues values = new ContentValues();
                values.put("saved_amount", 0d);
                database.update(TABLE_SAVINGS_GOALS, values, "id=?",
                        new String[]{Long.toString(goal.getId())});
            }
            database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
    }

    private void insertArchive(SQLiteDatabase database, long created, String kind,
            String name, long start, long end, double allocated, double spentOrSaved,
            double remaining, String details) {
        ContentValues values = new ContentValues();
        values.put("created_at", created);
        values.put("kind", kind);
        values.put("name", name);
        values.put("period_start", start);
        values.put("period_end", end);
        values.put("allocated", allocated);
        values.put("spent_or_saved", spentOrSaved);
        values.put("remaining", remaining);
        values.put("details", details);
        database.insertOrThrow(TABLE_CYCLE_ARCHIVE, null, values);
    }

    public synchronized boolean deleteBudget(long id) {
        return getWritableDatabase().delete(TABLE_BUDGETS, "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public synchronized double getSpentForCategoryThisMonth(String category) {
        return getSpentForCategoryBetween(category, getEffectiveBudgetStartTimestamp(), System.currentTimeMillis());
    }

    private void ensureMonthlyRollover() {
        Calendar current = cycleStartFor(Calendar.getInstance(), getCycleStartDay());
        String currentId = currentCycleId();
        String previousId = getSetting("last_rollover_cycle", "");
        if (previousId.isEmpty()) {
            // Preserve the last known rollover marker from v4.4 installations.
            String legacy = getSetting("last_rollover_month", "");
            if (legacy.matches("[0-9]{6}")) {
                previousId = legacy.substring(0, 4) + "-" + legacy.substring(4);
            } else {
                previousId = currentId;
            }
            setSetting("last_rollover_cycle", previousId);
        }
        if (previousId.equals(currentId)) return;
        Calendar prev = Calendar.getInstance();
        try {
            String[] parts = previousId.split("-");
            prev.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, 1, 0, 0, 0);
            prev.set(Calendar.DAY_OF_MONTH, Math.min(getCycleStartDay(), prev.getActualMaximum(Calendar.DAY_OF_MONTH)));
            prev.set(Calendar.MILLISECOND, 0);
        } catch (Exception e) {
            setSetting("last_rollover_cycle", currentId);
            return;
        }
        // Replay each completed cycle once; historical transactions are never deleted.
        int guard = 0;
        while (prev.before(current) && guard++ < 120) {
            Calendar next = (Calendar) prev.clone();
            next.set(Calendar.DAY_OF_MONTH, 1);
            next.add(Calendar.MONTH, 1);
            next.set(Calendar.DAY_OF_MONTH, Math.min(getCycleStartDay(), next.getActualMaximum(Calendar.DAY_OF_MONTH)));
            long start = Math.max(prev.getTimeInMillis(),
                    (long) getSettingDouble("budget_reset_at", 0));
            long end = next.getTimeInMillis() - 1;
            SQLiteDatabase writable = getWritableDatabase();
            writable.beginTransaction();
            try {
                for (BudgetEnvelope budget : getBudgetsRaw()) {
                    double spent = getSpentForCategoryBetween(budget.getName(), start, end);
                    double allocated = budget.getEffectiveAmount();
                    insertArchive(writable, System.currentTimeMillis(), "envelope_cycle",
                            budget.getName(), start, end, allocated, spent,
                            allocated - spent, "automatic cycle close");
                    ContentValues v = new ContentValues();
                    v.put("carry_amount",
                            budget.isRolloverEnabled() || budget.isSinkingFund()
                                    ? Math.max(0, allocated - spent) : 0);
                    writable.update(TABLE_BUDGETS, v, "id=?",
                            new String[]{Long.toString(budget.getId())});
                }
                ContentValues checkpoint = new ContentValues();
                checkpoint.put("key", "last_rollover_cycle");
                checkpoint.put("value", String.format(Locale.US, "%04d-%02d",
                        next.get(Calendar.YEAR), next.get(Calendar.MONTH) + 1));
                writable.insertWithOnConflict(TABLE_SETTINGS, null, checkpoint,
                        SQLiteDatabase.CONFLICT_REPLACE);
                writable.setTransactionSuccessful();
            } finally { writable.endTransaction(); }
            prev = next;
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
