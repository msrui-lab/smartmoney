package com.smartmoney.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Color;
import java.util.Calendar;

public class MainActivity extends Activity {
    private WebView webView;
    private static final int SMS_PERMISSION_CODE = 100;
    private static final String PREFS = "smartmoney_prefs";
    private static final String KEY_LAST_SYNC = "last_sms_sync";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.parseColor("#f5f7fa"));
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                importNewSms();
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

        webView.loadUrl("https://msrui-lab.github.io/smartmoney/");
        requestSmsPermission();
    }

    private void requestSmsPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS},
                    SMS_PERMISSION_CODE
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            boolean granted = true;
            for (int r : grantResults) { if (r != PackageManager.PERMISSION_GRANTED) granted = false; }
            final boolean fg = granted;
            if (webView != null) {
                webView.post(() -> webView.evaluateJavascript(
                    "if(typeof onSmsPermissionResult==='function'){onSmsPermissionResult(" + fg + ");}", null
                ));
            }
            if (granted) importNewSms();
        }
    }

    private void importNewSms() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long lastSync = prefs.getLong(KEY_LAST_SYNC, 0);

        // If never synced, start from Jan 1 this year
        if (lastSync == 0) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.MONTH, Calendar.JANUARY);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            lastSync = cal.getTimeInMillis();
        }

        String[] bankNums = {"%95588%","%95533%","%95599%","%95566%","%95555%","%95558%","%95559%","%95528%","%95561%","%95568%","%95595%","%95501%","%95577%","%95508%","%95580%"};
        StringBuilder whereClause = new StringBuilder("(");
        for (int i = 0; i < bankNums.length; i++) {
            if (i > 0) whereClause.append(" OR ");
            whereClause.append("address LIKE ?");
        }
        whereClause.append(") AND date > ?");
        String[] selectionArgs = new String[bankNums.length + 1];
        System.arraycopy(bankNums, 0, selectionArgs, 0, bankNums.length);
        selectionArgs[bankNums.length] = String.valueOf(lastSync);

        StringBuilder sb = new StringBuilder("[");
        try {
            Uri uri = Telephony.Sms.Inbox.CONTENT_URI;
            Cursor cursor = getContentResolver().query(uri, new String[]{"address","body","date"}, whereClause.toString(), selectionArgs, "date ASC");
            if (cursor != null) {
                boolean first = true;
                while (cursor.moveToNext()) {
                    String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                    if (body != null && !body.isEmpty()) {
                        if (!first) sb.append(",");
                        first = false;
                        String escaped = body.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");
                        sb.append("\"").append(escaped).append("\"");
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {}

        // Save sync timestamp
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply();

        sb.append("]");
        final String jsonArray = sb.toString();
        if (webView != null && sb.length() > 2) {
            webView.postDelayed(() -> webView.evaluateJavascript(
                "if(typeof onNativeHistoricalSms==='function'){onNativeHistoricalSms(" + jsonArray + ");}", null
            ), 2000);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
