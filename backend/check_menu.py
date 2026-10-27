# -*- coding: utf-8 -*-
"""T0 功能检查：按角色核对菜单树（空目录已剪枝、判权码已对齐）。"""
import json
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080/api"
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def req(method, path, token=None, body=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with opener.open(r) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            return json.loads(raw)
        except Exception:
            return {"code": e.code, "msg": raw[:150]}


def login(u):
    for _ in range(3):
        r = req("POST", "/system/auth/login", body={"username": u, "password": "123456"})
        if r.get("code") == 200 and r.get("data"):
            return r["data"]["accessToken"]
        time.sleep(1.5)
    raise RuntimeError("登录失败 %s: %s" % (u, r))


ok = True
for u in ["admin", "director", "doc01", "nurse01", "cashier01", "pharm01", "fin01"]:
    tk = login(u)
    tree = req("GET", "/system/menu/tree", tk)["data"]
    parts = []
    for m in tree:
        ch = [c["menuName"] for c in (m.get("children") or [])]
        # menuType=1 是目录，没有子项就是空分组；menuType=2 是叶子菜单，无子项正常
        if m.get("menuType") == 1 and not ch:
            parts.append(m["menuName"] + "(空目录!)")
        else:
            parts.append(m["menuName"] + (("(" + ",".join(ch) + ")") if ch else ""))
    line = " | ".join(parts)
    if "空目录!" in line:
        ok = False
    print("%-9s %s" % (u, line))
    time.sleep(0.3)

print()
print("无空目录：" + ("PASS" if ok else "FAIL"))
