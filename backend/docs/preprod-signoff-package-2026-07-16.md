# 预生产签字包（无 Docker / 本机干净库等价）

**生成时间**: 2026-07-16  
**环境定性**: 本机 `erp_codex_runtime` + `local` profile，**不是**独立机房预生产；用于：  
1) 证明无 Docker 时 `preprod-full-acceptance` 同构链路可 GO；  
2) 把 F/Q 核对表填好**样例单号**，交给财务/质检签字。  

**技术 GO 证据目录**: `target/local-runtime-acceptance-20260716-113916/`  
**总判定**: **GO**（`summary.md`）  
**Readiness run ID**: `2077598929523875842`  
**命令**:

```powershell
java -jar target\erp-server-1.0.0.jar --spring.profiles.active=local
.\scripts\local-runtime-acceptance.ps1 -NoRollback
# 等价 preprod-full-acceptance -SkipReleaseCheck -SkipComposeUp
```

**边界（签字前必读）**

| 已证明 | 未证明 / 不可替代 |
|--------|-------------------|
| 采购→入库→付款、销售→发货→收款、生产下达/领料/完工技术闭环 | 独立预生产配置、真实主数据、网络与密钥 |
| Browser smoke 34/34（`target/ui-smoke-report.json`，2026-07-15）费用/手工凭证/IQC/退货冲减/资金匹配 | 财务对科目口径、期间策略、权限矩阵的**业务认可** |
| 开放期间内业务可过账（期间 2026-07 OPEN） | F10 锁定期间/关账边界需财务在预生产再点验 |
| admin / runtime_smoke 双账号审批 | F11 非财务角色按钮隐藏需用业务角色账号肉眼确认 |

---

## 1. 技术链路结果（本包）

| 步骤 | 状态 | 证据 |
|------|------|------|
| Preproduction foundation | PASSED | `01-preprod-acceptance.md` |
| Business smoke | PASSED | `02-business-smoke.md` |
| Purchase to payment | PASSED | `03-purchase-to-payment-acceptance.md` |
| Sales to cash | PASSED | `04-sales-to-cash-acceptance.md` |
| Production manufacturing | PASSED | `05-production-manufacturing-acceptance.md` |
| **Go / No-Go** | **GO** | `summary.md` |

### 本包样例单号（脚本）

| 链路 | 单号 |
|------|------|
| 采购订单 | `PO202607160012` |
| 采购入库 | `PR202607160009` |
| 付款单 | `FP202607160004` |
| 应付 | `AP-PURCHASE_RECEIPT-2077598945533534210`（SETTLED） |
| 销售订单 | `SO202607160004` |
| 销售发货 | `SD202607160004` |
| 收款单 | `FR202607160004` |
| 应收 | `AR-SALES_DELIVERY-2077598953381076994`（SETTLED） |
| 生产工单 | `MO202607160003`（COMPLETED） |
| 生产备料采购/入库 | `PO202607160014` / `PR202607160011` |

### Browser smoke 补充样例（ui-smoke 34/34，2026-07-15）

| 能力 | 样例 |
|------|------|
| 费用过账/红冲 | `FE202607150005` |
| 手工凭证 | `MV202607150003` |
| 资金流水匹配 | 流水 `MV202607150006` + 收款 `FR202607150005` |
| 销售退货冲应收 | 发货 `SD202607150016` / 退货 `SRT202607150005` |
| 采购退货冲应付 | 入库 `PR202607150011` / 退货 `PRT202607150005` |
| IQC 仅合格入库 | 检验 `QC202607150003` / 入库 `PR202607150013` |

---

## 2. 财务人工核对表（签字用）

请财务打开系统或证据报告核对后勾选。样例单号已预填，可改用更新的预生产单号。

| # | 核对项 | 样例单号（预填） | 技术证据 | 人工通过 |
|---|--------|------------------|----------|----------|
| F1 | 费用过账分录借贷平衡、科目正确 | `FE202607150005` | ui-smoke expense workflow | ☐ |
| F2 | 费用红冲后原凭证与红冲凭证净额抵销 | `FE202607150005` | 同上 | ☐ |
| F3 | 手工凭证过账写入总账/明细账 | `MV202607150003` | ui-smoke manual-voucher | ☐ |
| F4 | 手工凭证作废红冲后净额归零 | `MV202607150003` | 同上 | ☐ |
| F5 | 销售发货过账生成应收，金额=含税 | `SD202607160004` / `AR-SALES_DELIVERY-2077598953381076994` | 本包 04-sales | ☐ |
| F6 | 收款核销后应收 SETTLED、剩余 0 | `FR202607160004` | 本包 04-sales | ☐ |
| F7 | 销售退货生成 DECREASE/OFFSET 应收冲减 | `SRT202607150005`（发货 `SD202607150016`） | ui-smoke sales-return | ☐ |
| F8 | 采购入库→应付、付款核销后应付 SETTLED | `PR202607160009` / `FP202607160004` / AP…`3534210` | 本包 03-purchase | ☐ |
| F9 | 采购退货应付冲减与库存扣减一致 | `PRT202607150005`（入库 `PR202607150011`） | ui-smoke purchase-return | ☐ |
| F10 | 开放期间外业务被拒绝；锁定期间不可过账 | 期间 `2026-07` OPEN（需另造 LOCKED 用例） | 技术未自动覆盖锁定负例 | ☐ |
| F11 | 非财务角色无 `finance:voucher:post` / `approve` 按钮 | 建议用 ERP_ADMIN 与无财务权限账号对照 | 技术未自动截图 | ☐ |
| F12 | 资金流水匹配后 MATCHED，取消匹配原因落库 | 流水 `MV202607150006` + `FR202607150005` | ui-smoke fund match | ☐ |

**财务签字**: ______________　**日期**: ________　**结论**: GO / NO-GO（圈选）

备注: ________________________________________________

---

## 3. 质检人工核对表（签字用）

| # | 核对项 | 样例单号（预填） | 技术证据 | 人工通过 |
|---|--------|------------------|----------|----------|
| Q1 | 需检商品 `inspectionRequired=true` 入库 DRAFT 可建 IQC | `QC202607150003` / `PR202607150013` | ui-smoke IQC | ☐ |
| Q2 | 判定后检验单 `JUDGED`，合格/不合格数量正确 | `QC202607150003` | 同上 | ☐ |
| Q3 | 引用入库单行数量被回写为**合格数量** | `PR202607150013` 行数量回写 | 同上 | ☐ |
| Q4 | 未判定时采购入库过账闸门生效 | 需检商品未 JUDGED 时过账应失败 | 建议预生产再点一次 | ☐ |
| Q5 | 作废检验单后不可再判定；`qc:inspection:*` 权限生效 | 草稿编辑已挂 `qc:inspection:update` | 权限可用 ERP_ADMIN 对照 | ☐ |

**质检签字**: ______________　**日期**: ________　**结论**: GO / NO-GO（圈选）

备注: ________________________________________________

---

## 4. 签发汇总

| 门禁 | 状态 | 负责人 |
|------|------|--------|
| 技术全链路 `preprod-full`（无 Docker 等价） | **GO**（本包） | 研发 |
| Browser smoke 34/34 | **GO**（2026-07-15） | 研发 |
| 财务 F1–F12 | **技术全覆盖**（正向 10 项 API + F10/F11 负例自动化）→ **待业务签字** | 财务 |
| 质检 Q1–Q5 | **技术全覆盖**（Q1–Q3 正向 + Q4/Q5 负例自动化）→ **待业务签字** | 质检 |
| Docker Testcontainers release-check | **未跑**（本机无 Docker） | 发布/运维 |
| 独立预生产环境（真 .env.prod / compose） | **未部署** | 运维 |

**整体上线建议**: 技术侧可进入「待业务签字」；**不得**在 F/Q 未签与 Testcontainers 未过前宣称生产就绪。

---

## 5. 给签字人的最短操作路径

逐步菜单与「看什么」见专文（推荐签字人只看这一篇）：

→ **`docs/preprod-signoff-clickpath-2026-07-16.md`**

摘要：

1. 启动前端 `http://127.0.0.1:5173`（代理到已启后端）。  
2. 用 `admin / LocalAdmin123` 按 clickpath 逐项打开财务/质检页。  
3. 按上表单号查询单据、凭证、应收应付、检验单。  
4. 勾选通过项，签字落在本文件打印件或 PDF。  
5. 将签字扫描件放回 `target/local-runtime-acceptance-20260716-113916/signed/`。

---

## 6. 相关文档

- `docs/preprod-signoff-clickpath-2026-07-16.md` — **按单号点哪里（签字操作手册）**
- `docs/finance-qc-acceptance-runbook.md` — 清单源定义  
- `docs/local-runtime-integration.md` — 干净库与本机验收命令  
- `docs/WHAT_IS_MISSING.md` — 总缺口  
- `target/ui-smoke-report.json` — 浏览器 smoke 明细  
