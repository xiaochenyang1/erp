# Track C 进度（2026-07-17）

| ID | 项 | 状态 | 证据 |
|----|----|------|------|
| C1 | 过时文档归档 | **DONE** | `docs/archive/2026-06-stale/`（46 文件 + README） |
| C2 | 前端测试加厚 | **DONE** | vitest **17** 全绿（含新增 `sales.test.ts`） |
| C3 | 只读页导出权限 | **DONE** | ledger/receivables/payables/reports/logs/imports 导出按钮 `v-permission` |
| C6 | 契约检查 | **DONE**（既有） | 前端 `npm run check:contracts`；后端 `docs/api-conventions.md` |
| C7 | 迁移种子 id 安全网 | **DONE** | `scripts/check-seed-id-occupancy.cjs`；高水位 menu=5331 seq=2030 |
| C4 | 巨石拆分 | **PLAN_ONLY** | 热点清单见 backlog；未改业务行为，避免无回归窗口硬拆 |
| C5 | Redis 缓存深化 | **BASE_EXISTS** | `common/cache/CacheService` + Redis/Local 实现已在；热点字典/菜单深化单独立项 |

## C4 热点（下次专项）

- `PurchaseOrderService` ~840
- `SalesDeliveryService` ~794
- `FinancePostingService` ~692
- `InventoryPostingService` ~559
- 前端 `stocks/index.vue` ~1268

规则：一次只拆一个；全量相关测试绿。

## C5 后续

在 `CacheService` 上为字典/菜单/权限快照加 Cache-Aside + 主动失效，强制 key 含 company/accountBook。
