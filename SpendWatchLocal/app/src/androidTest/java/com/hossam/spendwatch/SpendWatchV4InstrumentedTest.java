package com.hossam.spendwatch;

import static org.junit.Assert.*;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;

@RunWith(AndroidJUnit4.class)
public class SpendWatchV4InstrumentedTest {

    private Context context;
    private SpendDatabase db;
    private NotificationManager notificationManager;

    @Before
    public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = SpendDatabase.getInstance(context);
        clearDatabase();
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) notificationManager.cancelAll();
        NotificationHelper.ensureChannels(context);
    }

    @After
    public void teardown() {
        if (notificationManager != null) notificationManager.cancelAll();
        clearDatabase();
    }

    private void clearDatabase() {
        db.getWritableDatabase().execSQL("DELETE FROM transactions");
        db.getWritableDatabase().execSQL("DELETE FROM recurring_bills");
        db.getWritableDatabase().execSQL("DELETE FROM budgets");
        db.getWritableDatabase().execSQL("DELETE FROM settings");
    }

    private Transaction makeSpend(double amount, long timestamp, String merchant) {
        Transaction t = new Transaction();
        t.setAmount(amount);
        t.setCurrency("AED");
        t.setMerchant(merchant);
        t.setTimestamp(timestamp);
        t.setBankSource("Ruya");
        t.setRawMessage("QA");
        t.setCategory("General");
        t.setTransactionType("card_purchase");
        return t;
    }

    @Test
    public void parser_handlesRepresentativeRuyaAlerts_andRejectsBadAlerts() {
        assertParsed("Ruya", "Your card ending 1234 was used for AED 42.75 at TEST CAFE. Successful transaction.",
                "com.ruya.bank", 42.75, "card_purchase");
        assertParsed("Card transaction", "AED 15.25 debited at CARREFOUR.", "com.ruya.bank",
                15.25, "card_purchase");
        assertParsed("Ruya", "Payment of AED: 55.00 at NOON was successfully processed.", "com.ruya.bank",
                55.00, "card_purchase");
        assertParsed("Ruya", "POS purchase AED 87.10 at LULU HYPERMARKET", "com.ruya.bank",
                87.10, "pos");
        assertParsed("Ruya", "Cash withdrawal AED 500.00 at ATM", "com.ruya.bank",
                500.00, "atm");
        assertParsed("Ruya", "Transfer sent AED 750.00 to Ahmed", "com.ruya.bank",
                750.00, "transfer_sent");
        assertParsed("Ruya", "Refund AED 20.00 from merchant", "com.ruya.bank",
                20.00, "refund");
        assertParsed("Ruya", "Salary AED 5000.00 credited to your account", "com.ruya.bank",
                5000.00, "income");

        assertNull(BankTransactionParser.parseNotification(
                "Ruya", "Purchase AED 10.00 declined due to insufficient funds", "com.ruya.bank"));
        assertNull(BankTransactionParser.parseNotification(
                "Ruya", "Your OTP is 123456 for AED 10.00 transaction", "com.ruya.bank"));
        assertNull(BankTransactionParser.parseNotification(
                "Random App", "Purchase AED 10.00 at TEST", "com.random.app"));
    }

    private void assertParsed(String title, String body, String pkg, double amount, String type) {
        Transaction t = BankTransactionParser.parseNotification(title, body, pkg);
        assertNotNull("Expected transaction for: " + body, t);
        assertEquals(amount, t.getAmount(), 0.001);
        assertEquals(type, t.getTransactionType());
        assertEquals("Ruya", t.getBankSource());
    }

    @Test
    public void notificationExtractor_readsExpandedAndLineContent() {
        Notification n = new Notification();
        n.extras.putCharSequence(Notification.EXTRA_TITLE, "Ruya");
        n.extras.putCharSequence(Notification.EXTRA_TEXT, "AED 42.75");
        n.extras.putCharSequence(Notification.EXTRA_BIG_TEXT, "Your card was used for AED 42.75 at TEST CAFE.");
        n.extras.putCharSequenceArray(Notification.EXTRA_TEXT_LINES,
                new CharSequence[]{"Successful transaction", "Card ending 1234"});

        assertEquals("Ruya", NotificationTextExtractor.title(n));
        String body = NotificationTextExtractor.body(n);
        assertTrue(body.contains("AED 42.75"));
        assertTrue(body.contains("TEST CAFE"));
        assertTrue(body.contains("Successful transaction"));
        assertTrue(body.contains("Card ending 1234"));
    }

    @Test
    public void database_preventsDuplicates_andSafeToSpendMathIsCorrect() {
        long now = System.currentTimeMillis();

        Transaction first = makeSpend(1000.00, now, "QA SHOP");
        assertTrue(db.insertTransaction(first) > 0);

        Transaction duplicate = makeSpend(1000.00, now + 60_000L, "QA SHOP");
        assertEquals(-1L, db.insertTransaction(duplicate));

        Transaction later = makeSpend(1000.00, now + 11 * 60_000L, "QA SHOP");
        assertTrue(db.insertTransaction(later) > 0);

        clearDatabase();
        db.setMonthlyIncome(10000.00);
        assertTrue(db.insertTransaction(makeSpend(1000.00, now, "QA SHOP")) > 0);

        db.saveRecurringBill(new RecurringBill(
                0, "QA Bill", 500.00, "AED", 31, "General", true));
        db.saveBudget(new BudgetEnvelope(
                0, "QA Sinking", 200.00, true, 0.00, true));

        assertEquals(1000.00, db.getTotalSpentAedThisMonth(), 0.01);
        assertEquals(500.00, db.getUpcomingRecurringAedThisMonth(), 0.01);
        assertEquals(200.00, db.getTotalSinkingMonthlyAllocation(), 0.01);
        assertEquals(8300.00, db.getSafeToSpendRestOfMonth(), 0.01);
    }

    @Test
    public void transactionConfirmationNotification_isActuallyPosted() throws Exception {
        Transaction t = makeSpend(84.50, System.currentTimeMillis(), "QA CAFE");
        NotificationHelper.notifyTransactionRecorded(context, t);
        Thread.sleep(400);

        StatusBarNotification found = findActiveNotification("Transaction recorded");
        assertNotNull("Transaction confirmation notification was not posted", found);

        String text = String.valueOf(found.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT));
        assertTrue(text.contains("AED 84.50"));
        assertTrue(text.contains("QA CAFE"));
    }

    @Test
    public void monthlySummaryNotification_reportsPreviousMonthTotal() throws Exception {
        Calendar timestamp = Calendar.getInstance();
        timestamp.set(Calendar.DAY_OF_MONTH, 1);
        timestamp.add(Calendar.DAY_OF_MONTH, -1);
        timestamp.set(Calendar.HOUR_OF_DAY, 12);
        timestamp.set(Calendar.MINUTE, 0);
        timestamp.set(Calendar.SECOND, 0);
        timestamp.set(Calendar.MILLISECOND, 0);

        assertTrue(db.insertTransaction(makeSpend(2500.00, timestamp.getTimeInMillis(), "QA MONTH")) > 0);

        new MonthlySummaryReceiver().onReceive(context, new Intent());
        Thread.sleep(400);

        StatusBarNotification found = findActiveNotification("Your monthly SpendWatch recap");
        assertNotNull("Monthly recap notification was not posted", found);
        String text = String.valueOf(found.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT));
        assertTrue("Unexpected monthly summary: " + text, text.contains("2,500.00"));
    }

    @Test
    public void homeWidget_rendersLiveSpendSafeAndBillsValues() {
        long now = System.currentTimeMillis();
        db.setMonthlyIncome(10000.00);
        assertTrue(db.insertTransaction(makeSpend(1250.00, now, "QA WIDGET")) > 0);
        db.saveRecurringBill(new RecurringBill(
                0, "Widget Bill", 500.00, "AED", 31, "General", true));

        final View[] rendered = new View[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RemoteViews views = SpendWatchWidget.buildViews(context, 991);
            FrameLayout parent = new FrameLayout(context);
            rendered[0] = views.apply(context, parent);
        });

        assertNotNull(rendered[0]);
        TextView spent = rendered[0].findViewById(R.id.widget_spent);
        TextView safe = rendered[0].findViewById(R.id.widget_safe);
        TextView bills = rendered[0].findViewById(R.id.widget_bills);

        assertEquals("AED 1,250", spent.getText().toString());
        assertEquals("AED 8,250", safe.getText().toString());
        assertEquals("AED 500", bills.getText().toString());
    }

    @Test
    public void mainActivity_launchesWithoutCrash() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity);
                assertNotNull(activity.findViewById(android.R.id.content));
            });
        }
    }


    @Test
    public void parser_handlesLargeAndGroupedAmountsInBothOrders() {
        assertParsed("Card transaction", "Purchase AED 12345.67 at TEST SHOP", "com.ruya.bank",
                12345.67, "card_purchase");
        assertParsed("Card transaction", "Purchase AED 12,345.67 at TEST SHOP", "com.ruya.bank",
                12345.67, "card_purchase");
        assertParsed("Card transaction", "Purchase 12345.67 AED at TEST SHOP", "com.ruya.bank",
                12345.67, "card_purchase");
        assertParsed("Card transaction", "Purchase 12,345.67 AED at TEST SHOP", "com.ruya.bank",
                12345.67, "card_purchase");
    }

    @Test
    public void aedTotalsExcludeForeignCurrencyTransfersIncomeRefundsAndIgnoredRows() {
        long now = System.currentTimeMillis();
        long base = now - 10 * 60_000L;

        Transaction usd = makeSpend(50.0, base, "USD SHOP");
        usd.setCurrency("USD");
        assertTrue(db.insertTransaction(usd) > 0);

        Transaction transfer = makeSpend(300.0, base + 20_000, "TRANSFER");
        transfer.setTransactionType("transfer_sent");
        transfer.setCategory("Transfers");
        assertTrue(db.insertTransaction(transfer) > 0);

        Transaction income = makeSpend(500.0, base + 40_000, "SALARY");
        income.setTransactionType("income");
        income.setCategory("Income");
        assertTrue(db.insertTransaction(income) > 0);

        Transaction refund = makeSpend(40.0, base + 60_000, "REFUND");
        refund.setTransactionType("refund");
        refund.setCategory("Refunds");
        assertTrue(db.insertTransaction(refund) > 0);

        Transaction ignored = makeSpend(70.0, base + 80_000, "IGNORED");
        ignored.setIgnored(true);
        assertTrue(db.insertTransaction(ignored) > 0);

        Transaction spend = makeSpend(125.0, base + 100_000, "REAL SPEND");
        assertTrue(db.insertTransaction(spend) > 0);

        assertEquals(125.0, db.getTotalSpentAedThisMonth(), 0.01);
    }

    @Test
    public void transactionAndRecurringBillCrudWorks() {
        long id = db.insertTransaction(makeSpend(90.0, System.currentTimeMillis(), "CRUD SHOP"));
        assertTrue(id > 0);
        assertTrue(db.updateTransaction(id, "Shopping", "fee", true));

        Transaction loaded = db.getAllTransactions().get(0);
        assertEquals("Shopping", loaded.getCategory());
        assertEquals("fee", loaded.getTransactionType());
        assertTrue(loaded.isIgnored());

        assertTrue(db.deleteTransaction(id));
        assertTrue(db.getAllTransactions().isEmpty());

        long billId = db.saveRecurringBill(new RecurringBill(
                0, "Phone", 250.0, "AED", 20, "Utilities & Telecom", true));
        assertTrue(billId > 0);
        assertEquals(1, db.getRecurringBills().size());
        assertEquals(250.0, db.getRecurringBills().get(0).getAmount(), 0.01);

        db.saveRecurringBill(new RecurringBill(
                billId, "Phone", 275.0, "AED", 21, "Utilities & Telecom", false));
        RecurringBill updated = db.getRecurringBills().get(0);
        assertEquals(275.0, updated.getAmount(), 0.01);
        assertEquals(21, updated.getDueDay());
        assertFalse(updated.isActive());

        assertTrue(db.deleteRecurringBill(billId));
        assertTrue(db.getRecurringBills().isEmpty());
    }

    @Test
    public void rolloverCarriesUnusedPreviousMonthMoney() {
        db.saveBudget(new BudgetEnvelope(
                0, "Groceries", 1000.0, true, 100.0, false));

        Calendar previous = Calendar.getInstance();
        previous.add(Calendar.MONTH, -1);
        previous.set(Calendar.DAY_OF_MONTH, 15);
        previous.set(Calendar.HOUR_OF_DAY, 12);
        previous.set(Calendar.MINUTE, 0);
        previous.set(Calendar.SECOND, 0);
        previous.set(Calendar.MILLISECOND, 0);

        Transaction previousSpend = makeSpend(400.0, previous.getTimeInMillis(), "PREV GROCERIES");
        previousSpend.setCategory("Groceries");
        assertTrue(db.insertTransaction(previousSpend) > 0);

        int previousYm = previous.get(Calendar.YEAR) * 100 + (previous.get(Calendar.MONTH) + 1);
        db.setSetting("last_rollover_month", String.valueOf(previousYm));

        BudgetEnvelope groceries = null;
        for (BudgetEnvelope envelope : db.getBudgets()) {
            if ("Groceries".equals(envelope.getName())) {
                groceries = envelope;
                break;
            }
        }

        assertNotNull(groceries);
        assertEquals(700.0, groceries.getCarryAmount(), 0.01);
        assertEquals(1700.0, groceries.getEffectiveAmount(), 0.01);
    }

    @Test
    public void allPrimaryActivitiesLaunchWithoutCrash() {
        try (ActivityScenario<BudgetActivity> scenario = ActivityScenario.launch(BudgetActivity.class)) {
            scenario.onActivity(activity -> assertNotNull(activity.findViewById(android.R.id.content)));
        }
        try (ActivityScenario<RecurringActivity> scenario = ActivityScenario.launch(RecurringActivity.class)) {
            scenario.onActivity(activity -> assertNotNull(activity.findViewById(android.R.id.content)));
        }
    }

    private StatusBarNotification findActiveNotification(String title) {
        if (notificationManager == null) return null;
        for (StatusBarNotification sbn : notificationManager.getActiveNotifications()) {
            CharSequence value = sbn.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE);
            if (value != null && title.contentEquals(value)) return sbn;
        }
        return null;
    }
}
