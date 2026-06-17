package com.smartmoney.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Base64;
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

            if (sender == null || body == null) continue;
            if (!sender.contains("95588")) continue;

            WebView wv = MainActivity.getWebView();
            if (wv == null) return;

            // Use base64 to safely pass SMS body to JavaScript (avoids escaping bugs)
            String b64 = Base64.encodeToString(body.getBytes(), Base64.NO_WRAP);
            final String js = "if(typeof onNativeSmsB64==='function'){onNativeSmsB64('" + b64 + "');}";
            wv.post(() -> wv.evaluateJavascript(js, null));
        }
    }
}
