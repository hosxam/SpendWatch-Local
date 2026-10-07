package com.hossam.spendwatch;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class SpendDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "spendwatch_uae.db";
    private static final int DATABASE_VERSION = 2;

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

    private static SpendDatabase instance;

    public static synchronized SpendDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new SpendDatabase(context.getApplicationContext());
        }
        return instance;
    }

    public SpendDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTableQuery = "CREATE TABLE " + TABLE_TRANSACTIONS + " ("
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
                + ");";
        db.execSQL(createTableQuery);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRANSACTIONS);
        onCreate(db);
    }

    // Duplicate detection within 10-minute window
    public synchronized boolean isDuplicate(Transaction t) {
        SQLiteDatabase db = this.getReadableDatabase();
        long window = 10 * 60 * 1000;
        long start = t.getTimestamp() - window;
        long end = t.getTimestamp() + window;

        String query = "SELECT " + COLUMN_ID + " FROM " + TABLE_TRANSACTIONS
                + " WHERE " + COLUMN_BANK_SOURCE + " = ? AND " + COLUMN_AMOUNT + " = ? AND " + COLUMN_CURRENCY + " = ?"
                + " AND " + COLUMN_TIMESTAMP + " >= ? AND " + COLUMN_TIMESTAMP + " <= ?";
        Cursor cursor = db.rawQuery(query, new String[]{
                t.getBankSource(), String.valueOf(t.getAmount()), t.getCurrency(),
                String.valueOf(start), String.valueOf(end)
        });

        boolean duplicate = false;
        if (cursor != null) {
            duplicate = cursor.getCount() > 0;
            cursor.close();
        }
        return duplicate;
    }

    public synchronized long insertTransaction(Transaction transaction) {
        SQLiteDatabase db = this.getWritableDatabase();
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
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_TRANSACTIONS, null, null, null, null, null, COLUMN_TIMESTAMP + " DESC");

        if (cursor != null) {
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
                    Transaction t = new Transaction(
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
                    );
                    list.add(t);
                }
            } finally {
                cursor.close();
            }
        }
        return list;
    }

    public synchronized double getTotalSpentAedBetween(long startTimestamp, long endTimestamp) {
        SQLiteDatabase db = this.getReadableDatabase();
        double total = 0.0;
        String query = "SELECT SUM(" + COLUMN_AMOUNT + ") FROM " + TABLE_TRANSACTIONS
                + " WHERE " + COLUMN_CURRENCY + " = 'AED' AND " + COLUMN_IS_IGNORED + " = 0"
                + " AND " + COLUMN_TIMESTAMP + " >= ? AND " + COLUMN_TIMESTAMP + " <= ?";
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(startTimestamp), String.valueOf(endTimestamp)});
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) total = cursor.getDouble(0);
            } finally {
                cursor.close();
            }
        }
        return total;
    }

    public synchronized double getTotalSpentAedThisMonth() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long start = cal.getTimeInMillis();
        long now = System.currentTimeMillis();
        return getTotalSpentAedBetween(start, now);
    }

    public synchronized double getTotalSpentAedToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long start = cal.getTimeInMillis();
        long now = System.currentTimeMillis();
        return getTotalSpentAedBetween(start, now);
    }

    public synchronized double getTotalSpentAedThisWeek() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long start = cal.getTimeInMillis();
        long now = System.currentTimeMillis();
        return getTotalSpentAedBetween(start, now);
    }

    public synchronized double getTotalSpentAedAllTime() {
        SQLiteDatabase db = this.getReadableDatabase();
        double total = 0.0;
        String query = "SELECT SUM(" + COLUMN_AMOUNT + ") FROM " + TABLE_TRANSACTIONS
                + " WHERE " + COLUMN_CURRENCY + " = 'AED' AND " + COLUMN_IS_IGNORED + " = 0";
        Cursor cursor = db.rawQuery(query, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) total = cursor.getDouble(0);
            } finally {
                cursor.close();
            }
        }
        return total;
    }

    public synchronized boolean updateCategory(long id, String newCategory) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_CATEGORY, newCategory);
        return db.update(TABLE_TRANSACTIONS, cv, COLUMN_ID + " = ?", new String[]{String.valueOf(id)}) > 0;
    }

    public synchronized boolean toggleIgnore(long id, boolean isIgnored) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_IS_IGNORED, isIgnored ? 1 : 0);
        return db.update(TABLE_TRANSACTIONS, cv, COLUMN_ID + " = ?", new String[]{String.valueOf(id)}) > 0;
    }

    public synchronized boolean deleteTransaction(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_TRANSACTIONS, COLUMN_ID + " = ?", new String[]{String.valueOf(id)}) > 0;
    }

    public synchronized void clearAll() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_TRANSACTIONS, null, null);
    }
}
