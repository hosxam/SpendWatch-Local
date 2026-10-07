package com.hossam.spendwatch;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class RecurringActivity extends Activity {

    private SpendDatabase database;
    private LinearLayout list;
    private TextView upcomingTotal;
    private TextView billCount;

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
        TextView badge = UiKit.pill(this, "COMMITMENTS", UiKit.AMBER);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeLp.setMargins(UiKit.dp(this, 8), 0, 0, 0);
        top.addView(badge, badgeLp);
        root.addView(top);

        TextView title = UiKit.text(this, "Recurring bills", 30, UiKit.TEXT, true);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        title.setPadding(0, UiKit.dp(this, 15), 0, 0);
        root.addView(title);

        TextView desc = UiKit.text(this,
                "Reserve money before bills hit. Add rent, subscriptions, insurance, phone plans and predictable commitments.",
                13, UiKit.MUTED, false);
        desc.setPadding(0, UiKit.dp(this, 6), 0, UiKit.dp(this, 18));
        root.addView(desc);

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout upcoming = statCard("UPCOMING", UiKit.AMBER);
        upcomingTotal = (TextView) upcoming.getChildAt(1);
        summary.addView(upcoming, weight(true));
        LinearLayout count = statCard("ACTIVE BILLS", UiKit.CYAN);
        billCount = (TextView) count.getChildAt(1);
        summary.addView(count, weight(false));
        root.addView(summary);

        root.addView(UiKit.gap(this, 16));
        TextView add = UiKit.pill(this, "＋  ADD RECURRING BILL", UiKit.CYAN);
        add.setOnClickListener(v -> showDialog(null));
        root.addView(add);

        root.addView(UiKit.gap(this, 16));
        root.addView(UiKit.label(this, "YOUR COMMITMENTS"));
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
        TextView value = UiKit.text(this, "—", 20, UiKit.TEXT, true);
        value.setPadding(0, UiKit.dp(this, 8), 0, 0);
        card.addView(value);
        return card;
    }

    private void refresh() {
        if (list == null) return;
        list.removeAllViews();
        List<RecurringBill> bills = database.getRecurringBills();
        int active = 0;
        for (RecurringBill b : bills) if (b.isActive()) active++;
        billCount.setText(String.valueOf(active));
        upcomingTotal.setText(String.format(Locale.US, "AED %,.2f", database.getUpcomingRecurringAedThisMonth()));

        if (bills.isEmpty()) {
            LinearLayout empty = UiKit.card(this, 20);
            empty.addView(UiKit.text(this, "Nothing scheduled yet", 17, UiKit.TEXT, true));
            TextView t = UiKit.text(this, "Add recurring commitments so Safe to Spend stops treating bill money as disposable.", 12, UiKit.MUTED, false);
            t.setPadding(0, UiKit.dp(this, 7), 0, 0);
            empty.addView(t);
            list.addView(empty);
            return;
        }

        int today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        for (RecurringBill b : bills) {
            LinearLayout card = UiKit.card(this, 17);
            card.setAlpha(b.isActive() ? 1f : .62f);

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = UiKit.text(this, b.getName(), 15, UiKit.TEXT, true);
            top.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            top.addView(UiKit.pill(this, b.isActive() ? "ACTIVE" : "PAUSED", b.isActive() ? UiKit.GREEN : UiKit.MUTED));
            card.addView(top);

            TextView amount = UiKit.text(this,
                    String.format(Locale.US, "%s %,.2f", b.getCurrency(), b.getAmount()),
                    24, UiKit.TEXT, true);
            amount.setPadding(0, UiKit.dp(this, 11), 0, UiKit.dp(this, 6));
            card.addView(amount);

            int days = b.getDueDay() - today;
            String dueText;
            if (days == 0) dueText = "Due today";
            else if (days > 0) dueText = "Due in " + days + " day" + (days == 1 ? "" : "s");
            else dueText = "Next cycle • day " + b.getDueDay();

            TextView detail = UiKit.text(this,
                    dueText + "  ·  " + b.getCategory(),
                    11, days >= 0 && days <= 3 ? UiKit.AMBER : UiKit.MUTED, false);
            card.addView(detail);

            card.setOnClickListener(v -> showDialog(b));
            list.addView(card);
            list.addView(UiKit.gap(this, 9));
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
        form.setPadding(UiKit.dp(this, 24), 0, UiKit.dp(this, 24), 0);

        EditText name = input("Bill name");
        if (existing != null) name.setText(existing.getName());
        form.addView(name);

        EditText amount = input("Amount in AED");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (existing != null) amount.setText(String.format(Locale.US, "%.2f", existing.getAmount()));
        form.addView(amount);

        EditText due = input("Due day (1–31)");
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
        active.setTextColor(UiKit.TEXT);
        active.setChecked(existing == null || existing.isActive());
        form.addView(active);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add recurring bill" : "Edit recurring bill")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        String n = name.getText().toString().trim();
                        double a = Double.parseDouble(amount.getText().toString());
                        int day = Integer.parseInt(due.getText().toString());
                        if (n.isEmpty() || day < 1 || day > 31) throw new IllegalArgumentException();
                        database.saveRecurringBill(new RecurringBill(
                                existing == null ? 0 : existing.getId(),
                                n, a, "AED", day,
                                cats.get(category.getSelectedItemPosition()),
                                active.isChecked()));
                        refresh();
                    } catch (Exception e) {
                        Toast.makeText(this, "Enter a valid name, amount and due day.", Toast.LENGTH_SHORT).show();
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

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(112, 128, 151));
        e.setTextColor(UiKit.TEXT);
        e.setSingleLine(true);
        return e;
    }
}
