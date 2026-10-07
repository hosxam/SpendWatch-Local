package com.hossam.spendwatch;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecurringActivity extends Activity {

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
        title.setText("Recurring bills");
        title.setTextSize(25);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xFF0F172A);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("Add rent, phone, subscriptions, insurance, gym, and other predictable commitments.");
        desc.setTextColor(0xFF64748B);
        desc.setPadding(0, 6, 0, 14);
        root.addView(desc);

        Button add = new Button(this);
        add.setText("Add recurring bill");
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
        List<RecurringBill> bills = database.getRecurringBills();
        if (bills.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No recurring bills added yet.");
            empty.setTextColor(0xFF64748B);
            empty.setPadding(12, 20, 12, 20);
            list.addView(empty);
            return;
        }

        for (RecurringBill b : bills) {
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

            TextView detail = new TextView(this);
            detail.setText(String.format(Locale.US, "%s %.2f  •  due day %d  •  %s%s",
                    b.getCurrency(), b.getAmount(), b.getDueDay(), b.getCategory(),
                    b.isActive() ? "" : "  •  paused"));
            detail.setTextColor(0xFF64748B);
            detail.setPadding(0, 6, 0, 0);
            item.addView(detail);

            item.setOnClickListener(v -> showDialog(b));
            list.addView(item);
        }
    }

    private List<String> categories() {
        List<String> out = new ArrayList<>();
        for (BudgetEnvelope b : database.getBudgets()) out.add(b.getName());
        if (out.isEmpty()) out.add("General");
        return out;
    }

    private void showDialog(RecurringBill existing) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(36, 8, 36, 0);

        EditText name = new EditText(this);
        name.setHint("Bill name");
        if (existing != null) name.setText(existing.getName());
        form.addView(name);

        EditText amount = new EditText(this);
        amount.setHint("Amount in AED");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (existing != null) amount.setText(String.format(Locale.US, "%.2f", existing.getAmount()));
        form.addView(amount);

        EditText due = new EditText(this);
        due.setHint("Due day (1-31)");
        due.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (existing != null) due.setText(String.valueOf(existing.getDueDay()));
        form.addView(due);

        List<String> cats = categories();
        Spinner category = new Spinner(this);
        category.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cats));
        if (existing != null) {
            int idx = cats.indexOf(existing.getCategory());
            if (idx >= 0) category.setSelection(idx);
        }
        form.addView(category);

        CheckBox active = new CheckBox(this);
        active.setText("Active");
        active.setChecked(existing == null || existing.isActive());
        form.addView(active);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add recurring bill" : "Edit recurring bill")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        String n = name.getText().toString().trim();
                        if (n.isEmpty()) throw new IllegalArgumentException();
                        double a = Double.parseDouble(amount.getText().toString());
                        int day = Integer.parseInt(due.getText().toString());
                        if (day < 1 || day > 31) throw new IllegalArgumentException();
                        RecurringBill bill = new RecurringBill(
                                existing == null ? 0 : existing.getId(),
                                n, a, "AED", day,
                                cats.get(category.getSelectedItemPosition()),
                                active.isChecked()
                        );
                        database.saveRecurringBill(bill);
                        refresh();
                    } catch (Exception e) {
                        Toast.makeText(this, "Enter a valid name, amount, and due day.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null);

        if (existing != null) {
            builder.setNeutralButton("Delete", (d, w) -> {
                database.deleteRecurringBill(existing.getId());
                refresh();
            });
        }
        builder.show();
    }
}
