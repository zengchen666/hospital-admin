# ============================================================================
# 后端镜像：多阶段构建
#
#   stage 1  build   : Maven 3.9 + JDK 25 编译打包，并拆成 Spring Boot 分层产物
#   stage 2  runtime : Temurin JRE 25，只带运行必需的 mysqldump 与 curl
#
# 单独构建（在 backend/ 目录下）：
#   docker build -t hospital-admin:0.1.0 .
#
# 通常由根目录的 docker-compose.yml 驱动，context 就是 backend/。
# ============================================================================


# ---------------------------------------------------------------------------
# 构建阶段
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /build

# 先只拷 POM 把依赖下载成独立一层：
# 业务代码天天改，依赖几乎不动，拆开之后改一行代码不需要重新拉几百 MB 依赖。
# --mount=type=cache 让 ~/.m2 跨构建复用（BuildKit 默认开启）。
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline -DskipTests || true

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp clean package -DskipTests \
 && cp target/*.jar app.jar \
 && java -Djarmode=layertools -jar app.jar extract --destination /build/extracted


# ---------------------------------------------------------------------------
# 运行阶段
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre

# mysqldump 不是可选项：数据安全模块的「手动备份」会 fork 外部进程导出 SQL，
# 镜像里没有这个命令，点备份就是 500，而且报错离现场很远（只会看到 exit code 2）。
# curl 是容器健康探针要用的。
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends curl mysql-client tzdata; \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 分层拷贝：变动频率从低到高。
# 依赖层几十 MB 基本不动，应用层每次发版都变，放最后才能吃到缓存。
COPY --from=build /build/extracted/dependencies/          ./
COPY --from=build /build/extracted/spring-boot-loader/    ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/           ./

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    # 容器感知：按 cgroup 限额按比例取堆，不要写死 -Xmx，否则换机器就得改镜像。
    # ExitOnOutOfMemoryError：OOM 后直接退出让编排重启，比留一个半死的进程继续接流量强。
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 \
-XX:InitialRAMPercentage=50.0 \
-XX:+ExitOnOutOfMemoryError \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=/app/logs \
-Djava.security.egd=file:/dev/./urandom \
-Duser.timezone=Asia/Shanghai \
-Dfile.encoding=UTF-8"

# 非 root 运行：容器里跑 root 等于把一次 RCE 直接升级成宿主机权限问题。
RUN groupadd -r hospital \
 && useradd -r -g hospital -u 10001 -d /app -s /usr/sbin/nologin hospital \
 && mkdir -p /app/logs /app/backup \
 && chown -R hospital:hospital /app

USER hospital

EXPOSE 8080

# 探针直连业务健康检查端点（无需鉴权，DB 挂了返回 503）。
# start-period 给足：Spring Boot + Hikari 首次建连在国内机器上偶尔要二三十秒。
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8080/api/system/health || exit 1

# exec 让 java 顶替 sh 成为 PID 1，SIGTERM 才能被 Spring 的优雅停机接住。
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} org.springframework.boot.loader.launch.JarLauncher"]
