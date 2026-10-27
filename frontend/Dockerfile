# ============================================================================
# 前端镜像：多阶段构建
#
#   stage 1  build   : Node 22 跑 npm ci + vite build，产出 dist
#   stage 2  runtime : nginx 托管静态资源，并把 /api 反代到后端
#
# 单独构建（在 frontend/ 目录下）：
#   docker build -t hospital-web:0.1.0 .
# ============================================================================


# ---------------------------------------------------------------------------
# 构建阶段
# ---------------------------------------------------------------------------
FROM node:22-alpine AS build

WORKDIR /build

# 先只拷 lock 文件装依赖：和后端同理，依赖层与源码层分开，改页面不用重装 node_modules。
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

COPY . .
# package.json 的 build = "vue-tsc --noEmit && vite build"：
# 类型检查留在构建阶段，类型不过关镜像直接构建失败，不会把坏产物带到线上。
RUN npm run build


# ---------------------------------------------------------------------------
# 运行阶段
# ---------------------------------------------------------------------------
FROM nginx:1.27-alpine

COPY --from=build /build/dist /usr/share/nginx/html

# nginx 官方镜像会把 /etc/nginx/templates/*.template 用 envsubst 渲染到
# /etc/nginx/conf.d/。它只替换「已定义的环境变量」，所以 conf 里的
# $uri / $host 这类 nginx 自身变量不会被吃成空字符串 —— 这正是用 .template
# 而不是手写 sed 的原因。
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template

# 后端地址做成环境变量：Compose 里是 app:8080，K8s 里换成 Service 名，
# 同一个镜像不用重新构建。
ENV BACKEND_UPSTREAM=app:8080

EXPOSE 80

# nginx:alpine 自带 busybox wget，不需要额外装 curl。
HEALTHCHECK --interval=15s --timeout=5s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
