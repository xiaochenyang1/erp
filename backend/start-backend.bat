# 后端快速启动脚本 (Windows)
# 默认联调库：erp_codex_runtime（勿用历史脏库 erp）

@echo off
chcp 65001 >nul
echo.
echo ════════════════════════════════════════════════════════════════
echo    ERP 后端启动（local profile → erp_codex_runtime）
echo ════════════════════════════════════════════════════════════════
echo.

echo [1/5] 检查 Java...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Java 未安装或未配置到 PATH（需要 Java 17）
    pause
    exit /b 1
)
echo ✅ Java 已安装

echo.
echo [2/5] 检查 MySQL 客户端...
mysql --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ⚠️  未找到 mysql 命令，请确认 MySQL 客户端在 PATH 中
) else (
    echo ✅ MySQL 客户端可用
)

echo.
echo [3/5] Redis 非必需（local profile 已排除 Redis 自动装配）...
redis-cli --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ℹ️  Redis 未安装，local 模式可正常联调
) else (
    echo ✅ Redis 可用（local 仍不强制依赖）
)

echo.
echo [4/5] 确保干净联调库 erp_codex_runtime 存在...
echo CREATE DATABASE IF NOT EXISTS erp_codex_runtime CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; | mysql -u root -p12345678
if %errorlevel% neq 0 (
    echo ⚠️  自动建库失败，请手工创建 erp_codex_runtime 后重试
) else (
    echo ✅ 数据库 erp_codex_runtime 就绪
)

echo.
echo [5/5] 启动应用（local profile）...
echo 账号：admin / LocalAdmin123
echo 验收账号：runtime_smoke / RuntimeSmoke123（需手工预置并赋 ERP_ADMIN=3002）
echo ════════════════════════════════════════════════════════════════
echo.

call mvnw.cmd spring-boot:run

pause
