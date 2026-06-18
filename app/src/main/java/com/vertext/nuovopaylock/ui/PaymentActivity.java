package com.vertext.nuovopaylock.ui;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.vertext.nuovopaylock.R;
import com.vertext.nuovopaylock.admin.LockDeviceAdminReceiver;
import com.vertext.nuovopaylock.api.NuovoPayClient;
import com.vertext.nuovopaylock.model.LoanInfo;

public class PaymentActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 100;
    private static final int REQUEST_DEVICE_ADMIN = 101;

    private TextView tvCustomerName, tvCustomerId, tvDeviceModel;
    private TextView tvLoanAmount, tvAmountPaid, tvAmountDue;
    private TextView tvDaysRemaining, tvNextDueDate, tvLoanStatus;
    private TextView tvProgressLabel, tvDemoBanner;
    private ProgressBar progressRepayment;
    private EditText etPaymentAmount, etPhoneNumber;
    private Spinner spinnerPaymentMethod;
    private Button btnPayNow;
    private LinearLayout layoutLoading, layoutContent, layoutSuccess;
    private TextView tvLoadingMsg, tvSuccessMsg;

    private NuovoPayClient apiClient;
    private LoanInfo loanInfo;
    private DevicePolicyManager dpm;
    private ComponentName adminComponent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_payment);

        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, LockDeviceAdminReceiver.class);
        apiClient = new NuovoPayClient();

        bindViews();
        setupPaymentMethodSpinner();
        requestRequiredPermissions();

        btnPayNow.setOnClickListener(v -> handlePayment());
    }

    private void requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_PHONE_STATE},
                        REQUEST_PERMISSIONS);
            } else {
                onPermissionsReady();
            }
        } else {
            onPermissionsReady();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        onPermissionsReady();
    }

    private void onPermissionsReady() {
        if (dpm != null && !dpm.isAdminActive(adminComponent)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "NuovoPay needs device admin to lock this device when payments are overdue and unlock it after payment.");
            startActivityForResult(intent, REQUEST_DEVICE_ADMIN);
        } else {
            fetchDeviceInfo();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DEVICE_ADMIN) {
            fetchDeviceInfo();
        }
    }

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

    private void fetchDeviceInfo() {
        showLoading("Fetching account details…");
        String imei = getDeviceImei();
        apiClient.getDeviceInfo(imei, new NuovoPayClient.DeviceInfoCallback() {
            @Override public void onSuccess(NuovoPayClient.DeviceInfo info) {
                loanInfo = mapToLoanInfo(info);
                populateUI();
                showContent();
            }
            @Override public void onError(String message) {
                loanInfo = LoanInfo.demoData();
                populateUI();
                showContent();
            }
        });
    }

    private String getDeviceImei() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED) {
                TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null) {
                    String imei = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? tm.getImei() : tm.getDeviceId();
                    if (imei != null && !imei.isEmpty()) return imei;
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private void populateUI() {
        tvDemoBanner.setVisibility(NuovoPayClient.isDemoMode() ? View.VISIBLE : View.GONE);
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
            tvLoanStatus.setTextColor(0xFFE74C3C);
        } else if (loanInfo.isCompleted()) {
            tvLoanStatus.setBackgroundResource(R.drawable.badge_completed);
            tvLoanStatus.setTextColor(0xFF3498DB);
        } else {
            tvLoanStatus.setBackgroundResource(R.drawable.badge_active);
            tvLoanStatus.setTextColor(0xFF2ECC71);
        }

        int progress = (int) (loanInfo.getRepaymentProgress() * 100);
        progressRepayment.setProgress(progress);
        tvProgressLabel.setText(progress + "% repaid  ·  "
                + formatKES(loanInfo.loanAmount - loanInfo.amountPaid) + " remaining");
        etPaymentAmount.setText(String.valueOf((int) loanInfo.amountDue));
        if (loanInfo.phoneNumber != null && !loanInfo.phoneNumber.isEmpty()) {
            etPhoneNumber.setText(loanInfo.phoneNumber);
        }
    }

    private void handlePayment() {
        String amountStr = etPaymentAmount.getText().toString().trim();
        String phone = etPhoneNumber.getText().toString().trim();
        if (amountStr.isEmpty()) { etPaymentAmount.setError("Enter payment amount"); return; }
        double amount;
        try { amount = Double.parseDouble(amountStr); }
        catch (NumberFormatException e) { etPaymentAmount.setError("Invalid amount"); return; }
        if (amount <= 0) { etPaymentAmount.setError("Amount must be greater than 0"); return; }
        if (phone.isEmpty()) { etPhoneNumber.setError("Enter M-Pesa / phone number"); return; }

        String method = spinnerPaymentMethod.getSelectedItem().toString();
        String demoNote = NuovoPayClient.isDemoMode() ? "\n\n⚠️ DEMO MODE — no real payment." : "";
        new AlertDialog.Builder(this)
                .setTitle("Confirm Payment")
                .setMessage("Pay " + formatKES(amount) + " via " + method
                        + "\nPhone: " + phone + demoNote + "\n\nProceed?")
                .setPositiveButton("Pay Now", (d, w) -> executePayment(amount, phone, method))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executePayment(double amount, String phone, String method) {
        showLoading("Processing payment…");
        String imei = loanInfo != null ? loanInfo.deviceImei : "";
        apiClient.submitPayment(imei, amount, phone, method,
                new NuovoPayClient.PaymentCallback() {
                    @Override public void onSuccess(String txId) {
                        showLoading("Payment confirmed!\nSending unlock signal…");
                        requestUnlock(txId);
                    }
                    @Override public void onError(String message) {
                        showContent();
                        showError("Payment Failed", message);
                    }
                });
    }

    private void requestUnlock(String txId) {
        String imei = loanInfo != null ? loanInfo.deviceImei : "";
        apiClient.requestUnlock(imei, txId, new NuovoPayClient.UnlockCallback() {
            @Override public void onSuccess() {
                getSharedPreferences("nuovopay_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("device_unlocked", true).apply();
                try {
                    if (dpm != null && dpm.isAdminActive(adminComponent))
                        dpm.removeActiveAdmin(adminComponent);
                } catch (Exception ignored) {}
                showSuccess("Payment received! Device unlocked.\n\nThank you, "
                        + (loanInfo != null ? loanInfo.customerName : "") + "! 🎉"
                        + "\nRef: " + txId);
            }
            @Override public void onError(String message) {
                showContent();
                showError("Unlock Error", "Payment received but unlock failed.\nRef: " + txId);
            }
        });
    }

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
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg)
                .setPositiveButton("OK", null).show();
    }

    private String formatKES(double amount) {
        return String.format("KES %,.0f", amount);
    }

    private LoanInfo mapToLoanInfo(NuovoPayClient.DeviceInfo info) {
        LoanInfo loan = new LoanInfo();
        loan.customerName = info.customerName;
        loan.customerId = info.customerId;
        loan.deviceImei = info.deviceImei;
        loan.deviceModel = info.deviceModel;
        loan.loanAmount = info.loanAmount;
        loan.amountPaid = info.amountPaid;
        loan.amountDue = info.amountDue;
        loan.loanDurationDays = info.loanDurationDays;
        loan.daysRemaining = info.daysRemaining;
        loan.nextDueDate = info.nextDueDate;
        loan.loanStatus = info.loanStatus;
        return loan;
    }

    @Override
    public void onBackPressed() {}
}
