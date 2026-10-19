package com.hossam.spendwatch;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MerchantLearningInstrumentedTest {

    private SpendDatabase db;

    @Before
    public void setup() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = SpendDatabase.getInstance(context);
        db.getWritableDatabase().delete("transactions", null, null);
        db.forgetLearnedMerchants();
    }

    @After
    public void cleanup() {
        db.getWritableDatabase().delete("transactions", null, null);
        db.forgetLearnedMerchants();
    }

    private Transaction transaction(String merchant, String bank, String category, String type, double amount) {
        Transaction t = new Transaction();
        t.setMerchant(merchant);
        t.setBankSource(bank);
        t.setCurrency("AED");
        t.setAmount(amount);
        t.setCategory(category);
        t.setTransactionType(type);
        t.setTimestamp(System.currentTimeMillis());
        t.setRawMessage("Test purchase at " + merchant);
        return t;
    }

    @Test
    public void correctionForHospitalCanteenIsUsedOnNextPurchase() {
        Transaction first = transaction("NMC HOSPITAL CANTEEN", "Ruya",
                "General", "card_purchase", 15.0);
        assertTrue(db.insertTransaction(first) > 0);
        assertTrue(db.updateTransactionAndLearn(first.getId(), "Food & Dining",
                "card_purchase", false, true));

        assertEquals(1, db.getLearnedMerchantCount());
        assertEquals("Food & Dining", db.getLearnedCategory("Ruya", "NMC HOSPITAL CANTEEN"));

        Transaction next = transaction("nmc hospital-canteen", "Ruya",
                "Health & Pharmacy", "card_purchase", 23.0);
        assertTrue(db.insertTransaction(next) > 0);
        assertEquals("Food & Dining", next.getCategory());
        assertEquals("Food & Dining", db.getAllTransactions().get(0).getCategory());

        // Different merchants and different banks do not inherit this private correction.
        Transaction unrelated = transaction("NMC Hospital Pharmacy", "Ruya",
                "Health & Pharmacy", "card_purchase", 19.0);
        assertTrue(db.insertTransaction(unrelated) > 0);
        assertEquals("Health & Pharmacy", unrelated.getCategory());
        Transaction otherBank = transaction("NMC HOSPITAL CANTEEN", "ADCB",
                "General", "card_purchase", 10.0);
        assertTrue(db.insertTransaction(otherBank) > 0);
        assertEquals("General", otherBank.getCategory());
    }

    @Test
    public void correctionOverridesBuiltInGuessAndNewerCorrectionWins() {
        Transaction first = transaction("TIM HORTONS", "Ruya",
                "Food & Dining", "pos", 13.0);
        assertTrue(db.insertTransaction(first) > 0);
        assertTrue(db.updateTransactionAndLearn(first.getId(), "Shopping", "pos", false, true));

        Transaction next = transaction("TIM HORTONS", "Ruya",
                "Food & Dining", "pos", 22.0);
        assertTrue(db.insertTransaction(next) > 0);
        assertEquals("Shopping", next.getCategory());

        assertTrue(db.updateTransactionAndLearn(next.getId(), "Food & Dining", "pos", false, true));
        Transaction third = transaction("TIM HORTONS", "Ruya",
                "Shopping", "pos", 38.0);
        assertTrue(db.insertTransaction(third) > 0);
        assertEquals("Food & Dining", third.getCategory());
        assertEquals(1, db.getLearnedMerchantCount());
    }

    @Test
    public void cannotLearnGenericMerchantOrNonSpendingTransaction() {
        Transaction generic = transaction("Unknown Merchant", "Ruya",
                "General", "card_purchase", 51.0);
        assertTrue(db.insertTransaction(generic) > 0);
        assertTrue(db.updateTransactionAndLearn(generic.getId(), "Food & Dining",
                "card_purchase", false, true));
        assertEquals(0, db.getLearnedMerchantCount());

        Transaction transfer = transaction("NMC HOSPITAL CANTEEN", "Ruya",
                "Transfers", "transfer_sent", 24.0);
        assertTrue(db.insertTransaction(transfer) > 0);
        assertTrue(db.updateTransactionAndLearn(transfer.getId(), "Food & Dining",
                "transfer_sent", false, true));
        assertEquals(0, db.getLearnedMerchantCount());

        Transaction ignored = transaction("HOSPITAL CANTEEN", "Ruya",
                "General", "card_purchase", 8.0);
        assertTrue(db.insertTransaction(ignored) > 0);
        assertTrue(db.updateTransactionAndLearn(ignored.getId(), "Food & Dining",
                "card_purchase", true, true));
        assertEquals(0, db.getLearnedMerchantCount());
    }

    @Test
    public void uncheckingRememberDoesNotCreateRule() {
        Transaction first = transaction("STAFF CANTEEN", "Ruya",
                "General", "card_purchase", 17.0);
        assertTrue(db.insertTransaction(first) > 0);
        assertTrue(db.updateTransactionAndLearn(first.getId(), "Food & Dining",
                "card_purchase", false, false));
        assertEquals(0, db.getLearnedMerchantCount());
        Transaction next = transaction("Staff Canteen", "Ruya",
                "General", "card_purchase", 27.0);
        assertTrue(db.insertTransaction(next) > 0);
        assertEquals("General", next.getCategory());
    }

    @Test
    public void learnedPreferencesCanBeForgottenWithoutErasingHistory() {
        Transaction t = transaction("STAFF CANTEEN", "Ruya", "General", "card_purchase", 17.0);
        assertTrue(db.insertTransaction(t) > 0);
        assertTrue(db.updateTransactionAndLearn(t.getId(), "Food & Dining", "card_purchase", false, true));
        assertNotNull(db.getLearnedCategory("Ruya", "STAFF CANTEEN"));
        db.forgetLearnedMerchants();
        assertNull(db.getLearnedCategory("Ruya", "STAFF CANTEEN"));
        assertEquals(1, db.getAllTransactions().size());
        assertEquals("Food & Dining", db.getAllTransactions().get(0).getCategory());
    }

    @Test
    public void upgradingV4ToV5KeepsExistingTransactions() {
        Transaction t = transaction("NMC HOSPITAL CANTEEN", "Ruya",
                "General", "card_purchase", 14.0);
        assertTrue(db.insertTransaction(t) > 0);
        SQLiteDatabase writable = db.getWritableDatabase();
        writable.execSQL("DROP TABLE IF EXISTS merchant_category_rules");
        db.onUpgrade(writable, 4, 5);
        assertEquals(1, db.getAllTransactions().size());
        assertEquals(0, db.getLearnedMerchantCount());
        assertTrue(db.updateTransactionAndLearn(t.getId(), "Food & Dining",
                "card_purchase", false, true));
        assertEquals("Food & Dining", db.getLearnedCategory("Ruya", "NMC HOSPITAL CANTEEN"));
    }
}
