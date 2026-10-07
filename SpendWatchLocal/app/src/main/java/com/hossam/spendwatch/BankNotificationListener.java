package com.hossam.spendwatch;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class BankNotificationListener extends NotificationListenerService {

    private static final String TAG = "SpendWatch_Listener";
    public static final String ACTION_TRANSACTION_RECORDED = "com.hossam.spendwatch.TRANSACTION_RECORDED";

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "SpendWatch Notification Listener Connected. Listening for bank alerts locally.");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.d(TAG, "SpendWatch Notification Listener Disconnected.");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        // Ignore our own app notifications to prevent any loops
        if (getPackageName().equals(sbn.getPackageName())) {
            return;
        }

        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) {
            return;
        }

        Bundle extras = notification.extras;
        CharSequence titleSeq = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textSeq = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bigTextSeq = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);

        String title = titleSeq != null ? titleSeq.toString() : "";
        String body = bigTextSeq != null ? bigTextSeq.toString() : (textSeq != null ? textSeq.toString() : "");
        String packageName = sbn.getPackageName();

        // Pass to parser to detect if this is an outgoing transaction
        Transaction transaction = BankTransactionParser.parseNotification(title, body, packageName);

        if (transaction != null) {
            // Set exact notification post time if available
            long postTime = sbn.getPostTime();
            if (postTime > 0) {
                transaction.setTimestamp(postTime);
            }

            // Save strictly to local SQLite database
            long insertedId = SpendDatabase.getInstance(this).insertTransaction(transaction);
            Log.d(TAG, "Detected outgoing transaction #" + insertedId + " (" + transaction.getCurrency() + " " + transaction.getAmount() + " at " + transaction.getMerchant() + ")");

            // Notify UI to refresh
            Intent broadcast = new Intent(ACTION_TRANSACTION_RECORDED);
            broadcast.putExtra("transaction_id", insertedId);
            sendBroadcast(broadcast);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // No action needed when notification is dismissed
    }
}
