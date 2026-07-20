# 生产工艺路线与工作中心一期设计

日期：2026-07-08

## 背景

当前系统已经具备 `BOM + 生产工单 + 下达/领料/完工/退料` 的轻量制造执行闭环，生产核心链路已经能跑通。但生产模块仍停留在“工单执行层”，没有工艺路线、工序、工作中心这些制造主数据，后续如果要继续往报工、排产、工序在制、质检闭环推进，基础模型就会缺口明显。

第一期目标不是一次性补齐完整 MES，也不是把生产工单逻辑全部重做，而是先补上最基础、最稳定、最容易复用的制造主数据层：`工作中心 + 工艺路线 + 工序明细`。这层先立稳，后面再做工单展开工序、报工、排产，才不至于边做边拆。

## 目标

- 新增工作中心主数据能力，支持创建、查询、更新、启用、停用。
- 新增工艺路线能力，支持按 `BOM` 维护一条工艺路线及多道工序。
- 工艺路线工序支持绑定工作中心，并维护标准工时分钟数。
- 所有数据继续受 `company_id + account_book_id` 约束，复用现有租户隔离模式。
- 新增菜单、权限、数据库迁移和自动化测试，和现有生产模块风格保持一致。

## 非目标

- 不做生产工单下达时自动展开工序。
- 不做班组、设备、人员派工。
- 不做工序报工、暂停、完工、返工、报废。
- 不做排产、产能平衡、APS。
- 不做工艺路线版本管理。
- 不做多条启用路线同时挂同一 `BOM`。
- 不做前端页面和路由。
- 不做导出能力。
- 不引入新的 sequence 规则，工作中心编码和工艺路线编码第一期由用户手工维护。

## 方案选择

本期采用“独立 `work-center` / `routing` 模块，工艺路线绑定 `BOM`”的方案。

不把工艺路线直接塞进现有 `BOM` 模块，也不直接绑定到 `product`：

- 绑定 `BOM` 更贴合当前生产工单以 `bomId` 为入口的链路，后续工单扩展工序时不需要额外做 `product -> bom -> routing` 二次映射。
- 独立模块能把工艺主数据和物料清单主数据分开，避免 `BOM` 服务继续膨胀。
- 第一版约束为“一个 `BOM` 对应一条路线”，先把边界收紧，后面真要上版本管理再单独设计，不拿第一期试图一步登天。

## 数据模型

建议新增两份迁移：

- `V92__production_routing_work_center_schema.sql`
- `V93__production_routing_work_center_menu_seed.sql`

### `prd_work_center`

- `id`
- `company_id`
- `account_book_id`
- `work_center_code`
- `work_center_name`
- `status`: `ACTIVE` / `DISABLED`
- `deleted_flag`
- `remark`
- `created_by`
- `created_time`
- `updated_by`
- `updated_time`
- `version`

约束与索引：

- `company_id + account_book_id + work_center_code` 唯一。
- `company_id + account_book_id + status` 索引。
- `deleted_flag` 默认 `0`。
- 第一版无删除接口，但表仍保留 `deleted_flag`，和现有主数据表保持一致。

### `prd_routing`

- `id`
- `company_id`
- `account_book_id`
- `routing_code`
- `routing_name`
- `bom_id`
- `status`: `ACTIVE` / `DISABLED`
- `deleted_flag`
- `remark`
- `created_by`
- `created_time`
- `updated_by`
- `updated_time`
- `version`

约束与索引：

- `company_id + account_book_id + routing_code` 唯一。
- `company_id + account_book_id + bom_id` 唯一，确保第一期一个 `BOM` 只能挂一条路线。
- `company_id + account_book_id + status` 索引。
- `deleted_flag` 默认 `0`。

### `prd_routing_operation`

- `id`
- `company_id`
- `account_book_id`
- `routing_id`
- `line_no`
- `operation_code`
- `operation_name`
- `work_center_id`
- `standard_minutes`
- `remark`
- `created_by`
- `created_time`
- `updated_by`
- `updated_time`
- `version`

约束与索引：

- `company_id + account_book_id + routing_id + line_no` 唯一。
- `company_id + account_book_id + routing_id + operation_code` 唯一。
- `company_id + account_book_id + work_center_id` 索引。
- `standard_minutes` 使用 `DECIMAL(18, 2)`，必须大于 `0`。
- 第一版工序顺序以后端重排后的 `line_no` 为准，不依赖前端传入顺序号。

## 模块设计

### `production/workcenter`

新增目录：

- `controller`
- `service`
- `mapper`
- `model`
- `web`

`ProductionWorkCenterService` 职责：

- 创建工作中心。
- 更新工作中心基础资料。
- 查询工作中心列表和详情。
- 启用、停用工作中心。
- 校验编码唯一、状态合法、租户归属正确。
- 停用前检查是否仍被启用中的工艺路线引用；若被引用则拒绝停用。

### `production/routing`

新增目录：

- `controller`
- `service`
- `mapper`
- `model`
- `web`

`ProductionRoutingService` 职责：

- 创建工艺路线。
- 更新工艺路线头和工序明细。
- 查询工艺路线列表和详情。
- 启用、停用工艺路线。
- 校验 `BOM` 存在、启用且属于当前账套。
- 校验工序至少一条、工序编码在同一路线内不重复、标准工时大于 `0`。
- 校验工序绑定的工作中心存在、启用且属于当前账套。
- 更新时先更新路线头，再全量替换工序明细，复用现有 `BOM` 行更新的简单模式。

## API 设计

### 工作中心

- `GET /api/production/work-centers`
  - 查询列表。
  - 查询条件：`pageNo`、`pageSize`、`keyword`、`status`。
  - 权限：`production:work-center:view`。

- `GET /api/production/work-centers/{id}`
  - 查询详情。
  - 权限：`production:work-center:view`。

- `POST /api/production/work-centers`
  - 创建工作中心。
  - 权限：`production:work-center:create`。

- `PUT /api/production/work-centers/{id}`
  - 更新工作中心。
  - 权限：`production:work-center:update`。

- `POST /api/production/work-centers/{id}/enable`
  - 启用工作中心。
  - 权限：`production:work-center:enable`。

- `POST /api/production/work-centers/{id}/disable`
  - 停用工作中心。
  - 权限：`production:work-center:disable`。

### 工艺路线

- `GET /api/production/routings`
  - 查询列表。
  - 查询条件：`pageNo`、`pageSize`、`keyword`、`status`、`bomId`。
  - 权限：`production:routing:view`。

- `GET /api/production/routings/{id}`
  - 查询详情。
  - 权限：`production:routing:view`。

- `POST /api/production/routings`
  - 创建工艺路线。
  - 权限：`production:routing:create`。

- `PUT /api/production/routings/{id}`
  - 更新工艺路线。
  - 权限：`production:routing:update`。

- `POST /api/production/routings/{id}/enable`
  - 启用工艺路线。
  - 权限：`production:routing:enable`。

- `POST /api/production/routings/{id}/disable`
  - 停用工艺路线。
  - 权限：`production:routing:disable`。

本期不新增“按 `BOM` 单独查询详情”的专用接口。需要按 `bomId` 获取路线时，直接走 `GET /api/production/routings?bomId=...`。

## 请求与响应模型

### 工作中心请求

`ProductionWorkCenterCreateRequest`：

- `workCenterCode`
- `workCenterName`
- `remark`

`ProductionWorkCenterUpdateRequest`：

- `workCenterName`
- `remark`

约束：

- `workCenterCode` 在创建时必填、去首尾空格后不能为空。
- `workCenterName` 在创建和更新时都必填、去首尾空格后不能为空。
- `remark` 可空。

### 工作中心响应

`ProductionWorkCenterResponse`：

- `id`
- `workCenterCode`
- `workCenterName`
- `status`
- `remark`

### 工艺路线请求

`ProductionRoutingCreateRequest`：

- `routingCode`
- `routingName`
- `bomId`
- `remark`
- `operations`

`ProductionRoutingUpdateRequest`：

- `routingName`
- `remark`
- `operations`

`ProductionRoutingOperationRequest`：

- `operationCode`
- `operationName`
- `workCenterId`
- `standardMinutes`
- `remark`

约束：

- `routingCode`、`routingName`、`bomId` 在创建时必填。
- `routingName` 在更新时必填。
- `operations` 在创建和更新时都至少一条。
- `operationCode`、`operationName` 必填。
- `workCenterId` 必填。
- `standardMinutes > 0`。
- 后端按请求顺序重排 `lineNo`，不信任前端传入的序号。

### 工艺路线响应

`ProductionRoutingResponse`：

- `id`
- `routingCode`
- `routingName`
- `bomId`
- `bomNo`
- `productId`
- `status`
- `remark`
- `operations`

`ProductionRoutingOperationResponse`：

- `id`
- `lineNo`
- `operationCode`
- `operationName`
- `workCenterId`
- `workCenterCode`
- `workCenterName`
- `standardMinutes`
- `remark`

## 业务规则

### 工作中心

- 创建默认状态为 `ACTIVE`。
- 更新不允许改编码，只允许改名称和备注，避免编码一改牵出一串引用关系。
- 停用时若存在启用中的工艺路线工序引用当前工作中心，则返回业务错误，不做级联停用。
- 启用和停用都必须校验租户归属。

### 工艺路线

- 创建默认状态为 `ACTIVE`。
- 更新不允许改 `routingCode` 和 `bomId`，只允许改名称、备注和工序明细。
- 第一版一个 `BOM` 只允许存在一条路线；重复创建返回业务错误。
- 路线工序必须至少一条。
- 同一路线内 `operationCode` 不可重复。
- 工序引用的工作中心必须是当前账套、启用状态、未删除。
- 停用路线不影响历史数据展示；本期因为工艺路线还未接入工单，不做更多联动校验。

### 租户与数据边界

- `list/detail/update/enable/disable` 都必须显式带上 `company_id + account_book_id + deleted_flag = 0` 过滤。
- 引用校验必须拦住“同公司不同账簿”这种假同租户数据。
- 工艺路线引用 `BOM`、工序引用工作中心时，都必须按当前审计账套校验，不能只看主键存在。

## 权限与菜单

新增权限常量：

- `production:work-center:view`
- `production:work-center:create`
- `production:work-center:update`
- `production:work-center:enable`
- `production:work-center:disable`
- `production:routing:view`
- `production:routing:create`
- `production:routing:update`
- `production:routing:enable`
- `production:routing:disable`

对应新增 `HAS_*` 表达式，继续挂到 `ProductionPermissionCodes` / `PermissionCodes`。

菜单建议挂在现有 `生产管理`（`id=5080`）下，采用新号段避免和现有 `5081-5089` 冲突：

- `5090`：`工作中心`
- `5091-5094`：工作中心按钮权限
- `5095`：`工艺路线`
- `5096-5099`：工艺路线按钮权限

`ERP_ADMIN` 默认授予上述菜单和按钮权限，延续现有生产模块 seed 风格。

## 错误处理

建议沿用现有生产模块的 `IllegalArgumentException` / `BusinessConflictException` 风格，不在本期扩展新的异常体系。

典型错误：

- 工作中心不存在：`工作中心不存在`
- 工作中心停用引用冲突：`工作中心已被启用工艺路线引用，不能停用`
- `BOM` 不存在或已停用：`BOM不存在或已停用`
- 工艺路线重复绑定 `BOM`：`当前BOM已存在工艺路线`
- 工序为空：`工艺路线至少需要一道工序`
- 工序编码重复：`工序编码不能重复`
- 标准工时非法：`标准工时必须大于0`

## 测试范围

新增测试至少覆盖三层。

### `ServiceTest`

- 创建工作中心成功。
- 工作中心编码重复被拒绝。
- 停用被启用路线引用的工作中心被拒绝。
- 创建工艺路线成功，并按请求顺序回写 `lineNo`。
- 同一 `BOM` 重复创建路线被拒绝。
- 路线工序为空被拒绝。
- 路线工序编码重复被拒绝。
- 路线引用停用工作中心被拒绝。
- 更新路线全量替换工序后详情正确。

### `TenantBoundaryTest`

- 列表查询 SQL 片段包含 `company_id` 和 `account_book_id`。
- 详情/更新/停用查询带租户和账簿过滤。
- 路线引用同公司不同账簿 `BOM` 被拒绝。
- 工序引用同公司不同账簿工作中心被拒绝。

### `ControllerTest`

- `POST /api/production/work-centers` + `GET /api/production/work-centers/{id}` happy path。
- `POST /api/production/routings` + `GET /api/production/routings/{id}` happy path。
- `POST /api/production/work-centers/{id}/disable` 冲突场景返回 4xx。
- `GET /api/production/routings?bomId=...` 能按 `BOM` 过滤结果。

## 交付顺序

1. 新增数据库迁移和菜单 seed。
2. 新增实体、Mapper、DTO、权限常量。
3. 新增工作中心 service/controller 和测试。
4. 新增工艺路线 service/controller 和测试。
5. 运行专项测试与全量回归。

## 风险与约束

- 当前仓库已有未提交改动，实施时只动新增的 `workcenter` / `routing` 模块及必要的权限、迁移、菜单文件，别顺手搅其他模块。
- 第一版路线绑定 `BOM` 且一个 `BOM` 只能一条路线，后续如果要做版本管理，需要单独重设计，不要偷偷在现有表上叠条件。
- 本期不改现有工单接口，避免把“主数据建设”和“执行流程改造”混在一次交付里，最后俩都做不瓷实。
