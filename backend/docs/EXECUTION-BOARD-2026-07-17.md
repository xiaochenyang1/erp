# 三线执行总看板（终态 · 2026-07-17 深夜加固）

## Track A 上线最小集

| ID | 状态 | 证据 |
|----|------|------|
| A2 | **DONE** | `fq-signoff-all.cjs` technicalPass=true |
| A1 | **BLOCKED_HUMAN** | `docs/FQ-SIGNOFF-FINAL-2026-07-16.md` 等人签 |
| A3 | **PARTIAL** | F/Q readiness 已登记 |
| A4 | **READY_FOR_OPS** | 监控模板/手册已列 |
| A5 | **OPTIONAL** | 本机 Docker 等价证据 |
| A6 | **DONE** | 发布冻结流程文档 |
| 汇总 | | `docs/TRACK-A-COMPLETION-2026-07-17.md` |

## Track B 业务扩展

| ID | 状态 | 证据 |
|----|------|------|
| B1 | **DONE** | data-scope 12/12；矩阵文档 |
| B2 | **DONE** | SalesCreditEvaluator + 单测；**extension smoke B2-1 PASS** |
| B3 | **DONE** | V108 询价 + 一键生成 PO + **smoke B3 全 PASS** |
| B4 | **DONE** | V109 OQC 闸门 + **smoke B4 全 PASS** |
| B5 | **PARTIAL** | 补货+制造底座；完整 MRP 不在范围 |
| B6 | **DONE** | V107 发票登记 + **smoke B6 全 PASS** |
| B7 | **DONE** | WebhookPublisher + V110 配置种子 |
| B8 | **DONE** | OperationsDashboard 既有指标 |

## Track C 工程债

| ID | 状态 | 证据 |
|----|------|------|
| C1 | **DONE** | `docs/archive/2026-06-stale/` |
| C2 | **DONE** | 前端 vitest 19 全绿 |
| C3 | **DONE** | 导出按钮 v-permission |
| C6 | **DONE** | contracts 既有 |
| C7 | **DONE** | `scripts/check-seed-id-occupancy.cjs`（水位已抬到 2033/5360） |
| C4 | **PLAN_ONLY** | 巨石拆分待专项窗口 |
| C5 | **BASE_EXISTS** | CacheService 已在，热点深化待专项 |

## 本轮加固（2026-07-17 深夜）

- `fin_invoice_register` 纳入租户拦截表
- 询价：商品/供应商下拉、一键生成采购订单草稿、落败报价 REJECTED
- 客户：结算方式 + 信用额度 0=不限额 契约修复
- OQC：编辑弹窗支持出库单；ui-smoke IQC 对话框文案对齐
- V110：询价号段 company/book + `notification.webhook.url` 种子
- 新脚本：`scripts/extension-features-api-smoke.cjs` → **15/15 PASS**
- 回归入口：`scripts/local-extension-regression.ps1`（extension + data-scope，**15+12 PASS**）
- ui-smoke 新增浏览器 workflow：`finance-invoice-create-post-cancel`、`purchase-inquiry-create-and-convert-to-po`（**均 PASS**）
- **全量 `UI_SMOKE_ROUTES=0 node scripts/ui-smoke.mjs` → 36/36 PASS**（2026-07-17 深夜，`target/ui-smoke-report.json`）
- 路由 smoke 补：`/finance/invoices`、`/purchase/inquiries`、`/qc/inspections`
- `local-runtime-acceptance.ps1` 成功后自动串 extension regression

## 新迁移（需重启后端 Flyway）

- V107 发票登记
- V108 采购询价
- V109 OQC 列
- V110 Track B 收口（号段 + webhook 配置）

## 人类唯一硬阻塞

**A1 财务/质检业务签字**（技术不可代签）

## 后续专项（不阻塞签字）

- C4 巨石 Service/Vue 拆分
- C5 字典/菜单/权限 Redis Cache-Aside 深化
- B5 完整 MRP / 工序报工（产品边界升级）
- 可选：将 extension smoke 关键入 ui-smoke 浏览器 workflow（API 证据已齐）
