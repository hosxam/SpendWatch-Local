package com.hossam.spendwatch;

public class BudgetEnvelope {
    private long id;
    private String name;
    private double monthlyAmount;
    private boolean rolloverEnabled;
    private double carryAmount;
    private boolean sinkingFund;

    public BudgetEnvelope(long id, String name, double monthlyAmount, boolean rolloverEnabled,
                          double carryAmount, boolean sinkingFund) {
        this.id = id;
        this.name = name;
        this.monthlyAmount = monthlyAmount;
        this.rolloverEnabled = rolloverEnabled;
        this.carryAmount = carryAmount;
        this.sinkingFund = sinkingFund;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public double getMonthlyAmount() { return monthlyAmount; }
    public boolean isRolloverEnabled() { return rolloverEnabled; }
    public double getCarryAmount() { return carryAmount; }
    public boolean isSinkingFund() { return sinkingFund; }
    public double getEffectiveAmount() { return monthlyAmount + carryAmount; }

    public void setId(long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setMonthlyAmount(double monthlyAmount) { this.monthlyAmount = monthlyAmount; }
    public void setRolloverEnabled(boolean rolloverEnabled) { this.rolloverEnabled = rolloverEnabled; }
    public void setCarryAmount(double carryAmount) { this.carryAmount = carryAmount; }
    public void setSinkingFund(boolean sinkingFund) { this.sinkingFund = sinkingFund; }
}
