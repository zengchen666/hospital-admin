# -*- coding: utf-8 -*-
"""T3 功能检查：模块五 住院管理（床位 / 入院 / 医嘱 / 护理记录 / 转科 / 出院）。"""
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
    for _ in range(6):
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


# ---------- 清理上一轮残留 ----------
sql("""DELETE FROM biz_nursing_record WHERE admission_id IN
         (SELECT id FROM biz_admission WHERE patient_id IN (96031,96032));
       DELETE FROM biz_medical_order WHERE admission_id IN
         (SELECT id FROM biz_admission WHERE patient_id IN (96031,96032));
       DELETE FROM biz_admission WHERE patient_id IN (96031,96032);
       DELETE FROM biz_patient WHERE id IN (96031,96032);
       UPDATE biz_bed SET status=0 WHERE id IN (8201,8202,8205);""")

# ---------- 准备：两名患者 ----------
sql("""INSERT INTO biz_patient (id,patient_no,name,gender,birth_date,allergy_history,card_no,
        card_balance,create_time,update_time,deleted,version)
        VALUES (96031,'PT31','住院测试甲',1,'1985-05-05','头孢过敏','CT31',0,NOW(),NOW(),0,0),
               (96032,'PT32','住院测试乙',2,'1990-09-09',NULL,'CT32',0,NOW(),NOW(),0,0)
        ON DUPLICATE KEY UPDATE deleted=0;""")

admin = login("admin")
doctor = login("doc01")
nurse = login("nurse01")
cashier = login("cashier01")

opts = req("GET", "/system/user/options?roleCode=doctor", admin)
doctor_id = opts["data"][0]["id"]

print("--- 病区与床位 ---")
r = req("GET", "/inpatient/ward/list", nurse)
check("病区列表", r, extra=lambda d: (len(d) == 2, "病区数=%d" % len(d)))

r = req("GET", "/inpatient/bed/page?pageNum=1&pageSize=50", nurse)
check("床位一览", r, extra=lambda d: (d["total"] == 8, "床位总数=%s" % d["total"]))

r = req("GET", "/inpatient/bed/available?wardId=8101", nurse)
check("内科一病区可分配床位", r, extra=lambda d: (len(d) == 4, "空闲=%d" % len(d)))

check("收费员无住院查询权 -> 40301",
      req("GET", "/inpatient/bed/page?pageNum=1&pageSize=10", cashier), 40301)

print("--- 入院登记 ---")
r = req("POST", "/inpatient/admission", doctor,
        {"patientId": "96031", "deptId": "1001", "doctorId": doctor_id,
         "diagnosis": "社区获得性肺炎", "deposit": 5000})
adm1 = r.get("data")
check("办理入院", r, extra=lambda d: (bool(d), "住院ID=%s" % d))

r = req("GET", "/inpatient/admission/page?pageNum=1&pageSize=10&status=0", nurse)
check("在院列表含新入院患者", r,
      extra=lambda d: (any(x["id"] == str(adm1) for x in d["list"]),
                       "在院 %s 人" % d["total"]))

check("重复入院同一患者 -> 40900",
      req("POST", "/inpatient/admission", doctor,
          {"patientId": "96031", "deptId": "1001", "doctorId": doctor_id}), 40900)

check("收费员办理入院 -> 40301",
      req("POST", "/inpatient/admission", cashier,
          {"patientId": "96032", "deptId": "1001", "doctorId": doctor_id}), 40301)

print("--- 分床与预交金 ---")
r = req("GET", "/inpatient/admission/%s" % adm1, nurse)
check("住院详情（未分床）", r, extra=lambda d: (d["bedNo"] is None, "床位=%s 押金=%s" % (d["bedNo"], d["deposit"])))

check("分床 A01", req("PUT", "/inpatient/admission/%s/bed?bedId=8201" % adm1, doctor))

r = req("GET", "/inpatient/bed/available?wardId=8101", nurse)
check("分床后空闲床位 -1", r, extra=lambda d: (len(d) == 3, "空闲=%d" % len(d)))

r = req("POST", "/inpatient/admission", doctor,
        {"patientId": "96032", "deptId": "1001", "doctorId": doctor_id, "diagnosis": "阑尾炎"})
adm2 = r.get("data")
check("第二名患者入院", r, extra=lambda d: (bool(d), "住院ID=%s" % d))

check("抢已被占用的床位 -> 40900",
      req("PUT", "/inpatient/admission/%s/bed?bedId=8201" % adm2, doctor), 40900)

# 乙占住 A02，这样后面「甲转科释放 A01」才能验证「只释放甲的床、乙的床不受影响」
check("患者乙分床 A02", req("PUT", "/inpatient/admission/%s/bed?bedId=8202" % adm2, doctor))

check("预交金充值 2000", req("POST", "/inpatient/admission/%s/deposit" % adm1, doctor, {"amount": 2000}))
r = req("GET", "/inpatient/admission/%s" % adm1, nurse)
check("押金累加正确（5000+2000）", r, extra=lambda d: (float(d["deposit"]) == 7000.0, "押金=%s" % d["deposit"]))

check("充值 0 元 -> 40001",
      req("POST", "/inpatient/admission/%s/deposit" % adm1, doctor, {"amount": 0}), 40001)

print("--- 医嘱 ---")
r = req("POST", "/inpatient/order", doctor,
        {"admissionId": str(adm1), "orderType": 1, "content": "头孢曲松钠 2g 静滴",
         "dosage": "2g", "frequency": "qd"})
order1 = r.get("data")
check("开立长期医嘱", r, extra=lambda d: (bool(d), "医嘱ID=%s" % d))

r = req("GET", "/inpatient/order/list?admissionId=%s" % adm1, nurse)
check("医嘱列表", r, extra=lambda d: (len(d) == 1, "医嘱数=%d" % len(d)))

check("护士执行医嘱", req("PUT", "/inpatient/order/%s/execute" % order1, nurse))
r = req("GET", "/inpatient/order/list?admissionId=%s" % adm1, nurse)
check("执行后状态=1", r, extra=lambda d: (d[0]["status"] == 1, "status=%s" % d[0]["status"]))

check("重复执行 -> 40902", req("PUT", "/inpatient/order/%s/execute" % order1, nurse), 40902)

check("停止医嘱", req("PUT", "/inpatient/order/%s/stop" % order1, doctor))
r = req("GET", "/inpatient/order/list?admissionId=%s" % adm1, nurse)
check("停止后状态=2", r, extra=lambda d: (d[0]["status"] == 2, "status=%s" % d[0]["status"]))

check("重复停止 -> 40902", req("PUT", "/inpatient/order/%s/stop" % order1, doctor), 40902)

check("护士无开医嘱权 -> 40301",
      req("POST", "/inpatient/order", nurse,
          {"admissionId": str(adm1), "orderType": 2, "content": "x"}), 40301)

check("收费员无执行医嘱权 -> 40301",
      req("PUT", "/inpatient/order/%s/execute" % order1, cashier), 40301)

print("--- 护理记录 ---")
check("新增体温单（含体温）",
      req("POST", "/inpatient/nursing", nurse,
          {"admissionId": str(adm1), "recordType": 2, "temperature": 38.6,
           "pulse": 96, "breath": 20, "bloodPressure": "120/80"}))

check("体温单缺体温 -> 40001",
      req("POST", "/inpatient/nursing", nurse,
          {"admissionId": str(adm1), "recordType": 2, "pulse": 96}), 40001)

check("护理记录缺内容 -> 40001",
      req("POST", "/inpatient/nursing", nurse,
          {"admissionId": str(adm1), "recordType": 1}), 40001)

check("体温超出合理范围(50℃) -> 40001",
      req("POST", "/inpatient/nursing", nurse,
          {"admissionId": str(adm1), "recordType": 2, "temperature": 50}), 40001)

check("血压格式非法 -> 40001",
      req("POST", "/inpatient/nursing", nurse,
          {"admissionId": str(adm1), "recordType": 3, "bloodPressure": "120-80"}), 40001)

check("新增护理记录（文本）",
      req("POST", "/inpatient/nursing", nurse,
          {"admissionId": str(adm1), "recordType": 1, "content": "协助翻身，皮肤完整"}))

r = req("GET", "/inpatient/nursing/page?admissionId=%s&recordType=2" % adm1, nurse)
check("体温单分页只出体征记录", r,
      extra=lambda d: (d["total"] == 1 and d["list"][0]["content"] is None,
                       "体温单 %s 条，体温=%s" % (d["total"], d["list"][0]["temperature"])))

r = req("GET", "/inpatient/nursing/page?admissionId=%s&recordType=1" % adm1, nurse)
check("护理记录分页（类型隔离生效）", r,
      extra=lambda d: (d["total"] == 1 and d["list"][0]["content"] == "协助翻身，皮肤完整",
                       "护理记录 %s 条" % d["total"]))

check("收费员无护理记录权 -> 40301",
      req("POST", "/inpatient/nursing", cashier,
          {"admissionId": str(adm1), "recordType": 1, "content": "x"}), 40301)

print("--- 转科 ---")
check("转科不带目标科室 -> 40001",
      req("POST", "/inpatient/admission/%s/transfer" % adm1, doctor, {}), 40001)

check("护士无转科权 -> 40301",
      req("POST", "/inpatient/admission/%s/transfer" % adm1, nurse, {"deptId": "1002"}), 40301)

check("转科到外科（1002）",
      req("POST", "/inpatient/admission/%s/transfer" % adm1, doctor,
          {"deptId": "1002", "doctorId": doctor_id}))

r = req("GET", "/inpatient/admission/%s" % adm1, nurse)
check("转科后床位已释放", r,
      extra=lambda d: (d["bedNo"] is None and d["deptId"] == 1002,
                       "床位=%s 科室=%s 状态=%s" % (d["bedNo"], d["deptId"], d["status"])))

r = req("GET", "/inpatient/bed/available?wardId=8101", nurse)
check("转科只释放本人床位（A01 空出、A02 仍由乙占用）", r,
      extra=lambda d: (len(d) == 3 and any(b["bedNo"] == "A01" for b in d)
                       and not any(b["bedNo"] == "A02" for b in d),
                       "内科空闲=%d（A01 已释放，A02 被乙占用）" % len(d)))

print("--- 出院 ---")
check("办理出院", req("PUT", "/inpatient/admission/%s/discharge" % adm1, doctor))

r = req("GET", "/inpatient/admission/page?pageNum=1&pageSize=10&status=1", nurse)
check("出院后进入出院列表", r,
      extra=lambda d: (any(x["id"] == str(adm1) for x in d["list"]), "出院 %s 人" % d["total"]))

check("重复出院 -> 40902",
      req("PUT", "/inpatient/admission/%s/discharge" % adm1, doctor), 40902)

check("出院后补录护理记录 -> 40902",
      req("POST", "/inpatient/nursing", nurse,
          {"admissionId": str(adm1), "recordType": 1, "content": "补录"}), 40902)

check("出院后开立医嘱 -> 40902",
      req("POST", "/inpatient/order", doctor,
          {"admissionId": str(adm1), "orderType": 2, "content": "补开"}), 40902)

check("出院后转科 -> 40902",
      req("POST", "/inpatient/admission/%s/transfer" % adm1, doctor, {"deptId": "1001"}), 40902)

# 患者乙仍在院，收尾清理
check("患者乙出院（清理）", req("PUT", "/inpatient/admission/%s/discharge" % adm2, doctor))

r = req("GET", "/inpatient/bed/available?wardId=8101", nurse)
check("全部出院后床位复位", r, extra=lambda d: (len(d) == 4, "内科空闲=%d" % len(d)))

# ---------- 清理 ----------
sql("""DELETE FROM biz_nursing_record WHERE admission_id IN
         (SELECT id FROM biz_admission WHERE patient_id IN (96031,96032));
       DELETE FROM biz_medical_order WHERE admission_id IN
         (SELECT id FROM biz_admission WHERE patient_id IN (96031,96032));
       DELETE FROM biz_admission WHERE patient_id IN (96031,96032);
       DELETE FROM biz_patient WHERE id IN (96031,96032);
       UPDATE biz_bed SET status=0 WHERE id IN (8201,8202,8205);""")

ok = sum(1 for x in _results if x[0])
print("\n==== T3 住院管理：%d/%d 通过 ====" % (ok, len(_results)))
for good, name, detail in _results:
    if not good:
        print("  [FAIL] %s -> %s" % (name, detail))
sys.exit(0 if ok == len(_results) else 1)
