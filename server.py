# -*- coding: utf-8 -*-
"""
坚果云第三方应用管理客户端 - 后端服务
零依赖：仅使用 Python 3 标准库

接口原理（通过坚果云移动端网页 mobile_asp 实现）：
  - 列表:  GET  {server}/d/mobile_asp              -> 从 HTML 提取 userAspsStr JSON
  - 创建:  POST {server}/d/ajax/userop/generateAsp  -> asp_name=<名称>
  - 删除:  POST {server}/d/ajax/userop/revokeAsp    -> asp_name=<名称>
  - 登录:  POST {server}/d/login                     -> login_email / login_password
          （坚果云登录通常强制腾讯防水墙验证码，程序登录可能失败，
            此时推荐使用「Cookie 登录」：从已登录的浏览器复制 Cookie）
"""

import json
import re
import secrets
import urllib.parse
import urllib.request
import http.cookiejar
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = "127.0.0.1"
PORT = 8787

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

# 内存会话: token -> {"cookie": str, "server": str, "account": str}
SESSIONS = {}
SESSION_TTL = 24 * 3600  # 秒


# ---------------- 坚果云接口封装 ----------------

def normalize_server(server: str) -> str:
    s = (server or "").strip().rstrip("/")
    if not s:
        s = "https://www.jianguoyun.com"
    if not s.startswith(("http://", "https://")):
        s = "https://" + s
    return s


def http_request(url, method="GET", headers=None, data=None, cookie=""):
    """发起 HTTP 请求，返回 (status, body_text, set_cookie_list)"""
    hdrs = {
        "User-Agent": UA,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9",
        "Accept-Encoding": "identity",
    }
    if cookie:
        hdrs["Cookie"] = cookie
    if headers:
        hdrs.update(headers)
    body = None
    if data is not None:
        body = urllib.parse.urlencode(data).encode("utf-8")
    req = urllib.request.Request(url, data=body, method=method, headers=hdrs)
    set_cookies = []

    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            return None

    opener = urllib.request.build_opener(NoRedirect)
    try:
        resp = opener.open(req, timeout=30)
        status = resp.status
        set_cookies = resp.headers.get_all("Set-Cookie") or []
        raw = resp.read()
        enc = resp.headers.get_content_charset() or "utf-8"
        return status, raw.decode(enc, "replace"), set_cookies
    except urllib.error.HTTPError as e:
        raw = e.read()
        return e.code, raw.decode("utf-8", "replace"), e.headers.get_all("Set-Cookie") or []


def parse_cookie_str(set_cookie_list) -> str:
    """把 Set-Cookie 列表转成 name=value; 连接串"""
    parts = []
    for sc in set_cookie_list:
        m = re.match(r"\s*([^=;,\s]+)=([^;]*)", sc)
        if m:
            parts.append(f"{m.group(1)}={m.group(2)}")
    return "; ".join(parts)


def login_by_password(server: str, email: str, password: str):
    """账号密码登录。返回 (ok, cookie, errmsg)"""
    base = normalize_server(server)
    # 1. 先 GET 登录页拿基础 cookie
    status, body, sc = http_request(base + "/d/login")
    base_cookie = parse_cookie_str(sc)
    # 2. POST 登录
    status, body, sc = http_request(
        base + "/d/login",
        method="POST",
        headers={
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "Origin": base,
            "Referer": base + "/d/login",
        },
        data={
            "login_email": email,
            "login_password": password,
            "remember_me": "on",
            "login_dest_uri": "/d/home",
        },
        cookie=base_cookie,
    )
    new_cookie = parse_cookie_str(sc)
    cookie = "; ".join(x for x in [base_cookie, new_cookie] if x)
    if status in (200, 302) and "403" not in body[:2000] and "400" not in body[:2000]:
        # 检查是否真的登录成功（mobile_asp 可解析即成功）
        ok, _, _ = try_fetch_asps(base, cookie)
        if ok:
            return True, cookie, ""
    if "403" in body[:3000]:
        return False, cookie, "账号或密码不正确，或触发坚果云风控（403）"
    if "400" in body[:3000]:
        return False, cookie, ("登录被拒绝（400）：坚果云网页登录通常要求腾讯滑块验证码，"
                               "程序无法自动完成。请改用「Cookie 登录」方式。")
    return False, cookie, f"登录失败（HTTP {status}），建议改用 Cookie 登录方式"


def parse_asps_from_html(html: str):
    """从 mobile_asp 页面提取应用密码列表"""
    m = re.search(r"userAspsStr\s*:\s*'((?:\\'|[^'])*)'", html)
    if not m:
        return None
    raw = m.group(1).replace("\\\\", "\x00").replace("\\'", "'").replace("\x00", "\\")
    try:
        arr = json.loads(raw)
    except json.JSONDecodeError:
        return None
    result = []
    for item in arr:
        if not isinstance(item, dict):
            continue
        result.append({
            "name": str(item.get("name", "")).strip() or "未命名应用",
            "credential": str(item.get("credential", "")),
            "creationTime": str(item.get("creationTime", "")),
        })
    return result


def try_fetch_asps(server: str, cookie: str):
    """拉取应用密码列表。返回 (ok, list_or_errmsg)"""
    status, body, _ = http_request(
        normalize_server(server) + "/d/mobile_asp",
        cookie=cookie,
        headers={"Referer": normalize_server(server) + "/d/home"},
    )
    if status != 200:
        return False, f"HTTP {status}"
    asps = parse_asps_from_html(body)
    if asps is None:
        return False, "无法解析页面（登录态可能已失效）"
    return True, asps


def generate_asp(server: str, cookie: str, name: str):
    """创建应用密码。返回 (ok, dict_or_errmsg)"""
    status, body, _ = http_request(
        normalize_server(server) + "/d/ajax/userop/generateAsp",
        method="POST",
        cookie=cookie,
        headers={
            "Accept": "application/json, text/javascript, */*; q=0.01",
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "Origin": normalize_server(server),
            "Referer": normalize_server(server) + "/d/mobile_asp",
            "X-Requested-With": "XMLHttpRequest",
        },
        data={"asp_name": name},
    )
    if status != 200:
        return False, f"HTTP {status}"
    try:
        obj = json.loads(body)
        if "credential" not in obj:
            return False, body[:200] or "服务端未返回密码"
        return True, obj
    except json.JSONDecodeError:
        return False, body[:200] or "响应解析失败"


def revoke_asp(server: str, cookie: str, name: str):
    """删除应用密码。返回 (ok, errmsg)"""
    status, body, _ = http_request(
        normalize_server(server) + "/d/ajax/userop/revokeAsp",
        method="POST",
        cookie=cookie,
        headers={
            "Accept": "*/*",
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "Origin": normalize_server(server),
            "Referer": normalize_server(server) + "/d/mobile_asp",
            "X-Requested-With": "XMLHttpRequest",
        },
        data={"asp_name": name},
    )
    if status != 200:
        return False, f"HTTP {status}"
    return True, ""


# ---------------- HTTP 服务 ----------------

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        pass  # 静默日志

    # --- 工具 ---
    def _send_json(self, code, obj):
        payload = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(payload)

    def _read_body(self):
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0:
            return {}
        try:
            return json.loads(self.rfile.read(length).decode("utf-8"))
        except (json.JSONDecodeError, UnicodeDecodeError):
            return {}

    def _session(self):
        token = self.headers.get("X-Session-Token", "")
        return SESSIONS.get(token), token

    # --- 静态页面 ---
    def do_GET(self):
        if self.path in ("/", "/index.html"):
            try:
                with open(__file__.rsplit("\\", 1)[0].rsplit("/", 1)[0] + "/web/index.html", "rb") as f:
                    data = f.read()
            except OSError:
                try:
                    with open("web/index.html", "rb") as f:
                        data = f.read()
                except OSError:
                    self._send_json(404, {"error": "index.html 缺失"})
                    return
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        else:
            self._send_json(404, {"error": "not found"})

    # --- API ---
    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        route = parsed.path
        body = self._read_body()

        # 登录
        if route == "/api/login":
            server = normalize_server(body.get("server", ""))
            account = (body.get("account") or "").strip()
            password = (body.get("password") or "").strip()
            cookie = (body.get("cookie") or "").strip()
            mode = body.get("mode", "cookie")

            if mode == "password":
                if not account or not password:
                    self._send_json(400, {"ok": False, "error": "请填写账号和密码"})
                    return
                ok, cookie, err = login_by_password(server, account, password)
                if not ok:
                    self._send_json(401, {"ok": False, "error": err})
                    return
            else:
                if not cookie:
                    self._send_json(400, {"ok": False, "error": "请填写 Cookie"})
                    return
                if not account:
                    self._send_json(400, {"ok": False, "error": "请填写账号（坚果云邮箱）"})
                    return

            # 验证 cookie 有效性
            ok, data = try_fetch_asps(server, cookie)
            if not ok:
                self._send_json(401, {"ok": False, "error": f"Cookie 无效或已过期（{data}）。请重新从浏览器复制。"})
                return

            token = secrets.token_hex(16)
            SESSIONS[token] = {"cookie": cookie, "server": server, "account": account}
            self._send_json(200, {"ok": True, "token": token, "account": account, "server": server})
            return

        # 以下路由均需会话
        sess, token = self._session()
        if not sess:
            self._send_json(401, {"ok": False, "error": "未登录或会话已失效"})
            return
        server, cookie = sess["server"], sess["cookie"]

        # 创建应用密码
        if route == "/api/asps":
            name = (body.get("name") or "").strip()
            if not name:
                self._send_json(400, {"ok": False, "error": "请填写应用名称"})
                return
            if len(name) > 30:
                self._send_json(400, {"ok": False, "error": "应用名称过长（最多 30 字符）"})
                return
            ok, data = generate_asp(server, cookie, name)
            if not ok:
                # 会话失效时给出明确提示
                ok2, probe = try_fetch_asps(server, cookie)
                err = "登录态已失效，请重新登录" if not ok2 else f"创建失败（{data}）"
                self._send_json(400, {"ok": False, "error": err})
                return
            self._send_json(200, {"ok": True, "asp": {
                "name": data.get("name", name),
                "credential": data.get("credential", ""),
                "creationTime": data.get("creationTime", ""),
            }})
            return

        self._send_json(404, {"ok": False, "error": "not found"})

    def do_DELETE(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path != "/api/asps":
            self._send_json(404, {"ok": False, "error": "not found"})
            return
        sess, _ = self._session()
        if not sess:
            self._send_json(401, {"ok": False, "error": "未登录或会话已失效"})
            return
        qs = urllib.parse.parse_qs(parsed.query)
        name = (qs.get("name", [""])[0] or "").strip()
        if not name:
            self._send_json(400, {"ok": False, "error": "缺少应用名称"})
            return
        ok, err = revoke_asp(sess["server"], sess["cookie"], name)
        if not ok:
            self._send_json(400, {"ok": False, "error": f"删除失败（{err}）"})
            return
        self._send_json(200, {"ok": True})

    def do_PUT(self):
        # 列表用 PUT /api/asps 避免 DELETE 带 body 的兼容问题
        self.do_GET_list()

    def do_GET_list(self):
        pass


def main():
    # 让 GET /api/asps 生效（补在 do_GET 里）
    orig_do_get = Handler.do_GET

    def do_get(self):
        if urllib.parse.urlparse(self.path).path == "/api/asps":
            sess, _ = self._session()
            if not sess:
                self._send_json(401, {"ok": False, "error": "未登录或会话已失效"})
                return
            ok, data = try_fetch_asps(sess["server"], sess["cookie"])
            if not ok:
                self._send_json(401, {"ok": False, "error": f"登录态已失效（{data}），请重新登录"})
                return
            self._send_json(200, {"ok": True, "asps": data, "account": sess["account"],
                                  "server": sess["server"]})
            return
        orig_do_get(self)

    Handler.do_GET = do_get

    srv = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"坚果云第三方应用管理客户端已启动: http://{HOST}:{PORT}")
    print("按 Ctrl+C 退出")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        print("\n已退出")


if __name__ == "__main__":
    main()
