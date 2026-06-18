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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
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

    private static final int REQ_PERMS = 100;
    private static final int REQ_ADMIN = 101;

    private TextView tvCustomerName, tvCustomerId, tvDeviceModel;
    private TextView tvLoanAmount, tvAmountPaid, tvAmountDue;
    private TextView tvDaysRemaining, tvNextDueDate, tvLoanStatus;
    private TextView tvProgressLabel, tvDemoBanner;
    private ProgressBar progressRepayment;
    private EditText etPaymentAmount, etPhoneNumber;
    private Spinner spinnerPaymentMethod;
    private Button btnPayNow;
    private LinearLayout layoutLoading, layoutSuccess;
    private android.widget.ScrollView layoutContent;
    private TextView tvLoadingMsg, tvSuccessMsg;

    private NuovoPayClient apiClient;
    private LoanInfo loanInfo;
    private DevicePolicyManager dpm;
    private ComponentName adminComponent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            setContentView(R.layout.activity_payment);
            dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            adminComponent = new ComponentName(this, LockDeviceAdminReceiver.class);
            apiClient = new NuovoPayClient();
            bindViews();
            setupSpinner();
            btnPayNow.setOnClickListener(v -> handlePayment());
            askPhonePermission();
        } catch (Exception e) {
            Toast.makeText(this, "Startup error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void askPhonePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_PHONE_STATE}, REQ_PERMS);
        } else {
            askDeviceAdmin();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        askDeviceAdmin();
    }

    private void askDeviceAdmin() {
        try {
            if (dpm != null && !dpm.isAdminActive(adminComponent)) {
                Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "NuovoPay needs Device Admin to lock this device on missed payments and unlock after payment.");
                startActivityForResult(i, REQ_ADMIN);
            } else {
                loadData();
            }
        } catch (Exception e) {
            loadData();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_ADMIN) loadData();
    }

    private void loadData() {
        showLoading("Fetching account details…");
        String imei = getImei();
        apiClient.getDeviceInfo(imei, new NuovoPayClient.DeviceInfoCallback() {
            @Override public void onSuccess(NuovoPayClient.DeviceInfo info) {
                loanInfo = map(info);
                fillUI();
                showContent();
            }
            @Override public void onError(String msg) {
                loanInfo = LoanInfo.demoData();
                fillUI();
                showContent();
            }
        });
    }

    private String getImei() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED) {
                TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null) {
                    String id = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? tm.getImei() : tm.getDeviceId();
                    if (id != null && !id.isEmpty()) return id;
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private void fillUI() {
        try {
            tvDemoBanner.setVisibility(NuovoPayClient.isDemoMode() ? View.VISIBLE : View.GONE);
            tvCustomerName.setText(loanInfo.customerName);
            tvCustomerId.setText("ID: " + loanInfo.customerId);
            tvDeviceModel.setText(loanInfo.deviceModel);
            tvLoanAmount.setText(fmt(loanInfo.loanAmount));
            tvAmountPaid.setText(fmt(loanInfo.amountPaid));
            tvAmountDue.setText(fmt(loanInfo.amountDue));
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
            int pct = (int) (loanInfo.getRepaymentProgress() * 100);
            progressRepayment.setProgress(pct);
            tvProgressLabel.setText(pct + "% repaid  ·  "
                    + fmt(loanInfo.loanAmount - loanInfo.amountPaid) + " remaining");
            etPaymentAmount.setText(String.valueOf((int) loanInfo.amountDue));
            if (loanInfo.phoneNumber != null && !loanInfo.phoneNumber.isEmpty())
                etPhoneNumber.setText(loanInfo.phoneNumber);
        } catch (Exception e) {
            Toast.makeText(this, "UI error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handlePayment() {
        try {
            String amtStr = etPaymentAmount.getText().toString().trim();
            String phone  = etPhoneNumber.getText().toString().trim();
            if (amtStr.isEmpty()) { etPaymentAmount.setError("Enter amount"); return; }
            double amt;
            try { amt = Double.parseDouble(amtStr); }
            catch (NumberFormatException e) { etPaymentAmount.setError("Invalid amount"); return; }
            if (amt <= 0) { etPaymentAmount.setError("Must be > 0"); return; }
            if (phone.isEmpty()) { etPhoneNumber.setError("Enter phone number"); return; }
            String method = spinnerPaymentMethod.getSelectedItem().toString();
            String demoNote = NuovoPayClient.isDemoMode() ? "\n\n⚠️ DEMO MODE — no real payment." : "";
            new AlertDialog.Builder(this)
                    .setTitle("Confirm Payment")
                    .setMessage("Pay " + fmt(amt) + " via " + method + "\nPhone: " + phone + demoNote)
                    .setPositiveButton("Pay Now", (d, w) -> doPayment(amt, phone, method))
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void doPayment(double amt, String phone, String method) {
        showLoading("Processing payment…");
        String imei = loanInfo != null ? loanInfo.deviceImei : "";
        apiClient.submitPayment(imei, amt, phone, method, new NuovoPayClient.PaymentCallback() {
            @Override public void onSuccess(String txId) {
                showLoading("Payment confirmed!\nSending unlock signal…");
                doUnlock(txId);
            }
            @Override public void onError(String msg) { showContent(); showError("Payment Failed", msg); }
        });
    }

    private void doUnlock(String txId) {
        String imei = loanInfo != null ? loanInfo.deviceImei : "";
        apiClient.requestUnlock(imei, txId, new NuovoPayClient.UnlockCallback() {
            @Override public void onSuccess() {
                getSharedPreferences("nuovopay_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("device_unlocked", true).apply();
                try { if (dpm != null && dpm.isAdminActive(adminComponent)) dpm.removeActiveAdmin(adminComponent); }
                catch (Exception ignored) {}
                showSuccess("Device unlocked! 🎉\n\nThank you, "
                        + (loanInfo != null ? loanInfo.customerName : "") + "!\nRef: " + txId);
            }
            @Override public void onError(String msg) {
                showContent(); showError("Unlock Error", "Payment ok but unlock failed.\nRef: " + txId);
            }
        });
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

    private void setupSpinner() {
        String[] methods = {"M-Pesa", "Airtel Money", "Bank Transfer", "Cash (Agent)"};
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, methods);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPaymentMethod.setAdapter(a);
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

    private void showError(String t, String msg) {
        new AlertDialog.Builder(this).setTitle(t).setMessage(msg).setPositiveButton("OK", null).show();
    }

    private String fmt(double v) { return String.format("KES %,.0f", v); }

    private LoanInfo map(NuovoPayClient.DeviceInfo i) {
        LoanInfo l = new LoanInfo();
        l.customerName = i.customerName; l.customerId = i.customerId;
        l.deviceImei = i.deviceImei; l.deviceModel = i.deviceModel;
        l.loanAmount = i.loanAmount; l.amountPaid = i.amountPaid;
        l.amountDue = i.amountDue; l.loanDurationDays = i.loanDurationDays;
        l.daysRemaining = i.daysRemaining; l.nextDueDate = i.nextDueDate;
        l.loanStatus = i.loanStatus;
        return l;
    }

    @Override public void onBackPressed() {}
}
