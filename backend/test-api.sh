#!/bin/bash

echo "================================"
echo "ERP系统自测报告"
echo "================================"
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

# 测试函数
test_api() {
    local name=$1
    local url=$2
    local expected_code=$3

    TOTAL=$((TOTAL+1))

    echo -n "测试 $name ... "

    response=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null)

    if [ "$response" = "$expected_code" ]; then
        echo -e "${GREEN}✓ PASS${NC} (HTTP $response)"
        PASSED=$((PASSED+1))
    else
        echo -e "${RED}✗ FAIL${NC} (Expected: $expected_code, Got: $response)"
        FAILED=$((FAILED+1))
    fi
}

echo "1. 服务健康检查"
echo "--------------------------------"
test_api "后端健康检查" "http://localhost:8080/actuator/health" "200"
test_api "前端服务" "http://localhost:5173" "200"
echo ""

echo "2. 公开API端点测试（无需认证）"
echo "--------------------------------"
test_api "健康检查端点" "http://localhost:8080/actuator/health" "200"
echo ""

echo "3. 需要认证的API端点测试"
echo "--------------------------------"
echo -e "${YELLOW}注意: 以下端点需要JWT认证，预期返回401${NC}"
test_api "产品列表API" "http://localhost:8080/api/masterdata/products?page=1&size=10" "401"
test_api "客户列表API" "http://localhost:8080/api/masterdata/customers?page=1&size=10" "401"
test_api "采购订单API" "http://localhost:8080/api/purchase/orders?page=1&size=10" "401"
test_api "销售订单API" "http://localhost:8080/api/sales/orders?page=1&size=10" "401"
test_api "库存查询API" "http://localhost:8080/api/inventory/stocks?page=1&size=10" "401"
echo ""

echo "================================"
echo "测试总结"
echo "================================"
echo "总计: $TOTAL"
echo -e "${GREEN}通过: $PASSED${NC}"
echo -e "${RED}失败: $FAILED${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ 所有测试通过！${NC}"
    exit 0
else
    echo -e "${RED}✗ 有 $FAILED 个测试失败${NC}"
    exit 1
fi
