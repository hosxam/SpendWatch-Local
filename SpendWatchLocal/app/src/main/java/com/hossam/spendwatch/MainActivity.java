package com.hossam.spendwatch;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private SpendDatabase database;
    private TextView tvTotalToday;
    private TextView tvTotalWeek;
    private TextView tvTotalMonth;
    private TextView tvTotalAllTime;
    private TextView tvStatusMessage;
    private LinearLayout layoutPermissionBanner;
    private LinearLayout layoutTransactions;
    private Button btnGrantPermission;
    private Button btnTestNotification;
    private Button btnClearData;

    private final BroadcastReceiver transactionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshData();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = SpendDatabase.getInstance(this);
        setupViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkNotificationPermission();
        refreshData();

        IntentFilter filter = new IntentFilter(BankNotificationListener.ACTION_TRANSACTION_RECORDED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(transactionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(transactionReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(transactionReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (flat != null && !flat.isEmpty()) {
            final String[] names = flat.split(":");
            for (String name : names) {
                if (name.contains(pkgName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void checkNotificationPermission() {
        boolean enabled = isNotificationServiceEnabled();
        if (enabled) {
            layoutPermissionBanner.setVisibility(View.GONE);
            tvStatusMessage.setText("Listening locally for Ruya & UAE bank notifications.");
            tvStatusMessage.setTextColor(0xFF10B981);
        } else {
            layoutPermissionBanner.setVisibility(View.VISIBLE);
            tvStatusMessage.setText("Notification access is required to detect UAE bank alerts.");
            tvStatusMessage.setTextColor(0xFFEF4444);
        }
    }

    private void openNotificationSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        } else {
            intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Please open Settings > Notification Access to grant permission.", Toast.LENGTH_LONG).show();
        }
    }

    private void refreshData() {
        double today = database.getTotalSpentAedToday();
        double week = database.getTotalSpentAedThisWeek();
        double month = database.getTotalSpentAedThisMonth();
        double allTime = database.getTotalSpentAedAllTime();

        tvTotalToday.setText(String.format(Locale.US, "AED %.2f", today));
        tvTotalWeek.setText(String.format(Locale.US, "AED %.2f", week));
        tvTotalMonth.setText(String.format(Locale.US, "AED %.2f", month));
        tvTotalAllTime.setText(String.format(Locale.US, "AED %.2f", allTime));

        populateTransactionsList();
    }

    private void populateTransactionsList() {
        layoutTransactions.removeAllViews();
        List<Transaction> transactions = database.getAllTransactions();

        if (transactions.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("No outgoing UAE transactions detected yet.\nSpendWatch will automatically record Ruya & UAE bank alerts.");
            emptyView.setPadding(32, 48, 32, 48);
            emptyView.setTextSize(14);
            emptyView.setTextColor(0xFF64748B);
            emptyView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            layoutTransactions.addView(emptyView);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault());

        for (final Transaction t : transactions) {
            if (t.isIgnored()) continue;

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(36, 28, 36, 28);
            item.setBackgroundColor(0xFFFFFFFF);

            LinearLayout rowTop = new LinearLayout(this);
            rowTop.setOrientation(LinearLayout.HORIZONTAL);

            TextView tvMerchant = new TextView(this);
            tvMerchant.setText(t.getMerchant());
            tvMerchant.setTextSize(16);
            tvMerchant.setTextColor(0xFF0F172A);
            tvMerchant.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams lpMerch = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            rowTop.addView(tvMerchant, lpMerch);

            TextView tvAmount = new TextView(this);
            tvAmount.setText(String.format(Locale.US, "-%s %.2f", t.getCurrency(), t.getAmount()));
            tvAmount.setTextSize(16);
            tvAmount.setTextColor(0xFFEF4444);
            tvAmount.setTypeface(null, android.graphics.Typeface.BOLD);
            rowTop.addView(tvAmount);

            item.addView(rowTop);

            LinearLayout rowBottom = new LinearLayout(this);
            rowBottom.setOrientation(LinearLayout.HORIZONTAL);
            rowBottom.setPadding(0, 8, 0, 0);

            TextView tvDetails = new TextView(this);
            tvDetails.setText(sdf.format(new Date(t.getTimestamp())) + " • " + t.getBankSource() + " • " + t.getCategory());
            tvDetails.setTextSize(12);
            tvDetails.setTextColor(0xFF64748B);
            rowBottom.addView(tvDetails);

            item.addView(rowBottom);

            View divider = new View(this);
            divider.setBackgroundColor(0xFFE2E8F0);
            LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2);
            divParams.setMargins(0, 20, 0, 0);
            item.addView(divider, divParams);

            layoutTransactions.addView(item);
        }
    }

    private void runSampleNotificationTest() {
        String testTitle = "Ruya";
        String testBody = "Your Ruya card ending 4091 was used for AED 142.50 at CARREFOUR DEIRA on 07/10/2026.";
        Transaction t = BankTransactionParser.parseNotification(testTitle, testBody, "ae.ruya.digital");
        if (t != null) {
            database.insertTransaction(t);
            refreshData();
            Toast.makeText(this, "Sample Ruya transaction recorded!", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupViews() {
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF8FAFC);
        root.setPadding(24, 24, 24, 48);

        TextView tvAppTitle = new TextView(this);
        tvAppTitle.setText("SpendWatch Local (UAE)");
        tvAppTitle.setTextSize(24);
        tvAppTitle.setTextColor(0xFF0F172A);
        tvAppTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(tvAppTitle);

        TextView tvPrivacy = new TextView(this);
        tvPrivacy.setText("🔒 Ruya Primary Bank • 100% On-Device • Zero Internet");
        tvPrivacy.setTextSize(12);
        tvPrivacy.setTextColor(0xFF10B981);
        tvPrivacy.setPadding(0, 4, 0, 16);
        root.addView(tvPrivacy);

        tvStatusMessage = new TextView(this);
        tvStatusMessage.setTextSize(13);
        root.addView(tvStatusMessage);

        layoutPermissionBanner = new LinearLayout(this);
        layoutPermissionBanner.setOrientation(LinearLayout.VERTICAL);
        layoutPermissionBanner.setPadding(24, 20, 24, 20);
        layoutPermissionBanner.setBackgroundColor(0xFFFEE2E2);
        LinearLayout.LayoutParams lpBanner = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpBanner.setMargins(0, 16, 0, 16);
        layoutPermissionBanner.setLayoutParams(lpBanner);

        TextView tvPermNotice = new TextView(this);
        tvPermNotice.setText("Notification Access is disabled. Tap below to enable SpendWatch in Android Settings.");
        tvPermNotice.setTextColor(0xFF991B1B);
        tvPermNotice.setTextSize(13);
        layoutPermissionBanner.addView(tvPermNotice);

        btnGrantPermission = new Button(this);
        btnGrantPermission.setText("Grant Notification Access");
        btnGrantPermission.setBackgroundColor(0xFFEF4444);
        btnGrantPermission.setTextColor(0xFFFFFFFF);
        btnGrantPermission.setOnClickListener(v -> openNotificationSettings());
        layoutPermissionBanner.addView(btnGrantPermission);
        root.addView(layoutPermissionBanner);

        // Totals Card
        LinearLayout cardTotals = new LinearLayout(this);
        cardTotals.setOrientation(LinearLayout.VERTICAL);
        cardTotals.setBackgroundColor(0xFFFFFFFF);
        cardTotals.setPadding(32, 28, 32, 28);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCard.setMargins(0, 16, 0, 24);
        cardTotals.setLayoutParams(lpCard);

        TextView tvCardTitle = new TextView(this);
        tvCardTitle.setText("UAE SPENDING SUMMARY (AED)");
        tvCardTitle.setTextSize(12);
        tvCardTitle.setTextColor(0xFF64748B);
        tvCardTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        cardTotals.addView(tvCardTitle);

        tvTotalMonth = new TextView(this);
        tvTotalMonth.setText("AED 0.00");
        tvTotalMonth.setTextSize(34);
        tvTotalMonth.setTextColor(0xFF0F172A);
        tvTotalMonth.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTotalMonth.setPadding(0, 8, 0, 4);
        cardTotals.addView(tvTotalMonth);

        TextView tvMonthLabel = new TextView(this);
        tvMonthLabel.setText("Total This Month (AED)");
        tvMonthLabel.setTextSize(12);
        tvMonthLabel.setTextColor(0xFF64748B);
        cardTotals.addView(tvMonthLabel);

        LinearLayout subTotals = new LinearLayout(this);
        subTotals.setOrientation(LinearLayout.HORIZONTAL);
        subTotals.setPadding(0, 20, 0, 0);

        LinearLayout colToday = new LinearLayout(this);
        colToday.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpCol = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        TextView lblToday = new TextView(this);
        lblToday.setText("Today");
        lblToday.setTextSize(11);
        lblToday.setTextColor(0xFF64748B);
        tvTotalToday = new TextView(this);
        tvTotalToday.setText("AED 0.00");
        tvTotalToday.setTextSize(14);
        tvTotalToday.setTextColor(0xFF0F172A);
        tvTotalToday.setTypeface(null, android.graphics.Typeface.BOLD);
        colToday.addView(lblToday);
        colToday.addView(tvTotalToday);
        subTotals.addView(colToday, lpCol);

        LinearLayout colWeek = new LinearLayout(this);
        colWeek.setOrientation(LinearLayout.VERTICAL);
        TextView lblWeek = new TextView(this);
        lblWeek.setText("This Week");
        lblWeek.setTextSize(11);
        lblWeek.setTextColor(0xFF64748B);
        tvTotalWeek = new TextView(this);
        tvTotalWeek.setText("AED 0.00");
        tvTotalWeek.setTextSize(14);
        tvTotalWeek.setTextColor(0xFF0F172A);
        tvTotalWeek.setTypeface(null, android.graphics.Typeface.BOLD);
        colWeek.addView(lblWeek);
        colWeek.addView(tvTotalWeek);
        subTotals.addView(colWeek, lpCol);

        LinearLayout colAll = new LinearLayout(this);
        colAll.setOrientation(LinearLayout.VERTICAL);
        TextView lblAll = new TextView(this);
        lblAll.setText("All Time");
        lblAll.setTextSize(11);
        lblAll.setTextColor(0xFF64748B);
        tvTotalAllTime = new TextView(this);
        tvTotalAllTime.setText("AED 0.00");
        tvTotalAllTime.setTextSize(14);
        tvTotalAllTime.setTextColor(0xFF0F172A);
        tvTotalAllTime.setTypeface(null, android.graphics.Typeface.BOLD);
        colAll.addView(lblAll);
        colAll.addView(tvTotalAllTime);
        subTotals.addView(colAll, lpCol);

        cardTotals.addView(subTotals);
        root.addView(cardTotals);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, 0, 0, 16);

        btnTestNotification = new Button(this);
        btnTestNotification.setText("Test Ruya Alert");
        btnTestNotification.setOnClickListener(v -> runSampleNotificationTest());
        actionRow.addView(btnTestNotification, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        btnClearData = new Button(this);
        btnClearData.setText("Clear All");
        btnClearData.setOnClickListener(v -> {
            database.clearAll();
            refreshData();
            Toast.makeText(this, "Cleared transactions", Toast.LENGTH_SHORT).show();
        });
        actionRow.addView(btnClearData, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        root.addView(actionRow);

        TextView tvTxHeader = new TextView(this);
        tvTxHeader.setText("TRANSACTIONS (AED)");
        tvTxHeader.setTextSize(13);
        tvTxHeader.setTextColor(0xFF64748B);
        tvTxHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTxHeader.setPadding(0, 12, 0, 8);
        root.addView(tvTxHeader);

        layoutTransactions = new LinearLayout(this);
        layoutTransactions.setOrientation(LinearLayout.VERTICAL);
        root.addView(layoutTransactions);

        scrollView.addView(root);
        setContentView(scrollView);
    }
}
