# -*- coding: utf-8 -*-
"""T6 功能检查：模块八 收费与财务管理（价目表 / 发票 / 医保 / 日结月结 / 退费闭环）。"""
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


def money(v):
    return round(float(v), 2)


# ---------- 清理上一轮残留 ----------
TODAY = time.strftime("%Y-%m-%d")
THIS_MONTH = TODAY[:7]
sql("""DELETE FROM biz_insurance_settle WHERE charge_id IN
         (SELECT id FROM biz_charge WHERE patient_id = 96061);
       DELETE FROM biz_invoice WHERE charge_id IN
         (SELECT id FROM biz_charge WHERE patient_id = 96061);
       DELETE FROM biz_refund_item WHERE refund_id IN
         (SELECT id FROM biz_refund WHERE patient_id = 96061);
       DELETE FROM biz_refund WHERE patient_id = 96061;
       DELETE FROM biz_charge_item WHERE charge_id IN
         (SELECT id FROM biz_charge WHERE patient_id = 96061);
       DELETE FROM biz_charge WHERE patient_id = 96061;
       DELETE FROM biz_exam_item WHERE apply_id IN
         (SELECT id FROM biz_exam_apply WHERE patient_id = 96061);
       DELETE FROM biz_exam_report WHERE apply_id IN
         (SELECT id FROM biz_exam_apply WHERE patient_id = 96061);
       DELETE FROM biz_exam_apply WHERE patient_id = 96061;
       DELETE FROM biz_registration WHERE patient_id = 96061;
       DELETE FROM biz_schedule WHERE schedule_date = '2027-01-05';
       DELETE FROM biz_daily_settle WHERE settle_date IN ('2027-01-05', '%s');
       DELETE FROM biz_treat_item WHERE item_code IN ('T6T1','T6T2');
       DELETE FROM biz_patient WHERE id = 96061;""" % TODAY)

admin = login("admin")
cashier = login("cashier01")
fin = login("fin01")
director = login("director")
doctor = login("doc01")

# ---------- 前置：患者 + 排班 + 挂号 + 开检查 + 收费 ----------
sql("""INSERT INTO biz_patient (id,patient_no,name,gender,birth_date,allergy_history,card_no,
        card_balance,create_time,update_time,deleted,version)
        VALUES (96061,'PT61','财务测试患者',1,'1988-03-03',NULL,'CT61',0,NOW(),NOW(),0,0);""")

opts = req("GET", "/system/user/options?roleCode=doctor", admin)["data"]
doctor_id = opts[0]["id"]
sched_id = req("POST", "/registration/schedule", cashier,
               {"doctorId": doctor_id, "deptId": "1001", "scheduleDate": "2027-01-05",
                "timePeriod": 1, "regType": 1, "totalNum": 5, "regFee": 20}).get("data")
visit_id = (req("POST", "/registration", cashier,
                {"patientId": "96061", "scheduleId": sched_id}).get("data") or {}).get("id")

apply_id = req("POST", "/exam/apply", doctor,
               {"visitId": visit_id, "examType": 1, "examItem": "血常规",
                "items": [{"itemCode": "EXM_CBC", "itemName": "血常规", "sort": 0}]}).get("data")
check("前置：开检查单", {"code": 200, "data": apply_id}, extra=lambda d: (bool(d), "申请单=%s" % d))

r = req("POST", "/finance/charge/exam", cashier, {"applyId": apply_id, "payType": 1})
charge_id = (r.get("data") or {}).get("id")
check("前置：检查收费（血常规 30，现金）", r,
      extra=lambda d: (money(d["actualAmount"]) == 30.0, "实收=%s" % d["actualAmount"]))

charge_no = req("GET", "/finance/charge/%s" % charge_id, cashier)["data"]["chargeNo"]

print("--- 诊疗项目价目表 ---")
r = req("GET", "/finance/treat/page?pageNum=1&pageSize=100&onlyEffective=true", cashier)
check("价目表分页（只列生效价）", r, extra=lambda d: (d["total"] >= 14, "生效价 %s 条" % d["total"]))

r = req("POST", "/finance/treat", fin,
        {"itemCode": "T6T1", "itemName": "T6测试项目", "itemType": 1,
         "unit": "次", "price": 100, "insuranceType": 1, "insuranceRatio": 90})
treat_id = r.get("data")
check("财务岗新增诊疗项目", r, extra=lambda d: (bool(d), "项目ID=%s" % d))

check("项目编码重复 -> 40904",
      req("POST", "/finance/treat", fin,
          {"itemCode": "T6T1", "itemName": "重复", "itemType": 1, "unit": "次", "price": 1}), 40904)

check("收费员无调价权 -> 40301",
      req("POST", "/finance/treat", cashier,
          {"itemCode": "T6T2", "itemName": "x", "itemType": 1, "unit": "次", "price": 1}), 40301)

check("价格为负 -> 40001",
      req("POST", "/finance/treat", fin,
          {"itemCode": "T6T2", "itemName": "x", "itemType": 1, "unit": "次", "price": -1}), 40001)

r = req("POST", "/finance/treat/%s/price" % treat_id, fin,
        {"itemCode": "T6T1", "itemName": "T6测试项目", "itemType": 1, "unit": "次",
         "price": 120, "insuranceType": 1, "insuranceRatio": 90,
         "effectiveDate": "2027-01-06"})
new_treat_id = r.get("data")
check("调价新增一版（100 -> 120）", r, extra=lambda d: (bool(d), "新版本ID=%s" % d))

check("同一天不能有两版价 -> 40904",
      req("POST", "/finance/treat/%s/price" % new_treat_id, fin,
          {"itemCode": "T6T1", "itemName": "T6测试项目", "itemType": 1, "unit": "次",
           "price": 130, "effectiveDate": "2027-01-06"}), 40904)

r = req("GET", "/finance/treat/page?pageNum=1&pageSize=100&keyword=T6T1", cashier)
check("同一编码保留两版价格", r,
      extra=lambda d: (d["total"] == 2,
                       "版本数=%s 价格=%s" % (d["total"], [x["price"] for x in d["list"]])))

# 生效日期 2027-01-06 还在未来，当前生效价应仍是 100
r = req("GET", "/finance/treat/page?pageNum=1&pageSize=100&keyword=T6T1&onlyEffective=true", cashier)
check("未到生效日不切换价格（仍取 100）", r,
      extra=lambda d: (d["total"] == 1 and money(d["list"][0]["price"]) == 100.0,
                       "当前价=%s" % d["list"][0]["price"]))

print("--- 发票管理 ---")
r = req("POST", "/finance/invoice/print", cashier, {"chargeId": charge_id, "invoiceType": 1})
invoice_id = r.get("data")
check("开票（金额取实收 30）", r, extra=lambda d: (bool(d), "发票ID=%s" % d))

check("重复开票 -> 40902",
      req("POST", "/finance/invoice/print", cashier, {"chargeId": charge_id, "invoiceType": 1}), 40902)

r = req("GET", "/finance/invoice/%s" % invoice_id, cashier)
check("发票详情：金额等于收费单实收", r,
      extra=lambda d: (money(d["amount"]) == 30.0 and d["status"] == 0,
                       "金额=%s 状态=%s" % (d["amount"], d["status"])))

check("发票类型取值非法 -> 40001",
      req("POST", "/finance/invoice/print", cashier, {"chargeId": charge_id, "invoiceType": 9}), 40001)

check("收费员无发票作废权 -> 40301",
      req("PUT", "/finance/invoice/%s/void" % invoice_id, cashier), 40301)

check("财务岗作废发票", req("PUT", "/finance/invoice/%s/void" % invoice_id, fin))
check("重复作废 -> 40900", req("PUT", "/finance/invoice/%s/void" % invoice_id, fin), 40900)

# 作废后可以重开
r = req("POST", "/finance/invoice/print", cashier, {"chargeId": charge_id, "invoiceType": 2})
check("作废后可重新开票", r, extra=lambda d: (bool(d), "新发票ID=%s" % d))

req("PUT", "/finance/invoice/%s/void" % r.get("data"), fin)

print("--- 医保结算（Mock）---")
check("医保类型为空 -> 40001",
      req("POST", "/finance/insurance/settle", cashier, {"chargeId": charge_id, "insuranceType": ""}), 40001)

r = req("POST", "/finance/insurance/settle", cashier,
        {"chargeId": charge_id, "insuranceType": "职工"})
check("提交医保结算", r, extra=lambda d: (bool(d), "结算ID=%s" % d))

r = req("GET", "/finance/insurance/settle/%s" % charge_id, cashier)
check("结算详情：统筹 + 自付 = 总额", r,
      extra=lambda d: (money(d["insuranceAmount"]) + money(d["selfAmount"]) == money(d["totalAmount"]),
                       "总额=%s 统筹=%s 自付=%s"
                       % (d["totalAmount"], d["insuranceAmount"], d["selfAmount"])))

check("重复结算 -> 40902",
      req("POST", "/finance/insurance/settle", cashier,
          {"chargeId": charge_id, "insuranceType": "职工"}), 40902)

print("--- 日结 ---")
# 收费时间是「收费那一刻」，不是未来日期 —— 日结只能按当天查
# 当天可能还有别的测试流水，所以断言用「至少包含这一笔 30 元现金」
r = req("GET", "/finance/daily?date=%s" % TODAY, cashier)
check("日结汇总：现金 >= 30", r,
      extra=lambda d: (money(d["expectedCash"]) >= 30.0 and d["chargeCount"] >= 1,
                       "总额=%s 应有现金=%s 笔数=%s"
                       % (d["totalAmount"], d["expectedCash"], d["chargeCount"])))

check("日结对账口径：各支付方式之和 = 总额", r,
      extra=lambda d: (money(sum(float(x["amount"]) for x in d["payTypeAmounts"]))
                       == money(d["totalAmount"]),
                       "分项和=%s" % money(sum(float(x["amount"]) for x in d["payTypeAmounts"]))))

expected_cash = money(r["data"]["expectedCash"])

check("收费员无提交日结权 -> 40301",
      req("POST", "/finance/daily/submit", cashier,
          {"settleDate": TODAY, "actualCash": expected_cash}), 40301)

check("差异非 0 未填说明 -> 40001",
      req("POST", "/finance/daily/submit", fin,
          {"settleDate": TODAY, "actualCash": expected_cash - 10}), 40001)

check("提交日结（短款 10，已填说明）",
      req("POST", "/finance/daily/submit", fin,
          {"settleDate": TODAY, "actualCash": expected_cash - 10,
           "diffRemark": "患者少付 10 元，已补齐"}))

r = req("GET", "/finance/daily?date=%s" % TODAY, cashier)
check("日结回填实点与差异", r,
      extra=lambda d: (d["submitted"] and money(d["diffAmount"]) == -10.0,
                       "实点=%s 差异=%s 说明=%s" % (d["actualCash"], d["diffAmount"], d["diffRemark"])))

check("重复提交覆盖（实点补齐，差异 0）",
      req("POST", "/finance/daily/submit", fin, {"settleDate": TODAY, "actualCash": expected_cash}))

r = req("GET", "/finance/daily?date=%s" % TODAY, cashier)
check("覆盖后差异归零", r,
      extra=lambda d: (money(d["diffAmount"]) == 0.0, "差异=%s" % d["diffAmount"]))

print("--- 月结 ---")
r = req("GET", "/finance/monthly?month=%s" % THIS_MONTH, cashier)
check("月结按日汇总", r,
      extra=lambda d: (d["month"] == THIS_MONTH and money(d["totalAmount"]) >= 30.0
                       and len(d["days"]) >= 1,
                       "月份=%s 总额=%s 天数=%d" % (d["month"], d["totalAmount"], len(d["days"]))))

check("月份格式非法 -> 40001",
      req("GET", "/finance/monthly?month=202701", cashier), 40001)

print("--- 退费闭环 ---")
items = req("GET", "/finance/charge/%s" % charge_id, cashier)["data"]["items"]
item_id = items[0]["id"]

check("退费原因必填 -> 40001",
      req("POST", "/finance/refund/apply", cashier,
          {"chargeId": charge_id, "reason": "", "items": [{"chargeItemId": item_id, "quantity": 1}]}), 40001)

check("退数量超过原数量 -> 40001",
      req("POST", "/finance/refund/apply", cashier,
          {"chargeId": charge_id, "reason": "检查取消",
           "items": [{"chargeItemId": item_id, "quantity": 99}]}), 40001)

r = req("POST", "/finance/refund/apply", cashier,
        {"chargeId": charge_id, "reason": "患者取消检查",
         "items": [{"chargeItemId": item_id, "quantity": 1}]})
refund_id = r.get("data")
check("收费员申请退费", r, extra=lambda d: (bool(d), "退费单ID=%s" % d))

check("收费员无退费审核权 -> 40301",
      req("PUT", "/finance/refund/%s/approve" % refund_id, cashier), 40301)

# fin01 同时有 apply 与 audit，用它来验「自己申请的单自己不能审」
r = req("POST", "/finance/refund/apply", fin,
        {"chargeId": charge_id, "reason": "财务自查",
         "items": [{"chargeItemId": item_id, "quantity": 1}]})
self_refund_id = r.get("data")
check("财务岗申请第二笔退费（自检用）", r, extra=lambda d: (bool(d), "退费单ID=%s" % d))

check("申请人不能审自己的单 -> 40301",
      req("PUT", "/finance/refund/%s/approve" % self_refund_id, fin), 40301)

# 同一账号审「别人申请的单」可以通过，证明上一条不是权限问题
check("财务岗审核通过（非本人申请的单）",
      req("PUT", "/finance/refund/%s/approve" % refund_id, fin))
check("重复审核 -> 40902", req("PUT", "/finance/refund/%s/approve" % refund_id, fin), 40902)

check("财务岗执行退款", req("PUT", "/finance/refund/%s/complete" % refund_id, fin))

r = req("GET", "/finance/refund/%s" % refund_id, cashier)
check("退费完成状态=已退款（2）", r,
      extra=lambda d: (d["status"] == 2 and money(d["refundAmount"]) == 30.0,
                       "状态=%s 金额=%s 审核人=%s" % (d["status"], d["refundAmount"], d["auditorName"])))

check("退完再退 -> 40001",
      req("POST", "/finance/refund/apply", cashier,
          {"chargeId": charge_id, "reason": "再退一次",
           "items": [{"chargeItemId": item_id, "quantity": 1}]}), 40001)

print("--- 权限隔离 ---")
check("医生无价目表调价权 -> 40301",
      req("POST", "/finance/treat", doctor,
          {"itemCode": "T6X", "itemName": "x", "itemType": 1, "unit": "次", "price": 1}), 40301)
check("医生无开票权 -> 40301",
      req("POST", "/finance/invoice/print", doctor, {"chargeId": charge_id, "invoiceType": 1}), 40301)
check("技师无财务查询权 -> 40301",
      req("GET", "/finance/charge/page?pageNum=1&pageSize=10", login("tech01")), 40301)

# ---------- 清理 ----------
sql("""DELETE FROM biz_insurance_settle WHERE charge_id IN
         (SELECT id FROM biz_charge WHERE patient_id = 96061);
       DELETE FROM biz_invoice WHERE charge_id IN
         (SELECT id FROM biz_charge WHERE patient_id = 96061);
       DELETE FROM biz_refund_item WHERE refund_id IN
         (SELECT id FROM biz_refund WHERE patient_id = 96061);
       DELETE FROM biz_refund WHERE patient_id = 96061;
       DELETE FROM biz_charge_item WHERE charge_id IN
         (SELECT id FROM biz_charge WHERE patient_id = 96061);
       DELETE FROM biz_charge WHERE patient_id = 96061;
       DELETE FROM biz_exam_item WHERE apply_id IN
         (SELECT id FROM biz_exam_apply WHERE patient_id = 96061);
       DELETE FROM biz_exam_report WHERE apply_id IN
         (SELECT id FROM biz_exam_apply WHERE patient_id = 96061);
       DELETE FROM biz_exam_apply WHERE patient_id = 96061;
       DELETE FROM biz_registration WHERE patient_id = 96061;
       DELETE FROM biz_schedule WHERE schedule_date = '2027-01-05';
       DELETE FROM biz_daily_settle WHERE settle_date IN ('2027-01-05', '%s');
       DELETE FROM biz_treat_item WHERE item_code IN ('T6T1','T6T2');
       DELETE FROM biz_patient WHERE id = 96061;""" % TODAY)

ok = sum(1 for x in _results if x[0])
print("\n==== T6 收费与财务管理：%d/%d 通过 ====" % (ok, len(_results)))
for good, name, detail in _results:
    if not good:
        print("  [FAIL] %s -> %s" % (name, detail))
sys.exit(0 if ok == len(_results) else 1)
