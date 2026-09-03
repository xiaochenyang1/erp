#!/bin/bash
# MySQL数据库备份脚本
# 使用方法: ./backup-database.sh [环境]
# 示例: ./backup-database.sh production

set -e  # 遇到错误立即退出
set -o pipefail  # 让 mysqldump 或 gzip 任一失败都使管道失败

# ========================================
# 配置区域
# ========================================

ENVIRONMENT=${1:-production}
BACKUP_DIR="/data/backups/mysql"
RETENTION_DAYS=30
DATE=$(date +%Y%m%d_%H%M%S)

# 数据库配置（从环境变量读取）
DB_HOST=${MYSQL_HOST:-localhost}
DB_PORT=${MYSQL_PORT:-3306}
DB_NAME=${MYSQL_DATABASE:-erp_server}
DB_USER=${MYSQL_USER:-root}
DB_PASSWORD=${MYSQL_PASSWORD}

# 备份文件名
BACKUP_FILE="${BACKUP_DIR}/${DB_NAME}_${ENVIRONMENT}_${DATE}.sql.gz"
CHECKSUM_FILE="${BACKUP_FILE}.sha256"

# ========================================
# 颜色输出
# ========================================

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

# ========================================
# 前置检查
# ========================================

log_info "开始数据库备份流程..."
log_info "环境: ${ENVIRONMENT}"
log_info "数据库: ${DB_NAME}@${DB_HOST}:${DB_PORT}"

# 检查必要工具
command -v mysqldump >/dev/null 2>&1 || {
    log_error "mysqldump 未安装"
    exit 1
}

command -v gzip >/dev/null 2>&1 || {
    log_error "gzip 未安装"
    exit 1
}

# 检查数据库密码
if [ -z "$DB_PASSWORD" ]; then
    log_error "数据库密码未设置 (MYSQL_PASSWORD)"
    exit 1
fi

# 创建备份目录
mkdir -p "$BACKUP_DIR"

# 检查磁盘空间（需要至少5GB）
AVAILABLE_SPACE=$(df "$BACKUP_DIR" | tail -1 | awk '{print $4}')
if [ "$AVAILABLE_SPACE" -lt 5242880 ]; then
    log_error "磁盘空间不足 (需要至少5GB)"
    exit 1
fi

# ========================================
# 执行备份
# ========================================

log_info "开始导出数据库..."

# 导出数据库并压缩
# --single-transaction: InnoDB一致性快照
# --routines: 包含存储过程和函数
# --triggers: 包含触发器
# --events: 包含事件
# --hex-blob: 二进制数据用十六进制编码
if mysqldump \
    --host="$DB_HOST" \
    --port="$DB_PORT" \
    --user="$DB_USER" \
    --password="$DB_PASSWORD" \
    --single-transaction \
    --routines \
    --triggers \
    --events \
    --hex-blob \
    --default-character-set=utf8mb4 \
    "$DB_NAME" | gzip > "$BACKUP_FILE"; then
    log_info "数据库导出成功"
else
    log_error "数据库导出失败"
    rm -f "$BACKUP_FILE"
    exit 1
fi

# ========================================
# 生成校验和
# ========================================

log_info "生成校验和..."
sha256sum "$BACKUP_FILE" > "$CHECKSUM_FILE"

# ========================================
# 验证备份文件
# ========================================

log_info "验证备份文件..."

# 检查文件大小（至少1KB）
FILE_SIZE=$(stat -f%z "$BACKUP_FILE" 2>/dev/null || stat -c%s "$BACKUP_FILE")
if [ "$FILE_SIZE" -lt 1024 ]; then
    log_error "备份文件异常小 (${FILE_SIZE} bytes)"
    exit 1
fi

# 测试压缩文件完整性
if ! gzip -t "$BACKUP_FILE"; then
    log_error "备份文件压缩损坏"
    exit 1
fi

log_info "备份文件完整性验证通过"

# ========================================
# 清理旧备份
# ========================================

log_info "清理${RETENTION_DAYS}天前的旧备份..."

find "$BACKUP_DIR" -name "${DB_NAME}_${ENVIRONMENT}_*.sql.gz" -mtime +${RETENTION_DAYS} -delete
find "$BACKUP_DIR" -name "${DB_NAME}_${ENVIRONMENT}_*.sql.gz.sha256" -mtime +${RETENTION_DAYS} -delete

DELETED_COUNT=$(find "$BACKUP_DIR" -name "${DB_NAME}_${ENVIRONMENT}_*.sql.gz" -mtime +${RETENTION_DAYS} | wc -l)
if [ "$DELETED_COUNT" -gt 0 ]; then
    log_info "已删除 ${DELETED_COUNT} 个旧备份"
fi

# ========================================
# 输出备份信息
# ========================================

log_info "========================================"
log_info "备份完成！"
log_info "========================================"
log_info "备份文件: ${BACKUP_FILE}"
log_info "文件大小: $(du -h "$BACKUP_FILE" | cut -f1)"
log_info "校验和: $(cat "$CHECKSUM_FILE")"
log_info "保留期限: ${RETENTION_DAYS}天"
log_info "========================================"

# ========================================
# 可选：上传到对象存储
# ========================================

# 如果配置了OSS/S3，可以在这里上传
# if [ -n "$AWS_S3_BUCKET" ]; then
#     log_info "上传到S3..."
#     aws s3 cp "$BACKUP_FILE" "s3://${AWS_S3_BUCKET}/backups/"
#     aws s3 cp "$CHECKSUM_FILE" "s3://${AWS_S3_BUCKET}/backups/"
# fi

exit 0
