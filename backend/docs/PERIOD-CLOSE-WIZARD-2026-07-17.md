# 期间结账向导（2026-07-17）

## 后端

`GET /api/finance/periods/{id}/close-check` 现返回：

- `passed` / `issues`（阻塞项）
- `checks`：固定检查清单（业务/对账/财务/资金/库存），便于向导展示

新增检查：期间内未完结业务单据（销售/采购订单草稿审批中、发货收货草稿、费用未过账、手工凭证未过账）。

锁定前仍强制 `passed=true`。

## 前端

会计期间页：

- 行操作「结账向导」：概览 → 月结检查 → 锁定/结账
- 月结检查弹窗展示 checklist，并通过后可直接锁定/结账

## 测试

`AccountPeriodControllerTest` + `AccountPeriodCloseCheckerTenantScopeTest` 全绿。
