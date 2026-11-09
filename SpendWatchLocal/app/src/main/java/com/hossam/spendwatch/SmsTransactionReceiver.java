package com.hossam.spendwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

/**
 * Receives only real Android incoming-SMS broadcasts. Unlike a Notification
 * Listener it cannot read WhatsApp, shopping apps, or other app notifications.
 * Requires the user's RECEIVE_SMS runtime permission.
 */
public final class SmsTransactionReceiver extends BroadcastReceiver {
    private static final String TAG = "SpendWatchSMS";
    public static final String ACTION_TRANSACTION_RECORDED =
            "com.hossam.spendwatch.TRANSACTION_RECORDED";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction()))
            return;
        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) return;

        String sender = messages[0].getDisplayOriginatingAddress();
        if (sender == null) sender = "";
        StringBuilder body = new StringBuilder();
        long received = messages[0].getTimestampMillis();
        for (SmsMessage sms : messages) {
            if (sms == null) continue;
            String from = sms.getDisplayOriginatingAddress();
            if (from != null && !sender.equals(from)) continue;
            if (sms.getDisplayMessageBody() != null) body.append(sms.getDisplayMessageBody());
        }
        if (body.length() == 0) return;

        Transaction transaction = BankTransactionParser.parseNotification(
                sender, body.toString(), "SMS");
        if (transaction == null) return;
        if (received > 0) transaction.setTimestamp(received);

        long id;
        try {
            id = SpendDatabase.getInstance(context).insertTransaction(transaction);
        } catch (Exception e) {
            Log.e(TAG, "Could not record bank SMS", e);
            return;
        }
        if (id <= 0) return;

        NotificationHelper.notifyTransactionRecorded(context, transaction);
        SpendWatchWidget.updateAll(context);
        ExcelHistory.schedule(context);
        Intent changed = new Intent(ACTION_TRANSACTION_RECORDED);
        changed.setPackage(context.getPackageName());
        changed.putExtra("transaction_id", id);
        context.sendBroadcast(changed);
    }
}
