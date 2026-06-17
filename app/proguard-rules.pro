# Keep Device Admin receiver
-keep class com.vertext.nuovopaylock.admin.** { *; }

# Keep API models (JSON parsing)
-keep class com.vertext.nuovopaylock.model.** { *; }
-keep class com.vertext.nuovopaylock.api.NuovoPayClient$DeviceInfo { *; }

# Keep Activity
-keep class com.vertext.nuovopaylock.ui.PaymentActivity { *; }

# JSON
-keep class org.json.** { *; }

# AppCompat
-keep class androidx.appcompat.** { *; }
