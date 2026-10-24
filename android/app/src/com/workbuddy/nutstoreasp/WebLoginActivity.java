package com.workbuddy.nutstoreasp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

/**
 * WebView 登录验证页：
 *  - 自动加载坚果云登录页并填入账号密码
 *  - 用户手动完成滑块验证并登录
 *  - 登录成功后自动提取 Cookie 保存，返回主界面
 */
public class WebLoginActivity extends Activity {

    public static final String EXTRA_SERVER = "server";
    public static final String EXTRA_ACCOUNT = "account";
    public static final String EXTRA_PASSWORD = "password";

    private WebView webView;
    private String server;
    private String account;
    private String password;
    private boolean finishing = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weblogin);

        server = getIntent().getStringExtra(EXTRA_SERVER);
        account = getIntent().getStringExtra(EXTRA_ACCOUNT);
        password = getIntent().getStringExtra(EXTRA_PASSWORD);
        if (server == null || server.trim().isEmpty()) {
            server = "https://www.jianguoyun.com";
        }
        server = server.trim();
        if (server.endsWith("/")) server = server.substring(0, server.length() - 1);

        webView = findViewById(R.id.webview);

        // WebView Cookie 全局开启
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.removeSessionCookies(null);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setUserAgentString(Api.UA);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (finishing) return;
                // 登录成功后会跳转到 /d/home
                if (url != null && (url.contains("/d/home")
                        || url.contains("/d/welcome") || url.contains("/d/mobile_asp"))) {
                    trySuccess();
                    return;
                }
                // 登录页则自动填入账号密码
                if (url != null && url.contains("/d/login")) {
                    fillForm(view);
                }
            }
        });

        findViewById(R.id.btn_cancel).setOnClickListener(v -> {
            finishing = true;
            setResult(RESULT_CANCELED);
            finish();
        });

        findViewById(R.id.btn_done).setOnClickListener(v -> trySuccess());

        webView.loadUrl(server + "/d/login");
    }

    /** 向登录表单注入账号密码 */
    private void fillForm(WebView view) {
        if (account == null || password == null) return;
        try {
            String js = "(function(){try{"
                    + "var e=document.getElementById('login_email');"
                    + "var p=document.getElementById('login_password');"
                    + "if(e){e.value=" + jsString(account) + ";}"
                    + "if(p){p.value=" + jsString(password) + ";}"
                    + "}catch(x){}})()";
            view.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    /** 登录成功：提取 Cookie 并保存返回 */
    private void trySuccess() {
        if (finishing) return;
        finishing = true;
        final String cookie = CookieManager.getInstance().getCookie(server + "/");
        if (cookie == null || cookie.trim().isEmpty()) {
            finishing = false;
            Toast.makeText(this, "尚未检测到登录会话，请先完成登录", Toast.LENGTH_SHORT).show();
            return;
        }
        CookieManager.getInstance().flush();

        // 后台验证 Cookie 是否真正有效
        new Thread(() -> {
            boolean ok = Api.cookieValid(server, cookie);
            runOnUiThread(() -> {
                if (ok) {
                    Store.saveLogin(WebLoginActivity.this, server, account, cookie, password);
                    setResult(RESULT_OK);
                    finish();
                } else {
                    finishing = false;
                    Toast.makeText(WebLoginActivity.this,
                            "登录会话无效，请确认已在网页中完成登录", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    /** 把字符串转为 JS 字符串字面量 */
    private static String jsString(String s) {
        StringBuilder sb = new StringBuilder("'");
        for (char c : s.toCharArray()) {
            if (c == '\\') sb.append("\\\\");
            else if (c == '\'') sb.append("\\'");
            else if (c == '\n') sb.append("\\n");
            else sb.append(c);
        }
        return sb.append("'").toString();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack() && !finishing) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
