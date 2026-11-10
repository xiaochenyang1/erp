**ERP 项目开发现状与下一步建议 · 2026-10-14**

> 同日优化更新：下文保留优化前的审计基线。编译失败、合同/发票币种遗漏、合同历史快照和尾差展示问题现已修复，前后端与完整浏览器回归已通过。当前进展、验证结果和剩余验收事项见 [优化与回归记录](OPTIMIZATION-2026-10-14.md)，不要再将下文的历史编译失败当作当前状态。

**以下为优化开始前的审计记录。**

当前项目处于「核心业务主体已有实现，多币种扩展仍在收尾，等待完整回归与上线验收」阶段。当前工作区存在确定的后端编译错误，不能作为可发布版本。

本报告依据当前代码、实际执行结果和现存报告重新核对，不沿用旧文档的完成统计。检查分支为 `refactor/postrelease-sql-gate`，HEAD 为 `73917294748a493c55ce42fe037cf373f3ab625d`。检查开始时已有 76 个 Git 状态路径（57 个已跟踪修改、19 个未跟踪路径，部分路径为目录），涉及财务、合同、报表、迁移和 UI smoke。本次只新增分析报告，未修改业务源码或代为提交这些改动。

**1. 各模块做到哪一步**

“主体已有实现”表示能找到对应的业务服务、接口、页面或数据库实现；不表示当前工作区的所有场景已经验收通过。

| 领域 | 已有能力 | 当前判断与边界 |
|---|---|---|
| 基础资料与权限 | 商品、客户、供应商、仓库/库位、用户、角色、菜单、组织、数据范围、公司/账套隔离 | 主体已有实现；公司/账套数据隔离不等于完整 SaaS 租户运营 |
| 采购 | 请购、询价/报价/选标、价目、订单审批、收货入库、退货、应付、付款核销 | 主链路已有实现，保存的 UI 报告覆盖采购到付款和采购退货；新币种改动仍需随候选复验 |
| 销售 | 报价、价目、订单审批、授信、库存预占、发货、物流/签收、退货、应收、收款 | 主链路已有实现，保存的 UI 报告覆盖下单到收款及退货 |
| 库存 | 余额/流水、批次/效期、拣选、序列号、调拨、调整、盘点、预占、补货、谱系追溯、Web 扫码 | 功能覆盖较完整，已有库存调整/调拨/盘点及草稿扫码回归入口 |
| 生产与质检 | BOM、工艺路线、工作中心、生产工单、领退料、分批完工/反完工、报工、MRP-lite、IQC/IPQC/OQC | 属于轻量制造；MRP 主要按需求、库存、在途、在制计算建议，材料展开后建议采购，尚不是多层计划与有限产能 APS |
| 财务 | 应收应付、收付款、费用、发票登记、手工/业务凭证、总账明细账、资金对账、账龄、毛利、预算、期间锁账/月结、库存财务对账 | 基础链路已有实现；生产成本报表主要是材料、完工、在制金额，尚不是完整人工/制造费用成本核算 |
| 多币种 | 本位币、币种/汇率、订单与收发货快照、子账、收付款汇兑损益、期末调汇/冲回/撤销、期初导入 | 已经开发了较多能力，不能再列作整体“未开发”；合同、发票、报表展示和当前版本回归尚未闭环 |
| 合同、审批与运营 | 合同台账、订单绑定、履约、版本历史、附件、预警；审批/转签/超时、通知、异常工单/SLA、看板、报表与追踪、导入和上线验收中心 | 主体已有实现；合同的多币种改造是当前明确的半成品 |

规模参考：78 个后端 Controller、74 个 Vue 视图文件、485 个 Java `*Test.java` 文件、245 个前端 `*.test.ts` 文件；SQL 迁移 165 个，最高版本 V168。文件数不用于计算完成率；在没有固定需求分母和逐项验收记录时，不宜宣称“完成 95%”。

代表性实现：[采购订单](../src/main/java/com/tuowei/erp/purchase/order/controller/PurchaseOrderController.java)、[销售订单](../src/main/java/com/tuowei/erp/sales/order/controller/SalesOrderController.java)、[库存查询](../../frontend/src/views/inventory/stocks/index.vue)、[生产工单](../src/main/java/com/tuowei/erp/production/order/controller/ProductionOrderController.java)、[质检](../src/main/java/com/tuowei/erp/qc/inspection/controller/QcInspectionController.java)、[MRP 计算](../src/main/java/com/tuowei/erp/inventory/mrp/service/MrpPlanCalculationService.java)、[生产成本报表](../src/main/java/com/tuowei/erp/report/service/ProductionCostReportService.java)、[期间/月结/调汇](../src/main/java/com/tuowei/erp/finance/period/controller/AccountPeriodController.java)。

**2. 当前确定没有收尾的工作**

**P0：合同返回结构改了一半，后端无法编译。**

[ContractResponse](../src/main/java/com/tuowei/erp/commercial/contract/web/ContractResponse.java) 新增了 `currencyCode`、`exchangeRate`、`baseTotalAmount`、`baseCurrencyCode` 四个参数，但 [ContractQueryService](../src/main/java/com/tuowei/erp/commercial/contract/service/ContractQueryService.java) 第 172 行仍调用旧的 15 参数构造器。

本轮实际执行完整测试入口，Maven 在主代码编译阶段报错：构造器需要 19 个参数、实际提供 15 个参数。本轮后端测试没有进入执行阶段。下一步首先要把合同查询返回、相关调用和测试一起接齐，恢复编译，再继续业务回归。

**P1：合同和发票的多币种前后端没有接通。**

- 合同后端写入服务已经解析并保存币种/汇率，但 [前端合同类型](../../frontend/src/api/contracts.ts)、[编辑表单](../../frontend/src/composables/useContractForm.ts) 和合同页面没有对应字段接入。合同历史版本的 [记录/恢复](../src/main/java/com/tuowei/erp/commercial/contract/service/ContractVersionCommandService.java) 也没有保存/恢复币种、汇率和本位币金额。
- 发票后端 [创建/编辑服务](../src/main/java/com/tuowei/erp/finance/invoice/service/FinanceInvoiceCommandService.java) 已支持币种快照；[前端发票表单](../../frontend/src/composables/useFinanceInvoiceForm.ts) 编辑回填和保存 payload 都没有币种、汇率，页面也没有相应选择与展示。
- 本轮直接加载现有两个 composable，以 `100 USD、汇率 7.2` 的模拟详情执行编辑/保存。两个表单输出均不含 `currencyCode` 和 `exchangeRate`，发票金额仍为 100。这里只替换 API 与日期依赖，实际执行的是现有表单逻辑，没有向数据库写入数据。
- [SettlementCurrencyService](../src/main/java/com/tuowei/erp/finance/currency/service/SettlementCurrencyService.java) 对缺省币种使用账套本位币。因此在人民币账套编辑一张外币草稿时，当前调用链有将其保存成人民币金额的风险。表单漏字段已经复现；因后端编译失败，本轮没有执行完整 HTTP 保存复现。

验收标准：外币合同/发票可以创建、查看、编辑和再次打开，原币与汇率快照不丢；合同版本记录/恢复保留金额语义；订单绑定时币种一致性有正反例；本位币和历史无快照记录的兼容规则明确。

**P1：核销尾差已接入后端，报表页面还没跟上。**

[SettlementAmountAllocator](../src/main/java/com/tuowei/erp/finance/posting/SettlementAmountAllocator.java) 和 [V168](../src/main/resources/db/migration/V168__settlement_posting_snapshots.sql) 已引入实际过账金额、子账冲减金额和尾差快照；收付款创建及作废、凭证、子账剩余金额、往来对账也有相应改动。

[汇兑损益报表 Mapper](../src/main/java/com/tuowei/erp/report/mapper/FxGainLossReportMapper.java) 已返回 `roundingAmount` 和 `postingSnapshot`，且将“汇兑损益为 0、但尾差不为 0”的记录纳入结果。然而 [前端报表类型](../../frontend/src/api/workflow.ts) 和 [汇兑损益标签页](../../frontend/src/views/reports/index.vue) 没有对应字段展示。

因此用户看到的本位币收付、冲减和汇兑损益三列可能相差一个尾差，却看不到差额解释。这里确认的是展示缺口，不能据此断言新过账算法仍有旧的五分钱问题。下一步应接齐尾差列、口径说明、页面汇总与导出核对。

**P1：新财务改动尚无当前完整候选的验证结论。**

10 月 8 日报告中的五个重点问题，当前均能找到后续实现：

| 原问题 | 当前代码进展 | 仍需完成 |
|---|---|---|
| 外币往来结清后对账不归零 | `PartnerStatementSettlementService` 已有汇兑与尾差调整；现存相关 SQL 测试报告 41 项通过 | 按最新过账快照规则重新做完整回归 |
| 期末调汇混入期后业务、重复执行依据不足 | 已有期末余额还原、依据 JSON/hash、月结复验和 V167；现存 workflow 报告 47 项通过 | 当前编译恢复后，复验跨期核销、汇率变化、撤销重算与月结 |
| 多笔核销尾差与凭证不一致 | 已有分摊器、V168、实际过账快照与冲回逻辑；现存 workflow 报告 6 项通过 | 页面补齐，并验证新旧记录、凭证、资金、子账、报表一致 |
| 供应商敞口漏采购退货 | 查询已包含 `OFFSET`，保留 `DECREASE` 负方向 | 纳入当前版本的退货、部分核销与敞口回归 |
| 有历史业务仍能改本位币 | 已有 `BaseCurrencyDataGuard`，按账套检查金额/币种数据；现存普通测试 4 项、MySQL 测试 1 项通过 | 复验空账套、有数据账套及跨账套边界，完成业务口径确认 |

这些应归类为“已有修复实现、待当前候选验收”，不应继续照旧文档列为完全未做，也不能因为有旧测试 XML 就宣布当前版本已通过。

**P1：发布交付仍未闭环。**

1. 财务 F1–F12、质检 Q1–Q5 的可查签字模板仍为空，最近的 [RC 交接记录](FQ-SIGNOFF-RC-989FBC4-2026-08-24.md) 仍为 `WAITING_FOR_BUSINESS_SIGNOFF`。
2. 工作区尚未冻结为干净候选；新增 UI smoke CI 文件、V167/V168 等仍未跟踪，不能把当前修改等同于已进入版本库和远端 CI。
3. 当前编译失败，无法生成对应的可发布后端包；没有当前候选的完整门禁、制品和业务验收证据链。
4. [RC 冻结记录](RC-FREEZE-2026-08-24.md) 所指的 `/Users/xiao/Desktop/python/release-evidence/` 当前不存在，本地没有 Git tag。仓库仍记录真实预生产、监控告警和签字待完成。

本轮没有访问外部 CI、制品库或真实部署平台，因此以上是本地可核实的证据缺口，不代表断言其他机器上没有任何部署。正式上线仍需核实目标环境、备份恢复、回滚、监控告警、期初数据和业务权限。

**3. 真正尚未开发的扩展方向**

结合 [功能扩展路线图](FEATURE-EXPANSION-ROADMAP.md)、现有业务包、Controller 和前端视图，下列方向尚未形成完整业务模块：

| 方向 | 缺少的完整能力 | 与当前功能的区别 |
|---|---|---|
| 固定资产 | 资产卡片、折旧、转移、处置与总账联动 | 现有库存商品不等于资产管理 |
| 税务管理 | 税码/税率规则、进销项管理、税务报表和申报对接 | 现有单据税额与发票登记只覆盖基础记录 |
| 供应商协同、客户门户 | 外部账号、订单确认、交付/对账等协同流程 | 当前主要是企业内部 ERP 操作 |
| 售后、维修、设备管理 | 服务工单、维修记录、设备台账/保养等 | 现有异常工单不是完整售后或设备业务 |
| 高级生产计划与成本 | 多层物料计划、产能日历、有限产能排程、人工与制造费用分摊 | 当前 MRP-lite、工序报工和材料成本已有基础 |
| 自助 BI | 自定义维度、指标、报表设计与分析 | 当前是预设报表和经营看板 |
| 其他独立产品方向 | 完整 SaaS 租户运营、集团合并报表、自由 BPM、移动端完整产品 | 需另定产品范围，不应自动作为当前版本上线前必补项 |

这些是后续产品选择，应按真实业务需求排序；它们与当前的编译失败、币种漏接和发布验收是不同性质的工作。

**4. 本次验证结果与适用范围**

| 检查 | 结果 | 证据边界 |
|---|---|---|
| 本轮前端单测 | 245 文件、1,143 项通过 | 没有覆盖上述合同/发票币种保留场景，不能以此证明所有表单完整 |
| 本轮前端工程检查 | `type-check`、`lint`、`check:contracts`、`build` 全部通过 | OpenAPI 类型生成仍仅商品模块试点，不是全部业务接口契约检查 |
| 本轮后端完整测试入口 | `BUILD FAILURE`，主代码编译失败 | 后端测试、MySQL/Redis Testcontainers 测试均未进入本轮执行 |
| 本轮表单定向复现 | 合同、发票均漏传已有币种和汇率 | 执行现有表单代码，API/日期为替身；不是完整浏览器/HTTP 验证 |
| 现存后端 XML | 484 份报告合计 2,465 项，0 failures/errors、3 skipped | 报告来自 10 月 12 日与 13 日的混合批次，不是今天代码的全量通过证据；3 个 skipped 为 PowerShell 相关行为测试 |
| 现存浏览器报告 | 10 月 12 日，28/28 页面、36/36 workflow 通过 | `ci=null`，没有当前 commit 绑定；本轮未重跑浏览器，不能代表当前编译失败工作区的运行状态 |

浏览器报告已经比 10 月 8 日文档引用的“26/36 通过”更新，因此不能继续把那 10 条失败列为当前缺陷。现存 36 条流程覆盖常规采销收付、库存、生产、IQC、费用、手工凭证等，但没有专门的外币合同/发票编辑、尾差报表和期末调汇重算流程；多币种专项及受限角色矩阵仍应纳入新候选验证。

后端实际执行命令：

```bash
cd backend
./scripts/test-local.sh -Ptestcontainers -Derp.testcontainers.enabled=true test
```

测试入口创建了一次性 MySQL，编译失败后已由脚本清理。本轮未使用或修改业务数据库。没有为绕过失败而修改合同代码、跳过编译或使用旧 JAR 冒充当前构建。

本轮日志位于 `/private/tmp/erp-status-20261014-backend.log`、`/private/tmp/erp-status-20261014-frontend-*.log`；表单复现结果为 `/private/tmp/erp-status-20261014-form-reproduction.json`；关键源码及旧 UI 报告哈希为 `/private/tmp/erp-status-20261014-baseline.json`。这些是分析材料，不是正式发布归档。影响结论的输入、输出与源码位置已记录在本文中。

**5. 下一步按什么顺序做**

| 顺序 | 工作 | 完成标准 |
|---|---|---|
| 1 | 接完合同的后端返回结构、所有调用与测试 | 当前工作区主代码和测试代码能编译；不能仅加空值参数掩盖字段漏接 |
| 2 | 补齐合同/发票币种表单、详情、保存和合同历史恢复；补齐汇兑尾差展示 | 100 USD、汇率 7.2 的草稿编辑后仍保持快照；版本恢复及订单绑定正确；页面能解释凭证与报表金额 |
| 3 | 完成财务和跨模块回归 | 同一版本覆盖多笔/部分核销、全额结清、退货、作废、历史记录、跨期调汇和本位币保护；采销产、库存与权限流程通过 |
| 4 | 整理提交，冻结候选并重跑门禁 | 干净 commit 对应前后端构建、完整 Testcontainers、API/UI smoke、JAR/SBOM/迁移证据；PowerShell 跳过项在相应环境实际执行 |
| 5 | 预生产试跑与人工验收 | 真实主数据/期初、岗位权限、备份恢复/回滚、告警触发恢复均有证据；财务/质检签字后作 GO/NO-GO 决策 |
| 6 | 根据业务选择下一期模块 | 内部 ERP 稳定后，再按固定资产、税务、协同或生产深度的实际需求立项 |

工程上，优先把接口契约与关键业务行为测试扩展到合同、发票、财务报表。大量 Service/Vue 拆分已经做过，继续按文件行数机械拆分的收益低于修复当前闭环问题。文档也需统一：旧清单仍把多币种列为 WONT，10 月 8 日正文与后续工作区实现也有差异，应以本报告的代码和验证基线安排下一轮任务。
