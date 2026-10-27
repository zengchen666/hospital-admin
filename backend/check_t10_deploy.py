# -*- coding: utf-8 -*-
"""T10 功能检查：容器化部署（Docker 镜像 + Compose 编排 + K8s manifest）。

本机 Docker 守护进程没开，跑不了 docker build / compose up，
所以这里做的是「能静态验证的部分」：
  1. docker compose config 能否被解析（能解析就说明 YAML 与变量插值合法）
  2. Dockerfile / Nginx 配置 / K8s manifest 的结构与关键约定
  3. .dockerignore / .gitignore / .env.example 是否把该挡的挡住了
真机验证命令写在《部署说明.md》里。
"""
import base64
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_results = []


class _Tee(object):
    """同时写控制台和 UTF-8 日志文件。

    Windows 下把 stdout 交给 PowerShell 转存会被按 ANSI 二次解码成乱码，
    所以让 Python 自己按 UTF-8 落一份，绕开外层解码。
    """

    def __init__(self, *streams):
        self.streams = streams

    def write(self, data):
        for s in self.streams:
            try:
                s.write(data)
            except Exception:
                pass

    def flush(self):
        for s in self.streams:
            try:
                s.flush()
            except Exception:
                pass


sys.stdout = _Tee(sys.stdout,
                  open(os.path.join(ROOT, "backend", "check_t10_deploy.log"),
                       "w", encoding="utf-8"))


# --------------------------------------------------------------------------- #
# 工具函数（一律定义在文件顶部，避免 NameError）
# --------------------------------------------------------------------------- #
def read(*parts):
    """按相对 ROOT 的路径读文本文件，不存在返回空串。"""
    path = os.path.join(ROOT, *parts)
    if not os.path.exists(path):
        return ""
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def exists(*parts):
    return os.path.exists(os.path.join(ROOT, *parts))


def dotenv():
    """解析 .env.example，返回 {key: value}。"""
    kv = {}
    for line in read(".env.example").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        kv[k.strip()] = v.strip()
    return kv


def load_compose():
    """优先用 docker compose config 拿到插值后的模型；拿不到就直读 YAML。"""
    compose = os.path.join(ROOT, "docker-compose.yml")
    envfile = os.path.join(ROOT, ".env.example")
    try:
        out = subprocess.run(
            ["docker", "compose", "--env-file", envfile, "-f", compose,
             "config", "--format", "json"],
            capture_output=True, timeout=180)
        if out.returncode == 0 and out.stdout.strip():
            return json.loads(out.stdout.decode("utf-8")), "docker compose config"
    except Exception:
        pass
    try:
        import yaml
        with open(compose, "r", encoding="utf-8") as f:
            return yaml.safe_load(f), "yaml 直读（未做变量插值）"
    except Exception:
        return {}, "解析失败"


def yaml_docs(*parts):
    """读一个可能含多文档（---）的 YAML 文件，返回文档列表。"""
    import yaml
    return [d for d in yaml.safe_load_all(read(*parts)) if d]


def envmap(service):
    """服务的 environment 可能是 dict 或 list，统一成 dict。"""
    e = service.get("environment") or {}
    if isinstance(e, list):
        out = {}
        for item in e:
            if isinstance(item, str) and "=" in item:
                k, v = item.split("=", 1)
                out[k] = v
            elif isinstance(item, dict):
                out.update(item)
        return out
    return dict(e)


def port_text(p):
    """compose config 的端口是 dict，直读 YAML 是 "a:b"，统一成字符串。"""
    if isinstance(p, dict):
        return "%s:%s" % (p.get("published", ""), p.get("target", ""))
    return str(p)


def ports_text(service):
    return [port_text(p) for p in (service.get("ports") or [])]


def container_of(deployment, name=None):
    """取 Deployment 里指定（或第一个）容器。"""
    for c in deployment["spec"]["template"]["spec"]["containers"]:
        if name is None or c.get("name") == name:
            return c
    return {}


def check(name, cond, detail=""):
    _results.append((bool(cond), name, detail))
    print(("[OK]   " if cond else "[FAIL] ") + name + ("  ->  " + detail if detail else ""))
    return bool(cond)


def summary():
    total = len(_results)
    passed = sum(1 for ok, _, _ in _results if ok)
    failed = [n for ok, n, _ in _results if not ok]
    print("\n" + "=" * 78)
    print("T10 容器化部署检查：%d/%d 通过" % (passed, total))
    if failed:
        print("未通过：")
        for n in failed:
            print("  - " + n)
    print("=" * 78)
    return 0 if not failed else 1


# =========================================================================== #
# A. 交付文件清单
# =========================================================================== #
print("\n--- A. 交付文件清单 ---")
for rel in ["docker-compose.yml", ".env.example", ".gitignore",
            "backend/Dockerfile", "backend/.dockerignore",
            "frontend/Dockerfile", "frontend/.dockerignore",
            "frontend/nginx/default.conf.template"]:
    check("存在 " + rel, exists(rel))

for rel in ["00-namespace.yaml", "01-configmap.yaml", "02-secret.example.yaml",
            "10-mysql.yaml", "11-redis.yaml", "20-backend.yaml",
            "30-frontend.yaml", "40-ingress.yaml", "kustomization.yaml"]:
    check("存在 deploy/k8s/" + rel, exists("deploy", "k8s", rel))


# =========================================================================== #
# B. Compose 编排
# =========================================================================== #
print("\n--- B. docker-compose.yml ---")
compose, source = load_compose()
check("compose 可解析", bool(compose), "解析方式：" + source)
services = compose.get("services") or {}

for name in ["mysql", "redis", "app", "web"]:
    check("服务 %s 已定义" % name, name in services)

mysql = services.get("mysql") or {}
redis = services.get("redis") or {}
app = services.get("app") or {}
web = services.get("web") or {}

# MySQL
check("mysql 镜像带明确版本（非 latest）",
      bool(re.search(r"mysql:\d", str(mysql.get("image", "")))), str(mysql.get("image")))
check("mysql 挂载 sql/ 到 docker-entrypoint-initdb.d",
      any("/docker-entrypoint-initdb.d" in str(v) for v in (mysql.get("volumes") or [])),
      ";".join(str(v) for v in (mysql.get("volumes") or [])))
check("mysql 有 healthcheck", bool(mysql.get("healthcheck")),
      json.dumps(mysql.get("healthcheck", {}), ensure_ascii=False)[:80])
check("mysql 数据持久化到具名卷",
      any(str(v).startswith("mysql-data:") or "mysql-data" in str(v)
          for v in (mysql.get("volumes") or [])))

# Redis
check("redis 开启 AOF（登录态不能随容器重启丢失）",
      "appendonly" in json.dumps(redis.get("command", []), ensure_ascii=False))
check("redis 有 healthcheck", bool(redis.get("healthcheck")))

# 后端
check("app 使用 backend/ 构建上下文",
      str((app.get("build") or {}).get("context", "")).replace("\\", "/").endswith("backend")
      or str(app.get("build")) == "./backend",
      str(app.get("build")))
app_env = envmap(app)
check("app 启用 prod profile", app_env.get("SPRING_PROFILES_ACTIVE") == "prod",
      str(app_env.get("SPRING_PROFILES_ACTIVE")))
check("app 通过服务名连库（DB_HOST=mysql）", app_env.get("DB_HOST") == "mysql",
      str(app_env.get("DB_HOST")))
check("app 通过服务名连 Redis（REDIS_HOST=redis）", app_env.get("REDIS_HOST") == "redis",
      str(app_env.get("REDIS_HOST")))
check("app 登录日志落容器内 /app/logs", app_env.get("LOG_PATH") == "/app/logs",
      str(app_env.get("LOG_PATH")))
check("app 备份目录指向 /app/backup", app_env.get("HOSPITAL_BACKUP_DIR") == "/app/backup",
      str(app_env.get("HOSPITAL_BACKUP_DIR")))

# 注意：compose config 的输出是「已插值」的，值里已经看不到 ${...}，
# 所以「未硬编码」要看原始文件里有没有 ${KEY:?} 这种强制必填写法，
# 「能取到值」再看插值后的结果非空。两条都满足才算对。
compose_txt = read("docker-compose.yml")
for key in ["JWT_SECRET", "HOSPITAL_AES_KEY", "DB_PASSWORD"]:
    declared = re.search(r"\$\{%s:\?" % key, compose_txt) is not None
    resolved = bool(str(app_env.get(key, "")).strip())
    check("app 的 %s 走环境变量且缺失即启动失败" % key, declared and resolved,
          "声明 ${%s:?}=%s，解析后长度=%d" % (key, declared, len(str(app_env.get(key, "")))))

dep = app.get("depends_on") or {}
check("app 等 mysql 健康后才启动",
      (dep.get("mysql") or {}).get("condition") == "service_healthy", json.dumps(dep, ensure_ascii=False))
check("app 等 redis 健康后才启动",
      (dep.get("redis") or {}).get("condition") == "service_healthy", json.dumps(dep, ensure_ascii=False))
check("app 映射 8080", any(p.endswith(":8080") for p in ports_text(app)), ";".join(ports_text(app)))
check("app 挂载日志与备份卷",
      {"app-logs", "app-backup"} <= set(compose.get("volumes") or {}),
      ",".join(sorted(compose.get("volumes") or {})))

# 前端
web_env = envmap(web)
check("web 传入反代目标 BACKEND_UPSTREAM", bool(web_env.get("BACKEND_UPSTREAM")),
      str(web_env.get("BACKEND_UPSTREAM")))
check("web 默认反代到 app:8080", str(web_env.get("BACKEND_UPSTREAM")).endswith("app:8080"),
      str(web_env.get("BACKEND_UPSTREAM")))
check("web 等 app 健康后才启动",
      ((web.get("depends_on") or {}).get("app") or {}).get("condition") == "service_healthy")
check("web 映射 80 端口", any(p.endswith(":80") for p in ports_text(web)), ";".join(ports_text(web)))

check("四个数据卷全部声明",
      {"mysql-data", "redis-data", "app-logs", "app-backup"} <= set(compose.get("volumes") or {}),
      ",".join(sorted(compose.get("volumes") or {})))
check("全部服务在同一个内网",
      all(s in (compose.get("networks") or {}) for s in ["hospital-net"]),
      ",".join(sorted(compose.get("networks") or {})))


# =========================================================================== #
# C. Dockerfile
# =========================================================================== #
print("\n--- C. Dockerfile ---")
be = read("backend", "Dockerfile")
fe = read("frontend", "Dockerfile")

check("后端是多阶段构建（2 个 FROM）", len(re.findall(r"(?m)^FROM\s", be)) == 2,
      "%d 个" % len(re.findall(r"(?m)^FROM\s", be)))
check("后端构建阶段命名 build", bool(re.search(r"(?m)^FROM\s+\S+\s+AS\s+build", be)))
check("后端运行阶段用 JRE（不带编译期工具）",
      bool(re.search(r"(?m)^FROM\s+eclipse-temurin:25-jre", be)))
check("后端以非 root 运行", bool(re.search(r"(?m)^USER\s+(?!root)\S+", be)))
check("后端镜像装了 mysqldump（备份功能依赖外部命令）", "mysql-client" in be)
check("后端暴露 8080", bool(re.search(r"(?m)^EXPOSE\s+8080", be)))
check("后端健康探针打到 /api/system/health",
      "/api/system/health" in be and "HEALTHCHECK" in be)
check("后端按 Spring Boot 分层拷贝（4 层）",
      len(re.findall(r"COPY --from=build /build/extracted/", be)) == 4,
      "%d 层" % len(re.findall(r"COPY --from=build /build/extracted/", be)))
check("后端用分层 JarLauncher 启动",
      "org.springframework.boot.loader.launch.JarLauncher" in be)
check("后端堆内存按容器配额百分比配置（不写死 -Xmx）", "MaxRAMPercentage" in be)
check("后端 OOM 后退出而非假死", "ExitOnOutOfMemoryError" in be)
check("后端 entrypoint 用 exec（java 接管 PID 1，才能收到 SIGTERM）",
      "exec java" in be)
check("后端非 root uid 与 K8s runAsUser 对齐(10001)", "10001" in be)

check("前端是多阶段构建（2 个 FROM）", len(re.findall(r"(?m)^FROM\s", fe)) == 2)
check("前端构建阶段用 npm ci（依赖锁定，构建可复现）", "npm ci" in fe)
check("前端运行阶段用 nginx", bool(re.search(r"(?m)^FROM\s+nginx:", fe)))
check("前端产物拷到 nginx 站点目录",
      "/usr/share/nginx/html" in fe and "--from=build" in fe)
check("前端 conf 走 envsubst 模板目录",
      "/etc/nginx/templates/" in fe and ".template" in fe)
check("前端暴露 80", bool(re.search(r"(?m)^EXPOSE\s+80", fe)))
check("前端有健康探针", "HEALTHCHECK" in fe and "/healthz" in fe)


# =========================================================================== #
# D. Nginx 配置
# =========================================================================== #
print("\n--- D. frontend/nginx/default.conf.template ---")
conf = read("frontend", "nginx", "default.conf.template")
# 注释里会提到 rewrite / gzip 这些词，判指令时必须先把注释行剔掉
conf_code = "\n".join(ln for ln in conf.splitlines() if not ln.lstrip().startswith("#"))
check("反代目标用模板变量 ${BACKEND_UPSTREAM}", "proxy_pass http://${BACKEND_UPSTREAM}" in conf)
check("/api 单独 location", bool(re.search(r"location\s+/api/", conf_code)))
check("/api 不做 rewrite（保住后端 context-path）", "rewrite" not in conf_code.lower())
check("SPA 回退到 index.html", "try_files $uri $uri/ /index.html" in conf_code)
check("上传体积上限 50m（与后端 max-request-size 对齐）", "client_max_body_size 50m" in conf_code)
check("有 /healthz 探针且不打 access_log", "/healthz" in conf_code and "access_log off" in conf_code)
check("开启 gzip", bool(re.search(r"^\s*gzip\s+on\s*;", conf_code, re.M)))
check("带 hash 的 assets 长缓存", "location /assets/" in conf and "immutable" in conf)
check("读超时放宽到 120s（报表导出）", "proxy_read_timeout    120s" in conf)


# =========================================================================== #
# E. Kubernetes manifest
# =========================================================================== #
print("\n--- E. deploy/k8s ---")
docs = {}
for fn in ["00-namespace.yaml", "01-configmap.yaml", "10-mysql.yaml",
           "11-redis.yaml", "20-backend.yaml", "30-frontend.yaml",
           "40-ingress.yaml", "kustomization.yaml"]:
    try:
        docs[fn] = yaml_docs("deploy", "k8s", fn)
        check("%s 可解析" % fn, bool(docs[fn]), "%d 个文档" % len(docs[fn]))
    except Exception as ex:
        docs[fn] = []
        check("%s 可解析" % fn, False, str(ex))


def find(kind, fname=None):
    out = []
    for fn, ds in docs.items():
        if fname and fn != fname:
            continue
        for d in ds:
            if d.get("kind") == kind:
                out.append(d)
    return out


cm = find("ConfigMap")
check("ConfigMap 存在且带后端配置", len(cm) == 1, "%d 个" % len(cm))
cm_data = (cm[0].get("data") or {}) if cm else {}
for k in ["SPRING_PROFILES_ACTIVE", "DB_HOST", "REDIS_HOST", "LOG_PATH", "BACKEND_UPSTREAM"]:
    check("ConfigMap 含 %s" % k, k in cm_data, str(cm_data.get(k, "")))
check("ConfigMap 未出现密钥字段",
      not any(k in cm_data for k in ["JWT_SECRET", "DB_PASSWORD", "HOSPITAL_AES_KEY"]),
      ",".join(sorted(cm_data)))

bd = find("Deployment", "20-backend.yaml")
check("后端 Deployment 存在", len(bd) == 1)
if bd:
    spec = bd[0]["spec"]
    pod = spec["template"]["spec"]
    check("后端多副本（>=2）", spec.get("replicas", 1) >= 2, str(spec.get("replicas")))
    check("后端注入 ConfigMap 与 Secret",
          len(pod["containers"][0].get("envFrom") or []) == 2)
    c = container_of(bd[0])
    for probe in ["startupProbe", "readinessProbe", "livenessProbe"]:
        chk = (c.get(probe) or {}).get("httpGet") or {}
        check("后端 %s 指向 /api/system/health" % probe,
              chk.get("path") == "/api/system/health", str(chk.get("path")))
    check("后端容器非 root 运行",
          (c.get("securityContext") or {}).get("runAsNonRoot") is True)
    check("后端容器 uid 与镜像一致（10001）",
          (c.get("securityContext") or {}).get("runAsUser") == 10001)
    check("后端优雅停机宽限期 >= 30s",
          pod.get("terminationGracePeriodSeconds", 0) >= 30,
          str(pod.get("terminationGracePeriodSeconds")))
    check("后端有 preStop 钩子（等 Endpoint 摘除）", "preStop" in (c.get("lifecycle") or {}))
    check("后端设置了资源 requests/limits",
          "requests" in (c.get("resources") or {}) and "limits" in (c.get("resources") or {}))

svc = find("Service", "20-backend.yaml")
check("后端 Service 暴露 8080", svc and svc[0]["spec"]["ports"][0]["port"] == 8080)

fd = find("Deployment", "30-frontend.yaml")
check("前端 Deployment 存在", len(fd) == 1)
if fd:
    c = container_of(fd[0])
    envs = {e.get("name"): e for e in (c.get("env") or [])}
    check("前端从 ConfigMap 取反代目标",
          "BACKEND_UPSTREAM" in envs
          and "configMapKeyRef" in (envs["BACKEND_UPSTREAM"].get("valueFrom") or {}),
          json.dumps(envs.get("BACKEND_UPSTREAM", {})))
    check("前端探针打到 /healthz",
          ((c.get("readinessProbe") or {}).get("httpGet") or {}).get("path") == "/healthz")

ing = find("Ingress")
check("Ingress 存在", len(ing) == 1)
if ing:
    paths = ing[0]["spec"]["rules"][0]["http"]["paths"]
    backends = {p["path"]: p["backend"]["service"]["name"] for p in paths}
    check("Ingress 把 /api 路由到后端", backends.get("/api") == "hospital-backend", json.dumps(backends))
    check("Ingress 把 / 路由到前端", backends.get("/") == "hospital-web")
    check("Ingress 指定 nginx controller",
          ing[0]["spec"].get("ingressClassName") == "nginx")
    ann = ing[0]["metadata"].get("annotations") or {}
    check("Ingress 放开 50m 上传与 120s 读超时",
          "50m" in str(ann.get("nginx.ingress.kubernetes.io/proxy-body-size"))
          and "120" in str(ann.get("nginx.ingress.kubernetes.io/proxy-read-timeout")))

kust = find("Kustomization")
check("kustomization 存在", len(kust) == 1)
if kust:
    res = kust[0].get("resources") or []
    check("kustomization 不含 Secret（避免把占位密钥推上去）",
          not any("secret" in r.lower() for r in res), ",".join(res))
    for r in ["00-namespace.yaml", "20-backend.yaml", "30-frontend.yaml"]:
        check("kustomization 引用 %s" % r, r in res)

check("Secret 仅以 .example 形式提供（不参与 apply）",
      exists("deploy", "k8s", "02-secret.example.yaml")
      and not exists("deploy", "k8s", "02-secret.yaml"))
sec_txt = read("deploy", "k8s", "02-secret.example.yaml")
check("Secret 模板里给出 kubectl create secret 用法",
      "kubectl" in sec_txt and "create secret generic" in sec_txt)
check("Secret 模板提示 AES 换密钥会导致存量密文解不开", "解不开" in sec_txt)


# =========================================================================== #
# F. 忽略规则与密钥样例
# =========================================================================== #
print("\n--- F. 忽略规则与密钥样例 ---")
be_ignore = read("backend", ".dockerignore")
fe_ignore = read("frontend", ".dockerignore")
gi = read(".gitignore")
check("后端忽略 target/（不要把构建产物送进上下文）", "target/" in be_ignore)
check("后端忽略本地 logs/backup/", "logs/" in be_ignore and "backup/" in be_ignore)
check("前端忽略 node_modules/", "node_modules/" in fe_ignore)
check("前端忽略 dist/", "dist/" in fe_ignore)
check(".gitignore 忽略 .env（真实密钥不进仓库）", bool(re.search(r"(?m)^\.env$", gi)))
check(".gitignore 忽略构建产物",
      "backend/target/" in gi and "frontend/node_modules/" in gi)

env = dotenv()
for k in ["JWT_SECRET", "HOSPITAL_AES_KEY", "MYSQL_ROOT_PASSWORD", "DB_PASSWORD", "DB_USERNAME"]:
    check(".env.example 含 %s" % k, k in env and env[k] != "")
check(".env.example 的 JWT 密钥长度足够（>=32）", len(env.get("JWT_SECRET", "")) >= 32,
      "%d 字符" % len(env.get("JWT_SECRET", "")))
try:
    aes = base64.b64decode(env.get("HOSPITAL_AES_KEY", ""), validate=True)
    ok = len(aes) == 32
except Exception:
    ok = False
check(".env.example 的 AES 密钥是 32 字节 Base64", ok)
check(".env.example 提醒生产必须重新生成密钥", "重新生成" in read(".env.example"))

sys.exit(summary())
