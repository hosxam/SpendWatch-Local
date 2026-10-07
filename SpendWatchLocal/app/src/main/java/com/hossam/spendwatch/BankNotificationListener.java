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
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;

        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) return;

        Bundle extras = notification.extras;
        CharSequence titleSeq = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textSeq = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bigTextSeq = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);

        String title = titleSeq == null ? "" : titleSeq.toString();
        String body = bigTextSeq != null ? bigTextSeq.toString() : (textSeq == null ? "" : textSeq.toString());

        Transaction transaction = BankTransactionParser.parseNotification(title, body, sbn.getPackageName());
        if (transaction == null) return;

        if (sbn.getPostTime() > 0) transaction.setTimestamp(sbn.getPostTime());

        long insertedId = SpendDatabase.getInstance(this).insertTransaction(transaction);
        if (insertedId <= 0) {
            Log.d(TAG, "Duplicate or rejected transaction ignored.");
            return;
        }

        Intent broadcast = new Intent(ACTION_TRANSACTION_RECORDED);
        broadcast.setPackage(getPackageName());
        broadcast.putExtra("transaction_id", insertedId);
        sendBroadcast(broadcast);
    }
}
