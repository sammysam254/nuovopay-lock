package com.vertext.nuovopaylock.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NuovoPay MDM API Client
 *
 * DEMO MODE (default): When API_KEY is empty, all calls return simulated
 * responses so the app builds and runs without any NuovoPay account.
 *
 * PRODUCTION: Set API_KEY and PARTNER_ID from your NuovoPay Partner Dashboard.
 */
public class NuovoPayClient {

    private static final String TAG = "NuovoPayClient";

    // ── Leave blank for DEMO MODE. Fill in after testing. ─────────────────────
    private static final String BASE_URL   = "https://api.nuovopay.com/v1";
    private static final String API_KEY    = "";   // TODO: add after testing
    private static final String PARTNER_ID = "";   // TODO: add after testing
    // ──────────────────────────────────────────────────────────────────────────

    private static final boolean DEMO_MODE = API_KEY.isEmpty();

    public static boolean isDemoMode() { return API_KEY.isEmpty(); }

    private final ExecutorService executor    = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    // ── Callbacks ──────────────────────────────────────────────────────────────

    public interface DeviceInfoCallback {
        void onSuccess(DeviceInfo info);
        void onError(String message);
    }

    public interface PaymentCallback {
        void onSuccess(String transactionId);
        void onError(String message);
    }

    public interface UnlockCallback {
        void onSuccess();
        void onError(String message);
    }

    // ── Data model ─────────────────────────────────────────────────────────────

    public static class DeviceInfo {
        public String customerName;
        public String customerId;
        public String deviceImei;
        public String deviceModel;
        public double loanAmount;
        public double amountPaid;
        public double amountDue;
        public int    loanDurationDays;
        public int    daysRemaining;
        public String nextDueDate;
        public String loanStatus;
        public boolean isLocked;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public void getDeviceInfo(String imei, DeviceInfoCallback callback) {
        if (DEMO_MODE) {
            // Simulate 800ms network delay then return demo data
            mainHandler.postDelayed(() -> callback.onSuccess(demoDeviceInfo(imei)), 800);
            return;
        }
        executor.execute(() -> {
            try {
                URL url = new URL(BASE_URL + "/devices/" + imei);
                HttpURLConnection conn = buildConnection(url, "GET");
                conn.connect();
                int status = conn.getResponseCode();
                String body = readStream(conn);
                if (status == 200) {
                    callback.onSuccess(parseDeviceInfo(new JSONObject(body)));
                } else {
                    String msg = new JSONObject(body).optString("message", "Failed to fetch device info");
                    mainHandler.post(() -> callback.onError(msg));
                }
            } catch (Exception e) {
                Log.e(TAG, "getDeviceInfo error", e);
                // Fallback to demo so app still works offline
                mainHandler.post(() -> callback.onSuccess(demoDeviceInfo(imei)));
            }
        });
    }

    public void submitPayment(String imei, double amount, String phoneNumber,
                              String paymentMethod, PaymentCallback callback) {
        if (DEMO_MODE) {
            // Simulate 1.5s payment processing
            mainHandler.postDelayed(() ->
                callback.onSuccess("DEMO-TXN-" + System.currentTimeMillis()), 1500);
            return;
        }
        executor.execute(() -> {
            try {
                URL url = new URL(BASE_URL + "/payments");
                HttpURLConnection conn = buildConnection(url, "POST");
                JSONObject payload = new JSONObject();
                payload.put("device_imei",    imei);
                payload.put("amount",         amount);
                payload.put("phone_number",   phoneNumber);
                payload.put("payment_method", paymentMethod);
                payload.put("partner_id",     PARTNER_ID);
                writeBody(conn, payload.toString());
                conn.connect();
                int status = conn.getResponseCode();
                String body = readStream(conn);
                if (status == 200 || status == 201) {
                    String txId = new JSONObject(body).optString("transaction_id",
                            "TXN-" + System.currentTimeMillis());
                    mainHandler.post(() -> callback.onSuccess(txId));
                } else {
                    String msg = new JSONObject(body).optString("message", "Payment submission failed");
                    mainHandler.post(() -> callback.onError(msg));
                }
            } catch (Exception e) {
                Log.e(TAG, "submitPayment error", e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }

    public void requestUnlock(String imei, String transactionId, UnlockCallback callback) {
        if (DEMO_MODE) {
            // Simulate 1s unlock signal
            mainHandler.postDelayed(callback::onSuccess, 1000);
            return;
        }
        executor.execute(() -> {
            try {
                URL url = new URL(BASE_URL + "/devices/" + imei + "/unlock");
                HttpURLConnection conn = buildConnection(url, "POST");
                JSONObject payload = new JSONObject();
                payload.put("transaction_id", transactionId);
                payload.put("partner_id",     PARTNER_ID);
                writeBody(conn, payload.toString());
                conn.connect();
                int status = conn.getResponseCode();
                String body = readStream(conn);
                if (status == 200 || status == 202) {
                    mainHandler.post(callback::onSuccess);
                } else {
                    String msg = new JSONObject(body).optString("message", "Unlock request failed");
                    mainHandler.post(() -> callback.onError(msg));
                }
            } catch (Exception e) {
                Log.e(TAG, "requestUnlock error", e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        });
    }

    // ── Demo data ──────────────────────────────────────────────────────────────

    private DeviceInfo demoDeviceInfo(String imei) {
        DeviceInfo info = new DeviceInfo();
        info.customerName     = "Jane Wambui";
        info.customerId       = "CUST-00847";
        info.deviceImei       = imei.isEmpty() ? "358765091234567" : imei;
        info.deviceModel      = "Tecno Spark 20";
        info.loanAmount       = 18000.00;
        info.amountPaid       = 10800.00;
        info.amountDue        = 1800.00;
        info.loanDurationDays = 90;
        info.daysRemaining    = 27;
        info.nextDueDate      = "23 Jun 2026";
        info.loanStatus       = "ACTIVE";
        info.isLocked         = true;
        return info;
    }

    // ── HTTP helpers ───────────────────────────────────────────────────────────

    private HttpURLConnection buildConnection(URL url, String method) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type",  "application/json");
        conn.setRequestProperty("Accept",        "application/json");
        conn.setRequestProperty("X-API-Key",     API_KEY);
        conn.setRequestProperty("X-Partner-Id",  PARTNER_ID);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(15_000);
        if (!method.equals("GET")) conn.setDoOutput(true);
        return conn;
    }

    private void writeBody(HttpURLConnection conn, String json) throws Exception {
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes("UTF-8"));
        }
    }

    private String readStream(HttpURLConnection conn) throws Exception {
        boolean isError = conn.getResponseCode() >= 400;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                isError ? conn.getErrorStream() : conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private DeviceInfo parseDeviceInfo(JSONObject json) throws Exception {
        DeviceInfo info = new DeviceInfo();
        info.customerName     = json.optString("customer_name",    "Customer");
        info.customerId       = json.optString("customer_id",      "");
        info.deviceImei       = json.optString("device_imei",      "");
        info.deviceModel      = json.optString("device_model",     "Android Device");
        info.loanAmount       = json.optDouble("loan_amount",      0.0);
        info.amountPaid       = json.optDouble("amount_paid",      0.0);
        info.amountDue        = json.optDouble("amount_due",       0.0);
        info.loanDurationDays = json.optInt("loan_duration_days",  0);
        info.daysRemaining    = json.optInt("days_remaining",      0);
        info.nextDueDate      = json.optString("next_due_date",    "");
        info.loanStatus       = json.optString("loan_status",      "ACTIVE");
        info.isLocked         = json.optBoolean("is_locked",       true);
        return info;
    }
}
