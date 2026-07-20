# 财务/质检签字点击路径（2026-07-17）

**签字单（勾选+签字）**: [`docs/FQ-SIGNOFF-FINAL-2026-07-16.md`](FQ-SIGNOFF-FINAL-2026-07-16.md)  
**详细菜单路径（仍适用）**: [`docs/preprod-signoff-clickpath-2026-07-16.md`](preprod-signoff-clickpath-2026-07-16.md)  
**技术门禁进展**: [`docs/GATE_PROGRESS_2026-07-17.md`](GATE_PROGRESS_2026-07-17.md)

> 本页目标：让财务/质检 **约 40 分钟** 对照系统点完并在终版单上签字。  
> **技术已 PASS ≠ 业务放行**；请真人勾选 ☐ 后签字。

---

## 0. 开工前检查（2 分钟）

| 检查 | 期望 |
|------|------|
| 后端 | `http://127.0.0.1:8080/actuator/health` → `UP`（日常联调库 `erp_codex_runtime`） |
| 前端 | `http://127.0.0.1:5173` 登录侧边栏有 采购/销售/财务/质量 |
| 主账号 | `admin` / `LocalAdmin123` |
| 对照账号（F11） | `runtime_smoke` / `RuntimeSmoke123`（ERP_ADMIN） |
| 技术报告（可选） | `target/fq-signoff-api-check/all-summary.json` → `technicalPass: true`（2026-07-17 复跑） |

**不要**用历史脏库 `erp` 做签字核对。

---

## 1. 建议顺序与样例单号

样例来自签字单与 7-16/7-17 技术证据；若库被清过，用同路径 **现造一单** 对照规则即可，并在签字单备注写新单号。

### 财务（F1–F12）

| # | 看什么 | 菜单路径 | 样例/提示 |
|---|--------|----------|-----------|
| F1 | 费用过账借贷平衡 | 财务 → 费用管理 `/finance/expenses` | `FE202607150005` → POSTED + 凭证分录平衡 |
| F2 | 费用红冲净额抵销 | 同上 + 凭证查询 | 红冲凭证与原凭证抵销 |
| F3 | 手工凭证过账 | 财务 → 手工凭证 `/finance/vouchers/manual` | `MV202607150003` |
| F4 | 手工凭证作废红冲 | 同上 | 作废后有 reversal；总账可解释 |
| F5 | 发货生成应收 | 销售发货 `/sales/deliveries` → 应收 `/finance/receivables` | `SD202607160004` |
| F6 | 收款核销 SETTLED | 收付款 `/finance/payments` | `FR202607160004` remain=0 |
| F7 | 销售退货冲应收 | 销售退货 `/sales/returns` | `SRT202607150005` |
| F8 | 采购付款应付 SETTLED | 采购收货 + 应付 + 付款 | `PR…` / `FP…` / `AP-…` |
| F9 | 采购退货冲应付 | 采购退货 `/purchase/returns` | `PRT202607150005` |
| F10 | 锁定期间不可过账 | 会计期间 `/finance/periods` | **负例**：锁测试期间后再过账应拒绝；测完 reopen；**勿锁共享生产期不恢复** |
| F11 | 无凭证权限不可过账 | 手工凭证页 + 换 `runtime_smoke` | 无 post/approve 按钮或 403 |
| F12 | 资金取消匹配原因 | 资金对账 `/finance/funds` | 取消匹配后 UNMATCHED + 原因 |

### 质检（Q1–Q5）

| # | 看什么 | 菜单路径 | 样例/提示 |
|---|--------|----------|-----------|
| Q1 | 需检可建 IQC | 质量 → 来料检验 `/qc/inspections` | `QC202607150003` |
| Q2 | 判定数量正确 | 同上详情 | JUDGED；合格+不合格=行数量 |
| Q3 | 仅合格品回写入库 | 采购收货对应 PR | 行数量=合格数 |
| Q4 | 未判定禁止入库过账 | **建议新造**草稿入库过账 | 应拒绝「尚未完成质检」类提示 |
| Q5 | 作废后不可判定 | 检验单作废后再判定 | 应拒绝 |

更细的「点哪里、看哪一列」见 7-16 clickpath 原文。

---

## 2. 技术侧已替你做过的（可对照，不可代替签字）

| 项 | 位置 |
|----|------|
| F/Q API 技术预检 | `target/fq-signoff-api-check/report.md` + `human-items-report.md` |
| 汇总 | `target/fq-signoff-api-check/all-summary.json`（`technicalPass=true`） |
| Docker 采销产 + DECIDED_GO | `target/docker-preprod-full-20260717b/` |
| 证据包 zip | `.../release-evidence-bundle-20260717-093444.zip` |

---

## 3. 签字与归档

1. 在 `FQ-SIGNOFF-FINAL-2026-07-16.md` 勾选 F1–F12、Q1–Q5  
2. 财务结论 / 质检结论 选 **GO 或 NO-GO** 并签字  
3. 扫描件建议目录：  
   `target/docker-preprod-full-20260717b/signed/`  
   或 `target/fq-signoff-api-check/signed/`

---

## 4. 安全注意

- 负例 F10/Q4 **只在测试数据**上做；锁期间后务必恢复 OPEN  
- 不要在签字过程中对共享联调库做 `flyway repair` / 删库  
- 当前开发机 working tree 有未发布重构（见 `WORKTREE_SAFETY_INVENTORY_2026-07-17.md`）；**签字以库内业务数据 + 已归档技术证据为准**，不以未提交代码为准
