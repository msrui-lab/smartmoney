package com.smartmoney.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;

public class SmsReceiver extends BroadcastReceiver {

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

            // Queue SMS for processing — WebView may not be ready yet
            MainActivity.queueSms(body);
        }
    }
}
