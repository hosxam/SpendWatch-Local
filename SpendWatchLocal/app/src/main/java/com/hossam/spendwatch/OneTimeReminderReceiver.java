package com.hossam.spendwatch;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

/** Inexact but battery-friendly reminders for manually planned future expenses. */
public final class OneTimeReminderReceiver extends BroadcastReceiver {
    private static final String EXTRA_ID = "commitment_id";
    private static final String PREFS = "spendwatch_delivered_reminders_v1";

    private static String key(long id) { return "payment_" + id; }

    /**
     * Use the reminder configuration as the delivery identity. A reminder is
     * shown only once for a particular due date + lead time, even if the app
     * is reopened or another payment is edited after it fires.
     */
    private static String fingerprint(OneTimeCommitment item) {
        return item.getDueAt() + ":" + item.getRemindDays();
    }

    static boolean wasDelivered(Context context, OneTimeCommitment item) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return fingerprint(item).equals(prefs.getString(key(item.getId()), null));
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        long id = intent.getLongExtra(EXTRA_ID, -1L);
        OneTimeCommitment item = SpendDatabase.getInstance(context).getOneTimeCommitment(id);
        if (item == null || !item.isPending()
                || item.getRemindDays() == OneTimeCommitment.NO_REMINDER
                || wasDelivered(context, item)) return;
        // Guard against outdated alarms dispatched after a reminder is edited.
        if (reminderTimestamp(item) > System.currentTimeMillis()) return;
        // Persist before notifying, so reopening the app never repeats it.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(key(item.getId()), fingerprint(item)).commit();
        NotificationHelper.notifyOneTimeCommitment(context, item);
    }

    private static PendingIntent pending(Context context, long id) {
        Intent i = new Intent(context, OneTimeReminderReceiver.class)
                .putExtra(EXTRA_ID, id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(context, (int)(id & 0x7fffffff), i, flags);
    }

    public static long reminderTimestamp(OneTimeCommitment item) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(item.getDueAt());
        c.set(Calendar.HOUR_OF_DAY, 9);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (item.getRemindDays() < 0)
            c.add(Calendar.MONTH, item.getRemindDays());
        else
            c.add(Calendar.DAY_OF_MONTH, -item.getRemindDays());
        return c.getTimeInMillis();
    }

    public static void scheduleAll(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        long now = System.currentTimeMillis();
        for (OneTimeCommitment item : SpendDatabase.getInstance(context).getOneTimeCommitments()) {
            PendingIntent p = pending(context, item.getId());
            manager.cancel(p);
            if (!item.isPending()
                    || item.getRemindDays() == OneTimeCommitment.NO_REMINDER
                    || wasDelivered(context, item)) continue;
            long when = reminderTimestamp(item);
            if (item.getDueAt() < now) continue;
            if (when <= now) when = now + 60_000L;
            if (Build.VERSION.SDK_INT >= 23)
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, p);
            else manager.set(AlarmManager.RTC_WAKEUP, when, p);
        }
    }
}
