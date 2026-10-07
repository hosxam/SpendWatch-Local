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
            TextView name = UiKit.text(this, glyph(b.getName()) + "  " + b.getName(), 15, UiKit.TEXT, true);
            header.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            if (b.isSinkingFund()) header.addView(UiKit.pill(this, "SINKING", UiKit.PURPLE));
            else if (b.isRolloverEnabled()) header.addView(UiKit.pill(this, "ROLLOVER", UiKit.CYAN));
            item.addView(header);

            LinearLayout nums = new LinearLayout(this);
            nums.setOrientation(LinearLayout.HORIZONTAL);
            nums.setGravity(Gravity.CENTER_VERTICAL);
            nums.setPadding(0, UiKit.dp(this, 12), 0, UiKit.dp(this, 7));
            nums.addView(UiKit.text(this, money(spent) + " spent", 12, UiKit.MUTED, false),
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            nums.addView(UiKit.text(this, money(remaining) + " left", 12, accent, true));
            item.addView(nums);

            int progress = total > 0 ? (int)(Math.min(1.0, ratio) * 1000) : 0;
            item.addView(UiKit.progress(this, progress, accent));

            TextView meta = UiKit.text(this,
                    "Monthly " + money(b.getMonthlyAmount())
                            + (b.getCarryAmount() > 0 ? "  ·  carried " + money(b.getCarryAmount()) : ""),
                    10, UiKit.MUTED, false);
            meta.setPadding(0, UiKit.dp(this, 9), 0, 0);
            item.addView(meta);

            item.setOnClickListener(v -> showDialog(b));
            list.addView(item);
            list.addView(UiKit.gap(this, 9));
        }
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
