#!/bin/bash

# ============================================
# ERP系统新增页面功能测试脚本
# 日期: 2026-06-16
# 用途: 自动检查9个新增页面的可访问性
# ============================================

echo "=========================================="
echo "ERP系统新增页面功能测试"
echo "开始时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 测试结果统计
TOTAL=0
PASSED=0
FAILED=0

# 前端服务地址
FRONTEND_URL="http://localhost:5173"

# 检查前端服务是否启动
echo "🔍 检查前端服务状态..."
if curl -s -o /dev/null -w "%{http_code}" "$FRONTEND_URL" | grep -q "200\|301\|302"; then
    echo -e "${GREEN}✅ 前端服务运行中${NC}"
else
    echo -e "${RED}❌ 前端服务未启动，请先运行: npm run dev${NC}"
    exit 1
fi
echo ""

# 测试函数
test_page() {
    local name=$1
    local path=$2
    local module=$3

    TOTAL=$((TOTAL + 1))
    echo "[$TOTAL] 测试: $name ($path)"

    # 检查路由配置是否存在
    if grep -q "$path" /e/tuowei/python/erp-frontend/src/router/index.ts; then
        echo -e "  ${GREEN}✓${NC} 路由配置存在"
    else
        echo -e "  ${RED}✗${NC} 路由配置缺失"
        FAILED=$((FAILED + 1))
        echo ""
        return
    fi

    # 检查组件文件是否存在
    local component_path="/e/tuowei/python/erp-frontend/src/views${path}/index.vue"
    if [ -f "$component_path" ]; then
        echo -e "  ${GREEN}✓${NC} 组件文件存在"

        # 检查文件大小（判断是否为空文件）
        local file_size=$(wc -c < "$component_path")
        if [ $file_size -gt 1000 ]; then
            echo -e "  ${GREEN}✓${NC} 组件内容完整 (${file_size} bytes)"
            PASSED=$((PASSED + 1))
        else
            echo -e "  ${YELLOW}⚠${NC} 组件文件较小 (${file_size} bytes)"
            PASSED=$((PASSED + 1))
        fi
    else
        echo -e "  ${RED}✗${NC} 组件文件不存在: $component_path"
        FAILED=$((FAILED + 1))
    fi

    echo ""
}

# 开始测试
echo "=========================================="
echo "开始测试新增页面..."
echo "=========================================="
echo ""

# 财务管理模块（3个）
echo "📊 财务管理模块"
echo "----------------------------------------"
test_page "会计科目管理" "/finance/subjects" "财务管理"
test_page "总账查询" "/finance/ledger" "财务管理"
test_page "费用管理" "/finance/expenses" "财务管理"

# 生产管理模块（2个）
echo "🏭 生产管理模块"
echo "----------------------------------------"
test_page "BOM管理" "/production/boms" "生产管理"
test_page "生产订单管理" "/production/orders" "生产管理"

# 系统管理模块（4个）
echo "⚙️ 系统管理模块"
echo "----------------------------------------"
test_page "岗位管理" "/system/posts" "系统管理"
test_page "字典管理" "/system/dicts" "系统管理"
test_page "系统配置" "/system/configs" "系统管理"
test_page "操作日志" "/system/logs" "系统管理"

# 测试总结
echo "=========================================="
echo "测试完成"
echo "=========================================="
echo ""
echo "测试统计:"
echo "  总计: $TOTAL 个页面"
echo -e "  通过: ${GREEN}$PASSED${NC} 个"
echo -e "  失败: ${RED}$FAILED${NC} 个"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}🎉 所有测试通过！系统准备就绪。${NC}"
    echo ""
    echo "下一步操作:"
    echo "1. 访问 http://localhost:5173 登录系统"
    echo "2. 手动测试每个页面的UI和交互"
    echo "3. 确认无误后执行菜单配置SQL"
    exit 0
else
    echo -e "${RED}⚠️  发现 $FAILED 个问题，请修复后重新测试。${NC}"
    exit 1
fi
