#!/bin/bash
# 数据库备份恢复脚本
# 使用方法: ./restore-database.sh <备份文件路径>
# 示例: ./restore-database.sh /data/backups/mysql/erp_server_production_20260612_143000.sql.gz

set -euo pipefail
umask 077

BACKUP_FILE=${1:-}
MYSQL_DEFAULTS_FILE=""

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

cleanup() {
    if [ -n "$MYSQL_DEFAULTS_FILE" ]; then
        rm -f -- "$MYSQL_DEFAULTS_FILE"
    fi
}

trap cleanup EXIT HUP INT TERM

fail() {
    log_error "$1"
    exit 1
}

validate_positive_integer() {
    local name="$1"
    local value="$2"
    case "$value" in
        ""|*[!0-9]*)
            fail "$name 必须是正整数"
            ;;
    esac
    if [ "$value" -lt 1 ]; then
        fail "$name 必须是正整数"
    fi
}

validate_identifier() {
    local name="$1"
    local value="$2"
    case "$value" in
        ""|*[!A-Za-z0-9._-]*)
            fail "$name 只能包含字母、数字、点、下划线或连字符"
            ;;
    esac
}

write_mysql_defaults_file() {
    MYSQL_DEFAULTS_FILE=$(mktemp "${TMPDIR:-/tmp}/erp-mysql-client.XXXXXX")
    chmod 600 "$MYSQL_DEFAULTS_FILE"
    {
        printf '%s\n' '[client]'
        printf 'host=%s\n' "$DB_HOST"
        printf 'port=%s\n' "$DB_PORT"
        printf 'user=%s\n' "$DB_USER"
        printf 'password=%s\n' "$DB_PASSWORD"
    } > "$MYSQL_DEFAULTS_FILE"
}

verify_checksum() {
    local checksum_file="$1"
    local backup_file="$2"
    local line_count expected_hash actual_hash

    if [ ! -f "$checksum_file" ]; then
        if [ "${ALLOW_MISSING_CHECKSUM:-false}" = "true" ]; then
            log_warn "校验文件缺失，因 ALLOW_MISSING_CHECKSUM=true 跳过校验"
            return 0
        fi
        fail "校验文件不存在: ${checksum_file}（如确需恢复旧备份，请显式设置 ALLOW_MISSING_CHECKSUM=true）"
    fi

    line_count=$(awk 'NF {count++} END {print count + 0}' "$checksum_file")
    if [ "$line_count" -ne 1 ]; then
        fail "校验文件必须且只能包含一条 SHA-256 记录: $checksum_file"
    fi

    expected_hash=$(awk 'NF {print $1}' "$checksum_file")
    if ! printf '%s\n' "$expected_hash" | grep -Eq '^[[:xdigit:]]{64}$'; then
        fail "校验文件中的 SHA-256 格式无效: $checksum_file"
    fi

    actual_hash=$(sha256sum "$backup_file" | awk '{print $1}')
    if [ "$(printf '%s' "$expected_hash" | tr '[:lower:]' '[:upper:]')" != "$(printf '%s' "$actual_hash" | tr '[:lower:]' '[:upper:]')" ]; then
        fail "校验和验证失败，文件可能已损坏"
    fi

    log_info "校验和验证通过"
}

# 检查参数
if [ -z "$BACKUP_FILE" ]; then
    log_error "用法: $0 <备份文件路径>"
    exit 1
fi

if [ ! -f "$BACKUP_FILE" ]; then
    log_error "备份文件不存在: $BACKUP_FILE"
    exit 1
fi

case "$BACKUP_FILE" in
    *.sql.gz) ;;
    *) fail "备份文件必须是 .sql.gz 压缩文件" ;;
esac

# 数据库配置
DB_HOST=${MYSQL_HOST:-localhost}
DB_PORT=${MYSQL_PORT:-3306}
DB_NAME=${MYSQL_DATABASE:-erp_server}
DB_USER=${MYSQL_USER:-root}
DB_PASSWORD=${MYSQL_PASSWORD:-}

validate_positive_integer "MYSQL_PORT" "$DB_PORT"
validate_identifier "MYSQL_DATABASE" "$DB_NAME"

if [ -z "$DB_PASSWORD" ]; then
    log_error "数据库密码未设置 (MYSQL_PASSWORD)"
    exit 1
fi

# 验证校验和
CHECKSUM_FILE="${BACKUP_FILE}.sha256"
command -v mysql >/dev/null 2>&1 || {
    log_error "mysql 客户端未安装"
    exit 1
}
command -v gzip >/dev/null 2>&1 || {
    log_error "gzip 未安装"
    exit 1
}
command -v sha256sum >/dev/null 2>&1 || {
    log_error "sha256sum 未安装"
    exit 1
}

log_info "验证备份文件校验和..."
verify_checksum "$CHECKSUM_FILE" "$BACKUP_FILE"

# 验证压缩文件
log_info "验证压缩文件完整性..."
if ! gzip -t "$BACKUP_FILE"; then
    log_error "备份文件压缩损坏"
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

write_mysql_defaults_file

# 恢复数据库
log_info "开始恢复数据库..."
if gunzip < "$BACKUP_FILE" | mysql \
    --defaults-extra-file="$MYSQL_DEFAULTS_FILE" \
    --host="$DB_HOST" \
    --port="$DB_PORT" \
    --user="$DB_USER" \
    "$DB_NAME"; then
    log_info "========================================"
    log_info "数据库恢复成功！"
    log_info "========================================"
else
    log_error "数据库恢复失败"
    exit 1
fi
