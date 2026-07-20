# 生产制造最小闭环设计

## 背景

当前后端已经具备基础资料、采购、销售、库存、库存预占、轻量财务、报表和初始导入能力。系统可以完成进销存和资金往来主链路，但还没有生产制造业务。继续扩业务深度时，下一步应把系统从“进销存 ERP”推进到“小型制造 ERP”。

第一版生产制造不追求完整 MES，不做排产、工序、设备、工时和复杂成本核算。目标是先打通最核心事实链路：根据 BOM 创建生产工单，释放工单后预占材料库存，领料时扣减原材料库存，完工时增加成品库存，并让单据状态、库存流水和库存预占能够追溯。

## 目标

- 提供成品 BOM 管理能力。
- 支持创建、查询、更新、释放、作废生产工单。
- 工单释放时按 BOM 展开材料需求并预占原材料库存。
- 支持一次性生产领料，领料后扣减原材料库存并释放对应预占。
- 支持一次性完工入库，入库后增加成品库存。
- 生产领料出库成本作为第一版完工入库金额来源。
- 所有事实写入复用现有库存服务，避免生产模块直接改库存余额。
- 生产单据受公司、账簿和仓库数据范围约束。

## 非目标

- 不做多版本 BOM。
- 不做替代料。
- 不做半成品递归 BOM 展开。
- 不做工序路线、派工、设备、工时。
- 不做生产排程。
- 不做多次领料或多次完工。
- 不做生产退料。
- 不做生产报废。
- 不做成本差异分摊。
- 不生成财务凭证。
- 不引入数据库触发器处理库存。

## 方案选择

采用“独立生产模块 + 复用库存预占与库存落账”的方案。

新增 `production` 模块，包含 BOM、生产工单、工单材料行和生产动作服务。生产模块只维护生产业务状态和需求明细，库存数量变化统一通过 `InventoryPostingService` 完成：

- 工单释放：调用 `reserve`，来源类型使用 `PRODUCTION_ORDER`，来源行使用工单材料行 id。
- 领料：先调用 `releaseReservation` 释放材料预占，再调用 `postOutbound` 扣减原材料库存。
- 完工：调用 `postInbound` 增加成品库存。

这个方案和现有销售订单预占模型一致，能少造一套库存逻辑。生产模块如果自己直接改 `inv_balance`，那就是在库存系统旁边又架一套野路子库存，后面查账能把人查冒烟。

## 数据模型

新增迁移建议命名为 `V38__production_manufacturing_schema.sql`。如果会计期间锁账先落地，则本迁移版本号顺延。

### `prd_bom`

- `id`
- `company_id`
- `account_book_id`
- `bom_no`
- `product_id`
- `product_qty`
- `status`: `ACTIVE` 或 `DISABLED`
- `remark`
- `created_by`
- `created_time`
- `updated_by`
- `updated_time`
- `version`

约束与索引：

- `company_id + bom_no` 唯一。
- `company_id + product_id + status` 索引。
- 第一版同一公司同一成品只允许一个启用 BOM。

### `prd_bom_line`

- `id`
- `bom_id`
- `line_no`
- `material_product_id`
- `material_qty`
- `loss_rate`
- `remark`
- `created_by`
- `created_time`
- `updated_by`
- `updated_time`
- `version`

约束与索引：

- `bom_id + line_no` 唯一。
- `bom_id + material_product_id` 索引。
- `material_qty` 必须大于 0。
- `loss_rate` 默认 0，不能小于 0。

### `prd_order`

- `id`
- `company_id`
- `account_book_id`
- `order_no`
- `bom_id`
- `product_id`
- `planned_qty`
- `completed_qty`
- `material_warehouse_id`
- `finished_warehouse_id`
- `order_date`
- `planned_start_date`
- `planned_finish_date`
- `status`: `DRAFT`、`RELEASED`、`MATERIAL_ISSUED`、`COMPLETED`、`CANCELLED`
- `issued_amount`
- `finished_amount`
- `remark`
- `created_by`
- `created_time`
- `updated_by`
- `updated_time`
- `version`

约束与索引：

- `company_id + order_no` 唯一。
- `company_id + status + order_date` 索引。
- `company_id + material_warehouse_id` 索引。
- `company_id + finished_warehouse_id` 索引。

### `prd_order_material`

- `id`
- `order_id`
- `line_no`
- `material_product_id`
- `required_qty`
- `issued_qty`
- `issued_amount`
- `remark`
- `created_by`
- `created_time`
- `updated_by`
- `updated_time`
- `version`

约束与索引：

- `order_id + line_no` 唯一。
- `order_id + material_product_id` 索引。
- `required_qty` 必须大于 0。
- `issued_qty` 默认 0。

## 模块设计

### `ProductionBomService`

职责：

- 创建 BOM。
- 更新未被工单引用的 BOM。
- 启用和停用 BOM。
- 查询 BOM 列表和详情。
- 校验成品、材料均属于当前公司且处于启用状态。
- 校验 BOM 行材料不能重复，材料不能和成品相同。
- 启用 BOM 时停用或拒绝同一成品的其他启用 BOM。第一版建议拒绝，让用户明确处理，不偷偷改其他 BOM。

### `ProductionOrderService`

职责：

- 基于启用 BOM 创建生产工单。
- 创建时展开 BOM 行，按 `plannedQty / bom.productQty * materialQty * (1 + lossRate)` 计算材料需求。
- 草稿工单允许修改计划数量、仓库、日期和备注，并重新展开材料需求。
- 释放工单时按材料需求调用库存预占。
- 作废草稿或已释放未领料工单。已释放工单作废时释放全部材料预占。
- 查询工单列表和详情。

状态规则：

- `DRAFT` 可编辑、释放、作废。
- `RELEASED` 表示材料已预占，可领料或作废。
- `MATERIAL_ISSUED` 表示材料已出库，只能完工。
- `COMPLETED` 表示成品已入库，不能再修改。
- `CANCELLED` 表示单据作废，不能再执行业务动作。

### `ProductionIssueService`

职责：

- 对 `RELEASED` 工单执行一次性领料。
- 对每条材料行调用 `releaseReservation("PRODUCTION_ORDER", materialLineId, requiredQty, audit)`。
- 随后调用 `postOutbound` 扣减原材料库存。
- 汇总每行出库成本写入 `prd_order_material.issued_amount`。
- 汇总总成本写入 `prd_order.issued_amount`。
- 更新工单状态为 `MATERIAL_ISSUED`。
- 重复领料返回业务错误，不做静默成功。

领料库存流水：

- `bizType`: `PRODUCTION_ISSUE`
- `bizNo`: 生产工单号
- `bizLineId`: 工单材料行 id
- `direction`: `OUT`

### `ProductionCompletionService`

职责：

- 对 `MATERIAL_ISSUED` 工单执行一次性完工入库。
- 完工数量第一版等于计划数量，不允许部分完工。
- 入库金额使用工单 `issued_amount`。
- 调用 `postInbound` 增加成品库存。
- 写入 `completed_qty`、`finished_amount` 并更新状态为 `COMPLETED`。
- 重复完工返回业务错误，不做静默成功。

完工库存流水：

- `bizType`: `PRODUCTION_COMPLETION`
- `bizNo`: 生产工单号
- `bizLineId`: 生产工单 id
- `direction`: `IN`

## API 设计

### BOM

- `GET /api/production/boms`
  - 查询 BOM 列表。
  - 权限：`production:bom:view`。

- `GET /api/production/boms/{id}`
  - 查询 BOM 详情。
  - 权限：`production:bom:view`。

- `POST /api/production/boms`
  - 创建 BOM。
  - 权限：`production:bom:manage`。

- `PUT /api/production/boms/{id}`
  - 更新 BOM。
  - 权限：`production:bom:manage`。

- `POST /api/production/boms/{id}/enable`
  - 启用 BOM。
  - 权限：`production:bom:manage`。

- `POST /api/production/boms/{id}/disable`
  - 停用 BOM。
  - 权限：`production:bom:manage`。

### 生产工单

- `GET /api/production/orders`
  - 查询工单列表。
  - 权限：`production:order:view`。

- `GET /api/production/orders/{id}`
  - 查询工单详情，包含材料需求行和库存预占摘要。
  - 权限：`production:order:view`。

- `POST /api/production/orders`
  - 创建工单。
  - 权限：`production:order:create`。

- `PUT /api/production/orders/{id}`
  - 更新草稿工单。
  - 权限：`production:order:update`。

- `POST /api/production/orders/{id}/release`
  - 释放工单并预占材料。
  - 权限：`production:order:release`。

- `POST /api/production/orders/{id}/issue`
  - 一次性生产领料。
  - 权限：`production:order:issue`。

- `POST /api/production/orders/{id}/complete`
  - 一次性完工入库。
  - 权限：`production:order:complete`。

- `POST /api/production/orders/{id}/cancel`
  - 作废工单。
  - 权限：`production:order:cancel`。

新增权限码：

- `production:bom:view`
- `production:bom:manage`
- `production:order:view`
- `production:order:create`
- `production:order:update`
- `production:order:release`
- `production:order:issue`
- `production:order:complete`
- `production:order:cancel`

## 数据流

1. 用户创建成品 BOM，维护材料和用量。
2. 用户基于启用 BOM 创建生产工单。
3. 服务按计划生产数量展开材料需求，写入工单材料行。
4. 用户释放工单。
5. 服务逐行预占材料库存，工单状态变为 `RELEASED`。
6. 用户执行领料。
7. 服务释放材料预占并扣减原材料库存，记录材料出库成本，工单状态变为 `MATERIAL_ISSUED`。
8. 用户执行完工。
9. 服务按材料出库总成本增加成品库存，工单状态变为 `COMPLETED`。

## 数据权限与租户边界

- 所有查询必须限定当前 `companyId`。
- 创建 BOM 和工单时，成品、材料、领料仓、完工仓必须属于当前公司。
- 工单列表和详情需要接入现有仓库数据范围。第一版以 `materialWarehouseId` 和 `finishedWarehouseId` 两个仓库共同约束：非全量仓库权限用户必须同时具备两个仓库访问权，或满足自建数据范围。
- 库存预占、领料和完工继续使用当前用户的审计上下文。
- 禁止跨公司引用 BOM、商品、仓库和库存预占。

## 错误处理

- BOM 不存在、停用或跨公司：返回业务错误。
- BOM 没有材料行：禁止启用和创建工单。
- BOM 行材料重复：拒绝保存。
- 工单状态不匹配：返回明确动作错误。
- 材料库存可用量不足：工单释放失败，不产生部分预占。
- 领料时预占不存在或不足：拒绝领料。
- 原材料库存不足：拒绝领料。
- 完工前未领料：拒绝完工。
- 重复领料、重复完工：返回业务错误。
- 乐观锁更新失败：沿用现有并发冲突错误。

## 测试计划

新增最小自动化测试覆盖：

- 创建 BOM 成功，材料需求行按行号保存。
- BOM 拒绝重复材料、停用商品、跨公司商品。
- 启用 BOM 时拒绝同一成品已有启用 BOM。
- 创建工单时按 BOM 和计划数量展开材料需求。
- 更新草稿工单后重新展开材料需求。
- 释放工单后创建 `PRODUCTION_ORDER` 来源的库存预占。
- 材料库存不足时释放失败，且不产生部分预占。
- 已释放未领料工单作废时释放材料预占。
- 领料扣减原材料库存，释放对应预占，写入 `PRODUCTION_ISSUE` 库存流水。
- 领料后工单状态变为 `MATERIAL_ISSUED`，重复领料失败。
- 完工入库增加成品库存，写入 `PRODUCTION_COMPLETION` 库存流水。
- 完工金额等于材料领料出库成本合计。
- 完工后工单状态变为 `COMPLETED`，重复完工失败。
- 工单详情受公司和仓库数据范围限制。
- `scripts/release-check.ps1` 必须通过。

## 后续扩展

第一版稳定后，再按业务价值追加：

1. 生产退料。
2. 生产报废。
3. 分批领料和分批完工。
4. 多版本 BOM 和默认版本。
5. 半成品递归 BOM 展开。
6. 工序路线和报工。
7. 生产成本差异和财务凭证。

这些功能不能混进第一版。第一版要先把库存事实链打稳，否则后面成本核算就是在沙地上修高铁。
