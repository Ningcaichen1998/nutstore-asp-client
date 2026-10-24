package com.workbuddy.nutstoreasp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_WEB_LOGIN = 1001;
    private static final String DAV_URL = "https://dav.jianguoyun.com/dav/";

    private View loginContainer;
    private View mainContainer;
    private TextView btnSettings;
    private TextView btnLogout;
    private EditText inServer;
    private EditText inAccount;
    private EditText inPassword;
    private Button btnLogin;

    // 卡片（用于应用透明度）
    private View loginCard;
    private View mineCardInfo;
    private View mineCardStats;
    private View manageCardCreate;

    // 「我的」页
    private View tabMine;
    private View tabManage;
    private TextView tabMineBtn;
    private TextView tabManageBtn;
    private TextView mineDav;
    private TextView mineAccount;
    private TextView minePassword;
    private TextView mineCount;
    private TextView mineRecentName;
    private TextView mineRecentTime;

    // 「管理」页
    private EditText inAppName;
    private Button btnCreate;
    private ListView listAsps;
    private TextView txtEmpty;
    private ProgressBar loading;

    private AspAdapter adapter;
    private List<Api.Asp> currentAsps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loginContainer = findViewById(R.id.login_container);
        mainContainer = findViewById(R.id.main_container);
        btnSettings = findViewById(R.id.btn_settings);
        btnLogout = findViewById(R.id.btn_logout);
        inServer = findViewById(R.id.in_server);
        inAccount = findViewById(R.id.in_account);
        inPassword = findViewById(R.id.in_password);
        btnLogin = findViewById(R.id.btn_login);

        loginCard = findViewById(R.id.login_card);
        mineCardInfo = findViewById(R.id.mine_card_info);
        mineCardStats = findViewById(R.id.mine_card_stats);
        manageCardCreate = findViewById(R.id.manage_card_create);

        tabMine = findViewById(R.id.tab_mine);
        tabManage = findViewById(R.id.tab_manage);
        tabMineBtn = findViewById(R.id.tab_mine_btn);
        tabManageBtn = findViewById(R.id.tab_manage_btn);

        mineDav = findViewById(R.id.mine_dav);
        mineAccount = findViewById(R.id.mine_account);
        minePassword = findViewById(R.id.mine_password);
        mineCount = findViewById(R.id.mine_count);
        mineRecentName = findViewById(R.id.mine_recent_name);
        mineRecentTime = findViewById(R.id.mine_recent_time);

        inAppName = findViewById(R.id.in_appname);
        btnCreate = findViewById(R.id.btn_create);
        listAsps = findViewById(R.id.list_asps);
        txtEmpty = findViewById(R.id.txt_empty);
        loading = findViewById(R.id.loading);

        adapter = new AspAdapter(this, asp -> confirmDelete(asp));
        listAsps.setAdapter(adapter);
        listAsps.setEmptyView(txtEmpty);

        btnLogin.setOnClickListener(v -> startWebLogin());
        btnCreate.setOnClickListener(v -> createAsp());
        btnLogout.setOnClickListener(v -> logout());
        btnSettings.setOnClickListener(v -> showSettings());

        tabMineBtn.setOnClickListener(v -> switchTab(true));
        tabManageBtn.setOnClickListener(v -> switchTab(false));

        findViewById(R.id.mine_btn_copy_dav).setOnClickListener(
                v -> copyText(DAV_URL, "WebDAV 地址已复制"));
        findViewById(R.id.mine_btn_copy_account).setOnClickListener(
                v -> copyText(mineAccount.getText().toString(), "账号已复制"));
        findViewById(R.id.mine_btn_copy_password).setOnClickListener(
                v -> copyText(minePassword.getText().toString(), "密码已复制"));

        if (Store.hasLogin(this)) {
            showMain();
        } else {
            showLogin();
        }
    }

    // ---------------- 视图切换 ----------------

    private void showLogin() {
        loginContainer.setVisibility(View.VISIBLE);
        mainContainer.setVisibility(View.GONE);
        btnSettings.setVisibility(View.GONE);
        btnLogout.setVisibility(View.GONE);
        inAccount.setText(Store.account(this));
        inPassword.setText("");
        applyCardAlpha();
    }

    private void showMain() {
        loginContainer.setVisibility(View.GONE);
        mainContainer.setVisibility(View.VISIBLE);
        btnSettings.setVisibility(View.VISIBLE);
        btnLogout.setVisibility(View.VISIBLE);
        fillMineHeader();
        switchTab(true);
        applyCardAlpha();
        loadAsps();
    }

    private void logout() {
        Store.clear(this);
        Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show();
        showLogin();
    }

    /** 应用自定义卡片透明度 */
    private void applyCardAlpha() {
        int alpha = Store.cardAlpha(this);
        loginCard.setBackground(Ui.cardBg(this, alpha));
        mineCardInfo.setBackground(Ui.cardBg(this, alpha));
        mineCardStats.setBackground(Ui.cardBg(this, alpha));
        manageCardCreate.setBackground(Ui.cardBg(this, alpha));
    }

    // ---------------- 底栏切换 ----------------

    private void switchTab(boolean mine) {
        tabMine.setVisibility(mine ? View.VISIBLE : View.GONE);
        tabManage.setVisibility(mine ? View.GONE : View.VISIBLE);
        if (mine) {
            tabMineBtn.setBackgroundResource(R.drawable.bg_tab_selected);
            tabMineBtn.setTextColor(getResources().getColor(R.color.primary));
            tabMineBtn.setTypeface(null, Typeface.BOLD);
            tabManageBtn.setBackground(null);
            tabManageBtn.setTextColor(0xFF5A6B7E);
            tabManageBtn.setTypeface(null, Typeface.NORMAL);
        } else {
            tabManageBtn.setBackgroundResource(R.drawable.bg_tab_selected);
            tabManageBtn.setTextColor(getResources().getColor(R.color.primary));
            tabManageBtn.setTypeface(null, Typeface.BOLD);
            tabMineBtn.setBackground(null);
            tabMineBtn.setTextColor(0xFF5A6B7E);
            tabMineBtn.setTypeface(null, Typeface.NORMAL);
        }
    }

    // ---------------- 我的页 ----------------

    private void fillMineHeader() {
        mineDav.setText(DAV_URL);
        mineAccount.setText(Store.account(this).isEmpty() ? "-" : Store.account(this));
        String pwd = Store.password(this);
        minePassword.setText(pwd.isEmpty() ? "未保存" : pwd);
        updateMineStats();
    }

    private void updateMineStats() {
        if (currentAsps == null) {
            mineCount.setText("-");
            mineRecentName.setText("-");
            mineRecentTime.setText("");
            return;
        }
        mineCount.setText(String.valueOf(currentAsps.size()) + " 个");
        Api.Asp latest = null;
        for (Api.Asp a : currentAsps) {
            if (latest == null || a.creationTime.compareTo(latest.creationTime) > 0) {
                latest = a;
            }
        }
        if (latest != null) {
            mineRecentName.setText(latest.name);
            mineRecentTime.setText("创建于 " + (latest.creationTime.isEmpty() ? "-" : latest.creationTime));
        } else {
            mineRecentName.setText("无");
            mineRecentTime.setText("");
        }
    }

    // ---------------- 设置 ----------------

    private void showSettings() {
        android.widget.CheckBox cbPlain = new android.widget.CheckBox(this);
        cbPlain.setText("默认显示应用密码（不隐藏）");
        android.widget.CheckBox cbClickCopy = new android.widget.CheckBox(this);
        cbClickCopy.setText("点击应用密码即自动复制");

        int pad = dp(18);
        for (android.widget.CheckBox cb : new android.widget.CheckBox[]{cbPlain, cbClickCopy}) {
            cb.setTextSize(15);
            cb.setTextColor(0xFF243342);
            cb.setPadding(pad, pad / 2, pad, pad / 2);
        }
        cbPlain.setChecked(Store.showPasswordPlain(this));
        cbClickCopy.setChecked(Store.clickToCopy(this));

        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, pad);
        box.addView(cbPlain);
        box.addView(cbClickCopy);

        // 卡片透明度
        TextView alphaLabel = new TextView(this);
        alphaLabel.setText("卡片透明度");
        alphaLabel.setTextSize(14);
        alphaLabel.setTextColor(0xFF243342);
        alphaLabel.setPadding(pad, dp(10), pad, dp(2));
        box.addView(alphaLabel);
        final android.widget.SeekBar seekAlpha = new android.widget.SeekBar(this);
        seekAlpha.setMax(100);
        seekAlpha.setProgress(alphaToProgress(Store.cardAlpha(this)));
        box.addView(seekAlpha);

        // 分隔线
        View divider = new View(this);
        divider.setBackgroundColor(0xFFE3EAF3);
        android.widget.LinearLayout.LayoutParams dlp =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        dlp.setMargins(0, dp(14), 0, dp(6));
        divider.setLayoutParams(dlp);
        box.addView(divider);

        // 关于行
        TextView aboutRow = new TextView(this);
        aboutRow.setText("关于");
        aboutRow.setTextSize(15);
        aboutRow.setTextColor(0xFF243342);
        aboutRow.setPadding(0, dp(10), 0, dp(4));
        box.addView(aboutRow);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("设置")
                .setView(box)
                .setPositiveButton("确定", (d, w) -> {
                    Store.setShowPasswordPlain(this, cbPlain.isChecked());
                    Store.setClickToCopy(this, cbClickCopy.isChecked());
                    Store.setCardAlpha(this, progressToAlpha(seekAlpha.getProgress()));
                    applyCardAlpha();
                    adapter.notifyDataSetChanged();
                })
                .setNegativeButton("取消", null)
                .create();

        aboutRow.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(MainActivity.this, AboutActivity.class));
        });

        dialog.show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int progressToAlpha(int p) {
        return 255 - (int) (p * 1.6);
    }

    private int alphaToProgress(int a) {
        return (int) ((255 - a) / 1.6 + 0.5);
    }

    // ---------------- 登录 ----------------

    private void startWebLogin() {
        final String server = inServer.getText().toString().trim();
        final String account = inAccount.getText().toString().trim();
        final String password = inPassword.getText().toString();

        if (account.isEmpty()) {
            Toast.makeText(this, "请填写账号（坚果云邮箱）", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.isEmpty()) {
            Toast.makeText(this, "请填写密码", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent it = new Intent(this, WebLoginActivity.class);
        it.putExtra(WebLoginActivity.EXTRA_SERVER, server);
        it.putExtra(WebLoginActivity.EXTRA_ACCOUNT, account);
        it.putExtra(WebLoginActivity.EXTRA_PASSWORD, password);
        startActivityForResult(it, REQ_WEB_LOGIN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_WEB_LOGIN && resultCode == RESULT_OK) {
            Toast.makeText(this, "登录成功，会话已保存", Toast.LENGTH_SHORT).show();
            showMain();
        }
    }

    // ---------------- 应用列表 ----------------

    private void loadAsps() {
        loading.setVisibility(View.VISIBLE);
        txtEmpty.setVisibility(View.GONE);
        final String server = Store.server(this);
        final String cookie = Store.cookie(this);
        new Api.Task<List<Api.Asp>>() {
            @Override
            protected List<Api.Asp> run() throws Exception {
                return Api.listAsps(server, cookie);
            }

            @Override
            protected void done(boolean ok, List<Api.Asp> result, String error) {
                loading.setVisibility(View.GONE);
                if (ok) {
                    currentAsps = result;
                    adapter.setData(result);
                    updateMineStats();
                } else {
                    currentAsps = null;
                    adapter.setData(null);
                    updateMineStats();
                    txtEmpty.setText(error + "\n登录会话可能已失效，请退出后重新登录");
                    txtEmpty.setVisibility(View.VISIBLE);
                }
            }
        }.start(this);
    }

    // ---------------- 创建 ----------------

    private void createAsp() {
        final String name = inAppName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "请先输入应用名称", Toast.LENGTH_SHORT).show();
            return;
        }
        btnCreate.setEnabled(false);
        btnCreate.setText("生成中…");
        final String server = Store.server(this);
        final String cookie = Store.cookie(this);
        new Api.Task<Api.Asp>() {
            @Override
            protected Api.Asp run() throws Exception {
                return Api.generateAsp(server, cookie, name);
            }

            @Override
            protected void done(boolean ok, Api.Asp result, String error) {
                btnCreate.setEnabled(true);
                btnCreate.setText("生成密码");
                if (!ok) {
                    Toast.makeText(MainActivity.this,
                            "创建失败：" + error, Toast.LENGTH_LONG).show();
                    return;
                }
                inAppName.setText("");
                showNewPassword(result);
                loadAsps();
            }
        }.start(this);
    }

    private void showNewPassword(final Api.Asp asp) {
        View v = getLayoutInflater().inflate(R.layout.dialog_newpw, null);
        TextView txtPw = v.findViewById(R.id.txt_newpw);
        TextView txtMeta = v.findViewById(R.id.txt_newmeta);
        txtPw.setText(asp.credential);
        txtMeta.setText("应用名称：" + asp.name + "\n授权日期：" + asp.creationTime);
        v.findViewById(R.id.btn_copy_newpw).setOnClickListener(
                b -> copyText(asp.credential, "应用密码已复制"));

        new AlertDialog.Builder(this)
                .setView(v)
                .setPositiveButton("我已保存", null)
                .setCancelable(false)
                .show();
    }

    // ---------------- 删除 ----------------

    private void confirmDelete(final Api.Asp asp) {
        new AlertDialog.Builder(this)
                .setTitle("删除应用密码")
                .setMessage("确定删除「" + asp.name + "」吗？\n删除后使用该密码的第三方应用将无法继续访问坚果云。")
                .setPositiveButton("删除", (d, w) -> deleteAsp(asp))
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteAsp(final Api.Asp asp) {
        final String server = Store.server(this);
        final String cookie = Store.cookie(this);
        new Api.Task<Void>() {
            @Override
            protected Void run() throws Exception {
                Api.revokeAsp(server, cookie, asp.name);
                return null;
            }

            @Override
            protected void done(boolean ok, Void result, String error) {
                if (ok) {
                    Toast.makeText(MainActivity.this,
                            "已删除「" + asp.name + "」", Toast.LENGTH_SHORT).show();
                    loadAsps();
                } else {
                    Toast.makeText(MainActivity.this,
                            "删除失败：" + error, Toast.LENGTH_LONG).show();
                }
            }
        }.start(this);
    }

    // ---------------- 工具 ----------------

    private void copyText(String text, String tip) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("nutstore", text));
            Toast.makeText(this, tip, Toast.LENGTH_SHORT).show();
        }
    }
}
