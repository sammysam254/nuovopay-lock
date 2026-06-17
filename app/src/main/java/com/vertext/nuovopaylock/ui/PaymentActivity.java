package com.vertext.nuovopaylock.ui;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.vertext.nuovopaylock.R;
import com.vertext.nuovopaylock.admin.LockDeviceAdminReceiver;
import com.vertext.nuovopaylock.api.NuovoPayClient;
import com.vertext.nuovopaylock.model.LoanInfo;

public class PaymentActivity extends AppCompatActivity {

    // ── Views ──────────────────────────────────────────────────────────────────
    private TextView    tvCustomerName, tvCustomerId, tvDeviceModel;
    private TextView    tvLoanAmount, tvAmountPaid, tvAmountDue;
    private TextView    tvDaysRemaining, tvNextDueDate, tvLoanStatus;
    private TextView    tvProgressLabel, tvDemoBanner;
    private ProgressBar progressRepayment;
    private EditText    etPaymentAmount, etPhoneNumber;
    private Spinner     spinnerPaymentMethod;
    private Button      btnPayNow;
    private LinearLayout layoutLoading, layoutContent, layoutSuccess;
    private TextView    tvLoadingMsg, tvSuccessMsg;

    // ── State ──────────────────────────────────────────────────────────────────
    private NuovoPayClient apiClient;
    private LoanInfo       loanInfo;
    private DevicePolicyManager dpm;
    private ComponentName  adminComponent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_payment);

        dpm            = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, LockDeviceAdminReceiver.class);
        apiClient      = new NuovoPayClient();

        bindViews();
        setupPaymentMethodSpinner();
        fetchDeviceInfo();

        btnPayNow.setOnClickListener(v -> handlePayment());
    }

    // ── Bind ───────────────────────────────────────────────────────────────────

    private void bindViews() {
        tvCustomerName    = findViewById(R.id.tv_customer_name);
        tvCustomerId      = findViewById(R.id.tv_customer_id);
        tvDeviceModel     = findViewById(R.id.tv_device_model);
        tvLoanAmount      = findViewById(R.id.tv_loan_amount);
        tvAmountPaid      = findViewById(R.id.tv_amount_paid);
        tvAmountDue       = findViewById(R.id.tv_amount_due);
        tvDaysRemaining   = findViewById(R.id.tv_days_remaining);
        tvNextDueDate     = findViewById(R.id.tv_next_due_date);
        tvLoanStatus      = findViewById(R.id.tv_loan_status);
        tvProgressLabel   = findViewById(R.id.tv_progress_label);
        tvDemoBanner      = findViewById(R.id.tv_demo_banner);
        progressRepayment = findViewById(R.id.progress_repayment);
        etPaymentAmount   = findViewById(R.id.et_payment_amount);
        etPhoneNumber     = findViewById(R.id.et_phone_number);
        spinnerPaymentMethod = findViewById(R.id.spinner_payment_method);
        btnPayNow         = findViewById(R.id.btn_pay_now);
        layoutLoading     = findViewById(R.id.layout_loading);
        layoutContent     = findViewById(R.id.layout_content);
        layoutSuccess     = findViewById(R.id.layout_success);
        tvLoadingMsg      = findViewById(R.id.tv_loading_msg);
        tvSuccessMsg      = findViewById(R.id.tv_success_msg);
    }

    private void setupPaymentMethodSpinner() {
        String[] methods = {"M-Pesa", "Airtel Money", "Bank Transfer", "Cash (Agent)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, methods);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPaymentMethod.setAdapter(adapter);
    }

    // ── Fetch ──────────────────────────────────────────────────────────────────

    private void fetchDeviceInfo() {
        showLoading("Fetching account details…");
        String imei = getDeviceImei();

        apiClient.getDeviceInfo(imei, new NuovoPayClient.DeviceInfoCallback() {
            @Override
            public void onSuccess(NuovoPayClient.DeviceInfo info) {
                loanInfo = mapToLoanInfo(info);
                populateUI();
                showContent();
            }
            @Override
            public void onError(String message) {
                loanInfo = LoanInfo.demoData();
                populateUI();
                showContent();
            }
        });
    }

    private String getDeviceImei() {
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            String imei = tm != null ? tm.getImei() : null;
            return (imei != null && !imei.isEmpty()) ? imei : "";
        } catch (SecurityException e) {
            return "";
        }
    }

    // ── Populate ───────────────────────────────────────────────────────────────

    private void populateUI() {
        // Show DEMO banner if no API key configured
        boolean isDemo = NuovoPayClient.isDemoMode();
        tvDemoBanner.setVisibility(isDemo ? View.VISIBLE : View.GONE);

        tvCustomerName.setText(loanInfo.customerName);
        tvCustomerId.setText("ID: " + loanInfo.customerId);
        tvDeviceModel.setText(loanInfo.deviceModel);

        tvLoanAmount.setText(formatKES(loanInfo.loanAmount));
        tvAmountPaid.setText(formatKES(loanInfo.amountPaid));
        tvAmountDue.setText(formatKES(loanInfo.amountDue));

        tvDaysRemaining.setText(loanInfo.daysRemaining + " days left");
        tvNextDueDate.setText("Due: " + loanInfo.nextDueDate);

        tvLoanStatus.setText(loanInfo.loanStatus);
        if (loanInfo.isOverdue()) {
            tvLoanStatus.setBackgroundResource(R.drawable.badge_overdue);
        } else if (loanInfo.isCompleted()) {
            tvLoanStatus.setBackgroundResource(R.drawable.badge_completed);
        } else {
            tvLoanStatus.setBackgroundResource(R.drawable.badge_active);
        }

        int progress = (int) (loanInfo.getRepaymentProgress() * 100);
        progressRepayment.setProgress(progress);
        tvProgressLabel.setText(progress + "% repaid  ·  KES " +
                formatKES(loanInfo.loanAmount - loanInfo.amountPaid) + " remaining");

        etPaymentAmount.setText(String.valueOf((int) loanInfo.amountDue));
        if (loanInfo.phoneNumber != null && !loanInfo.phoneNumber.isEmpty()) {
            etPhoneNumber.setText(loanInfo.phoneNumber);
        }
    }

    // ── Payment ────────────────────────────────────────────────────────────────

    private void handlePayment() {
        String amountStr = etPaymentAmount.getText().toString().trim();
        String phone     = etPhoneNumber.getText().toString().trim();

        if (amountStr.isEmpty()) { etPaymentAmount.setError("Enter payment amount"); return; }
        double amount = Double.parseDouble(amountStr);
        if (amount <= 0) { etPaymentAmount.setError("Amount must be greater than 0"); return; }
        if (phone.isEmpty()) { etPhoneNumber.setError("Enter M-Pesa / phone number"); return; }

        String method = spinnerPaymentMethod.getSelectedItem().toString();
        confirmAndPay(amount, phone, method);
    }

    private void confirmAndPay(double amount, String phone, String method) {
        String demoNote = NuovoPayClient.isDemoMode() ? "\n\n⚠️ DEMO MODE — no real payment will be made." : "";
        new AlertDialog.Builder(this)
                .setTitle("Confirm Payment")
                .setMessage("Pay " + formatKES(amount) + " via " + method +
                        "\nPhone: " + phone + demoNote + "\n\nProceed?")
                .setPositiveButton("Pay Now", (d, w) -> executePayment(amount, phone, method))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executePayment(double amount, String phone, String method) {
        showLoading("Processing payment…");
        apiClient.submitPayment(loanInfo.deviceImei, amount, phone, method,
                new NuovoPayClient.PaymentCallback() {
                    @Override public void onSuccess(String transactionId) {
                        showLoading("Payment confirmed!\nSending unlock signal to NuovoPay…");
                        requestUnlock(transactionId);
                    }
                    @Override public void onError(String message) {
                        showContent();
                        showError("Payment Failed", message);
                    }
                });
    }

    private void requestUnlock(String transactionId) {
        apiClient.requestUnlock(loanInfo.deviceImei, transactionId,
                new NuovoPayClient.UnlockCallback() {
                    @Override public void onSuccess() {
                        saveUnlockState();
                        performLocalUnlock();
                        showSuccess("Payment received!\nYour device is now unlocked.\n\nThank you, "
                                + loanInfo.customerName + "! 🎉\n\nRef: " + transactionId);
                    }
                    @Override public void onError(String message) {
                        showContent();
                        showError("Unlock Error",
                                "Payment was received but unlock signal failed.\n" +
                                "Please contact support.\n\nRef: " + transactionId);
                    }
                });
    }

    private void saveUnlockState() {
        getSharedPreferences("nuovopay_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("device_unlocked", true).apply();
    }

    private void performLocalUnlock() {
        try {
            if (dpm.isAdminActive(adminComponent)) {
                dpm.removeActiveAdmin(adminComponent);
            }
        } catch (Exception ignored) {
            // NuovoPay MDM agent handles remote unlock if we don't have Device Owner
        }
    }

    // ── UI states ──────────────────────────────────────────────────────────────

    private void showLoading(String msg) {
        layoutLoading.setVisibility(View.VISIBLE);
        layoutContent.setVisibility(View.GONE);
        layoutSuccess.setVisibility(View.GONE);
        tvLoadingMsg.setText(msg);
        btnPayNow.setEnabled(false);
    }

    private void showContent() {
        layoutLoading.setVisibility(View.GONE);
        layoutContent.setVisibility(View.VISIBLE);
        layoutSuccess.setVisibility(View.GONE);
        btnPayNow.setEnabled(true);
    }

    private void showSuccess(String msg) {
        layoutLoading.setVisibility(View.GONE);
        layoutContent.setVisibility(View.GONE);
        layoutSuccess.setVisibility(View.VISIBLE);
        tvSuccessMsg.setText(msg);
    }

    private void showError(String title, String msg) {
        new AlertDialog.Builder(this)
                .setTitle(title).setMessage(msg).setPositiveButton("OK", null).show();
    }

    private String formatKES(double amount) {
        return String.format("KES %,.0f", amount);
    }

    private LoanInfo mapToLoanInfo(NuovoPayClient.DeviceInfo info) {
        LoanInfo loan = new LoanInfo();
        loan.customerName     = info.customerName;
        loan.customerId       = info.customerId;
        loan.deviceImei       = info.deviceImei;
        loan.deviceModel      = info.deviceModel;
        loan.loanAmount       = info.loanAmount;
        loan.amountPaid       = info.amountPaid;
        loan.amountDue        = info.amountDue;
        loan.loanDurationDays = info.loanDurationDays;
        loan.daysRemaining    = info.daysRemaining;
        loan.nextDueDate      = info.nextDueDate;
        loan.loanStatus       = info.loanStatus;
        return loan;
    }

    @Override
    public void onBackPressed() { /* blocked while locked */ }
}
