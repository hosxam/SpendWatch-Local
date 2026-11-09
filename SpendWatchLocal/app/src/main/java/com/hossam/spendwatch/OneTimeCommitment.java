package com.hossam.spendwatch;

/** A future expected one-time payment; not a posted transaction. */
public final class OneTimeCommitment {
    private final long id;
    private final String title;
    private final double amount;
    private final long dueAt;
    private final int remindDays;
    private final String status;

    public OneTimeCommitment(long id, String title, double amount, long dueAt,
                             int remindDays, String status) {
        this.id = id; this.title = title; this.amount = amount;
        this.dueAt = dueAt; this.remindDays = remindDays; this.status = status;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public double getAmount() { return amount; }
    public long getDueAt() { return dueAt; }
    public int getRemindDays() { return remindDays; }
    public String getStatus() { return status; }
    public boolean isPending() { return "pending".equals(status); }
}
