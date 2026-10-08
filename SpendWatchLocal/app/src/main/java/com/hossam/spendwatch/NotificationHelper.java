package com.hossam.spendwatch;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Locale;

public final class NotificationHelper {

    public static final String CHANNEL_TRANSACTIONS = "transaction_confirmations";
    public static final String CHANNEL_SUMMARIES = "monthly_summaries";

    private NotificationHelper() {}

    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel transactionChannel = new NotificationChannel(
                CHANNEL_TRANSACTIONS,
                "Transaction confirmations",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        transactionChannel.setDescription("Confirms immediately when SpendWatch records a transaction.");
        transactionChannel.enableVibration(true);
        nm.createNotificationChannel(transactionChannel);

        NotificationChannel summaryChannel = new NotificationChannel(
                CHANNEL_SUMMARIES,
                "Monthly spending summaries",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        summaryChannel.setDescription("Simple monthly spending recaps.");
        nm.createNotificationChannel(summaryChannel);
    }

    public static void notifyTransactionRecorded(Context context, Transaction transaction) {
        ensureChannels(context);
        if (!canNotify(context)) return;

        String amount = String.format(Locale.US, "%s %,.2f",
                transaction.getCurrency() == null ? "AED" : transaction.getCurrency(),
                transaction.getAmount());

        String merchant = transaction.getMerchant();
        if (merchant == null || merchant.trim().isEmpty() || "Unknown Merchant".equals(merchant)) {
            merchant = transaction.getBankSource() == null ? "transaction" : transaction.getBankSource() + " transaction";
        }

        String title = "Transaction recorded";
        String body;
        if ("income".equals(transaction.getTransactionType())) {
            body = amount + " received and added to SpendWatch.";
        } else if ("refund".equals(transaction.getTransactionType())) {
            body = amount + " refund recorded.";
        } else if ("transfer_sent".equals(transaction.getTransactionType())) {
            body = amount + " transfer recorded.";
        } else {
            body = amount + " at " + merchant + " was recorded.";
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        PendingIntent openApp = PendingIntent.getActivity(
                context,
                2001,
                new Intent(context, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag()
        );

        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new android.app.Notification.Builder(context, CHANNEL_TRANSACTIONS)
                : new android.app.Notification.Builder(context);

        builder.setSmallIcon(com.hossam.spendwatch.R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(body))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis());

        nm.notify((int) (System.currentTimeMillis() & 0x7fffffff), builder.build());
    }

    public static void notifyMonthlySummary(Context context, double amount, String monthLabel) {
        ensureChannels(context);
        if (!canNotify(context)) return;

        String body = String.format(Locale.US,
                "You spent AED %,.2f in %s.", amount, monthLabel);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        PendingIntent openApp = PendingIntent.getActivity(
                context,
                2002,
                new Intent(context, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag()
        );

        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new android.app.Notification.Builder(context, CHANNEL_SUMMARIES)
                : new android.app.Notification.Builder(context);

        builder.setSmallIcon(com.hossam.spendwatch.R.drawable.ic_notification)
                .setContentTitle("Your monthly SpendWatch recap")
                .setContentText(body)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(body))
                .setContentIntent(openApp)
                .setAutoCancel(true);

        nm.notify(93001, builder.build());
    }

    private static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT >= 33) {
            return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private static int immutableFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
    }
}
