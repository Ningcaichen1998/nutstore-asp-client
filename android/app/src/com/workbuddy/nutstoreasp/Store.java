package com.workbuddy.nutstoreasp;

import android.content.Context;
import android.content.SharedPreferences;

/** 本地凭证存储（仅保存在本机私有目录，不上传任何服务器） */
public class Store {
    private static final String PREFS = "nutstore_asp";

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void saveLogin(Context c, String server, String account, String cookie, String password) {
        prefs(c).edit()
                .putString("server", server)
                .putString("account", account)
                .putString("cookie", cookie)
                .putString("password", password == null ? "" : password)
                .apply();
    }

    public static String server(Context c) {
        return prefs(c).getString("server", "https://www.jianguoyun.com");
    }

    public static String account(Context c) {
        return prefs(c).getString("account", "");
    }

    public static String password(Context c) {
        return prefs(c).getString("password", "");
    }

    public static String cookie(Context c) {
        return prefs(c).getString("cookie", "");
    }

    public static boolean hasLogin(Context c) {
        String ck = cookie(c);
        return ck != null && !ck.trim().isEmpty();
    }

    /** 设置：默认明文显示应用密码（不隐藏） */
    public static boolean showPasswordPlain(Context c) {
        return prefs(c).getBoolean("show_password_plain", false);
    }

    public static void setShowPasswordPlain(Context c, boolean v) {
        prefs(c).edit().putBoolean("show_password_plain", v).apply();
    }

    /** 设置：点击应用密码即自动复制 */
    public static boolean clickToCopy(Context c) {
        return prefs(c).getBoolean("click_to_copy", false);
    }

    public static void setClickToCopy(Context c, boolean v) {
        prefs(c).edit().putBoolean("click_to_copy", v).apply();
    }

    /** 设置：卡片背景透明度（0-255，255 为完全不透明） */
    public static int cardAlpha(Context c) {
        return prefs(c).getInt("card_alpha", 255);
    }

    public static void setCardAlpha(Context c, int v) {
        prefs(c).edit().putInt("card_alpha", v).apply();
    }

    public static void clear(Context c) {
        prefs(c).edit().clear().apply();
    }
}
