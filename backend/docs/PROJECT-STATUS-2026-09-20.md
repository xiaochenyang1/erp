**ERP 项目现状与下一步建议 · 2026-09-20**

结论：核心 ERP 主干已经形成较完整的实现，当前处于“多币种功能收尾、业务正确性修补、发布验收”阶段。现有证据不足以认定可以正式上线。最大的开发缺口集中在多币种跨模块金额口径，而上线缺口集中在当前候选的端到端验证和真实环境验收。

本次依据当前工作区、实际执行的测试、关键业务代码与本地归档进行判断，未修改业务代码。检查基线为分支 `refactor/postrelease-sql-gate`、HEAD `5cd5dca`；检查开始时有 45 个已跟踪文件被修改，另有 11 个未跟踪源码/迁移/测试文件，以及 `release-evidence/`。因此测试对象是“HEAD 加未提交修改”，不能把结果归属于 HEAD 单独对应的版本。

不采用统一完成百分比：现有范围涵盖轻量 ERP 主干、正在扩展的多币种，以及明确列入后续阶段的资产、税务等能力，缺少可加权的统一验收分母。

当前规模为 74 个 Vue 页面、78 个后端 Controller、458 个 Java 测试类、241 个前端测试文件。数据库迁移共 162 个脚本，最高编号 V165；这些数量用于说明规模，不等于业务验收完成度。

| 业务领域 | 当前已有实现 | 当前阶段与边界 |
|---|---|---|
| 系统与基础资料 | 公司/账套隔离、角色权限、数据范围、用户组织、商品、客户、供应商、仓库库位、附件、通知、审计、导入 | 主干已实现；公司/账套隔离不等于完整 SaaS 租户运营 |
| 采购 | 请购、询价报价与选标、订单、收货、退货、应付、付款核销 | 主链路已实现，需在最新候选复验业务全流程 |
| 销售 | 报价、订单审批、授信、库存预占、发货、签收、退货、应收、收款 | 主链路已实现；多币种授信计算尚有实际缺口 |
| 库存 | 余额/流水、批次效期、FEFO/FIFO、序列号、调拨、调整、盘点、预占、补货、批次谱系与召回 | 覆盖较完整，包含前后端入口和自动化测试 |
| 生产与质检 | BOM、工艺路线、工作中心、工单、领退料、报工、完工/反完工、MRP-lite、IQC/IPQC/OQC | 轻量制造闭环已实现；MRP-lite 不是完整 APS，生产成本报表目前主要体现材料与完工成本 |
| 财务 | 应收应付、收付款、费用、发票登记、凭证、总账明细账、资金对账、账龄、毛利、期间锁账/月结、预算 | 基础财务主干已实现；多币种尚未覆盖全部汇总与控制口径 |
| 合同与协作 | 销售/采购合同、订单绑定、履约回写、预警、附件、版本快照、客户/供应商商品关系 | 已有实际业务实现和页面，不属于纯规划 |
| 工作流与运营 | 审批配置/待办/转签/超时升级、异常工单、规则与 SLA、看板、业务追踪、验收中心 | 主体已实现；当前 UI 全流程通过证据不足 |

以上判断来自接口、业务服务、页面、迁移与测试的交叉核对，不代表逐页面人工验收完成。代表性实现包括 `production/order/controller/ProductionOrderController.java`、`inventory/mrp/controller/MrpPlanController.java`、`commercial/contract/controller/ContractController.java`、`finance/budget/controller/BudgetController.java`，均位于 `backend/src/main/java/com/tuowei/erp/`。

**尚未收尾的开发与正确性问题**

1. **多币种授信控制没有统一到本位币，已在内存调用当前编译代码复现。**

   [SalesCreditEvaluator.java](/Users/xiao/Desktop/python/erp/backend/src/main/java/com/tuowei/erp/sales/order/service/SalesCreditEvaluator.java:71) 使用订单原币总额，应收敞口和未发货订单敞口也使用原币金额；[SalesOrderWorkflowService.java](/Users/xiao/Desktop/python/erp/backend/src/main/java/com/tuowei/erp/sales/order/service/SalesOrderWorkflowService.java:90) 在提交和审批时实际调用这一校验。

   复现输入：客户额度 5000，本位币为人民币，订单 1000 美元，汇率 7，本位币订单金额 7000，既有敞口为 0。实际输出为 `projectedExposure=1000.00, exceeded=false`，授信校验放行。该验证使用空数据 Mapper 代理，没有访问数据库，也没有写入业务数据。应修正当前订单、既有应收、部分发货敞口和预览接口，并覆盖跨币种组合测试。

2. **多币种的报表和看板口径仍有遗漏，不能认定“报表口径已全面收口”。**

   | 位置 | 当前代码行为 | 影响 |
   |---|---|---|
   | [GrossMarginQueryService.java](/Users/xiao/Desktop/python/erp/backend/src/main/java/com/tuowei/erp/finance/margin/service/GrossMarginQueryService.java:35) | 销售额直接 `sum(l.amount)`，成本取库存流水金额 | 外币收入与本位币成本口径不一致，毛利会失真 |
   | [PartnerStatementService.java](/Users/xiao/Desktop/python/erp/backend/src/main/java/com/tuowei/erp/finance/statement/service/PartnerStatementService.java:88) | 应收应付原币金额、收付款原币金额直接形成往来余额，未按币种拆分 | 同一客户/供应商的多个币种会混算 |
   | [SupplierPayableExposureService.java](/Users/xiao/Desktop/python/erp/backend/src/main/java/com/tuowei/erp/masterdata/supplier/service/SupplierPayableExposureService.java:55) | 应付余额、采购承诺金额均直接用原币金额 | 应付敞口缺少统一币种口径 |
   | [OperationsDashboardPresentationService.java](/Users/xiao/Desktop/python/erp/backend/src/main/java/com/tuowei/erp/dashboard/service/OperationsDashboardPresentationService.java:183) | 未结应收/应付合计为原币 `originalAmount - settledAmount` 的加总 | 看板金额可能与已切换本位币的账龄报表不一致 |
   | [OrderReportQueryService.java](/Users/xiao/Desktop/python/erp/backend/src/main/java/com/tuowei/erp/report/service/OrderReportQueryService.java:218) | 订单报表返回原币总额，响应 `OrderReportResponse` 无币种/汇率字段 | 报表和导出无法明确区分金额币种 |

   这些是代码检查确认的口径缺口，尚未逐项进行数据库与 UI 场景复现。应统一定义：跨币种汇总使用本位币，原币展示同时携带币种；往来对账还需明确核销、汇兑差额和预收预付的展示方式。

3. **供应商敞口还遗漏部分核销状态。**

   上述 `SupplierPayableExposureService` 只查询 `status=UNSETTLED`，付款服务会把部分付款后的应付标为 `PARTIALLY_SETTLED`。因此该概览会漏掉部分付款后的剩余应付，与是否使用外币无关。还需结合采购退货确认净应付敞口定义。

4. **多笔核销的尾差处理尚未形成“单据金额等于资金过账金额”的闭环。**

   [ReceiptCommandService.java](/Users/xiao/Desktop/python/erp/backend/src/main/java/com/tuowei/erp/finance/receipt/service/ReceiptCommandService.java:99) 的单据本位币金额按总额乘汇率保存，过账则汇总各核销行取整后的金额。[SettlementPostingAmounts.java](/Users/xiao/Desktop/python/erp/backend/src/main/java/com/tuowei/erp/finance/posting/SettlementPostingAmounts.java:38) 的注释也明确允许资金分录与单据 `base_amount` 相差几分。

   本轮通过当前计算类复现：收款原币 0.02，汇率 2.5，分配到两笔各 0.01 的核销；单据本位币为 0.050000，当前资金过账计算结果为 0.06。此验证覆盖金额计算路径，未执行完整收款 API。需要与财务确定尾差分配规则，并验证整单金额、核销明细、凭证、作废冲回及最终结清余额一致。

5. **期末未实现汇兑损益重估仍未开发。**

   本轮新增 V164/V165、核销币种约束、已实现汇兑损益凭证、作废冲回、账龄/结算本位币字段、汇兑损益报表已有实际代码与测试，但仍处于未提交工作区。

   尚缺：期末未结清外币应收应付重估、调汇凭证、下期初冲回、重复执行保护及月结检查衔接。[AccountPeriodCloseChecker.java](/Users/xiao/Desktop/python/erp/backend/src/main/java/com/tuowei/erp/finance/period/service/AccountPeriodCloseChecker.java:65) 当前检查列表没有调汇项；[WHAT_IS_MISSING.md](/Users/xiao/Desktop/python/erp/backend/docs/WHAT_IS_MISSING.md:27) 也明确保留该缺口。若要支持完整外币月结，应在财务验收前补齐；它不属于本轮已实现结算汇兑损益的自动延伸。

6. **工程整理仍在进行，但优先级低于金额正确性。**

   Service 和 Vue 已经做了大量拆分。当前仍有约 500–669 行的复杂服务、约 1000 行的页面；行数本身不能证明有缺陷，建议围绕后续要修改的业务逐步整理。OpenAPI 自动生成类型目前只有商品模块试点，`check:contracts` 通过不能解读为所有接口都有生成契约覆盖。

**上线尚缺的证据与工作**

| 项目 | 当前证据 | 下一步 |
|---|---|---|
| 候选版本冻结 | 当前存在未提交源码、V164/V165 和测试 | 先修正业务问题，再按边界整理提交并确定干净候选 |
| 自动化发布验证 | 本轮默认测试与前端检查通过 | 在候选上运行 Testcontainers 发布 profile、PowerShell 发布检查及产物复验 |
| UI 全流程 | 9 月 16 日归档：页面 27/28 通过，工作流 13/36 通过 | 在修复后的候选重跑，按新报告区分脚本/测试数据问题与产品缺陷 |
| 真实预生产 | 本地材料未提供当前候选的独立真实环境全量通过证据 | 验证 MySQL/Redis、迁移、关键业务链路、数据隔离、备份恢复与回滚 |
| 财务/质检签字 | 当前清单仍标 `BLOCKED_HUMAN`，旧 RC 也记录等待签字 | 使用新候选样例完成财务 F1–F12、质检 Q1–Q5 验收 |
| 运维监控 | 有 Prometheus/Alertmanager 等配置与脚本，缺目标环境完成证据 | 完成实际部署、告警触发与恢复演练，归档结果 |
| 正式发布 | 上述前置项未满足 | 全部门禁完成后，由授权人决定 GO 并打正式版本 |

归档 UI 报告为 [ui-smoke-report.json](/Users/xiao/Desktop/python/erp/release-evidence/e559f46-20260916/ui-smoke/ui-smoke-report.json)，绑定旧候选 `e559f46`。其失败包括客户测试数据缺必填类型、附件闸门和选择器超时。之后 `5502d48` 与 `5cd5dca` 修复了部分测试数据/文案/选择器，但本地归档没有对应的新全绿报告。**23 个旧工作流失败不等于当前仍有 23 个产品缺陷，也不能据修复提交推定所有场景已通过。**

`release-evidence/e559f46-20260916/verification-summary.txt`、JAR 和 SBOM 属于旧版本证据；该目录未包含当前候选的完整 release-check、真实预生产、人工签字和 GO 归档。对外部平台是否另外已有验收，本次没有取得可核验结果，不能把“仓库没有证据”直接推断为“外部绝对没有执行”。

**本轮实际验证结果**

| 检查 | 命令/方式 | 结果 |
|---|---|---|
| 后端默认测试 | `cd backend && ./scripts/test-local.sh test`，一次性 MySQL 8.4 | 2230 项，0 failures、0 errors、3 skipped，BUILD SUCCESS |
| 前端全量单测 | `cd frontend && npm test -- --reporter=dot` | 241 个文件，1105 项通过 |
| 前端类型 | `npm run type-check` | 通过 |
| 前端代码检查 | `npm run lint` | 通过 |
| 前后端契约检查 | `npm run check:contracts` | 通过，覆盖范围依脚本定义，含商品 OpenAPI 漂移检查 |
| 前端生产构建 | `npm run build` | 通过 |
| 改动格式检查 | `git diff --check` | 通过 |
| 授信与核销尾差 | 临时 Java 内存调用当前编译类 | 复现上述授信放行和 0.05/0.06 尾差 |

3 项跳过来自需要 PowerShell 的环境生成/归档复验行为测试；本次未运行 `-Ptestcontainers -Derp.testcontainers.enabled=true clean package`，也未重跑 UI smoke、部署或获取真人签字。因此本轮通过结果证明当前已有测试通过，不证明未覆盖的跨币种业务正确，也不构成正式发布通过结论。

本机原始核验日志位于 `/private/tmp/erp-audit-20260920-*.log`，内存复现代码位于 `/private/tmp/ErpReadOnlyAudit.java`；临时文件仅用于本次分析，不是正式发布归档。

**建议的推进顺序与完成标准**

1. **先修金额正确性。** 统一授信、供应商敞口、毛利、往来对账、看板、订单报表的币种口径；处理部分核销漏计和分摊尾差。完成标准：同一业务在单据、应收应付、凭证、库存和报表之间能够核对，包含多币种、不同汇率、多次部分核销、最后一笔结清、作废和退货场景。
2. **补外币月结闭环。** 若版本目标包含完整多币种财务，实施期末调汇、冲回和期间检查；与财务确认重估范围及凭证口径。完成标准：跨期外币未结清余额可重估、可追踪、不会重复调汇，期间锁定后不可绕过。
3. **冻结候选，完成全链路回归。** 整理未提交实现，在同一干净候选上跑后端发布 profile、前端检查、采购到付款/销售到收款/生产与质检、权限和跨币种 API/UI smoke，归档匹配 commit 的迁移、JAR、SBOM 和测试报告。修复后应重新绑定新候选。
4. **完成预生产与业务上线验收。** 开发负责可重复构建和业务问题收口，财务与质检负责业务签字，运维负责目标环境、监控、备份恢复与回滚证据，授权人做最终 GO 决策。
5. **稳定后扩展新业务。** 按实际经营需要安排固定资产、税务基础、客户/供应商门户、售后维修/设备管理、高级排程、自助 BI；这些在扩展路线图中属于后续阶段，不能并入“现有轻量 ERP 必须已完成”的口径。

现有文档还需要一次状态校准：[未完成.md](/Users/xiao/Desktop/python/erp/backend/docs/未完成.md:240) 的旧“边界外”段落仍把多币种列为 WONT，而文件开头和 [FEATURE-EXPANSION-ROADMAP.md](/Users/xiao/Desktop/python/erp/backend/docs/FEATURE-EXPANSION-ROADMAP.md:9) 已把它纳入开发；历史测试数字与候选说明也有不同时间快照。应使用“已实现、待验证、待开发、后续范围”分别记录，避免继续用总览中的 DONE 数量替代当前验收结论。
