package com.hossam.spendwatch;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import java.util.Locale;

public class SpendWatchWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateWidgets(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        updateAll(context);
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, SpendWatchWidget.class);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids != null && ids.length > 0) updateWidgets(context, manager, ids);
    }

    private static void updateWidgets(Context context, AppWidgetManager manager, int[] ids) {
        SpendDatabase db = SpendDatabase.getInstance(context);
        double spent = db.getTotalSpentAedThisMonth();
        double safe = db.getSafeToSpendRestOfMonth();
        double income = db.getMonthlyIncome();
        double bills = db.getUpcomingRecurringAedThisMonth();

        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_spendwatch);
            views.setTextViewText(R.id.widget_spent, money(spent));
            views.setTextViewText(R.id.widget_safe, income > 0 ? money(safe) : "Set income");
            views.setTextViewText(R.id.widget_bills, money(bills));

            Intent open = new Intent(context, MainActivity.class);
            PendingIntent pending = PendingIntent.getActivity(
                    context,
                    id,
                    open,
                    PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag()
            );
            views.setOnClickPendingIntent(R.id.widget_root, pending);
            manager.updateAppWidget(id, views);
        }
    }

    private static String money(double value) {
        return String.format(Locale.US, "AED %,.0f", value);
    }

    private static int immutableFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
    }
}
