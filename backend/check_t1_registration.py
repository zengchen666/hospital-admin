# -*- coding: utf-8 -*-
"""T1 功能检查：模块三 挂号与预约（现场挂号 / 排班管理 / 候诊队列）。"""
import json
import sys
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080/api"
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
_results = []


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
            return {"code": e.code, "msg": "HTTP %s" % e.code}


def login(u):
    import time
    for _ in range(4):
        r = req("POST", "/system/auth/login", body={"username": u, "password": "123456"})
        if r.get("code") == 200 and r.get("data"):
            return r["data"]["accessToken"]
        time.sleep(1.5)
    raise RuntimeError("登录失败 %s" % u)


def check(name, resp, expect=200, extra=None):
    ok = resp.get("code") == expect
    detail = "code=%s msg=%s" % (resp.get("code"), resp.get("msg"))
    if ok and extra:
        try:
            ok2, d2 = extra(resp.get("data"))
            ok = ok2
            detail += " | " + d2
        except Exception as ex:
            ok, detail = False, detail + " | 断言异常: %s" % ex
    _results.append((ok, name, detail))
    print(("[OK]   " if ok else "[FAIL] ") + name + "  ->  " + detail)
    return resp


def sql(s):
    import subprocess
    subprocess.run(["mysql", "-uroot", "-p123456", "--default-character-set=utf8mb4",
                    "hospital", "-e", s], capture_output=True)


# ---------- 准备：造一个患者 ----------
sql("""INSERT INTO biz_patient (id,patient_no,name,gender,birth_date,card_no,card_balance,
        create_time,update_time,deleted,version)
        VALUES (96011,'PT1','挂号测试患者',1,'1990-01-01','CT1',0,NOW(),NOW(),0,0)
        ON DUPLICATE KEY UPDATE name='挂号测试患者', deleted=0;""")

admin = login("admin")
cashier = login("cashier01")
doc = login("doc01")

print("--- 排班管理 ---")
# 医生下拉（新增接口）
opts = req("GET", "/system/user/options?roleCode=doctor", admin)
check("医生下拉 /system/user/options?roleCode=doctor", opts,
      extra=lambda d: (len(d) > 0, "%s 名医生" % len(d)))
doctor_id = opts["data"][0]["id"]

check("无角色过滤时返回全部启用用户",
      req("GET", "/system/user/options", admin),
      extra=lambda d: (len(d) >= len(opts["data"]), "%s 人" % len(d)))

sched_body = {
    "doctorId": doctor_id, "deptId": "1001", "scheduleDate": "2026-11-02",
    "timePeriod": 1, "regType": 1, "totalNum": 5, "regFee": 15,
}
r = req("POST", "/registration/schedule", cashier, sched_body)
check("收费员新增排班", r, extra=lambda d: (d is not None, "id=%s" % d))
sched_id = r.get("data")

check("同一医生同时段同号别重复排班 -> 40904",
      req("POST", "/registration/schedule", cashier, sched_body), 40904)

check("医生无排班编辑权限 -> 40301",
      req("POST", "/registration/schedule", doc, {**sched_body, "scheduleDate": "2026-11-03"}), 40301)

r = req("GET", "/registration/schedule?pageNum=1&pageSize=20&deptId=1001", admin)
check("排班分页", r, extra=lambda d: (d["total"] > 0, "total=%s" % d["total"]))

r = req("GET", "/registration/schedule?onlyAvailable=true&pageSize=50", admin)
check("只看有号", r, extra=lambda d: (all(x["remainNum"] > 0 for x in d["list"]),
                                     "有号 %s 条" % len(d["list"])))

print("--- 现场挂号 ---")
r = req("POST", "/registration", cashier, {"patientId": "96011", "scheduleId": sched_id})
check("挂号", r, extra=lambda d: (d["regNo"] and d["queueNo"] >= 1,
                                 "单号=%s 排队号=%s 费=%s" % (d["regNo"], d["queueNo"], d["regFee"])))
reg_id = (r.get("data") or {}).get("id")

r = req("GET", "/registration/schedule?pageSize=50&onlyAvailable=false&deptId=1001", admin)
cur = [x for x in r["data"]["list"] if x["id"] == str(sched_id)]
check("挂号后号源扣减", {"code": 200, "msg": "ok"},
      extra=lambda _: (cur and cur[0]["remainNum"] == 4,
                       "剩余 %s" % (cur[0]["remainNum"] if cur else "?")))

print("--- 候诊队列 ---")
r = req("GET", "/registration/queue/1001", cashier)
check("候诊队列", r, extra=lambda d: (any(x["id"] == str(reg_id) for x in d), "%s 人候诊" % len(d)))

r = req("POST", "/registration/call/%s" % reg_id, cashier)
check("叫号", r)

r = req("GET", "/registration/queue/1001", cashier)
called = [x for x in r["data"] for x in [x] if x["id"] == str(reg_id)]
check("叫号后状态变为就诊中", {"code": 200, "msg": "ok"},
      extra=lambda _: (called and called[0]["status"] == 1,
                       "status=%s" % (called[0]["status"] if called else "?")))

check("重复叫号 -> 40902", req("POST", "/registration/call/%s" % reg_id, cashier), 40902)

check("同一患者同一时段重复挂号 -> 40900",
      req("POST", "/registration", cashier, {"patientId": "96011", "scheduleId": sched_id}), 40900)

check("就诊中的挂号不能退号 -> 40902",
      req("PUT", "/registration/%s/cancel" % reg_id, cashier), 40902)

print("--- 退号（另排一个时段）---")
# 另排下午时段，挂号后立刻退号，验证号源归还
sched2 = {**sched_body, "timePeriod": 2, "totalNum": 3}
r0 = req("POST", "/registration/schedule", cashier, sched2)
sched2_id = r0.get("data")
r2 = req("POST", "/registration", cashier, {"patientId": "96011", "scheduleId": sched2_id})
check("下午时段挂号", r2, extra=lambda d: (d is not None, "id=%s" % (d or {}).get("id")))
reg2 = (r2.get("data") or {}).get("id")

check("退号", req("PUT", "/registration/%s/cancel" % reg2, cashier))
r = req("GET", "/registration/schedule?pageSize=50&onlyAvailable=false&deptId=1001", admin)
cur = [x for x in r["data"]["list"] if x["id"] == str(sched2_id)]
check("退号后号源归还", {"code": 200, "msg": "ok"},
      extra=lambda _: (cur and cur[0]["remainNum"] == 3,
                       "剩余 %s（总3，挂1退1应回到3）" % (cur[0]["remainNum"] if cur else "?")))
check("重复退号 -> 40902", req("PUT", "/registration/%s/cancel" % reg2, cashier), 40902)

print("--- 停诊 ---")
check("停诊", req("PUT", "/registration/schedule/%s/stop" % sched2_id, cashier))
check("停诊后不能再挂号",
      req("POST", "/registration", cashier, {"patientId": "96011", "scheduleId": sched2_id}), 40902)
check("医生无停诊权限 -> 40301",
      req("PUT", "/registration/schedule/%s/stop" % sched2_id, doc), 40301)

print("--- 清理 ---")
sql("""DELETE FROM biz_registration WHERE patient_id=96011;
       DELETE FROM biz_schedule WHERE doctor_id=%s AND schedule_date='2026-11-02';
       DELETE FROM biz_patient WHERE id=96011;""" % doctor_id)

passed = sum(1 for ok, _, _ in _results if ok)
print()
print("========== T1 挂号与预约：%d / %d ==========" % (passed, len(_results)))
for ok, name, detail in _results:
    if not ok:
        print("[FAIL] %s -> %s" % (name, detail))
sys.exit(0 if passed == len(_results) else 1)
