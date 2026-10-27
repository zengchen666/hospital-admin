# -*- coding: utf-8 -*-
"""T2 功能检查：模块四 门诊管理（医生工作台 / 处方管理）。"""
import json
import subprocess
import sys
import time
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
    subprocess.run(["mysql", "-uroot", "-p123456", "--default-character-set=utf8mb4",
                    "hospital", "-e", s], capture_output=True)


# ---------- 准备：患者 + 排班 + 挂号 ----------
sql("""INSERT INTO biz_patient (id,patient_no,name,gender,birth_date,allergy_history,card_no,
        card_balance,create_time,update_time,deleted,version)
        VALUES (96021,'PT2','门诊测试患者',1,'1988-03-03','青霉素过敏','CT2',0,NOW(),NOW(),0,0)
        ON DUPLICATE KEY UPDATE name='门诊测试患者', allergy_history='青霉素过敏', deleted=0;""")

admin = login("admin")
cashier = login("cashier01")
doctor = login("doc01")
nurse = login("nurse01")

opts = req("GET", "/system/user/options?roleCode=doctor", admin)
doctor_id = opts["data"][0]["id"]

sched = {"doctorId": doctor_id, "deptId": "1001", "scheduleDate": "2026-11-05",
         "timePeriod": 1, "regType": 1, "totalNum": 5, "regFee": 20}
r = req("POST", "/registration/schedule", cashier, sched)
sched_id = r.get("data")
r = req("POST", "/registration", cashier, {"patientId": "96021", "scheduleId": sched_id})
visit_id = (r.get("data") or {}).get("id")

print("--- 医生工作台 ---")
r = req("GET", "/clinic/visit/page?pageNum=1&pageSize=10&status=0", doctor)
check("待就诊列表", r, extra=lambda d: (any(x["id"] == str(visit_id) for x in d["list"]),
                                       "待诊 %s 人" % d["total"]))

r = req("POST", "/clinic/visit/%s/receive" % visit_id, doctor)
check("接诊", r, extra=lambda d: (d["visitId"] == str(visit_id), "患者=%s 过敏史=%s"
                                 % (d["patientName"], d["allergyHistory"])))
check("过敏史随接诊返回（开方前必看）", {"code": 200, "msg": "ok"},
      extra=lambda _: (r["data"]["allergyHistory"] == "青霉素过敏", r["data"]["allergyHistory"]))

r = req("GET", "/clinic/visit/page?pageNum=1&pageSize=10&status=1", doctor)
check("接诊后进入「就诊中」", r, extra=lambda d: (any(x["id"] == str(visit_id) for x in d["list"]),
                                            "就诊中 %s 人" % d["total"]))

r = req("POST", "/clinic/visit/%s/receive" % visit_id, doctor)
check("重复接诊幂等（不报错，返回同一就诊）", r,
      extra=lambda d: (d["visitId"] == str(visit_id), "visitId=%s" % d["visitId"]))

print("--- 病历 ---")
check("存草稿", req("POST", "/clinic/record", doctor,
                   {"visitId": visit_id, "chiefComplaint": "咳嗽三天", "submit": False}))
r = req("GET", "/clinic/record?visitId=%s" % visit_id, doctor)
check("草稿可查回", r, extra=lambda d: (d and d["chiefComplaint"] == "咳嗽三天",
                                       "主诉=%s status=%s" % (d["chiefComplaint"], d["status"]) if d else "无"))

check("提交病历", req("POST", "/clinic/record", doctor,
                     {"visitId": visit_id, "chiefComplaint": "咳嗽三天", "presentIllness": "伴发热",
                      "diagnosis": "上呼吸道感染", "icdCode": "J06.9", "submit": True}))
r = req("GET", "/clinic/record?visitId=%s" % visit_id, doctor)
check("提交后状态为 1（已提交）", r, extra=lambda d: (d["status"] == 1, "status=%s" % d["status"]))

check("已提交病历不可再改 -> 40902",
      req("POST", "/clinic/record", doctor, {"visitId": visit_id, "chiefComplaint": "改一下", "submit": True}),
      40902)

# 护士有 clinic:receive，但受数据权限约束：只能写自己接诊患者的病历
check("护士写他人接诊的病历 -> 40302（数据权限）",
      req("POST", "/clinic/record", nurse, {"visitId": visit_id, "chiefComplaint": "x", "submit": False}),
      40302)
check("收费员无写病历权限 -> 40301",
      req("POST", "/clinic/record", cashier, {"visitId": visit_id, "chiefComplaint": "x", "submit": False}),
      40301)

print("--- 开处方 ---")
r = req("GET", "/clinic/drug/options", doctor)
check("药品选项", r, extra=lambda d: (len(d) > 0, "%s 个药品" % len(d)))
drug = r["data"][0]
check("药品选项不含进价", {"code": 200, "msg": "ok"},
      extra=lambda _: ("purchasePrice" not in drug, "字段=%s" % sorted(drug.keys())))

r = req("POST", "/clinic/prescription", doctor, {
    "visitId": visit_id, "prescType": 1, "diagnosis": "上呼吸道感染",
    "items": [{"drugId": drug["id"], "usage": "口服", "dosage": "1片",
               "frequency": "tid", "days": 3, "quantity": 2}],
})
check("开处方", r, extra=lambda d: (d is not None, "处方 id=%s" % d))
presc_id = r.get("data")

r = req("GET", "/clinic/prescription/%s" % presc_id, doctor)
check("处方金额由服务端计算", r,
      extra=lambda d: (d["totalAmount"] == round(drug["salePrice"] * 2, 2),
                       "单价%s × 2 = %s" % (drug["salePrice"], d["totalAmount"])))
check("处方初始为待审核", {"code": 200, "msg": "ok"},
      extra=lambda _: (r["data"]["status"] == 0, "status=%s" % r["data"]["status"]))

check("护士无开方权限 -> 40301",
      req("POST", "/clinic/prescription", nurse,
          {"visitId": visit_id, "prescType": 1,
           "items": [{"drugId": drug["id"], "quantity": 1}]}), 40301)

print("--- 处方审核 ---")
r = req("GET", "/clinic/prescription/page?pageNum=1&pageSize=10&status=0", doctor)
check("待审核处方列表", r, extra=lambda d: (d["total"] >= 1, "%s 条" % d["total"]))

# 不相容职务：开方与审核不能是同一人（后端 40902）
check("开方医生不能审核自己的处方 -> 40902",
      req("PUT", "/clinic/prescription/%s/audit" % presc_id, doctor), 40902)
check("由他人（admin）审核处方", req("PUT", "/clinic/prescription/%s/audit" % presc_id, admin))
r = req("GET", "/clinic/prescription/%s" % presc_id, doctor)
check("审核后状态为 1", {"code": 200, "msg": "ok"},
      extra=lambda _: (r["data"]["status"] == 1, "status=%s" % r["data"]["status"]))

check("重复审核 -> 40900", req("PUT", "/clinic/prescription/%s/audit" % presc_id, admin), 40900)
# 已审核但未收费：钱没收、药没发，此时作废是安全的（收了钱就必须走退费，见收费模块）
check("已审核未收费的处方可作废", req("DELETE", "/clinic/prescription/%s" % presc_id, doctor))
check("重复作废 -> 40902", req("DELETE", "/clinic/prescription/%s" % presc_id, doctor), 40902)

print("--- 清理 ---")
sql("""DELETE FROM biz_prescription_item WHERE presc_id IN (SELECT id FROM biz_prescription WHERE visit_id=%s);
       DELETE FROM biz_prescription WHERE visit_id=%s;
       DELETE FROM biz_medical_record WHERE visit_id=%s;
       DELETE FROM biz_registration WHERE patient_id=96021;
       DELETE FROM biz_schedule WHERE doctor_id=%s AND schedule_date='2026-11-05';
       DELETE FROM biz_patient WHERE id=96021;""" % (visit_id, visit_id, visit_id, doctor_id))

passed = sum(1 for ok, _, _ in _results if ok)
print()
print("========== T2 门诊管理：%d / %d ==========" % (passed, len(_results)))
for ok, name, detail in _results:
    if not ok:
        print("[FAIL] %s -> %s" % (name, detail))
sys.exit(0 if passed == len(_results) else 1)
