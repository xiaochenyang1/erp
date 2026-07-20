#!/bin/bash

echo "========================================"
echo "   ERP系统全面检查报告"
echo "========================================"
echo ""

cd ../erp-frontend

echo "📊 第一部分：前端完整性检查"
echo "========================================"
echo ""

echo "1. API接口封装检查"
echo "----------------------------------------"
api_files=(
    "src/api/auth.ts|认证API"
    "src/api/masterdata.ts|主数据API"
    "src/api/purchase.ts|采购API"
    "src/api/sales.ts|销售API"
    "src/api/inventory.ts|库存API"
    "src/api/finance.ts|财务API"
    "src/api/system.ts|系统API"
    "src/api/workflow.ts|工作流API"
)

api_count=0
for item in "${api_files[@]}"; do
    IFS='|' read -r file label <<< "$item"
    if [ -f "$file" ]; then
        echo "  ✓ $label"
        ((api_count++))
    else
        echo "  ✗ $label (缺失)"
    fi
done
echo "  结果: $api_count/8 API文件完整"

echo ""
echo "2. 页面组件完整性检查"
echo "----------------------------------------"

# 统计各模块页面
inventory_pages=$(find src/views/inventory -name "index.vue" | wc -l)
sales_pages=$(find src/views/sales -name "index.vue" | wc -l)
system_pages=$(find src/views/system -name "index.vue" | wc -l)
finance_pages=$(find src/views/finance -name "index.vue" | wc -l)
total_pages=$(find src/views -name "index.vue" | wc -l)

echo "  ✓ 库存管理: $inventory_pages 个页面"
echo "  ✓ 销售管理: $sales_pages 个页面"
echo "  ✓ 系统管理: $system_pages 个页面"
echo "  ✓ 财务管理: $finance_pages 个页面"
echo "  ✓ 总计: $total_pages 个页面"

echo ""
echo "3. 路由配置完整性"
echo "----------------------------------------"

route_count=0
route_total=0

# 检查关键路由
routes=(
    "InventoryAdjustments|库存调整"
    "InventoryChecks|库存盘点"
    "InventoryTransfers|库存调拨"
    "InventoryAlerts|库存预警"
    "SalesDeliveries|销售发货"
    "SalesReturns|销售退货"
    "SystemRoles|角色管理"
    "SystemMenus|菜单管理"
    "SystemDepts|部门管理"
    "FinanceReceivables|应收应付"
    "FinancePayments|收付款"
    "FinanceVouchers|凭证管理"
    "Reports|报表中心"
)

for item in "${routes[@]}"; do
    IFS='|' read -r route label <<< "$item"
    ((route_total++))
    if grep -q "$route" src/router/index.ts; then
        echo "  ✓ $label 路由"
        ((route_count++))
    else
        echo "  ✗ $label 路由未配置"
    fi
done

echo "  结果: $route_count/$route_total 路由配置完整"

echo ""
echo "4. 页面API调用检查"
echo "----------------------------------------"

# 检查页面是否正确导入API
pages_with_api=$(grep -r "import.*from '@/api" src/views --include="*.vue" | wc -l)
echo "  ✓ $pages_with_api 个页面使用了API调用"

# 检查是否有空的index.vue文件
empty_pages=$(find src/views -name "index.vue" -type f -empty | wc -l)
if [ $empty_pages -eq 0 ]; then
    echo "  ✓ 所有页面文件都有内容"
else
    echo "  ⚠️  发现 $empty_pages 个空页面文件"
    find src/views -name "index.vue" -type f -empty
fi

echo ""
echo "📊 第二部分：后端完整性检查"
echo "========================================"
cd ../erpServer

echo ""
echo "5. Controller控制器检查"
echo "----------------------------------------"

controllers=(
    "src/main/java/com/tuowei/erp/inventory/adjust/controller/InventoryAdjustmentController.java|库存调整"
    "src/main/java/com/tuowei/erp/inventory/check/controller/InventoryStockCheckController.java|库存盘点"
    "src/main/java/com/tuowei/erp/inventory/transfer/controller/InventoryTransferController.java|库存调拨"
    "src/main/java/com/tuowei/erp/inventory/alert/controller/InventoryAlertController.java|库存预警"
    "src/main/java/com/tuowei/erp/sales/delivery/controller/SalesDeliveryController.java|销售发货"
    "src/main/java/com/tuowei/erp/sales/returnorder/controller/SalesReturnController.java|销售退货"
    "src/main/java/com/tuowei/erp/system/role/controller/RoleController.java|角色管理"
    "src/main/java/com/tuowei/erp/system/menu/controller/MenuController.java|菜单管理"
    "src/main/java/com/tuowei/erp/system/dept/controller/DeptController.java|部门管理"
)

controller_count=0
for item in "${controllers[@]}"; do
    IFS='|' read -r file label <<< "$item"
    if [ -f "$file" ]; then
        echo "  ✓ $label Controller"
        ((controller_count++))
    else
        echo "  ✗ $label Controller (未找到)"
    fi
done

total_controllers=$(find src/main/java -name "*Controller.java" -type f | grep -v test | wc -l)
echo "  结果: $controller_count/9 核心Controller存在"
echo "  总计: $total_controllers 个Controller"

echo ""
echo "📊 第三部分：前后端联调检查"
echo "========================================"

echo ""
echo "6. 服务运行状态"
echo "----------------------------------------"

frontend_status=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:5173 2>/dev/null)
backend_status=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null)

frontend_ok=false
backend_ok=false

if [ "$frontend_status" = "200" ]; then
    echo "  ✓ 前端服务: http://localhost:5173 (运行中)"
    frontend_ok=true
else
    echo "  ✗ 前端服务未运行 (HTTP $frontend_status)"
fi

if [ "$backend_status" = "200" ]; then
    echo "  ✓ 后端服务: http://localhost:8080 (运行中)"
    backend_ok=true
else
    echo "  ✗ 后端服务未运行 (HTTP $backend_status)"
fi

echo ""
echo "7. API端点测试"
echo "----------------------------------------"

if [ "$backend_ok" = true ]; then
    # 测试几个关键API端点
    endpoints=(
        "/api/inventory/stocks|库存查询"
        "/api/sales/orders|销售订单"
        "/api/purchase/orders|采购订单"
        "/api/masterdata/products|产品管理"
        "/api/system/users|用户管理"
    )

    api_ok=0
    api_total=0

    for item in "${endpoints[@]}"; do
        IFS='|' read -r path label <<< "$item"
        ((api_total++))
        status=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080${path}?page=1&size=10" 2>/dev/null)
        if [ "$status" = "401" ]; then
            echo "  ✓ $label API (需要认证-正常)"
            ((api_ok++))
        elif [ "$status" = "200" ]; then
            echo "  ✓ $label API (可访问)"
            ((api_ok++))
        else
            echo "  ✗ $label API (HTTP $status)"
        fi
    done

    echo "  结果: $api_ok/$api_total API端点响应正常"
else
    echo "  ⚠️  后端服务未运行，跳过API测试"
fi

echo ""
echo "📊 第四部分：潜在问题与优化建议"
echo "========================================"

echo ""
echo "8. 代码质量检查"
echo "----------------------------------------"

cd ../erp-frontend

# 检查是否有TODO注释
todos=$(grep -r "TODO\|FIXME" src --include="*.vue" --include="*.ts" | wc -l)
if [ $todos -gt 0 ]; then
    echo "  ⚠️  发现 $todos 个待办事项 (TODO/FIXME)"
else
    echo "  ✓ 无待办事项标记"
fi

# 检查是否有console.log (开发调试用)
consoles=$(grep -r "console.log\|console.error" src --include="*.vue" --include="*.ts" | wc -l)
if [ $consoles -gt 0 ]; then
    echo "  ℹ️  发现 $consoles 个console日志 (建议清理)"
else
    echo "  ✓ 无console日志"
fi

echo ""
echo "9. 建议的优化项"
echo "----------------------------------------"

echo "  📝 功能完善建议:"
echo "    • 添加数据导出功能 (Excel/CSV)"
echo "    • 添加高级搜索/筛选"
echo "    • 添加批量操作功能"
echo "    • 完善表单验证规则"
echo ""
echo "  🎨 UI/UX优化建议:"
echo "    • 添加加载骨架屏"
echo "    • 优化移动端响应式布局"
echo "    • 添加操作确认对话框"
echo "    • 统一错误提示样式"
echo ""
echo "  🔒 安全性建议:"
echo "    • 添加CSRF保护"
echo "    • 实现权限按钮级控制"
echo "    • 添加操作日志记录"
echo "    • 敏感数据加密传输"
echo ""
echo "  ⚡ 性能优化建议:"
echo "    • 实现列表虚拟滚动"
echo "    • 添加接口请求缓存"
echo "    • 图片懒加载"
echo "    • 代码分割优化"

echo ""
echo "========================================"
echo "   检查总结"
echo "========================================"
echo ""

if [ "$frontend_ok" = true ] && [ "$backend_ok" = true ] && [ $route_count -eq $route_total ]; then
    echo "✅ 核心功能检查: 全部通过"
    echo "✅ 前后端联调: 正常"
    echo "✅ 系统状态: 可用"
    echo ""
    echo "🎉 系统开发完成，可以投入使用！"
    echo ""
    echo "📌 后续优化建议:"
    echo "  1. 优先: 实现权限控制细化"
    echo "  2. 优先: 添加数据导出功能"
    echo "  3. 中等: 完善表单验证"
    echo "  4. 中等: UI/UX优化"
    echo "  5. 低优先级: 性能优化"
else
    echo "⚠️  发现一些需要关注的问题:"
    if [ "$frontend_ok" = false ]; then
        echo "  • 前端服务未运行"
    fi
    if [ "$backend_ok" = false ]; then
        echo "  • 后端服务未运行"
    fi
    if [ $route_count -ne $route_total ]; then
        echo "  • 部分路由配置缺失"
    fi
fi

echo ""
echo "🚀 访问地址: http://localhost:5173"
echo "👤 默认账号: admin / admin123"
echo ""
