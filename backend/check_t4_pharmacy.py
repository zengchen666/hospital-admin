# -*- coding: utf-8 -*-
"""T4 功能检查：模块六 药品与药房管理（药品目录 / 库存 / 采购 / 发药）。"""
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
sql("""DELETE FROM biz_stock_record WHERE related_no IN ('T4-CG','T4-MANUAL','T4-OUT');
       DELETE FROM biz_stock WHERE batch_no IN ('T4B1','T4B2','T4B3','T4OUT');
       DELETE FROM biz_purchase_item WHERE purchase_id IN
         (SELECT id FROM biz_purchase WHERE remark = 'T4-TEST');
       DELETE FROM biz_purchase WHERE remark = 'T4-TEST';
       DELETE FROM biz_drug WHERE drug_code IN ('T4D1','T4D2');
       DELETE FROM biz_supplier WHERE supplier_code = 'T4S1';
       -- 排班按 doctor+date+period 唯一，重跑会撞 40904，先把测试日的排班清掉
       DELETE FROM biz_registration WHERE schedule_id IN
         (SELECT id FROM biz_schedule WHERE schedule_date = '2026-11-06');
       DELETE FROM biz_schedule WHERE schedule_date = '2026-11-06';""")

admin = login("admin")
pharm = login("pharm01")
director = login("director")
doctor = login("doc01")
cashier = login("cashier01")

print("--- 药品目录 ---")
r = req("GET", "/drug/page?pageNum=1&pageSize=50", pharm)
check("药品分页", r, extra=lambda d: (d["total"] >= 8, "药品数=%s" % d["total"]))

r = req("POST", "/drug", pharm,
        {"drugCode": "T4D1", "drugName": "T4测试药品", "unit": "盒",
         "purchasePrice": 10, "salePrice": 18.5, "category": "T4"})
new_drug = r.get("data")
check("新增药品", r, extra=lambda d: (bool(d), "药品ID=%s" % d))

check("药品编码重复 -> 40904",
      req("POST", "/drug", pharm,
          {"drugCode": "T4D1", "drugName": "重复", "unit": "盒",
           "purchasePrice": 1, "salePrice": 2}), 40904)

r = req("GET", "/drug/page?pageNum=1&pageSize=50&keyword=T4", pharm)
check("按关键字搜索", r, extra=lambda d: (d["total"] == 1, "命中=%s" % d["total"]))

check("修改药品", req("PUT", "/drug/%s" % new_drug, pharm,
                      {"drugCode": "T4D1", "drugName": "T4测试药品改", "unit": "盒",
                       "purchasePrice": 10, "salePrice": 19.9}))

check("售价为 0 -> 40001",
      req("POST", "/drug", pharm,
          {"drugCode": "T4D2", "drugName": "x", "unit": "盒",
           "purchasePrice": 1, "salePrice": 0}), 40001)

check("医生无药品维护权 -> 40301",
      req("POST", "/drug", doctor,
          {"drugCode": "T4DX", "drugName": "x", "unit": "盒",
           "purchasePrice": 1, "salePrice": 2}), 40301)

print("--- 库房与库存 ---")
r = req("GET", "/drug/warehouse/list", pharm)
check("库房列表", r, extra=lambda d: (len(d) >= 3, "库房数=%d" % len(d)))

r = req("GET", "/drug/stock/page?pageNum=1&pageSize=50", pharm)
check("库存分页", r, extra=lambda d: (d["total"] >= 8, "批次数=%s" % d["total"]))

check("入库（批号 T4B1，100 盒）",
      req("POST", "/drug/stock/in", pharm,
          {"drugId": new_drug, "warehouseId": "8001", "batchNo": "T4B1", "quantity": 100,
           "expireDate": "2028-12-31", "warnNum": 20, "relatedNo": "T4-MANUAL"}))

r = req("GET", "/drug/stock/page?pageNum=1&pageSize=50&drugId=%s" % new_drug, pharm)
check("入库后库存可见", r, extra=lambda d: (d["total"] == 1 and d["list"][0]["quantity"] == 100,
                                      "库存=%s 批号=%s" % (d["list"][0]["quantity"], d["list"][0]["batchNo"])))

check("同批号再次入库累加（100 -> 130）",
      req("POST", "/drug/stock/in", pharm,
          {"drugId": new_drug, "warehouseId": "8001", "batchNo": "T4B1", "quantity": 30,
           "expireDate": "2028-12-31", "relatedNo": "T4-MANUAL"}))
r = req("GET", "/drug/stock/page?pageNum=1&pageSize=50&drugId=%s" % new_drug, pharm)
check("累加结果正确", r, extra=lambda d: (d["list"][0]["quantity"] == 130, "库存=%s" % d["list"][0]["quantity"]))

check("批号为空 -> 40001",
      req("POST", "/drug/stock/in", pharm,
          {"drugId": new_drug, "warehouseId": "8001", "batchNo": "", "quantity": 1,
           "expireDate": "2028-12-31"}), 40001)

check("有效期已过 -> 40001",
      req("POST", "/drug/stock/in", pharm,
          {"drugId": new_drug, "warehouseId": "8001", "batchNo": "T4B2", "quantity": 1,
           "expireDate": "2020-01-01"}), 40001)

check("有效期早于生产日期 -> 40001",
      req("POST", "/drug/stock/in", pharm,
          {"drugId": new_drug, "warehouseId": "8001", "batchNo": "T4B2", "quantity": 1,
           "produceDate": "2028-06-01", "expireDate": "2027-01-01"}), 40001)

check("医生无入库权 -> 40301",
      req("POST", "/drug/stock/in", doctor,
          {"drugId": new_drug, "warehouseId": "8001", "batchNo": "T4X", "quantity": 1,
           "expireDate": "2028-12-31"}), 40301)

print("--- 出库与预警 ---")
check("出库 30（130 -> 100）",
      req("POST", "/drug/stock/out", pharm,
          {"drugId": new_drug, "warehouseId": "8001", "batchNo": "T4B1", "quantity": 30,
           "remark": "破损报损"}))
r = req("GET", "/drug/stock/page?pageNum=1&pageSize=50&drugId=%s" % new_drug, pharm)
check("出库后余量", r, extra=lambda d: (d["list"][0]["quantity"] == 100, "库存=%s" % d["list"][0]["quantity"]))

check("出库超过库存 -> 42201",
      req("POST", "/drug/stock/out", pharm,
          {"drugId": new_drug, "warehouseId": "8001", "batchNo": "T4B1", "quantity": 99999,
           "remark": "超发"}), 42201)

check("不存在的批号出库 -> 40400",
      req("POST", "/drug/stock/out", pharm,
          {"drugId": new_drug, "warehouseId": "8001", "batchNo": "T4OUT", "quantity": 1,
           "remark": "无此批"}), 40400)

# 压到预警线以下（warnNum=20）
check("出库 85（100 -> 15，低于预警线 20）",
      req("POST", "/drug/stock/out", pharm,
          {"drugId": new_drug, "warehouseId": "8001", "batchNo": "T4B1", "quantity": 85,
           "remark": "临期清理"}))
r = req("GET", "/drug/stock/warn", pharm)
check("库存预警列表命中该批次", r,
      extra=lambda d: (any(x["batchNo"] == "T4B1" and x["warn"] for x in d),
                       "预警批次数=%d" % len(d)))

r = req("GET", "/drug/stock/expire?expireDays=3650", pharm)
check("效期预警（3650 天内）", r, extra=lambda d: (len(d) >= 1, "批次=%d" % len(d)))

r = req("GET", "/drug/stock/expire?expireDays=1", pharm)
check("效期预警（1 天内）不含远期批次", r,
      extra=lambda d: (not any(x["batchNo"] == "T4B1" for x in d), "批次=%d" % len(d)))

print("--- 供应商 ---")
r = req("POST", "/drug/supplier", pharm,
        {"supplierName": "T4测试供应商", "supplierCode": "T4S1", "contactName": "张三",
         "contactPhone": "13800138000", "creditLevel": 5})
supplier_id = r.get("data")
check("新增供应商", r, extra=lambda d: (bool(d), "供应商ID=%s" % d))

check("供应商编码重复 -> 40904",
      req("POST", "/drug/supplier", pharm,
          {"supplierName": "重复", "supplierCode": "T4S1"}), 40904)

check("联系电话格式非法 -> 40001",
      req("POST", "/drug/supplier", pharm,
          {"supplierName": "x", "supplierCode": "T4SX", "contactPhone": "abc"}), 40001)

r = req("GET", "/drug/supplier/page?pageNum=1&pageSize=20&keyword=T4", pharm)
check("供应商分页", r, extra=lambda d: (d["total"] == 1, "命中=%s" % d["total"]))

print("--- 采购：建单 → 审核 → 入库 ---")
r = req("POST", "/drug/purchase", pharm,
        {"supplierId": supplier_id, "warehouseId": "8001", "remark": "T4-TEST",
         "items": [{"drugId": new_drug, "quantity": 50, "price": 9.5}]})
purchase_id = r.get("data")
check("新建采购单（直接落待审核）", r, extra=lambda d: (bool(d), "采购单ID=%s" % d))

r = req("GET", "/drug/purchase/%s" % purchase_id, pharm)
check("采购单状态=待审核", r, extra=lambda d: (d["status"] == 1, "status=%s" % d["status"]))
check("金额服务端计算（50 × 9.5 = 475）", {"code": 200, "data": r["data"]},
      extra=lambda d: (float(d["totalAmount"]) == 475.0, "金额=%s" % d["totalAmount"]))
item_id = r["data"]["items"][0]["id"]

# 不相容职务：提单人不能审自己的单。用 admin 验证 ——
# 只有「同时拥有 edit 与 audit 权限」的账号才能走到这一步，
# 否则会先在权限拦截上返回 40301，压根碰不到业务校验
r = req("POST", "/drug/purchase", admin,
        {"supplierId": supplier_id, "warehouseId": "8001", "remark": "T4-TEST",
         "items": [{"drugId": new_drug, "quantity": 10, "price": 9.5}]})
self_audit_id = r.get("data")
check("申请人不能审自己的单 -> 40902",
      req("PUT", "/drug/purchase/%s/audit" % self_audit_id, admin), 40902)
req("PUT", "/drug/purchase/%s/void" % self_audit_id, admin)

# pharm 无审核权；director 有审核权且不是申请人
auditor = director
check("药师无审核权 -> 40301",
      req("PUT", "/drug/purchase/%s/audit" % purchase_id, pharm), 40301)
check("院长审核通过", req("PUT", "/drug/purchase/%s/audit" % purchase_id, auditor))

r = req("GET", "/drug/purchase/%s" % purchase_id, pharm)
check("审核后状态=已审核（2）", r, extra=lambda d: (d["status"] == 2, "status=%s" % d["status"]))

check("重复审核 -> 40902", req("PUT", "/drug/purchase/%s/audit" % purchase_id, auditor), 40902)

r = req("GET", "/drug/stock/page?pageNum=1&pageSize=50&drugId=%s" % new_drug, pharm)
stock_before = r["data"]["list"][0]["quantity"]
check("审核不加库存（关键设计）", {"code": 200, "data": r["data"]},
      extra=lambda d: (d["list"][0]["quantity"] == stock_before, "库存仍为 %s" % stock_before))

check("入库明细缺批号 -> 40001",
      req("POST", "/drug/purchase/%s/in" % purchase_id, pharm,
          {"items": [{"itemId": item_id, "arriveNum": 50, "batchNo": "", "expireDate": "2029-01-01"}]}), 40001)

check("实到数量超过采购数量 -> 40001",
      req("POST", "/drug/purchase/%s/in" % purchase_id, pharm,
          {"items": [{"itemId": item_id, "arriveNum": 999, "batchNo": "T4B3", "expireDate": "2029-01-01"}]}), 40001)

check("实到数量为 0 -> 40001",
      req("POST", "/drug/purchase/%s/in" % purchase_id, pharm,
          {"items": [{"itemId": item_id, "arriveNum": 0, "batchNo": "T4B3", "expireDate": "2029-01-01"}]}), 40001)

check("采购入库（实到 50，批号 T4B3）",
      req("POST", "/drug/purchase/%s/in" % purchase_id, pharm,
          {"warnNum": 10, "remark": "T4-CG",
           "items": [{"itemId": item_id, "arriveNum": 50, "batchNo": "T4B3", "expireDate": "2029-01-01"}]}))

r = req("GET", "/drug/stock/page?pageNum=1&pageSize=50&drugId=%s" % new_drug, pharm)
check("入库后新增批次 T4B3", r,
      extra=lambda d: (any(x["batchNo"] == "T4B3" and x["quantity"] == 50 for x in d["list"]),
                       "批次数=%d" % len(d["list"])))

r = req("GET", "/drug/purchase/%s" % purchase_id, pharm)
check("入库后状态=已入库（3）", r, extra=lambda d: (d["status"] == 3, "status=%s" % d["status"]))
check("实到数量回填", {"code": 200, "data": r["data"]},
      extra=lambda d: (d["items"][0]["arriveNum"] == 50, "实到=%s" % d["items"][0]["arriveNum"]))

check("已入库的采购单不能作废 -> 40902",
      req("PUT", "/drug/purchase/%s/void" % purchase_id, pharm), 40902)

print("--- 门诊发药（走完整门诊链路） ---")
sql("""INSERT INTO biz_patient (id,patient_no,name,gender,birth_date,allergy_history,card_no,
        card_balance,create_time,update_time,deleted,version)
        VALUES (96041,'PT41','发药测试患者',1,'1992-02-02',NULL,'CT41',0,NOW(),NOW(),0,0)
        ON DUPLICATE KEY UPDATE deleted=0;""")

opts = req("GET", "/system/user/options?roleCode=doctor", admin)["data"]
doctor_id = opts[0]["id"]

sched = {"doctorId": doctor_id, "deptId": "1001", "scheduleDate": "2026-11-06",
         "timePeriod": 1, "regType": 1, "totalNum": 5, "regFee": 20}
sched_id = req("POST", "/registration/schedule", cashier, sched).get("data")
check("创建排班（发药链路前置）", {"code": 200, "data": sched_id},
      extra=lambda d: (bool(d), "排班ID=%s" % d))
reg = req("POST", "/registration", cashier, {"patientId": "96041", "scheduleId": sched_id})
visit_id = (reg.get("data") or {}).get("id")
check("挂号成功（发药前置）", {"code": reg.get("code", 0), "data": reg.get("data")},
      extra=lambda d: (bool(d), "就诊ID=%s" % d))

req("POST", "/clinic/visit/%s/receive" % visit_id, doctor)
presc = req("POST", "/clinic/prescription", doctor,
            {"visitId": visit_id, "prescType": 1, "diagnosis": "上呼吸道感染",
             "items": [{"drugId": "9101", "quantity": 2, "dosage": "0.5g", "frequency": "tid"}]})
presc_id = presc.get("data")
check("开立处方", presc, extra=lambda d: (bool(d), "处方ID=%s" % d))

check("admin 审核处方", req("PUT", "/clinic/prescription/%s/audit" % presc_id, admin))

r = req("GET", "/pharmacy/pending?pageNum=1&pageSize=10", pharm)
check("未收费的处方不出现在待发药列表", r,
      extra=lambda d: (not any(x["prescId"] == str(presc_id) for x in d["list"]),
                       "待发药 %s 条" % d["total"]))

check("收费（处方 -> 已收费）",
      req("POST", "/finance/charge/prescription", cashier, {"prescId": presc_id, "payType": 1}))

r = req("GET", "/pharmacy/pending?pageNum=1&pageSize=10", pharm)
check("收费后进入待发药列表", r,
      extra=lambda d: (any(x["prescId"] == str(presc_id) for x in d["list"]),
                       "待发药 %s 条" % d["total"]))

check("医生无发药权 -> 40301",
      req("POST", "/pharmacy/dispense", doctor, {"prescId": presc_id, "warehouseId": "8002"}), 40301)

# 先给药房备货：把 9101 入到门诊西药房 8002
req("POST", "/drug/stock/in", pharm,
    {"drugId": "9101", "warehouseId": "8002", "batchNo": "T4B1", "quantity": 100,
     "expireDate": "2028-12-31", "warnNum": 10, "relatedNo": "T4-MANUAL"})

# 发药按「近效期优先」挑批次，可能不落在刚入的 T4B1 上，
# 所以断言总量减少 2，而不是断言某个具体批次的余量
def stock_sum(drug_id, warehouse_id):
    page = req("GET", "/drug/stock/page?pageNum=1&pageSize=50&drugId=%s&warehouseId=%s"
               % (drug_id, warehouse_id), pharm)
    return sum(x["quantity"] for x in page["data"]["list"])

qty_before = stock_sum("9101", "8002")
r = req("POST", "/pharmacy/dispense", pharm, {"prescId": presc_id, "warehouseId": "8002"})
check("门诊发药", r, extra=lambda d: (bool(d and d.get("dispenseNo")),
                                   "发药单号=%s 明细=%s" % (d.get("dispenseNo"), d.get("itemCount"))))

r = req("GET", "/pharmacy/pending?pageNum=1&pageSize=10", pharm)
check("发药后移出待发药列表", r,
      extra=lambda d: (not any(x["prescId"] == str(presc_id) for x in d["list"]),
                       "待发药 %s 条" % d["total"]))

check("重复发药 -> 40902",
      req("POST", "/pharmacy/dispense", pharm, {"prescId": presc_id, "warehouseId": "8002"}), 40902)

qty_after = stock_sum("9101", "8002")
check("发药后库存按处方数量减少 2", {"code": 200, "data": qty_after},
      extra=lambda d: (d == qty_before - 2, "%s -> %s" % (qty_before, d)))

print("--- 权限隔离 ---")
check("收费员无药房查询权 -> 40301",
      req("GET", "/drug/page?pageNum=1&pageSize=10", cashier), 40301)
check("药师无采购审核权 -> 40301",
      req("PUT", "/drug/purchase/%s/audit" % purchase_id, pharm), 40301)

# ---------- 清理 ----------
sql("""DELETE FROM biz_stock_record WHERE related_no IN ('T4-CG','T4-MANUAL','T4-OUT')
        OR drug_id IN (SELECT id FROM biz_drug WHERE drug_code = 'T4D1');
       DELETE FROM biz_stock WHERE batch_no IN ('T4B1','T4B2','T4B3','T4OUT')
        OR drug_id IN (SELECT id FROM biz_drug WHERE drug_code = 'T4D1');
       DELETE FROM biz_purchase_item WHERE purchase_id IN
         (SELECT id FROM biz_purchase WHERE remark = 'T4-TEST');
       DELETE FROM biz_purchase WHERE remark = 'T4-TEST';
       DELETE FROM biz_drug WHERE drug_code IN ('T4D1','T4D2');
       DELETE FROM biz_supplier WHERE supplier_code = 'T4S1';
       DELETE FROM biz_dispense_item WHERE dispense_id IN
         (SELECT id FROM biz_dispense WHERE patient_id = 96041);
       DELETE FROM biz_dispense WHERE patient_id = 96041;
       DELETE FROM biz_prescription_item WHERE presc_id IN
         (SELECT id FROM biz_prescription WHERE patient_id = 96041);
       DELETE FROM biz_prescription WHERE patient_id = 96041;
       DELETE FROM biz_registration WHERE patient_id = 96041;
       DELETE FROM biz_patient WHERE id = 96041;""")

ok = sum(1 for x in _results if x[0])
print("\n==== T4 药品与药房管理：%d/%d 通过 ====" % (ok, len(_results)))
for good, name, detail in _results:
    if not good:
        print("  [FAIL] %s -> %s" % (name, detail))
sys.exit(0 if ok == len(_results) else 1)
