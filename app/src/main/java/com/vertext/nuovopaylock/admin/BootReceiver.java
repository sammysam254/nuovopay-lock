package com.vertext.nuovopaylock.admin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.vertext.nuovopaylock.ui.PaymentActivity;

/**
 * Launches the payment lock screen after device reboot.
 * This prevents the user from rebooting to bypass the lock.
 *
 * NuovoPay's MDM agent also enforces this independently,
 * but this receiver ensures our UI appears immediately on boot.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences("nuovopay_prefs", Context.MODE_PRIVATE);
            boolean isUnlocked = prefs.getBoolean("device_unlocked", false);

            if (!isUnlocked) {
                // Device is still locked — show payment screen
                Intent launch = new Intent(context, PaymentActivity.class);
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_NO_HISTORY);
                context.startActivity(launch);
            }
        }
    }
}
