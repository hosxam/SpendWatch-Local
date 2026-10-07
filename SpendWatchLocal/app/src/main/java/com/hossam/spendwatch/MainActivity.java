package com.hossam.spendwatch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private SpendDatabase database;
    private TextView tvSafeToSpend;
    private TextView tvMonthSpent;
    private TextView tvUpcoming;
    private TextView tvProjected;
    private TextView tvIncome;
    private TextView tvInsight;
    private TextView tvStatusMessage;
    private LinearLayout layoutPermissionBanner;
    private LinearLayout layoutBudgets;
    private LinearLayout layoutTransactions;
    private boolean receiverRegistered = false;

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
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(BankNotificationListener.ACTION_TRANSACTION_RECORDED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(transactionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(transactionReceiver, filter);
            }
            receiverRegistered = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (receiverRegistered) {
            try { unregisterReceiver(transactionReceiver); } catch (Exception ignored) {}
            receiverRegistered = false;
        }
    }

    private GradientDrawable cardBg(int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(28f);
        gd.setStroke(1, 0xFFE2E8F0);
        return gd;
    }

    private TextView sectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(0xFF64748B);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, 26, 0, 10);
        return tv;
    }

    private Button actionButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private boolean isNotificationServiceEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (flat == null) return false;
        return flat.contains(getPackageName());
    }

    private void checkNotificationPermission() {
        boolean enabled = isNotificationServiceEnabled();
        layoutPermissionBanner.setVisibility(enabled ? View.GONE : View.VISIBLE);
        tvStatusMessage.setText(enabled
                ? "Listening locally for supported UAE bank notifications."
                : "Notification access is required for automatic capture.");
        tvStatusMessage.setTextColor(enabled ? 0xFF059669 : 0xFFDC2626);
    }

    private void openNotificationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Open Settings > Notification access and enable SpendWatch.", Toast.LENGTH_LONG).show();
        }
    }

    private void refreshData() {
        double income = database.getMonthlyIncome();
        double spent = database.getTotalSpentAedThisMonth();
        double upcoming = database.getUpcomingRecurringAedThisMonth();
        double safe = database.getSafeToSpendRestOfMonth();
        double projectedSpend = database.getProjectedMonthEndSpend();
        double projectedBalance = income > 0
                ? income - projectedSpend - database.getTotalSinkingMonthlyAllocation()
                : 0.0;

        tvIncome.setText(income > 0 ? String.format(Locale.US, "AED %.2f", income) : "Not set");
        tvMonthSpent.setText(String.format(Locale.US, "AED %.2f", spent));
        tvUpcoming.setText(String.format(Locale.US, "AED %.2f", upcoming));
        tvSafeToSpend.setText(income > 0 ? String.format(Locale.US, "AED %.2f", safe) : "Set income");
        tvProjected.setText(income > 0
                ? String.format(Locale.US, "AED %.2f", projectedBalance)
                : "Set income");

        tvInsight.setText(database.getTopBudgetPaceInsight());
        populateBudgets();
        populateTransactions();
    }

    private void populateBudgets() {
        layoutBudgets.removeAllViews();
        List<BudgetEnvelope> budgets = database.getBudgets();
        boolean shown = false;
        for (BudgetEnvelope b : budgets) {
            double spent = database.getSpentForCategoryThisMonth(b.getName());
            if (b.getEffectiveAmount() <= 0 && spent <= 0) continue;
            shown = true;

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(24, 18, 24, 18);
            item.setBackground(cardBg(0xFFFFFFFF));
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            itemLp.setMargins(0, 0, 0, 10);
            item.setLayoutParams(itemLp);

            TextView top = new TextView(this);
            double remaining = b.getEffectiveAmount() - spent;
            String badge = b.isSinkingFund() ? "  •  Sinking fund" : (b.isRolloverEnabled() ? "  •  Rollover" : "");
            top.setText(b.getName() + badge);
            top.setTextSize(15);
            top.setTextColor(0xFF0F172A);
            top.setTypeface(null, Typeface.BOLD);
            item.addView(top);

            TextView values = new TextView(this);
            values.setText(String.format(Locale.US,
                    "Budget AED %.2f   •   Spent AED %.2f   •   Remaining AED %.2f",
                    b.getEffectiveAmount(), spent, remaining));
            values.setTextSize(12);
            values.setTextColor(remaining < 0 ? 0xFFDC2626 : 0xFF64748B);
            values.setPadding(0, 8, 0, 8);
            item.addView(values);

            ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(1000);
            int p = b.getEffectiveAmount() > 0
                    ? (int) Math.min(1000, Math.max(0, (spent / b.getEffectiveAmount()) * 1000))
                    : 0;
            progress.setProgress(p);
            item.addView(progress);

            item.setOnClickListener(v -> startActivity(new Intent(this, BudgetActivity.class)));
            layoutBudgets.addView(item);
        }

        if (!shown) {
            TextView empty = new TextView(this);
            empty.setText("No funded envelopes yet. Tap Budgets to allocate money.");
            empty.setTextColor(0xFF64748B);
            empty.setPadding(12, 18, 12, 18);
            layoutBudgets.addView(empty);
        }
    }

    private boolean isSpendingType(String type) {
        return "card_purchase".equals(type) || "pos".equals(type) || "online".equals(type)
                || "atm".equals(type) || "direct_debit".equals(type)
                || "fee".equals(type) || "other_expense".equals(type);
    }

    private void populateTransactions() {
        layoutTransactions.removeAllViews();
        List<Transaction> transactions = database.getAllTransactions();
        if (transactions.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No transactions captured yet.");
            empty.setTextColor(0xFF64748B);
            empty.setPadding(12, 20, 12, 20);
            layoutTransactions.addView(empty);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
        int count = 0;
        for (Transaction t : transactions) {
            if (count++ >= 30) break;

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(24, 18, 24, 18);
            item.setBackground(cardBg(t.isIgnored() ? 0xFFF8FAFC : 0xFFFFFFFF));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 8);
            item.setLayoutParams(lp);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            TextView merchant = new TextView(this);
            merchant.setText(t.getMerchant());
            merchant.setTextSize(15);
            merchant.setTypeface(null, Typeface.BOLD);
            merchant.setTextColor(0xFF0F172A);
            row.addView(merchant, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView amount = new TextView(this);
            boolean spendType = isSpendingType(t.getTransactionType());
            String prefix = spendType ? "-" : ("income".equals(t.getTransactionType()) || "refund".equals(t.getTransactionType()) ? "+" : "");
            amount.setText(String.format(Locale.US, "%s%s %.2f", prefix, t.getCurrency(), t.getAmount()));
            amount.setTextColor(spendType ? 0xFFDC2626
                    : ("income".equals(t.getTransactionType()) || "refund".equals(t.getTransactionType()) ? 0xFF059669 : 0xFF475569));
            amount.setTypeface(null, Typeface.BOLD);
            row.addView(amount);
            item.addView(row);

            TextView details = new TextView(this);
            details.setText(sdf.format(new Date(t.getTimestamp())) + " • " + t.getBankSource()
                    + " • " + t.getCategory() + " • " + t.getTransactionType()
                    + (t.isIgnored() ? " • IGNORED" : ""));
            details.setTextSize(11);
            details.setTextColor(0xFF64748B);
            details.setPadding(0, 6, 0, 0);
            item.addView(details);

            item.setOnClickListener(v -> showEditTransactionDialog(t));
            layoutTransactions.addView(item);
        }
    }

    private List<String> categoryNames() {
        List<String> names = new ArrayList<>();
        for (BudgetEnvelope b : database.getBudgets()) names.add(b.getName());
        if (!names.contains("Income")) names.add("Income");
        if (!names.contains("Refunds")) names.add("Refunds");
        if (!names.contains("Transfers")) names.add("Transfers");
        return names;
    }

    private int indexOf(List<String> values, String value) {
        int idx = values.indexOf(value);
        return idx < 0 ? 0 : idx;
    }

    private void showEditTransactionDialog(Transaction t) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(36, 20, 36, 0);

        TextView raw = new TextView(this);
        raw.setText(t.getRawMessage());
        raw.setTextSize(11);
        raw.setTextColor(0xFF64748B);
        raw.setPadding(0, 0, 0, 14);
        form.addView(raw);

        List<String> categories = categoryNames();
        Spinner category = new Spinner(this);
        category.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories));
        category.setSelection(indexOf(categories, t.getCategory()));
        form.addView(category);

        String[] types = new String[]{"card_purchase","pos","online","atm","direct_debit","fee","other_expense","transfer_sent","income","refund"};
        Spinner type = new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types));
        int typeIndex = 0;
        for (int i = 0; i < types.length; i++) if (types[i].equals(t.getTransactionType())) typeIndex = i;
        type.setSelection(typeIndex);
        form.addView(type);

        CheckBox ignored = new CheckBox(this);
        ignored.setText("Ignore this transaction in calculations");
        ignored.setChecked(t.isIgnored());
        form.addView(ignored);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit transaction")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    database.updateTransaction(t.getId(),
                            categories.get(category.getSelectedItemPosition()),
                            types[type.getSelectedItemPosition()],
                            ignored.isChecked());
                    refreshData();
                })
                .setNeutralButton("Delete", (d, w) -> {
                    database.deleteTransaction(t.getId());
                    refreshData();
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
    }

    private void showIncomeDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        double current = database.getMonthlyIncome();
        if (current > 0) input.setText(String.format(Locale.US, "%.2f", current));
        input.setHint("Monthly take-home income in AED");
        int pad = 40;
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(pad, 12, pad, 0);
        wrapper.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("Monthly income")
                .setView(wrapper)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        database.setMonthlyIncome(Double.parseDouble(input.getText().toString()));
                        refreshData();
                    } catch (Exception e) {
                        Toast.makeText(this, "Enter a valid amount.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showManualTransactionDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(36, 10, 36, 0);

        EditText merchant = new EditText(this);
        merchant.setHint("Merchant / description");
        form.addView(merchant);

        EditText amount = new EditText(this);
        amount.setHint("Amount in AED");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(amount);

        List<String> categories = categoryNames();
        Spinner category = new Spinner(this);
        category.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories));
        form.addView(category);

        String[] types = new String[]{"card_purchase","atm","other_expense","transfer_sent","income","refund"};
        Spinner type = new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types));
        form.addView(type);

        new AlertDialog.Builder(this)
                .setTitle("Add manual transaction")
                .setView(form)
                .setPositiveButton("Add", (d, w) -> {
                    try {
                        Transaction t = new Transaction();
                        t.setAmount(Double.parseDouble(amount.getText().toString()));
                        t.setCurrency("AED");
                        t.setMerchant(merchant.getText().toString().trim().isEmpty() ? "Manual entry" : merchant.getText().toString().trim());
                        t.setTimestamp(System.currentTimeMillis());
                        t.setBankSource("Manual");
                        t.setRawMessage("Manual transaction");
                        t.setCategory(categories.get(category.getSelectedItemPosition()));
                        t.setTransactionType(types[type.getSelectedItemPosition()]);
                        database.insertTransaction(t);
                        refreshData();
                    } catch (Exception e) {
                        Toast.makeText(this, "Enter a valid amount.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void runSampleNotificationTest() {
        String title = "Ruya";
        String body = "Your Ruya card ending 4091 was used for AED 142.50 at CARREFOUR DEIRA.";
        Transaction t = BankTransactionParser.parseNotification(title, body, "ae.ruya.digital");
        if (t != null) {
            t.setTimestamp(System.currentTimeMillis());
            long id = database.insertTransaction(t);
            Toast.makeText(this, id > 0 ? "Sample Ruya transaction added." : "Duplicate sample ignored.", Toast.LENGTH_SHORT).show();
            refreshData();
        }
    }

    private void setupViews() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 48);
        root.setBackgroundColor(0xFFF8FAFC);

        TextView title = new TextView(this);
        title.setText("SpendWatch Local");
        title.setTextSize(26);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xFF0F172A);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("UAE-first budgeting • Local-only • No internet permission");
        subtitle.setTextColor(0xFF059669);
        subtitle.setTextSize(12);
        subtitle.setPadding(0, 4, 0, 12);
        root.addView(subtitle);

        tvStatusMessage = new TextView(this);
        tvStatusMessage.setTextSize(12);
        root.addView(tvStatusMessage);

        layoutPermissionBanner = new LinearLayout(this);
        layoutPermissionBanner.setOrientation(LinearLayout.VERTICAL);
        layoutPermissionBanner.setPadding(20, 16, 20, 16);
        layoutPermissionBanner.setBackground(cardBg(0xFFFFF1F2));
        TextView perm = new TextView(this);
        perm.setText("Automatic capture is off. Enable notification access.");
        perm.setTextColor(0xFF9F1239);
        layoutPermissionBanner.addView(perm);
        Button permission = actionButton("Grant notification access");
        permission.setOnClickListener(v -> openNotificationSettings());
        layoutPermissionBanner.addView(permission);
        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bannerLp.setMargins(0, 14, 0, 12);
        root.addView(layoutPermissionBanner, bannerLp);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(28, 24, 28, 24);
        hero.setBackground(cardBg(0xFFFFFFFF));

        TextView safeLabel = new TextView(this);
        safeLabel.setText("SAFE TO SPEND • REST OF MONTH");
        safeLabel.setTextSize(11);
        safeLabel.setTextColor(0xFF64748B);
        safeLabel.setTypeface(null, Typeface.BOLD);
        hero.addView(safeLabel);

        tvSafeToSpend = new TextView(this);
        tvSafeToSpend.setText("Set income");
        tvSafeToSpend.setTextSize(34);
        tvSafeToSpend.setTypeface(null, Typeface.BOLD);
        tvSafeToSpend.setTextColor(0xFF0F172A);
        tvSafeToSpend.setPadding(0, 6, 0, 2);
        hero.addView(tvSafeToSpend);

        TextView safeDesc = new TextView(this);
        safeDesc.setText("Income minus recorded spending, upcoming recurring bills, and monthly sinking-fund contributions.");
        safeDesc.setTextSize(11);
        safeDesc.setTextColor(0xFF64748B);
        hero.addView(safeDesc);

        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        heroLp.setMargins(0, 12, 0, 12);
        root.addView(hero, heroLp);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.VERTICAL);
        metrics.setPadding(24, 20, 24, 20);
        metrics.setBackground(cardBg(0xFFFFFFFF));

        tvIncome = metricRow(metrics, "Monthly income");
        tvMonthSpent = metricRow(metrics, "Spent this month");
        tvUpcoming = metricRow(metrics, "Upcoming bills");
        tvProjected = metricRow(metrics, "Projected month-end balance");
        root.addView(metrics);

        root.addView(sectionTitle("INSIGHT"));
        tvInsight = new TextView(this);
        tvInsight.setTextSize(14);
        tvInsight.setTextColor(0xFF0F172A);
        tvInsight.setPadding(20, 18, 20, 18);
        tvInsight.setBackground(cardBg(0xFFFFFFFF));
        root.addView(tvInsight);

        root.addView(sectionTitle("ACTIONS"));
        LinearLayout actionRow1 = new LinearLayout(this);
        Button incomeBtn = actionButton("Set income");
        incomeBtn.setOnClickListener(v -> showIncomeDialog());
        actionRow1.addView(incomeBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button budgetsBtn = actionButton("Budgets");
        budgetsBtn.setOnClickListener(v -> startActivity(new Intent(this, BudgetActivity.class)));
        actionRow1.addView(budgetsBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(actionRow1);

        LinearLayout actionRow2 = new LinearLayout(this);
        Button recurringBtn = actionButton("Recurring bills");
        recurringBtn.setOnClickListener(v -> startActivity(new Intent(this, RecurringActivity.class)));
        actionRow2.addView(recurringBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button manualBtn = actionButton("Add transaction");
        manualBtn.setOnClickListener(v -> showManualTransactionDialog());
        actionRow2.addView(manualBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(actionRow2);

        Button testBtn = actionButton("Test Ruya parser");
        testBtn.setOnClickListener(v -> runSampleNotificationTest());
        root.addView(testBtn);

        root.addView(sectionTitle("BUDGET ENVELOPES"));
        layoutBudgets = new LinearLayout(this);
        layoutBudgets.setOrientation(LinearLayout.VERTICAL);
        root.addView(layoutBudgets);

        root.addView(sectionTitle("RECENT TRANSACTIONS • TAP TO EDIT"));
        layoutTransactions = new LinearLayout(this);
        layoutTransactions.setOrientation(LinearLayout.VERTICAL);
        root.addView(layoutTransactions);

        scroll.addView(root);
        setContentView(scroll);
    }

    private TextView metricRow(LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 7, 0, 7);

        TextView left = new TextView(this);
        left.setText(label);
        left.setTextColor(0xFF64748B);
        left.setTextSize(13);
        row.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView value = new TextView(this);
        value.setText("AED 0.00");
        value.setTextColor(0xFF0F172A);
        value.setTypeface(null, Typeface.BOLD);
        value.setTextSize(13);
        row.addView(value);
        parent.addView(row);
        return value;
    }
}
