package com.hossam.spendwatch.qasender;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.os.Bundle;

public class QaSenderActivity extends Activity {
    private static final String CHANNEL = "qa_bank";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(new NotificationChannel(
                        CHANNEL, "QA Bank Alerts", NotificationManager.IMPORTANCE_HIGH));
            }

            android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new android.app.Notification.Builder(this, CHANNEL)
                    : new android.app.Notification.Builder(this);

            builder.setSmallIcon(android.R.drawable.stat_notify_more)
                    .setContentTitle("Card transaction")
                    .setContentText("Your card ending 1234 was used for AED 42.75 at TEST CAFE. Successful transaction.")
                    .setStyle(new android.app.Notification.BigTextStyle()
                            .bigText("Your card was used for AED 42.75 at TEST CAFE. Successful transaction."))
                    .setAutoCancel(false);

            nm.notify(4242, builder.build());
        }
        finish();
    }
}
