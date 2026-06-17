package com.smartmoney.app;

import android.Manifest;
import android.app.Activity;
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
    private static WebView webView;
    private static final int SMS_PERMISSION_CODE = 100;
    private boolean historicalSmsLoaded = false;

    public static WebView getWebView() {
        return webView;
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
                importHistoricalSms();
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

        webView.loadUrl("https://msrui-lab.github.io/smartmoney/");

        // Request SMS permission on first launch
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
            } else {
                // Already granted
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
            if (permGranted) {
                importHistoricalSms();
            }
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

        // Calculate timestamp for January 1 of current year
        Calendar cal = Calendar.getInstance();
        int currentYear = cal.get(Calendar.YEAR);
        cal.set(currentYear, Calendar.JANUARY, 1, 0, 0, 0);
        long janFirstMillis = cal.getTimeInMillis();

        StringBuilder sb = new StringBuilder();
        sb.append("[");

        try {
            Uri uri = Telephony.Sms.Inbox.CONTENT_URI;
            String[] projection = {"address", "body", "date"};
            String selection = "address LIKE ? AND date >= ?";
            String[] selectionArgs = {"%95588%", String.valueOf(janFirstMillis)};
            String sortOrder = "date ASC";

            Cursor cursor = getContentResolver().query(uri, projection, selection, selectionArgs, sortOrder);
            if (cursor != null) {
                boolean first = true;
                while (cursor.moveToNext()) {
                    String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                    if (body != null && !body.isEmpty()) {
                        if (!first) sb.append(",");
                        first = false;
                        // Escape for JSON
                        String escaped = body.replace("\\", "\\\\")
                                            .replace("\"", "\\\"")
                                            .replace("\n", "\\n")
                                            .replace("\r", "\\r");
                        sb.append("\"").append(escaped).append("\"");
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            // SMS provider might not be available
        }

        sb.append("]");
        final String jsonArray = sb.toString();

        if (webView != null && sb.length() > 2) {
            webView.postDelayed(() -> {
                webView.evaluateJavascript(
                    "if(typeof onNativeHistoricalSms==='function'){onNativeHistoricalSms(" + jsonArray + ");}",
                    null
                );
            }, 2000); // Wait 2s for page JS to initialize
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
