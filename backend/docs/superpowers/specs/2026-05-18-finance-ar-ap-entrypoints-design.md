# 财务应收应付入口与收付款作废设计

## 背景

当前后端已经具备轻量财务主链路：

- 采购入库、采购退货生成应付
- 销售出库、销售退货生成应收
- 收款单、付款单支持核销
- 报表模块可通过 `/api/reports/finance-settlements` 查询应收应付汇总结果

但这套能力现在有两个明显缺口：

1. 应收、应付仍然挂在报表接口下面，没有财务主模块自己的正式查询入口。
2. 收款单、付款单虽然表状态已经预留 `CANCELLED`，但服务层和接口层没有真正实现作废回滚。

结果就是功能看着像有，真正一上业务就别扭：财务人员想查应收应付，得绕到报表口；录错收付款，还得靠人工改库兜底。这种做法时间一长，账就能被你们自己玩脏。

本设计覆盖并扩展此前仅针对“收付款作废”的最小方案，作为本轮财务闭环补齐的主规格。

## 目标

- 为应收、应付提供正式财务接口，而不是继续寄生在报表模块。
- 为收款单、付款单提供正式作废动作，自动回退核销金额并记录审计信息。
- 抽取应收应付查询内核，让财务主入口和报表中心复用同一套筛选与数据权限逻辑。
- 让详情接口和列表接口共享同一口径，不能出现“列表拦住了、详情又开后门”这种低级事故。
- 补齐最小自动化测试切片，覆盖本轮新增行为。

## 非目标

- 不做应收、应付的手工新增、修改、删除。
- 不做新的红冲单、反向单、核销事件表。
- 不做新的前端页面，只补后端接口、权限、菜单种子和测试。
- 不把财务查询和报表查询彻底合并成一个万能控制器。
- 不处理完整会计中台问题，比如结账、反结账、复杂冲销和期间控制。

## 方案选择

本轮采用“新增正经财务入口 + 复用共享查询内核 + 头表状态作废回滚”的方案。

### 不选方案一：继续复用报表接口

优点是改动最少，但语义太歪。应收应付是财务主能力，不该一直躲在 `report` 名下装报表结果。

### 不选方案二：统一做成 `/api/finance/settlements`

这会减少控制器数量，但会把应收和应付强行揉成一个模糊资源。后面一旦继续扩展各自详情、追踪、核销历史，很容易越写越拧巴。

### 选定方案：分开 `receivable` / `payable` 主入口

- `receivable` 和 `payable` 各自提供列表、详情
- `receipt` 和 `payment` 各自提供作废
- 查询逻辑抽成共享服务，报表与财务入口复用

这套拆法最符合当前代码风格，也最方便后续继续补财务功能。

## API 设计

### 应收接口

- `GET /api/finance/receivables`
- `GET /api/finance/receivables/{id}`

列表查询参数：

- `pageNo`
- `pageSize`
- `customerId`
- `status`
- `sourceType`
- `bizDateFrom`
- `bizDateTo`

列表和详情统一返回字段：

- `id`
- `receivableNo`
- `customerId`
- `bizDate`
- `sourceType`
- `sourceId`
- `sourceNo`
- `direction`
- `originalAmount`
- `settledAmount`
- `remainingAmount`
- `status`
- `remark`

### 应付接口

- `GET /api/finance/payables`
- `GET /api/finance/payables/{id}`

列表查询参数：

- `pageNo`
- `pageSize`
- `supplierId`
- `status`
- `sourceType`
- `bizDateFrom`
- `bizDateTo`

列表和详情统一返回字段：

- `id`
- `payableNo`
- `supplierId`
- `bizDate`
- `sourceType`
- `sourceId`
- `sourceNo`
- `direction`
- `originalAmount`
- `settledAmount`
- `remainingAmount`
- `status`
- `remark`

### 收款单作废接口

- `POST /api/finance/receipts/{id}/cancel`

请求体：

```json
{
  "reason": "收款录入错误"
}
```

### 付款单作废接口

- `POST /api/finance/payments/{id}/cancel`

请求体：

```json
{
  "reason": "付款录入错误"
}
```

### 作废请求规则

- `reason` 去首尾空格后不能为空
- `reason` 长度不能超过 200
- 只有 `POSTED` 状态允许首次作废
- 已经是 `CANCELLED` 时必须幂等返回当前详情

### 作废响应扩展字段

`PaymentResponse`、`ReceiptResponse` 扩展：

- `cancelReason`
- `cancelledBy`
- `cancelledTime`

## 数据模型与迁移

### 表结构变更

在 `fin_payment` 增加：

- `cancel_reason`
- `cancelled_by`
- `cancelled_time`

在 `fin_receipt` 增加：

- `cancel_reason`
- `cancelled_by`
- `cancelled_time`

这部分通过新的 Flyway 迁移完成，不修改现有核销明细表。

### 权限与菜单种子

由于当前权限模型来自 `sys_menu.permission`，不是只加 `PermissionCodes` 常量就完事，所以必须同步补菜单和角色授权种子。

新增权限：

- `finance:receivable:view`
- `finance:payable:view`

建议新增财务菜单：

- `FINANCE_RECEIVABLE`
- `FINANCE_PAYABLE`

默认授予现有财务管理员角色 `3002`。

这次不新增前端页面实现，但菜单和权限种子必须先有，不然登录用户根本拿不到权限码。

## 服务与模块设计

### 新增模块入口

新增：

- `finance/receivable/controller`
- `finance/receivable/service`
- `finance/receivable/web`
- `finance/payable/controller`
- `finance/payable/service`
- `finance/payable/web`

### 抽取共享查询内核

从 `report` 模块中抽出应收应付查询逻辑，形成财务共享查询支撑。

职责：

- 组装应收、应付筛选条件
- 统一计算 `remainingAmount`
- 统一应用来源单据数据权限
- 提供分页、列表、详情共用的查询入口

`ReportQueryService` 继续保留报表接口职责，但内部改为调用这套共享查询支撑，而不是自己再维护一份财务查询逻辑。

### 收付款作废职责归属

- `PaymentService.cancel` 负责付款作废
- `ReceiptService.cancel` 负责收款作废

不新建单独“作废服务”，避免为了一个动作再生一层空壳。

## 数据权限设计

这是本次最容易写歪的地方，必须把口径钉死。

### 基本原则

- 所有应收、应付查询默认限定当前公司
- 列表和详情都必须走同一套权限判断
- 不允许报表接口和财务主入口出现两个不同的可见范围结果

### 业务来源记录

对来源于业务单据的应收、应付，继续沿用来源单据权限：

- 应付来源：
  - `PURCHASE_RECEIPT`
  - `PURCHASE_RETURN`
- 应收来源：
  - `SALES_DELIVERY`
  - `SALES_RETURN`

这类记录的可见性，取决于对应采购/销售单据在现有数据权限下是否可见。

### 财务原生或期初记录

对不直接挂采购/销售单据的数据，例如：

- `OPENING_RECEIVABLE`
- `OPENING_PAYABLE`
- 后续可能新增的财务原生来源类型

不再硬套仓库权限。否则期初往来数据会被仓库维度直接吞掉，查都查不见。

这些记录采用：

- `ALL` 直接全可见
- `SELF / DEPT / POST` 按创建人范围可见
- 单独的 `WAREHOUSE` 权限不扩大这类记录可见范围

换句话说，财务原生记录只吃“人员范围”，不吃“仓库范围”。

### 详情权限

详情接口必须复用与列表一致的权限判断结果：

- 能在列表里看见的，详情可以打开
- 列表看不见的，详情也必须拒绝

不允许出现“知道 ID 就能查详情”的弱智后门。

## 作废事务流程

### 付款单作废

1. 按当前公司加载付款单
2. 若不存在、已删除或跨公司，返回“付款单不存在”
3. 若状态为 `CANCELLED`，直接返回当前详情
4. 若状态不是 `POSTED`，返回“只有已过账付款单可以作废”
5. 查询全部付款核销明细
6. 逐条加载应付记录并校验：
   - 记录存在、未删除、同公司
   - `settledAmount >= allocation.amount`
7. 逐条回退应付：
   - `settledAmount = settledAmount - allocation.amount`
   - 重新计算 `UNSETTLED / PARTIALLY_SETTLED / SETTLED`
8. 更新付款单头：
   - `status = CANCELLED`
   - `cancelReason / cancelledBy / cancelledTime`
   - 常规更新审计字段
9. 返回最新详情

### 收款单作废

流程对称：

- 校验 `ReceiptEntity`
- 回退 `ReceivableEntity.settledAmount`
- 重算应收结算状态
- 更新收款单头作废信息

### 幂等与并发

- 重复作废请求只返回当前已作废结果
- 回退应收应付和更新头表都继续走现有乐观锁保护
- 任一关联记录发生并发冲突，整个事务回滚

## 响应与状态规则

### 应收应付剩余金额

统一由服务端返回：

`remainingAmount = originalAmount - settledAmount`

不允许前端自己现算，不然多个页面迟早算出两个口径。

### 作废后的单据语义

- 收款单、付款单作废后保留原核销明细
- 作废不会删除历史事实
- 作废后单据不能再参与任何新的业务动作

## 异常口径

建议统一使用以下错误语义：

- 应收记录不存在
- 应付记录不存在
- 收款单不存在
- 付款单不存在
- 只有已过账收款单可以作废
- 只有已过账付款单可以作废
- 作废原因不能为空
- 作废原因长度不能超过 200 个字符
- 应收已核销金额不足，无法作废当前收款单
- 应付已核销金额不足，无法作废当前付款单
- 相关记录已被其他操作修改，请刷新后重试
- 无权访问该应收记录
- 无权访问该应付记录

## 测试与验证

本轮不装死说“编译过就算完事”，至少要补本次功能的最小测试切片。

覆盖范围：

- 应收列表分页
- 应收详情权限拦截
- 应付列表分页
- 应付详情权限拦截
- 收款单作废回退单条应收
- 收款单作废回退多条应收
- 付款单作废回退单条应付
- 付款单作废回退多条应付
- 重复作废幂等
- 非法状态作废拒绝

命令层验证：

- 定向测试本轮新增测试类
- `.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -DskipTests compile`
- 必要时再跑 `clean package`

## 发布与兼容性说明

- 现有 `/api/reports/finance-settlements` 继续保留，对外兼容
- 报表模块内部改为复用共享查询内核，不改变报表响应字段
- 当前实现不改变应收、应付生成逻辑，只补查询入口和收付款纠错闭环
- 后续如果继续做应收应付追踪、核销历史、手工财务单据，可以在本次新增入口上扩展，不必再绕回报表模块

## 结论

这轮交付的重点不是“让财务模块看起来更丰满”，而是把已经散在报表和底层表里的能力收回正经入口，同时补齐最基本的纠错动作。

做完之后，系统会从“财务内核勉强有，入口和纠错都别扭”，进化到“应收应付能正经查，收付款录错能正经撤”。这才算像个能上线用的后端，不然现在这状态顶多叫半熟。
