# ERP Gap Closure Backlog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 ERP V1 设计文档里尚未落地的一期能力拆成可执行 backlog，明确实施顺序、依赖关系、目标文件范围和验收口径。本文件于 2026-05-11 同步为当前实现状态。

**Architecture:** 当前代码基线已经具备 `system + masterdata + purchase + sales + inventory + finance + workflow + report`，并补齐数据权限、字典、日志体系和库存调拨闭环。本计划保留原始拆解方式，同时记录各 Epic 的完成证据，避免文档继续冒充“待办清单”。

**Tech Stack:** Spring Boot 3.3.x, Spring Security + JWT, MyBatis-Plus, Flyway, H2, MockMvc, JUnit 5, Redis

---

## File Map

**Current Baseline Modules:**
- `src/main/java/com/tuowei/erp/system`
- `src/main/java/com/tuowei/erp/masterdata`
- `src/main/java/com/tuowei/erp/purchase`
- `src/main/java/com/tuowei/erp/sales`
- `src/main/java/com/tuowei/erp/inventory`
- `src/main/java/com/tuowei/erp/finance`
- `src/main/java/com/tuowei/erp/workflow`
- `src/main/java/com/tuowei/erp/report`

**Implemented Migration Files by Epic:**
- `src/main/resources/db/migration/V16__system_data_scope_schema.sql`
- `src/main/resources/db/migration/V17__sales_order_delivery_schema.sql`
- `src/main/resources/db/migration/V18__finance_receivable_payable_schema.sql`
- `src/main/resources/db/migration/V19__inventory_operation_schema.sql`
- `src/main/resources/db/migration/V20__workflow_schema.sql`
- `V21` intentionally unused; report queries reuse existing business tables and do not require a schema migration.
- `src/main/resources/db/migration/V22__system_dict_schema.sql`
- `src/main/resources/db/migration/V23__system_log_schema.sql`
- `src/main/resources/db/migration/V24__system_audit_log_schema.sql`
- `src/main/resources/db/migration/V25__system_dict_seed.sql`
- `src/main/resources/db/migration/V26__inventory_transfer_schema.sql`
- `src/main/resources/db/migration/V34__finance_ledger_minimum_schema.sql`

**Implemented Module Directories:**
- `src/main/java/com/tuowei/erp/system/datascope`
- `src/main/java/com/tuowei/erp/sales`
- `src/main/java/com/tuowei/erp/finance`
- `src/main/java/com/tuowei/erp/inventory/adjust`
- `src/main/java/com/tuowei/erp/inventory/check`
- `src/main/java/com/tuowei/erp/inventory/alert`
- `src/main/java/com/tuowei/erp/inventory/transfer`
- `src/main/java/com/tuowei/erp/workflow`
- `src/main/java/com/tuowei/erp/report`
- `src/main/java/com/tuowei/erp/system/dict`
- `src/main/java/com/tuowei/erp/system/log`

## Current Baseline

- 已完成：认证登录、功能权限、用户/角色/菜单、部门/岗位、系统参数、编号规则、商品/客户/供应商/仓库主数据
- 已完成：采购订单、采购入库、采购退货、库存余额与库存流水落账内核
- 已完成：采购关键链路的审计字段、编号并发安全、乐观锁冲突统一化
- 已完成：数据权限、销售主链路、轻量财务、库存盘点/调整/预警/查询入口/调拨、审批中心、报表中心、字典、日志体系
- 已完成：采购订单关闭、采购追踪视图、采购到付款/到凭证关联视图
- 已完成：财务账簿最小闭环，会计科目、费用登记、凭证分录、总账和明细账查询已落地
- 当前自动化测试源码已按要求删除，历史计划中的 `Test:` 路径只表示曾经的实现证据，不再表示当前仓库存在这些测试文件

## Backlog Summary

| Priority | Epic | Status | Evidence |
|---|---|---|---|
| P0 | 数据权限底座 | Done | `DataScopeServiceTest` and purchase/sales/inventory data-scope tests |
| P0 | 销售主链路 | Done | `src/main/java/com/tuowei/erp/sales` and sales controller/service tests |
| P0 | 轻量财务主链路 | Done | `src/main/java/com/tuowei/erp/finance` and finance tests |
| P0 | 库存业务功能 | Done | `inventory/adjust`、`inventory/check`、`inventory/alert`、`inventory/transfer` and inventory tests |
| P1 | 采购增强闭环 | Done | `PurchaseOrderTraceTest` covers close and trace views |
| P1 | 审批中心 | Done | `2026-05-09-workflow-center-minimum.md` and workflow tests |
| P1 | 报表中心 | Done | `ReportControllerTest` covers report query/export endpoints |
| P1 | 财务账簿最小闭环 | Done | `V34__finance_ledger_minimum_schema.sql` and `finance/subject`、`finance/expense`、`finance/voucher`、`finance/ledger` modules |
| P2 | 字典与日志体系 | Done | system dict/log schema, controller and auto-capture tests |

### Task 1: 数据权限底座

**Files:**
- Create: `src/main/resources/db/migration/V16__system_data_scope_schema.sql`
- Create: `src/main/java/com/tuowei/erp/system/datascope/mapper/RoleDataScopeMapper.java`
- Create: `src/main/java/com/tuowei/erp/system/datascope/mapper/UserDataScopeMapper.java`
- Create: `src/main/java/com/tuowei/erp/system/datascope/model/RoleDataScopeEntity.java`
- Create: `src/main/java/com/tuowei/erp/system/datascope/model/UserDataScopeEntity.java`
- Create: `src/main/java/com/tuowei/erp/common/security/DataScopeRule.java`
- Create: `src/main/java/com/tuowei/erp/common/security/DataScopeSnapshot.java`
- Create: `src/main/java/com/tuowei/erp/common/security/DataScopeService.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/ErpPrincipal.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/DatabaseUserDetailsService.java`
- Modify: `src/main/java/com/tuowei/erp/system/auth/service/AuthService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnService.java`
- Test: `src/test/java/com/tuowei/erp/common/security/DataScopeServiceTest.java`
- Test: `src/test/java/com/tuowei/erp/purchase/order/PurchaseOrderDataScopeTest.java`
- Test: `src/test/java/com/tuowei/erp/purchase/receipt/PurchaseReceiptDataScopeTest.java`
- Test: `src/test/java/com/tuowei/erp/purchase/returnorder/PurchaseReturnDataScopeTest.java`

- [x] 建立 `sys_role_data_scope`、`sys_user_data_scope` 及最小初始化数据，先支持 `ALL / DEPT / POST / WAREHOUSE / SELF` 五种范围
- [x] 扩展 `ErpPrincipal` 和登录流程，让登录态能携带数据范围快照，而不是只有权限码
- [x] 给采购订单、采购入库、采购退货的列表和详情查询统一接入数据权限过滤
- [x] 明确“详情接口也要拦”的原则，不能只拦列表然后详情开后门
- [x] 补齐数据权限相关测试：全局可见、仓库隔离、部门隔离、自建可见、越权详情拦截

**Acceptance:**
- 采购查询接口能按仓库、部门、岗位、自建范围做过滤
- 登录返回和后端认证上下文都能读取数据权限快照
- 详情、列表、导出预留查询都共享一套数据范围判断逻辑
- 不引入前端参与的数据权限判断

**Detailed Plan File:**
- `docs/superpowers/plans/2026-05-07-erp-phase2-data-scope-foundation.md`

### Task 2: 销售主链路

**Files:**
- Create: `src/main/resources/db/migration/V17__sales_order_delivery_schema.sql`
- Create: `src/main/java/com/tuowei/erp/sales/order`
- Create: `src/main/java/com/tuowei/erp/sales/delivery`
- Create: `src/main/java/com/tuowei/erp/sales/returnorder`
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryPostingService.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`
- Test: `src/test/java/com/tuowei/erp/sales/order`
- Test: `src/test/java/com/tuowei/erp/sales/delivery`
- Test: `src/test/java/com/tuowei/erp/sales/returnorder`

- [x] 新增销售订单、销售订单明细、销售出库、销售出库明细、销售退货、销售退货明细表结构
- [x] 按采购现有风格实现销售订单草稿、提交、审批、驳回、作废、分页、详情
- [x] 实现销售出库，复用库存落账服务做出库扣减，并守住“不允许负库存”
- [x] 实现销售退货，回补库存并回写销售订单净出库进度
- [x] 为销售模块补齐权限码、控制器、集成测试和演示初始化数据

**Acceptance:**
- 销售订单到出库到退货主链能跑通
- 出库数量不能超过剩余可出库数量
- 销售出库前校验库存充足，默认不允许负库存
- 销售链路能复用现有审计、编号、并发保护模式

**Detailed Plan File:**
- `docs/superpowers/plans/2026-05-08-erp-phase2-sales-chain.md`

### Task 3: 轻量财务主链路

**Files:**
- Create: `src/main/resources/db/migration/V18__finance_receivable_payable_schema.sql`
- Create: `src/main/java/com/tuowei/erp/finance/payable`
- Create: `src/main/java/com/tuowei/erp/finance/payment`
- Create: `src/main/java/com/tuowei/erp/finance/receivable`
- Create: `src/main/java/com/tuowei/erp/finance/receipt`
- Create: `src/main/java/com/tuowei/erp/finance/voucher`
- Modify: `src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnService.java`
- Modify: `src/main/java/com/tuowei/erp/sales/delivery`
- Modify: `src/main/java/com/tuowei/erp/sales/returnorder`
- Test: `src/test/java/com/tuowei/erp/finance`

- [x] 新增应付、付款、应收、收款、凭证最小表结构，先不做复杂总账体系
- [x] 采购入库过账后自动形成应付依据，采购退货冲减应付依据
- [x] 销售出库后自动形成应收依据，销售退货冲减应收依据
- [x] 实现付款单、收款单的最小登记和部分核销能力
- [x] 实现最小凭证生成与追溯字段，先保证来源单据可追踪，不先做完整会计中台

**Acceptance:**
- 采购到付款链路、销售到收款链路均可部分核销
- 应收应付剩余金额由服务端累计推导
- 凭证至少能回溯来源业务单据和核销单据
- 财务写入和业务写回边界清晰，避免采购服务和销售服务直接改 `fin_*` 细节

**Detailed Plan File:**
- `docs/superpowers/plans/2026-05-08-erp-phase3-light-finance.md`

### Task 4: 库存业务功能

**Files:**
- Create: `src/main/resources/db/migration/V19__inventory_operation_schema.sql`
- Create: `src/main/java/com/tuowei/erp/inventory/stock/controller`
- Create: `src/main/java/com/tuowei/erp/inventory/adjust`
- Create: `src/main/java/com/tuowei/erp/inventory/check`
- Create: `src/main/java/com/tuowei/erp/inventory/alert`
- Modify: `src/main/java/com/tuowei/erp/inventory/stock/service/InventoryPostingService.java`
- Test: `src/test/java/com/tuowei/erp/inventory`

- [x] 给现有库存余额和流水补齐查询控制器、分页接口和详情视图
- [x] 新增库存调整单与明细、库存盘点任务与明细、库存预警规则表结构
- [x] 实现库存盘点录入、差异计算、调整生效主流程
- [x] 实现库存增加调整和减少调整，统一走库存流水
- [x] 实现按商品 + 仓库维度的最低库存预警查询
- [x] 实现库存调拨草稿、详情和过账闭环，调出仓扣减、调入仓增加，统一写入库存流水
- [x] 给库存调拨接入功能权限、数据权限、初始化脚本和 Flyway schema

**Acceptance:**
- 仓库管理员能查看库存余额、库存流水
- 盘点不能直接改余额，必须走调整单
- 所有库存调整都写入 `inv_*` 流水并可追溯原因
- 预警查询支持仓库维度和商品维度过滤
- 调拨必须拒绝同仓调拨、非正数量、负单价和库存不足；非全量数据权限下必须同时覆盖调出仓和调入仓，或满足创建人范围

**Implementation Evidence:**
- `src/main/java/com/tuowei/erp/inventory`
- `src/test/java/com/tuowei/erp/inventory`
- `src/main/resources/db/migration/V26__inventory_transfer_schema.sql`
- `InventoryTransferServiceTest`
- `InventoryTransferControllerTest`
- `InventoryTransferDataScopeTest`
- `InventoryTransferSchemaMigrationTest`

### Task 5: 采购增强闭环

**Files:**
- Modify: `src/main/java/com/tuowei/erp/purchase/order/controller/PurchaseOrderController.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/order/web/PurchaseOrderResponse.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptService.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnService.java`
- Modify: `src/main/java/com/tuowei/erp/finance/payable`
- Test: `src/test/java/com/tuowei/erp/purchase/order/PurchaseOrderTraceTest.java`

- [x] 增加采购订单 `close` 动作，并补齐前置校验
- [x] 为采购订单详情补充执行进度、关联入库摘要、关联付款摘要、凭证摘要
- [x] 提供采购订单全过程追踪查询接口，不要求一上来就搞复杂图谱
- [x] 打通采购付款状态和采购订单追踪视图
- [x] 修整当前采购响应结构，让文档里要求的 `approvalInfo / executionInfo / relatedDocs` 有最小映射

**Acceptance:**
- 采购订单既能看明细，也能看执行进度和关联单据摘要
- 已完成业务影响的采购单不能随意关闭
- 追踪视图至少覆盖订单、入库、退货、应付、付款、凭证摘要

**Implementation Evidence:**
- `src/test/java/com/tuowei/erp/purchase/order/PurchaseOrderTraceTest.java`

### Task 6: 审批中心

**Files:**
- Create: `src/main/resources/db/migration/V20__workflow_schema.sql`
- Create: `src/main/java/com/tuowei/erp/workflow`
- Modify: `src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderService.java`
- Modify: `src/main/java/com/tuowei/erp/sales/order`
- Modify: `src/main/java/com/tuowei/erp/inventory/adjust`
- Test: `src/test/java/com/tuowei/erp/workflow`

- [x] 新增审批定义、节点定义、实例、待办、审批记录表结构
- [x] 把采购订单当前写死的 `approve/reject` 升级为“业务状态 + 审批记录”双写
- [x] 让销售订单、库存调整、费用登记后续能接同一套审批底座
- [x] 提供待办审批查询、审批记录查询和驳回重提能力
- [x] 保留现有简单状态接口，但内部改成调用统一审批服务

**Acceptance:**
- 审批中心负责审批状态和记录，不直接执行业务落账
- 至少采购订单先切到统一审批记录模型
- 待办、已办、审批历史可查询

**Detailed Plan File:**
- `docs/superpowers/plans/2026-05-09-workflow-center-minimum.md`

### Task 7: 报表中心

**Files:**
- Schema: no `V21` migration was needed; report queries use existing business tables.
- Create: `src/main/java/com/tuowei/erp/report`
- Create: `src/main/java/com/tuowei/erp/common/export`
- Test: `src/test/java/com/tuowei/erp/report`

- [x] 提供采购统计、销售统计、库存余额、库存流水、应收应付查询类报表
- [x] 所有报表先做查询和分页，再决定是否补异步导出
- [x] 报表统一复用数据权限过滤，不能绕过查询层
- [x] 为导出接口提供最小公共封装，至少先支持同步导出
- [x] 报表口径严格对齐业务单据、库存流水、财务往来

**Acceptance:**
- 核心报表能查询、分页、按条件过滤
- 导出接口服从当前用户数据权限
- 报表结果可追溯来源单据或流水

**Implementation Evidence:**
- `src/main/java/com/tuowei/erp/report`
- `src/test/java/com/tuowei/erp/report/ReportControllerTest.java`

### Task 8: 字典与日志体系

**Files:**
- Create: `src/main/resources/db/migration/V22__system_dict_schema.sql`
- Create: `src/main/resources/db/migration/V23__system_log_schema.sql`
- Create: `src/main/resources/db/migration/V24__system_audit_log_schema.sql`
- Create: `src/main/resources/db/migration/V25__system_dict_seed.sql`
- Create: `src/main/java/com/tuowei/erp/system/dict`
- Create: `src/main/java/com/tuowei/erp/system/log`
- Modify: `src/main/java/com/tuowei/erp/system/auth/service/AuthService.java`
- Modify: `src/main/java/com/tuowei/erp/common/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/com/tuowei/erp/system/dict`
- Test: `src/test/java/com/tuowei/erp/system/log`

- [x] 新增字典类型、字典项、登录日志、操作日志、审计日志表结构
- [x] 实现字典维护接口，供主数据校验和前端枚举加载使用
- [x] 给登录、关键业务操作、审批动作补最小日志记录
- [x] 提供日志查询接口，支持按时间、用户、模块、单号过滤
- [x] 先做“关键动作可追溯”，别一上来搞成日志平台

**Acceptance:**
- 基础字典可维护、可查询、可被业务校验引用
- 登录和关键操作有日志记录
- 日志查询能按文档要求维度过滤

**Implementation Evidence:**
- `src/main/java/com/tuowei/erp/system/dict`
- `src/main/java/com/tuowei/erp/system/log`
- `src/test/java/com/tuowei/erp/system/dict`
- `src/test/java/com/tuowei/erp/system/log`

## Recommended Delivery Waves

### Wave 1: 先补地基

- [x] Task 1 数据权限底座
- [x] Task 4 库存业务功能中的查询入口部分

**Exit Criteria:**
- 采购和库存查询已经服从数据权限
- 后续业务模块不需要重新发明一套查询隔离逻辑

### Wave 2: 打通第二条业务主链

- [x] Task 2 销售主链路
- [x] Task 4 库存业务功能中的盘点/调整/预警

**Exit Criteria:**
- 采购和销售两条货物流转链都已成型
- 库存模块既能被动落账，也有主动业务入口

### Wave 3: 打通资金主链

- [x] Task 3 轻量财务主链路
- [x] Task 5 采购增强闭环

**Exit Criteria:**
- 采购到付款、销售到收款都能闭环
- 采购追踪视图不再只停留在单据层

### Wave 4: 查询与治理能力收尾

- [x] Task 6 审批中心
- [x] Task 7 报表中心
- [x] Task 8 字典与日志体系

**Exit Criteria:**
- 审批、报表、日志、字典达到一期文档承诺的最小可用标准

## Scope Guardrails

- 不要把销售、财务、库存、审批一次性揉进同一个超大计划里直接开抡
- 每个 Epic 都要单开详细实施计划，再进入 TDD 编码
- 继续沿用当前仓库风格：`controller + service + mapper + model + web`
- 继续坚持现有约束：`apply_patch` 改文件、先红后绿、定向回归后全量回归
- 数据权限必须后端强约束，不能靠前端藏按钮装样子
- 销售、财务上线前不要偷改采购现有业务口径

## Closure Status

- 2026-05-09 全量测试命令 `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" test` 通过：254 tests, 0 failures, 0 errors.
- 2026-05-11 补齐库存调拨闭环后，全量测试命令 `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" test` 通过：263 tests, 0 failures, 0 errors.
- 2026-05-15 已删除 `src/test` 自动化测试源码；当前 `mvn test` 只验证主源码编译和构建生命周期，输出 `No tests to run.` 属于预期。
- 2026-05-09 已将历史计划文件中的任务 checkbox 按当前实现状态对齐；这些文件现在是实现记录，不再表示未完成开发入口。
- 本文件现在作为一期 gap closure 的状态记录，不再作为新的开发入口。
- 后续如果继续扩展，应单独新建计划文件，别在这个已闭环 backlog 上继续糊泥巴。

