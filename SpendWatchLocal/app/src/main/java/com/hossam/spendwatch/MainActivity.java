package com.hossam.spendwatch;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private SpendDatabase database;
    private LinearLayout content;
    private LinearLayout bottomNav;
    private int currentTab = 0;
    private boolean receiverRegistered = false;

    private final String[] NAV_LABELS = {"Home", "Budgets", "Activity", "Insights", "Settings"};
    private final String[] NAV_ICONS = {"⌂", "◫", "⇅", "↗", "⚙"};

    private final BroadcastReceiver transactionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            renderCurrent();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = SpendDatabase.getInstance(this);
        NotificationHelper.ensureChannels(this);
        MonthlySummaryReceiver.scheduleNext(this);
        requestNotificationPermissionIfNeeded();
        configureWindow();
        buildShell();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onResume() {
        super.onResume();
        renderCurrent();
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

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4101);
        }
    }

    private void configureWindow() {
        Window w = getWindow();
        w.setStatusBarColor(UiKit.BG);
        w.setNavigationBarColor(UiKit.BG);
        if (Build.VERSION.SDK_INT >= 23) {
            w.getDecorView().setSystemUiVisibility(0);
        }
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void buildShell() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(UiKit.BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 18), UiKit.dp(this, 20), UiKit.dp(this, 28));
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 8), UiKit.dp(this, 8), UiKit.dp(this, 10));
        bottomNav.setBackground(UiKit.round(this, Color.rgb(10, 16, 28), 0, Color.rgb(28, 39, 59)));
        shell.addView(bottomNav, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(this, 76)));

        setContentView(shell);
        renderBottomNav();
        renderCurrent();
    }

    private void renderBottomNav() {
        bottomNav.removeAllViews();
        for (int i = 0; i < NAV_LABELS.length; i++) {
            final int index = i;
            boolean selected = index == currentTab;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(UiKit.dp(this, 4), UiKit.dp(this, 4), UiKit.dp(this, 4), UiKit.dp(this, 4));
            if (selected) item.setBackground(UiKit.round(this, Color.rgb(22, 34, 58), 18, Color.rgb(56, 79, 121)));

            TextView icon = UiKit.text(this, NAV_ICONS[i], 18, selected ? UiKit.CYAN : UiKit.MUTED, true);
            icon.setGravity(Gravity.CENTER);
            item.addView(icon);

            TextView label = UiKit.text(this, NAV_LABELS[i], 10, selected ? UiKit.TEXT : UiKit.MUTED, selected);
            label.setGravity(Gravity.CENTER);
            item.addView(label);

            item.setOnClickListener(v -> {
                currentTab = index;
                renderBottomNav();
                renderCurrent();
            });
            bottomNav.addView(item, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        }
    }

    private void renderCurrent() {
        if (content == null) return;
        SpendWatchWidget.updateAll(this);
        content.removeAllViews();
        switch (currentTab) {
            case 1: renderBudgets(); break;
            case 2: renderTransactions(); break;
            case 3: renderInsights(); break;
            case 4: renderSettings(); break;
            default: renderHome(); break;
        }
    }

    private void addHeader(String eyebrow, String title, String subtitle) {
        TextView e = UiKit.label(this, eyebrow);
        e.setTextColor(UiKit.CYAN);
        content.addView(e);

        TextView t = UiKit.text(this, title, 29, UiKit.TEXT, true);
        t.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        t.setPadding(0, UiKit.dp(this, 4), 0, 0);
        content.addView(t);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = UiKit.text(this, subtitle, 13, UiKit.MUTED, false);
            s.setPadding(0, UiKit.dp(this, 6), 0, 0);
            content.addView(s);
        }
        content.addView(UiKit.gap(this, 18));
    }

    private void renderHome() {
        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout brandCol = new LinearLayout(this);
        brandCol.setOrientation(LinearLayout.VERTICAL);
        TextView micro = UiKit.label(this, "PRIVATE MONEY OS");
        micro.setTextColor(UiKit.CYAN);
        brandCol.addView(micro);

        TextView brand = UiKit.text(this, "SpendWatch", 30, UiKit.TEXT, true);
        brand.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        brand.post(() -> {
            float width = Math.max(1, brand.getPaint().measureText(brand.getText().toString()));
            brand.getPaint().setShader(new LinearGradient(
                    0, 0, width, 0,
                    new int[]{UiKit.TEXT, UiKit.CYAN, UiKit.PURPLE},
                    null, Shader.TileMode.CLAMP));
            brand.invalidate();
        });
        brandCol.addView(brand);
        brandRow.addView(brandCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        brandRow.addView(UiKit.pill(this, "●  LOCAL", UiKit.GREEN));
        content.addView(brandRow);
        content.addView(UiKit.gap(this, 16));

        if (!isNotificationServiceEnabled()) {
            LinearLayout warning = UiKit.card(this, 16);
            warning.setBackground(UiKit.gradient(this, 22,
                    Color.rgb(55, 24, 43), Color.rgb(31, 23, 43)));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView copy = UiKit.text(this, "Automatic capture is off\nEnable notification access to track Ruya alerts.", 12, UiKit.TEXT, true);
            row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView enable = UiKit.pill(this, "ENABLE", UiKit.RED);
            enable.setOnClickListener(v -> openNotificationSettings());
            row.addView(enable);
            warning.addView(row);
            content.addView(warning);
            content.addView(UiKit.gap(this, 14));
        }

        double income = database.getMonthlyIncome();
        double safe = database.getSafeToSpendRestOfMonth();
        double spent = database.getTotalSpentAedThisMonth();
        double upcoming = database.getUpcomingRecurringAedThisMonth();
        double projectedSpend = database.getProjectedMonthEndSpend();
        double projectedBalance = income > 0
                ? income - projectedSpend - database.getTotalSinkingMonthlyAllocation() : 0.0;

        LinearLayout hero = UiKit.card(this, 22);
        hero.setBackground(UiKit.gradient(this, 28,
                Color.rgb(28, 38, 78), Color.rgb(22, 28, 56), Color.rgb(28, 18, 54)));

        LinearLayout heroTop = new LinearLayout(this);
        heroTop.setOrientation(LinearLayout.HORIZONTAL);
        heroTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView heroLabel = UiKit.label(this, "SAFE TO SPEND");
        heroLabel.setTextColor(Color.rgb(189, 207, 255));
        heroTop.addView(heroLabel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        heroTop.addView(UiKit.pill(this, "REST OF MONTH", UiKit.CYAN));
        hero.addView(heroTop);

        TextView amount = UiKit.text(this,
                income > 0 ? money(safe) : "Set your income",
                income > 0 ? 40 : 28, UiKit.TEXT, true);
        amount.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        amount.setPadding(0, UiKit.dp(this, 12), 0, UiKit.dp(this, 5));
        hero.addView(amount);

        TextView desc = UiKit.text(this,
                income > 0
                        ? "After spending, upcoming bills and sinking-fund commitments."
                        : "Add monthly take-home income to unlock your live safe-to-spend number.",
                12, Color.rgb(190, 199, 220), false);
        hero.addView(desc);

        LinearLayout heroActions = new LinearLayout(this);
        heroActions.setOrientation(LinearLayout.HORIZONTAL);
        heroActions.setPadding(0, UiKit.dp(this, 18), 0, 0);
        TextView incomeAction = UiKit.pill(this, income > 0 ? "EDIT INCOME" : "SET INCOME", UiKit.CYAN);
        incomeAction.setOnClickListener(v -> showIncomeDialog());
        heroActions.addView(incomeAction);
        TextView addTx = UiKit.pill(this, "＋  ADD TRANSACTION", UiKit.PURPLE);
        LinearLayout.LayoutParams txLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        txLp.setMargins(UiKit.dp(this, 10), 0, 0, 0);
        heroActions.addView(addTx, txLp);
        addTx.setOnClickListener(v -> showManualTransactionDialog());
        hero.addView(heroActions);
        content.addView(hero);

        content.addView(UiKit.gap(this, 14));
        LinearLayout stats1 = new LinearLayout(this);
        stats1.setOrientation(LinearLayout.HORIZONTAL);
        stats1.addView(metricCard("SPENT", money(spent), "this month", UiKit.RED), weightWithEnd());
        stats1.addView(metricCard("BILLS", money(upcoming), "still upcoming", UiKit.AMBER), weightNoEnd());
        content.addView(stats1);

        content.addView(UiKit.gap(this, 10));
        LinearLayout stats2 = new LinearLayout(this);
        stats2.setOrientation(LinearLayout.HORIZONTAL);
        stats2.addView(metricCard("INCOME", income > 0 ? money(income) : "Not set", "monthly", UiKit.GREEN), weightWithEnd());
        stats2.addView(metricCard("FORECAST", income > 0 ? money(projectedBalance) : "—", "month-end balance", UiKit.PURPLE), weightNoEnd());
        content.addView(stats2);

        content.addView(UiKit.gap(this, 20));
        sectionRow("LOCAL INTELLIGENCE", "INSIGHTS", () -> { currentTab = 3; renderBottomNav(); renderCurrent(); });

        LinearLayout insight = UiKit.card(this, 18);
        LinearLayout insightTop = new LinearLayout(this);
        insightTop.setOrientation(LinearLayout.HORIZONTAL);
        insightTop.setGravity(Gravity.CENTER_VERTICAL);
        insightTop.addView(UiKit.pill(this, "✦  SMART PACE", UiKit.PURPLE));
        insight.addView(insightTop);
        TextView insightText = UiKit.text(this, database.getTopBudgetPaceInsight(), 15, UiKit.TEXT, true);
        insightText.setPadding(0, UiKit.dp(this, 12), 0, 0);
        insight.addView(insightText);
        content.addView(insight);

        content.addView(UiKit.gap(this, 20));
        sectionRow("ENVELOPES", "SEE ALL", () -> { currentTab = 1; renderBottomNav(); renderCurrent(); });
        addBudgetPreview();

        content.addView(UiKit.gap(this, 20));
        sectionRow("RECENT ACTIVITY", "SEE ALL", () -> { currentTab = 2; renderBottomNav(); renderCurrent(); });
        addRecentTransactions(5);
    }

    private LinearLayout.LayoutParams weightWithEnd() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(0, 0, UiKit.dp(this, 5), 0);
        return lp;
    }

    private LinearLayout.LayoutParams weightNoEnd() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(UiKit.dp(this, 5), 0, 0, 0);
        return lp;
    }

    private LinearLayout metricCard(String label, String value, String caption, int accent) {
        LinearLayout card = UiKit.card(this, 16);
        TextView l = UiKit.label(this, label);
        l.setTextColor(accent);
        card.addView(l);
        TextView v = UiKit.text(this, value, 19, UiKit.TEXT, true);
        v.setPadding(0, UiKit.dp(this, 8), 0, UiKit.dp(this, 3));
        card.addView(v);
        card.addView(UiKit.text(this, caption, 11, UiKit.MUTED, false));
        return card;
    }

    private void sectionRow(String title, String action, Runnable runnable) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = UiKit.label(this, title);
        row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView a = UiKit.text(this, action, 11, UiKit.CYAN, true);
        a.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 6), 0, UiKit.dp(this, 6));
        a.setOnClickListener(v -> runnable.run());
        row.addView(a);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, UiKit.dp(this, 10));
        content.addView(row, lp);
    }

    private void addBudgetPreview() {
        List<BudgetEnvelope> budgets = database.getBudgets();
        List<BudgetEnvelope> funded = new ArrayList<>();
        for (BudgetEnvelope b : budgets) {
            if (b.getEffectiveAmount() > 0 || database.getSpentForCategoryThisMonth(b.getName()) > 0) funded.add(b);
        }
        if (funded.isEmpty()) {
            LinearLayout empty = UiKit.card(this, 18);
            empty.addView(UiKit.text(this, "Your envelopes are waiting.", 16, UiKit.TEXT, true));
            TextView sub = UiKit.text(this, "Fund categories like groceries, eating out and travel to see live remaining balances.", 12, UiKit.MUTED, false);
            sub.setPadding(0, UiKit.dp(this, 7), 0, UiKit.dp(this, 12));
            empty.addView(sub);
            TextView btn = UiKit.pill(this, "SET UP BUDGETS", UiKit.CYAN);
            btn.setOnClickListener(v -> startActivity(new Intent(this, BudgetActivity.class)));
            empty.addView(btn);
            content.addView(empty);
            return;
        }

        int count = 0;
        for (BudgetEnvelope b : funded) {
            if (count++ >= 4) break;
            content.addView(budgetCard(b));
            content.addView(UiKit.gap(this, 9));
        }
    }

    private LinearLayout budgetCard(BudgetEnvelope b) {
        double spent = database.getSpentForCategoryThisMonth(b.getName());
        double total = b.getEffectiveAmount();
        double remaining = total - spent;
        double ratio = total > 0 ? spent / total : 0;
        int accent = remaining < 0 ? UiKit.RED : ratio > 0.8 ? UiKit.AMBER : UiKit.GREEN;

        LinearLayout card = UiKit.card(this, 16);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = UiKit.text(this, categoryGlyph(b.getName()) + "  " + b.getName(), 14, UiKit.TEXT, true);
        top.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (b.isSinkingFund()) top.addView(UiKit.pill(this, "SINKING", UiKit.PURPLE));
        else if (b.isRolloverEnabled()) top.addView(UiKit.pill(this, "ROLLOVER", UiKit.CYAN));
        card.addView(top);

        LinearLayout nums = new LinearLayout(this);
        nums.setOrientation(LinearLayout.HORIZONTAL);
        nums.setGravity(Gravity.CENTER_VERTICAL);
        nums.setPadding(0, UiKit.dp(this, 10), 0, UiKit.dp(this, 8));
        TextView spentTv = UiKit.text(this, money(spent) + " spent", 12, UiKit.MUTED, false);
        nums.addView(spentTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView remTv = UiKit.text(this, money(remaining) + " left", 12, accent, true);
        nums.addView(remTv);
        card.addView(nums);

        card.addView(UiKit.progress(this, total > 0 ? (int)(Math.min(1.0, ratio) * 1000) : 0, accent));
        card.setOnClickListener(v -> startActivity(new Intent(this, BudgetActivity.class)));
        return card;
    }

    private void addRecentTransactions(int max) {
        List<Transaction> txs = database.getAllTransactions();
        if (txs.isEmpty()) {
            LinearLayout empty = UiKit.card(this, 18);
            empty.addView(UiKit.text(this, "No activity yet", 15, UiKit.TEXT, true));
            TextView sub = UiKit.text(this, "Ruya and supported UAE bank alerts will appear here automatically.", 12, UiKit.MUTED, false);
            sub.setPadding(0, UiKit.dp(this, 6), 0, 0);
            empty.addView(sub);
            content.addView(empty);
            return;
        }

        LinearLayout card = UiKit.card(this, 6);
        int count = 0;
        for (Transaction t : txs) {
            if (count++ >= max) break;
            card.addView(transactionRow(t));
            if (count < Math.min(max, txs.size())) card.addView(UiKit.divider(this));
        }
        content.addView(card);
    }

    private View transactionRow(Transaction t) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 13), UiKit.dp(this, 10), UiKit.dp(this, 13));

        TextView badge = UiKit.text(this, categoryGlyph(t.getCategory()), 16, UiKit.TEXT, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(UiKit.round(this, UiKit.SURFACE_3, 15, UiKit.BORDER));
        row.addView(badge, new LinearLayout.LayoutParams(UiKit.dp(this, 42), UiKit.dp(this, 42)));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setPadding(UiKit.dp(this, 12), 0, UiKit.dp(this, 8), 0);
        TextView merchant = UiKit.text(this, t.getMerchant(), 14, UiKit.TEXT, true);
        center.addView(merchant);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d • h:mm a", Locale.getDefault());
        TextView meta = UiKit.text(this, t.getCategory() + "  ·  " + sdf.format(new Date(t.getTimestamp())), 10, UiKit.MUTED, false);
        meta.setPadding(0, UiKit.dp(this, 3), 0, 0);
        center.addView(meta);
        row.addView(center, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        boolean spend = isSpendingType(t.getTransactionType());
        boolean positive = "income".equals(t.getTransactionType()) || "refund".equals(t.getTransactionType());
        String prefix = spend ? "−" : positive ? "+" : "";
        TextView amount = UiKit.text(this,
                prefix + formatAmount(t.getCurrency(), t.getAmount()),
                13, spend ? UiKit.RED : positive ? UiKit.GREEN : UiKit.MUTED, true);
        row.addView(amount);
        row.setOnClickListener(v -> showEditTransactionDialog(t));
        return row;
    }

    private void renderBudgets() {
        addHeader("CONTROL CENTER", "Budget envelopes", "Give every dirham a job without making budgeting feel like a spreadsheet.");

        List<BudgetEnvelope> budgets = database.getBudgets();
        double allocated = 0, spent = 0, remaining = 0;
        for (BudgetEnvelope b : budgets) {
            if (b.getEffectiveAmount() <= 0) continue;
            allocated += b.getEffectiveAmount();
            double s = database.getSpentForCategoryThisMonth(b.getName());
            spent += s;
            remaining += b.getEffectiveAmount() - s;
        }

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.addView(metricCard("ALLOCATED", money(allocated), "this month", UiKit.CYAN), weightWithEnd());
        summary.addView(metricCard("REMAINING", money(remaining), "across envelopes", remaining < 0 ? UiKit.RED : UiKit.GREEN), weightNoEnd());
        content.addView(summary);
        content.addView(UiKit.gap(this, 10));

        LinearLayout second = new LinearLayout(this);
        second.setOrientation(LinearLayout.HORIZONTAL);
        second.addView(metricCard("SPENT", money(spent), "from envelopes", UiKit.RED), weightWithEnd());
        second.addView(metricCard("SINKING", money(database.getTotalSinkingMonthlyAllocation()), "monthly set-aside", UiKit.PURPLE), weightNoEnd());
        content.addView(second);

        content.addView(UiKit.gap(this, 18));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView manage = UiKit.pill(this, "＋  MANAGE ENVELOPES", UiKit.CYAN);
        manage.setOnClickListener(v -> startActivity(new Intent(this, BudgetActivity.class)));
        actions.addView(manage);
        content.addView(actions);
        content.addView(UiKit.gap(this, 16));

        boolean any = false;
        for (BudgetEnvelope b : budgets) {
            double s = database.getSpentForCategoryThisMonth(b.getName());
            if (b.getEffectiveAmount() <= 0 && s <= 0) continue;
            any = true;
            content.addView(budgetCard(b));
            content.addView(UiKit.gap(this, 9));
        }
        if (!any) {
            LinearLayout empty = UiKit.card(this, 20);
            empty.addView(UiKit.text(this, "No funded envelopes yet", 17, UiKit.TEXT, true));
            TextView t = UiKit.text(this, "Start with Groceries, Eating Out and Transport, then add sinking funds for bigger future expenses.", 13, UiKit.MUTED, false);
            t.setPadding(0, UiKit.dp(this, 8), 0, 0);
            empty.addView(t);
            content.addView(empty);
        }
    }

    private void renderTransactions() {
        addHeader("LEDGER", "Activity", "Every captured expense, transfer, income event and refund in one clean timeline.");

        List<Transaction> txs = database.getAllTransactions();
        int expenseCount = 0, incomeCount = 0, transferCount = 0;
        for (Transaction t : txs) {
            if (isSpendingType(t.getTransactionType())) expenseCount++;
            else if ("income".equals(t.getTransactionType()) || "refund".equals(t.getTransactionType())) incomeCount++;
            else if ("transfer_sent".equals(t.getTransactionType())) transferCount++;
        }

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(UiKit.pill(this, txs.size() + "  ALL", UiKit.CYAN));
        chips.addView(chipWithMargin(expenseCount + "  EXPENSES", UiKit.RED));
        chips.addView(chipWithMargin(incomeCount + "  INCOME / REFUNDS", UiKit.GREEN));
        chips.addView(chipWithMargin(transferCount + "  TRANSFERS", UiKit.PURPLE));
        hsv.addView(chips);
        content.addView(hsv);
        content.addView(UiKit.gap(this, 16));

        TextView add = UiKit.pill(this, "＋  ADD MANUALLY", UiKit.CYAN);
        add.setOnClickListener(v -> showManualTransactionDialog());
        content.addView(add);
        content.addView(UiKit.gap(this, 14));

        if (txs.isEmpty()) {
            LinearLayout empty = UiKit.card(this, 20);
            empty.addView(UiKit.text(this, "Nothing here yet", 17, UiKit.TEXT, true));
            empty.addView(UiKit.text(this, "Turn on Notification Access or add a transaction manually.", 12, UiKit.MUTED, false));
            content.addView(empty);
            return;
        }

        LinearLayout card = UiKit.card(this, 6);
        int count = 0;
        for (Transaction t : txs) {
            card.addView(transactionRow(t));
            count++;
            if (count < txs.size()) card.addView(UiKit.divider(this));
        }
        content.addView(card);
    }

    private TextView chipWithMargin(String text, int color) {
        TextView v = UiKit.pill(this, text, color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(UiKit.dp(this, 8), 0, 0, 0);
        v.setLayoutParams(lp);
        return v;
    }

    private void renderInsights() {
        addHeader("LOCAL INTELLIGENCE", "Insights", "Fast financial signals calculated entirely on-device from your own activity.");

        double income = database.getMonthlyIncome();
        double spent = database.getTotalSpentAedThisMonth();
        double forecast = database.getProjectedMonthEndSpend();

        Calendar now = Calendar.getInstance();
        int day = Math.max(1, now.get(Calendar.DAY_OF_MONTH));
        double dailyAverage = spent / day;

        LinearLayout hero = UiKit.card(this, 20);
        hero.setBackground(UiKit.gradient(this, 26, Color.rgb(18, 41, 61), Color.rgb(28, 24, 58)));
        hero.addView(UiKit.pill(this, "✦  MONTHLY SIGNAL", UiKit.CYAN));
        TextView msg = UiKit.text(this, database.getTopBudgetPaceInsight(), 18, UiKit.TEXT, true);
        msg.setPadding(0, UiKit.dp(this, 13), 0, UiKit.dp(this, 8));
        hero.addView(msg);
        String forecastText = income > 0
                ? "At the current pace, projected total outflow is " + money(forecast) + "."
                : "Set monthly income to unlock affordability and month-end balance forecasts.";
        hero.addView(UiKit.text(this, forecastText, 12, Color.rgb(190, 201, 220), false));
        content.addView(hero);

        content.addView(UiKit.gap(this, 12));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(metricCard("DAILY AVG", money(dailyAverage), "spent per day", UiKit.AMBER), weightWithEnd());
        row.addView(metricCard("FORECAST", money(forecast), "projected outflow", UiKit.PURPLE), weightNoEnd());
        content.addView(row);

        content.addView(UiKit.gap(this, 20));
        sectionRow("CATEGORY PRESSURE", "BUDGETS", () -> { currentTab = 1; renderBottomNav(); renderCurrent(); });

        List<BudgetEnvelope> budgets = database.getBudgets();
        List<CategorySpend> categories = new ArrayList<>();
        double max = 0;
        for (BudgetEnvelope b : budgets) {
            double s = database.getSpentForCategoryThisMonth(b.getName());
            if (s <= 0) continue;
            categories.add(new CategorySpend(b.getName(), s));
            if (s > max) max = s;
        }
        Collections.sort(categories, (a,b) -> Double.compare(b.amount, a.amount));

        if (categories.isEmpty()) {
            LinearLayout empty = UiKit.card(this, 18);
            empty.addView(UiKit.text(this, "Not enough spending data yet", 15, UiKit.TEXT, true));
            empty.addView(UiKit.text(this, "Your top categories will appear here as transactions accumulate.", 12, UiKit.MUTED, false));
            content.addView(empty);
        } else {
            LinearLayout chart = UiKit.card(this, 18);
            int limit = Math.min(6, categories.size());
            for (int i = 0; i < limit; i++) {
                CategorySpend cs = categories.get(i);
                LinearLayout label = new LinearLayout(this);
                label.setOrientation(LinearLayout.HORIZONTAL);
                TextView name = UiKit.text(this, categoryGlyph(cs.name) + "  " + cs.name, 12, UiKit.TEXT, true);
                label.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                label.addView(UiKit.text(this, money(cs.amount), 12, UiKit.MUTED, true));
                chart.addView(label);
                chart.addView(UiKit.gap(this, 6));
                int p = max > 0 ? (int)((cs.amount / max) * 1000) : 0;
                chart.addView(UiKit.progress(this, p, i == 0 ? UiKit.RED : UiKit.ACCENT));
                if (i < limit - 1) chart.addView(UiKit.gap(this, 14));
            }
            content.addView(chart);
        }

        content.addView(UiKit.gap(this, 18));
        sectionRow("UPCOMING COMMITMENTS", "MANAGE", () -> startActivity(new Intent(this, RecurringActivity.class)));
        List<RecurringBill> bills = database.getRecurringBills();
        LinearLayout billsCard = UiKit.card(this, 8);
        if (bills.isEmpty()) {
            TextView empty = UiKit.text(this, "No recurring bills configured.", 12, UiKit.MUTED, false);
            empty.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 14), UiKit.dp(this, 10), UiKit.dp(this, 14));
            billsCard.addView(empty);
        } else {
            int shown = 0;
            for (RecurringBill b : bills) {
                if (!b.isActive()) continue;
                LinearLayout br = new LinearLayout(this);
                br.setOrientation(LinearLayout.HORIZONTAL);
                br.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 12), UiKit.dp(this, 10), UiKit.dp(this, 12));
                LinearLayout bc = new LinearLayout(this);
                bc.setOrientation(LinearLayout.VERTICAL);
                bc.addView(UiKit.text(this, b.getName(), 13, UiKit.TEXT, true));
                bc.addView(UiKit.text(this, "Due day " + b.getDueDay() + "  ·  " + b.getCategory(), 10, UiKit.MUTED, false));
                br.addView(bc, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                br.addView(UiKit.text(this, formatAmount(b.getCurrency(), b.getAmount()), 12, UiKit.AMBER, true));
                billsCard.addView(br);
                shown++;
                if (shown < bills.size()) billsCard.addView(UiKit.divider(this));
                if (shown >= 5) break;
            }
        }
        content.addView(billsCard);
    }

    private static class CategorySpend {
        String name; double amount;
        CategorySpend(String name, double amount) { this.name = name; this.amount = amount; }
    }

    private void renderSettings() {
        addHeader("SYSTEM", "Settings", "Tune the money engine, capture rules and local privacy controls.");

        LinearLayout privacy = UiKit.card(this, 18);
        privacy.setBackground(UiKit.gradient(this, 24, Color.rgb(15, 45, 45), Color.rgb(16, 29, 47)));
        privacy.addView(UiKit.pill(this, "✓  PRIVATE BY DESIGN", UiKit.GREEN));
        TextView pTitle = UiKit.text(this, "Your data stays on this phone.", 17, UiKit.TEXT, true);
        pTitle.setPadding(0, UiKit.dp(this, 12), 0, UiKit.dp(this, 6));
        privacy.addView(pTitle);
        privacy.addView(UiKit.text(this, "No INTERNET permission. Transactions, budgets and forecasts are stored locally in SQLite.", 12, Color.rgb(187, 211, 207), false));
        content.addView(privacy);

        content.addView(UiKit.gap(this, 18));
        sectionRow("MONEY", "", () -> {});
        content.addView(settingRow("Monthly income", database.getMonthlyIncome() > 0 ? money(database.getMonthlyIncome()) : "Not set", UiKit.GREEN, this::showIncomeDialog));
        content.addView(UiKit.gap(this, 8));
        content.addView(settingRow("Budget envelopes", "Allocations, rollover & sinking funds", UiKit.CYAN,
                () -> startActivity(new Intent(this, BudgetActivity.class))));
        content.addView(UiKit.gap(this, 8));
        content.addView(settingRow("Recurring bills", database.getRecurringBills().size() + " configured", UiKit.AMBER,
                () -> startActivity(new Intent(this, RecurringActivity.class))));

        content.addView(UiKit.gap(this, 18));
        sectionRow("CAPTURE", "", () -> {});
        content.addView(settingRow("Notification access",
                isNotificationServiceEnabled() ? "Enabled • listening locally" : "Disabled",
                isNotificationServiceEnabled() ? UiKit.GREEN : UiKit.RED,
                this::openNotificationSettings));
        content.addView(UiKit.gap(this, 8));
        content.addView(settingRow("Test Ruya parser", "Inject one local sample purchase", UiKit.PURPLE, this::runSampleNotificationTest));
        content.addView(UiKit.gap(this, 8));
        content.addView(settingRow("Add manual transaction", "Expense, income, transfer or refund", UiKit.CYAN, this::showManualTransactionDialog));

        content.addView(UiKit.gap(this, 18));
        sectionRow("DATA", "", () -> {});
        content.addView(settingRow("Clear transaction history", "Budgets and settings stay intact", UiKit.RED, this::confirmClearTransactions));

        content.addView(UiKit.gap(this, 20));
        TextView version = UiKit.text(this, "SpendWatch Local 4.0  •  UAE-first  •  offline", 11, UiKit.MUTED, false);
        version.setGravity(Gravity.CENTER);
        content.addView(version);
    }

    private LinearLayout settingRow(String title, String subtitle, int accent, Runnable action) {
        LinearLayout row = UiKit.card(this, 15);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView dot = UiKit.text(this, "●", 12, accent, true);
        row.addView(dot);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(UiKit.dp(this, 12), 0, UiKit.dp(this, 8), 0);
        copy.addView(UiKit.text(this, title, 14, UiKit.TEXT, true));
        TextView sub = UiKit.text(this, subtitle, 11, UiKit.MUTED, false);
        sub.setPadding(0, UiKit.dp(this, 3), 0, 0);
        copy.addView(sub);
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(UiKit.text(this, "›", 25, UiKit.MUTED, false));
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private boolean isNotificationServiceEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    private void openNotificationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Open Settings > Notification access and enable SpendWatch.", Toast.LENGTH_LONG).show();
        }
    }

    private void showIncomeDialog() {
        LinearLayout wrapper = dialogForm();
        EditText input = darkInput("Monthly take-home income in AED");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        double current = database.getMonthlyIncome();
        if (current > 0) input.setText(String.format(Locale.US, "%.2f", current));
        wrapper.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Monthly income")
                .setMessage("Used locally for safe-to-spend and month-end forecasting.")
                .setView(wrapper)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        database.setMonthlyIncome(Double.parseDouble(input.getText().toString()));
                        renderCurrent();
                    } catch (Exception e) {
                        Toast.makeText(this, "Enter a valid amount.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
    }

    private LinearLayout dialogForm() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(UiKit.dp(this, 24), UiKit.dp(this, 8), UiKit.dp(this, 24), 0);
        return form;
    }

    private EditText darkInput(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(112, 128, 151));
        e.setTextColor(UiKit.TEXT);
        e.setSingleLine(true);
        return e;
    }

    private List<String> categoryNames() {
        List<String> names = new ArrayList<>();
        for (BudgetEnvelope b : database.getBudgets()) names.add(b.getName());
        if (!names.contains("Income")) names.add("Income");
        if (!names.contains("Refunds")) names.add("Refunds");
        if (!names.contains("Transfers")) names.add("Transfers");
        return names;
    }

    private void showManualTransactionDialog() {
        LinearLayout form = dialogForm();

        EditText merchant = darkInput("Merchant / description");
        form.addView(merchant);

        EditText amount = darkInput("Amount in AED");
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
                .setTitle("Add transaction")
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
                        renderCurrent();
                    } catch (Exception e) {
                        Toast.makeText(this, "Enter a valid amount.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditTransactionDialog(Transaction t) {
        LinearLayout form = dialogForm();

        TextView raw = UiKit.text(this, t.getRawMessage(), 11, UiKit.MUTED, false);
        raw.setPadding(0, 0, 0, UiKit.dp(this, 12));
        form.addView(raw);

        List<String> categories = categoryNames();
        Spinner category = new Spinner(this);
        category.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories));
        category.setSelection(Math.max(0, categories.indexOf(t.getCategory())));
        form.addView(category);

        String[] types = new String[]{"card_purchase","pos","online","atm","direct_debit","fee","other_expense","transfer_sent","income","refund"};
        Spinner type = new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types));
        int typeIndex = 0;
        for (int i = 0; i < types.length; i++) if (types[i].equals(t.getTransactionType())) typeIndex = i;
        type.setSelection(typeIndex);
        form.addView(type);

        CheckBox ignored = new CheckBox(this);
        ignored.setText("Ignore in calculations");
        ignored.setTextColor(UiKit.TEXT);
        ignored.setChecked(t.isIgnored());
        form.addView(ignored);

        new AlertDialog.Builder(this)
                .setTitle("Edit transaction")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    database.updateTransaction(t.getId(),
                            categories.get(category.getSelectedItemPosition()),
                            types[type.getSelectedItemPosition()],
                            ignored.isChecked());
                    renderCurrent();
                })
                .setNeutralButton("Delete", (d, w) -> {
                    database.deleteTransaction(t.getId());
                    renderCurrent();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearTransactions() {
        new AlertDialog.Builder(this)
                .setTitle("Clear transaction history?")
                .setMessage("This removes captured transactions from this phone. Budgets, recurring bills and settings remain.")
                .setPositiveButton("Clear", (d, w) -> {
                    database.clearAll();
                    renderCurrent();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void runSampleNotificationTest() {
        Transaction t = BankTransactionParser.parseNotification(
                "Ruya",
                "Your Ruya card ending 4091 was used for AED 142.50 at CARREFOUR DEIRA.",
                "ae.ruya.digital");
        if (t == null) return;
        t.setTimestamp(System.currentTimeMillis());
        long id = database.insertTransaction(t);
        Toast.makeText(this, id > 0 ? "Sample Ruya purchase added." : "Duplicate sample ignored.", Toast.LENGTH_SHORT).show();
        renderCurrent();
    }

    private boolean isSpendingType(String type) {
        return "card_purchase".equals(type) || "pos".equals(type) || "online".equals(type)
                || "atm".equals(type) || "direct_debit".equals(type)
                || "fee".equals(type) || "other_expense".equals(type);
    }

    private String categoryGlyph(String category) {
        if (category == null) return "•";
        String c = category.toLowerCase(Locale.ROOT);
        if (c.contains("grocer")) return "◆";
        if (c.contains("food") || c.contains("dining")) return "●";
        if (c.contains("transport") || c.contains("fuel")) return "▲";
        if (c.contains("shopping")) return "■";
        if (c.contains("entertain")) return "✦";
        if (c.contains("health") || c.contains("pharmacy")) return "＋";
        if (c.contains("fitness")) return "◇";
        if (c.contains("cash") || c.contains("atm")) return "◉";
        if (c.contains("fee")) return "!";
        if (c.contains("income")) return "↑";
        if (c.contains("refund")) return "↺";
        if (c.contains("transfer")) return "⇄";
        return "•";
    }

    private String money(double value) {
        return String.format(Locale.US, "AED %,.2f", value);
    }

    private String formatAmount(String currency, double value) {
        return String.format(Locale.US, "%s %,.2f", currency == null ? "AED" : currency, value);
    }
}
