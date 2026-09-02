#!/usr/bin/env bash

# 新增页面静态注册检查。真实登录、权限、渲染和接口检查由 ui-smoke.mjs 负责。
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
FRONTEND_DIR="$REPO_ROOT/frontend"
ROUTER_FILE="$FRONTEND_DIR/src/router/index.ts"

if ! command -v rg >/dev/null 2>&1; then
    echo "ERROR: rg is required"
    exit 1
fi

if [[ ! -f "$ROUTER_FILE" ]]; then
    echo "ERROR: router file not found: $ROUTER_FILE"
    exit 1
fi

entries=(
    "会计科目管理|path: 'subjects'|src/views/finance/subjects/index.vue"
    "总账查询|path: 'ledger'|src/views/finance/ledger/index.vue"
    "费用管理|path: 'expenses'|src/views/finance/expenses/index.vue"
    "预算管理|path: 'budgets'|src/views/finance/budgets/index.vue"
    "BOM管理|path: 'boms'|src/views/production/boms/index.vue"
    "生产订单管理|path: 'orders'|src/views/production/orders/index.vue"
    "岗位管理|path: 'posts'|src/views/system/posts/index.vue"
    "字典管理|path: 'dicts'|src/views/system/dicts/index.vue"
    "系统配置|path: 'configs'|src/views/system/configs/index.vue"
    "操作日志|path: 'logs'|src/views/system/logs/index.vue"
    "合同台账|path: '/contracts'|src/views/commercial/contracts/index.vue"
    "报表中心|path: '/reports'|src/views/reports/index.vue"
)

passed=0
failed=0

for entry in "${entries[@]}"; do
    IFS='|' read -r name route_fragment component_path <<< "$entry"
    component_file="$FRONTEND_DIR/$component_path"
    failures=()

    if ! rg -F --quiet "$route_fragment" "$ROUTER_FILE"; then
        failures+=("route missing: $route_fragment")
    fi
    if [[ ! -s "$component_file" ]]; then
        failures+=("component missing or empty: $component_path")
    fi

    if (( ${#failures[@]} == 0 )); then
        passed=$((passed + 1))
        echo "[PASS] $name"
    else
        failed=$((failed + 1))
        echo "[FAIL] $name - ${failures[*]}"
    fi
done

echo "RESULT $passed/${#entries[@]} PASS"
if (( failed > 0 )); then
    exit 1
fi

echo "Static registration checks passed. Run 'node backend/scripts/ui-smoke.mjs' for authenticated browser smoke."
