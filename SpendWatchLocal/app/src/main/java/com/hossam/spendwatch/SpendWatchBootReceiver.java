package com.hossam.spendwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SpendWatchBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        MonthlySummaryReceiver.scheduleNext(context);
        SpendWatchWidget.updateAll(context);
    }
}
