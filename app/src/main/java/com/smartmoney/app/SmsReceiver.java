package com.smartmoney.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.webkit.WebView;

public class SmsReceiver extends BroadcastReceiver {
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

            // Only process ICBC messages (95588)
            if (sender == null || body == null) continue;
            if (!sender.contains("95588")) continue;

            // Forward to WebView via JS
            WebView wv = MainActivity.getWebView();
            if (wv != null) {
                String escapedBody = body.replace("\\", "\\\\")
                                        .replace("'", "\\'")
                                        .replace("\n", "\\n")
                                        .replace("\r", "\\r");
                final String js = "if(typeof onNativeSmsReceived==='function'){onNativeSmsReceived('" + escapedBody + "');}";
                wv.post(() -> wv.evaluateJavascript(js, null));
            }
        }
    }
}
