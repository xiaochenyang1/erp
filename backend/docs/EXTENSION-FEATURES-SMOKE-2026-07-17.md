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

- 原 **15/15 PASS**（extension-features）；V125 原子转换加入幂等校验后为 16 项，待目标环境复验
- **12/12 PASS**（data-scope 历史基线；2026-08-25 加固版新增双非管理员账号、报表与隐藏追踪负例，待目标环境复验）
- 报告：`target/extension-features-api-smoke/`、`target/data-scope-api-smoke/`

| 段 | 覆盖 |
|----|------|
| B3 | 两商品询价创建→提交→按 `inquiryLineId` 录入两组不同价格→中标→逐行价格 po-prefill→原子转换 PO 草稿→逐行来源/价格、重复请求幂等与双向来源校验 |
| B6 | 发票草稿→确认 POSTED→作废 CANCELLED |
| B2 | 客户 creditLimit=1，大额 SO 审批被拦截 |
| B4 | 未 OQC 禁止出库；创建/判定 OQC 后允许过账 |
| B1 | 数据范围 SELF / WAREHOUSE 矩阵（独立脚本） |
