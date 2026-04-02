#!/bin/bash

# JeecgBoot AI 一键部署脚本
# 使用方法: ./deploy.sh [选项]
#   --frontend-only  只部署前端
#   --backend-only   只部署后端
#   --skip-build     跳过本地构建，只同步和重启

set -e

PROJECT_DIR="/Users/zhangxj/source/java/jeecgAI/JeecgAI"
REMOTE_HOST="218.26.173.130"
REMOTE_DIR="/opt/jeecg"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 解析参数
FRONTEND=true
BACKEND=true
SKIP_BUILD=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --frontend-only)
            BACKEND=false
            shift
            ;;
        --backend-only)
            FRONTEND=false
            shift
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        -h|--help)
            echo "使用方法: ./deploy.sh [选项]"
            echo "选项:"
            echo "  --frontend-only  只部署前端"
            echo "  --backend-only   只部署后端"
            echo "  --skip-build     跳过本地构建，只同步和重启"
            echo "  -h, --help       显示帮助信息"
            exit 0
            ;;
        *)
            log_error "未知参数: $1"
            exit 1
            ;;
    esac
done

cd "$PROJECT_DIR"

# 构建前端
if [ "$FRONTEND" = true ] && [ "$SKIP_BUILD" = false ]; then
    log_info "构建前端..."
    cd jeecgboot-vue3
    pnpm build
    cd ..
    log_info "前端构建完成"
fi

# 构建后端
if [ "$BACKEND" = true ] && [ "$SKIP_BUILD" = false ]; then
    log_info "构建后端..."
    cd jeecg-boot
    mvn clean package -DskipTests
    cd ..
    log_info "后端构建完成"
fi

# 同步到服务器
log_info "同步文件到服务器..."

if [ "$FRONTEND" = true ]; then
    log_info "同步前端..."
    rsync -avz --delete jeecgboot-vue3/ root@${REMOTE_HOST}:${REMOTE_DIR}/jeecgboot-vue3/
fi

if [ "$BACKEND" = true ]; then
    log_info "同步后端..."
    rsync -avz --delete jeecg-boot/ root@${REMOTE_HOST}:${REMOTE_DIR}/jeecg-boot/
fi

# 重启容器
log_info "重新构建并启动容器..."
ssh root@${REMOTE_HOST} "cd ${REMOTE_DIR} && docker compose up -d --build"

# 检查状态
log_info "检查服务状态..."
sleep 5
ssh root@${REMOTE_HOST} "docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | grep -E 'jeecg|NAMES'"

log_info "================================"
log_info "部署完成!"
log_info "前端: http://${REMOTE_HOST}:8866"
log_info "后端: http://${REMOTE_HOST}:8080/jeecg-boot"
log_info "================================"
