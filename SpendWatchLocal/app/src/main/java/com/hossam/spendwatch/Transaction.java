package com.hossam.spendwatch;

import java.io.Serializable;

public class Transaction implements Serializable {
    private long id;
    private double amount;
    private String currency; // Default "AED"
    private String merchant;
    private long timestamp;
    private String bankSource;
    private String rawMessage;
    private String category;
    private String transactionType; // pos, online, atm, transfer_sent, direct_debit, fee, card_purchase
    private boolean isIgnored;

    public Transaction() {
        this.timestamp = System.currentTimeMillis();
        this.currency = "AED";
        this.category = "General";
        this.transactionType = "card_purchase";
        this.isIgnored = false;
    }

    public Transaction(long id, double amount, String currency, String merchant, long timestamp,
                       String bankSource, String rawMessage, String category, String transactionType, boolean isIgnored) {
        this.id = id;
        this.amount = amount;
        this.currency = (currency != null && !currency.isEmpty()) ? currency : "AED";
        this.merchant = (merchant != null && !merchant.isEmpty()) ? merchant : "Unknown Merchant";
        this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        this.bankSource = (bankSource != null && !bankSource.isEmpty()) ? bankSource : "UAE Bank Alert";
        this.rawMessage = (rawMessage != null) ? rawMessage : "";
        this.category = (category != null && !category.isEmpty()) ? category : "General";
        this.transactionType = (transactionType != null && !transactionType.isEmpty()) ? transactionType : "card_purchase";
        this.isIgnored = isIgnored;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getBankSource() { return bankSource; }
    public void setBankSource(String bankSource) { this.bankSource = bankSource; }

    public String getRawMessage() { return rawMessage; }
    public void setRawMessage(String rawMessage) { this.rawMessage = rawMessage; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public boolean isIgnored() { return isIgnored; }
    public void setIgnored(boolean ignored) { isIgnored = ignored; }
}
