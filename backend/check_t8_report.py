# -*- coding: utf-8 -*-
"""T8 功能检查：模块十 统计报表（门诊/住院/收入/药品/患者 + 自定义报表 + 导出 + 快照）。"""
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
            payload = resp.read()
            if raw:
                return {"code": 200, "raw": payload, "headers": dict(resp.headers)}
            return json.loads(payload.decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw_body = e.read().decode("utf-8")
        try:
            return json.loads(raw_body)
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


def _dept(rows, name):
    for r in rows or []:
        if r.get("deptName") == name:
            return r
    return None


def _find(menus, path):
    for m in menus:
        if m.get("path") == path:
            return m
        hit = _find(m.get("children") or [], path)
        if hit:
            return hit
    return None


def _menu_ok(data):
    # 菜单树 VO 不带 status 字段：后端只查 status=1 的菜单，
    # 能出现在树里本身就说明已启用，因此这里只校验判权码
    menus = data if isinstance(data, list) else []
    for p in ["/report/outpatient", "/report/inpatient", "/report/income",
              "/report/drug", "/report/patient", "/report/custom"]:
        m = _find(menus, p)
        if not m or m.get("perms") != "report:query":
            return False
    return True


def _menu_desc(data):
    menus = data if isinstance(data, list) else []
    return ",".join((_find(menus, p) or {}).get("menuName", "缺失") for p in
                    ["/report/outpatient", "/report/income", "/report/patient", "/report/custom"])


# ---------- 清理上一轮残留 ----------
sql("""DELETE FROM biz_dispense_item WHERE dispense_id = 96081;
       DELETE FROM biz_dispense WHERE id = 96081;
       DELETE FROM biz_charge_item WHERE charge_id = 96081;
       DELETE FROM biz_charge WHERE id = 96081;
       DELETE FROM biz_medical_record WHERE id = 96081;
       DELETE FROM biz_registration WHERE id IN (96081, 96082, 96083);
       DELETE FROM biz_patient WHERE id IN (96081, 96082);
       DELETE FROM sys_report_config WHERE creator_id IS NOT NULL AND report_name LIKE 'T8%';""")

admin = login("admin")
fin = login("fin01")
doc = login("doc01")
cashier = login("cashier01")

# ---------- 造数据：报表是只读聚合，没有明细就永远测不出「算得对不对」 ----------
# 两名患者（一男 1980 年、一女 1995 年）→ 年龄分布 / 性别分布 / 建档趋势
# 三笔挂号（内科 2 笔、外科 1 笔，其中一笔已退号）→ 门诊人次/人数/退号不计入口径
sql("""
INSERT INTO biz_patient (id,patient_no,name,gender,birth_date,card_no,card_balance,
        create_time,update_time,deleted,version)
VALUES (96081,'PT81','报表测试甲',1,'1980-03-03','CT81',0,NOW(),NOW(),0,0),
       (96082,'PT82','报表测试乙',2,'1995-08-08','CT82',0,NOW(),NOW(),0,0);

INSERT INTO biz_registration (id,reg_no,patient_id,doctor_id,dept_id,schedule_id,
        reg_type,reg_fee,queue_no,status,reg_time,create_time,update_time,deleted,version)
VALUES (96081,'RG81',96081,3,1001,0,1,30.00,1,1,NOW(),NOW(),NOW(),0,0),
       (96082,'RG82',96081,3,1001,0,1,30.00,2,1,NOW(),NOW(),NOW(),0,0),
       (96083,'RG83',96082,3,1002,0,1,30.00,1,3,NOW(),NOW(),NOW(),0,0);

INSERT INTO biz_medical_record (id,patient_id,visit_id,visit_no,visit_type,diagnosis,
        icd_code,doctor_id,visit_time,status,create_time,update_time,deleted,version)
VALUES (96081,96081,96081,'VS81',1,'急性上呼吸道感染','J06.900',3,NOW(),2,NOW(),NOW(),0,0);

INSERT INTO biz_charge (id,charge_no,patient_id,visit_id,source_type,charge_type,
        total_amount,discount_amount,actual_amount,pay_type,pay_status,operator_id,
        charge_time,create_time,update_time,deleted,version)
VALUES (96081,'CH81',96081,96081,1,2,110.00,0.00,110.00,1,1,5,NOW(),NOW(),NOW(),0,0);

INSERT INTO biz_charge_item (id,charge_id,item_type,item_id,item_name,quantity,price,
        amount,refund_num,create_time,update_time,deleted,version)
VALUES (960811,96081,2,7001,'血常规',1,30.00,30.00,0,NOW(),NOW(),0,0),
       (960812,96081,3,8001,'阿莫西林胶囊',2,40.00,80.00,0,NOW(),NOW(),0,0);

INSERT INTO biz_dispense (id,dispense_no,presc_id,patient_id,warehouse_id,dispense_type,
        total_amount,operator_id,dispense_time,status,create_time,update_time,deleted,version)
VALUES (96081,'DSP81',NULL,96081,1005,1,80.00,6,NOW(),1,NOW(),NOW(),0,0);

INSERT INTO biz_dispense_item (id,dispense_id,drug_id,batch_no,quantity,price,amount,
        create_time,update_time,deleted,version)
VALUES (960811,96081,8001,'B2401',2,40.00,80.00,NOW(),NOW(),0,0);

-- 一张「已退」的发药单（status=2），数量故意给 99：
-- 若药品统计没排除已退记录，排行里的数量会变成 101，一眼就能看出来
INSERT INTO biz_dispense (id,dispense_no,presc_id,patient_id,warehouse_id,dispense_type,
        total_amount,operator_id,dispense_time,status,create_time,update_time,deleted,version)
VALUES (96082,'DSP82',NULL,96081,1005,1,9900.00,6,NOW(),2,NOW(),NOW(),0,0);

INSERT INTO biz_dispense_item (id,dispense_id,drug_id,batch_no,quantity,price,amount,
        create_time,update_time,deleted,version)
VALUES (960821,96082,8001,'B2401',99,100.00,9900.00,NOW(),NOW(),0,0);
""")

TODAY = time.strftime("%Y-%m-%d")
START = time.strftime("%Y-%m-%d", time.localtime(time.time() - 29 * 86400))

print("--- 门诊统计 ---")
check("门诊科室分布", req("GET", "/report/outpatient?startTime=%s&endTime=%s" % (START, TODAY), admin),
      extra=lambda d: (len(d) >= 1, "rows=%s 科室=%s" % (len(d), [r.get("deptName") for r in d])))
check("退号不计入门诊量（3 笔挂号内 1 笔已退，人次应为 2）",
      req("GET", "/report/outpatient?startTime=%s&endTime=%s" % (START, TODAY), admin),
      extra=lambda d: (sum(r.get("regCount", 0) for r in d) == 2,
                       "人次合计=%s" % sum(r.get("regCount", 0) for r in d)))
check("人次与人数分开统计（同一人两笔 → 人次 2、人数 1）",
      req("GET", "/report/outpatient?startTime=%s&endTime=%s" % (START, TODAY), admin),
      extra=lambda d: (_dept(d, "内科") is not None
                       and _dept(d, "内科").get("regCount") == 2
                       and _dept(d, "内科").get("patientCount") == 1,
                       "内科=%s" % _dept(d, "内科")))
check("门诊按日趋势", req("GET", "/report/outpatient/trend?startTime=%s&endTime=%s" % (START, TODAY), admin),
      extra=lambda d: (len(d) >= 1, "rows=%s" % len(d)))
check("门诊趋势每行带日期", req("GET", "/report/outpatient/trend?startTime=%s&endTime=%s" % (START, TODAY), admin),
      extra=lambda d: (all(r.get("statDate") for r in d) if d else False, "sample=%s" % (d[0].get("statDate") if d else None)))
check("开始日期晚于结束日期 40001",
      req("GET", "/report/outpatient?startTime=%s&endTime=%s" % (TODAY, START), admin), expect=40001)
check("区间超过 366 天 40001",
      req("GET", "/report/outpatient?startTime=2020-01-01&endTime=%s" % TODAY, admin), expect=40001)
check("挂号收费员无报表查询权 40301",
      req("GET", "/report/outpatient?startTime=%s&endTime=%s" % (START, TODAY), cashier), expect=40301)

print("--- 住院统计 ---")
check("住院概况",
      req("GET", "/report/inpatient?startTime=%s&endTime=%s" % (START, TODAY), admin),
      extra=lambda d: (d is not None and "bedUsageRate" in d,
                       "在院=%s 使用率=%s" % (d.get("inHospitalCount"), d.get("bedUsageRate"))))
check("床位使用率不超过 100",
      req("GET", "/report/inpatient?startTime=%s&endTime=%s" % (START, TODAY), admin),
      extra=lambda d: (0 <= float(d.get("bedUsageRate") or 0) <= 100, "rate=%s" % d.get("bedUsageRate")))

print("--- 收入统计 ---")
check("收入构成（项目类型）",
      req("GET", "/report/income?dimension=itemType&startTime=%s&endTime=%s" % (START, TODAY), admin),
      extra=lambda d: (isinstance(d, list), "rows=%s" % len(d)))
check("科室收入",
      req("GET", "/report/income?dimension=dept&startTime=%s&endTime=%s" % (START, TODAY), admin),
      extra=lambda d: (isinstance(d, list), "rows=%s" % len(d)))
check("医生绩效带接诊人次",
      req("GET", "/report/income?dimension=doctor&startTime=%s&endTime=%s" % (START, TODAY), admin),
      extra=lambda d: (all(r.get("visitCount") is not None for r in d) if d else True,
                       "rows=%s" % len(d)))
check("收入维度非法 40001",
      req("GET", "/report/income?dimension=dropTable&startTime=%s&endTime=%s" % (START, TODAY), admin),
      expect=40001)

print("--- 药品统计 ---")
check("药品销售排行",
      req("GET", "/report/drug?startTime=%s&endTime=%s" % (START, TODAY), admin),
      extra=lambda d: (len(d) >= 1, "rows=%s" % len(d)))
check("已退药品不计入消耗（2 盒，不是 101 盒）",
      req("GET", "/report/drug?startTime=%s&endTime=%s" % (START, TODAY), admin),
      extra=lambda d: (sum(r.get("quantity", 0) for r in d) == 2,
                       "数量合计=%s" % sum(r.get("quantity", 0) for r in d)))
check("topN 生效（<=5）",
      req("GET", "/report/drug?startTime=%s&endTime=%s&topN=5" % (START, TODAY), admin),
      extra=lambda d: (len(d) <= 5, "rows=%s" % len(d)))
check("排行名次从 1 递增",
      req("GET", "/report/drug?startTime=%s&endTime=%s" % (START, TODAY), admin),
      extra=lambda d: (len(d) >= 1 and all(r.get("rank") == i + 1 for i, r in enumerate(d)),
                       "first=%s" % (d[0].get("rank") if d else None)))
check("topN 超上限 40001",
      req("GET", "/report/drug?topN=999&startTime=%s&endTime=%s" % (START, TODAY), admin), expect=40001)

print("--- 患者分析 ---")
for dim, label in [("age", "年龄分布"), ("gender", "性别分布"), ("disease", "病种分布"), ("new", "建档趋势")]:
    check("患者分析 %s" % label,
          req("GET", "/report/patient?dimension=%s&startTime=%s&endTime=%s" % (dim, START, TODAY), admin),
          extra=lambda d: (isinstance(d, list) and len(d) >= 1, "rows=%s" % len(d)))
check("患者维度非法 40001",
      req("GET", "/report/patient?dimension=passwd&startTime=%s&endTime=%s" % (START, TODAY), admin),
      expect=40001)

print("--- 自定义报表配置 ---")
r = req("POST", "/report/config", admin, body={
    "reportName": "T8内科收入月报", "reportType": "income", "dimension": "dept", "chartType": "bar"})
check("新建自定义报表", r, extra=lambda d: (d is not None, "id=%s" % d))
cfg_id = r.get("data")

check("配置分页", req("GET", "/report/config/page?pageNum=1&pageSize=10", admin),
      extra=lambda d: (d.get("total", 0) >= 1, "total=%s" % d.get("total")))
check("配置详情带维度中文名", req("GET", "/report/config/%s" % cfg_id, admin),
      extra=lambda d: (d.get("dimensionName") == "科室", "dimension=%s name=%s" % (d.get("dimension"), d.get("dimensionName"))))
check("数据集与维度不匹配 40001",
      req("POST", "/report/config", admin, body={
          "reportName": "T8非法组合", "reportType": "drug", "dimension": "dept"}), expect=40001)
check("报表名称为空 40001",
      req("POST", "/report/config", admin, body={
          "reportName": "  ", "reportType": "income", "dimension": "dept"}), expect=40001)
check("展示形式非法 40001",
      req("POST", "/report/config", admin, body={
          "reportName": "T8图表", "reportType": "income", "dimension": "dept", "chartType": "chartjs"}),
      expect=40001)
check("财务无配置维护权 40301",
      req("POST", "/report/config", fin, body={
          "reportName": "T8越权", "reportType": "income", "dimension": "dept"}), expect=40301)

print("--- 预览与执行 ---")
check("预览（不落库）",
      req("POST", "/report/config/preview?reportType=outpatient&dimension=dept&startTime=%s&endTime=%s" % (START, TODAY), admin),
      extra=lambda d: (bool(d.get("columns")) and "rangeDesc" in d,
                       "cols=%s rows=%s" % (len(d.get("columns", [])), len(d.get("rows", [])))))
check("按配置执行",
      req("POST", "/report/config/%s/execute?startTime=%s&endTime=%s" % (cfg_id, START, TODAY), admin),
      extra=lambda d: (d.get("columns", [{}])[0].get("title") == "科室",
                       "firstCol=%s" % d.get("columns", [{}])[0].get("title")))
check("区间说明回显结束日",
      req("POST", "/report/config/%s/execute?startTime=%s&endTime=%s" % (cfg_id, START, TODAY), admin),
      extra=lambda d: (d.get("rangeDesc", "").endswith(TODAY), "range=%s" % d.get("rangeDesc")))

print("--- 导出 ---")
r = req("GET", "/report/income/export?format=excel&dimension=itemType&startTime=%s&endTime=%s" % (START, TODAY), admin, raw=True)
check("导出 Excel", r, extra=lambda d: (d.get("raw", b"")[:2] == b"PK", "bytes=%s" % len(d.get("raw", b""))))
cd = (r.get("headers", {}).get("Content-Disposition") or r.get("headers", {}).get("content-disposition") or "")
check("响应头带中文文件名", {"code": 200, "data": cd} if "filename*=UTF-8''" in cd else {"code": 0, "msg": cd},
      extra=lambda d: (True, d[:80]) if isinstance(d, str) else (False, str(d)))

r = req("GET", "/report/drug/export?format=pdf&dimension=drug&startTime=%s&endTime=%s" % (START, TODAY), admin, raw=True)
check("导出 PDF", r, extra=lambda d: (d.get("raw", b"")[:4] == b"%PDF", "bytes=%s" % len(d.get("raw", b""))))

check("导出格式非法 40001",
      req("GET", "/report/income/export?format=csv", admin), expect=40001)
check("医生无导出权 40301",
      req("GET", "/report/income/export?format=excel", doc), expect=40301)

print("--- 快照 ---")
YESTERDAY = time.strftime("%Y-%m-%d", time.localtime(time.time() - 86400))
check("生成快照", req("POST", "/report/income/snapshot?date=%s" % YESTERDAY, admin),
      extra=lambda d: (str(d).isdigit(), "id=%s" % d))
check("重复生成是覆盖（仍成功）", req("POST", "/report/income/snapshot?date=%s" % YESTERDAY, admin))
check("读取快照", req("GET", "/report/income/snapshot?date=%s" % YESTERDAY, admin),
      extra=lambda d: (d is not None and isinstance(d, str), "len=%s" % len(str(d or ""))))
check("不能为未来日期生成快照 40001",
      req("POST", "/report/income/snapshot?date=2099-01-01", admin), expect=40001)
check("报表类型非法 40001", req("POST", "/report/nosuch/snapshot", admin), expect=40001)
check("财务可生成快照（日结月结要对得上历史快照）",
      req("POST", "/report/drug/snapshot?date=%s" % YESTERDAY, fin))
check("医生无快照生成权 40301", req("POST", "/report/income/snapshot", doc), expect=40301)

print("--- 菜单与权限隔离 ---")
check("报表菜单已启用且判 report:query",
      req("GET", "/system/menu/tree", admin),
      extra=lambda d: (_menu_ok(d), _menu_desc(d)))
check("医生可查报表（仅有 report:query）",
      req("GET", "/report/outpatient?startTime=%s&endTime=%s" % (START, TODAY), doc))


# ---------- 汇总 ----------
passed = sum(1 for ok, _, _ in _results if ok)
total = len(_results)
print()
print("=" * 60)
print("==== T8 统计报表：%s/%s 通过 ====" % (passed, total))
print("=" * 60)
for ok, name, detail in _results:
    if not ok:
        print("[FAIL] %s -> %s" % (name, detail))
sys.exit(0 if passed == total else 1)
