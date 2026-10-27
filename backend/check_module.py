# -*- coding: utf-8 -*-
"""
通用模块功能检查器。

用法：
    python check_module.py <模块名>
脚本按 CHECKS 里的顺序跑，每条打印 [OK]/[FAIL]，最后汇总。
"""
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://127.0.0.1:8080/api"
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

_results = []


def req(method, path, token=None, body=None, params=None):
    url = BASE + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
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
            return {"code": e.code, "msg": "HTTP %s" % e.code}


def login(u):
    for _ in range(4):
        r = req("POST", "/system/auth/login", body={"username": u, "password": "123456"})
        if r.get("code") == 200 and r.get("data"):
            return r["data"]["accessToken"]
        time.sleep(1.5)
    raise RuntimeError("登录失败 %s: %s" % (u, r))


def check(name, resp, expect=200, extra=None):
    ok = resp.get("code") == expect
    detail = "code=%s msg=%s" % (resp.get("code"), resp.get("msg"))
    if ok and extra:
        try:
            ok2, d2 = extra(resp.get("data"))
            ok = ok2
            detail += " | " + d2
        except Exception as ex:
            ok = False
            detail += " | 断言异常: %s" % ex
    _results.append((ok, name, detail))
    print(("[OK]   " if ok else "[FAIL] ") + name + "  ->  " + detail)
    return ok


def sql(statement):
    import subprocess
    subprocess.run(["mysql", "-uroot", "-p123456", "--default-character-set=utf8mb4",
                    "hospital", "-e", statement], capture_output=True)


def summary(title):
    passed = sum(1 for ok, _, _ in _results if ok)
    print()
    print("========== %s：%d / %d ==========" % (title, passed, len(_results)))
    for ok, name, detail in _results:
        if not ok:
            print("[FAIL] %s -> %s" % (name, detail))
    return passed == len(_results)


def main(checks):
    title = checks["title"]
    ok = checks["run"]()
    all_ok = summary(title)
    sys.exit(0 if (ok and all_ok) else 1)
