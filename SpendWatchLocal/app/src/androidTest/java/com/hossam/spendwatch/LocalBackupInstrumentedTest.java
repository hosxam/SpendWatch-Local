package com.hossam.spendwatch;

import android.content.Context;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LocalBackupInstrumentedTest {
    private Context context;
    private SpendDatabase db;

    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = SpendDatabase.getInstance(context);
    }

    private Transaction makePurchase(String merchant, double amount) {
        Transaction tx = new Transaction();
        tx.setBankSource("Ruya");
        tx.setCurrency("AED");
        tx.setAmount(amount);
        tx.setMerchant(merchant);
        tx.setRawMessage("Backup test " + merchant);
        tx.setCategory("Food & Dining");
        tx.setTransactionType("card_purchase");
        tx.setTimestamp(System.currentTimeMillis());
        return tx;
    }

    @Test public void fullBackupRestoresTransactionsGoalsAndMonthlyIncome() throws Exception {
        db.setMonthlyIncome(2345);
        Transaction t = makePurchase("SPENDWATCH BACKUP FIXTURE", 19.65);
        long id = db.insertTransaction(t);
        assertTrue(id > 0);
        long goalId = db.saveSavingsGoal(0, "BACKUP GOAL FIXTURE", 500);
        assertTrue(goalId > 0);
        assertTrue(db.changeSavingsGoalBalance(goalId, 112.50));

        File output = new File(context.getCacheDir(), "spendwatch-roundtrip-test.zip");
        try {
            LocalBackupManager.exportBackup(context, Uri.fromFile(output));
            assertTrue(output.length() > 100);
            db.deleteTransaction(id);
            db.deleteSavingsGoal(goalId);
            db.setMonthlyIncome(100);

            LocalBackupManager.importBackup(context, Uri.fromFile(output));
            assertEquals(2345, db.getMonthlyIncome(), .001);

            boolean foundTransaction = false;
            for (Transaction row : db.getAllTransactions()) {
                if (row.getId() == id) {
                    foundTransaction = true;
                    assertEquals("SPENDWATCH BACKUP FIXTURE", row.getMerchant());
                    assertEquals(19.65, row.getAmount(), .001);
                }
            }
            assertTrue("Backup must restore transaction history", foundTransaction);
            boolean foundGoal = false;
            for (SavingsGoal goal : db.getSavingsGoals()) {
                if (goal.getId() == goalId) {
                    foundGoal = true;
                    assertEquals(112.50, goal.getSavedAmount(), .001);
                }
            }
            assertTrue("Backup must restore savings goals", foundGoal);
        } finally {
            db.deleteTransaction(id);
            db.deleteSavingsGoal(goalId);
            db.setMonthlyIncome(0);
            output.delete();
        }
    }

    @Test public void corruptBackupIsRejectedWithoutChangingCurrentHistory() throws Exception {
        Transaction t = makePurchase("KEEP THIS TRANSACTION", 26.10);
        long id = db.insertTransaction(t);
        assertTrue(id > 0);
        File corrupt = new File(context.getCacheDir(), "spendwatch-invalid.zip");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(corrupt))) {
                zip.putNextEntry(new ZipEntry("databases/spendwatch_uae.db"));
                zip.write("NOT A DATABASE".getBytes("UTF-8"));
                zip.closeEntry();
            }
            boolean failed = false;
            try {
                LocalBackupManager.importBackup(context, Uri.fromFile(corrupt));
            } catch (Exception expected) {
                failed = true;
            }
            assertTrue("Invalid backup must be rejected", failed);
            boolean stillThere = false;
            for (Transaction row : db.getAllTransactions()) {
                if (row.getId() == id) stillThere = true;
            }
            assertTrue("Existing financial records must survive failed import", stillThere);
        } finally {
            db.deleteTransaction(id);
            corrupt.delete();
        }
    }
}
