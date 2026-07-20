# Track B 扩展联调加固（2026-07-17 深夜）

## 一键回归

```powershell
# 后端 local + erp_codex_runtime 已启动后：
.\scripts\local-extension-regression.ps1
```

等价于：

```powershell
node scripts/extension-features-api-smoke.cjs
node scripts/data-scope-api-smoke.cjs
```

`local-runtime-acceptance.ps1` 在主链路 GO 后也会自动串跑上述 extension regression。

## 浏览器 workflow（ui-smoke）

```powershell
# 仅新 workflow
UI_SMOKE_ROUTES=0 UI_SMOKE_WORKFLOW=finance-invoice,purchase-inquiry node scripts/ui-smoke.mjs

# 全量（2026-07-17 深夜 36/36 PASS）
UI_SMOKE_ROUTES=0 node scripts/ui-smoke.mjs
```

| name | 结果 |
|------|------|
| `finance-invoice-create-post-cancel` | PASS |
| `purchase-inquiry-create-and-convert-to-po` | PASS |
| **全量 36 workflow** | **36/36 PASS**（`target/ui-smoke-report.json`） |

## API smoke 结果

- **15/15 PASS**（extension-features）
- **12/12 PASS**（data-scope）
- 报告：`target/extension-features-api-smoke/`、`target/data-scope-api-smoke/`

| 段 | 覆盖 |
|----|------|
| B3 | 询价创建→提交→报价→中标→po-prefill→生成 PO 草稿 |
| B6 | 发票草稿→确认 POSTED→作废 CANCELLED |
| B2 | 客户 creditLimit=1，大额 SO 审批被拦截 |
| B4 | 未 OQC 禁止出库；创建/判定 OQC 后允许过账 |
| B1 | 数据范围 SELF / WAREHOUSE 矩阵（独立脚本） |
