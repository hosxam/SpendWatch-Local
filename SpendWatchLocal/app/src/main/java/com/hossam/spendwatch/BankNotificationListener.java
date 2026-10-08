package com.hossam.spendwatch;

import android.app.Notification;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;


public class BankNotificationListener extends NotificationListenerService {

    private static final String TAG = "SpendWatch_Listener";
    public static final String ACTION_TRANSACTION_RECORDED = "com.hossam.spendwatch.TRANSACTION_RECORDED";

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        NotificationHelper.ensureChannels(this);
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification sbn : active) {
                    processNotification(sbn);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not scan active notifications", e);
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        processNotification(sbn);
    }

    private void processNotification(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;

        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) return;

        String title = NotificationTextExtractor.title(notification);
        String body = NotificationTextExtractor.body(notification);

        Transaction transaction = BankTransactionParser.parseNotification(title, body, sbn.getPackageName());
        if (transaction == null) return;

        if (sbn.getPostTime() > 0) transaction.setTimestamp(sbn.getPostTime());

        long insertedId = SpendDatabase.getInstance(this).insertTransaction(transaction);
        if (insertedId <= 0) {
            Log.d(TAG, "Duplicate or already-recorded transaction ignored.");
            return;
        }

        NotificationHelper.notifyTransactionRecorded(this, transaction);
        SpendWatchWidget.updateAll(this);

        Intent broadcast = new Intent(ACTION_TRANSACTION_RECORDED);
        broadcast.setPackage(getPackageName());
        broadcast.putExtra("transaction_id", insertedId);
        sendBroadcast(broadcast);
    }

}
