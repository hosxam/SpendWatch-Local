package com.hossam.spendwatch;

import android.app.NotificationManager;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SpendWatchInstrumentedTest {

    private Context context;
    private SpendDatabase db;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        db = SpendDatabase.getInstance(context);
        db.clearAll();
        db.setMonthlyIncome(0);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancelAll();
    }

    @After
    public void tearDown() {
        db.clearAll();
        db.setMonthlyIncome(0);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancelAll();
    }

    private Transaction tx(double amount, long timestamp, String merchant, String type, String category) {
        Transaction t = new Transaction();
        t.setAmount(amount);
        t.setCurrency("AED");
        t.setMerchant(merchant);
        t.setTimestamp(timestamp);
        t.setBankSource("Ruya");
        t.setRawMessage("test");
        t.setCategory(category);
        t.setTransactionType(type);
        return t;
    }

    @Test
    public void duplicateProtectionAndSpendMathAreCorrect() {
        long now = System.currentTimeMillis();
        Transaction purchase = tx(142.50, now, "CARREFOUR DEIRA", "card_purchase", "Groceries");
        assertTrue(db.insertTransaction(purchase) > 0);

        Transaction duplicate = tx(142.50, now + 60_000, "CARREFOUR DEIRA", "card_purchase", "Groceries");
        assertEquals(-1L, db.insertTransaction(duplicate));

        assertTrue(db.insertTransaction(tx(500, now + 120_000, "Transfer", "transfer_sent", "Transfers")) > 0);
        assertTrue(db.insertTransaction(tx(1000, now + 180_000, "Salary", "income", "Income")) > 0);
        assertTrue(db.insertTransaction(tx(25, now + 240_000, "Refund", "refund", "Refunds")) > 0);

        assertEquals(142.50, db.getTotalSpentAedThisMonth(), 0.001);
        assertEquals(142.50, db.getSpentForCategoryThisMonth("Groceries"), 0.001);
        assertEquals(4, db.getAllTransactions().size());
    }

    @Test
    public void safeToSpendUsesIncomeSpendBillsAndSinkingFunds() {
        long now = System.currentTimeMillis();
        db.setMonthlyIncome(1000);
        assertTrue(db.insertTransaction(tx(100, now, "TEST SHOP", "card_purchase", "General")) > 0);

        Calendar cal = Calendar.getInstance();
        int dueDay = cal.get(Calendar.DAY_OF_MONTH);
        RecurringBill bill = new RecurringBill(0, "Phone plan", 200, "AED", dueDay, "Utilities & Telecom", true);
        long billId = db.saveRecurringBill(bill);

        assertEquals(700.0, db.getSafeToSpendRestOfMonth(), 0.001);

        if (billId > 0) db.deleteRecurringBill(billId);
    }

    @Test
    public void widgetProviderIsRegisteredAndRemoteViewsRenderCurrentTotals() {
        db.setMonthlyIncome(1000);
        assertTrue(db.insertTransaction(tx(125, System.currentTimeMillis(), "CARREFOUR", "card_purchase", "Groceries")) > 0);

        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName expected = new ComponentName(context, SpendWatchWidget.class);
        boolean found = false;
        List<AppWidgetProviderInfo> providers = manager.getInstalledProviders();
        for (AppWidgetProviderInfo info : providers) {
            if (expected.equals(info.provider)) {
                found = true;
                break;
            }
        }
        assertTrue("SpendWatch widget provider must be registered", found);

        RemoteViews views = SpendWatchWidget.buildViews(context, 88001);
        View rendered = views.apply(context, new FrameLayout(context));

        TextView spent = rendered.findViewById(R.id.widget_spent);
        TextView safe = rendered.findViewById(R.id.widget_safe);
        assertNotNull(spent);
        assertNotNull(safe);
        assertEquals("AED 125", spent.getText().toString());
        assertEquals("AED 875", safe.getText().toString());

        SpendWatchWidget.updateAll(context);
    }

    @Test
    public void transactionConfirmationNotificationIsPosted() throws Exception {
        NotificationHelper.ensureChannels(context);
        Transaction t = tx(84.50, System.currentTimeMillis(), "CARREFOUR", "card_purchase", "Groceries");
        NotificationHelper.notifyTransactionRecorded(context, t);
        Thread.sleep(250);

        StatusBarNotification match = findOwnNotificationContaining("Transaction recorded");
        assertNotNull("Confirmation notification should be active", match);
        CharSequence text = match.getNotification().extras.getCharSequence("android.text");
        assertNotNull(text);
        assertTrue(text.toString().contains("AED 84.50"));
        assertTrue(text.toString().contains("CARREFOUR"));
    }

    @Test
    public void monthlySummaryReceiverUsesPreviousMonthSpend() throws Exception {
        Calendar end = Calendar.getInstance();
        end.set(Calendar.DAY_OF_MONTH, 1);
        end.set(Calendar.HOUR_OF_DAY, 0);
        end.set(Calendar.MINUTE, 0);
        end.set(Calendar.SECOND, 0);
        end.set(Calendar.MILLISECOND, 0);

        Calendar previous = (Calendar) end.clone();
        previous.add(Calendar.MONTH, -1);
        previous.set(Calendar.DAY_OF_MONTH, Math.min(15, previous.getActualMaximum(Calendar.DAY_OF_MONTH)));
        previous.set(Calendar.HOUR_OF_DAY, 12);

        assertTrue(db.insertTransaction(tx(2500, previous.getTimeInMillis(), "PREVIOUS MONTH TEST", "card_purchase", "General")) > 0);

        new MonthlySummaryReceiver().onReceive(context, new Intent(context, MonthlySummaryReceiver.class));
        Thread.sleep(250);

        StatusBarNotification match = findOwnNotificationContaining("monthly SpendWatch recap");
        assertNotNull("Monthly summary notification should be active", match);
        CharSequence text = match.getNotification().extras.getCharSequence("android.text");
        assertNotNull(text);
        assertTrue("Summary should contain previous-month total", text.toString().contains("AED 2,500.00"));
    }

    private StatusBarNotification findOwnNotificationContaining(String titlePart) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return null;
        for (StatusBarNotification sbn : nm.getActiveNotifications()) {
            CharSequence title = sbn.getNotification().extras.getCharSequence("android.title");
            if (title != null && title.toString().contains(titlePart)) return sbn;
        }
        return null;
    }
}
