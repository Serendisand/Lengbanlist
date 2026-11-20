#!/usr/bin/env python3
"""Lengbanlist 管理面板本地 Mock 后端。

只用于**前端联调**：不启动 Minecraft、不连数据库，所有数据都在内存里，
重启即恢复样例。接口形状与 `org.leng.web` 下的真实控制器保持一致。

用法：
    python scripts/mock-web-server.py
    python scripts/mock-web-server.py --port 8080
    python scripts/mock-web-server.py --user admin --password lban123

打开 http://127.0.0.1:8080/ 即可。登录默认 admin / lban123。
"""

from __future__ import annotations

import argparse
import base64
import json
import mimetypes
import re
import threading
import time
import urllib.parse
import uuid
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

# 永久封禁的时间戳，与核心 TimeUtils 的 Long.MAX_VALUE 语义一致
PERMANENT = 2**63 - 1
DAY_MS = 24 * 60 * 60 * 1000

UNIT_MS = {
    "s": 1000,
    "m": 60 * 1000,
    "h": 60 * 60 * 1000,
    "d": DAY_MS,
    "w": 7 * DAY_MS,
    "M": 30 * DAY_MS,
    "y": 365 * DAY_MS,
}
DURATION_RE = re.compile(r"^(\d+)([smhdwMy])$")

ALL_BUTTONS = [
    "ban", "unban", "mute", "unmute", "warn", "report",
    "audit", "player", "ip", "history", "alts", "broadcast",
]


# ---------------------------------------------------------------- 时间工具

def now_ms() -> int:
    return int(time.time() * 1000)


def readable(timestamp: int) -> str:
    if timestamp >= PERMANENT:
        return "永久"
    return datetime.fromtimestamp(timestamp / 1000).strftime("%Y-%m-%d %H:%M:%S")


def format_duration(millis: int) -> str:
    """与核心 TimeUtils.formatDuration 的中文口径一致（粗粒度）。"""
    if millis >= PERMANENT:
        return "永久"
    if millis <= 0:
        return "0秒"
    seconds = millis // 1000
    if seconds < 60:
        return f"{seconds}秒"
    minutes = seconds // 60
    if minutes < 60:
        return f"{minutes}分钟"
    hours = minutes // 60
    if hours < 24:
        return f"{hours}小时"
    days = hours // 24
    if days < 7:
        return f"{days}天"
    weeks = days // 7
    if weeks < 4:
        return f"{weeks}周"
    months = days // 30
    if months < 12:
        return f"{months}个月"
    return f"{days // 365}年"


def format_remaining(end: int) -> str:
    if end >= PERMANENT:
        return "永久"
    left = end - now_ms()
    return "已到期" if left <= 0 else format_duration(left)


def parse_duration(text: str) -> int:
    """支持 30m / 7d / forever / auto；非法值按 7 天处理，与真实后端一致。"""
    raw = (text or "").strip()
    if raw.lower() in ("forever", "perm", "permanent"):
        return PERMANENT
    if raw.lower() == "auto":
        return 7 * DAY_MS
    match = DURATION_RE.match(raw)
    if not match:
        return 7 * DAY_MS
    return int(match.group(1)) * UNIT_MS[match.group(2)]


# ---------------------------------------------------------------- 内存数据

class MockState:
    def __init__(self) -> None:
        self.lock = threading.RLock()
        current = now_ms()
        self.bans: list[dict[str, Any]] = [
            {"target": "Steve", "staff": "Admin", "reason": "使用外挂/作弊",
             "start": current - 2 * DAY_MS, "end": current + 5 * DAY_MS, "auto": False},
            {"target": "Alex", "staff": "Console", "reason": "恶意破坏",
             "start": current - 10 * DAY_MS, "end": PERMANENT, "auto": False},
            {"target": "Herobrine", "staff": "Admin", "reason": "刷屏广告",
             "start": current - 30 * 60 * 1000, "end": current + 30 * 60 * 1000, "auto": True},
        ]
        self.ip_bans: list[dict[str, Any]] = [
            {"ip": "203.0.113.42", "staff": "Admin", "reason": "使用代理/VPN",
             "start": current - DAY_MS, "end": current + 29 * DAY_MS},
        ]
        self.mutes: list[dict[str, Any]] = [
            {"target": "Notch", "staff": "Admin", "reason": "言语辱骂",
             "start": current - 2 * 60 * 60 * 1000, "end": current + 22 * 60 * 60 * 1000},
            {"target": "Griefer123", "staff": "Console", "reason": "刷屏",
             "start": current - 3 * DAY_MS, "end": current + 4 * DAY_MS},
        ]
        self.warnings: dict[str, list[dict[str, Any]]] = {
            "Steve": [
                {"staff": "Admin", "reason": "轻微刷屏", "time": current - 5 * DAY_MS, "revoked": False},
                {"staff": "Admin", "reason": "辱骂他人", "time": current - 2 * DAY_MS, "revoked": False},
            ],
            "Alex": [
                {"staff": "Console", "reason": "破坏建筑", "time": current - 20 * DAY_MS, "revoked": True},
            ],
        }
        self.reports: list[dict[str, Any]] = [
            {"id": "R-1001", "target": "Steve", "reporter": "Alex",
             "reason": "在出生点使用外挂", "status": "未处理", "timestamp": current - 15 * 60 * 1000},
            {"id": "R-1002", "target": "Alex", "reporter": "Notch",
             "reason": "恶意破坏公共建筑", "status": "未处理", "timestamp": current - 2 * 60 * 60 * 1000},
            {"id": "R-1003", "target": "Herobrine", "reporter": "Steve",
             "reason": "聊天室刷广告", "status": "未处理", "timestamp": current - DAY_MS},
        ]
        self.appeals: list[dict[str, Any]] = [
            {"id": "A-2001", "target": "Steve", "contact": "steve@example.com",
             "reason": "我没有使用外挂，是误判，请管理员复核。", "status": "待处理",
             "createdAt": current - 3 * 60 * 60 * 1000, "handledBy": "", "handledAt": 0, "response": "",
             "ticket": "T-AAAA1111"},
            {"id": "A-2002", "target": "Alex", "contact": "QQ 123456",
             "reason": "建筑是别人破坏的，我只是路过。", "status": "待处理",
             "createdAt": current - 26 * 60 * 60 * 1000, "handledBy": "", "handledAt": 0, "response": "",
             "ticket": "T-BBBB2222"},
            {"id": "A-2003", "target": "Griefer123", "contact": "",
             "reason": "已经认识到错误，申请提前解封。", "status": "已驳回",
             "createdAt": current - 5 * DAY_MS, "handledBy": "Admin", "handledAt": current - 4 * DAY_MS,
             "response": "违规情节较重，暂不解封。", "ticket": "T-CCCC3333"},
        ]
        self.audit: list[dict[str, Any]] = [
            {"id": 1, "timestamp": current - 5 * 60 * 1000, "actor": "Admin", "action": "封禁玩家",
             "target": "Steve", "reason": "使用外挂/作弊", "success": True, "server": "lobby"},
            {"id": 2, "timestamp": current - 20 * 60 * 1000, "actor": "Admin", "action": "禁言玩家",
             "target": "Notch", "reason": "言语辱骂", "success": True, "server": "lobby"},
            {"id": 3, "timestamp": current - 45 * 60 * 1000, "actor": "Console", "action": "解封",
             "target": "Herobrine", "reason": "申诉通过", "success": True, "server": "lobby"},
            {"id": 4, "timestamp": current - 2 * 60 * 60 * 1000, "actor": "Admin", "action": "开启隐身",
             "target": "Admin", "reason": "", "success": True, "server": "lobby"},
            {"id": 5, "timestamp": current - 3 * 60 * 60 * 1000, "actor": "Admin", "action": "广播封禁人数",
             "target": "", "reason": "", "success": True, "server": "lobby"},
            {"id": 6, "timestamp": current - 5 * 60 * 60 * 1000, "actor": "Console", "action": "踢出玩家",
             "target": "Griefer123", "reason": "AFK", "success": False, "server": "survival"},
        ]
        self.online: list[dict[str, Any]] = [
            {"name": "Admin", "uuid": "00000000-0000-0000-0000-000000000001", "ping": 18},
            {"name": "Steve", "uuid": "00000000-0000-0000-0000-000000000002", "ping": 42},
            {"name": "Alex", "uuid": "00000000-0000-0000-0000-000000000003", "ping": 67},
            {"name": "Notch", "uuid": "00000000-0000-0000-0000-000000000004", "ping": 91},
        ]
        self.theme: dict[str, Any] = {
            "background_type": "default",
            "background_url": "",
            "background_file": "",
            "served_url": "",
            "hidden_buttons": [],
        }
        self.uploads: dict[str, tuple[bytes, str]] = {}
        self.audit_seq = 100

    # ------------------------------------------------------------ 审计

    def add_audit(self, actor: str, action: str, target: str = "", reason: str = "",
                  success: bool = True, server: str = "lobby") -> None:
        self.audit_seq += 1
        self.audit.insert(0, {
            "id": self.audit_seq,
            "timestamp": now_ms(),
            "actor": actor or "Admin",
            "action": action,
            "target": target or "",
            "reason": reason or "",
            "success": success,
            "server": server,
        })

    # ------------------------------------------------------------ 序列化

    @staticmethod
    def ban_json(record: dict[str, Any]) -> dict[str, Any]:
        return {
            "target": record["target"],
            "staff": record["staff"],
            "reason": record["reason"],
            "end_time": readable(record["end"]),
            "duration": format_duration(record["end"] - record["start"]),
            "remaining": format_remaining(record["end"]),
            "active": record["end"] >= PERMANENT or record["end"] > now_ms(),
            "auto": record.get("auto", False),
        }

    @staticmethod
    def ip_ban_json(record: dict[str, Any]) -> dict[str, Any]:
        return {
            "ip": record["ip"],
            "staff": record["staff"],
            "reason": record["reason"],
            "end_time": readable(record["end"]),
            "duration": format_duration(record["end"] - record["start"]),
            "remaining": format_remaining(record["end"]),
            "active": record["end"] >= PERMANENT or record["end"] > now_ms(),
        }

    @staticmethod
    def mute_json(record: dict[str, Any]) -> dict[str, Any]:
        return {
            "target": record["target"],
            "staff": record["staff"],
            "reason": record["reason"],
            "end_time": readable(record["end"]),
            "duration": format_duration(record["end"] - record["start"]),
            "remaining": format_remaining(record["end"]),
            "active": record["end"] >= PERMANENT or record["end"] > now_ms(),
        }


STATE = MockState()


# ---------------------------------------------------------------- 工具

def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def make_token(username: str) -> str:
    header = b64url(json.dumps({"alg": "none", "typ": "JWT"}).encode())
    payload = b64url(json.dumps({"sub": username, "exp": int(time.time()) + 86400}).encode())
    return f"{header}.{payload}.mock"


def token_subject(token: str) -> str:
    try:
        payload = token.split(".")[1]
        payload += "=" * (-len(payload) % 4)
        data = json.loads(base64.urlsafe_b64decode(payload))
        return data.get("sub") or "Admin"
    except Exception:
        return "Admin"


def looks_like_ip(value: str) -> bool:
    return bool(re.fullmatch(r"[0-9a-fA-F:.]+(/\d{1,3})?", value)) and (
        ":" in value or "." in value
    )


def safe_filename(name: str) -> str:
    name = urllib.parse.unquote(name or "background.png")
    name = Path(name).name
    name = re.sub(r"[^a-zA-Z0-9._-]", "_", name)
    if not name:
        name = "background.png"
    if "." not in name:
        name += ".png"
    return name


def find_web_dir(explicit: str | None) -> Path:
    root = Path(__file__).resolve().parent.parent
    candidates = [
        Path(explicit) if explicit else None,
        root / "Lengbanlist - main" / "src" / "main" / "resources" / "web",
        root / "Lengbanlist - main" / "target" / "classes" / "web",
        root / "web",
    ]
    for candidate in candidates:
        if candidate and (candidate / "index.html").is_file():
            return candidate.resolve()
    raise SystemExit("找不到前端目录，请用 --web-dir 指定含 index.html 的目录。")


# ---------------------------------------------------------------- HTTP

class MockHandler(BaseHTTPRequestHandler):
    server_version = "LengbanlistMock/1.0"

    def __init__(self, *args: Any, web_dir: Path, user: str, password: str, **kwargs: Any) -> None:
        self.web_dir = web_dir
        self.user = user
        self.password = password
        super().__init__(*args, **kwargs)

    # -------------------------------------------------------- 基础响应

    def log_message(self, fmt: str, *args: Any) -> None:
        print(f"[mock] {self.address_string()} {fmt % args}")

    def _cors(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers",
                         "Content-Type, Authorization, Content-Disposition")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")

    def _json(self, status: int, payload: Any) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self._cors()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _bytes(self, status: int, body: bytes, content_type: str) -> None:
        self.send_response(status)
        self._cors()
        self.send_header("Content-Type", content_type)
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _error(self, status: int, message: str) -> None:
        self._json(status, {"error": message})

    def _body(self) -> bytes:
        length = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(length) if length > 0 else b""

    def _json_body(self) -> dict[str, Any]:
        raw = self._body()
        if not raw:
            return {}
        try:
            data = json.loads(raw.decode("utf-8"))
            return data if isinstance(data, dict) else {}
        except (UnicodeDecodeError, json.JSONDecodeError):
            return {}

    # -------------------------------------------------------- 鉴权

    def _check_auth(self, path: str) -> bool:
        if not path.startswith("/api/") or path in ("/api/login",) or path.startswith("/api/public/"):
            return True
        # 背景图由浏览器直接请求（<img>/background-image），带不上 Authorization 头；
        # 真实 ThemeController 的这个端点同样不鉴权。
        if path.startswith("/api/theme/file/"):
            return True
        header = self.headers.get("Authorization") or ""
        if header.startswith("Bearer ") and header[7:].strip():
            return True
        self._error(401, "未登录或登录已过期")
        return False

    def _actor(self) -> str:
        header = self.headers.get("Authorization") or ""
        return token_subject(header[7:].strip()) if header.startswith("Bearer ") else "Admin"

    # -------------------------------------------------------- 路由

    def do_OPTIONS(self) -> None:  # noqa: N802
        self.send_response(204)
        self._cors()
        self.end_headers()

    def do_GET(self) -> None:  # noqa: N802
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        if path.startswith("/api/"):
            if not self._check_auth(path):
                return
            self._handle_api_get(path, urllib.parse.parse_qs(parsed.query))
            return
        self._serve_static(path)

    def do_POST(self) -> None:  # noqa: N802
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        if not path.startswith("/api/"):
            self._error(404, "仅支持 API 与静态资源")
            return
        if not self._check_auth(path):
            return
        self._handle_api_post(path, urllib.parse.parse_qs(parsed.query))

    # -------------------------------------------------------- 静态文件

    def _serve_static(self, path: str) -> None:
        if path in ("/", ""):
            relative = "index.html"
        elif path in ("/appeal", "/appeal/"):
            relative = "appeal.html"
        else:
            relative = path.lstrip("/")

        target = (self.web_dir / relative).resolve()
        try:
            target.relative_to(self.web_dir)
        except ValueError:
            self._error(403, "非法路径")
            return
        if not target.is_file():
            self._error(404, "文件不存在")
            return

        # ?autologin=1：本地联调时跳过登录页，直接带 mock token 进入面板。
        query = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
        if relative == "index.html" and query.get("autologin", ["0"])[0] in ("1", "true", "yes"):
            html = target.read_text(encoding="utf-8")
            token = json.dumps(make_token(self.user))
            script = ("<script>try{localStorage.setItem('lengbanlist_token'," + token +
                      ");localStorage.setItem('lengbanlist_eula','accepted');}catch(e){}"
                      "setTimeout(function(){var t=new URLSearchParams(location.search).get('tab');"
                      "if(t){var b=document.querySelector('[data-tab=\"'+t+'\"]');if(b)b.click();}},1500);"
                      "</script>")
            html = html.replace("</head>", script + "</head>", 1)
            self._bytes(200, html.encode("utf-8"), "text/html; charset=utf-8")
            return

        content_type = mimetypes.guess_type(str(target))[0] or "application/octet-stream"
        if content_type.startswith("text/") or content_type in ("application/javascript", "application/json"):
            content_type += "; charset=utf-8"
        self._bytes(200, target.read_bytes(), content_type)

    # -------------------------------------------------------- GET API

    def _handle_api_get(self, path: str, query: dict[str, list[str]]) -> None:
        with STATE.lock:
            if path == "/api/stats":
                self._stats()
            elif path == "/api/online":
                self._json(200, {"players": STATE.online, "total": len(STATE.online)})
            elif path == "/api/players":
                self._players(query.get("q", [""])[0])
            elif path == "/api/bans":
                bans = [STATE.ban_json(b) for b in STATE.bans]
                self._json(200, {"bans": bans, "total": len(bans)})
            elif path == "/api/ipbans":
                ip_bans = [STATE.ip_ban_json(b) for b in STATE.ip_bans]
                self._json(200, {"ip_bans": ip_bans, "total": len(ip_bans)})
            elif path == "/api/mutes":
                mutes = [STATE.mute_json(m) for m in STATE.mutes]
                self._json(200, {"mutes": mutes, "total": len(mutes)})
            elif path == "/api/reports":
                reports = [r for r in STATE.reports if r["status"] == "未处理"]
                self._json(200, {"reports": reports, "total": len(reports)})
            elif path == "/api/appeals":
                self._appeals(query.get("status", [""])[0])
            elif path == "/api/history":
                self._history(query.get("player", [""])[0])
            elif path == "/api/audit":
                self._audit(query)
            elif path == "/api/theme":
                self._theme_get()
            elif path.startswith("/api/theme/file/"):
                self._theme_file(path[len("/api/theme/file/"):])
            elif path == "/api/public/appeal/status":
                self._appeal_status(query.get("ticket", [""])[0])
            else:
                self._error(404, f"未知接口: {path}")

    def _stats(self) -> None:
        current = now_ms()
        active_bans = sum(1 for b in STATE.bans if b["end"] >= PERMANENT or b["end"] > current)
        recent = [STATE.ban_json(b) for b in STATE.bans[:5]]
        warned = sum(1 for warns in STATE.warnings.values() if any(not w["revoked"] for w in warns))
        self._json(200, {
            "plugin_version": "2.2.1-mock",
            "online_players": len(STATE.online),
            "max_players": 20,
            "database_status": "正常",
            "database_type": "SQLite (mock)",
            "total_bans": len(STATE.bans) + len(STATE.ip_bans),
            "active_bans": active_bans,
            "ip_bans": len(STATE.ip_bans),
            "mutes": len(STATE.mutes),
            "warnings": warned,
            "pending_reports": sum(1 for r in STATE.reports if r["status"] == "未处理"),
            "recent_bans": recent,
        })

    def _players(self, query: str) -> None:
        query = (query or "").strip()
        if not query:
            self._error(400, "缺少查询参数 q")
            return
        if looks_like_ip(query):
            names = sorted({
                b["target"] for b in STATE.bans
                if looks_like_ip(b["target"])
            })
            self._json(200, {"query": query, "type": "ip", "players": names})
            return
        associated = ["Alex"] if query.lower() != "alex" else ["Steve"]
        ips = [
            {"ip": "198.51.100.23", "first_seen": readable(now_ms() - 30 * DAY_MS),
             "last_seen": readable(now_ms() - 2 * 60 * 60 * 1000)},
            {"ip": "203.0.113.42", "first_seen": readable(now_ms() - 12 * DAY_MS),
             "last_seen": readable(now_ms() - 5 * DAY_MS)},
        ]
        self._json(200, {
            "query": query,
            "type": "player",
            "associated_players": associated,
            "ips": ips,
        })

    def _appeals(self, status: str) -> None:
        status = (status or "").strip()
        items = [a for a in STATE.appeals if not status or a["status"] == status]
        pending = sum(1 for a in STATE.appeals if a["status"] == "待处理")
        self._json(200, {"appeals": items, "total": len(items), "pending": pending})

    def _history(self, player: str) -> None:
        player = (player or "").strip()
        if not player:
            self._error(400, "缺少查询参数 player")
            return
        key = player.lower()
        bans = [STATE.ban_json(b) for b in STATE.bans if b["target"].lower() == key]
        mutes = [STATE.mute_json(m) for m in STATE.mutes if m["target"].lower() == key]
        warnings = []
        for name, warns in STATE.warnings.items():
            if name.lower() != key:
                continue
            for warn in warns:
                warnings.append({
                    "staff": warn["staff"],
                    "reason": warn["reason"],
                    "warn_time": warn["time"],
                    "revoked": warn["revoked"],
                })
        self._json(200, {"target": player, "player": player,
                         "bans": bans, "mutes": mutes, "warnings": warnings})

    def _audit(self, query: dict[str, list[str]]) -> None:
        try:
            limit = max(1, min(int(query.get("limit", ["50"])[0]), 200))
        except ValueError:
            limit = 50
        player = (query.get("player", [""])[0] or "").strip().lower()
        logs = [
            entry for entry in STATE.audit
            if not player or player in entry["actor"].lower() or player in entry["target"].lower()
        ][:limit]
        self._json(200, {"logs": logs, "total": len(logs)})

    def _theme_get(self) -> None:
        theme = dict(STATE.theme)
        theme["all_buttons"] = ALL_BUTTONS
        self._json(200, theme)

    def _theme_file(self, filename: str) -> None:
        item = STATE.uploads.get(filename)
        if item is None:
            self._error(404, "文件不存在")
            return
        body, content_type = item
        self._bytes(200, body, content_type)

    # -------------------------------------------------------- POST API

    def _handle_api_post(self, path: str, query: dict[str, list[str]]) -> None:
        with STATE.lock:
            if path == "/api/login":
                self._login()
            elif path == "/api/logout":
                self._json(200, {"success": True, "message": "已退出登录"})
            elif path == "/api/ban":
                self._ban()
            elif path == "/api/unban":
                self._unban()
            elif path == "/api/mute":
                self._mute()
            elif path == "/api/unmute":
                self._unmute()
            elif path == "/api/warn":
                self._warn()
            elif path == "/api/kick":
                self._kick()
            elif path == "/api/report/action":
                self._report_action()
            elif path == "/api/appeals/handle":
                self._appeal_handle()
            elif path == "/api/reload":
                STATE.add_audit(self._actor(), "重载配置")
                self._json(200, {"success": True, "message": "Web 配置已热重载"})
            elif path == "/api/broadcast":
                STATE.add_audit(self._actor(), "广播封禁人数")
                self._json(200, {"success": True, "message": "已广播封禁人数"})
            elif path == "/api/theme":
                self._theme_post()
            elif path == "/api/theme/upload":
                self._theme_upload()
            elif path == "/api/public/appeal/precheck":
                self._appeal_precheck()
            elif path == "/api/public/appeal/submit":
                self._appeal_submit()
            else:
                self._error(404, f"未知接口: {path}")

    def _login(self) -> None:
        body = self._json_body()
        username = str(body.get("username") or "")
        password = str(body.get("password") or "")
        if not username or not password:
            self._error(400, "请输入用户名和密码")
            return
        if username != self.user or password != self.password:
            self._error(401, "用户名或密码错误")
            return
        self._json(200, {"token": make_token(username), "username": username})

    def _ban(self) -> None:
        body = self._json_body()
        target = str(body.get("target") or "").strip()
        if not target:
            self._error(400, "缺少 target")
            return
        duration = parse_duration(str(body.get("duration") or "7d"))
        reason = str(body.get("reason") or "管理员操作")
        end = PERMANENT if duration >= PERMANENT else now_ms() + duration
        actor = self._actor()
        if looks_like_ip(target):
            STATE.ip_bans.insert(0, {"ip": target, "staff": actor, "reason": reason,
                                     "start": now_ms(), "end": end})
            STATE.add_audit(actor, "封禁IP", target, reason)
        else:
            STATE.bans.insert(0, {"target": target, "staff": actor, "reason": reason,
                                  "start": now_ms(), "end": end, "auto": False})
            STATE.add_audit(actor, "封禁玩家", target, reason)
        self._json(200, {"success": True,
                         "message": f"{target} 已被封禁,时长: {format_duration(duration)}"})

    def _unban(self) -> None:
        body = self._json_body()
        target = str(body.get("target") or "").strip()
        if not target:
            self._error(400, "缺少 target")
            return
        before = len(STATE.bans) + len(STATE.ip_bans)
        STATE.bans = [b for b in STATE.bans if b["target"].lower() != target.lower()]
        STATE.ip_bans = [b for b in STATE.ip_bans if b["ip"].lower() != target.lower()]
        if len(STATE.bans) + len(STATE.ip_bans) == before:
            self._error(404, "未找到封禁记录")
            return
        STATE.add_audit(self._actor(), "解封", target, "")
        self._json(200, {"success": True, "message": f"{target} 已被解封"})

    def _mute(self) -> None:
        body = self._json_body()
        target = str(body.get("target") or "").strip()
        if not target:
            self._error(400, "缺少 target")
            return
        duration = parse_duration(str(body.get("duration") or "7d"))
        reason = str(body.get("reason") or "管理员操作")
        end = PERMANENT if duration >= PERMANENT else now_ms() + duration
        STATE.mutes.insert(0, {"target": target, "staff": self._actor(), "reason": reason,
                               "start": now_ms(), "end": end})
        STATE.add_audit(self._actor(), "禁言玩家", target, reason)
        self._json(200, {"success": True,
                         "message": f"{target} 已被禁言,时长: {format_duration(duration)}"})

    def _unmute(self) -> None:
        body = self._json_body()
        target = str(body.get("target") or "").strip()
        if not target:
            self._error(400, "缺少 target")
            return
        before = len(STATE.mutes)
        STATE.mutes = [m for m in STATE.mutes if m["target"].lower() != target.lower()]
        if len(STATE.mutes) == before:
            self._error(404, "未找到禁言记录")
            return
        STATE.add_audit(self._actor(), "解除禁言", target, "")
        self._json(200, {"success": True, "message": f"{target} 已被解除禁言"})

    def _warn(self) -> None:
        body = self._json_body()
        target = str(body.get("target") or "").strip()
        if not target:
            self._error(400, "缺少 target")
            return
        reason = str(body.get("reason") or "管理员操作")
        STATE.warnings.setdefault(target, []).append({
            "staff": self._actor(), "reason": reason, "time": now_ms(), "revoked": False,
        })
        STATE.add_audit(self._actor(), "警告玩家", target, reason)
        self._json(200, {"success": True, "message": f"{target} 已被警告"})

    def _kick(self) -> None:
        body = self._json_body()
        target = str(body.get("target") or "").strip()
        if not target:
            self._error(400, "缺少 target")
            return
        before = len(STATE.online)
        STATE.online = [p for p in STATE.online if p["name"].lower() != target.lower()]
        if len(STATE.online) == before:
            self._error(404, "玩家不在线")
            return
        reason = str(body.get("reason") or "管理员操作")
        STATE.add_audit(self._actor(), "踢出玩家", target, reason)
        self._json(200, {"success": True, "message": f"{target} 已被踢出"})

    def _report_action(self) -> None:
        body = self._json_body()
        report_id = str(body.get("id") or "")
        action = str(body.get("action") or "")
        report = next((r for r in STATE.reports if r["id"] == report_id), None)
        if report is None:
            self._error(404, "举报不存在")
            return
        if report["status"] != "未处理":
            self._error(409, "举报已处理或已受理")
            return
        if action == "accept":
            report["status"] = "受理中"
            STATE.add_audit(self._actor(), "受理举报", report["target"], report["reason"])
        elif action == "close":
            report["status"] = "已关闭"
            STATE.add_audit(self._actor(), "关闭举报", report["target"], report["reason"])
        else:
            self._error(400, f"未知 action: {action}")
            return
        self._json(200, {"success": True, "message": f"举报 {report_id} 处理完成"})

    def _appeal_handle(self) -> None:
        body = self._json_body()
        appeal_id = str(body.get("id") or "")
        action = str(body.get("action") or "")
        response = str(body.get("response") or "")
        appeal = next((a for a in STATE.appeals if a["id"] == appeal_id), None)
        if appeal is None:
            self._error(404, "申诉不存在")
            return
        if appeal["status"] != "待处理":
            self._error(409, "申诉已处理")
            return
        if action == "approve":
            appeal["status"] = "已通过"
            STATE.bans = [b for b in STATE.bans if b["target"].lower() != appeal["target"].lower()]
            STATE.add_audit(self._actor(), "通过申诉", appeal["target"], response)
        elif action == "reject":
            appeal["status"] = "已驳回"
            STATE.add_audit(self._actor(), "驳回申诉", appeal["target"], response)
        else:
            self._error(400, f"未知 action: {action}")
            return
        appeal["handledBy"] = self._actor()
        appeal["handledAt"] = now_ms()
        appeal["response"] = response
        message = "已通过申诉并解除封禁" if action == "approve" else "已驳回申诉"
        self._json(200, {"success": True, "message": message})

    # -------------------------------------------------------- 主题

    def _theme_post(self) -> None:
        body = self._json_body()
        if body.get("reset_background"):
            STATE.theme.update({"background_type": "default", "background_url": "",
                                "background_file": "", "served_url": ""})
        if "background_url" in body:
            url = str(body.get("background_url") or "")
            STATE.theme.update({"background_type": "url", "background_url": url, "served_url": url})
        if "background_file" in body:
            filename = str(body.get("background_file") or "")
            STATE.theme.update({"background_type": "upload", "background_file": filename,
                                "served_url": f"/api/theme/file/{filename}"})
        if "hidden_buttons" in body and isinstance(body["hidden_buttons"], list):
            STATE.theme["hidden_buttons"] = [b for b in body["hidden_buttons"] if b in ALL_BUTTONS]
        self._json(200, {"success": True, "message": "主题已更新"})

    def _theme_upload(self) -> None:
        disposition = self.headers.get("Content-Disposition") or ""
        match = re.search(r'filename="?([^";]+)"?', disposition)
        original = match.group(1) if match else "background.png"
        data = self._body()
        if len(data) > 5 * 1024 * 1024:
            self._error(413, "文件超过 5MB 上限")
            return
        filename = f"{uuid.uuid4().hex[:8]}-{safe_filename(original)}"
        content_type = mimetypes.guess_type(filename)[0] or "application/octet-stream"
        STATE.uploads[filename] = (data, content_type)
        self._json(200, {"success": True, "filename": filename})

    # -------------------------------------------------------- 公开申诉

    def _appeal_precheck(self) -> None:
        body = self._json_body()
        player = str(body.get("player") or "").strip()
        if not player:
            self._json(200, {"ok": False, "message": "请输入玩家名"})
            return
        self._json(200, {
            "ok": True,
            "message": "",
            "player": player,
            "reason": "使用外挂/作弊",
            "ban_ends_at": "永久",
            "ticket": "T-MOCK0001",
        })

    def _appeal_submit(self) -> None:
        body = self._json_body()
        ticket = str(body.get("ticket") or "T-MOCK0001")
        contact = str(body.get("contact") or "")
        reason = str(body.get("reason") or "")
        if not reason.strip():
            self._json(200, {"ok": False, "message": "请填写申诉理由"})
            return
        appeal_id = f"A-{len(STATE.appeals) + 2001}"
        STATE.appeals.insert(0, {
            "id": appeal_id,
            "target": "MockPlayer",
            "contact": contact,
            "reason": reason,
            "status": "待处理",
            "createdAt": now_ms(),
            "handledBy": "",
            "handledAt": 0,
            "response": "",
            "ticket": ticket,
        })
        self._json(200, {"ok": True, "message": "申诉已提交", "ticket": ticket})

    def _appeal_status(self, ticket: str) -> None:
        appeal = next((a for a in STATE.appeals if a["ticket"] == ticket), None)
        if appeal is None:
            self._json(200, {"ok": False, "message": "没有查到该申诉，请确认查询码是否正确"})
            return
        self._json(200, {
            "ok": True,
            "status": appeal["status"],
            "createdAt": appeal["createdAt"],
            "handledAt": appeal["handledAt"],
            "response": appeal["response"],
        })


def main() -> None:
    parser = argparse.ArgumentParser(description="Lengbanlist 管理面板本地 Mock 后端")
    parser.add_argument("--host", default="127.0.0.1", help="监听地址（默认 127.0.0.1）")
    parser.add_argument("--port", type=int, default=8080, help="监听端口（默认 8080）")
    parser.add_argument("--user", default="admin", help="登录用户名（默认 admin）")
    parser.add_argument("--password", default="lban123", help="登录密码（默认 lban123）")
    parser.add_argument("--web-dir", default=None, help="前端目录，默认自动找 src/main/resources/web")
    args = parser.parse_args()

    web_dir = find_web_dir(args.web_dir)
    handler_cls = lambda *a, **kw: MockHandler(  # noqa: E731
        *a, web_dir=web_dir, user=args.user, password=args.password, **kw)
    server = ThreadingHTTPServer((args.host, args.port), handler_cls)
    print("Lengbanlist 管理面板 Mock 后端已启动")
    print(f"  地址: http://{args.host}:{args.port}/")
    print(f"  申诉页: http://{args.host}:{args.port}/appeal")
    print(f"  登录: {args.user} / {args.password}")
    print(f"  前端: {web_dir}")
    print("  数据仅保存在内存，重启恢复样例。Ctrl+C 退出。")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止。")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
