#!/bin/bash
# 数据库备份恢复脚本
# 使用方法: ./restore-database.sh <备份文件路径>
# 示例: ./restore-database.sh /data/backups/mysql/erp_server_production_20260612_143000.sql.gz

set -e
set -o pipefail

BACKUP_FILE=$1

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 检查参数
if [ -z "$BACKUP_FILE" ]; then
    log_error "用法: $0 <备份文件路径>"
    exit 1
fi

if [ ! -f "$BACKUP_FILE" ]; then
    log_error "备份文件不存在: $BACKUP_FILE"
    exit 1
fi

# 数据库配置
DB_HOST=${MYSQL_HOST:-localhost}
DB_PORT=${MYSQL_PORT:-3306}
DB_NAME=${MYSQL_DATABASE:-erp_server}
DB_USER=${MYSQL_USER:-root}
DB_PASSWORD=${MYSQL_PASSWORD}

if [ -z "$DB_PASSWORD" ]; then
    log_error "数据库密码未设置 (MYSQL_PASSWORD)"
    exit 1
fi

log_warn "========================================"
log_warn "警告：即将恢复数据库"
log_warn "数据库: ${DB_NAME}@${DB_HOST}:${DB_PORT}"
log_warn "备份文件: ${BACKUP_FILE}"
log_warn "当前数据库数据将被覆盖！"
log_warn "========================================"
read -p "确认继续吗？(输入 YES 继续): " CONFIRM

if [ "$CONFIRM" != "YES" ]; then
    log_info "已取消恢复操作"
    exit 0
fi

# 验证校验和
CHECKSUM_FILE="${BACKUP_FILE}.sha256"
if [ -f "$CHECKSUM_FILE" ]; then
    log_info "验证备份文件校验和..."
    if sha256sum -c "$CHECKSUM_FILE"; then
        log_info "校验和验证通过"
    else
        log_error "校验和验证失败，文件可能已损坏"
        exit 1
    fi
fi

# 验证压缩文件
log_info "验证压缩文件完整性..."
if ! gzip -t "$BACKUP_FILE"; then
    log_error "备份文件压缩损坏"
    exit 1
fi

# 恢复数据库
log_info "开始恢复数据库..."
if gunzip < "$BACKUP_FILE" | mysql \
    --host="$DB_HOST" \
    --port="$DB_PORT" \
    --user="$DB_USER" \
    --password="$DB_PASSWORD" \
    "$DB_NAME"; then
    log_info "========================================"
    log_info "数据库恢复成功！"
    log_info "========================================"
else
    log_error "数据库恢复失败"
    exit 1
fi
