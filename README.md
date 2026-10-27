# 医疗后台管理系统 · Hospital Admin

> 一个覆盖「挂号 → 接诊 → 处方 → 收费 → 发药 → 检查检验 → 住院 → 公卫 → 报表」全流程的
> 医院后台管理系统：12 个业务模块、55 张表、动态菜单与按钮级权限、操作审计与数据脱敏。

后端 Spring Boot 4 + 前端 Vue 3 全栈单仓库，配套 Docker Compose 一键编排与 K8s 基础 manifest。

---

## 目录

- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [功能一览](#功能一览)
- [值得一看的实现](#值得一看的实现)
- [项目结构](#项目结构)
- [环境变量](#环境变量)
- [容器化部署](#容器化部署)
- [已知限制](#已知限制)
- [说明与免责](#说明与免责)

---

## 技术栈

| 层次 | 选型 | 说明 |
|---|---|---|
| 后端框架 | Spring Boot **4.1.1**（Spring Framework 7 / Tomcat 11） | Boot 4 模块化 starter，如 `spring-boot-starter-webmvc` |
| JDK | **25（LTS）**，已开启虚拟线程 | IO 密集型后台管理收益明显 |
| 持久层 | MyBatis-Plus 3.5.17（`mybatis-plus-spring-boot4-starter`）+ JSqlParser | **Boot 4 必须用 boot4 starter**，boot3 starter 启动即报错 |
| 数据库 | MySQL 8.0 | 55 张业务表，不用外键约束，雪花 ID 主键 |
| 缓存 | Redis 7 | 登录态、登录失败计数、单号序列 |
| 鉴权 | Spring Security 7 + 手写 JWT HS256 | `sid` / `jti` / `ver` 三声明，支持会话撤销与全端下线 |
| 序列化 | Jackson 3 | 包名已迁到 `tools.jackson`，注解仍是 `com.fasterxml.jackson.annotation` |
| 报表导出 | EasyExcel 4.0.3（xlsx）+ OpenPDF 2.0.3 | OpenPDF 是 iText 4 的 LGPL 分支，规避 AGPL 授权风险 |
| 前端框架 | Vue 3.5 + TypeScript 5.7 + Vite 6 | 组合式 API + `<script setup>`，hash 路由 |
| UI 组件 | Element Plus 2.9 + Pinia 3 | 后端驱动的动态路由（菜单来自 `/system/menu/tree`） |
| 测试 | JUnit 5 + Mockito + AssertJ | **516 个单元测试**，另有 8 个模块级端到端检查脚本 |

---

## 快速开始

### 1. 初始化数据库

按编号顺序执行 `sql/` 下的 26 个脚本（00 建库 → 01~07 建表 → 08 种子数据 → 09~25 增量补丁）：

```bash
mysql -uroot -p < sql/00_create_database.sql
mysql -uroot -p hospital < sql/01_sys.sql
# ……以此类推，编号不能打乱
```

种子数据已内置 9 个账号、9 个角色、80+ 权限点、14 个数据字典，无需自己造测试数据。

### 2. 启动后端

前置：本机 MySQL（默认 3306）与 Redis（默认 **6380**，可用 `REDIS_PORT` 覆盖）。

```bash
cd backend
mvn spring-boot:run
```

看到 `Started HospitalApplication` 即启动成功，接口挂在 **http://localhost:8080/api**。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 **http://localhost:5173**（`/api` 已由 Vite 代理到 8080）。

### 4. 测试账号

初始密码统一为 `123456`。

| 用户名 | 角色 | 能干什么 |
|---|---|---|
| `admin` | 系统管理员 | 全部功能 |
| `director` | 院长 | 查看报表、审核采购/退费，但不能改字典参数 |
| `doc01` | 门诊医生 | 接诊、开处方、开检查单、看本人患者的数据 |
| `nurse01` | 护士 | 住院护理记录、体温单 |
| `cashier01` | 收费员 | 收费、退费、发票 |
| `pharm01` | 药师 | 发药、库存、采购 |
| `fin01` | 财务 | 日结、医保结算、诊疗项目调价 |
| `tech01` | 技师 | 检验采样、报告录入（无审核权） |
| `ph01` | 公卫专员 | 健康档案、疫苗接种、传染病上报 |

> 不同账号登录后侧边栏菜单不一样 —— 这不是 bug，是后端按权限裁剪过的动态路由。
> 用 `cashier01` 登录只能看到工作台 / 患者 / 收费 / 消息四个顶层菜单。

### 5. 跑测试

```bash
cd backend && mvn test          # 516 个单元测试
python backend/check_t8_report.py   # 各模块功能检查（需要后端在 8080 运行）
```

---

## 功能一览

| 模块 | 能力 |
|---|---|
| 系统管理 | 用户/角色/菜单/字典/参数/操作日志；角色只读 + 权限矩阵视图 |
| 患者管理 | 建档、就诊卡、标签、合并；身份证与手机号 AES 加密存储 |
| 挂号与预约 | 排班、放号、挂号、退号（退号不计量） |
| 门诊管理 | 接诊、病历、处方（开方 → 收费 → 发药的状态机） |
| 收费与财务 | 收费、退费、发票、医保 Mock 结算、诊疗项目调价、日结/月结 |
| 药品与药房 | 药品/库存/库房、供应商、采购（审核 ≠ 入库）、发药、退药 |
| 住院管理 | 入院登记、分床、转科、医嘱、护理记录、出院结算 |
| 检查检验 | 检查单、采样条码、结果录入、报告发布、危急值处理 |
| 公共卫生 | 健康档案、随访、疫苗接种、传染病上报（填卡/上报/反馈三段式） |
| 统计报表 | 门诊/住院/收入/药品/患者五类内置报表 + 自定义报表 + Excel/PDF 导出 |
| 消息与通知 | 站内消息、公告（草稿/发布两段式）、短信记录、预警中心 |
| 数据安全 | AES 字段加密、操作审计（AOP 切面）、数据备份（mysqldump）、安全参数 |

---

## 值得一看的实现

写在这里的每一条都对应仓库里真实存在的代码，且都遇到过具体问题。

### 1. JWT 三声明：会话可撤销、改密即下线

普通「无状态 JWT」签出去就收不回来。这里在 token 里塞了三个声明：

| 声明 | 作用 |
|---|---|
| `sid` | 会话 ID，登出时删除 Redis 会话，refresh token 直接作废 |
| `jti` | 单个 token 的身份，登出后 access token 不立即失效（2h 自然过期），但刷新轮换时旧的进黑名单 |
| `ver` | 用户版本号，改密码/禁用账号时 `+1`，该用户所有设备立刻 401 |

允许多设备同时在线，各设备会话互相隔离 —— 手机上登出不影响电脑。

### 2. 权限缓存的 fail-open 设计

权限走 Redis 缓存，Redis 挂了**回源数据库而不是拒绝服务**。配套两个细节：

- Lettuce 默认命令超时 60s，不收紧的话 Redis 一故障，所有请求一起卡一分钟，
  缓存故障会被放大成全站雪崩 —— 所以显式收紧到 1s；
- 登录失败计数放 Redis 而不是内存，多实例部署时锁定状态才是一致的。

### 3. 状态机防并发：条件更新而不是先查再改

```java
// 错误做法：先 SELECT 判断 status，再 UPDATE —— 两个请求同时通过判断，重复发药
// 正确做法：把状态判断压进 WHERE，数据库层面保证只有一个请求能命中
int rows = mapper.transitStatus(id, FROM_STATUS, TO_STATUS);
if (rows == 0) throw new BusinessException(ErrorCode.CONFLICT);  // 40900
```

收费、发药、退费、审核、出院结算全部走这一套。命中 0 行就是有人抢先一步，
返回 40900 让前端刷新重试，而不是 500。

### 4. 敏感字段加密：裸 TypeHandler，不能继承 BaseTypeHandler

身份证、手机号用 **AES-256-GCM** 加密落库，另存一份 SHA-256 的 `*_hash` 字段做等值检索
（密文字段没法 `WHERE id_card = ?`）。

实现时踩过的坑：继承 `BaseTypeHandler<String>` 会让**全库所有 String 字段**都被加密，
必须实现裸的 `TypeHandler<String>` 接口，只对标注了 `@TableField(typeHandler=...)` 的字段生效。

### 5. 报表引擎：白名单矩阵，不拼 SQL

自定义报表不存可执行 SQL（`config_json`），而是「数据集 × 维度」白名单矩阵：

```
OUTPATIENT → {dept, date}      INPATIENT → {summary}
INCOME     → {itemType, dept, doctor}      DRUG → {drug}
PATIENT    → {age, gender, disease, new}
```

用户只能从允许的组合里选，前端拿不到任何拼 SQL 的入口。
配套一个教训：MySQL 开着 `only_full_group_by`，`GROUP BY` 必须写 SELECT 里的别名
（`GROUP BY displayName, statMonth`），写原始表达式会直接 50001。

### 6. 前端动态路由：菜单可见性与接口权限必须同码

菜单树由后端按权限裁剪后返回，前端 `viewMap` 只做「菜单 → 组件」的映射。
中间踩过的坑：某菜单判了 `message:query`，而接口实际要求 `message:sms:send`，
医生能看见菜单却点进去 403。**规则固定为：菜单一律判 `xxx:query`，页内按钮用 `v-perm` 各判各的。**

### 7. 备份：只给备份，不给一键恢复

`BackupService` 调 `mysqldump` 生成 SQL 文件并落一条记录，但**刻意不做「一键恢复」** ——
恢复会把库整体覆盖，界面上放一个按钮等于把删库能力交给一次点击。
密码走 `MYSQL_PWD` 环境变量传递，不出现在 `ps` 能看到的命令行参数里。

---

## 项目结构

```
医疗管理系统/
├── backend/                          # 后端（Spring Boot 4.1 单体）
│   └── src/main/java/com/hospital/
│       ├── common/                   # 统一响应、异常、安全、加密 TypeHandler、审计切面
│       ├── system/                   # 用户/角色/菜单/字典/参数/日志/备份
│       ├── patient/ reg/ clinic/     # 患者 / 挂号 / 门诊
│       ├── finance/ pharmacy/        # 收费财务 / 药品药房
│       ├── inpatient/ exam/          # 住院 / 检查检验
│       ├── publichealth/ report/     # 公卫 / 统计报表
│       └── web/                      # 健康检查
├── frontend/                         # 前端（Vue 3 + TS + Vite）
│   └── src/
│       ├── api/                      # 按模块拆分的接口封装
│       ├── router/index.ts           # 后端菜单 → 组件的 viewMap
│       ├── directives/               # v-perm 按钮级权限
│       └── views/                    # 12 个模块的页面
├── sql/                              # 26 个按编号顺序执行的建库/建表/补丁脚本
├── deploy/k8s/                       # K8s 基础 manifest（Deployment/Service/ConfigMap/Ingress）
├── backend/Dockerfile                # 多阶段：Maven+JDK25 构建 → JRE25 运行
├── frontend/Dockerfile               # 多阶段：Node22 构建 → Nginx 托管
├── docker-compose.yml                # mysql / redis / app / web 四服务编排
├── 需求文档.md                        # 需求与开发计划
├── 接口,字段,原型图文档.md             # 12 个模块的字段/接口/原型（v1.6）
└── 部署说明.md                        # 容器化与 K8s 部署
```

---

## 环境变量

全部可选，不设置时使用括号内的默认值。

| 变量 | 默认值 | 作用 |
|---|---|---|
| `DB_HOST` | `127.0.0.1` | 数据库地址 |
| `DB_PORT` | `3306` | 数据库端口 |
| `DB_NAME` | `hospital` | 数据库名 |
| `DB_USERNAME` | `root` | 数据库账号 |
| `DB_PASSWORD` | `123456` | 数据库密码 |
| `REDIS_HOST` | `127.0.0.1` | Redis 地址 |
| `REDIS_PORT` | `6380` | Redis 端口 |
| `REDIS_PASSWORD` | 空 | Redis 密码 |
| `JWT_SECRET` | **无默认值** | JWT 签名密钥，**不设置直接启动失败**（fail-fast） |
| `HOSPITAL_AES_KEY` | 内置开发密钥 | 敏感字段 AES-256-GCM 密钥，**必须是 32 字节的 Base64** |
| `LOG_PATH` | `logs` | 日志目录 |
| `HOSPITAL_BACKUP_DIR` | `./backup` | 备份文件目录 |

> ⚠️ `HOSPITAL_AES_KEY` 一换，库里已加密的身份证/手机号就解不开了。
> 接入已有数据库时必须沿用原密钥。

---

## 容器化部署

```bash
cp .env.example .env        # 改掉里面的占位密钥
docker compose up -d --build
# 打开 http://localhost
```

编排了 `mysql` + `redis` + `app` + `web(Nginx)` 四个服务，MySQL 首次启动会自动执行 `sql/` 全部脚本。
宿主端口映射为 **MySQL 3307 / Redis 6381**（避开本机已占用的 3306 / 6380），页面在 80。

详见 [部署说明.md](./部署说明.md)；K8s 部署见 `deploy/k8s/`。

---

## 已知限制

如实记录尚未解决的问题，避免误解。

### 1. 疫苗接种只登记、不扣库存

疫苗与药品库存在两张表里，目前接种登记不会联动扣减疫苗库存，需要库房手工盘点。

### 2. 首页未接消息角标

未读消息数、预警条数的接口已经就绪，但工作台的角标还没有接线。

### 3. 角色与授权在界面上是只读的

权限变更走 `sql/` 补丁脚本而不是界面勾选 —— 脚本本身就是 review 记录，
三个月后还能回答「为什么收费员现在能看全院收入」。这是刻意的设计取舍，不是没做完。

### 4. Docker / K8s 未做真机验证

配置经过 133 条静态断言校验（`python backend/check_t10_deploy.py`），
但编写时本机 Docker 守护进程未启动，`docker build` 与 `compose up` 尚未实际跑过。
镜像基础 tag 已确认存在于 Docker Hub。

### 5. Element Plus 全量引入

前端主包体积偏大，未做按需引入与分包优化。

---

## 说明与免责

- 本项目为**学习交流项目**，用于个人学习与技术展示，**不用于任何商业用途**
- 数据均为虚构的测试数据，不含任何真实患者信息
- 账号密码为教学用的弱口令，**请勿用于任何生产环境**；正式部署务必用环境变量覆盖数据库密码、`JWT_SECRET` 与 `HOSPITAL_AES_KEY`
- 业务规则与表结构参考了《需求文档.md》与《接口,字段,原型图文档.md》

---

如果发现问题或有改进建议，欢迎提 Issue。
