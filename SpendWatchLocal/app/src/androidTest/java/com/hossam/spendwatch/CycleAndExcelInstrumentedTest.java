package com.hossam.spendwatch;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.util.Calendar;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CycleAndExcelInstrumentedTest {
    private Context context;
    private SpendDatabase db;

    @Before public void setup() {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        db=SpendDatabase.getInstance(context);
        db.clearAll();
        db.setSetting("budget_reset_at","0");
        db.setCycleStartDay(1);
        for(SavingsGoal goal:db.getSavingsGoals())db.deleteSavingsGoal(goal.getId());
    }
    @After public void cleanup() {
        db.clearAll();
        db.setSetting("budget_reset_at","0");
        db.setCycleStartDay(1);
        for(SavingsGoal goal:db.getSavingsGoals())db.deleteSavingsGoal(goal.getId());
        ExcelHistory.setContinuousDestination(context,null);
    }
    private Transaction purchase(double amount,String category,long timestamp){
        Transaction t=new Transaction();
        t.setAmount(amount);t.setCategory(category);t.setTimestamp(timestamp);
        t.setBankSource("Ruya");t.setCurrency("AED");
        t.setMerchant("TEST MERCHANT "+amount);
        t.setRawMessage("test");t.setTransactionType("card_purchase");return t;
    }

    @Test public void moveEnvelopeOrderIsPersisted() {
        List<BudgetEnvelope> budgets=db.getBudgets();
        assertTrue(budgets.size()>1);
        long first=budgets.get(0).getId(), second=budgets.get(1).getId();
        assertTrue(db.moveBudget(second,-1));
        assertEquals(second,db.getBudgets().get(0).getId());
        assertEquals(first,db.getBudgets().get(1).getId());
        assertTrue(db.moveBudget(second,1));
    }

    @Test public void cycleStartUsesCustomDayAndKeepsPastTransactions() {
        db.setCycleStartDay(15);
        Calendar c=Calendar.getInstance();
        c.setTimeInMillis(db.getCycleStartTimestamp());
        assertEquals(15,c.get(Calendar.DAY_OF_MONTH));
        assertTrue(db.insertTransaction(purchase(43,"General",System.currentTimeMillis()))>0);
        assertEquals(43,db.getTotalSpentAedThisMonth(),.001);
        assertEquals(43,db.getSpentForCategoryThisMonth("General"),.001);
    }

    @Test public void envelopeResetArchivesWithoutDeletingTransactions() {
        assertTrue(db.insertTransaction(purchase(71,"General",System.currentTimeMillis()))>0);
        int before=db.getAllTransactions().size();
        db.softResetEnvelopes();
        assertEquals(before,db.getAllTransactions().size());
        assertEquals(0,db.getSpentForCategoryThisMonth("General"),.001);
        try(Cursor c=db.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM cycle_archive WHERE kind='envelope' AND name='General'",null)){
            assertTrue(c.moveToFirst()); assertTrue(c.getInt(0)>0);
        }
    }

    @Test public void goalsResetIndependentlyAndArchivesSavedAmounts() {
        long id=db.saveSavingsGoal(0,"TEST RESET GOAL",500);
        assertTrue(id>0);
        assertTrue(db.changeSavingsGoalBalance(id,125));
        assertTrue(db.insertTransaction(purchase(17,"General",System.currentTimeMillis()))>0);
        db.softResetGoals();
        assertEquals(0,db.getSavingsGoals().get(0).getSavedAmount(),.001);
        assertEquals(17,db.getTotalSpentAedThisMonth(),.001);
        try(Cursor c=db.getReadableDatabase().rawQuery(
                "SELECT spent_or_saved FROM cycle_archive WHERE kind='goal' AND name='TEST RESET GOAL' ORDER BY id DESC LIMIT 1",null)){
            assertTrue(c.moveToFirst());assertEquals(125,c.getDouble(0),.001);
        }
    }

    @Test public void excelWorkbookContainsOldHistoryAndSnapshots() throws Exception {
        Calendar old=Calendar.getInstance();
        old.add(Calendar.MONTH,-8);
        assertTrue(db.insertTransaction(purchase(66,"General",old.getTimeInMillis()))>0);
        assertTrue(db.insertTransaction(purchase(12,"General",System.currentTimeMillis()))>0);
        db.softResetEnvelopes();
        File export=new File(context.getCacheDir(),"spendwatch-report-test.xlsx");
        try {
            ExcelHistory.export(context,Uri.fromFile(export));
            assertTrue(export.length()>1500);
            boolean transactions=false,monthly=false,archives=false,marker=false;
            try(ZipInputStream z=new ZipInputStream(new FileInputStream(export))){
                ZipEntry entry;
                while((entry=z.getNextEntry())!=null){
                    String name=entry.getName();
                    if(name.equals("xl/worksheets/sheet1.xml")){
                        transactions=true;
                        java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream();
                        byte[] buf=new byte[4096];int len;
                        while((len=z.read(buf))>0)b.write(buf,0,len);
                        String xml=b.toString("UTF-8");
                        marker=xml.contains("66.0")&&xml.contains("12.0");
                    }
                    if(name.equals("xl/worksheets/sheet2.xml"))monthly=true;
                    if(name.equals("xl/worksheets/sheet5.xml"))archives=true;
                    z.closeEntry();
                }
            }
            assertTrue(transactions&&monthly&&archives&&marker);
        } finally {export.delete();}
    }
}
