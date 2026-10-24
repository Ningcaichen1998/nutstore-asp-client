package com.workbuddy.nutstoreasp;

import android.app.Activity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 坚果云第三方应用密码接口封装。
 *
 * 原理：坚果云无公开的应用密码管理 API，本客户端调用其移动端网页接口
 * （与官方 Windows / 安卓客户端「第三方应用管理」同源）：
 *   - 列表:  GET  /d/mobile_asp              -> 从 HTML 提取 userAspsStr JSON
 *   - 创建:  POST /d/ajax/userop/generateAsp  -> asp_name=<名称>
 *   - 删除:  POST /d/ajax/userop/revokeAsp    -> asp_name=<名称>
 */
public class Api {

    public static final String UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Mobile Safari/537.36";

    private static final Pattern USER_ASPS =
            Pattern.compile("userAspsStr\\s*:\\s*'((?:\\\\'|[^'])*)'");

    public static class Asp {
        public String name = "";
        public String credential = "";
        public String creationTime = "";
    }

    /** 后台任务封装：子线程执行 run()，主线程回调 done() */
    public abstract static class Task<T> {
        public void start(final Activity ctx) {
            final T[] holder = (T[]) new Object[1];
            final String[] err = new String[1];
            Thread t = new Thread(() -> {
                try {
                    holder[0] = run();
                } catch (Exception e) {
                    err[0] = e.getMessage() == null ? e.toString() : e.getMessage();
                }
                ctx.runOnUiThread(() -> done(err[0] == null, holder[0], err[0]));
            });
            t.setDaemon(true);
            t.start();
        }

        protected abstract T run() throws Exception;

        protected abstract void done(boolean ok, T result, String error);
    }

    // ---------------- HTTP ----------------

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String http(String method, String url, String cookie,
                               String formBody, boolean xhr, String referer) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(25000);
        conn.setRequestProperty("User-Agent", UA);
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
        if (cookie != null && !cookie.trim().isEmpty()) {
            conn.setRequestProperty("Cookie", cookie);
        }
        if (xhr) {
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            conn.setRequestProperty("Referer", referer);
            conn.setRequestProperty("Origin", originOf(url));
            conn.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01");
        }
        if ("POST".equals(method)) {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8");
            byte[] body = formBody.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(body.length);
            OutputStream os = conn.getOutputStream();
            os.write(body);
            os.close();
        }
        int code = conn.getResponseCode();
        String body = readAll(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        conn.disconnect();
        if (code != 200) {
            throw new IOException("HTTP " + code);
        }
        return body;
    }

    private static String originOf(String url) {
        int idx = url.indexOf("/", url.indexOf("://") + 3);
        return idx > 0 ? url.substring(0, idx) : url;
    }

    private static String form(String name, String value) {
        try {
            return "asp_name=" + URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return "asp_name=";
        }
    }

    // ---------------- 业务接口 ----------------

    /** 拉取应用密码列表；cookie 无效时抛异常 */
    public static List<Asp> listAsps(String server, String cookie) throws IOException {
        String base = server.endsWith("/") ? server.substring(0, server.length() - 1) : server;
        String html = http("GET", base + "/d/mobile_asp", cookie, null, false, base + "/d/home");
        Matcher m = USER_ASPS.matcher(html);
        if (!m.find()) {
            throw new IOException("登录会话已失效，请重新登录");
        }
        String raw = m.group(1)
                .replace("\\\\", "\u0001")
                .replace("\\'", "'")
                .replace("\u0001", "\\");
        List<Asp> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Asp a = new Asp();
                a.name = o.optString("name", "").trim();
                if (a.name.isEmpty()) a.name = "未命名应用";
                a.credential = o.optString("credential", "");
                a.creationTime = o.optString("creationTime", "");
                list.add(a);
            }
        } catch (Exception e) {
            throw new IOException("解析应用列表失败：" + e.getMessage());
        }
        return list;
    }

    /** 创建应用密码，返回服务端响应 */
    public static Asp generateAsp(String server, String cookie, String name) throws IOException {
        String base = server.endsWith("/") ? server.substring(0, server.length() - 1) : server;
        String body = http("POST", base + "/d/ajax/userop/generateAsp",
                cookie, form("asp_name", name), true, base + "/d/mobile_asp");
        try {
            JSONObject o = new JSONObject(body);
            if (!o.has("credential")) {
                throw new IOException("服务端未返回密码：" + body.substring(0, Math.min(120, body.length())));
            }
            Asp a = new Asp();
            a.name = o.optString("name", name);
            a.credential = o.optString("credential", "");
            a.creationTime = o.optString("creationTime", "");
            return a;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("创建失败：" + e.getMessage());
        }
    }

    /** 删除应用密码 */
    public static void revokeAsp(String server, String cookie, String name) throws IOException {
        String base = server.endsWith("/") ? server.substring(0, server.length() - 1) : server;
        http("POST", base + "/d/ajax/userop/revokeAsp",
                cookie, form("asp_name", name), true, base + "/d/mobile_asp");
    }

    /** 校验 cookie 是否可解析应用列表（用于登录成功判定） */
    public static boolean cookieValid(String server, String cookie) {
        try {
            listAsps(server, cookie);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
