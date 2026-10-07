package com.hossam.spendwatch;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = SpendDatabase.getInstance(this);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private GradientDrawable cardBg() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xFFFFFFFF);
        gd.setCornerRadius(28f);
        gd.setStroke(1, 0xFFE2E8F0);
        return gd;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 48);
        root.setBackgroundColor(0xFFF8FAFC);

        TextView title = new TextView(this);
        title.setText("Budget envelopes");
        title.setTextSize(25);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xFF0F172A);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("Allocate monthly money to categories. Rollover keeps unused money; sinking funds accumulate it.");
        desc.setTextColor(0xFF64748B);
        desc.setPadding(0, 6, 0, 14);
        root.addView(desc);

        Button add = new Button(this);
        add.setText("Add envelope");
        add.setAllCaps(false);
        add.setOnClickListener(v -> showDialog(null));
        root.addView(add);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 14, 0, 0);
        root.addView(list);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void refresh() {
        list.removeAllViews();
        List<BudgetEnvelope> budgets = database.getBudgets();
        for (BudgetEnvelope b : budgets) {
            double spent = database.getSpentForCategoryThisMonth(b.getName());
            double remaining = b.getEffectiveAmount() - spent;

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(24, 18, 24, 18);
            item.setBackground(cardBg());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 10);
            item.setLayoutParams(lp);

            TextView name = new TextView(this);
            name.setText(b.getName());
            name.setTextSize(16);
            name.setTypeface(null, Typeface.BOLD);
            name.setTextColor(0xFF0F172A);
            item.addView(name);

            TextView amount = new TextView(this);
            amount.setText(String.format(Locale.US,
                    "Monthly AED %.2f  •  Carry AED %.2f  •  Effective AED %.2f",
                    b.getMonthlyAmount(), b.getCarryAmount(), b.getEffectiveAmount()));
            amount.setTextSize(12);
            amount.setTextColor(0xFF64748B);
            item.addView(amount);

            TextView status = new TextView(this);
            status.setText(String.format(Locale.US,
                    "Spent AED %.2f  •  Remaining AED %.2f%s%s",
                    spent, remaining,
                    b.isRolloverEnabled() ? "  •  Rollover" : "",
                    b.isSinkingFund() ? "  •  Sinking fund" : ""));
            status.setTextColor(remaining < 0 ? 0xFFDC2626 : 0xFF059669);
            status.setPadding(0, 6, 0, 0);
            item.addView(status);

            item.setOnClickListener(v -> showDialog(b));
            list.addView(item);
        }
    }

    private void showDialog(BudgetEnvelope existing) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(36, 8, 36, 0);

        EditText name = new EditText(this);
        name.setHint("Envelope name");
        if (existing != null) name.setText(existing.getName());
        form.addView(name);

        EditText amount = new EditText(this);
        amount.setHint("Monthly amount in AED");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (existing != null) amount.setText(String.format(Locale.US, "%.2f", existing.getMonthlyAmount()));
        form.addView(amount);

        CheckBox rollover = new CheckBox(this);
        rollover.setText("Rollover unused money");
        rollover.setChecked(existing != null && existing.isRolloverEnabled());
        form.addView(rollover);

        CheckBox sinking = new CheckBox(this);
        sinking.setText("Sinking fund (accumulates month to month)");
        sinking.setChecked(existing != null && existing.isSinkingFund());
        form.addView(sinking);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add envelope" : "Edit envelope")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) {
                        Toast.makeText(this, "Envelope name is required.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double a = 0.0;
                    try { a = Double.parseDouble(amount.getText().toString()); } catch (Exception ignored) {}
                    BudgetEnvelope b = new BudgetEnvelope(
                            existing == null ? 0 : existing.getId(),
                            n, a, rollover.isChecked() || sinking.isChecked(),
                            existing == null ? 0.0 : existing.getCarryAmount(),
                            sinking.isChecked()
                    );
                    database.saveBudget(b);
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
}
