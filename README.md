# NuovoPay Lock — Device Financing Lock Screen App

A **Device Admin** Android app for device financing (PAYG — Pay As You Go) that integrates with **NuovoPay MDM** to:

- Show a payment screen when the device is locked for non-payment
- Display customer name, loan progress, amount due, and days remaining
- Accept M-Pesa / Airtel Money / Bank Transfer payments
- Send the payment confirmation to NuovoPay's API
- Trigger the remote **unlock signal** so NuovoPay releases the device

---

## 📱 Screenshots / UI Flow

```
┌─────────────────────────────┐
│  NuovoPay          [ACTIVE] │  ← Header + status badge
├─────────────────────────────┤
│  CUSTOMER                   │
│  Jane Wambui                │
│  ID: CUST-00847   Tecno S20 │
├─────────────────────────────┤
│  LOAN PROGRESS              │
│  Total: KES 18,000          │
│  Paid:  KES 10,800  Due: 1,800│
│  ████████████░░░░  60%      │
│  27 days left · Due 23 Jun  │
├─────────────────────────────┤
│  MAKE PAYMENT               │
│  Amount: [1800        ]     │
│  Phone:  [+254712345678]    │
│  Method: [M-Pesa        ▼]  │
├─────────────────────────────┤
│  [ PAY NOW & UNLOCK DEVICE ]│
└─────────────────────────────┘
```

---

## 🗂 Project Structure

```
NuovoPayLock/
├── app/src/main/
│   ├── java/com/vertext/nuovopaylock/
│   │   ├── admin/
│   │   │   ├── LockDeviceAdminReceiver.java   # Device Admin callbacks
│   │   │   └── BootReceiver.java              # Re-lock after reboot
│   │   ├── api/
│   │   │   └── NuovoPayClient.java            # NuovoPay REST API client
│   │   ├── model/
│   │   │   └── LoanInfo.java                  # Loan data model
│   │   └── ui/
│   │       └── PaymentActivity.java           # Main payment screen
│   ├── res/
│   │   ├── layout/activity_payment.xml        # Dark payment UI
│   │   ├── drawable/                          # Cards, buttons, badges
│   │   ├── values/strings.xml
│   │   ├── values/themes.xml
│   │   └── xml/device_admin.xml               # Admin policy declaration
│   └── AndroidManifest.xml
├── .github/workflows/build.yml                # CI/CD — builds APK
├── build.gradle
├── settings.gradle
└── README.md
```

---

## ⚙️ NuovoPay Integration

### How It Works

```
Device locked (non-payment)
        │
        ▼
PaymentActivity shown (lock screen)
        │
        ▼
User enters amount + M-Pesa number
        │
        ▼
POST /payments → NuovoPay API  (payment submitted)
        │
        ▼
POST /devices/{imei}/unlock → NuovoPay API
        │
        ├── NuovoPay server removes lock policy from device profile
        └── Our app clears local Device Admin lock
                │
                ▼
        Device UNLOCKED ✓
```

### NuovoPay API Endpoints Used

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `GET` | `/v1/devices/{imei}` | Fetch loan & customer info |
| `POST` | `/v1/payments` | Submit payment |
| `POST` | `/v1/devices/{imei}/unlock` | Trigger unlock |

### Configure Your Credentials

Open `NuovoPayClient.java` and set:

```java
private static final String BASE_URL   = "https://api.nuovopay.com/v1";
private static final String API_KEY    = "YOUR_NUOVOPAY_API_KEY";
private static final String PARTNER_ID = "YOUR_PARTNER_ID";
```

Get these from your **NuovoPay Partner Dashboard** → Settings → API Keys.

---

## 🔒 Device Admin Setup

### Option 1: Manual (for testing)

1. Install the APK on the device
2. Go to **Settings → Security → Device Admin Apps**
3. Enable **NuovoPay Device Manager**

### Option 2: NuovoPay MDM Provisioning (Production)

NuovoPay provisions devices as **Device Owner** during setup:

```bash
# NuovoPay typically provisions via QR code or NFC bump at device setup
# Or via ADB for enterprise deployment:
adb shell dpm set-device-owner com.vertext.nuovopaylock/.admin.LockDeviceAdminReceiver
```

Device Owner mode gives NuovoPay (and your app) full lock capabilities.

---

## 🛠 Build from Termux

Since you build from Android/Termux, push to GitHub and let CI build the APK:

```bash
# 1. Clone / init
cd ~
git clone https://github.com/YOUR_USERNAME/nuovopay-lock
cd nuovopay-lock

# 2. Copy project files in
# (paste the project structure from the zip)

# 3. Commit and push
git add .
git commit -m "feat: NuovoPay lock screen v1.0"
git push origin main

# 4. GitHub Actions builds the APK automatically
# Download from Actions → Build NuovoPay Lock APK → Artifacts
```

### Signing (for release)

Add these secrets to your GitHub repo (Settings → Secrets):

| Secret | Value |
|--------|-------|
| `SIGNING_KEY` | Base64-encoded `.jks` keystore |
| `KEY_ALIAS` | Your key alias |
| `KEY_STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Key password |

Generate a keystore:
```bash
keytool -genkey -v -keystore nuovopay.jks \
  -alias nuovopay -keyalg RSA -keysize 2048 -validity 10000
# Then base64 encode it:
base64 nuovopay.jks
```

---

## 📋 Permissions Explained

| Permission | Why |
|-----------|-----|
| `INTERNET` | NuovoPay API calls |
| `READ_PHONE_STATE` | Get device IMEI to identify device in NuovoPay |
| `BIND_DEVICE_ADMIN` | Lock/unlock device via MDM |
| `RECEIVE_BOOT_COMPLETED` | Re-show payment screen after reboot |
| `WAKE_LOCK` | Keep screen on during payment |

---

## 🧩 Customisation

### Change Currency
Find all `KES` / `formatKES()` references in `PaymentActivity.java` and `LoanInfo.java`.

### Add M-Pesa STK Push
After `submitPayment()` succeeds, initiate an STK push via your M-Pesa Daraja API before calling `requestUnlock()`. The payment flow would be:
1. User taps **Pay Now**
2. Your backend triggers STK push to user's phone
3. User enters M-Pesa PIN on their own device
4. Daraja callback confirms payment to your backend
5. Your backend calls NuovoPay unlock endpoint
6. NuovoPay releases the device

### Demo Mode (Offline / Testing)
The app automatically falls back to `LoanInfo.demoData()` if the API is unreachable. This lets you test the UI without a live NuovoPay account.

---

## 🏢 Built by Vertext Digital
Ruiru, Kenya · vertext.digital
