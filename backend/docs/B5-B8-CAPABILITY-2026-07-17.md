# B5 / B8 能力确认（2026-07-17）

## B5 生产深化轻量 · PARTIAL_DONE（用现有能力收口）

| 已有 | 入口 |
|------|------|
| 补货建议 | `inventory/replenishment/**`、前端 `views/inventory/replenishment-suggestions` |
| BOM/工单/领料/完工 | `production/order/**`、`views/production/orders` |
| 工艺/工作中心主数据 | `production/routing`、`workcenter` |

**明确未做（需独立立项）**: 完整 MRP、工序报工、排产甘特。  
本期 B5 验收口径：补货建议 + 现有制造闭环 smoke 即视为「轻量深化已具备底座」。

## B8 经营看板 · DONE

| 指标 | 来源 |
|------|------|
| 今日采购订单、今日销售金额 | `OperationsDashboardService` |
| 未结应收/应付笔数与金额 | 同上 |
| 待审批、低库存、逾期应收待办 | todos + lowStock preview |
| API | `GET /api/dashboard/operations` |
| 前端 | `views/dashboard/index.vue` |

增强空间（非阻塞）：账龄分段、TOP SKU——可后续只读聚合，不改主链路。
