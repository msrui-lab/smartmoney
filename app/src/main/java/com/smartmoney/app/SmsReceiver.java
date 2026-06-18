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

    // All major Chinese bank SMS numbers
    private static final String[] BANK_NUMBERS = {
        "95588", // 工商银行
        "95533", // 建设银行
        "95599", // 农业银行
        "95566", // 中国银行
        "95555", // 招商银行
        "95558", // 中信银行
        "95559", // 交通银行
        "95528", // 浦发银行
        "95561", // 兴业银行
        "95568", // 民生银行
        "95595", // 光大银行
        "95501", // 平安银行
        "95577", // 华夏银行
        "95508", // 广发银行
        "95580", // 邮政储蓄银行
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

        WebView wv = MainActivity.getWebView();
        if (wv == null) return; // App not running, skip

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

            // Use base64 to safely pass SMS body to JavaScript
            String b64 = Base64.encodeToString(body.getBytes(), Base64.NO_WRAP);
            final String js = "if(typeof onNativeSmsB64==='function'){onNativeSmsB64('" + b64 + "');}";
            wv.post(() -> wv.evaluateJavascript(js, null));
        }
    }
}
