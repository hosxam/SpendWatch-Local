import { useState } from 'react';
import { Code, Download, FileCode, Check, Copy, Shield } from 'lucide-react';
import JSZip from 'jszip';

interface SourceFile {
  name: string;
  path: string;
  language: string;
  content: string;
}

const ANDROID_FILES: SourceFile[] = [
  {
    name: 'AndroidManifest.xml',
    path: 'app/src/main/AndroidManifest.xml',
    language: 'xml',
    content: `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.hossam.spendwatch">

    <!-- STRICT LOCAL PRIVACY: Zero INTERNET permission requested -->

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.SpendWatch">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Android Notification Access Service for UAE Bank SMS & Push Alerts -->
        <service
            android:name=".BankNotificationListener"
            android:label="@string/notification_listener_label"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>

    </application>

</manifest>`,
  },
  {
    name: 'BankTransactionParser.java',
    path: 'app/src/main/java/com/hossam/spendwatch/BankTransactionParser.java',
    language: 'java',
    content: `package com.hossam.spendwatch;

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
            "(?:^|\\\\s)(AED|DHS|DH|DIRHAMS?|USD|EUR|GBP|SAR|QAR|KWD|OMR|BHD|INR|CAD|AUD)\\\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\\\.[0-9]{1,2})?|[0-9]+(?:\\\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_AMOUNT_FIRST = Pattern.compile(
            "(?:^|\\\\s)([0-9]{1,3}(?:,[0-9]{3})*(?:\\\\.[0-9]{1,2})?|[0-9]+(?:\\\\.[0-9]{1,2})?)\\\\s*(AED|DHS|DH|DIRHAMS?|USD|EUR|GBP|SAR|QAR|KWD|OMR|BHD|INR|CAD|AUD)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_MERCHANT = Pattern.compile(
            "(?:at|@|to|towards|for|merchant|payee)\\\\s+([A-Za-z0-9&'._\\\\-\\\\s/]{2,35}?)(?:\\\\s+(?:on|with|using|via|ending|ref|avail|bal|available|card|account|approx|on card|from|dt|date|AED|\\\\.|$)|\\n|\\\\.|$)",
            Pattern.CASE_INSENSITIVE
    );

    public static Transaction parseNotification(String title, String body, String packageName) {
        String combined = (title != null ? title + " " : "") + (body != null ? body : "");
        if (combined.trim().isEmpty()) return null;

        String lower = combined.toLowerCase(Locale.ROOT);

        // 1. Strict rejection checks
        for (String rej : REJECTION_KEYWORDS) {
            if (lower.contains(rej)) {
                // If it contains "credited" without "debited", reject
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
}`,
  },
  {
    name: 'BankNotificationListener.java',
    path: 'app/src/main/java/com/hossam/spendwatch/BankNotificationListener.java',
    language: 'java',
    content: `package com.hossam.spendwatch;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class BankNotificationListener extends NotificationListenerService {

    private static final String TAG = "SpendWatch_UAE";
    public static final String ACTION_TRANSACTION_RECORDED = "com.hossam.spendwatch.TRANSACTION_RECORDED";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        if (getPackageName().equals(sbn.getPackageName())) return;

        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) return;

        Bundle extras = notification.extras;
        CharSequence titleSeq = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textSeq = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bigTextSeq = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);

        String title = titleSeq != null ? titleSeq.toString() : "";
        String body = bigTextSeq != null ? bigTextSeq.toString() : (textSeq != null ? textSeq.toString() : "");
        String packageName = sbn.getPackageName();

        // Parse notification specifically for Ruya & UAE banking alerts
        Transaction transaction = BankTransactionParser.parseNotification(title, body, packageName);

        if (transaction != null) {
            long postTime = sbn.getPostTime();
            if (postTime > 0) {
                transaction.setTimestamp(postTime);
            }

            // Save strictly to local SQLite database with duplicate detection
            SpendDatabase db = SpendDatabase.getInstance(this);
            if (!db.isDuplicate(transaction)) {
                long id = db.insertTransaction(transaction);
                Log.d(TAG, "Recorded UAE expense #" + id + ": " + transaction.getCurrency() + " " + transaction.getAmount() + " at " + transaction.getMerchant());

                Intent broadcast = new Intent(ACTION_TRANSACTION_RECORDED);
                broadcast.putExtra("transaction_id", id);
                sendBroadcast(broadcast);
            }
        }
    }
}`,
  },
  {
    name: 'SpendDatabase.java',
    path: 'app/src/main/java/com/hossam/spendwatch/SpendDatabase.java',
    language: 'java',
    content: `package com.hossam.spendwatch;

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
        db.execSQL("CREATE TABLE " + TABLE_TRANSACTIONS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "amount REAL NOT NULL, "
                + "currency TEXT NOT NULL, "
                + "merchant TEXT NOT NULL, "
                + "timestamp INTEGER NOT NULL, "
                + "bank_source TEXT, "
                + "raw_message TEXT, "
                + "category TEXT, "
                + "transaction_type TEXT, "
                + "is_ignored INTEGER DEFAULT 0);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRANSACTIONS);
        onCreate(db);
    }

    public synchronized boolean isDuplicate(Transaction t) {
        SQLiteDatabase db = this.getReadableDatabase();
        long window = 10 * 60 * 1000; // 10 minutes
        Cursor c = db.query(TABLE_TRANSACTIONS, null,
                "bank_source = ? AND amount = ? AND currency = ? AND timestamp >= ? AND timestamp <= ?",
                new String[]{t.getBankSource(), String.valueOf(t.getAmount()), t.getCurrency(),
                        String.valueOf(t.getTimestamp() - window), String.valueOf(t.getTimestamp() + window)},
                null, null, null);
        boolean duplicate = false;
        if (c != null) {
            duplicate = c.getCount() > 0;
            c.close();
        }
        return duplicate;
    }

    public synchronized long insertTransaction(Transaction t) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("amount", t.getAmount());
        v.put("currency", t.getCurrency());
        v.put("merchant", t.getMerchant());
        v.put("timestamp", t.getTimestamp());
        v.put("bank_source", t.getBankSource());
        v.put("raw_message", t.getRawMessage());
        v.put("category", t.getCategory());
        v.put("transaction_type", t.getTransactionType());
        v.put("is_ignored", 0);
        return db.insert(TABLE_TRANSACTIONS, null, v);
    }

    public synchronized double getTotalSpentThisMonthAed() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long startOfMonth = cal.getTimeInMillis();

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT SUM(amount) FROM " + TABLE_TRANSACTIONS + " WHERE currency = 'AED' AND is_ignored = 0 AND timestamp >= ?",
                new String[]{String.valueOf(startOfMonth)});
        double total = 0.0;
        if (c != null) {
            try {
                if (c.moveToFirst()) total = c.getDouble(0);
            } finally {
                c.close();
            }
        }
        return total;
    }
}`,
  },
];

export function AndroidSourceCodeModal() {
  const [selectedFile, setSelectedFile] = useState<SourceFile>(ANDROID_FILES[1]); // Default to BankTransactionParser.java
  const [copied, setCopied] = useState(false);
  const [downloading, setDownloading] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(selectedFile.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownloadZip = async () => {
    setDownloading(true);
    try {
      const zip = new JSZip();
      const folder = zip.folder('SpendWatchLocal');

      folder?.file(
        'README.md',
        '# SpendWatch Local (UAE)\n100% on-device spending tracker using Android Notification Access.\nPrimary bank: Ruya. Secondary UAE banks: Emirates NBD, ADCB, FAB, Mashreq, etc.\n'
      );
      folder?.file('settings.gradle.kts', 'rootProject.name = "SpendWatchLocal"\ninclude(":app")\n');
      folder?.file('build.gradle.kts', 'plugins {\n    id("com.android.application") version "8.3.2" apply false\n}\n');
      folder?.file('gradle.properties', 'android.useAndroidX=true\nandroid.nonTransitiveRClass=true\n');

      ANDROID_FILES.forEach((f) => {
        folder?.file(f.path, f.content);
      });

      const content = await zip.generateAsync({ type: 'blob' });
      const url = URL.createObjectURL(content);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'SpendWatchLocal_UAE_Ruya.zip';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (e) {
      console.error('Failed to create zip', e);
    } finally {
      setDownloading(false);
    }
  };

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-lg space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-slate-100 flex items-center gap-2">
            <Code className="w-4 h-4 text-emerald-400" />
            <span>Android Project Sources (UAE / Ruya)</span>
          </h3>
          <p className="text-xs text-slate-400">
            Native implementation: NotificationListenerService + SQLite + Zero Internet Permission
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={handleCopy}
            className="flex items-center gap-1.5 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs rounded-xl font-medium transition-colors"
          >
            {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
            <span>{copied ? 'Copied' : 'Copy File'}</span>
          </button>
          <button
            onClick={handleDownloadZip}
            disabled={downloading}
            className="flex items-center gap-1.5 px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white text-xs rounded-xl font-semibold shadow-md transition-colors"
          >
            <Download className="w-3.5 h-3.5" />
            <span>{downloading ? 'Zipping...' : 'Download Android Zip'}</span>
          </button>
        </div>
      </div>

      {/* File Selector Tabs */}
      <div className="flex flex-wrap gap-1.5">
        {ANDROID_FILES.map((file) => (
          <button
            key={file.name}
            onClick={() => setSelectedFile(file)}
            className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-mono transition-colors ${
              selectedFile.name === file.name
                ? 'bg-emerald-950/80 text-emerald-400 border border-emerald-500/50'
                : 'bg-slate-950 text-slate-400 hover:text-slate-200 border border-slate-800'
            }`}
          >
            <FileCode className="w-3.5 h-3.5" />
            <span>{file.name}</span>
          </button>
        ))}
      </div>

      {/* Code Viewer */}
      <div className="relative rounded-xl overflow-hidden border border-slate-800 bg-slate-950">
        <div className="bg-slate-900/90 px-3 py-1.5 border-b border-slate-800 flex items-center justify-between text-[11px] text-slate-400 font-mono">
          <span>{selectedFile.path}</span>
          <span className="uppercase">{selectedFile.language}</span>
        </div>
        <pre className="p-4 text-xs font-mono text-slate-300 overflow-x-auto max-h-80 leading-relaxed">
          <code>{selectedFile.content}</code>
        </pre>
      </div>
    </div>
  );
}
