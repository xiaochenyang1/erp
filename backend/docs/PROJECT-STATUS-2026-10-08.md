**ERP 项目现状与下一步建议 · 2026-10-08**

更新：正文保留当日初始审计快照。第 1 项外币往来对账、第 2 项期末调汇已分阶段修复并完成定向验证，详见文末两节实施进展；收付款过账尾差、退货敞口和本位币变更保护仍待处理。

当前项目处于「核心业务已成形、财务正确性修补、全流程回归和上线验收」阶段。按现有轻量 ERP 范围，大部分业务能力已有前后端实现，下一步最需要投入的是金额与对账正确性、浏览器业务流程和发布验收。

现有证据不足以认定可以正式上线。本次在当前代码中复现了外币往来对账、收款尾差和期末调汇问题；因此也不能继续沿用旧执行板中「P0 剩余 0 可编码」作为整体判断。

检查基线为分支 `refactor/postrelease-sql-gate`、HEAD `73917294748a493c55ce42fe037cf373f3ab625d`，加检查开始时已有的 3 个未提交文件：采购订单查询服务、采购订单响应 DTO 及对应测试。分支相对本地保存的远端跟踪引用领先 24 个提交；本轮没有联网刷新远端状态。业务源码未在本轮修改。

**已实现的业务范围与边界**

| 领域 | 当前已有能力 | 所处阶段与边界 |
|---|---|---|
| 系统与基础资料 | 用户、角色、菜单、组织、数据范围、公司/账套隔离、商品、客户、供应商、仓库库位、附件、通知、审计、导入 | 主体已实现；公司/账套隔离不能直接等同于完整 SaaS 租户运营 |
| 采购 | 请购、询价报价与选标、价目、订单、审批、分批收货、退货、应付和付款核销 | 主链路已实现；采购订单币种返回字段修复仍在未提交工作区，需纳入候选 |
| 销售 | 报价、价目、订单审批、授信、库存预占、发货、物流/签收、退货、应收与收款 | 主链路已实现；授信已使用本位币，浏览器完整流程仍需复验 |
| 库存 | 余额/流水、批次效期、FEFO/FIFO、序列号、库位、调拨、调整、盘点、预占、补货、批次谱系和召回 | 覆盖较完整；最新保存的 UI 报告仍有调整、调拨、盘点场景失败 |
| 生产与质检 | BOM、工艺路线、工作中心、工单、领退料、报工、完工/反完工、MRP-lite、IQC/IPQC/OQC | 轻量制造能力已实现；MRP-lite 尚不具备完整 APS 能力，生产成本报表主要覆盖材料、完工与在制金额 |
| 财务 | 应收应付、收付款、费用、发票登记、凭证、总账明细账、资金对账、账龄、毛利、预算、期间/月结 | 基础业务已实现；外币对账、尾差和调汇边界仍有明确问题 |
| 多币种 | 账套本位币、币种与有效期汇率、业务单据快照、下游过账、已实现汇兑损益、期末调汇/下期冲回/撤销、期初导入币种 | 已有实现和前端入口，不能再列作整体「未开发」；尚未达到完整业务验收状态 |
| 合同、审批与运营 | 采购/销售合同、订单绑定、履约回写、预警、附件、版本历史、往来方商品关系；审批配置、转签、超时升级、异常工单/SLA、看板与追踪 | 主体已实现，需随当前候选做跨模块回归 |

本表依据当前 Controller、业务服务、前端 API/页面、数据库迁移和测试交叉检查，不表示每个页面都已人工验收。当前有 78 个后端 Controller、74 个 Vue 业务视图文件、479 个 Java 测试类、243 个前端测试文件；Flyway 共 163 个迁移脚本，最高编号 V166。数量说明项目规模，不用来计算完成百分比。

代表性边界证据：[生产成本报表](../src/main/java/com/tuowei/erp/report/service/ProductionCostReportService.java)、[期间调汇 API](../src/main/java/com/tuowei/erp/finance/period/controller/AccountPeriodController.java)、[期间页面](../../frontend/src/views/finance/periods/index.vue)。

9 月 20 日分析中的部分问题已经得到后续开发：销售授信使用本位币，供应商敞口已纳入部分核销状态，毛利、订单报表、看板与往来对账已接入本位币字段，期末调汇已有 V166 和业务实现。以下列的是当前仍存在的问题。

**需要继续开发或修正的重点**

1. **外币往来对账不能与已结清子账对平（后续已修复，见文末）。**

   当前 [PartnerStatementService](../src/main/java/com/tuowei/erp/finance/statement/service/PartnerStatementService.java) 把应收按入账汇率折算，把收款按结算汇率折算后直接相减，未补上相应汇兑损益调整。

   本轮通过当前编译类复现：应收 100 USD，入账汇率 7；全额收到 100 USD，结算汇率 7.2。应收子账剩余为 `0.00`，客户往来对账单期末余额却为 `-20.00`，明细只有 `RECEIVABLE` 和 `RECEIPT`。供应商方向存在相同计算结构。

   下一步应明确往来对账的核销、汇兑损益、预收预付展示规则，使全额结清后的往来余额与子账一致，并覆盖部分核销、退货和作废场景。仅把金额换成本位币还不够。

2. **期末调汇已有功能，但期间口径和重复执行检查尚不完整（后续已修复，见文末）。**

   [FxRevaluationService](../src/main/java/com/tuowei/erp/finance/fx/service/FxRevaluationService.java) 的 `openReceivables/openPayables` 只按租户、删除标志和当前未结状态取数，没有按目标期末过滤 `biz_date`，也没有根据核销日期还原期末余额。

   当前类的内存调用中，6 月 30 日调汇把 7 月 5 日的一笔 100 USD 应收计入，产生 20 元调整；捕获的实际查询条件没有日期条件。这项验证使用 Mock Mapper 返回样例记录，并核对生成的查询条件，没有执行完整 MySQL 查询。由当前余额推回历史期末时，还必须处理期后收付款与撤销。

   重复执行检查只比较全部外币原币余额相加得到的 `openOriginalTotal`，未比较币种构成、入账金额和期末汇率。另一组内存复现中：100 USD、入账本位币 700，期末汇率 7.2 时调汇 20；把期末有效汇率改为 7.5 后再次执行，仍返回旧调整 20 和 `alreadyPosted=true`，没有要求撤销重算。此时按新汇率计算的调整应为 50。

   [AccountPeriodCloseChecker](../src/main/java/com/tuowei/erp/finance/period/service/AccountPeriodCloseChecker.java) 的调汇检查也使用同一原币总和比较。下一步应建立按期末还原的余额、按币种/科目或明细保存的调汇依据，以及覆盖汇率和构成变化的重算与月结阻断规则。

3. **核销尾差处理仍可能造成单据、凭证与汇兑损益报表不一致。**

   [SettlementPostingAmounts.alignCashTo](../src/main/java/com/tuowei/erp/finance/posting/SettlementPostingAmounts.java) 只调整绝对值不超过 0.05 的尾差，调整写在凭证汇兑损益金额中。[收款服务](../src/main/java/com/tuowei/erp/finance/receipt/service/ReceiptCommandService.java) 与 [付款服务](../src/main/java/com/tuowei/erp/finance/payment/service/PaymentCommandService.java) 均使用它，核销明细的汇兑损益字段则在调整前保存。

   本轮直接调用当前收款创建服务、以 Mock 持久层和过账组件捕获结果：

   | 场景（入账、结算汇率均为 2.5） | 单据本位币 | 传给凭证过账的资金金额 | 凭证汇兑损益 / 核销明细汇兑损益合计 |
   |---|---:|---:|---:|
   | 2 笔各 0.01 USD，总额 0.02 USD | 0.05 | 0.05 | -0.01 / 0 |
   | 12 笔各 0.01 USD，总额 0.12 USD | 0.30 | 0.36 | 0 / 0 |

   [汇兑损益报表](../src/main/java/com/tuowei/erp/report/mapper/FxGainLossReportMapper.java) 按核销明细的 `fx_gain_loss_amount` 取数，第一种场景不会反映凭证中的尾差。第二种场景因超过 0.05 而保留资金金额差异。

   下一步应按整单资金总额设计分摊规则，保存可复验的尾差/分摊依据，保证核销明细、凭证、报表及作废冲回一致，不能依靠固定五分钱阈值兜底。

4. **供应商敞口对采购退货的取数仍不完整。**

   这是当前代码交叉检查确认的查询口径问题，尚未通过真实数据库 API 重放：[FinancePostingService](../src/main/java/com/tuowei/erp/finance/posting/FinancePostingService.java) 把采购退货记为 `DECREASE`；[FinanceSubledgerPostingService](../src/main/java/com/tuowei/erp/finance/posting/FinanceSubledgerPostingService.java) 将其状态设为 `OFFSET`；[SupplierPayableExposureService](../src/main/java/com/tuowei/erp/masterdata/supplier/service/SupplierPayableExposureService.java) 却只查询 `UNSETTLED` 和 `PARTIALLY_SETTLED`。

   因此，虽然聚合函数已经支持 `DECREASE` 减少敞口，实际退货记录仍会在查询阶段被排除。现有相关单测直接 Mock 返回包含退货的列表，且退货样例没有状态，不能验证数据库筛选条件。应统一净应付口径，并增加带真实状态和查询条件的回归。

5. **本位币配置还需补充已有业务数据的保护规则。**

   静态检查发现，[CurrencyAdminCommandService.setBaseCurrency](../src/main/java/com/tuowei/erp/finance/currency/service/CurrencyAdminCommandService.java) 校验目标币种有效后可直接更新账套本位币，没有检查账套是否已有单据、凭证或余额，也没有配套转换历史本位币金额。

   若在已有人民币历史金额的账套中直接切换本位币，新旧金额可能被放在不同币种口径下汇总。应明确并实现「仅空账套可设定/修改」或正式的本位币变更流程。本轮未对真实账套执行此操作。

采购订单还有一项**已修复、待纳入版本**的工作：当前 3 个未提交文件补回详情和列表的 `currencyCode/exchangeRate/baseTotalAmount/baseTotalTaxAmount`，避免外币订单编辑时丢失币种快照。对应测试本轮通过；整理候选时应保留并提交这项修复。

**验证结果与上线缺口**

| 项目 | 本次确认的证据 | 可以得出的结论 |
|---|---|---|
| 前端单测 | 本轮实际执行：243 文件、1138 项通过 | 现有单测通过 |
| 前端类型、Lint、契约、构建 | 本轮 `type-check`、`lint`、`check:contracts`、`build` 全部通过 | 可通过当前前端构建检查；OpenAPI 自动生成类型仍只有商品模块试点 |
| 后端定向验证 | 本轮实际执行 10 个测试类、64 项，0 failures/errors/skipped；Maven 编译通过 | 覆盖采购订单查询、授信、供应商敞口、币种、期初导入、收付汇兑差额及调汇现有用例；不能代表全量集成已通过 |
| 财务问题复现 | 当前编译类，临时 Java 程序，持久层/过账组件为 Mock | 已证实上述具体服务计算和查询构造行为；没有写入业务数据库，也没有运行完整 HTTP/UI 链路 |
| 9 月 30 日后端全量记录 | 文档记录 2361 项、8 skipped；现存 XML 显示其中 5 项因缺少 `erp.testcontainers.enabled` 跳过 | 当次有一次性 MySQL 普通集成验证，但不能宣称专门的 Testcontainers 组全部执行 |
| 最新保存的浏览器报告 | `backend/target/ui-smoke-report.json`，时间 2026-09-29，36 个 workflow 中 26 通过、10 失败，route results 为 0 | 未取得当前候选完整 UI 通过证据；报告没有 commit 字段，不能把 10 个失败直接等同于当前 10 个产品缺陷 |
| 候选与发布包 | 当前仍有 3 个未提交源码/测试文件；未找到绑定当前候选的完整独立发布归档 | 仍需冻结版本、生成并复验对应的 JAR/SBOM/迁移和测试证据 |
| 业务与运维验收 | 仓库执行板仍将财务、质检、监控、独立真实预生产和正式发布列为待完成 | 本轮未取得外部平台或真人签字的新证据，不能宣布发布就绪 |

9 月 30 日记录的命令是 `scripts/test-local.sh -Ptestcontainers test`，未带 `-Derp.testcontainers.enabled=true`。5 项跳过分别来自 MySQL 迁移 1 项、MySQL 库存过账 2 项、Redis 登录限流 2 项；另外 3 项是 PowerShell 相关行为测试。XML 的跳过原因明确为 `System property [erp.testcontainers.enabled] does not exist`，相关测试源码也有条件注解。正式 [release-check](../scripts/release-check.ps1) 的 `-IncludeTestcontainers` 分支会同时补上 profile 与启用参数。

保存的 10 个 UI 失败涉及：销售建单审批、收款核销、采购退货、采购到付款、库存调整/调拨/盘点、生产领料完工、采购收货草稿编辑、采购退货草稿编辑。错误包含选项/按钮/状态回查超时，应在干净测试数据和新候选上逐项区分脚本、测试数据、页面与后端问题。本轮没有重跑浏览器 smoke。

本机当前 Docker 的 Colima socket 不存在，且未安装 `pwsh`，因此本轮没有重跑 MySQL/Redis 全量发布门禁或正式 `release-check`。本轮运行前，`target/surefire-reports` 本身已混有 9 月 30 日和 10 月 1 日的报告；不能把目录合计数视为一次统一版本的全量结果。

现有 [财务/质检验收清单](finance-qc-acceptance-runbook.md) 中 F1–F12、Q1–Q5 和独立预生产仍标为未完成，本轮没有取得新的签字证据。新候选还应补充多币种、期末调汇、IPQC/OQC 等新增能力的验收场景，避免沿用旧范围签字。

**后续功能范围**

[功能扩展路线图](FEATURE-EXPANSION-ROADMAP.md) 仍将固定资产、税务基础、供应商协同/客户门户、售后维修/设备管理、高级生产排程、自助 BI 列为后续阶段，当前没有对应完整业务模块。发票登记及单据税率字段不等于税务申报系统；材料/完工成本报表也不等于包含人工和制造费用分摊的完整成本核算。

税局接口、集团合并报表、完整 SaaS 租户运营、移动端、自由 BPM 和复杂 APS 不在当前第一阶段范围内。是否启动这些模块应按实际经营需要排期，不应与当前轻量 ERP 的验收缺口混为一谈。

工程方面已完成大量 Service 与 Vue 拆分。旧执行板 E-1 仍显示 `DOING`，但最新记录已说明机械拆分候选基本耗尽；继续按文件行数拆服务的优先级应低于本报告确认的业务正确性问题。还需统一文档状态：`WHAT_IS_MISSING.md`、9 月 20 日报告和 `未完成.md` 的顶部统计存在时间快照冲突，后者「边界外」仍把已经开发的多币种列为 WONT。

**建议按以下顺序推进**

| 顺序 | 主要负责人 | 具体工作 | 完成标准 |
|---|---|---|---|
| 1 | 开发 + 财务确认口径 | 修正外币往来对账、尾差分摊、期末余额与调汇重算、退货敞口、本位币变更保护 | 同一组样例的单据、子账、凭证、资金和报表可核对；覆盖部分/多笔核销、全额结清、退货、作废、跨期及汇率变化 |
| 2 | 开发/测试 | 复验采购币种修复，修复并稳定 API/UI 全链路，补充上述真实数据场景 | 采购到付款、销售到收款、生产与质检、库存调整/调拨/盘点、权限隔离和多币种场景在同一版本通过，失败有明确归因 |
| 3 | 开发/发布负责人 | 整理提交、冻结干净候选；跑前后端门禁及完整 Testcontainers 发布构建，归档和复验产物 | commit、JAR、SBOM、迁移、测试、API/UI smoke、release-check 相互对应；所需测试确实执行，跳过项有明确处理 |
| 4 | 财务、质检、运维、业务负责人 | 在独立预生产验证真实主数据/期初导入、业务权限、监控告警、备份恢复和回滚，完成签字 | 财务/质检逐项认可当前候选，运维演练有证据，授权人据此作 GO 决策并发布 |
| 5 | 产品/业务 + 开发 | 稳定运行后按需要启动资产、税务、门户或排程等后续模块 | 每个模块先明确范围和验收样例，再独立开发、测试与发布 |

第 3 步可使用仓库现有入口，在具备 Docker 的环境运行：

```bash
cd backend
./scripts/test-local.sh -Ptestcontainers -Derp.testcontainers.enabled=true clean package
```

正式发布还需在已配置普通测试数据库、Docker 和 PowerShell 的环境执行 `pwsh -File scripts/release-check.ps1 -IncludeTestcontainers` 及产物复验；预生产流程参见 [business-readiness-checklist.md](business-readiness-checklist.md)。不要将允许脏工作树的本地排查报告作为正式发布证据。

本轮验证原始日志、64 项测试报告副本、旧报告摘要、源码基线哈希以及临时复现程序位于 `/private/tmp/erp-audit-20261008/`。关键文件为 `frontend-results.json`、`backend-unit.log`、`reproduction.log`、`ErpAuditReproduction.java`、`previous-ui-summary.json` 和 `previous-backend-report-index.json`。这些是本次分析材料，不是正式发布归档；本报告已经记录影响判断的输入、输出和代码来源，避免后续依赖临时目录才能理解结论。

**往来对账实施进展 · 2026-10-08**

已在当前工作区修复客户和供应商对账单的外币结算余额。核心实现为 [PartnerStatementSettlementService](../src/main/java/com/tuowei/erp/finance/statement/service/PartnerStatementSettlementService.java)，由 [PartnerStatementService](../src/main/java/com/tuowei/erp/finance/statement/service/PartnerStatementService.java) 生成对账明细。

- 保留收付款单实际本位币金额；在同一结算日期和单号下分别列出非零的 `FX_ADJUSTMENT`（汇兑调整）和 `ROUNDING_ADJUSTMENT`（尾差调整）。金额已不足本位币一分的收付款仍保留明细行。
- 核销冲减优先使用已保存的入账本位币快照，旧记录的零默认字段按历史单据汇率还原；按每笔应收/应付的累计冲减额取整，避免重复部分核销累积尾差。预收预付按结算汇率保留为往来贷方余额。
- 期初包含查询期间之前的有效结算及其调整，期后结算不影响目标期间；已作废、删除及其他往来单位、公司、账套的收付款不计入。核销明细缺失或引用当前往来单位以外的子账会明确报错。
- API 返回账套本位币代码，页面与打印据此显示币种，提供中英文调整名称和金额口径说明。

每次结算的对账余额变动为：`−实际收付款 + 汇兑调整 + 尾差调整 = −核销入账金额 −预收预付金额`。其中尾差用于衔接单据总额、子账累计冲减额与核销汇兑快照各自的取整结果。

原复现案例现为：100 USD 按 7 入账产生 700，应收/应付全额按 7.2 结清保留 720 收付款，增加 20 汇兑调整，期末余额 `700 − 720 + 20 = 0`。同汇率 2.5 下，一笔 0.02 USD 分两次各 0.01 结清，以及 12 笔各 0.01 USD 合并结清，均能展示相应尾差并得到零余额。

| 本次实施验证 | 结果与边界 |
|---|---|
| 后端回归 | 9 个测试类、87 项通过，0 failures/errors/skipped；含原采购订单修复的 22 项测试 |
| 新增对账 SQL 测试 | [PartnerStatementServiceTest](../src/test/java/com/tuowei/erp/finance/statement/PartnerStatementServiceTest.java) 41 项通过；在独立 H2 数据库执行真实 MyBatis 查询，覆盖双向汇率变化、部分/多笔核销、预收预付、跨月、期后结算、退货、作废、历史快照、尾差及隔离边界；另有 5 项原租户边界测试通过 |
| 前端全量单测 | 245 个文件、1,143 项通过，含 API 币种兼容、金额展示和真实打印 HTML 内容验证 |
| 前端工程检查 | `type-check`、`lint`、`check:contracts`、`build` 均通过 |
| 工作区保护 | 初始 3 个采购订单修改文件的 SHA-256 与审计开始时一致 |

本次没有执行 MySQL/Flyway 集成、完整 HTTP/浏览器业务流程或正式发布门禁；H2 查询测试不代替这些验证。对账调整属于查询展示，本次未修改收付款凭证过账或汇兑损益报表。第 2–5 项问题仍未关闭：期末调汇、收付款过账尾差、退货敞口、本位币变更保护。下一项优先修正期末调汇的期末余额还原与重复执行检查，同时保留凭证尾差问题的独立修复任务。

后端复验命令：

```bash
cd backend
./mvnw -o -B '-Dtest=PartnerStatementServiceTest,PartnerStatementServiceTenantBoundaryTest,ReceiptExchangeDifferenceTest,PaymentExchangeDifferenceTest,SettlementPostingAmountsTest,BaseCurrencyServiceTest,BusinessReadOnlyTransactionStructureTest,NativeSqlTenantScopeConfigurationTest,PurchaseOrderQueryServiceTest' test
```

本次实施日志位于 `/private/tmp/erp-statement-backend-final.log`、`/private/tmp/erp-statement-frontend-final.log` 及同目录 `erp-statement-{typecheck,lint,contracts,build}.log`；修复前失败案例保存在 `erp-statement-red.log`。这些日志对应当前工作区验证，尚未绑定正式发布候选。

**期末调汇实施进展 · 2026-10-08**

已修复第 2 项的期间口径、重复执行和月结校验。[FxRevaluationBasisService](../src/main/java/com/tuowei/erp/finance/fx/service/FxRevaluationBasisService.java) 生成共同计算依据，[FxRevaluationService](../src/main/java/com/tuowei/erp/finance/fx/service/FxRevaluationService.java) 和月结检查均使用它。

- [期末余额查询](../src/main/java/com/tuowei/erp/finance/fx/mapper/FxRevaluationBalanceMapper.java) 限定单据业务日期不晚于期末，纳入当前已结清但期后才核销的记录，并保留 `OFFSET` 退货的减少方向。各查询和核销关联均限定公司、账套及对应往来单位。
- 原币和入账本位币的期末已核销额分别按 `当前累计已核销 − 期后有效核销` 还原。这保留了期初导入中没有收付款明细的已核销部分，也避免期后部分或全额核销改变历史期末余额。取整前保留本位币快照的六位小数；历史零默认字段按入账汇率还原。
- 已作废收付款不参与核销还原。该口径与现有收付款作废凭证一致：冲回日期是原单业务日期，`cancelled_time` 是操作时间，不作为凭证业务日期。异常核销金额无法还原时会阻止调汇和月结。
- 保存逐笔期末原币余额、本位币账面余额、币种、期末汇率及调整金额，并包含账套、期间和本位币上下文。稳定排序并统一数字表示后保存 JSON 与 SHA-256 摘要；重复执行同时核对依据和应收/应付调整金额。
- 月结不再用跨币种原币总和判断是否需要调汇。币种、来源明细、账面金额或期末汇率变化均要求先撤销重算；即使合计为零，或新的期末余额已经归零，只要仍有不匹配的已过账调汇，就会阻止月结。缺失有效汇率会作为明确的失败检查项返回。

| 场景 | 修复后结果 |
|---|---|
| 7 月 5 日的 100 USD 应收，查询 6 月末调汇 | 不计入 6 月余额或调整 |
| 6 月应收 100 USD、入账汇率 7，7 月全额结清；6 月末汇率 7.2 | 6 月末仍按未结 100 USD、账面 700 计算，调整 20 |
| 同一笔 100 USD，期末汇率从 7.2 改为 7.5 后再次调汇 | 阻止复用旧 20；撤销后重算为 50，月结使用新依据 |
| 100 USD 应收与 100 EUR 减少方向应付，原币直接相加为零 | 分别计算币种和应收/应付调整，仍要求完成调汇 |

新增 [V167](../src/main/resources/db/migration/V167__fx_revaluation_basis.sql)，仅为调汇表追加 `basis_hash`、`basis_json`；不改已发布的 V166，不回填未经保存的历史依据。旧调汇记录保留金额及空依据，未结账期间须撤销后重算；已锁定的期间需要先解锁，并满足现有本期及冲回期间的开放检查。迁移不自动改动凭证或已关闭期间。

| 本阶段验证 | 结果与边界 |
|---|---|
| 修复前复现 | 新增真实 SQL 测试的 7 个案例全部失败，分别对应期后单据、期后结清、跨期部分核销及汇率变化 |
| 调汇专项 | 55 项通过，其中 [FxRevaluationWorkflowTest](../src/test/java/com/tuowei/erp/finance/fx/FxRevaluationWorkflowTest.java) 47 项执行真实 H2/MyBatis 查询、调汇记录读写及乐观锁；汇率提供、凭证过账和其他月结检查使用隔离替身 |
| 迁移 | H2 兼容迁移链的 27 项检查通过；另有 V166→V167 原 SQL 升级测试，验证历史金额与空依据保留、大体积依据可保存 |
| 最终回归 | 17 个测试类、177 项通过，0 failures/errors/skipped，编译通过；含往来对账、收付汇兑、币种、采购订单、租户范围及迁移规范检查 |
| 原有修改 | 本阶段开始时 18 个已修改/新增文件逐一核对哈希；业务源码与测试保留，仅本报告随后追加实施记录 |

本阶段没有执行 MySQL/Redis Testcontainers、完整 Spring Boot HTTP/浏览器业务流程或正式发布门禁。H2 迁移链沿用仓库的兼容处理，不等同于 MySQL 原生迁移验证。本阶段没有新增前端修改，前端测试结果仍参见上一阶段记录。

复验命令：

```bash
cd backend
./mvnw -o -B '-Dtest=FxRevaluationWorkflowTest,FxRevaluationServiceTest,FxRevaluationBasisMigrationTest,AccountPeriodCloseCheckerTenantScopeTest,AccountPeriodServiceDecompositionTest,FlywayMigrationSmokeTest,MigrationHistoryDocumentationTest,NativeSqlTenantScopeConfigurationTest,BusinessReadOnlyTransactionStructureTest,PartnerStatementServiceTest,PartnerStatementServiceTenantBoundaryTest,ReceiptExchangeDifferenceTest,PaymentExchangeDifferenceTest,SettlementPostingAmountsTest,BaseCurrencyServiceTest,CurrencyConversionServiceTest,PurchaseOrderQueryServiceTest' test
```

最终日志为 `/private/tmp/erp-fx-final.log`，修复前复现日志为 `/private/tmp/erp-fx-red.log`。第 3–5 项仍待修复，下一项优先统一收付款单、凭证与汇兑损益报表的尾差口径。
