# -*- coding: utf-8 -*-
"""T9 功能检查：模块一 系统管理剩余（角色/菜单）+ 模块十二 数据安全（日志/字典/参数/备份）。"""
import json
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://127.0.0.1:8080/api"
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
_results = []


def req(method, path, token=None, body=None, raw=False):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    r = urllib.request.Request(BASE + urllib.parse.quote(path, safe="/?&=:%"), data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with opener.open(r) as resp:
            if raw:
                # 导出接口返回二进制，不能当 JSON 解
                return {"code": 200, "raw": resp.read(), "headers": dict(resp.headers)}
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            return json.loads(raw)
        except Exception:
            return {"code": e.code, "msg": "HTTP %s" % e.code}


def login(u):
    for _ in range(10):
        r = req("POST", "/system/auth/login", body={"username": u, "password": "123456"})
        if r.get("code") == 200 and r.get("data"):
            return r["data"]["accessToken"]
        time.sleep(2)
    raise RuntimeError("登录失败 %s" % u)


def check(name, resp, expect=200, extra=None):
    ok = resp.get("code") == expect
    detail = "code=%s msg=%s" % (resp.get("code"), resp.get("msg"))
    if ok and extra:
        try:
            # raw 导出场景没有 data 字段，整个 resp 就是断言输入
            payload = resp.get("data") if "data" in resp else resp
            ok2, d2 = extra(payload)
            ok = ok2
            detail += " | " + d2
        except Exception as ex:
            ok, detail = False, detail + " | 断言异常: %s" % ex
    _results.append((ok, name, detail))
    print(("[OK]   " if ok else "[FAIL] ") + name + "  ->  " + detail)
    return resp


def sql(s):
    subprocess.run(["mysql", "-uroot", "-p123456", "--default-character-set=utf8mb4",
                    "hospital", "-e", s], capture_output=True)


def _backup_of(rows, backup_id):
    for b in rows or []:
        if b.get("id") == str(backup_id):
            return b
    return None

def _sorted_desc(rows):
    times = [r.get("createTime") for r in rows if r.get("createTime")]
    return times == sorted(times, reverse=True)


def _find(menus, path):
    for m in menus:
        if m.get("path") == path:
            return m
        hit = _find(m.get("children") or [], path)
        if hit:
            return hit
    return None


admin = login("admin")
director = login("director")
doctor = login("doc01")
cashier = login("cashier01")

# ===========================================================================
# 第 1 个：角色管理
# ===========================================================================
print("--- 角色管理 ---")
check("角色列表", req("GET", "/system/role/page", admin),
      extra=lambda d: (len(d) >= 7, "roles=%s" % len(d)))
check("角色列表带权限数与用户数",
      req("GET", "/system/role/page", admin),
      extra=lambda d: (all(r.get("userCount") is not None for r in d),
                       "admin用户数=%s" % next((r.get("userCount") for r in d if r.get("roleCode") == "admin"), None)))
check("角色详情（admin）", req("GET", "/system/role/2001", admin),
      extra=lambda d: (d.get("roleCode") == "admin" and d.get("permCount", 0) > 10,
                       "permCount=%s" % d.get("permCount")))
check("角色权限清单", req("GET", "/system/role/2007/permissions", admin),
      extra=lambda d: (len(d) >= 1 and all(p.get("permCode") for p in d), "perms=%s" % len(d)))
check("权限总表按模块分组", req("GET", "/system/role/permissions", admin),
      extra=lambda d: (len(d) >= 50 and all(p.get("module") for p in d), "total=%s" % len(d)))
check("权限矩阵标出已持有", req("GET", "/system/role/2005/permission-matrix", admin),
      extra=lambda d: (any(p.get("status") == 1 for p in d) and any(p.get("status") == 0 for p in d),
                       "持有=%s 未持有=%s" % (sum(1 for p in d if p.get("status") == 1),
                                          sum(1 for p in d if p.get("status") == 0))))
check("收费员不持有 report:query",
      req("GET", "/system/role/2005/permission-matrix", admin),
      extra=lambda d: (next((p.get("status") for p in d if p.get("permCode") == "report:query"), None) == 0,
                       "status=%s" % next((p.get("status") for p in d if p.get("permCode") == "report:query"), None)))
check("不存在的角色 40400", req("GET", "/system/role/9999", admin), expect=40400)
check("医生无角色查看权 40301", req("GET", "/system/role/page", doctor), expect=40301)
check("院长可查看角色（只读授权）", req("GET", "/system/role/page", director))

# ===========================================================================
# 第 2 个：菜单管理
# ===========================================================================
print("--- 菜单管理 ---")
check("全量菜单树", req("GET", "/system/menu/all", admin),
      extra=lambda d: (len(d) >= 10, "topLevel=%s" % len(d)))
check("全量树含系统管理目录",
      req("GET", "/system/menu/all", admin),
      extra=lambda d: (_find(d, "/system") is not None, "children=%s" % len((_find(d, "/system") or {}).get("children", []))))
check("报表 6 个子菜单都在全量树里",
      req("GET", "/system/menu/all", admin),
      extra=lambda d: (all(_find(d, p) for p in ["/report/outpatient", "/report/income",
                                                 "/report/drug", "/report/patient", "/report/custom"]),
                       "ok"))
check("动态路由树仍按权限裁剪（收费员看不到报表）",
      req("GET", "/system/menu/tree", cashier),
      extra=lambda d: (_find(d, "/report") is None, "顶层=%s" % [m.get("path") for m in d]))
check("医生无菜单查看权 40301", req("GET", "/system/menu/all", doctor), expect=40301)

# ===========================================================================
# 第 3 个：操作日志（模块十二 操作审计）
# ===========================================================================
print("--- 操作日志 ---")
check("操作日志分页", req("GET", "/system/oplog/page?pageNum=1&pageSize=10", admin),
      extra=lambda d: (d.get("total", 0) >= 1, "total=%s" % d.get("total")))
check("日志按时间倒序（出问题先看最近谁动了）",
      req("GET", "/system/oplog/page?pageNum=1&pageSize=10", admin),
      extra=lambda d: (_sorted_desc(d.get("list", [])), "首条=%s" % (d.get("list") or [{}])[0].get("createTime")))
check("按操作人过滤", req("GET", "/system/oplog/page?pageNum=1&pageSize=10&username=admin", admin),
      extra=lambda d: (all(r.get("username") == "admin" for r in d.get("list", [])),
                       "rows=%s" % len(d.get("list", []))))
check("按结果过滤（只看失败）",
      req("GET", "/system/oplog/page?pageNum=1&pageSize=10&status=0", admin),
      extra=lambda d: (all(r.get("status") == 0 for r in d.get("list", [])),
                       "rows=%s" % len(d.get("list", []))))
check("日志字段完整（含 IP / URI / 耗时）",
      req("GET", "/system/oplog/page?pageNum=1&pageSize=5", admin),
      extra=lambda d: (all(r.get("requestUri") for r in d.get("list", [])) if d.get("list") else False,
                       "sample=%s" % (d.get("list") or [{}])[0].get("requestUri")))
check("医生无日志查看权 40301", req("GET", "/system/oplog/page?pageNum=1&pageSize=10", doctor),
      expect=40301)
check("医生无日志导出权 40301", req("GET", "/system/oplog/export", doctor), expect=40301)
check("管理员可导出日志（xlsx 魔数 PK）",
      req("GET", "/system/oplog/export", admin, raw=True),
      extra=lambda d: (d.get("raw", b"")[:2] == b"PK", "bytes=%s" % len(d.get("raw", b""))))
check("操作日志菜单已启用", req("GET", "/system/menu/tree", admin),
      extra=lambda d: (_find(d, "/system/oplog") is not None,
                       "perms=%s" % (_find(d, "/system/oplog") or {}).get("perms")))

# ===========================================================================
# 第 4 个：数据字典
# ===========================================================================
print("--- 数据字典 ---")
# 清理上一轮残留（连带清字典项，避免孤儿数据污染下一次新建）
sql("DELETE FROM sys_dict_item WHERE dict_type = 't9_test';"
    "DELETE FROM sys_dict WHERE dict_type = 't9_test';")

check("字典列表", req("GET", "/system/dict/list", admin),
      extra=lambda d: (len(d) >= 5, "dicts=%s" % len(d)))
check("字典详情带字典项", req("GET", "/system/dict/list?dictType=gender", admin),
      extra=lambda d: (len(d) == 1 and len(d[0].get("items", [])) >= 2,
                       "items=%s" % len(d[0].get("items", []) if d else [])))
check("按类型取字典项（下拉框用）", req("GET", "/system/dict/items/pay_type", admin),
      extra=lambda d: (len(d) >= 2 and all(i.get("itemValue") for i in d), "items=%s" % len(d)))
check("字典项类型不存在时返回空", req("GET", "/system/dict/items/nosuchtype", admin),
      extra=lambda d: (len(d) == 0, "items=%s" % len(d)))

r = req("POST", "/system/dict", admin,
        body={"dictType": "t9_test", "dictName": "T9测试字典", "remark": "功能检查用", "status": 1})
check("新建字典", r, extra=lambda d: (d is not None, "id=%s" % d))
dict_id = r.get("data")

check("字典类型重复 40904", req("POST", "/system/dict", admin,
                            body={"dictType": "t9_test", "dictName": "重复", "status": 1}), expect=40904)
check("字典类型格式非法 40001（大写）", req("POST", "/system/dict", admin,
                                  body={"dictType": "T9_Bad", "dictName": "非法", "status": 1}), expect=40001)
check("字典名称为空 40001", req("POST", "/system/dict", admin,
                          body={"dictType": "t9_x", "dictName": "  ", "status": 1}), expect=40001)

r = req("POST", "/system/dict/%s/item" % dict_id, admin,
        body={"itemLabel": "选项一", "itemValue": "1", "sort": 1, "status": 1})
check("新增字典项", r, extra=lambda d: (d is not None, "id=%s" % d))
item_id = r.get("data")

check("字典项取值重复 40904",
      req("POST", "/system/dict/%s/item" % dict_id, admin,
          body={"itemLabel": "另一个一", "itemValue": "1", "status": 1}), expect=40904)
check("字典项取值为空 40001",
      req("POST", "/system/dict/%s/item" % dict_id, admin,
          body={"itemLabel": "空值", "itemValue": "  ", "status": 1}), expect=40001)
check("新增第二个字典项",
      req("POST", "/system/dict/%s/item" % dict_id, admin,
          body={"itemLabel": "选项二", "itemValue": "2", "sort": 2, "status": 1}))
check("修改字典项",
      req("PUT", "/system/dict/%s/item/%s" % (dict_id, item_id), admin,
          body={"itemLabel": "选项一（改）", "itemValue": "1", "sort": 9, "status": 1}))
check("改后取值不变、名称已变",
      req("GET", "/system/dict/list?dictType=t9_test", admin),
      extra=lambda d: (any(i.get("itemLabel") == "选项一（改）" and i.get("itemValue") == "1"
                           for i in (d[0].get("items") if d else [])),
                       "items=%s" % [i.get("itemLabel") for i in (d[0].get("items") if d else [])]))
check("删除字典项", req("DELETE", "/system/dict/%s/item/%s" % (dict_id, item_id), admin))
check("删除后只剩一项", req("GET", "/system/dict/list?dictType=t9_test", admin),
      extra=lambda d: (len(d[0].get("items", [])) == 1, "items=%s" % len(d[0].get("items", []))))
check("删除字典", req("DELETE", "/system/dict/%s" % dict_id, admin))
check("删字典连带清字典项（无孤儿）",
      req("GET", "/system/dict/items/t9_test", admin),
      extra=lambda d: (len(d) == 0, "items=%s" % len(d)))
check("医生无字典查看权 40301", req("GET", "/system/dict/list", doctor), expect=40301)
check("院长可看但不能改字典 40301",
      req("POST", "/system/dict", director, body={"dictType": "t9_x", "dictName": "越权", "status": 1}),
      expect=40301)

# ===========================================================================
# 第 5 个：系统参数
# ===========================================================================
print("--- 系统参数 ---")
sql("DELETE FROM sys_config WHERE config_key = 't9.test';")

check("参数列表", req("GET", "/system/config/list", admin),
      extra=lambda d: (len(d) >= 5, "configs=%s" % len(d)))
check("按类型过滤（系统内置）", req("GET", "/system/config/list?configType=1", admin),
      extra=lambda d: (len(d) >= 3 and all(c.get("configType") == 1 for c in d),
                       "内置=%s" % len(d)))
check("按键取值", req("GET", "/system/config/value/sys.login.fail.limit", admin),
      extra=lambda d: (d is not None, "value=%s" % d))
check("未配置的参数返回 null", req("GET", "/system/config/value/nosuch.key", admin),
      extra=lambda d: (d is None, "value=%s" % d))

r = req("POST", "/system/config", admin,
        body={"configKey": "t9.test", "configName": "T9测试参数", "configValue": "100",
              "configType": 2, "remark": "功能检查用"})
check("新增参数", r, extra=lambda d: (d is not None, "id=%s" % d))
cfg_id = r.get("data")

check("参数键重复 40904",
      req("POST", "/system/config", admin,
          body={"configKey": "t9.test", "configName": "重复", "configValue": "1", "configType": 2}),
      expect=40904)
check("参数键格式非法 40001（大写）",
      req("POST", "/system/config", admin,
          body={"configKey": "T9_Bad", "configName": "非法", "configValue": "1", "configType": 2}),
      expect=40001)
check("修改参数值",
      req("PUT", "/system/config/%s" % cfg_id, admin,
          body={"configKey": "t9.test", "configName": "T9测试参数", "configValue": "200", "configType": 2}))
check("改后立即生效", req("GET", "/system/config/value/t9.test", admin),
      extra=lambda d: (d == "200", "value=%s" % d))

builtin = req("GET", "/system/config/list?configType=1", admin).get("data")[0]
check("系统内置参数不允许删除 40902",
      req("DELETE", "/system/config/%s" % builtin.get("id"), admin), expect=40902)
check("删除业务参数", req("DELETE", "/system/config/%s" % cfg_id, admin))
check("删除后取值为 null", req("GET", "/system/config/value/t9.test", admin),
      extra=lambda d: (d is None, "value=%s" % d))
check("医生无参数查看权 40301", req("GET", "/system/config/list", doctor), expect=40301)
check("院长可看但不能改参数 40301",
      req("POST", "/system/config", director,
          body={"configKey": "t9.x", "configName": "越权", "configValue": "1", "configType": 2}),
      expect=40301)

# ===========================================================================
# 第 6 个：数据备份
# ===========================================================================
print("--- 数据备份 ---")
check("备份历史（初始）", req("GET", "/security/backup/list", admin),
      extra=lambda d: (isinstance(d, list), "records=%s" % len(d)))
r = req("POST", "/security/backup/run", admin)
check("执行备份（mysqldump）", r, extra=lambda d: (d is not None, "id=%s" % d))
backup_id = r.get("data")

check("备份记录已落库", req("GET", "/security/backup/list", admin),
      extra=lambda d: (any(b.get("id") == str(backup_id) for b in d), "records=%s" % len(d)))
check("备份状态为成功且文件非空",
      req("GET", "/security/backup/list", admin),
      extra=lambda d: (_backup_of(d, backup_id) is not None
                       and _backup_of(d, backup_id).get("status") == 1
                       and (_backup_of(d, backup_id).get("backupSize") or 0) > 0,
                       "size=%s" % (_backup_of(d, backup_id) or {}).get("sizeText")))
check("备份记录带可读大小", req("GET", "/security/backup/list", admin),
      extra=lambda d: (bool((_backup_of(d, backup_id) or {}).get("sizeText")),
                       "sizeText=%s" % (_backup_of(d, backup_id) or {}).get("sizeText")))
check("医生无备份权 40301", req("GET", "/security/backup/list", doctor), expect=40301)
check("医生不能执行备份 40301", req("POST", "/security/backup/run", doctor), expect=40301)
check("删除备份记录", req("DELETE", "/security/backup/%s" % backup_id, admin))
check("删除后不再出现", req("GET", "/security/backup/list", admin),
      extra=lambda d: (all(b.get("id") != str(backup_id) for b in d), "records=%s" % len(d)))

# ===========================================================================
# 第 7 个：安全配置
# ===========================================================================
print("--- 安全配置 ---")
check("安全配置项（系统内置参数）", req("GET", "/system/config/list?configType=1", admin),
      extra=lambda d: (len(d) >= 3, "items=%s" % [c.get("configKey") for c in d]))
check("登录失败上限可读", req("GET", "/system/config/value/sys.login.fail.limit", admin),
      extra=lambda d: (d is not None and str(d).isdigit(), "value=%s" % d))
check("会话超时可读", req("GET", "/system/config/value/sys.session.timeout", admin),
      extra=lambda d: (d is not None and str(d).isdigit(), "value=%s" % d))
check("安全配置菜单已启用", req("GET", "/system/menu/tree", admin),
      extra=lambda d: (_find(d, "/security/config") is not None,
                       "perms=%s" % (_find(d, "/security/config") or {}).get("perms")))
check("数据备份菜单已启用", req("GET", "/system/menu/tree", admin),
      extra=lambda d: (_find(d, "/security/backup") is not None,
                       "perms=%s" % (_find(d, "/security/backup") or {}).get("perms")))
check("院长看不到安全配置入口（只有 admin 有 security:config:edit）",
      req("GET", "/system/menu/tree", director),
      extra=lambda d: (_find(d, "/security/config") is None,
                       "顶层=%s" % [m.get("path") for m in d]))



# ---------- 汇总 ----------
passed = sum(1 for ok, _, _ in _results if ok)
total = len(_results)
print()
print("=" * 60)
print("==== T9 系统管理（角色/菜单）：%s/%s 通过 ====" % (passed, total))
print("=" * 60)
for ok, name, detail in _results:
    if not ok:
        print("[FAIL] %s -> %s" % (name, detail))
sys.exit(0 if passed == total else 1)
