package com.hossam.spendwatch;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.util.Calendar;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AudioRequirementsInstrumentedTest {
    private Context context;
    private SpendDatabase db;

    @Before public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = SpendDatabase.getInstance(context);
        db.clearAll();
        db.setSetting("budget_reset_at", "0");
        db.setCycleStartDay(1);
        for(OneTimeCommitment item:db.getOneTimeCommitments())
            db.deleteOneTimeCommitment(item.getId());
    }

    @After public void teardown() {
        db.clearAll();
        db.setSetting("budget_reset_at", "0");
        db.setCycleStartDay(1);
        for(OneTimeCommitment item:db.getOneTimeCommitments())
            db.deleteOneTimeCommitment(item.getId());
    }

    private Transaction tx(double amount,String merchant,long timestamp) {
        Transaction t=new Transaction();
        t.setAmount(amount);t.setCurrency("AED");t.setMerchant(merchant);
        t.setTimestamp(timestamp);t.setBankSource("Ruya");
        t.setRawMessage("real transaction");t.setCategory("General");
        t.setTransactionType("card_purchase");return t;
    }

    @Test public void percentageDirectionIsIndependentAndPersists() {
        BudgetEnvelope base=null;
        for (BudgetEnvelope b:db.getBudgets()) if ("General".equals(b.getName())) base=b;
        assertNotNull(base);
        base.setMonthlyAmount(100);
        base.setShowSpentProgress(true);
        db.saveBudget(base);
        BudgetEnvelope saved=null;
        for (BudgetEnvelope b:db.getBudgets()) if ("General".equals(b.getName())) saved=b;
        assertNotNull(saved);
        assertTrue(saved.isShowSpentProgress());
        assertEquals(35,DonutProgressView.percentOf(35,100));
        assertEquals(65,DonutProgressView.percentOf(100-35,100));

        saved.setShowSpentProgress(false);
        db.saveBudget(saved);
        for (BudgetEnvelope b:db.getBudgets()) if ("General".equals(b.getName()))
            assertFalse(b.isShowSpentProgress());
    }

    @Test public void futurePaymentReminderIsCalendarAccurateAndNonRecurring() {
        Calendar due=Calendar.getInstance();
        due.set(2027, Calendar.MAY, 15, 9, 0, 0);
        due.set(Calendar.MILLISECOND,0);
        long id=db.saveOneTimeCommitment(0,"Annual insurance",1000,
                due.getTimeInMillis(),-2);
        assertTrue(id>0);
        OneTimeCommitment item=db.getOneTimeCommitment(id);
        assertEquals("pending",item.getStatus());
        Calendar when=Calendar.getInstance();
        when.setTimeInMillis(OneTimeReminderReceiver.reminderTimestamp(item));
        assertEquals(Calendar.MARCH,when.get(Calendar.MONTH));
        assertEquals(15,when.get(Calendar.DAY_OF_MONTH));
        assertEquals(9,when.get(Calendar.HOUR_OF_DAY));
        assertEquals(1000,db.getUpcomingOneTimeCommitmentsTotal(),.001);

        assertTrue(db.setOneTimeCommitmentStatus(id,"paid"));
        assertEquals(0,db.getUpcomingOneTimeCommitmentsTotal(),.001);
        assertEquals("paid",db.getOneTimeCommitment(id).getStatus());
    }

    @Test public void rejectsInvalidPaymentsAndAllowsEditing() {
        assertEquals(-1,db.saveOneTimeCommitment(0,"",1000,System.currentTimeMillis(),-1));
        assertEquals(-1,db.saveOneTimeCommitment(0,"Test",Double.NaN,
                System.currentTimeMillis(),-1));
        assertEquals(-1,db.saveOneTimeCommitment(0,"Test",100,
                System.currentTimeMillis(),-4));
        long due=System.currentTimeMillis()+60*24*3600000L;
        long id=db.saveOneTimeCommitment(0,"Repair",300,due,-1);
        assertTrue(id>0);
        assertEquals(id,db.saveOneTimeCommitment(id,"Repair new",450,due,-2));
        assertEquals(450,db.getOneTimeCommitment(id).getAmount(),.001);
        assertTrue(db.deleteOneTimeCommitment(id));
        assertNull(db.getOneTimeCommitment(id));
    }

    @Test public void onlySmsBroadcastReceiverIsRegisteredAndNoNotificationListener() throws Exception {
        PackageInfo pkg=context.getPackageManager().getPackageInfo(
                context.getPackageName(),
                PackageManager.GET_RECEIVERS | PackageManager.GET_SERVICES
                        | PackageManager.GET_PERMISSIONS);
        boolean hasSms=false;
        assertNotNull(pkg.requestedPermissions);
        for(String perm:pkg.requestedPermissions){
            if ("android.permission.RECEIVE_SMS".equals(perm))hasSms=true;
            assertFalse("WhatsApp-like broad notification access must be absent",
                    "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE".equals(perm));
        }
        assertTrue(hasSms);
        if(pkg.receivers!=null){
            for(android.content.pm.ActivityInfo r:pkg.receivers)
                if(r.name.endsWith(".SmsTransactionReceiver"))hasSms=true;
        }
        assertTrue(hasSms);
        if(pkg.services!=null){
            for(android.content.pm.ServiceInfo service:pkg.services)
                assertFalse("NotificationListenerService must be absent from manifest",
                        service.name.endsWith(".BankNotificationListener"));
        }

        int before=db.getAllTransactions().size();
        Intent fake=new Intent("android.intent.action.NOTIFICATION_POSTED");
        fake.putExtra("text","WhatsApp promotion from Amazon: Ruya purchase AED 49");
        new SmsTransactionReceiver().onReceive(context,fake);
        assertEquals(before,db.getAllTransactions().size());
    }

    @Test public void dailyAndMonthlyHistoryIncludesPreviousYear() {
        Calendar date=Calendar.getInstance();
        date.add(Calendar.YEAR,-1);
        date.set(Calendar.HOUR_OF_DAY,12);
        long old=date.getTimeInMillis();
        assertTrue(db.insertTransaction(tx(21,"HISTORIC PAYMENT",old))>0);
        List<String> days=db.getHistoricalTotals(true,3660);
        List<String> months=db.getHistoricalTotals(false,240);
        String dateKey=new java.text.SimpleDateFormat("yyyy-MM-dd",
                java.util.Locale.US).format(date.getTime());
        assertTrue(days.toString().contains(dateKey));
        assertTrue(months.toString().contains(dateKey.substring(0,7)));
    }

    @Test public void workbookIncludesDailySummaryAndPlannedCommitments() throws Exception {
        long id=db.saveOneTimeCommitment(0,"ANNUAL INSURANCE QA",275.5,
                System.currentTimeMillis()+100000000L,-1);
        assertTrue(id>0);
        assertTrue(db.insertTransaction(tx(35,"QA HISTORICAL",System.currentTimeMillis()))>0);
        File output=new File(context.getCacheDir(),"audio-changes-report.xlsx");
        try {
            ExcelHistory.export(context,Uri.fromFile(output));
            assertTrue(output.length()>1600);
            Set<String> entries=new HashSet<>();
            boolean foundCommitment=false,foundTransaction=false;
            try(ZipInputStream zip=new ZipInputStream(new FileInputStream(output))) {
                ZipEntry e;
                while((e=zip.getNextEntry())!=null){
                    entries.add(e.getName());
                    if(e.getName().equals("xl/worksheets/sheet7.xml")
                            ||e.getName().equals("xl/worksheets/sheet1.xml")){
                        java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream();
                        byte[] buffer=new byte[4096];int n;
                        while((n=zip.read(buffer))!=-1)b.write(buffer,0,n);
                        String text=b.toString("UTF-8");
                        if(e.getName().endsWith("sheet7.xml"))
                            foundCommitment=text.contains("ANNUAL INSURANCE QA");
                        else foundTransaction=text.contains("QA HISTORICAL");
                    }
                    zip.closeEntry();
                }
            }
            for(int i=1;i<=7;i++)
                assertTrue("Missing Excel worksheet "+i,
                        entries.contains("xl/worksheets/sheet"+i+".xml"));
            assertTrue(foundCommitment);
            assertTrue(foundTransaction);
        } finally {output.delete();}
    }

    @Test public void upgradeFromV7KeepsHistoryAndCreatesNewTables() {
        int count=db.getAllTransactions().size();
        db.onUpgrade(db.getWritableDatabase(),7,8);
        assertEquals(count,db.getAllTransactions().size());
        long id=db.saveOneTimeCommitment(0,"After migration",100,
                System.currentTimeMillis()+200000L,-1);
        assertTrue(id>0);
        assertEquals(1,db.getOneTimeCommitments().size());
        try(Cursor cursor=db.getReadableDatabase().rawQuery(
                "SELECT progress_mode FROM budgets LIMIT 1",null)){
            assertTrue(cursor.moveToFirst());
        }
    }

    @Test public void futurePaymentsAndHistoryScreensLaunch() {
        try(ActivityScenario<CommitmentActivity> s=ActivityScenario.launch(
                CommitmentActivity.class)){
            s.onActivity(a->assertNotNull(a.findViewById(android.R.id.content)));
        }
        try(ActivityScenario<HistoryActivity> s=ActivityScenario.launch(
                HistoryActivity.class)){
            s.onActivity(a->assertNotNull(a.findViewById(android.R.id.content)));
        }
    }
}
