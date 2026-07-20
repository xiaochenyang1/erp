# 库存预警「实例生命周期」决策

**决策日期**: 2026-07-15  
**结论**: **不新建独立预警实例表**；维持「规则命中动态派生 + disposition 处置态」模型。

---

## 1. 现状（代码事实，非文档臆测）

### 后端

| 能力 | 位置 |
|------|------|
| 低库存规则 CRUD | `InventoryAlertController` `POST /api/inventory/alert-rules` |
| 命中列表（动态计算） | `GET /api/inventory/low-stock`：现存量 < `minQty` 才返回 |
| 忽略 | `POST /api/inventory/low-stock/ignore` → status `IGNORED` |
| 标记已处理 | `POST /api/inventory/low-stock/resolve` → status `RESOLVED` |
| 重新激活 | `POST /api/inventory/low-stock/reactivate` |
| 处置持久化 | 表 `inv_alert_disposition`（`InventoryAlertDispositionEntity`）按 **仓+商品** 唯一处置态 + 缺口快照 |

命中本身**不是**扫出来的历史事件行，而是查询时按规则 × 余额实时派生；处置只叠加展示状态。

### 前端

`views/inventory/alerts/index.vue` 已挂：

- 新增规则
- 标记已处理 / 忽略 / 重新激活（`v-permission="'inventory:alert:handle'"`）
- 生成补货建议
- 处置状态筛选（ACTIVE / IGNORED / RESOLVED）

`WHAT_IS_MISSING` 里「前端不再展示处理/忽略」的表述**已过时**。

---

## 2. 要不要「预警实例生命周期」？

| 方案 | 含义 | 何时需要 |
|------|------|----------|
| **A. 现状 disposition（选用）** | 仓+商品一条当前处置态；库存回升后命中自然消失；可忽略/已处理/再激活 | 运营只要「现在谁该补货、哪些我先不管」 |
| **B. 独立实例表** | 每次扫描生成 event/instance，含 OPEN→HANDLING→CLOSED 历史、幂等键、关闭原因流水 | 要审计「第 N 次预警何时关闭」、SLA、按次工单化 |

**当前产品阶段选 A**，原因：

1. 后端已具备 ignore/resolve/reactivate + 权限码，前端已接线——再造实例表是重复建设。
2. 低库存是**状态问题**不是**事件问题**：库存补上后告警应消失，而不是留下一堆 OPEN 实例要人工关。
3. 异常工单 / 补货建议已覆盖「要跟人跟单」的路径；预警页定位为规则命中看板即可。
4. B 方案要额外设计：扫描调度、幂等（同一缺口是否重复开单）、库存回升自动关闭、与 disposition 双写一致性——投入大、收益需业务明确提出。

---

## 3. 明确不做 / 可做

### 不做（除非业务书面要求）

- 新建 `inv_alert_instance` 及状态机
- 在命中查询上硬凑「关闭历史」列表冒充实例

### 可做（低成本增强，可选）

1. 给 `ui-smoke` 加一条：建规则 → 造低库存 → 忽略 → 再激活 → resolve（非必须，API 单测已覆盖核心）
2. 处置列表导出 / 处置人回显（若运营要报表）
3. 库存回升时自动清 disposition 或标 RESOLVED（体验优化）

### 何时重开 B 方案

同时满足再评估：

- 需要按「每一次跌破」计次考核或 SLA
- 需要与异常工单 1:1 自动建单且保留关闭审计
- 现有 disposition 无法回答合规抽查问题

届时 TDD：先实例表 + 扫描幂等 + 单测，再挂前端，**不要**在现有 list API 上堆状态。

---

## 4. 文档纠偏

- `docs/WHAT_IS_MISSING.md`：删除「库存预警尚无生命周期 / 勿硬凑状态」类待决表述，改为指向本文。
- 旧 6 月联调报告若仍写「预警空壳」，一律以源码与本文为准。
