package com.workbuddy.nutstoreasp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

/** 关于界面：版本号、查看源代码、背景图来源 */
public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        // 动态读取版本号，与 manifest 保持一致
        String ver = "1.0";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        ((TextView) findViewById(R.id.about_version)).setText("版本 " + ver);
        findViewById(R.id.about_card).setBackground(Ui.cardBg(this, Store.cardAlpha(this)));

        findViewById(R.id.about_back).setOnClickListener(v -> finish());
        findViewById(R.id.about_source).setOnClickListener(v ->
                openUrl("https://github.com/Ningcaichen1998/nutstore-asp-client"));
        findViewById(R.id.about_bg_credit).setOnClickListener(v ->
                openUrl("https://weibo.com/u/6034212582"));
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }
}
