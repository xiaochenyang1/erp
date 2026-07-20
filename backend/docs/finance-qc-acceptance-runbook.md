# 财务 / 质检业务验收 Runbook

**更新时间**: 2026-07-15  
**目的**: 把“财务/质检还要人工业务验收”从口头缺口变成可执行清单，并标明哪些已由 browser smoke 自动覆盖、哪些仍需预生产签字。

---

## 1. 已由 `ui-smoke.mjs` 自动覆盖（本机 2026-07-15 晚间 34/34 全绿）

干净联调库 `erp_codex_runtime`，命令：

```powershell
UI_SMOKE_ROUTES=0 node scripts\ui-smoke.mjs
```

证据文件：`target/ui-smoke-report.json`、`target/ui-smoke-full.log`。

| 领域 | workflow | 覆盖点 |
|------|----------|--------|
| 费用 | `finance-expense-create-submit-approve-post-reverse` | 双账号提交/审批、过账凭证、红冲 |
| 科目/凭证只读 | `finance-subject-save-toggle-and-voucher-readonly-detail` | 科目增改启停 + 凭证详情分录 |
| 手工凭证 | `manual-voucher-create-submit-approve-post-cancel` | 草稿→提交→审批→过账→作废红冲 |
| 会计期间 | `finance-period-generate-check-and-reconcile` | 生成年期间、检查、对账 |
| 资金对账 | `fund-account-and-statement-create` / `fund-statement-match-unmatch-receipt` | 账户/流水创建、匹配/取消匹配 |
| 销售应收 | `sales-delivery-create-post-receivable` / `sales-receipt-create-settle-receivable` | 发货应收、收款核销 |
| 销售冲减 | `sales-return-create-post-stock-receivable-offset` | 退货过账 + 应收冲减 |
| 采购应付 | `purchase-order-receipt-payment-settle-payable` / `purchase-return-...-payable-offset` | 采购到付款、退货应付冲减 |
| 质检 IQC | `qc-inspection-create-submit-judge-only-qualified` | 创建→提交→判定，**仅合格品回写入库数量** |
| 审批 | `workflow-task-detail-approve-and-record-filter` / `workflow-task-reject` / `workflow-withdraw` | 通过/驳回/提交人撤回 |

> 上述证明**链路可走通与账务/库存回查一致**，不等于财务人员已对分录口径、期间边界、权限矩阵签字。

---

## 2. 预生产签字级验收（仍需人工 / 有 Docker 的环境）

### 2.1 一键脚本（优先）

```powershell
.\scripts\preprod-full-acceptance.ps1 `
  -EnvFile .env.prod `
  -BaseUrl http://127.0.0.1:8080 `
  -Username admin -Password "<预生产密码>" `
  -WarehouseId <活跃仓> `
  -MaterialWarehouseId <材料仓> `
  -FinishedWarehouseId <成品仓> `
  -BusinessDate "<开放期间日期>"
```

会串联：基础验收 → 只读冒烟 → 采购到付款 → 销售到收款 → 生产制造，并写 readiness 证据。

### 2.2 分段脚本（排障用）

| 链路 | 脚本 |
|------|------|
| 采购→入库→付款 | `.\scripts\purchase-to-payment-acceptance.ps1` |
| 销售→发货→收款 | `.\scripts\sales-to-cash-acceptance.ps1` |
| 生产领料/完工 | `.\scripts\production-manufacturing-acceptance.ps1` |

### 2.3 财务人工核对表（签字用）

在预生产任选 1 组 smoke/脚本造出的样例单据，由财务核对并勾选：

| # | 核对项 | 样例单号 | 通过 |
|---|--------|----------|------|
| F1 | 费用过账分录借贷平衡、科目正确 | | ☐ |
| F2 | 费用红冲后原凭证与红冲凭证净额抵销 | | ☐ |
| F3 | 手工凭证过账写入总账/明细账 | | ☐ |
| F4 | 手工凭证作废红冲后净额归零 | | ☐ |
| F5 | 销售发货过账生成应收，金额=含税 | | ☐ |
| F6 | 收款核销后应收 SETTLED、剩余 0 | | ☐ |
| F7 | 销售退货生成 DECREASE/OFFSET 应收冲减 | | ☐ |
| F8 | 采购入库→应付、付款核销后应付 SETTLED | | ☐ |
| F9 | 采购退货应付冲减与库存扣减一致 | | ☐ |
| F10 | 开放期间外业务被拒绝；锁定期间不可过账 | | ☐ |
| F11 | 非财务角色无 `finance:voucher:post` / `approve` 按钮 | | ☐ |
| F12 | 资金流水匹配后 MATCHED，取消匹配原因落库 | | ☐ |

签字：________ 日期：________

### 2.4 质检人工核对表（签字用）

| # | 核对项 | 样例单号 | 通过 |
|---|--------|----------|------|
| Q1 | 需检商品 `inspectionRequired=true` 入库 DRAFT 可建 IQC | | ☐ |
| Q2 | 判定后检验单 `JUDGED`，合格/不合格数量正确 | | ☐ |
| Q3 | 引用入库单行数量被回写为**合格数量**（仅合格品入库） | | ☐ |
| Q4 | 未判定/未通过时采购入库过账闸门生效（按产品配置） | | ☐ |
| Q5 | 作废检验单后不可再判定；权限码 `qc:inspection:*` 生效 | | ☐ |

签字：________ 日期：________

---

## 3. 本机 vs 预生产边界

| 环境 | 能证明什么 | 不能代替什么 |
|------|------------|--------------|
| 本机 `erp_codex_runtime` + ui-smoke | 前后端契约、状态机、回查一致性 | 生产配置、真实主数据、财务口径签字 |
| 预生产 + acceptance 脚本 | 真实环境迁移、造数、补偿回滚 | 业务人员对科目/期间/权限的认可 |
| Docker Testcontainers release-check | CI/发布门禁可重复 | 业务验收 |

---

## 4. 本机干净库等价入口（无 Docker / 无独立预生产时）

日常联调与本机技术验收统一用 `erp_codex_runtime`（见 `docs/local-runtime-integration.md`）。后端 `local` profile 已默认连该库。

```powershell
# 后端已启动后：
.\scripts\local-runtime-acceptance.ps1
```

会调用 `preprod-full-acceptance.ps1 -SkipReleaseCheck -SkipComposeUp`，证据写到 `target/local-runtime-acceptance-<timestamp>/`，并生成 `finance-qc-signoff-seed.md` 方便摘抄样例单号做 F/Q 勾选。  
**本机 GO 不能替代预生产环境 GO 与财务/质检签字。**

---

## 5. 当前状态（2026-07-16 下午）

- [x] Browser smoke 财务/质检主干链路全绿（34 workflow）
- [x] 工作流驳回/撤回 browser smoke
- [x] 本机联调库默认切到 `erp_codex_runtime` + `scripts/local-runtime-acceptance.ps1` 入口
- [x] 本机 `local-runtime-acceptance` **技术 GO（含生产补偿回滚）**（最新：`target/local-runtime-acceptance-20260716-114617/summary.md`）
- [x] 无 Docker 预生产同构包 + F/Q 预填样例签字表：`docs/preprod-signoff-package-2026-07-16.md`
- [x] 生产退料后取消工单释放预留再采购退货（回滚库存不足已修）
- [x] F/Q 技术预检（正向 + F10/F11/Q4/Q5 负例）脚本：`scripts/fq-signoff-api-check.cjs` + `scripts/fq-signoff-human-items.cjs`（2026-07-16 全 PASS）
- [ ] **财务 F1–F12 业务签字**（技术已齐，见 `target/fq-signoff-api-check/`）
- [ ] **质检 Q1–Q5 业务签字**
- [ ] 独立机房预生产 `preprod-full-acceptance`（真 `.env.prod` / compose）
- [ ] Docker/Testcontainers `release-check` PASSED

签字包最短路径：打开 `docs/preprod-signoff-package-2026-07-16.md` → 按 `docs/preprod-signoff-clickpath-2026-07-16.md` 点菜单核对 → 勾选并签字 → 扫描件放 `target/local-runtime-acceptance-20260716-113916/signed/`。
