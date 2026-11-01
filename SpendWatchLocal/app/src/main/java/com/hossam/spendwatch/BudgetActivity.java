package com.hossam.spendwatch;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class BudgetActivity extends Activity {

    private SpendDatabase database;
    private LinearLayout list;
    private TextView totalAllocated;
    private TextView totalRemaining;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(UiKit.BG);
        getWindow().setNavigationBarColor(UiKit.BG);
        database = SpendDatabase.getInstance(this);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 18), UiKit.dp(this, 20), UiKit.dp(this, 30));
        root.setBackgroundColor(UiKit.BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = UiKit.pill(this, "‹  BACK", UiKit.CYAN);
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView status = UiKit.pill(this, "ENVELOPE ENGINE", UiKit.PURPLE);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusLp.setMargins(UiKit.dp(this, 8), 0, 0, 0);
        top.addView(status, statusLp);
        root.addView(top);

        TextView title = UiKit.text(this, "Budget control", 30, UiKit.TEXT, true);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        title.setPadding(0, UiKit.dp(this, 15), 0, 0);
        root.addView(title);

        TextView desc = UiKit.text(this,
                "Allocate money intentionally. Rollover keeps unused balance; sinking funds build reserves automatically.",
                13, UiKit.MUTED, false);
        desc.setPadding(0, UiKit.dp(this, 6), 0, UiKit.dp(this, 18));
        root.addView(desc);

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout allocatedCard = statCard("ALLOCATED", UiKit.CYAN);
        totalAllocated = (TextView) allocatedCard.getChildAt(1);
        summary.addView(allocatedCard, weight(true));
        LinearLayout remainingCard = statCard("REMAINING", UiKit.GREEN);
        totalRemaining = (TextView) remainingCard.getChildAt(1);
        summary.addView(remainingCard, weight(false));
        root.addView(summary);

        root.addView(UiKit.gap(this, 16));

        TextView add = UiKit.pill(this, "＋  NEW ENVELOPE", UiKit.CYAN);
        add.setOnClickListener(v -> showDialog(null));
        root.addView(add);
        root.addView(UiKit.gap(this, 12));

        LinearLayout cycleBar = new LinearLayout(this);
        cycleBar.setOrientation(LinearLayout.HORIZONTAL);
        cycleBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView day = UiKit.pill(this, "CYCLE START: DAY " + database.getBudgetStartDay(), UiKit.CYAN);
        day.setOnClickListener(v -> showStartDayPicker());
        cycleBar.addView(day);
        TextView reset = UiKit.pill(this, "RESET ENVELOPES", UiKit.AMBER);
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        resetLp.setMargins(UiKit.dp(this, 10), 0, 0, 0);
        reset.setLayoutParams(resetLp);
        reset.setOnClickListener(v -> confirmResetEnvelopes());
        cycleBar.addView(reset);
        root.addView(cycleBar);

        root.addView(UiKit.gap(this, 16));
        TextView section = UiKit.label(this, "YOUR ENVELOPES");
        root.addView(section);
        root.addView(UiKit.gap(this, 9));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        scroll.addView(root);
        setContentView(scroll);
    }

    private LinearLayout.LayoutParams weight(boolean left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (left) lp.setMargins(0, 0, UiKit.dp(this, 5), 0);
        else lp.setMargins(UiKit.dp(this, 5), 0, 0, 0);
        return lp;
    }

    private LinearLayout statCard(String label, int accent) {
        LinearLayout card = UiKit.card(this, 16);
        TextView l = UiKit.label(this, label);
        l.setTextColor(accent);
        card.addView(l);
        TextView value = UiKit.text(this, "AED 0.00", 20, UiKit.TEXT, true);
        value.setPadding(0, UiKit.dp(this, 8), 0, 0);
        card.addView(value);
        return card;
    }

    private void refresh() {
        if (list == null) return;
        list.removeAllViews();
        List<BudgetEnvelope> budgets = database.getBudgets();
        double allocated = 0, remainingTotal = 0;

        for (BudgetEnvelope b : budgets) {
            if (b.getEffectiveAmount() > 0) allocated += b.getEffectiveAmount();
            double spent = database.getSpentForCategoryThisMonth(b.getName());
            if (b.getEffectiveAmount() > 0) remainingTotal += b.getEffectiveAmount() - spent;
        }
        totalAllocated.setText(money(allocated));
        totalRemaining.setText(money(remainingTotal));
        totalRemaining.setTextColor(remainingTotal < 0 ? UiKit.RED : UiKit.TEXT);

        for (BudgetEnvelope b : budgets) {
            double spent = database.getSpentForCategoryThisMonth(b.getName());
            double total = b.getEffectiveAmount();
            double remaining = total - spent;
            double ratio = total > 0 ? spent / total : 0;
            int accent = remaining < 0 ? UiKit.RED : ratio > .8 ? UiKit.AMBER : UiKit.GREEN;

            LinearLayout item = UiKit.card(this, 17);

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = UiKit.text(this, glyph(b.getName()) + "  " + b.getName(),
                    15, UiKit.TEXT, true);
            header.addView(name, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            if (b.isSinkingFund()) header.addView(UiKit.pill(this, "SINKING", UiKit.PURPLE));
            else if (b.isRolloverEnabled()) header.addView(UiKit.pill(this, "ROLLOVER", UiKit.CYAN));
            item.addView(header);

            LinearLayout visual = new LinearLayout(this);
            visual.setOrientation(LinearLayout.HORIZONTAL);
            visual.setGravity(Gravity.CENTER_VERTICAL);
            visual.setPadding(0, UiKit.dp(this, 13), 0, 0);

            DonutProgressView donut = new DonutProgressView(this);
            donut.configure(Math.max(0, remaining), total, "LEFT", accent);
            donut.setContentDescription(b.getName() + ": " + donut.getDisplayedPercent()
                    + " percent remaining");
            visual.addView(donut, new LinearLayout.LayoutParams(
                    UiKit.dp(this, 108), UiKit.dp(this, 108)));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(UiKit.dp(this, 16), 0, 0, 0);
            int usedPercent = total > 0 ? (int) Math.round(spent / total * 100) : 0;
            info.addView(UiKit.text(this,
                    total > 0 ? usedPercent + "% USED" : "SET A BUDGET",
                    12, UiKit.CYAN, true));
            info.addView(UiKit.gap(this, 8));
            info.addView(UiKit.text(this, money(spent) + " spent", 13, UiKit.TEXT, true));
            info.addView(UiKit.gap(this, 5));
            info.addView(UiKit.text(this,
                    remaining < 0 ? money(-remaining) + " over budget" :
                            money(remaining) + " remaining",
                    13, accent, true));
            info.addView(UiKit.gap(this, 5));
            info.addView(UiKit.text(this,
                    "Monthly " + money(b.getMonthlyAmount())
                            + (b.getCarryAmount() > 0
                            ? "  ·  carried " + money(b.getCarryAmount()) : ""),
                    10, UiKit.MUTED, false));
            visual.addView(info, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            item.addView(visual);

            LinearLayout reorder = new LinearLayout(this);
            reorder.setOrientation(LinearLayout.HORIZONTAL);
            reorder.setGravity(Gravity.CENTER_VERTICAL);
            reorder.setPadding(0, UiKit.dp(this, 12), 0, 0);
            TextView moveUp = UiKit.pill(this, "↑  MOVE UP", UiKit.CYAN);
            moveUp.setContentDescription("Move " + b.getName() + " up");
            moveUp.setOnClickListener(v -> {
                database.moveBudget(b.getId(), -1);
                refresh();
            });
            reorder.addView(moveUp);
            TextView moveDown = UiKit.pill(this, "↓  MOVE DOWN", UiKit.CYAN);
            moveDown.setContentDescription("Move " + b.getName() + " down");
            LinearLayout.LayoutParams downLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            downLp.setMargins(UiKit.dp(this, 10), 0, 0, 0);
            reorder.addView(moveDown, downLp);
            moveDown.setOnClickListener(v -> {
                database.moveBudget(b.getId(), 1);
                refresh();
            });
            TextView edit = UiKit.pill(this, "EDIT", UiKit.PURPLE);
            LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            editLp.setMargins(UiKit.dp(this, 10), 0, 0, 0);
            reorder.addView(edit, editLp);
            edit.setOnClickListener(v -> showDialog(b));
            item.addView(reorder);
            item.setOnClickListener(v -> showDialog(b));
            list.addView(item);
            list.addView(UiKit.gap(this, 9));
        }
    }

    private void confirmResetEnvelopes() {
        new AlertDialog.Builder(this)
                .setTitle("Start a fresh envelope period?")
                .setMessage("Spent amounts for envelopes restart from zero and carried unused money resets. "
                        + "Your monthly allocations remain. Every previous transaction is kept, "
                        + "and old envelope balances are recorded in Reset History / Excel.")
                .setPositiveButton("Reset envelopes", (dialog, which) -> {
                    database.resetBudgetsPreservingHistory();
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showStartDayPicker() {
        EditText input = input("Start day 1 to 31");
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(database.getBudgetStartDay()));
        LinearLayout frame = new LinearLayout(this);
        frame.setPadding(UiKit.dp(this, 24), 0, UiKit.dp(this, 24), 0);
        frame.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("Monthly budget cycle")
                .setMessage("Choose the day your budget month begins. For short months, "
                        + "days 29–31 are treated as the last day. All historical "
                        + "transactions remain untouched.")
                .setView(frame)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        int value = Integer.parseInt(input.getText().toString().trim());
                        database.setBudgetStartDay(value);
                        recreate();
                    } catch (Exception ex) {
                        Toast.makeText(this, "Choose a day from 1 to 31.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDialog(BudgetEnvelope existing) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(UiKit.dp(this, 24), 0, UiKit.dp(this, 24), 0);

        EditText name = input("Envelope name");
        if (existing != null) name.setText(existing.getName());
        form.addView(name);

        EditText amount = input("Monthly amount in AED");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (existing != null) amount.setText(String.format(Locale.US, "%.2f", existing.getMonthlyAmount()));
        form.addView(amount);

        CheckBox rollover = check("Rollover unused money");
        rollover.setChecked(existing != null && existing.isRolloverEnabled());
        form.addView(rollover);

        CheckBox sinking = check("Sinking fund — accumulate month to month");
        sinking.setChecked(existing != null && existing.isSinkingFund());
        form.addView(sinking);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "New envelope" : "Edit envelope")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) {
                        Toast.makeText(this, "Envelope name is required.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double a = 0;
                    try { a = Double.parseDouble(amount.getText().toString()); } catch (Exception ignored) {}
                    database.saveBudget(new BudgetEnvelope(
                            existing == null ? 0 : existing.getId(),
                            n, a,
                            rollover.isChecked() || sinking.isChecked(),
                            existing == null ? 0 : existing.getCarryAmount(),
                            sinking.isChecked()));
                    refresh();
                })
                .setNegativeButton("Cancel", null);

        if (existing != null) {
            builder.setNeutralButton("Delete", (d, w) -> {
                database.deleteBudget(existing.getId());
                refresh();
            });
        }
        builder.show();
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(112, 128, 151));
        e.setTextColor(UiKit.TEXT);
        e.setSingleLine(true);
        return e;
    }

    private CheckBox check(String text) {
        CheckBox c = new CheckBox(this);
        c.setText(text);
        c.setTextColor(UiKit.TEXT);
        return c;
    }

    private String money(double v) {
        return String.format(Locale.US, "AED %,.2f", v);
    }

    private String glyph(String name) {
        String c = name.toLowerCase(Locale.ROOT);
        if (c.contains("grocer")) return "◆";
        if (c.contains("food")) return "●";
        if (c.contains("transport")) return "▲";
        if (c.contains("shopping")) return "■";
        if (c.contains("entertain")) return "✦";
        if (c.contains("health")) return "＋";
        if (c.contains("fitness")) return "◇";
        if (c.contains("cash")) return "◉";
        return "•";
    }
}
