package com.hossam.spendwatch;

import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.*;

/** Protects actual account values from pre-anchor history and unconfirmed salary. */
@RunWith(AndroidJUnit4.class)
public final class BalanceSalaryInstrumentedTest {
    private SpendDatabase db;

    @Before public void setup() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = SpendDatabase.getInstance(context);
        db.clearAll();
        db.clearBalanceSalaryTestData();
    }

    @After public void teardown() {
        db.clearAll();
        db.clearBalanceSalaryTestData();
    }

    private long add(String bank, String type, double amount, boolean ignored,
            String merchant) {
        Transaction tx = new Transaction();
        tx.setCurrency("AED");
        tx.setAmount(amount);
        tx.setMerchant(merchant);
        tx.setTimestamp(System.currentTimeMillis());
        tx.setBankSource(bank);
        tx.setCategory("General");
        tx.setTransactionType(type);
        tx.setRawMessage("test transaction " + merchant);
        tx.setIgnored(ignored);
        return db.insertTransaction(tx);
    }

    private static long day(int year, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month - 1, day, 9, 0, 0);
        return c.getTimeInMillis();
    }

    @Test public void balanceStartsAtManualActualAmountWithoutSubtractingOldSpending() {
        assertNull(db.getTrackedBalance());
        assertTrue(add("Ruya", "card_purchase", 120, false, "OLD PURCHASE") > 0);
        assertTrue(db.setTrackedBalance(575.25, "Ruya"));
        assertEquals(575.25, db.getTrackedBalance().currentAmount, 0.001);
        assertEquals(1, db.getTrackedBalanceCorrectionCount());

        assertTrue(add("Ruya", "card_purchase", 30, false, "NEW PURCHASE") > 0);
        assertTrue(add("Ruya", "income", 300, false, "SALARY SMS") > 0);
        assertTrue(add("Ruya", "refund", 10, false, "REFUND") > 0);
        assertTrue(add("Ruya", "transfer_sent", 20, false, "TRANSFER") > 0);
        assertEquals(835.25, db.getTrackedBalance().currentAmount, 0.001);

        // Other banks and ignored/foreign-currency entries do not affect Ruya.
        assertTrue(add("ENBD", "card_purchase", 800, false, "OTHER BANK") > 0);
        assertTrue(add("Ruya", "card_purchase", 100, true, "IGNORED TX") > 0);
        // SMS inserted later with an earlier actual transaction date must not
        // be deducted from a balance the user reconciled after that purchase.
        Transaction delayed = new Transaction();
        delayed.setCurrency("AED");
        delayed.setAmount(25);
        delayed.setMerchant("OLD DELAYED SMS");
        delayed.setBankSource("Ruya");
        delayed.setCategory("General");
        delayed.setTransactionType("card_purchase");
        delayed.setRawMessage("Old alert delivered after reconciliation");
        delayed.setTimestamp(db.getTrackedBalance().recordedAt - 60_000L);
        assertTrue(db.insertTransaction(delayed) > 0);
        assertEquals(835.25, db.getTrackedBalance().currentAmount, 0.001);
    }

    @Test public void correctionsReanchorWithoutRewritingOrDeletingTransactions() {
        assertTrue(db.setTrackedBalance(100, "Ruya"));
        assertTrue(add("Ruya", "card_purchase", 20, false, "FIRST") > 0);
        assertEquals(80, db.getTrackedBalance().currentAmount, 0.01);
        assertTrue(db.setTrackedBalance(91.45, "Ruya"));
        assertEquals(91.45, db.getTrackedBalance().currentAmount, 0.01);
        assertEquals(2, db.getTrackedBalanceCorrectionCount());
        assertTrue(add("Ruya", "card_purchase", 11.45, false, "SECOND") > 0);
        assertEquals(80.00, db.getTrackedBalance().currentAmount, 0.01);
        assertEquals(2, db.getAllTransactions().size());
    }

    @Test public void combinedBalanceIncludesAllSourcesAndManualIncome() {
        assertTrue(db.setTrackedBalance(1000, ""));
        assertTrue(add("Ruya", "card_purchase", 80, false, "RUYA") > 0);
        assertTrue(add("ENBD", "card_purchase", 20, false, "ENBD") > 0);
        assertTrue(add("Manual", "income", 150, false, "MANUAL SALARY") > 0);
        assertEquals(1050, db.getTrackedBalance().currentAmount, 0.01);
    }

    @Test public void differentSalaryDateForEachMonthNeverCreditsBalance() {
        assertTrue(db.setTrackedBalance(500, "Ruya"));
        int startingTransactions = db.getAllTransactions().size();
        assertTrue(db.saveSalaryPlan(day(2027, 3, 18), 3200));
        assertTrue(db.saveSalaryPlan(day(2027, 4, 27), 3200));
        assertEquals(2, db.getSalaryPlans().size());
        assertEquals("2027-03", SpendDatabase.salaryMonthKey(day(2027, 3, 18)));
        assertEquals("2027-04", SpendDatabase.salaryMonthKey(day(2027, 4, 27)));

        // Editing a month's date does not create an extra monthly entry.
        assertTrue(db.saveSalaryPlan(day(2027, 3, 22), 3350));
        List<SpendDatabase.SalaryPlan> plans = db.getSalaryPlans();
        assertEquals(2, plans.size());
        assertEquals(3350, plans.get(1).amount, 0.01);
        assertEquals(day(2027, 3, 22), plans.get(1).expectedDate);

        assertTrue(db.markSalaryReceived("2027-03"));
        assertFalse(db.markSalaryReceived("2027-03")); // idempotent confirmation
        assertEquals("received", db.getSalaryPlans().get(1).status);
        assertEquals(startingTransactions, db.getAllTransactions().size());
        assertEquals(500, db.getTrackedBalance().currentAmount, 0.01);

        // Only a recorded deposit alters the balance, exactly once.
        assertTrue(add("Ruya", "income", 3350, false, "BANK CREDIT") > 0);
        assertEquals(3850, db.getTrackedBalance().currentAmount, 0.01);
        assertTrue(db.deleteSalaryPlan("2027-04"));
        assertEquals(1, db.getSalaryPlans().size());
    }

    @Test public void badValuesRejectedAndHistorySurvivesOldSchemaUpgrade() {
        assertFalse(db.setTrackedBalance(Double.NaN, "Ruya"));
        assertFalse(db.setTrackedBalance(10, null));
        assertFalse(db.saveSalaryPlan(day(2027, 2, 15), Double.POSITIVE_INFINITY));
        assertFalse(db.saveSalaryPlan(day(2027, 2, 15), 0));

        assertTrue(add("Ruya", "card_purchase", 12, false, "PRESERVE ME") > 0);
        db.onUpgrade(db.getWritableDatabase(), 6, 9);
        assertEquals(1, db.getAllTransactions().size());
        assertTrue(db.setTrackedBalance(200, "Ruya"));
        assertTrue(db.saveSalaryPlan(day(2027, 2, 15), 500));
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='salary_plans'", null)) {
            assertTrue(c.moveToFirst());
        }
    }

    @Test public void balanceAndSalaryScreenLaunches() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(db.setTrackedBalance(125, "Ruya"));
        assertTrue(db.saveSalaryPlan(day(2027, 2, 20), 3000));
        try (ActivityScenario<MoneyTrackerActivity> scenario =
                     ActivityScenario.launch(MoneyTrackerActivity.class)) {
            scenario.onActivity(activity ->
                    assertNotNull(activity.findViewById(android.R.id.content)));
        }
    }

    @Test public void excelIncludesSalaryCalendarAndBalanceCorrections() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(db.setTrackedBalance(741.15, "Ruya"));
        assertTrue(db.saveSalaryPlan(day(2027, 7, 19), 4250));
        java.io.File output = new java.io.File(
                context.getCacheDir(), "balance-salary-export-test.xlsx");
        try {
            ExcelHistory.export(context, android.net.Uri.fromFile(output));
            boolean salary = false, balance = false;
            int sheets = 0;
            try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
                    new java.io.FileInputStream(output))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.getName().matches("xl/worksheets/sheet[1-9].xml"))
                        sheets++;
                    if (entry.getName().equals("xl/worksheets/sheet8.xml")
                            || entry.getName().equals("xl/worksheets/sheet9.xml")) {
                        java.io.ByteArrayOutputStream bytes =
                                new java.io.ByteArrayOutputStream();
                        byte[] buffer = new byte[4096];
                        int n;
                        while ((n = zip.read(buffer)) != -1)
                            bytes.write(buffer, 0, n);
                        String xml = bytes.toString("UTF-8");
                        if (entry.getName().endsWith("sheet8.xml"))
                            salary = xml.contains("2027-07") && xml.contains("4250");
                        if (entry.getName().endsWith("sheet9.xml"))
                            balance = xml.contains("Ruya") && xml.contains("741.15");
                    }
                    zip.closeEntry();
                }
            }
            assertEquals(9, sheets);
            assertTrue("Salary plan must be exported", salary);
            assertTrue("Account balance correction must be exported", balance);
        } finally {
            output.delete();
        }
    }

}
