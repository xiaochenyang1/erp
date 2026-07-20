# 预生产验收记录中心设计

## 背景

当前 ERP 后端已经具备采购、销售、库存、财务、生产、审批、附件、通知、幂等和会话管理等核心能力，但上线验收证据主要沉淀在 `docs/business-readiness-checklist.md` 和人工记录中。这个方式适合说明流程，不适合追踪每次发布候选的真实验收结论。

本设计新增后端侧“预生产验收记录中心”，用于把一次发布候选的环境、验收项、接口证据、业务单号、失败原因和 Go / No-Go 结论结构化保存，避免上线前靠口头结论和零散日志凑证据。

## 目标

- 支持创建一次发布候选验收运行单，记录 commit、环境、数据库、Redis、Docker、执行人和整体状态。
- 支持在运行单下维护多个验收项，覆盖登录、采购到付款、销售到收款、财务账簿、期间锁账、库存财务对账、生产制造、期初导入、租户隔离等链路。
- 支持为每个验收项追加证据，包括接口路径、HTTP 状态、业务单号、日志片段、备注和附件引用预留字段。
- 支持单项结果和整体 Go / No-Go 决策，阻止存在 P0 / P1 失败或阻塞项时误标整体通过。
- 所有数据按 `company_id`、`account_book_id` 隔离，符合当前多租户边界。
- 提供后端 API 和自动化测试，不做前端页面。

## 非目标

- 不实现完整 QA 测试平台，不做测试用例模板版本管理、自动调度、截图比对或缺陷流转。
- 不自动执行业务链路接口；本功能只保存人工或外部自动化产生的验收结果和证据。
- 不在本期强绑定附件中心；仅预留附件业务类型和业务 ID 字段，后续可接入附件查询。
- 不改变现有发布脚本、Docker Compose 或业务模块流程。

## 模块边界

新增模块目录：

- `src/main/java/com/tuowei/erp/system/readiness`

推荐分层保持现有风格：

- `controller`：REST API 入口。
- `service`：状态流转、租户隔离、结果判定规则。
- `mapper`：MyBatis-Plus Mapper。
- `model`：数据库实体。
- `web`：请求和响应 DTO。

该模块属于系统治理能力，不依赖采购、销售、库存、财务、生产等业务服务，只保存它们的验收证据引用，避免把发布治理和业务落账逻辑拧成麻花。

## 数据模型

### `sys_readiness_run`

表示一次发布候选验收运行单。

关键字段：

- `id`
- `company_id`
- `account_book_id`
- `run_no`：运行单编号，建议由服务生成，格式可为 `RDYyyyyMMddHHmmss`。
- `release_commit`：候选 commit 或 tag。
- `release_version`：版本号或镜像 tag。
- `environment`：`PREPROD`、`STAGING`、`PROD_DRY_RUN` 等。
- `database_instance`：数据库实例说明。
- `redis_instance`：Redis 实例说明。
- `docker_profile`：Docker Compose profile 或部署形态。
- `status`：`DRAFT`、`IN_PROGRESS`、`PASSED`、`FAILED`、`BLOCKED`、`NO_GO`。
- `decision`：`GO`、`NO_GO`、`PENDING`。
- `decision_comment`
- `started_by`
- `started_time`
- `decided_by`
- `decided_time`
- 通用审计字段：`deleted_flag`、`created_by`、`created_time`、`updated_by`、`updated_time`、`version`。

索引：

- `(company_id, account_book_id, run_no)` 唯一。
- `(company_id, account_book_id, release_commit)` 查询候选提交。
- `(company_id, account_book_id, status, created_time)` 分页查询。

### `sys_readiness_item`

表示运行单下的单个验收项。

关键字段：

- `id`
- `company_id`
- `account_book_id`
- `run_id`
- `item_code`：如 `AUTH_SMOKE`、`PURCHASE_TO_PAYMENT`。
- `item_name`
- `category`：`AUTH`、`PURCHASE`、`SALES`、`FINANCE`、`INVENTORY`、`PRODUCTION`、`IMPORT`、`TENANT`、`DEPLOYMENT`。
- `priority`：`P0`、`P1`、`P2`。
- `status`：`PENDING`、`PASSED`、`FAILED`、`BLOCKED`、`SKIPPED`。
- `expected_result`
- `actual_result`
- `failure_reason`
- `executed_by`
- `executed_time`
- 通用审计字段。

索引：

- `(company_id, account_book_id, run_id, priority, status)` 用于整体决策校验。
- `(company_id, account_book_id, item_code, created_time)` 用于历史查询。

### `sys_readiness_evidence`

表示验收项下的证据记录。

关键字段：

- `id`
- `company_id`
- `account_book_id`
- `run_id`
- `item_id`
- `evidence_type`：`API`、`BUSINESS_NO`、`LOG`、`SCREENSHOT`、`NOTE`、`ATTACHMENT`。
- `request_method`
- `request_uri`
- `http_status`
- `business_type`
- `business_id`
- `business_no`
- `summary`
- `detail`
- `attachment_business_type`
- `attachment_business_id`
- `recorded_by`
- `recorded_time`
- 通用审计字段。

索引：

- `(company_id, account_book_id, run_id, item_id, recorded_time)` 详情查询。
- `(company_id, account_book_id, business_type, business_id)` 业务证据反查预留。

## 状态流转

运行单：

- `DRAFT` 可编辑，可新增验收项和证据。
- `IN_PROGRESS` 可新增验收项和证据，可更新单项结果。
- `PASSED` 表示整体 Go，禁止继续修改验收项和证据。
- `FAILED` 表示验收失败但不一定阻塞发布，可由决策人转为 `NO_GO`。
- `BLOCKED` 表示验收被环境、数据或依赖阻塞。
- `NO_GO` 表示明确不可发布。

验收项：

- 默认 `PENDING`。
- 可标记为 `PASSED`、`FAILED`、`BLOCKED`、`SKIPPED`。
- `P0` 不允许 `SKIPPED`。
- `P1` 标记 `SKIPPED` 时必须填写原因。

整体决策：

- 当存在 `P0` 或 `P1` 的 `FAILED` / `BLOCKED` 项时，运行单不能标记为 `PASSED` 或 `GO`。
- `NO_GO` 必须填写决策说明。
- 运行单关闭后禁止新增证据，防止发布后补证据糊墙。

## API 设计

### 创建运行单

`POST /api/system/readiness/runs`

请求字段：

- `releaseCommit`
- `releaseVersion`
- `environment`
- `databaseInstance`
- `redisInstance`
- `dockerProfile`
- `remark`

返回运行单详情。

### 分页查询运行单

`GET /api/system/readiness/runs`

查询字段：

- `releaseCommit`
- `environment`
- `status`
- `decision`
- `createdTimeFrom`
- `createdTimeTo`
- `pageNo`
- `pageSize`

### 查看运行单详情

`GET /api/system/readiness/runs/{id}`

返回运行单、验收项列表和每项证据摘要。

### 添加验收项

`POST /api/system/readiness/runs/{id}/items`

请求字段：

- `itemCode`
- `itemName`
- `category`
- `priority`
- `expectedResult`

### 添加证据

`POST /api/system/readiness/items/{itemId}/evidence`

请求字段：

- `evidenceType`
- `requestMethod`
- `requestUri`
- `httpStatus`
- `businessType`
- `businessId`
- `businessNo`
- `summary`
- `detail`
- `attachmentBusinessType`
- `attachmentBusinessId`

### 标记单项结果

`POST /api/system/readiness/items/{itemId}/result`

请求字段：

- `status`
- `actualResult`
- `failureReason`

### 整体决策

`POST /api/system/readiness/runs/{id}/decision`

请求字段：

- `decision`：`GO` 或 `NO_GO`
- `status`：`PASSED`、`FAILED`、`BLOCKED`、`NO_GO`
- `decisionComment`

## 权限

新增权限码：

- `system:readiness:view`：查看运行单、验收项和证据。
- `system:readiness:manage`：创建运行单、维护验收项、追加证据、标记单项结果。
- `system:readiness:decide`：做整体 Go / No-Go 决策。

接口权限：

- 查询和详情使用 `system:readiness:view`。
- 创建、添加项、添加证据、标记结果使用 `system:readiness:manage`。
- 整体决策使用 `system:readiness:decide`。

迁移脚本需要写入 `sys_menu` 和默认管理员角色绑定，保持现有权限初始化风格。

## 租户隔离

所有写入使用当前 `AuditMetadataFactory` 的 `companyId` 和 `accountBookId`。

所有查询必须按当前租户过滤：

- 运行单按 `company_id`、`account_book_id` 查询。
- 验收项和证据必须通过所属运行单或自身租户字段双重约束。
- 跨租户访问详情、追加证据、标记结果、做决策均返回业务错误或空结果，不能只依赖前端藏按钮。

## 错误处理

统一抛出 `IllegalArgumentException` 表达业务规则错误，沿用现有全局异常处理。

主要错误：

- `验收运行单不存在`
- `验收项不存在`
- `已关闭的验收运行单不能修改`
- `P0 验收项不能跳过`
- `存在未通过的 P0/P1 验收项，不能标记发布通过`
- `No-Go 决策说明不能为空`

## 测试策略

新增 `ReadinessControllerTest`，覆盖：

- 创建运行单并分页查询。
- 添加验收项和证据，详情能返回结构化数据。
- 标记 P0/P1 失败后，整体决策不能标记 `GO`。
- `NO_GO` 决策必须填写说明。
- 已关闭运行单禁止新增证据。
- 不同 `company_id` / `account_book_id` 的用户不能访问对方运行单。
- 缺少权限时接口返回 `403`。

新增或扩展 Flyway smoke 测试，确保新迁移能在 H2 下执行。

## 验收标准

- 后端提供完整运行单、验收项、证据、决策 API。
- 数据结构能覆盖 `docs/business-readiness-checklist.md` 中上线记录模板的核心字段。
- P0/P1 失败或阻塞时不能给出 Go 结论。
- 租户隔离通过自动化测试证明。
- 全量 `.\mvnw.cmd test` 通过。
