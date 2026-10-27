# -*- coding: utf-8 -*-
"""T7 功能检查：模块九 公共卫生与健康管理（档案 / 接种 / 传染病 / 体检套餐）。"""
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


# ---------- 清理上一轮残留 ----------
sql("""DELETE FROM biz_checkup WHERE patient_id = 96071;
       DELETE FROM biz_infectious_report WHERE patient_id = 96071;
       DELETE FROM biz_vaccination WHERE patient_id = 96071;
       DELETE FROM biz_health_record WHERE patient_id = 96071;
       DELETE FROM biz_checkup_package WHERE package_name LIKE 'T7%';
       DELETE FROM biz_patient WHERE id = 96071;""")

admin = login("admin")
doctor = login("doc01")
nurse = login("nurse01")
ph = login("ph01")

# ---------- 前置：患者 ----------
sql("""INSERT INTO biz_patient (id,patient_no,name,gender,birth_date,allergy_history,card_no,
        card_balance,create_time,update_time,deleted,version)
        VALUES (96071,'PT71','公卫测试居民',1,'1975-06-06',NULL,'CT71',0,NOW(),NOW(),0,0);""")

print("--- 健康档案 ---")
check("患者为空 -> 40001",
      req("POST", "/health/record", nurse, {"chronicDisease": "高血压"}), 40001)

r = req("POST", "/health/record", nurse,
        {"patientId": 96071, "chronicDisease": "高血压,糖尿病", "smoking": 1,
         "drinking": 0, "exercise": "每周 1-2 次"})
record_id = r.get("data")
check("护士建档", r, extra=lambda d: (bool(d), "档案ID=%s" % d))

r = req("GET", "/health/record/%s" % record_id, nurse)
check("档案详情：慢病与生活习惯", r,
      extra=lambda d: (d["chronicDisease"] == "高血压,糖尿病" and d["smoking"] == 1
                       and d["patientName"] == "公卫测试居民",
                       "慢病=%s 吸烟=%s" % (d["chronicDisease"], d["smoking"])))

check("医生也有建档权（诊疗现场补录）",
      req("PUT", "/health/record/%s" % record_id, doctor,
          {"patientId": 96071, "chronicDisease": "高血压,糖尿病,冠心病",
           "smoking": 1, "drinking": 0, "exercise": "每周 3-5 次"}))

r = req("GET", "/health/record/page?pageNum=1&pageSize=10&chronicDisease=高血压", nurse)
check("按慢病筛选档案", r, extra=lambda d: (d["total"] >= 1, "命中=%s" % d["total"]))

r = req("GET", "/health/record/page?pageNum=1&pageSize=10&chronicDisease=恶性肿瘤", nurse)
check("不存在的慢病筛不到", r, extra=lambda d: (d["total"] == 0, "命中=%s" % d["total"]))

check("收费员无建档权 -> 40301",
      req("POST", "/health/record", login("cashier01"),
          {"patientId": 96071, "chronicDisease": "x"}), 40301)

print("--- 疫苗接种 ---")
check("疫苗名称为空 -> 40001",
      req("POST", "/health/vaccination", nurse,
          {"patientId": 96071, "vaccineName": "", "vaccinateDate": "2026-10-27"}), 40001)

check("接种日期为空 -> 40001",
      req("POST", "/health/vaccination", nurse,
          {"patientId": 96071, "vaccineName": "流感疫苗"}), 40001)

TODAY = time.strftime("%Y-%m-%d")

check("接种日期不能是未来 -> 40001",
      req("POST", "/health/vaccination", nurse,
          {"patientId": 96071, "vaccineName": "流感疫苗",
           "vaccinateDate": "2099-01-01"}), 40001)

r = req("POST", "/health/vaccination", nurse,
        {"patientId": 96071, "vaccineName": "流感疫苗", "doseNo": 1,
         "vaccinateDate": "2026-09-27", "nextDate": "2026-10-20"})
check("护士登记接种（第 1 剂）", r, extra=lambda d: (bool(d), "接种ID=%s" % d))

r = req("POST", "/health/vaccination", nurse,
        {"patientId": 96071, "vaccineName": "流感疫苗", "doseNo": 2,
         "vaccinateDate": "2026-10-20", "nextDate": TODAY})
check("登记第 2 剂（下次接种设为今天，用于提醒验证）", r, extra=lambda d: (bool(d), "接种ID=%s" % d))

r = req("GET", "/health/vaccination/remind?days=7", nurse)
check("接种提醒：今天到期在列", r,
      extra=lambda d: (any(x["vaccineName"] == "流感疫苗" for x in d), "提醒 %d 条" % len(d)))

r = req("GET", "/health/vaccination/page?pageNum=1&pageSize=10&vaccineName=流感疫苗", nurse)
check("接种记录分页", r, extra=lambda d: (d["total"] == 2, "记录数=%s" % d["total"]))

check("医生无接种登记权 -> 40301",
      req("POST", "/health/vaccination", doctor,
          {"patientId": 96071, "vaccineName": "乙肝疫苗", "vaccinateDate": "2026-10-27"}), 40301)

print("--- 传染病上报 ---")
check("病种为空 -> 40001",
      req("POST", "/health/infectious", doctor,
          {"patientId": 96071, "diseaseName": "", "reportDate": "2026-10-27"}), 40001)

r = req("POST", "/health/infectious", doctor,
        {"patientId": 96071, "diseaseName": "肺结核", "icdCode": "A15.9",
         "reportDate": TODAY})
infectious_id = r.get("data")
check("医生填卡（待上报）", r, extra=lambda d: (bool(d), "卡片ID=%s" % d))

r = req("GET", "/health/infectious/%s" % infectious_id, doctor)
check("填卡后状态=待上报（0）", r,
      extra=lambda d: (d["reportStatus"] == 0, "状态=%s 超时小时=%s"
                       % (d["reportStatus"], d.get("overdueHours"))))

r = req("GET", "/health/infectious/page?pageNum=1&pageSize=10&reportStatus=0", doctor)
check("待上报列表可见", r,
      extra=lambda d: (any(x["id"] == str(infectious_id) for x in d["list"]),
                       "待上报=%s" % d["total"]))

check("护士无传染病上报权 -> 40301",
      req("PUT", "/health/infectious/%s/report" % infectious_id, nurse), 40301)

check("医生上报疾控", req("PUT", "/health/infectious/%s/report" % infectious_id, doctor))
check("重复上报 -> 40902",
      req("PUT", "/health/infectious/%s/report" % infectious_id, doctor), 40902)

r = req("GET", "/health/infectious/%s" % infectious_id, doctor)
check("上报后状态=已上报（1）", r, extra=lambda d: (d["reportStatus"] == 1, "状态=%s" % d["reportStatus"]))

check("疾控反馈为空 -> 40001",
      req("PUT", "/health/infectious/%s/feedback?response=" % infectious_id, doctor), 40001)

check("登记疾控反馈",
      req("PUT", "/health/infectious/%s/feedback?response=%s"
          % (infectious_id, urllib.parse.quote("已纳入属地管理，密接筛查完成")), doctor))

r = req("GET", "/health/infectious/%s" % infectious_id, doctor)
check("反馈已留档（只追加不改病名）", r,
      extra=lambda d: (d["cdcResponse"] and d["diseaseName"] == "肺结核",
                       "反馈=%s 病种=%s" % (d["cdcResponse"], d["diseaseName"])))

print("--- 体检套餐 ---")
check("套餐名为空 -> 40001",
      req("POST", "/health/package", admin, {"packageName": "", "price": 500}), 40001)

check("套餐价为负 -> 40001",
      req("POST", "/health/package", admin, {"packageName": "T7套餐", "price": -1}), 40001)

r = req("POST", "/health/package", admin,
        {"packageName": "T7入职体检", "itemIds": "9010", "price": 299, "status": 1})
pkg_id = r.get("data")
check("新增体检套餐", r, extra=lambda d: (bool(d), "套餐ID=%s" % d))

r = req("GET", "/health/package/%s" % pkg_id, doctor)
check("套餐详情", r, extra=lambda d: (d["price"] == 299 and d["status"] == 1,
                                      "价=%s 状态=%s" % (d["price"], d["status"])))

check("停用套餐", req("PUT", "/health/package/%s/status?status=0" % pkg_id, admin))
r = req("GET", "/health/package/%s" % pkg_id, doctor)
check("停用后状态=0", r, extra=lambda d: (d["status"] == 0, "状态=%s" % d["status"]))

check("停用的套餐不出现在「仅启用」列表",
      req("GET", "/health/package/list?onlyEnabled=true", doctor),
      extra=lambda d: (not any(x["id"] == str(pkg_id) for x in d), "启用数=%d" % len(d)))

check("重新启用套餐", req("PUT", "/health/package/%s/status?status=1" % pkg_id, admin))

check("医生无套餐维护权 -> 40301",
      req("POST", "/health/package", doctor, {"packageName": "T7X", "price": 1}), 40301)

print("--- 体检登记与报告 ---")
r = req("POST", "/health/checkup", doctor,
        {"patientId": 96071, "packageId": pkg_id, "checkupDate": TODAY})
checkup_id = r.get("data")
check("医生登记体检（带套餐）", r, extra=lambda d: (bool(d), "体检ID=%s" % d))

r = req("GET", "/health/checkup/%s" % checkup_id, doctor)
check("登记后状态=待检（0）", r,
      extra=lambda d: (d["status"] == 0 and d["packageName"] == "T7入职体检",
                       "状态=%s 套餐=%s" % (d["status"], d["packageName"])))

check("体检日期为空 -> 40001",
      req("POST", "/health/checkup", doctor, {"patientId": 96071}), 40001)

check("完成体检（0 待检 -> 1 已检）",
      req("PUT", "/health/checkup/%s/complete" % checkup_id, doctor))

check("第二次「完成体检」-> 40902",
      req("PUT", "/health/checkup/%s/complete" % checkup_id, doctor), 40902)

r = req("GET", "/health/checkup/%s" % checkup_id, doctor)
check("完成体检后状态=已检待出报告（1）", r,
      extra=lambda d: (d["status"] == 1, "状态=%s" % d["status"]))

check("健康评估为空 -> 40001",
      req("PUT", "/health/checkup/%s/report" % checkup_id, doctor, {"summary": ""}), 40001)

check("出具体检报告",
      req("PUT", "/health/checkup/%s/report" % checkup_id, doctor,
          {"summary": "总体健康，血压偏高，建议复查", "abnormalItems": "血压偏高"}))

r = req("GET", "/health/checkup/%s" % checkup_id, doctor)
check("出报告后状态=已出报告（2）", r,
      extra=lambda d: (d["status"] == 2 and d["abnormalItems"] == "血压偏高",
                       "状态=%s 异常项=%s" % (d["status"], d["abnormalItems"])))

check("已出报告不能再出 -> 40902",
      req("PUT", "/health/checkup/%s/report" % checkup_id, doctor, {"summary": "再出一次"}), 40902)

r = req("GET", "/health/checkup/page?pageNum=1&pageSize=10&status=2", doctor)
check("体检按状态分页", r,
      extra=lambda d: (all(x["status"] == 2 for x in d["list"]), "已出报告=%s" % d["total"]))

print("--- 权限隔离 ---")
check("收费员无公卫查询权 -> 40301",
      req("GET", "/health/record/page?pageNum=1&pageSize=10", login("cashier01")), 40301)
check("公卫专员无体检登记权 -> 40301",
      req("POST", "/health/checkup", ph, {"patientId": 96071, "checkupDate": "2026-10-27"}), 40301)
check("公卫专员有接种登记权",
      req("POST", "/health/vaccination", ph,
          {"patientId": 96071, "vaccineName": "乙肝疫苗", "doseNo": 1,
           "vaccinateDate": "2026-10-27"}))
check("技师无公卫查询权 -> 40301",
      req("GET", "/health/record/page?pageNum=1&pageSize=10", login("tech01")), 40301)

# ---------- 清理 ----------
sql("""DELETE FROM biz_checkup WHERE patient_id = 96071;
       DELETE FROM biz_infectious_report WHERE patient_id = 96071;
       DELETE FROM biz_vaccination WHERE patient_id = 96071;
       DELETE FROM biz_health_record WHERE patient_id = 96071;
       DELETE FROM biz_checkup_package WHERE package_name LIKE 'T7%';
       DELETE FROM biz_patient WHERE id = 96071;""")

ok = sum(1 for x in _results if x[0])
print("\n==== T7 公共卫生与健康管理：%d/%d 通过 ====" % (ok, len(_results)))
for good, name, detail in _results:
    if not good:
        print("  [FAIL] %s -> %s" % (name, detail))
sys.exit(0 if ok == len(_results) else 1)
