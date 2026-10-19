package com.hossam.spendwatch;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SavingsGoalsInstrumentedTest {
    private Context context;
    private SpendDatabase db;

    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = SpendDatabase.getInstance(context);
        for (SavingsGoal g : db.getSavingsGoals()) db.deleteSavingsGoal(g.getId());
    }

    @After public void tearDown() {
        for (SavingsGoal g : db.getSavingsGoals()) db.deleteSavingsGoal(g.getId());
    }

    @Test public void createsAndContributesToSavingsGoal() {
        long id = db.saveSavingsGoal(0L, "Emergency savings", 300.00);
        assertTrue(id > 0);
        assertTrue(db.changeSavingsGoalBalance(id, 100));
        SavingsGoal g = db.getSavingsGoals().get(0);
        assertEquals("Emergency savings", g.getName());
        assertEquals(300d, g.getTargetAmount(), 0.001);
        assertEquals(100d, g.getSavedAmount(), 0.001);
        assertEquals(200d, g.getRemaining(), 0.001);
        assertEquals(100d, db.getTotalSavedForGoals(), 0.001);
    }

    @Test public void withdrawalNeverAllowsNegativeSavings() {
        long id = db.saveSavingsGoal(0L, "Trip", 500);
        assertTrue(db.changeSavingsGoalBalance(id, 50));
        assertFalse(db.changeSavingsGoalBalance(id, -51));
        assertTrue(db.changeSavingsGoalBalance(id, -30));
        assertEquals(20, db.getSavingsGoals().get(0).getSavedAmount(), 0.001);
    }

    @Test public void goalCanBeEditedAndDeletedWithoutChangingSavings() {
        long id = db.saveSavingsGoal(0L, "Old goal", 1000);
        assertTrue(db.changeSavingsGoalBalance(id, 300));
        assertEquals(id, db.saveSavingsGoal(id, "Renamed goal", 1200));
        SavingsGoal g = db.getSavingsGoals().get(0);
        assertEquals("Renamed goal", g.getName());
        assertEquals(1200, g.getTargetAmount(), 0.001);
        assertEquals(300, g.getSavedAmount(), 0.001);
        assertTrue(db.deleteSavingsGoal(id));
        assertTrue(db.getSavingsGoals().isEmpty());
    }

    @Test public void goalsRemainSeparateFromSpendingMath() {
        double spendingBefore = db.getTotalSpentAedThisMonth();
        long id = db.saveSavingsGoal(0L, "Laptop", 5000);
        assertTrue(db.changeSavingsGoalBalance(id, 400));
        assertEquals(spendingBefore, db.getTotalSpentAedThisMonth(), 0.001);
    }

    @Test public void invalidGoalValuesCannotCorruptDatabase() {
        assertEquals(-1, db.saveSavingsGoal(0L, "Bad", -200));
        assertEquals(-1, db.saveSavingsGoal(0L, "", 500));
        assertEquals(-1, db.saveSavingsGoal(0L, "Bad", Double.NaN));
        long id = db.saveSavingsGoal(0L, "Savings", 300);
        assertFalse(db.changeSavingsGoalBalance(id, Double.POSITIVE_INFINITY));
        assertFalse(db.changeSavingsGoalBalance(id, Double.NaN));
        assertFalse(db.changeSavingsGoalBalance(id, 0));
        assertEquals(0, db.getSavingsGoals().get(0).getSavedAmount(), 0.001);
    }

    @Test public void percentageMatchesBudgetAndGoalExamples() {
        assertEquals(40, DonutProgressView.percentOf(20, 50)); // budget: 30 spent, 20 left
        assertEquals(38, DonutProgressView.percentOf(19, 50)); // spend 1 AED more
        assertEquals(60, DonutProgressView.percentOf(300, 500)); // savings progress
        assertEquals(0, DonutProgressView.percentOf(-10, 50));
        assertEquals(100, DonutProgressView.percentOf(99, 50));
        assertEquals(0, DonutProgressView.percentOf(30, 0));
    }

    @Test public void donutActuallyRendersBothStates() {
        final AtomicReference<Throwable> error = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                DonutProgressView ring = new DonutProgressView(context);
                ring.configure(20, 50, "LEFT", UiKit.GREEN);
                assertEquals(40, ring.getDisplayedPercent());
                int measure = View.MeasureSpec.makeMeasureSpec(UiKit.dp(context, 108),
                        View.MeasureSpec.EXACTLY);
                ring.measure(measure, measure);
                ring.layout(0, 0, ring.getMeasuredWidth(), ring.getMeasuredHeight());
                Bitmap bitmap = Bitmap.createBitmap(ring.getWidth(), ring.getHeight(),
                        Bitmap.Config.ARGB_8888);
                ring.draw(new Canvas(bitmap));
                assertTrue("Donut should render visible pixels", bitmap.getPixel(
                        ring.getWidth()/2, UiKit.dp(context, 14)) != 0);
                bitmap.recycle();
                ring.configure(300, 500, "SAVED", UiKit.PURPLE);
                assertEquals(60, ring.getDisplayedPercent());
            } catch (Throwable t) { error.set(t); }
        });
        if (error.get() != null) throw new AssertionError(error.get());
    }

    @Test public void upgradingAnExistingV5DatabaseCreatesGoalsTable() {
        int transactionCount = db.getAllTransactions().size();
        // Simulate a device upgraded from v4.2: it has no goals table yet.
        db.getWritableDatabase().execSQL("DROP TABLE IF EXISTS savings_goals");
        db.onUpgrade(db.getWritableDatabase(), 5, 6);
        assertEquals(transactionCount, db.getAllTransactions().size());
        long id = db.saveSavingsGoal(0L, "After-upgrade savings", 200);
        assertTrue(id > 0);
        assertEquals(1, db.getSavingsGoals().size());
    }

    @Test public void migrationFromV5PreservesSavedGoalsAndExistingRecords() {
        long id = db.saveSavingsGoal(0L, "Emergency", 300);
        assertTrue(db.changeSavingsGoalBalance(id, 80));
        int txCount = db.getAllTransactions().size();
        db.onUpgrade(db.getWritableDatabase(), 5, 6);
        assertEquals(txCount, db.getAllTransactions().size());
        assertEquals(1, db.getSavingsGoals().size());
        assertEquals(80, db.getSavingsGoals().get(0).getSavedAmount(), 0.001);
    }
}
