package com.vertext.nuovopaylock.model;

/**
 * Represents loan and device data shown on the payment screen.
 * Populated from NuovoPay API or local storage (for offline fallback).
 */
public class LoanInfo {

    public String customerName;
    public String customerId;
    public String deviceImei;
    public String deviceModel;
    public String phoneNumber;

    public double loanAmount;
    public double amountPaid;
    public double amountDue;
    public int    loanDurationDays;
    public int    daysRemaining;
    public String nextDueDate;
    public String loanStatus;

    /** Progress 0.0 → 1.0 */
    public float getRepaymentProgress() {
        if (loanAmount <= 0) return 0f;
        return (float) (amountPaid / loanAmount);
    }

    public boolean isOverdue() {
        return "OVERDUE".equalsIgnoreCase(loanStatus);
    }

    public boolean isCompleted() {
        return "COMPLETED".equalsIgnoreCase(loanStatus);
    }

    /** Demo/fallback data used when API is unreachable */
    public static LoanInfo demoData() {
        LoanInfo info = new LoanInfo();
        info.customerName     = "Jane Wambui";
        info.customerId       = "CUST-00847";
        info.deviceImei       = "358765091234567";
        info.deviceModel      = "Tecno Spark 20";
        info.phoneNumber      = "+254712345678";
        info.loanAmount       = 18000.00;
        info.amountPaid       = 10800.00;
        info.amountDue        = 1800.00;
        info.loanDurationDays = 90;
        info.daysRemaining    = 27;
        info.nextDueDate      = "23 Jun 2026";
        info.loanStatus       = "ACTIVE";
        return info;
    }
}
