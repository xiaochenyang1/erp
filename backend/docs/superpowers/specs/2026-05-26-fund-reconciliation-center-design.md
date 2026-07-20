# 资金账户与银行流水对账中心设计

## 背景

当前 ERP 后端已经具备应收、应付、收款、付款、凭证、期间锁账和库存财务对账能力。收付款单能够核销应收应付，期间关闭前也会检查库存财务、凭证和核销金额一致性。

但系统缺少银行侧资金证据：收款单表示业务确认收款，付款单表示业务确认付款，银行流水才表示真实资金到账或出账。如果没有银行流水匹配，财务闭环仍停留在“账上说有钱”，不具备银行对账证据。这个缺口不补，月结时很容易出现账务挺整齐、银行压根对不上的尴尬局面。

本设计新增后端侧“资金账户与银行流水对账中心”，第一版只做可验收的最小闭环：资金账户维护、手工银行流水、手工匹配收付款、取消匹配、月结阻断未对账流水。

## 目标

- 支持维护资金账户，包括银行账户和现金账户。
- 支持手工录入银行流水，记录交易日期、方向、金额、对方户名、摘要和银行交易流水号。
- 支持银行流水匹配已过账收款单或付款单。
- 支持取消匹配，解除银行流水与业务单据的资金证据关联。
- 支持按租户和账簿隔离资金账户、银行流水和匹配操作。
- 支持会计期间关闭前检查期间内未匹配银行流水，并阻断关账。
- 提供后端 API 和自动化测试，不做前端页面。

## 非目标

- 不接银行直连接口。
- 不实现 CSV、Excel 或网银文件导入。
- 不做自动匹配、模糊匹配或批量匹配。
- 不引入多币种汇兑损益。
- 不在匹配银行流水时修改应收、应付、收款单、付款单或凭证事实。
- 不做复杂银行余额调节表，第一版只提供流水和业务单据的一对一匹配闭环。

## 设计原则

银行流水匹配是资金证据，不是业务核销事实。

现有收款单和付款单创建即 `POSTED`，并已经修改应收应付的 `settled_amount`。因此资金对账模块不能反向修改核销金额。否则同一个业务事实会被两个模块写，后面查账就成大型找茬游戏。

第一版采用一条银行流水匹配一张业务单据：

- `IN` 方向流水只能匹配 `POSTED` 收款单。
- `OUT` 方向流水只能匹配 `POSTED` 付款单。
- 流水金额必须等于业务单据金额。
- 已 `CANCELLED` 的收款单或付款单不能匹配。
- 已匹配流水不能重复匹配，必须先取消匹配。
- 取消匹配只解除银行流水关联，不改变业务单据状态和核销金额。

## 模块边界

新增模块目录：

- `src/main/java/com/tuowei/erp/finance/fund`

推荐分层保持现有风格：

- `controller`：REST API 入口。
- `service`：账户状态、流水录入、匹配规则、取消匹配、租户隔离。
- `mapper`：MyBatis-Plus Mapper。
- `model`：数据库实体。
- `web`：请求和响应 DTO。

该模块依赖现有收款、付款和期间锁账模块：

- 读取 `fin_receipt` 校验收款单状态、金额、租户和账簿。
- 读取 `fin_payment` 校验付款单状态、金额、租户和账簿。
- 扩展 `AccountPeriodCloseChecker`，在期间关闭前检查未匹配银行流水。

该模块不依赖应收、应付、凭证和库存服务，也不改它们的数据。

## 数据模型

### `fin_fund_account`

表示资金账户。

关键字段：

- `id`
- `company_id`
- `account_book_id`
- `account_code`：账户编码，租户账簿内唯一。
- `account_name`：账户名称。
- `account_type`：`BANK`、`CASH`。
- `bank_name`：银行名称，现金账户可为空。
- `bank_account_no`：银行账号，现金账户可为空。
- `currency_code`：币种，第一版默认 `CNY`。
- `opening_balance`：期初余额。
- `status`：`ENABLED`、`DISABLED`。
- `remark`
- 通用审计字段：`deleted_flag`、`created_by`、`created_time`、`updated_by`、`updated_time`、`version`。

索引：

- `(company_id, account_book_id, account_code)` 唯一。
- `(company_id, account_book_id, status, account_type)` 用于账户列表。

### `fin_bank_statement`

表示银行或现金流水。

关键字段：

- `id`
- `company_id`
- `account_book_id`
- `fund_account_id`
- `statement_no`：系统流水编号，建议服务生成。
- `external_txn_no`：银行交易流水号，允许为空。
- `transaction_date`
- `direction`：`IN`、`OUT`。
- `amount`
- `counterparty_name`
- `summary`
- `status`：`UNMATCHED`、`MATCHED`、`CANCELLED`。
- `matched_biz_type`：`RECEIPT`、`PAYMENT`，未匹配为空。
- `matched_biz_id`
- `matched_biz_no`
- `matched_time`
- `matched_by`
- `unmatch_reason`
- `remark`
- 通用审计字段。

索引：

- `(company_id, account_book_id, fund_account_id, transaction_date)` 用于账户流水查询。
- `(company_id, account_book_id, status, transaction_date)` 用于未对账查询和月结检查。
- `(company_id, account_book_id, matched_biz_type, matched_biz_id)` 用于业务单据反查。
- `(company_id, account_book_id, external_txn_no)` 非唯一普通索引，避免银行流水号为空或不同银行重复导致误伤。

## 状态流转

资金账户：

- `ENABLED` 可录入流水和用于查询。
- `DISABLED` 不允许新增流水，但历史流水仍可查询和取消匹配。

银行流水：

- `UNMATCHED`：已录入，未匹配业务单据。
- `MATCHED`：已匹配收款单或付款单。
- `CANCELLED`：流水录错后作废，第一版可作为预留状态；已匹配流水必须先取消匹配再作废。

第一版不做删除。录错数据应通过作废保留审计痕迹，别一删了之。财务系统里“删了就当没发生”这种活儿，早晚得挨锤。

## API 设计

### 创建资金账户

`POST /api/finance/fund/accounts`

请求字段：

- `accountCode`
- `accountName`
- `accountType`
- `bankName`
- `bankAccountNo`
- `currencyCode`
- `openingBalance`
- `remark`

### 查询资金账户

`GET /api/finance/fund/accounts`

查询字段：

- `accountType`
- `status`
- `keyword`
- `pageNo`
- `pageSize`

### 查看资金账户详情

`GET /api/finance/fund/accounts/{id}`

### 录入银行流水

`POST /api/finance/fund/statements`

请求字段：

- `fundAccountId`
- `externalTxnNo`
- `transactionDate`
- `direction`
- `amount`
- `counterpartyName`
- `summary`
- `remark`

### 查询银行流水

`GET /api/finance/fund/statements`

查询字段：

- `fundAccountId`
- `direction`
- `status`
- `transactionDateFrom`
- `transactionDateTo`
- `matchedBizType`
- `matchedBizNo`
- `pageNo`
- `pageSize`

### 查看银行流水详情

`GET /api/finance/fund/statements/{id}`

### 匹配银行流水

`POST /api/finance/fund/statements/{id}/match`

请求字段：

- `bizType`：`RECEIPT` 或 `PAYMENT`
- `bizId`
- `remark`

校验规则：

- 流水必须存在且属于当前租户和账簿。
- 流水状态必须是 `UNMATCHED`。
- `IN` 只能匹配 `RECEIPT`。
- `OUT` 只能匹配 `PAYMENT`。
- 业务单据必须存在且属于当前租户和账簿。
- 业务单据状态必须是 `POSTED`。
- 流水金额必须等于业务单据金额。
- 一张收款单或付款单第一版只能匹配一条银行流水。

### 取消匹配

`POST /api/finance/fund/statements/{id}/unmatch`

请求字段：

- `reason`

校验规则：

- 流水状态必须是 `MATCHED`。
- 必须填写取消原因。
- 取消后流水回到 `UNMATCHED`，清空匹配业务字段，并记录取消原因。

## 权限

新增权限码：

- `finance:fund:view`：查看资金账户和银行流水。
- `finance:fund:manage`：创建资金账户、录入银行流水。
- `finance:fund:reconcile`：匹配和取消匹配银行流水。

接口权限：

- 查询和详情使用 `finance:fund:view`。
- 创建账户和录入流水使用 `finance:fund:manage`。
- 匹配和取消匹配使用 `finance:fund:reconcile`。

迁移脚本需要写入 `sys_menu` 和默认管理员角色绑定，保持现有权限初始化风格。

## 租户隔离

所有写入使用当前 `AuditMetadataFactory` 的 `companyId` 和 `accountBookId`。

所有查询和匹配必须按当前租户和账簿过滤：

- 资金账户按 `company_id`、`account_book_id` 查询。
- 银行流水按 `company_id`、`account_book_id` 查询。
- 匹配收款单时必须校验 `fin_receipt.company_id` 和 `account_book_id`。
- 匹配付款单时必须校验 `fin_payment.company_id` 和 `account_book_id`。
- 跨租户访问详情、匹配或取消匹配均返回业务错误，不能只依赖前端藏按钮。

## 月结检查

扩展 `AccountPeriodCloseChecker`：

- 检查当前期间内 `fin_bank_statement.transaction_date` 落在期间起止日期内的流水。
- 只检查当前 `company_id` 和 `account_book_id`。
- 状态为 `UNMATCHED` 的流水计入阻断项。
- 状态为 `CANCELLED` 的流水不阻断。
- 如果存在未匹配流水，返回问题码 `BANK_STATEMENT_UNMATCHED`，问题描述为 `存在未匹配银行流水`，金额字段记录未匹配流水数量。

这项检查必须参与期间关闭门禁。库存、凭证、应收应付都对了，但银行流水没对上，也不能闭眼结账。

## 错误处理

沿用现有异常风格：

- 业务规则错误使用 `IllegalArgumentException`。
- 状态冲突使用 `BusinessConflictException`。

主要错误：

- `资金账户不存在`
- `资金账户已停用，不能录入流水`
- `银行流水不存在`
- `银行流水已匹配，不能重复匹配`
- `只有未匹配银行流水可以匹配`
- `只有已匹配银行流水可以取消匹配`
- `收入流水只能匹配收款单`
- `支出流水只能匹配付款单`
- `收款单不存在`
- `付款单不存在`
- `只有已过账收款单可以匹配`
- `只有已过账付款单可以匹配`
- `银行流水金额与业务单据金额不一致`
- `业务单据已匹配银行流水`

## 测试策略

新增 `FundReconciliationControllerTest`，覆盖：

- 创建资金账户并查询详情。
- 录入收入银行流水。
- 收入流水匹配已过账收款单。
- 支出流水匹配已过账付款单。
- 金额不一致时拒绝匹配。
- 方向不一致时拒绝匹配。
- 已作废收款单或付款单拒绝匹配。
- 已匹配流水不能重复匹配同一或其他业务单据。
- 取消匹配后流水回到 `UNMATCHED`。
- 跨租户不能访问和匹配对方资金账户、流水或业务单据。
- 缺少权限访问返回 `403`。

扩展 `AccountPeriodControllerTest` 或 `AccountPeriodGuardIntegrationTest`：

- 期间内存在未匹配银行流水时，关闭期间前检查返回 `BANK_STATEMENT_UNMATCHED`。
- 匹配后该阻断项消失。

扩展 Flyway smoke 测试，确保新迁移能在 H2 下执行。

## 验收标准

- 后端提供资金账户、银行流水、匹配和取消匹配 API。
- 匹配不修改应收、应付、收款、付款或凭证事实。
- 收入流水只能匹配收款单，支出流水只能匹配付款单。
- 金额、状态、租户、账簿不一致时必须拒绝匹配。
- 期间关闭检查能识别未匹配银行流水并阻断关账。
- 租户隔离和权限控制通过自动化测试证明。
- 全量 `.\mvnw.cmd test` 通过。
