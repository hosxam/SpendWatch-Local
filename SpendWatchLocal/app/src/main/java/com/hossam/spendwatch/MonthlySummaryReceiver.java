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
        Calendar end = Calendar.getInstance();
        end.set(Calendar.DAY_OF_MONTH, 1);
        end.set(Calendar.HOUR_OF_DAY, 0);
        end.set(Calendar.MINUTE, 0);
        end.set(Calendar.SECOND, 0);
        end.set(Calendar.MILLISECOND, 0);

        Calendar start = (Calendar) end.clone();
        start.add(Calendar.MONTH, -1);

        double spent = SpendDatabase.getInstance(context)
                .getTotalSpentAedBetween(start.getTimeInMillis(), end.getTimeInMillis() - 1);

        String month = new SimpleDateFormat("MMMM", Locale.getDefault()).format(start.getTime());
        NotificationHelper.notifyMonthlySummary(context, spent, month);
        scheduleNext(context);
    }

    public static void scheduleNext(Context context) {
        Calendar next = Calendar.getInstance();
        next.add(Calendar.MONTH, 1);
        next.set(Calendar.DAY_OF_MONTH, 1);
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
