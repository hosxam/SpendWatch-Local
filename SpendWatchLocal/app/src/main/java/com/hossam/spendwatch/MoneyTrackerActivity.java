package com.hossam.spendwatch;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Offline balance reconciliation and non-posting, per-calendar-month salary plans.
 * Planned salary does not create income or alter the tracked bank balance.
 */
public final class MoneyTrackerActivity extends Activity {
    private SpendDatabase db;
    private LinearLayout content;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = SpendDatabase.getInstance(this);
        getWindow().setStatusBarColor(UiKit.BG);
        getWindow().setNavigationBarColor(UiKit.BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(UiKit.BG);
        content.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 22),
                UiKit.dp(this, 20), UiKit.dp(this, 40));
        scroll.addView(content);
        setContentView(scroll);
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private static String money(double amount) {
        return String.format(Locale.US, "AED %,.2f", amount);
    }

    private static String date(long millis) {
        return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(new Date(millis));
    }

    private EditText input(String hint) {
        EditText edit = new EditText(this);
        edit.setTextColor(UiKit.TEXT);
        edit.setHintTextColor(UiKit.MUTED);
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        return edit;
    }

    private void refresh() {
        content.removeAllViews();
        TextView back = UiKit.pill(this, "‹ BACK", UiKit.CYAN);
        back.setOnClickListener(v -> finish());
        content.addView(back);
        content.addView(UiKit.gap(this, 18));
        content.addView(UiKit.text(this, "Account balance & salary", 27, UiKit.TEXT, true));
        content.addView(UiKit.gap(this, 8));
        content.addView(UiKit.text(this,
                "Keep actual cash separate from envelope budgets and planned income.",
                12, UiKit.MUTED, false));
        content.addView(UiKit.gap(this, 18));

        SpendDatabase.TrackedBalance balance = db.getTrackedBalance();
        LinearLayout bank = UiKit.card(this, 18);
        bank.addView(UiKit.text(this, "MANUALLY TRACKED BANK BALANCE", 11, UiKit.CYAN, true));
        bank.addView(UiKit.gap(this, 10));
        bank.addView(UiKit.text(this,
                balance == null ? "Not set" : money(balance.currentAmount),
                30, UiKit.TEXT, true));
        bank.addView(UiKit.gap(this, 7));
        String scope = balance == null ? "Set an opening balance to begin."
                : balance.bankSource.isEmpty() ? "All AED accounts combined"
                : "Account: " + balance.bankSource;
        bank.addView(UiKit.text(this, scope, 13, UiKit.MUTED, false));
        if (balance != null) {
            bank.addView(UiKit.gap(this, 7));
            bank.addView(UiKit.text(this,
                    "Last corrected " + date(balance.recordedAt)
                            + " · " + db.getTrackedBalanceCorrectionCount() + " saved reconciliation(s)",
                    11, UiKit.MUTED, false));
        }
        bank.addView(UiKit.gap(this, 14));
        TextView change = UiKit.pill(this,
                balance == null ? "SET CURRENT BALANCE" : "CORRECT CURRENT BALANCE", UiKit.CYAN);
        change.setOnClickListener(v -> showBalanceEditor());
        bank.addView(change);
        content.addView(bank);
        content.addView(UiKit.gap(this, 8));
        content.addView(UiKit.text(this,
                "A correction anchors the balance at that moment. Only subsequently recorded "
                + "AED transactions for the selected bank change it; old transactions are "
                + "not deducted twice. This is an estimate, not a live bank connection.",
                12, UiKit.MUTED, false));

        content.addView(UiKit.gap(this, 24));
        content.addView(UiKit.text(this, "Salary calendar", 23, UiKit.TEXT, true));
        content.addView(UiKit.gap(this, 8));
        content.addView(UiKit.text(this,
                "Choose a different expected salary date and amount for each calendar month. "
                + "An expected salary never adds money automatically.",
                12, UiKit.MUTED, false));
        content.addView(UiKit.gap(this, 13));

        TextView add = UiKit.pill(this, "＋ PLAN A MONTH'S SALARY", UiKit.PURPLE);
        add.setOnClickListener(v -> showSalaryEditor(null));
        content.addView(add);
        content.addView(UiKit.gap(this, 14));

        List<SpendDatabase.SalaryPlan> plans = db.getSalaryPlans();
        if (plans.isEmpty()) {
            LinearLayout empty = UiKit.card(this, 16);
            empty.addView(UiKit.text(this,
                    "No expected salary dates entered. Add one for each month.",
                    13, UiKit.MUTED, false));
            content.addView(empty);
        }

        for (SpendDatabase.SalaryPlan plan : plans) {
            LinearLayout card = UiKit.card(this, 16);
            card.addView(UiKit.text(this,
                    date(plan.expectedDate), 17, UiKit.TEXT, true));
            card.addView(UiKit.gap(this, 6));
            card.addView(UiKit.text(this, money(plan.amount), 23, UiKit.CYAN, true));
            card.addView(UiKit.gap(this, 5));
            card.addView(UiKit.text(this,
                    plan.monthKey + " · " +
                            ("received".equals(plan.status) ? "Confirmed received" : "Expected"),
                    12, "received".equals(plan.status) ? UiKit.GREEN : UiKit.AMBER,
                    false));
            card.addView(UiKit.gap(this, 12));

            LinearLayout buttons = new LinearLayout(this);
            buttons.setGravity(Gravity.CENTER_VERTICAL);
            if ("expected".equals(plan.status)) {
                TextView received = UiKit.pill(this, "RECEIVED", UiKit.GREEN);
                received.setOnClickListener(v -> confirmReceived(plan));
                buttons.addView(received);
            }
            TextView edit = UiKit.pill(this, "EDIT", UiKit.CYAN);
            LinearLayout.LayoutParams margin = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            margin.setMargins(UiKit.dp(this, 8), 0, 0, 0);
            buttons.addView(edit, margin);
            edit.setOnClickListener(v -> showSalaryEditor(plan));
            card.addView(buttons);
            content.addView(card);
            content.addView(UiKit.gap(this, 10));
        }

        content.addView(UiKit.gap(this, 10));
        content.addView(UiKit.text(this,
                "Received only confirms the calendar entry. It does not insert an income "
                + "transaction. Bank SMS income or a manually entered income transaction "
                + "is what increases the tracked balance. Do not record the same deposit twice.",
                12, UiKit.MUTED, false));
    }

    private void showBalanceEditor() {
        SpendDatabase.TrackedBalance old = db.getTrackedBalance();
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(UiKit.dp(this, 20), 0, UiKit.dp(this, 20), 0);
        EditText amount = input("Actual available balance in AED");
        if (old != null) amount.setText(String.format(Locale.US, "%.2f", old.currentAmount));
        form.addView(amount);

        List<String> choices = new ArrayList<>();
        choices.add("All AED accounts combined");
        choices.addAll(db.getBankSourceChoices());
        Spinner bank = new Spinner(this);
        bank.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, choices));
        if (old != null && !old.bankSource.isEmpty()) {
            int index = choices.indexOf(old.bankSource);
            if (index >= 0) bank.setSelection(index);
        }
        form.addView(UiKit.text(this, "TRACK WHICH ACCOUNT?", 11, UiKit.MUTED, true));
        form.addView(bank);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Reconcile actual balance")
                .setMessage("Enter the actual amount currently held in the selected bank account. "
                        + "Selecting a different bank requires entering that bank's own balance. "
                        + "Older transactions and historical corrections remain saved.")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(clicked -> {
                    double value;
                    try { value = Double.parseDouble(amount.getText().toString().trim()); }
                    catch (Exception e) { value = Double.NaN; }
                    String bankName = bank.getSelectedItemPosition() == 0 ? ""
                            : choices.get(bank.getSelectedItemPosition());
                    if (!db.setTrackedBalance(value, bankName)) {
                        Toast.makeText(this, "Enter a valid account balance.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    ExcelHistory.schedule(this);
                    dialog.dismiss();
                    refresh();
                }));
        dialog.show();
    }

    private void showSalaryEditor(SpendDatabase.SalaryPlan previous) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(UiKit.dp(this, 20), 0, UiKit.dp(this, 20), 0);

        Calendar chosen = Calendar.getInstance();
        if (previous != null) chosen.setTimeInMillis(previous.expectedDate);
        chosen.set(Calendar.HOUR_OF_DAY, 9);
        chosen.set(Calendar.MINUTE, 0);
        chosen.set(Calendar.SECOND, 0);
        chosen.set(Calendar.MILLISECOND, 0);

        EditText salaryAmount = input("Expected salary in AED");
        double defaultAmount = previous != null ? previous.amount : db.getMonthlyIncome();
        if (defaultAmount > 0)
            salaryAmount.setText(String.format(Locale.US, "%.2f", defaultAmount));
        form.addView(salaryAmount);

        TextView due = UiKit.pill(this, "EXPECTED: " + date(chosen.getTimeInMillis()), UiKit.CYAN);
        due.setOnClickListener(v -> {
            DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
                chosen.set(year, month, day, 9, 0, 0);
                chosen.set(Calendar.MILLISECOND, 0);
                due.setText("EXPECTED: " + date(chosen.getTimeInMillis()));
            }, chosen.get(Calendar.YEAR), chosen.get(Calendar.MONTH),
                    chosen.get(Calendar.DAY_OF_MONTH));
            picker.show();
        });
        form.addView(due);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(previous == null ? "Plan salary date" : "Edit expected salary")
                .setMessage("Expected date can vary each month. "
                        + "Saving resets this month's status to expected; no bank funds are changed.")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null);
        if (previous != null) {
            builder.setNeutralButton("Delete", (d, w) -> {
                db.deleteSalaryPlan(previous.monthKey);
                ExcelHistory.schedule(this);
                refresh();
            });
        }
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(clicked -> {
                    double value;
                    try { value = Double.parseDouble(salaryAmount.getText().toString().trim()); }
                    catch (Exception e) { value = Double.NaN; }
                    if (!Double.isFinite(value) || value <= 0) {
                        Toast.makeText(this, "Enter a valid salary amount.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (previous != null
                            && !previous.monthKey.equals(
                                    SpendDatabase.salaryMonthKey(chosen.getTimeInMillis())))
                        db.deleteSalaryPlan(previous.monthKey);
                    if (!db.saveSalaryPlan(chosen.getTimeInMillis(), value)) {
                        Toast.makeText(this, "Unable to save salary date.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    ExcelHistory.schedule(this);
                    dialog.dismiss();
                    refresh();
                }));
        dialog.show();
    }

    private void confirmReceived(SpendDatabase.SalaryPlan plan) {
        new AlertDialog.Builder(this)
                .setTitle("Salary actually received?")
                .setMessage("This marks the expected salary as received but does NOT add money. "
                        + "If the bank SMS already recorded it, the tracked balance has updated. "
                        + "Otherwise add a single manual income transaction under Settings or Home.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm received", (d, w) -> {
                    if (db.markSalaryReceived(plan.monthKey)) {
                        ExcelHistory.schedule(this);
                        refresh();
                    }
                }).show();
    }
}
