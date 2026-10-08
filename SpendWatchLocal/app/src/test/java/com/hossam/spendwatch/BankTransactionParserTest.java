package com.hossam.spendwatch;

import org.junit.Test;

import static org.junit.Assert.*;

public class BankTransactionParserTest {

    @Test
    public void parsesStandardRuyaCardPurchase() {
        Transaction t = BankTransactionParser.parseNotification(
                "Ruya",
                "Your Ruya card ending 4091 was used for AED 142.50 at CARREFOUR DEIRA.",
                "ae.ruya.digital");
        assertNotNull(t);
        assertEquals("Ruya", t.getBankSource());
        assertEquals(142.50, t.getAmount(), 0.001);
        assertEquals("AED", t.getCurrency());
        assertEquals("card_purchase", t.getTransactionType());
        assertEquals("Groceries", t.getCategory());
        assertTrue(t.getMerchant().toLowerCase().contains("carrefour"));
    }

    @Test
    public void parsesRuyaPaymentVariant() {
        Transaction t = BankTransactionParser.parseNotification(
                "Ruya Bank",
                "Payment of AED 32.00 successful at TALABAT.",
                "com.messaging.app");
        assertNotNull(t);
        assertEquals(32.00, t.getAmount(), 0.001);
        assertEquals("Food & Dining", t.getCategory());
    }

    @Test
    public void parsesAmountBeforeCurrency() {
        Transaction t = BankTransactionParser.parseNotification(
                "RUYA",
                "Card transaction for 49.75 AED at STARBUCKS completed successfully.",
                "com.messaging.app");
        assertNotNull(t);
        assertEquals(49.75, t.getAmount(), 0.001);
        assertEquals("AED", t.getCurrency());
        assertEquals("Food & Dining", t.getCategory());
    }

    @Test
    public void rejectsDeclinedTransaction() {
        assertNull(BankTransactionParser.parseNotification(
                "Ruya",
                "Card purchase of AED 200.00 at TEST MERCHANT was declined.",
                "ae.ruya.digital"));
    }

    @Test
    public void rejectsOtp() {
        assertNull(BankTransactionParser.parseNotification(
                "Ruya",
                "Your OTP for AED 100.00 transaction is 123456.",
                "ae.ruya.digital"));
    }

    @Test
    public void classifiesIncomingMoney() {
        Transaction t = BankTransactionParser.parseNotification(
                "Ruya",
                "AED 1500.00 has been credited to your account.",
                "ae.ruya.digital");
        assertNotNull(t);
        assertEquals("income", t.getTransactionType());
        assertEquals("Income", t.getCategory());
    }

    @Test
    public void classifiesOutgoingTransfer() {
        Transaction t = BankTransactionParser.parseNotification(
                "Ruya",
                "Outward transfer AED 325.00 sent to AHMED.",
                "ae.ruya.digital");
        assertNotNull(t);
        assertEquals("transfer_sent", t.getTransactionType());
        assertEquals("Transfers", t.getCategory());
    }

    @Test
    public void classifiesRefund() {
        Transaction t = BankTransactionParser.parseNotification(
                "Ruya",
                "Refund AED 75.25 from CARREFOUR has been credited.",
                "ae.ruya.digital");
        assertNotNull(t);
        assertEquals("refund", t.getTransactionType());
        assertEquals("Refunds", t.getCategory());
    }

    @Test
    public void ignoresUnknownNonBankNotification() {
        assertNull(BankTransactionParser.parseNotification(
                "Shopping app",
                "Payment AED 25.00 completed.",
                "com.example.shopping"));
    }
}
