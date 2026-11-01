package com.hossam.spendwatch;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LedgerAndCycleInstrumentedTest {
    private Context context;
    private SpendDatabase db;
    private final List<Long> transactionIds = new ArrayList<>();
    private final List<Long> envelopeIds = new ArrayList<>();
    private final List<Long> goalIds = new ArrayList<>();

    @Before public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = SpendDatabase.getInstance(context);
        db.setBudgetStartDay(1);
        db.setSetting("budget_reset_at", "0");
    }

    @After public void cleanup() {
        for (Long id : transactionIds) db.deleteTransaction(id);
        for (Long id : envelopeIds) db.deleteBudget(id);
        for (Long id : goalIds) db.deleteSavingsGoal(id);
        db.getWritableDatabase().delete("reset_history", null, null);
        db.setSetting("budget_reset_at", "0");
        db.setBudgetStartDay(1);
    }

    private Transaction purchase(String name, String category, double amount, long millis) {
        Transaction tx = new Transaction();
        tx.setBankSource("Ruya");
        tx.setCurrency("AED");
        tx.setAmount(amount);
        tx.setMerchant(name);
        tx.setRawMessage("TEST: " + name);
        tx.setCategory(category);
        tx.setTransactionType("card_purchase");
        tx.setTimestamp(millis);
        return tx;
    }

    private long insert(Transaction t) {
        long id = db.insertTransaction(t);
        assertTrue("Test transaction must be inserted", id > 0);
        transactionIds.add(id);
        return id;
    }

    private long envelope(String name, double monthly) {
        BudgetEnvelope b = new BudgetEnvelope(0, name, monthly, false, 0, false);
        long id = db.saveBudget(b);
        assertTrue(id > 0);
        envelopeIds.add(id);
        return id;
    }

    @Test public void customCycleBoundaryWorksAcrossMonthEnds() {
        db.setBudgetStartDay(15);
        Calendar day = Calendar.getInstance();
        day.set(2026, Calendar.OCTOBER, 14, 12, 0, 0);
        assertEquals(15, db.getBudgetStartDay());
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(db.getCycleStart(day.getTimeInMillis()));
        assertEquals(Calendar.SEPTEMBER, start.get(Calendar.MONTH));
        assertEquals(15, start.get(Calendar.DAY_OF_MONTH));

        day.set(2026, Calendar.OCTOBER, 15, 12, 0, 0);
        start.setTimeInMillis(db.getCycleStart(day.getTimeInMillis()));
        assertEquals(Calendar.OCTOBER, start.get(Calendar.MONTH));
        assertEquals(15, start.get(Calendar.DAY_OF_MONTH));

        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(db.getCycleEnd(day.getTimeInMillis()));
        assertEquals(Calendar.NOVEMBER, end.get(Calendar.MONTH));
        assertEquals(15, end.get(Calendar.DAY_OF_MONTH));

        // Leap-day / shorter-month rules clamp to each month's last day.
        db.setBudgetStartDay(31);
        day.set(2028, Calendar.FEBRUARY, 28, 12, 0, 0);
        start.setTimeInMillis(db.getCycleStart(day.getTimeInMillis()));
        assertEquals(Calendar.JANUARY, start.get(Calendar.MONTH));
        assertEquals(31, start.get(Calendar.DAY_OF_MONTH));
        day.set(2028, Calendar.FEBRUARY, 29, 12, 0, 0);
        start.setTimeInMillis(db.getCycleStart(day.getTimeInMillis()));
        assertEquals(Calendar.FEBRUARY, start.get(Calendar.MONTH));
        assertEquals(29, start.get(Calendar.DAY_OF_MONTH));
        end.setTimeInMillis(db.getCycleEnd(day.getTimeInMillis()));
        assertEquals(Calendar.MARCH, end.get(Calendar.MONTH));
        assertEquals(31, end.get(Calendar.DAY_OF_MONTH));
    }

    @Test public void envelopeOrderCanMoveUpAndDownPersistently() {
        long first = envelope("QA ORDER A", 100);
        long second = envelope("QA ORDER B", 100);
        List<BudgetEnvelope> before = db.getBudgets();
        int p1 = position(before, first);
        int p2 = position(before, second);
        assertTrue(p2 > p1);
        assertTrue(db.moveBudget(second, -1));
        List<BudgetEnvelope> after = db.getBudgets();
        assertEquals(p1, position(after, second));
        assertEquals(p1 + 1, position(after, first));
        assertTrue(db.moveBudget(second, 1));
        assertEquals(p2, position(db.getBudgets(), second));
    }

    private int position(List<BudgetEnvelope> budgets, long id) {
        for (int i=0;i<budgets.size();i++)
            if (budgets.get(i).getId() == id) return i;
        return -1;
    }

    @Test public void budgetResetRestartsEnvelopeButKeepsOldTransactions() {
        String category = "QA RESET FOOD";
        long envelopeId = envelope(category, 100);
        long firstId = insert(purchase("QA RESET FIRST", category, 30, System.currentTimeMillis()));
        double totalBefore = db.getTotalSpentAedThisMonth();
        assertEquals(30, db.getSpentForCategoryThisMonth(category), 0.01);

        db.resetBudgetsPreservingHistory();
        assertEquals(0, db.getSpentForCategoryThisMonth(category), 0.01);
        assertEquals(totalBefore, db.getTotalSpentAedThisMonth(), 0.01);
        assertTrue(db.getResetHistoryRows().size() > 0);

        insert(purchase("QA RESET SECOND", category, 11, System.currentTimeMillis()));
        assertEquals(11, db.getSpentForCategoryThisMonth(category), 0.01);
        assertEquals(totalBefore + 11, db.getTotalSpentAedThisMonth(), 0.01);
    }

    @Test public void goalsResetIndependentlyAndRetainTheirPriorBalancesInHistory() {
        long id = db.saveSavingsGoal(0, "QA RESET GOAL", 500);
        assertTrue(id > 0);
        goalIds.add(id);
        assertTrue(db.changeSavingsGoalBalance(id, 185));
        db.resetGoalsPreservingHistory();
        boolean found = false;
        for (SavingsGoal g : db.getSavingsGoals()) {
            if (g.getId() == id) {
                assertEquals(0, g.getSavedAmount(), 0.01);
                assertEquals(500, g.getTargetAmount(), 0.01);
                found = true;
            }
        }
        assertTrue(found);
        boolean archived = false;
        for (String[] event : db.getResetHistoryRows()) {
            if (event[1].equals("GOAL") && event[2].equals("QA RESET GOAL")) {
                assertEquals(185, Double.parseDouble(event[3]), 0.01);
                archived = true;
            }
        }
        assertTrue("Goal reset should be archived", archived);
    }

    @Test public void realXlsxIncludesAncientRecordsAndAllFiveSheets() throws Exception {
        // Old transaction must remain in the export even though it is not in the active cycle.
        Calendar historic = Calendar.getInstance();
        historic.set(2023, Calendar.JANUARY, 10, 10, 30, 0);
        insert(purchase("QA OLD ARCHIVE 2023", "Groceries", 17.55,
                historic.getTimeInMillis()));
        insert(purchase("QA NOW TRANSACTION", "Groceries", 11.25,
                System.currentTimeMillis()));
        long goalId = db.saveSavingsGoal(0, "QA XLSX GOAL", 100);
        goalIds.add(goalId);
        db.changeSavingsGoalBalance(goalId, 40);
        db.resetGoalsPreservingHistory();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        LocalLedgerExporter.writeWorkbook(context, output);
        Map<String,String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(output.toByteArray()))) {
            ZipEntry e;
            while ((e=zip.getNextEntry()) != null) {
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n=zip.read(buffer)) > 0) data.write(buffer,0,n);
                entries.put(e.getName(), new String(data.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        assertTrue(entries.containsKey("xl/workbook.xml"));
        for(int i=1;i<=5;i++) {
            String name = "xl/worksheets/sheet"+i+".xml";
            assertTrue(name,entries.containsKey(name));
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(entries.get(name).getBytes(StandardCharsets.UTF_8)));
        }
        String sheet = entries.get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("QA OLD ARCHIVE 2023"));
        assertTrue(sheet.contains("QA NOW TRANSACTION"));
        assertTrue(entries.get("xl/worksheets/sheet2.xml").contains("2023-01"));
        assertTrue(entries.get("xl/worksheets/sheet5.xml").contains("QA XLSX GOAL"));
    }
}
