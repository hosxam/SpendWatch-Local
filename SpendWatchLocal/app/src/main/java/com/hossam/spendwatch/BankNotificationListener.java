package com.hossam.spendwatch;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.LinkedHashSet;
import java.util.Set;

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

        Bundle extras = notification.extras;
        String title = stringValue(extras.getCharSequence(Notification.EXTRA_TITLE));
        String body = collectNotificationText(extras);

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

    private String collectNotificationText(Bundle extras) {
        Set<String> parts = new LinkedHashSet<>();
        add(parts, extras.getCharSequence(Notification.EXTRA_TEXT));
        add(parts, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        add(parts, extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        add(parts, extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));
        add(parts, extras.getCharSequence(Notification.EXTRA_INFO_TEXT));

        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence line : lines) add(parts, line);
        }

        if (BuildCompat.hasMessagingStyle()) {
            try {
                Parcelable[] rawMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
                if (rawMessages != null) {
                    for (Parcelable raw : rawMessages) {
                        if (raw instanceof Bundle) {
                            Bundle message = (Bundle) raw;
                            add(parts, message.getCharSequence("text"));
                            add(parts, message.getCharSequence("sender"));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        StringBuilder body = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (body.length() > 0) body.append(" ");
            body.append(p);
        }
        return body.toString();
    }

    private void add(Set<String> parts, CharSequence value) {
        String s = stringValue(value);
        if (!s.isEmpty()) parts.add(s);
    }

    private String stringValue(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    private static final class BuildCompat {
        static boolean hasMessagingStyle() {
            return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N;
        }
    }
}
