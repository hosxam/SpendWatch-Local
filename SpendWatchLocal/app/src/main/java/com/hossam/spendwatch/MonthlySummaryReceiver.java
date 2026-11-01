package com.hossam.spendwatch;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MonthlySummaryReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        SpendDatabase db = SpendDatabase.getInstance(context);
        long end = db.getCurrentCycleStart();
        long start = db.getCycleStart(end - 1);
        double spent = db.getTotalSpentAedBetween(start, end - 1);

        String label = new SimpleDateFormat("MMM d", Locale.getDefault()).format(new java.util.Date(start))
                + " – " + new SimpleDateFormat("MMM d", Locale.getDefault()).format(new java.util.Date(end - 1));
        NotificationHelper.notifyMonthlySummary(context, spent, label);
        LocalLedgerExporter.queueRefresh(context);
        scheduleNext(context);
    }

    public static void scheduleNext(Context context) {
        long nextPeriod = SpendDatabase.getInstance(context).getCurrentCycleEnd();
        Calendar next = Calendar.getInstance();
        next.setTimeInMillis(nextPeriod);
        next.set(Calendar.HOUR_OF_DAY, 9);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);

        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;

        PendingIntent pending = PendingIntent.getBroadcast(
                context,
                31001,
                new Intent(context, MonthlySummaryReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag()
        );

        long trigger = next.getTimeInMillis();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending);
        } else {
            alarm.set(AlarmManager.RTC_WAKEUP, trigger, pending);
        }
    }

    private static int immutableFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
    }
}
