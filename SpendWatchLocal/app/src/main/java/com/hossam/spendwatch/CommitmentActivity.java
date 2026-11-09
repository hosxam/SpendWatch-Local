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
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Non-recurring future commitments: separate from bank transactions. */
public final class CommitmentActivity extends Activity {
    private SpendDatabase database;
    private LinearLayout content;
    private static final String[] REMINDERS = {
            "No reminder", "On due date", "7 days before", "14 days before",
            "1 calendar month before", "2 calendar months before",
            "3 calendar months before"
    };
    private static final int[] REMINDER_VALUES = {
            OneTimeCommitment.NO_REMINDER, 0, 7, 14, -1, -2, -3
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = SpendDatabase.getInstance(this);
        getWindow().setStatusBarColor(UiKit.BG);
        getWindow().setNavigationBarColor(UiKit.BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 20),
                UiKit.dp(this, 20), UiKit.dp(this, 40));
        content.setBackgroundColor(UiKit.BG);
        scroll.addView(content);
        setContentView(scroll);
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private String money(double v) { return String.format(Locale.US, "AED %,.2f", v); }

    private String date(long ms) {
        return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date(ms));
    }

    private void refresh() {
        content.removeAllViews();
        TextView back = UiKit.pill(this, "‹ BACK", UiKit.CYAN);
        back.setOnClickListener(v -> finish());
        content.addView(back);
        content.addView(UiKit.gap(this, 15));
        content.addView(UiKit.text(this, "Planned payments", 29, UiKit.TEXT, true));
        content.addView(UiKit.gap(this, 8));
        content.addView(UiKit.text(this,
                "One-time expenses with a date and an optional advance reminder. "
                        + "Not counted as paid until you confirm them.",
                12, UiKit.MUTED, false));
        content.addView(UiKit.gap(this, 16));

        TextView add = UiKit.pill(this, "＋ ADD A FUTURE PAYMENT", UiKit.PURPLE);
        add.setOnClickListener(v -> showEditor(null));
        content.addView(add);
        content.addView(UiKit.gap(this, 17));

        List<OneTimeCommitment> all = database.getOneTimeCommitments();
        int pending = 0;
        double pendingTotal = 0;
        for (OneTimeCommitment item : all) if (item.isPending()) {
            pending++;
            pendingTotal += item.getAmount();
        }
        LinearLayout summary = UiKit.card(this, 18);
        summary.addView(UiKit.text(this, "UPCOMING COMMITMENTS",
                11, UiKit.CYAN, true));
        summary.addView(UiKit.gap(this, 8));
        summary.addView(UiKit.text(this, money(pendingTotal), 26, UiKit.TEXT, true));
        summary.addView(UiKit.gap(this, 5));
        summary.addView(UiKit.text(this, pending + " unpaid future payments",
                12, UiKit.MUTED, false));
        content.addView(summary);
        content.addView(UiKit.gap(this, 14));

        if (all.isEmpty()) {
            content.addView(UiKit.text(this,
                    "No future payments. Add planned expenses like a car repair or annual insurance.",
                    13, UiKit.MUTED, false));
        }

        for (OneTimeCommitment item : all) {
            LinearLayout card = UiKit.card(this, 18);
            card.addView(UiKit.text(this, item.getTitle(), 16, UiKit.TEXT, true));
            card.addView(UiKit.gap(this, 7));
            card.addView(UiKit.text(this, money(item.getAmount()),
                    22, item.isPending() ? UiKit.AMBER : UiKit.GREEN, true));
            card.addView(UiKit.gap(this, 6));
            card.addView(UiKit.text(this,
                    date(item.getDueAt()) + "  •  " + item.getStatus().toUpperCase(Locale.ROOT)
                            + (item.isPending() && item.getDueAt() < System.currentTimeMillis()
                            ? "  •  OVERDUE" : ""),
                    12, UiKit.MUTED, false));
            card.addView(UiKit.gap(this, 5));
            int index=0;for(int i=0;i<REMINDER_VALUES.length;i++)
                if(REMINDER_VALUES[i]==item.getRemindDays())index=i;
            card.addView(UiKit.text(this, "Reminder: " + REMINDERS[index],
                    11, UiKit.CYAN, false));
            card.addView(UiKit.gap(this, 12));

            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            if (item.isPending()) {
                TextView paid = UiKit.pill(this, "MARK PAID", UiKit.GREEN);
                paid.setOnClickListener(v -> setStatus(item, "paid"));
                actions.addView(paid);
            } else {
                TextView reopen = UiKit.pill(this, "REOPEN", UiKit.AMBER);
                reopen.setOnClickListener(v -> setStatus(item, "pending"));
                actions.addView(reopen);
            }
            TextView edit = UiKit.pill(this, "EDIT", UiKit.CYAN);
            LinearLayout.LayoutParams margin = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            margin.setMargins(UiKit.dp(this, 8),0,0,0);
            actions.addView(edit, margin);
            edit.setOnClickListener(v -> showEditor(item));
            card.addView(actions);
            content.addView(card);
            content.addView(UiKit.gap(this, 10));
        }
    }

    private void setStatus(OneTimeCommitment item, String status) {
        if (!database.setOneTimeCommitmentStatus(item.getId(), status)) return;
        OneTimeReminderReceiver.scheduleAll(this);
        ExcelHistory.schedule(this);
        SpendWatchWidget.updateAll(this);
        refresh();
    }

    private void showEditor(OneTimeCommitment old) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(UiKit.dp(this, 22), UiKit.dp(this, 5),
                UiKit.dp(this, 22), UiKit.dp(this, 4));

        EditText title = new EditText(this);
        title.setHint("Payment name, e.g. car insurance");
        title.setTextColor(UiKit.TEXT); title.setHintTextColor(UiKit.MUTED);
        title.setSingleLine(true);
        if (old != null) title.setText(old.getTitle());
        form.addView(title);

        EditText amount = new EditText(this);
        amount.setHint("Amount in AED");
        amount.setTextColor(UiKit.TEXT);amount.setHintTextColor(UiKit.MUTED);
        amount.setSingleLine(true);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (old != null) amount.setText(String.format(Locale.US, "%.2f",old.getAmount()));
        form.addView(amount);

        Calendar selected = Calendar.getInstance();
        if (old != null) selected.setTimeInMillis(old.getDueAt());
        else selected.add(Calendar.MONTH,1);
        TextView selectDate = UiKit.pill(this,"DUE: " + date(selected.getTimeInMillis()),UiKit.CYAN);
        selectDate.setOnClickListener(v -> {
            DatePickerDialog dp = new DatePickerDialog(this,(picker,year,month,day) -> {
                selected.set(year,month,day,9,0,0);
                selected.set(Calendar.MILLISECOND,0);
                selectDate.setText("DUE: " + date(selected.getTimeInMillis()));
            },selected.get(Calendar.YEAR),selected.get(Calendar.MONTH),
                    selected.get(Calendar.DAY_OF_MONTH));
            dp.show();
        });
        form.addView(selectDate);
        TextView label = UiKit.text(this,"REMIND ME",11,UiKit.MUTED,true);
        label.setPadding(0,UiKit.dp(this,12),0,0);
        form.addView(label);
        Spinner reminder = new Spinner(this);
        reminder.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, REMINDERS));
        if (old != null) {
            for(int i=0;i<REMINDER_VALUES.length;i++)
                if(REMINDER_VALUES[i]==old.getRemindDays())reminder.setSelection(i);
        } else reminder.setSelection(0);
        form.addView(reminder);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(old == null ? "New one-time payment" : "Edit planned payment")
                .setView(form)
                .setPositiveButton("Save",null)
                .setNegativeButton("Cancel",null)
                .setNeutralButton(old == null ? null : "Delete",
                        (d,w) -> { if(old != null) {
                            database.deleteOneTimeCommitment(old.getId());
                            OneTimeReminderReceiver.scheduleAll(this);
                            ExcelHistory.schedule(this);
                            refresh();
                        } }).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(clicked -> {
                    String name=title.getText().toString().trim();
                    double value;
                    try{value=Double.parseDouble(amount.getText().toString().trim());}
                    catch(Exception e){value=-1;}
                    if (name.isEmpty() || !Double.isFinite(value) || value<=0) {
                        Toast.makeText(this,"Enter a valid title and amount.",Toast.LENGTH_LONG).show();
                        return;
                    }
                    long id=database.saveOneTimeCommitment(
                            old == null ? 0 : old.getId(),name,value,
                            selected.getTimeInMillis(),
                            REMINDER_VALUES[reminder.getSelectedItemPosition()]);
                    if(id <= 0){
                        Toast.makeText(this,"Cannot save payment.",Toast.LENGTH_LONG).show();
                        return;
                    }
                    OneTimeReminderReceiver.scheduleAll(this);
                    ExcelHistory.schedule(this);
                    SpendWatchWidget.updateAll(this);
                    dialog.dismiss();
                    refresh();
                }));
        dialog.show();
    }
}
