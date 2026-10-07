package com.hossam.spendwatch;

public class RecurringBill {
    private long id;
    private String name;
    private double amount;
    private String currency;
    private int dueDay;
    private String category;
    private boolean active;

    public RecurringBill(long id, String name, double amount, String currency, int dueDay,
                         String category, boolean active) {
        this.id = id;
        this.name = name;
        this.amount = amount;
        this.currency = currency;
        this.dueDay = dueDay;
        this.category = category;
        this.active = active;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public double getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public int getDueDay() { return dueDay; }
    public String getCategory() { return category; }
    public boolean isActive() { return active; }

    public void setId(long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setAmount(double amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setDueDay(int dueDay) { this.dueDay = dueDay; }
    public void setCategory(String category) { this.category = category; }
    public void setActive(boolean active) { this.active = active; }
}
