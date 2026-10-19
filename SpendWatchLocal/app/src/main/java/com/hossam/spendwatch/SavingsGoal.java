package com.hossam.spendwatch;

public class SavingsGoal {
    private final long id;
    private final String name;
    private final double targetAmount;
    private final double savedAmount;

    public SavingsGoal(long id, String name, double targetAmount, double savedAmount) {
        this.id = id;
        this.name = name;
        this.targetAmount = targetAmount;
        this.savedAmount = savedAmount;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public double getTargetAmount() { return targetAmount; }
    public double getSavedAmount() { return savedAmount; }
    public double getRemaining() { return Math.max(0, targetAmount - savedAmount); }
}
