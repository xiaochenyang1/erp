# P0 人工/环境门禁执行包（逐项可执行版）

**生成时间**：2026-10-16
**适用分支**：`refactor/postrelease-sql-gate`
**基线候选**：以 `git rev-parse --short HEAD` 实时确认；本包生成时工作区干净（`991063b`，即 UI smoke CDP 连接异常处理迁移后的提交）
**门禁来源**：[WHAT_IS_MISSING.md](WHAT_IS_MISSING.md) 第 252–256 行「人工和环境门禁」五项
**责任分工来源**：[RELEASE-CANDIDATE-2026-10-15.md](RELEASE-CANDIDATE-2026-10-15.md) 第 68–76 行

---

## 0. 这个包解决什么问题

`WHAT_IS_MISSING.md` 结论段写明：功能实现已接近闭环，但**不是正式 RC，也不能创建 release tag**，原因是财务、质检、运维和真实预生产人工门禁仍未完成。这五项门禁全部是 `BLOCKED_HUMAN` / `BLOCKED_ENV`，本机自动化无法代替。

本包把五项门禁各自拆成「谁做 / 在哪做 / 做什么 / 看什么算通过 / 证据放哪 / 签字栏」，使责任人拿到即可执行，不需要再去翻散落的 runbook。

**五项门禁一览**

| # | 门禁 | 责任人 | 阻塞类型 | 本包章节 |
|---|------|--------|----------|----------|
| 1 | 财务 F1–F12 真人复核与签字 | 财务 | 需真人业务判断 | §1、§3 |
| 2 | 质检 Q1–Q5 真人复核与签字 | 质检 | 需真人业务判断 | §2、§3 |
| 3 | Prometheus/Grafana/Alertmanager 落地 + 告警触发/恢复演练 | 运维 | 需目标平台 | §4 |
| 4 | 独立真实预生产复验（真 MySQL/Redis/密钥/拓扑） | 运维 + 研发 | 需真实环境 | §5 |
| 5 | GO 决策与 release tag 创建 | 授权业务负责人 | 依赖 1–4 | §6 |

**先读这一句**：技术脚本 GO **不等于**业务签字。`fq-signoff-api-check.cjs` / `fq-signoff-human-items.cjs` 全 PASS 只说明「链路可走通、账务与库存回查一致」，`RELEASE-CANDIDATE-2026-10-15.md` 第 73 行仍将 F/Q 列为「待业务确认」。本包不会改变这个边界。

---

## 1. 门禁 1 · 财务 F1–F12

### 1.1 样例数据必须在新候选上重新生成

**这是本包的首要纠偏项。**

`WHAT_IS_MISSING.md` 第 260 行（历史证据使用边界）明确：既有 RC 证据「证明的是较早候选提交的技术预检结果。当前工作区已经新增业务功能、迁移和重构，因此旧证据只能用于格式和历史追溯，不能直接作为当前候选的签字或发布依据」。

因此 `preprod-signoff-package-2026-07-16.md` 第 73–86 行预填的样例单号（`FE202607150005`、`MV202607150003`、`SD202607160004`、`FR202607160004`、`SRT202607150005`、`PR202607160009`、`FP202607160004`、`PRT202607150005` 等）、`FQ-SIGNOFF-RC-989FBC4-2026-08-24.md` 的 8-24 样例，**都只能当格式模板用**。签字必须基于新候选上重新跑出来的单号。

新样例生成方式（二选一）：

```powershell
# 方式 A：本机干净库等价入口（无独立预生产时），会顺带生成 finance-qc-signoff-seed.md
.\scripts\local-runtime-acceptance.ps1

# 方式 B：真实预生产（需先按 §5.1 准备好环境）
.\scripts\preprod-full-acceptance.ps1 `
  -EnvFile .env.prod `
  -BaseUrl http://127.0.0.1:8080 `
  -Username admin -Password "<预生产密码>" `
  -WarehouseId <活跃仓> `
  -MaterialWarehouseId <材料仓> `
  -FinishedWarehouseId <成品仓> `
  -BusinessDate "<开放期间日期>"
```

两者都会在证据目录生成 `finance-qc-signoff-seed.md`，把可摘抄的样例单号集中列出，供直接填入下方表格。

### 1.2 技术预检（签字前先跑，结果贴进签字单）

```powershell
node scripts\fq-signoff-all.cjs
```

产物：`target/fq-signoff-api-check/all-summary.json`，关键字段 `technicalPass`（`true` 表示技术侧全通过）。包含：

- **正向**：`fq-signoff-api-check.cjs` 覆盖 F1–F9、F12、Q1–Q3；
- **负例**：`fq-signoff-human-items.cjs` 覆盖 F10、F11、Q4、Q5。

`technicalPass=true` 是**必要**条件，不是**充分**条件——仍须财务/质检真人逐项核对并签字。

### 1.3 财务核对表（签字用）

表头与 `finance-qc-acceptance-runbook.md` 第 2.3 节一致；「技术证据」列沿用 `preprod-signoff-package-2026-07-16.md` 第 73–86 行；**样例单号列留空，签字时从 §1.1 的 `finance-qc-signoff-seed.md` 摘抄新候选单号**。

| # | 核对项 | 样例单号（新候选） | 技术证据 | 菜单路径 | 通过标准 | 人工通过 |
|---|--------|--------------------|----------|----------|----------|----------|
| F1 | 费用过账分录借贷平衡、科目正确 | | ui-smoke `finance-expense-create-submit-approve-post-reverse` | 财务 → 费用管理 | 打开凭证详情，借方合计 = 贷方合计；费用科目与业务类型匹配 | ☐ |
| F2 | 费用红冲后原凭证与红冲凭证净额抵销 | | 同上 | 财务 → 费用管理 → 红冲 → 凭证详情 | 原凭证分录 + 红冲分录逐条反向，净额为 0 | ☐ |
| F3 | 手工凭证过账写入总账/明细账 | | ui-smoke `manual-voucher-create-submit-approve-post-cancel` | 财务 → 手工凭证；财务 → 总账/明细账 | 过账后凭证号出现在对应期间的总账与明细账 | ☐ |
| F4 | 手工凭证作废红冲后净额归零 | | 同上 | 财务 → 手工凭证 → 作废 | 作废生成红冲凭证，该科目本期发生额归零 | ☐ |
| F5 | 销售发货过账生成应收，金额 = 含税 | | `preprod-full` 04-sales | 销售 → 发货 → 应收 | 应收金额 = 发货含税金额，来源单据可回溯 | ☐ |
| F6 | 收款核销后应收 SETTLED、剩余 0 | | `preprod-full` 04-sales | 财务 → 收款 → 核销 | 应收状态 `SETTLED`，未结金额 = 0 | ☐ |
| F7 | 销售退货生成 DECREASE/OFFSET 应收冲减 | | ui-smoke `sales-return-create-post-stock-receivable-offset` | 销售 → 退货 | 生成 `DECREASE` 或 `OFFSET` 类型应收冲减，库存同向扣减 | ☐ |
| F8 | 采购入库 → 应付、付款核销后应付 SETTLED | | `preprod-full` 03-purchase | 采购 → 入库；财务 → 付款 → 核销 | 应付状态 `SETTLED`，未结金额 = 0 | ☐ |
| F9 | 采购退货应付冲减与库存扣减一致 | | ui-smoke `purchase-return-...-payable-offset` | 采购 → 退货 | 应付冲减金额与库存扣减数量/金额一致 | ☐ |
| F10 | 开放期间外业务被拒绝；锁定期间不可过账 | | `fq-signoff-human-items.cjs`（负例） | 财务 → 会计期间；任意业务过账入口 | 期间外业务被拒；锁定期间过账失败。**须在测试数据上造一个 LOCKED 期间，验完恢复 OPEN** | ☐ |
| F11 | 非财务角色无 `finance:voucher:post` / `approve` 按钮 | | `fq-signoff-human-items.cjs`（负例） | 用无财务权限账号登录任一财务页 | 过账/审批按钮不可见或不可点。**建议用 ERP_ADMIN 与无财务权限账号同屏对照并截图** | ☐ |
| F12 | 资金流水匹配后 MATCHED，取消匹配原因落库 | | ui-smoke `fund-statement-match-unmatch-receipt` | 财务 → 资金对账 | 流水状态 `MATCHED`；取消匹配后原因字段有值 | ☐ |

**财务签字**：________________　**日期**：________　**结论**：GO / NO-GO（圈选）

**备注**：________________________________________________

> F10 与 F11 是**唯二技术不可自动截图/不可自动判定**的两项（`preprod-signoff-package-2026-07-16.md` 第 84–85 行「技术未自动覆盖/未自动截图」）。这两项必须有财务真人操作记录或截图，不能只看 `technicalPass`。

---

## 2. 门禁 2 · 质检 Q1–Q5

### 2.1 技术预检

同 §1.2，`node scripts\fq-signoff-all.cjs` 正向覆盖 Q1–Q3，负例覆盖 Q4、Q5。产物 `all-summary.json` 的 `technicalPass`。

### 2.2 质检核对表（签字用）

核对项文字与 `finance-qc-acceptance-runbook.md` 第 2.4 节一致。

| # | 核对项 | 样例单号（新候选） | 技术证据 | 菜单路径 | 通过标准 | 人工通过 |
|---|--------|--------------------|----------|----------|----------|----------|
| Q1 | 需检商品 `inspectionRequired=true` 入库 DRAFT 可建 IQC | | ui-smoke `qc-inspection-create-submit-judge-only-qualified` | 质检 → IQC 检验单；采购 → 入库 | 入库单 DRAFT 状态下可基于它创建 IQC 检验单 | ☐ |
| Q2 | 判定后检验单 `JUDGED`，合格/不合格数量正确 | | 同上 | 质检 → IQC 检验单 → 判定 | 状态转为 `JUDGED`；合格 + 不合格 = 检验数量 | ☐ |
| Q3 | 引用入库单行数量被回写为**合格数量**（仅合格品入库） | | 同上 | 采购 → 入库 → 行明细 | 入库单行数量 = 合格数量，不合格部分不入库 | ☐ |
| Q4 | 未判定/未通过时采购入库过账闸门生效（按产品配置） | | `fq-signoff-human-items.cjs`（负例） | 采购 → 入库 → 过账 | 需检商品未 `JUDGED` 时过账应**失败**并给出明确报错。**建议在预生产再点一次** | ☐ |
| Q5 | 作废检验单后不可再判定；权限码 `qc:inspection:*` 生效 | | `fq-signoff-human-items.cjs`（负例） | 质检 → IQC 检验单 → 作废 | 作废后判定入口不可用；无 `qc:inspection:update` 权限的账号不能编辑草稿 | ☐ |

**质检签字**：________________　**日期**：________　**结论**：GO / NO-GO（圈选）

**备注**：________________________________________________

---

## 3. 门禁 1/2 补充 · 本候选新增口径（旧签字表没有的两类）

**为什么必须补**：`RELEASE-CANDIDATE-2026-10-15.md` 第 73 行要求 F/Q 签字时「补充多币种与 MRP 算例」，`PROJECT-STATUS-2026-10-15.md` 第 61 行同样要求「补上新增多币种业务口径确认」。

**缺口**：对 `business-readiness-checklist.md` 检索 `fx|多币种|currency|本位币|MRP|mrp` **无任何命中**——权威业务验收清单里没有这两类核对项。所以下面两节是本包新增的、必须一并签字的补充核对表。

### 3.1 多币种 / 本位币口径（财务）

背景（`WHAT_IS_MISSING.md` 第 33–43 行）：应收应付建账写币种/汇率快照与本位币金额；采购入库、采购退货、销售出库、销售退货一律按**本位币**过账；核销落库 `base_amount`/`base_settled_amount`/`fx_gain_loss_amount` 并生成 6061 汇兑损益腿；核销强制币种一致；期末未实现汇兑损益重估（V166）。

| # | 核对项 | 样例单号（新候选） | 通过标准 | 人工通过 |
|---|--------|--------------------|----------|----------|
| C1 | 外币采购入库按本位币过账 | | 1122/2202 科目按本位币金额入账；存货与应付按同一本位币口径轧得平 | ☐ |
| C2 | 外币销售出库按本位币过账 | | 应收账款本位币金额 = 含税外币金额 × 入账汇率（按快照） | ☐ |
| C3 | 核销强制币种一致 | | 用不同币种做核销应被拒绝，且报错原因明确 | ☐ |
| C4 | 核销生成 6061 汇兑损益腿 | | 「结算汇率 − 入账汇率」产生汇兑损益凭证腿，方向与金额可复核 | ☐ |
| C5 | 作废按落库快照精确冲回 | | 作废后 6061 腿与本位币金额按原快照冲回，净额为 0 | ☐ |
| C6 | 期末未实现汇兑损益重估 | | `POST /api/finance/periods/{id}/fx-revaluation` 首次过账成功；重复调用被拒；撤销后再调汇正常；月结检查纳入重估结果 | ☐ |
| C7 | 报表 `/api/reports/fx-gain-loss` 口径正确 | | 汇兑损益报表金额与凭证 6061 腿合计一致；报表中心标签页可见 | ☐ |
| C8 | 账龄与结算报表切本位币口径并给出币种敞口 | | 账龄/结算报表金额为本位币；币种敞口列有值且加总等于合计 | ☐ |
| C9 | 期初应收应付导入支持 `currency_code`/`exchange_rate` | | 带币种/汇率的模板导入后按本位币记账；旧表头仍可导入且默认本位币 | ☐ |

**财务补充签字**：________________　**日期**：________

### 3.2 MRP-lite 业务验收算例（业务 / 计划）

背景：`MrpPlanCalculationService` 净需求算法缺陷已修复（`RELEASE-CANDIDATE-2026-10-15.md` 第 5–12 行、`PROJECT-STATUS-2026-10-15.md` 第 28–53 行）。修复后必须在真实界面上按下列算例复核，因为签字人无法从代码判断算法是否正确。

**操作路径**：库存 → MRP 计划 → 运行计划 → 查看建议（或采购/生产建议列表）。每条算例独立起一套干净数据。

| # | 场景（输入） | 正确结果 | 实测建议 | 通过 |
|---|--------------|----------|----------|------|
| M1 | 安全库存 10，现存 4，无其他需求/供应 | 采购 **6** | | ☐ |
| M2 | 安全库存 10，现存 6，无其他需求/供应 | 采购 **4** | | ☐ |
| M3 | 销售 5、安全库存 10、现存 12 | 采购 **3** | | ☐ |
| M4 | 材料销售 5、生产用料 6、材料现存 5 | 材料采购 **6** | | ☐ |
| M5 | BOM 基准 10、用料 4、损耗 10%，生产 6 | 工单用料与对应 MRP 材料需求均为 **2.64** | | ☐ |
| M6 | 禁用的预警规则、其他账套库存 | 不影响当前启用规则和当前账套净需求 | | ☐ |

**关键判定**：M1–M4 验证「现存/在途/在制供应只抵扣一次」，M5 验证「BOM 基准数量 × 损耗率」，M6 验证「按 `enabled` 字段与账套隔离」。

**再补一条链路验证**（`PROJECT-STATUS-2026-10-15.md` 第 53 行要求）：运行计划 → 查看建议 → 转采购/生产单，确认建议可无损转单。

**注意**：历史 MRP 运行记录**不回写**。修复前生成的旧建议若尚未转单，应重新运行 MRP 复核后再转单（`RELEASE-CANDIDATE-2026-10-15.md` 第 5–12 行）。

**业务/计划补充签字**：________________　**日期**：________

---

## 4. 门禁 3 · 监控与告警（运维）

### 4.1 现状盘点（必读）

`backend/docs/monitoring/` 目录下**只有一份** `prometheus-alert-rules.yml`（47 行，group `erp-business-health`，5 条告警规则）。**没有** Grafana dashboard 定义、**没有** Alertmanager 配置、**没有** Prometheus 抓取配置、**没有**部署清单。

也就是说，门禁 3 的可交付材料目前只有告警表达式模板，**抓取、面板、路由、演练步骤都需要在目标平台现做**。下面给出以现有规则为规格的落地清单。

### 4.2 五大告警规格（`prometheus-alert-rules.yml` 逐条）

| 告警名 | 表达式 | for | severity | 含义 |
|--------|--------|-----|----------|------|
| `ErpReadinessP0P1Unpassed` | `erp_business_health_check_count{check="READINESS_UNPASSED_P0_P1"} > 0` | 5m | critical | 有 P0/P1 readiness 项未通过 |
| `ErpRecentImportFailures` | `erp_business_health_check_count{check="IMPORT_FAILED_RECENT"} > 0` | 10m | warning | 近 24h 有导入作业失败 |
| `ErpNegativeInventoryBalance` | `erp_business_health_check_count{check="NEGATIVE_INVENTORY_BALANCE"} > 0` | 10m | warning | 存在负库存余额 |
| `ErpNoOpenAccountingPeriod` | `erp_business_health_check_count{check="OPEN_PERIOD_COUNT"} == 0` | 5m | critical | 当前账套无 OPEN 会计期间 |
| `ErpBusinessHealthWarn` | `erp_business_health_overall_status > 0` | 5m | warning | 整体业务健康为 WARN |

### 4.3 落地清单

- [ ] **暴露端点确认**：`/actuator/prometheus` 与 `/api/system/observability/business-health` 均可访问，**且都不能匿名访问**（`business-readiness-checklist.md` 第 110 行起要求）。
- [ ] **指标存在性**：确认 `erp_business_health_overall_status`、`erp_business_health_check_count`、`erp_business_health_check_status` 三个指标都已上报。
- [ ] **Prometheus 抓取**：新增 scrape job 指向 `erp-server` 的 `/actuator/prometheus`，带鉴权或网络隔离；确认 ≥ 5 分钟抓取窗口内 target `up=1`。
- [ ] **规则加载**：把 `backend/docs/monitoring/prometheus-alert-rules.yml` 挂进 Prometheus `rule_files`；在 Prometheus UI 的 Alerts 页确认 5 条规则均为 `inactive`（无告警时）且状态健康。
- [ ] **Alertmanager 路由**：按 severity 分流（critical → 值班即时通道，warning → 日常通道）；确认 receiver 可达。
- [ ] **Grafana 面板**：至少建三块面板——① 业务健康总览（`overall_status` 时序 + 各 check 状态表）② 各 check 计数（`check_count` by `check`）③ 告警状态列表。
- [ ] **告警触发与恢复演练**（门禁明确要求）：
  - [ ] 至少覆盖一条 **critical**（推荐 `ErpNoOpenAccountingPeriod`：临时关闭全部 OPEN 期间 → 等 5m → 观察告警 firing → 打开一个期间 → 观察 resolved）与一条 **warning**（推荐 `ErpNegativeInventoryBalance` 或 `ErpRecentImportFailures`）。
  - [ ] 记录：触发时间、进入 `firing` 时间、通知送达时间、恢复时间、`resolved` 时间。
  - [ ] 演练**必须在测试/预生产环境**做，不得在生产库制造负库存或关闭会计期间。
- [ ] **证据归档**：Prometheus Alerts 页截图、Alertmanager 通知截图、Grafana 面板截图、演练时间线记录，放入发布证据包（**不得只存 `backend/target`**）。

**运维签字**：________________　**日期**：________　**结论**：GO / NO-GO

---

## 5. 门禁 4 · 独立真实预生产复验（运维 + 研发）

### 5.1 前置条件（环境负责人提供）

依据 `RELEASE-CANDIDATE-2026-10-15.md` 第 62–66 行：

- [ ] 部署地址与部署方式；
- [ ] **独立** MySQL 与 Redis（非本机联调库）；
- [ ] 环境文件位置（`.env.prod`）；
- [ ] 测试岗位账号与业务日期 / 仓库参数；
- [ ] 凭据用环境变量或受控配置传入，**不写入文档**；
- [ ] 先按 `production-deployment.md` 配置目标环境，再执行验收脚本；
- [ ] 明确传入 `EnvFile`、`BaseUrl`、`EvidenceDirectory`、账号/令牌、仓库参数。

> ⚠️ `preprod-full-acceptance.ps1` **会启动或检查目标服务并写入验收业务数据**，须只对确认的预生产环境执行。**不要用本地 smoke 数据冒充真实期初数据**。

### 5.2 复验清单

- [ ] **数据库迁移**：在目标环境备份副本上先做一次从 V167/V168 升级的演练，核对日志与关键金额（`RELEASE-CANDIDATE-2026-10-15.md` 第 69 行）。
- [ ] **健康检查**：`/actuator/health` 与 `/api/health` 均正常。
- [ ] **鉴权**：未登录访问受保护接口返回 `401`；已登录缺权限访问核心接口返回 `403`；受保护接口至少覆盖 `/api/system/profile`。
- [ ] **关键业务链路**：采购→入库→付款、销售→发货→收款、生产下达/领料/完工、IQC 判定入库、多币种核销与调汇。
- [ ] **备份恢复与回滚演练**：实际恢复 + 回滚，记录耗时（`RELEASE-CANDIDATE-2026-10-15.md` 第 74 行）。
- [ ] **岗位与跨账套权限正反例**：采购、销售、仓库、财务、审批各自正例 + 反例。
- [ ] **真实主数据与期初余额**：导入核对，库存与账簿对平。
- [ ] **监控接入**：门禁 3 的抓取与面板在预生产可见。

### 5.3 可复用的先例命令链

`GATE_PROGRESS_2026-07-17.md` 第 58–77 行记录过一次 Docker 等价预生产的完整复跑链，可作为预生产执行的模板：

```powershell
.\scripts\release-check.ps1 -IncludeTestcontainers -MavenRepoLocal $env:M2_REPO
docker compose --env-file .env.preprod-local -f docker-compose.yml -f docker-compose.prebuilt.yml --profile core up -d
.\scripts\purchase-to-payment-acceptance.ps1 -BaseUrl http://127.0.0.1:18080 -Username admin -Password <密码> -WarehouseId 4501 -BusinessDate <开放期间日期> -RollbackAfterSuccess
.\scripts\decide-readiness-release.ps1 -EvidenceDirectory target\docker-preprod-full-<时间戳> ...
.\scripts\export-release-evidence-bundle.ps1 -EvidenceDirectory <证据目录> -ReleaseCheckReportDirectory target
node scripts\fq-signoff-all.cjs
```

该先例留下的两条工程经验（来自同一文件第 25–28 行，预生产部署直接用得上）：

- JDBC URL 必须带 `characterEncoding=UTF-8`（`utf8mb4` 当 Java charset 会启动失败）；
- 验收前关闭限流：`SPRING_APPLICATION_JSON={"erp":{"rate-limit":{"enabled":false}}}`。

### 5.4 工作区安全

`GATE_PROGRESS_2026-07-17.md` 第 50–52 行记录本仓库存在两个安全备份 stash：

- 后端：`SAFETY-BACKUP-code-wip-20260717-before-safe-cleanup`
- 前端：`SAFETY-BACKUP-frontend-wip-20260717`

**执行预生产前先确认工作区干净**（`git status --porcelain` 为空），不要在这些 stash 未归档的情况下做清理动作。

**运维 / 研发签字**：________________　**日期**：________　**结论**：GO / NO-GO

---

## 6. 门禁 5 · GO 决策与 release tag 前置条件

### 6.1 前置核对表

| # | 前置条件 | 状态 | 证据位置 |
|---|----------|------|----------|
| 1 | 干净候选 HEAD，无未跟踪源码/迁移/测试 | | `git status --porcelain`（应为空） |
| 2 | 后端全量测试 + Testcontainers 发布构建 PASSED | | `target/release-check-report.json` |
| 3 | 前端五项门禁 + `npm audit --omit=dev --audit-level=high` | | 前端日志 |
| 4 | JAR / SBOM / 迁移清单 / smoke 报告已**独立归档**（非 `backend/target`） | | 独立证据目录 |
| 5 | 财务 F1–F12 签字（含 §3.1 多币种） | | 签字单扫描件 |
| 6 | 质检 Q1–Q5 签字 | | 签字单扫描件 |
| 7 | §3.2 MRP 算例复核 | | 复核记录 |
| 8 | 监控落地 + 告警触发/恢复演练记录 | | 演练时间线 + 截图 |
| 9 | 独立真实预生产复验 PASSED | | 验收证据目录 |
| 10 | 七行责任表各行均有责任人结论 | | 见 §7 |

### 6.2 readiness 补录

对既有 readiness 运行单补录人工证据：

```powershell
.\scripts\register-readiness-item-result.ps1
```

**可回填项白名单**（脚本第 23–27 行，逐字）：`FINANCE_LEDGER`、`PERIOD_LOCK`、`INVENTORY_FINANCE_RECONCILIATION`、`INITIAL_IMPORT`、`BACKUP_ROLLBACK`。

**允许状态**（第 29 行，逐字）：`PASSED`、`FAILED`、`BLOCKED`、`SKIPPED`。

**无法证明的项目保持 `BLOCKED`**——不要为了凑齐门禁而填 `PASSED`。

### 6.3 候选归档要求

依据 `RELEASE-CANDIDATE-2026-10-15.md` 第 58–60 行：

- 归档到**仓库之外**的独立证据目录；
- 收录：完整 Git SHA、干净工作区检查、源文件 SHA256 清单、前端检查日志与构建、后端 XML、release check JSON/Markdown、JAR、两份 SBOM、完整 UI 报告及日志、迁移版本清单；
- **历史失败日志单独保留**，不要覆盖；
- 正式候选必须 `allowDirtyWorktree=false`；
- UI 报告必须记录同一 SHA 且 `dirtyWorktree=false`。

> 不要在门禁全量通过后用专项报告覆盖唯一的完整报告副本（第 56 行）。

### 6.4 GO 决策

- [ ] 授权业务负责人对**同一候选证据包**作出 GO 决策并记录；
- [ ] GO 之后**才**创建 release tag；
- [ ] 在此之前**不得**宣称生产就绪。

---

## 7. 责任分工与安全边界

### 7.1 七行责任表（`RELEASE-CANDIDATE-2026-10-15.md` 第 68–76 行）

| 事项 | 责任人 | 证据 | 当前状态 |
|------|--------|------|----------|
| 数据库 V167/V168 升级与既有数据兼容 | 开发 / 运维 | 从目标环境备份副本升级的日志和金额核对 | 待目标环境验收 |
| 岗位与跨账套权限 | 业务 / 实施 | 采购、销售、仓库、财务、审批的正反例 | 待目标环境验收 |
| 真实主数据与期初余额 | 业务 / 财务 | 导入核对、库存与账簿对平 | 待业务确认 |
| **财务 F1–F12、质检 Q1–Q5** | 财务 / 质检 | 逐项核对和真实签字；**补充多币种与 MRP 算例** | 待业务确认 |
| 备份恢复与回滚 | 运维 | 实际恢复和回滚演练记录、耗时 | 待目标环境验收 |
| 监控与告警 | 运维 | 告警触发、送达、恢复记录 | 待目标环境验收 |
| GO/NO-GO | 授权业务负责人 | 对同一候选证据包作出的决定 | 未放行 |

### 7.2 安全边界（逐条遵守）

1. **F10 / Q4 负例只能在测试数据上做**，验证后必须把会计期间恢复为 `OPEN`；
2. **不得对共享联调库执行 `flyway repair` 或删库**；
3. 签字以**库内真实业务数据 + 已归档技术证据**为准，**不是**未提交代码；
4. 旧 RC 证据（含 `FQ-SIGNOFF-RC-989FBC4-2026-08-24.md`）**只能用于格式与历史追溯**（`WHAT_IS_MISSING.md` 第 260 行），不能作为当前候选的签字依据；
5. `backend/target` 下的 smoke 报告**会被 Maven `clean` 删除**，只能作开发联调证据，**签字扫描件不得只存这里**；
6. 技术脚本 PASS **≠** 业务放行；
7. 本机 GO **不能**替代预生产 GO 与财务/质检签字。

---

## 8. 最短执行路径（给签字人）

1. 在**新候选**上跑 §1.1 生成新样例单号（`finance-qc-signoff-seed.md`）；
2. 跑 §1.2 `node scripts\fq-signoff-all.cjs`，确认 `technicalPass=true`；
3. 启动前端，按单据管理 → `docs/preprod-signoff-clickpath-2026-07-16.md` 或 `docs/FQ-SIGNOFF-CLICKPATH-2026-07-17.md` 逐项打开财务/质检页；
4. 逐项核对 §1.3、§2.2、§3.1、§3.2 的表格并勾选；
5. 签字落在打印件或 PDF；扫描件放回独立证据目录（**不是** `backend/target`）。

---

## 9. 相关文档

- [WHAT_IS_MISSING.md](WHAT_IS_MISSING.md) — 总缺口与人工门禁来源
- [RELEASE-CANDIDATE-2026-10-15.md](RELEASE-CANDIDATE-2026-10-15.md) — 当前候选权威交接件（算例表、责任表、归档要求）
- [PROJECT-STATUS-2026-10-15.md](PROJECT-STATUS-2026-10-15.md) — MRP 缺陷审计报告
- [finance-qc-acceptance-runbook.md](finance-qc-acceptance-runbook.md) — F/Q 核对项权威定义
- [preprod-signoff-package-2026-07-16.md](preprod-signoff-package-2026-07-16.md) — 带样例单号的签字表模板（格式参考）
- [FQ-SIGNOFF-FINAL-2026-07-16.md](FQ-SIGNOFF-FINAL-2026-07-16.md) — 可打印签字单模板
- [preprod-signoff-clickpath-2026-07-16.md](preprod-signoff-clickpath-2026-07-16.md) — 按单号点哪里（详细）
- [FQ-SIGNOFF-CLICKPATH-2026-07-17.md](FQ-SIGNOFF-CLICKPATH-2026-07-17.md) — 菜单路径映射（紧凑）
- [GATE_PROGRESS_2026-07-17.md](GATE_PROGRESS_2026-07-17.md) — Docker 等价预生产先例与复跑命令链
- [business-readiness-checklist.md](business-readiness-checklist.md) — 冒烟与可观测性要求
- `monitoring/prometheus-alert-rules.yml` — 5 条告警规则规格
