package com.smartmoney.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.util.Base64;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;

public class MainActivity extends Activity {
    private static WebView webView;
    private static boolean pageReady = false;
    private static final int SMS_PERMISSION_CODE = 100;
    private boolean historicalSmsLoaded = false;
    private static final List<String> pendingSmsQueue = new ArrayList<>();

    public static WebView getWebView() {
        return webView;
    }

    // Called by SmsReceiver when SMS arrives — try to process immediately
    public static void onSmsReceived(String body) {
        if (pageReady && webView != null) {
            // Page is ready, process immediately
            injectSingleSms(body);
        } else {
            // Page not ready, queue for later
            synchronized (pendingSmsQueue) {
                pendingSmsQueue.add(body);
            }
        }
    }

    private static void injectSingleSms(String body) {
        String b64 = Base64.encodeToString(body.getBytes(), Base64.NO_WRAP);
        webView.post(() -> webView.evaluateJavascript(
            "if(typeof onNativeSmsB64==='function'){onNativeSmsB64('" + b64 + "');}",
            null
        ));
    }

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
                pageReady = true;
                importHistoricalSms();
                processPendingSms();
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

        webView.loadUrl("https://msrui-lab.github.io/smartmoney/");
        requestSmsPermission();
    }

    private static void processPendingSms() {
        List<String> batch;
        synchronized (pendingSmsQueue) {
            if (pendingSmsQueue.isEmpty()) return;
            batch = new ArrayList<>(pendingSmsQueue);
            pendingSmsQueue.clear();
        }
        for (String body : batch) {
            injectSingleSms(body);
        }
    }

    private void requestSmsPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS},
                    SMS_PERMISSION_CODE
                );
            } else {
                notifyWebViewPermission(true);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            boolean permGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) permGranted = false;
            }
            notifyWebViewPermission(permGranted);
            if (permGranted) importHistoricalSms();
        }
    }

    private void notifyWebViewPermission(boolean granted) {
        final boolean finalGranted = granted;
        if (webView != null) {
            webView.post(() -> webView.evaluateJavascript(
                "if(typeof onSmsPermissionResult==='function'){onSmsPermissionResult(" + finalGranted + ");}",
                null
            ));
        }
    }

    private void importHistoricalSms() {
        if (historicalSmsLoaded) return;
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return;
        }
        historicalSmsLoaded = true;

        Calendar cal = Calendar.getInstance();
        int currentYear = cal.get(Calendar.YEAR);
        cal.set(currentYear, Calendar.JANUARY, 1, 0, 0, 0);
        long janFirstMillis = cal.getTimeInMillis();

        String[] bankNums = {"%95588%","%95533%","%95599%","%95566%","%95555%","%95558%","%95559%","%95528%","%95561%","%95568%","%95595%","%95501%","%95577%","%95508%","%95580%"};
        StringBuilder whereClause = new StringBuilder("(");
        for (int i = 0; i < bankNums.length; i++) {
            if (i > 0) whereClause.append(" OR ");
            whereClause.append("address LIKE ?");
        }
        whereClause.append(") AND date >= ?");
        String[] selectionArgs = new String[bankNums.length + 1];
        System.arraycopy(bankNums, 0, selectionArgs, 0, bankNums.length);
        selectionArgs[bankNums.length] = String.valueOf(janFirstMillis);

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
