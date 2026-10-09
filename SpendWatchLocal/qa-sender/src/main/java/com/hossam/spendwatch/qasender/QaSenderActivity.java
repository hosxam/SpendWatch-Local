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

            String realRuyaAlert = "Dear Customer, Debit Card Purchase of AED 39.95 from account ending with 1234 "
                    + "was done by Card ending with 5678 from TIM HORTONS on 08/10/2026, "
                    + "your available balance is AED 326.77";

            builder.setSmallIcon(android.R.drawable.stat_notify_more)
                    .setContentTitle("Ruya")
                    .setContentText(realRuyaAlert)
                    .setStyle(new android.app.Notification.BigTextStyle().bigText(realRuyaAlert))
                    .setAutoCancel(false);

            nm.notify(4242, builder.build());
        }
        finish();
    }
}
