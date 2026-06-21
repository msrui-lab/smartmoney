package com.smartmoney.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;

public class SmsReceiver extends BroadcastReceiver {

    private static final String PREFS_NAME = "smartmoney_sms";
    private static final String KEY_PREFIX = "pending_";
    private static final String[] BANK_NUMBERS = {
        "95588","95533","95599","95566","95555","95558","95559",
        "95528","95561","95568","95595","95501","95577","95508","95580"
    };

    private boolean isBankSms(String sender) {
        if (sender == null) return false;
        for (String num : BANK_NUMBERS) {
            if (sender.contains(num)) return true;
        }
        return false;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return;

        for (Object pdu : pdus) {
            SmsMessage sms;
            if (Build.VERSION.SDK_INT >= 23) {
                sms = SmsMessage.createFromPdu((byte[]) pdu, "3gpp");
            } else {
                sms = SmsMessage.createFromPdu((byte[]) pdu);
            }

            String sender = sms.getOriginatingAddress();
            String body = sms.getMessageBody();
            if (sender == null || body == null) continue;
            if (!isBankSms(sender)) continue;

            // Store in SharedPreferences — reliable even if WebView not ready
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String key = KEY_PREFIX + System.currentTimeMillis();
            prefs.edit().putString(key, body).apply();
        }
    }

    // Called by MainActivity to drain pending SMS
    public static String[] drainPendingSms(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        java.util.Map<String, ?> all = prefs.getAll();
        String[] bodies = new String[all.size()];
        int i = 0;
        for (String key : all.keySet()) {
            if (key.startsWith(KEY_PREFIX)) {
                bodies[i++] = (String) all.get(key);
            }
        }
        // Clear
        prefs.edit().clear().apply();
        // Trim array
        String[] result = new String[i];
        System.arraycopy(bodies, 0, result, 0, i);
        return result;
    }
}
