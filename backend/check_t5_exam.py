# -*- coding: utf-8 -*-
"""T5 功能检查：模块七 检查检验管理（申请 / 采样 / 结果 / 报告 / 危急值闭环）。"""
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


def req(method, path, token=None, body=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    # 查询串里的中文要先百分号编码，否则 http.client 用 ascii 编码请求行会炸
    r = urllib.request.Request(BASE + urllib.parse.quote(path, safe="/?&=:%"), data=data, method=method)
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


def q1(s):
    out = subprocess.run(["mysql", "-uroot", "-p123456", "--default-character-set=utf8mb4",
                          "-N", "-B", "hospital", "-e", s], capture_output=True)
    return out.stdout.decode("utf-8").strip()


# ---------- 清理上一轮残留 ----------
# 表名先确认：biz_schedule（不是 biz_registration_schedule）
sql("""DELETE FROM biz_charge_item WHERE charge_id IN
         (SELECT id FROM biz_charge WHERE patient_id = 96051);
       DELETE FROM biz_charge WHERE patient_id = 96051;
       DELETE FROM biz_exam_item WHERE apply_id IN
         (SELECT id FROM biz_exam_apply WHERE patient_id = 96051);
       DELETE FROM biz_exam_report WHERE apply_id IN
         (SELECT id FROM biz_exam_apply WHERE patient_id = 96051);
       DELETE FROM biz_exam_apply WHERE patient_id = 96051;
       DELETE FROM biz_registration WHERE patient_id = 96051;
       DELETE FROM biz_schedule WHERE schedule_date = '2026-12-05';
       DELETE FROM biz_patient WHERE id = 96051;""")

admin = login("admin")
tech = login("tech01")
director = login("director")
doctor = login("doc01")
cashier = login("cashier01")

# ---------- 前置：患者 + 排班 + 挂号 ----------
sql("""INSERT INTO biz_patient (id,patient_no,name,gender,birth_date,allergy_history,card_no,
        card_balance,create_time,update_time,deleted,version)
        VALUES (96051,'PT51','检查测试患者',1,'1990-05-05',NULL,'CT51',0,NOW(),NOW(),0,0);""")

opts = req("GET", "/system/user/options?roleCode=doctor", admin)["data"]
doctor_id = opts[0]["id"]

sched_id = req("POST", "/registration/schedule", cashier,
               {"doctorId": doctor_id, "deptId": "1001", "scheduleDate": "2026-12-05",
                "timePeriod": 1, "regType": 1, "totalNum": 5, "regFee": 20}).get("data")
check("前置：创建排班", {"code": 200, "data": sched_id}, extra=lambda d: (bool(d), "排班ID=%s" % d))

reg = req("POST", "/registration", cashier, {"patientId": "96051", "scheduleId": sched_id})
visit_id = (reg.get("data") or {}).get("id")
check("前置：挂号", {"code": reg.get("code", 0), "data": visit_id},
      extra=lambda d: (bool(d), "就诊ID=%s" % d))

print("--- 开检查单 ---")
apply_body = {
    "visitId": visit_id,
    "examType": 1,
    "examItem": "血常规+肝功能",
    "bodyPart": "静脉血",
    "clinicalDiagnosis": "乏力待查",
    "items": [
        {"itemCode": "EXM_CBC", "itemName": "血常规", "sort": 0},
        {"itemCode": "EXM_LFT", "itemName": "肝功能", "sort": 1},
    ],
}
r = req("POST", "/exam/apply", doctor, apply_body)
apply_id = r.get("data")
check("医生开检查单", r, extra=lambda d: (bool(d), "申请单ID=%s" % d))

check("开单必须带明细 -> 40001",
      req("POST", "/exam/apply", doctor, {"visitId": visit_id, "examType": 1, "items": []}), 40001)

check("检查类型取值非法 -> 40001",
      req("POST", "/exam/apply", doctor,
          {"visitId": visit_id, "examType": 9,
           "items": [{"itemCode": "EXM_CBC", "itemName": "血常规"}]}), 40001)

check("收费员无开单权 -> 40301", req("POST", "/exam/apply", cashier, apply_body), 40301)

r = req("GET", "/exam/apply/%s" % apply_id, doctor)
check("开单后状态=待缴费（0）", r,
      extra=lambda d: (d["status"] == 0 and len(d["items"]) == 2,
                       "status=%s 明细=%d" % (d["status"], len(d["items"]))))

print("--- 收费闸门 ---")
check("医生无采样权 -> 40301", req("PUT", "/exam/apply/%s/start" % apply_id, doctor), 40301)
check("未缴费不能采样 -> 40902", req("PUT", "/exam/apply/%s/start" % apply_id, tech), 40902)
check("技师无收费权 -> 40301",
      req("POST", "/finance/charge/exam", tech, {"applyId": apply_id, "payType": 1}), 40301)

r = req("POST", "/finance/charge/exam", cashier, {"applyId": apply_id, "payType": 1})
check("检查收费（血常规30+肝功能80=110）", r,
      extra=lambda d: (str(d["actualAmount"]) in ("110.00", "110.0", "110"),
                       "应收=%s 实收=%s" % (d["totalAmount"], d["actualAmount"])))

check("重复收费 -> 40902",
      req("POST", "/finance/charge/exam", cashier, {"applyId": apply_id, "payType": 1}), 40902)

r = req("GET", "/exam/apply/%s" % apply_id, doctor)
check("收费后状态=待采样（1）", r, extra=lambda d: (d["status"] == 1, "status=%s" % d["status"]))

print("--- 采样与条码 ---")
r = req("POST", "/exam/sample", tech, {"applyId": apply_id})
barcode1 = r.get("data")
check("技师采样并分配条码", r,
      extra=lambda d: (bool(d) and d.startswith("SMP"), "条码=%s" % d))

check("重复采样 -> 40902", req("POST", "/exam/sample", tech, {"applyId": apply_id}), 40902)

r = req("POST", "/exam/sample/%s/barcode" % apply_id, tech)
check("条码首次查询", r, extra=lambda d: (d["barcodeNo"] == barcode1, "条码=%s" % d["barcodeNo"]))
r = req("POST", "/exam/sample/%s/barcode" % apply_id, doctor)
check("条码重打幂等（同一号）", r,
      extra=lambda d: (d["barcodeNo"] == barcode1, "条码=%s" % d["barcodeNo"]))

r = req("GET", "/exam/apply/%s" % apply_id, doctor)
check("采样后状态=检验中（2）", r,
      extra=lambda d: (d["status"] == 2 and d["barcodeNo"] == barcode1,
                       "status=%s 条码=%s" % (d["status"], d["barcodeNo"])))

print("--- 结果录入 ---")
r = req("GET", "/exam/item/%s" % apply_id, tech)
items = r.get("data") or []
check("查询明细骨架", r, extra=lambda d: (len(d) == 2, "明细=%d" % len(d)))
cbc = next(i for i in items if i["itemCode"] == "EXM_CBC")
lft = next(i for i in items if i["itemCode"] == "EXM_LFT")

check("批量保存结果",
      req("PUT", "/exam/item/%s" % apply_id, tech,
          [{"itemId": cbc["id"], "resultValue": "6.2", "refRange": "4-10",
            "unit": "10^9/L", "abnormalFlag": 0},
           {"itemId": lft["id"], "resultValue": "86", "refRange": "0-40",
            "unit": "U/L", "abnormalFlag": 1}]))

r = req("GET", "/exam/item/%s" % apply_id, tech)
check("结果已落库（含异常标记）", r,
      extra=lambda d: (any(x["itemCode"] == "EXM_LFT" and x["resultValue"] == "86"
                           and x["abnormalFlag"] == 1 for x in d),
                       "明细=%d" % len(d)))

check("明细不属于该检查单 -> 40001",
      req("PUT", "/exam/item/%s" % apply_id, tech,
          [{"itemId": 99999999, "resultValue": "1"}]), 40001)

check("医生无结果录入权 -> 40301",
      req("PUT", "/exam/item/%s" % apply_id, doctor, [{"itemId": cbc["id"], "resultValue": "1"}]), 40301)

print("--- 报告录入与发布 ---")
report_body = {
    "applyId": apply_id,
    "result": "白细胞 6.2×10^9/L，ALT 86U/L 轻度升高",
    "conclusion": "肝功能轻度异常，建议复查",
    "isCritical": 0,
    "items": [{"itemId": cbc["id"], "resultValue": "6.2", "refRange": "4-10",
               "unit": "10^9/L", "abnormalFlag": 0},
              {"itemId": lft["id"], "resultValue": "86", "refRange": "0-40",
               "unit": "U/L", "abnormalFlag": 1}],
}
r = req("POST", "/exam/report", tech, report_body)
report_id = r.get("data")
check("技师录入报告（草稿）", r, extra=lambda d: (bool(d), "报告ID=%s" % d))

r = req("GET", "/exam/apply/%s" % apply_id, doctor)
check("草稿不改变申请单状态（仍为 2 检验中）", r,
      extra=lambda d: (d["status"] == 2 and d["reportStatus"] == 0,
                       "申请单=%s 报告=%s" % (d["status"], d["reportStatus"])))

check("技师无发布权 -> 40301",
      req("PUT", "/exam/report/%s/publish" % report_id, tech), 40301)
check("医生无发布权 -> 40301",
      req("PUT", "/exam/report/%s/publish" % report_id, doctor), 40301)

check("院长（非录入者）发布报告", req("PUT", "/exam/report/%s/publish" % report_id, director))
# 状态机用的是条件更新，重复命中返回 40900（状态已变化）而不是 40902
check("重复发布 -> 40900", req("PUT", "/exam/report/%s/publish" % report_id, director), 40900)

r = req("GET", "/exam/report/%s" % report_id, doctor)
check("报告详情（含明细与审核人）", r,
      extra=lambda d: (d["status"] == 1 and len(d["items"]) == 2 and d["auditorName"] is not None,
                       "status=%s 明细=%d 审核=%s" % (d["status"], len(d["items"]), d["auditorName"])))

r = req("GET", "/exam/apply/%s" % apply_id, doctor)
check("发布后申请单推进到已出报告（3）", r,
      extra=lambda d: (d["status"] == 3, "status=%s" % d["status"]))

check("已发布后改结果 -> 40902",
      req("PUT", "/exam/item/%s" % apply_id, tech, [{"itemId": cbc["id"], "resultValue": "9"}]), 40902)
check("已发布后覆盖报告 -> 40902",
      req("POST", "/exam/report", tech, report_body), 40902)

print("--- 报告分页查询 ---")
r = req("GET", "/exam/report/page?pageNum=1&pageSize=10&patientName=检查测试", doctor)
check("按患者姓名查报告", r, extra=lambda d: (d["total"] >= 1, "命中=%s" % d["total"]))

r = req("GET", "/exam/report/page?pageNum=1&pageSize=10&status=1", doctor)
check("按发布状态过滤", r,
      extra=lambda d: (all(x["status"] == 1 for x in d["list"]), "已发布=%s" % d["total"]))

r = req("GET", "/exam/apply/page?pageNum=1&pageSize=10&status=3", doctor)
check("申请单按状态分页", r,
      extra=lambda d: (all(x["status"] == 3 for x in d["list"]), "已出报告=%s" % d["total"]))

print("--- 危急值闭环 ---")
r = req("POST", "/exam/apply", doctor,
        {"visitId": visit_id, "examType": 1, "examItem": "危急值项目",
         "clinicalDiagnosis": "高钾待排",
         "items": [{"itemCode": "EXM_RFT", "itemName": "肾功能", "sort": 0}]})
apply2 = r.get("data")
check("开第二张检查单（危急值）", r, extra=lambda d: (bool(d), "申请单ID=%s" % d))

req("POST", "/finance/charge/exam", cashier, {"applyId": apply2, "payType": 1})
req("POST", "/exam/sample", tech, {"applyId": apply2})

items2 = req("GET", "/exam/item/%s" % apply2, tech)["data"]
rft = items2[0]
r = req("POST", "/exam/report", tech,
        {"applyId": apply2, "result": "血钾 7.2mmol/L", "conclusion": "重度高钾血症",
         "isCritical": 1,
         "items": [{"itemId": rft["id"], "resultValue": "7.2", "refRange": "3.5-5.3",
                    "unit": "mmol/L", "abnormalFlag": 1}]})
report2 = r.get("data")
check("录入危急值报告", r, extra=lambda d: (bool(d), "报告ID=%s" % d))

r = req("GET", "/exam/report/critical?pageNum=1&pageSize=10", doctor)
check("未发布的危急值不进待处理列表", r,
      extra=lambda d: (not any(x["id"] == str(report2) for x in d["list"]),
                       "待处理=%s" % d["total"]))

check("院长发布危急值报告", req("PUT", "/exam/report/%s/publish" % report2, director))

msg_count = q1("SELECT COUNT(*) FROM sys_message WHERE biz_type='exam' AND biz_id=%s" % report2)
check("发布后自动推送危急值通知给申请医生",
      {"code": 200 if msg_count != "0" else 50001, "data": msg_count},
      extra=lambda d: (int(d) >= 1, "消息数=%s" % d))

r = req("GET", "/exam/report/critical?pageNum=1&pageSize=10", doctor)
check("已发布的危急值进入待处理列表", r,
      extra=lambda d: (any(x["id"] == str(report2) and x["handleStatus"] == 0 for x in d["list"]),
                       "待处理=%s" % d["total"]))

check("技师无危急值处理权 -> 40301",
      req("POST", "/exam/critical/handle", tech, {"reportId": report2, "remark": "已通知"}), 40301)
check("非危急值报告不能登记处理 -> 40001",
      req("POST", "/exam/critical/handle", doctor, {"reportId": report_id, "remark": "x"}), 40001)
check("处理措施为空 -> 40001",
      req("POST", "/exam/critical/handle", doctor, {"reportId": report2, "remark": ""}), 40001)

check("医生登记危急值处理",
      req("POST", "/exam/critical/handle", doctor,
          {"reportId": report2, "remark": "已电话通知患者立即返院，静推葡萄糖酸钙，复测血钾"}))

r = req("GET", "/exam/report/%s" % report2, doctor)
check("处理结果留档（人/时间/措施）", r,
      extra=lambda d: (d["handleStatus"] == 1 and d["handlerName"] and d["handleTime"],
                       "处理人=%s 时间=%s" % (d["handlerName"], d["handleTime"])))

check("重复处理 -> 40902",
      req("POST", "/exam/critical/handle", doctor, {"reportId": report2, "remark": "再处理一次"}), 40902)

r = req("GET", "/exam/report/page?pageNum=1&pageSize=10&pendingCritical=true", doctor)
check("处理完成后退出待处理列表", r,
      extra=lambda d: (not any(x["id"] == str(report2) for x in d["list"]),
                       "待处理=%s" % d["total"]))

print("--- 权限隔离 ---")
check("收费员无检查查询权 -> 40301",
      req("GET", "/exam/apply/page?pageNum=1&pageSize=10", cashier), 40301)
check("医生无结果录入权 -> 40301",
      req("PUT", "/exam/item/%s" % apply2, doctor, [{"itemId": rft["id"], "resultValue": "1"}]), 40301)
check("技师无开单权 -> 40301", req("POST", "/exam/apply", tech, apply_body), 40301)
check("院长无结果录入权 -> 40301",
      req("PUT", "/exam/item/%s" % apply2, director, [{"itemId": rft["id"], "resultValue": "1"}]), 40301)

# ---------- 清理 ----------
sql("""DELETE FROM sys_message WHERE biz_type='exam';
       DELETE FROM biz_charge_item WHERE charge_id IN
         (SELECT id FROM biz_charge WHERE patient_id = 96051);
       DELETE FROM biz_charge WHERE patient_id = 96051;
       DELETE FROM biz_exam_item WHERE apply_id IN
         (SELECT id FROM biz_exam_apply WHERE patient_id = 96051);
       DELETE FROM biz_exam_report WHERE apply_id IN
         (SELECT id FROM biz_exam_apply WHERE patient_id = 96051);
       DELETE FROM biz_exam_apply WHERE patient_id = 96051;
       DELETE FROM biz_registration WHERE patient_id = 96051;
       DELETE FROM biz_schedule WHERE schedule_date = '2026-12-05';
       DELETE FROM biz_patient WHERE id = 96051;""")

ok = sum(1 for x in _results if x[0])
print("\n==== T5 检查检验管理：%d/%d 通过 ====" % (ok, len(_results)))
for good, name, detail in _results:
    if not good:
        print("  [FAIL] %s -> %s" % (name, detail))
sys.exit(0 if ok == len(_results) else 1)
