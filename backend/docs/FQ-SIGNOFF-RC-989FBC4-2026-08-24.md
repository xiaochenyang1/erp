# 财务/质检签字交接记录（RC `989fbc4`）

## 结论

本轮 RC 尚未达到财务/质检签字条件，当前状态为 **BLOCKED**，不得沿用 2026-07-17 的旧样例编号或 `technicalPass=true` 结论作为本轮证据。

- RC commit：`989fbc4`
- 执行时间：2026-08-24（Asia/Shanghai）
- 环境：本机 `erp_codex_runtime`，MySQL 8.4，local profile
- Readiness run：`RDY20260824073648681`（run ID `2091791822203240449`）
- 独立证据根目录：`/Users/xiao/Desktop/python/release-evidence/rc-989fbc4/`

## 技术预检结果

| 范围 | 结果 | 说明 |
|---|---:|---|
| F1–F12 / Q1–Q5 正向固定样例 | 1 自动通过，4 待人工，12 自动失败 | 当前库中找不到旧表单依赖的固定历史单号，不能判定本轮技术通过 |
| F10 / F11 / Q4 / Q5 负例 | 4 / 4 通过 | 锁定期间拦截、权限 403、未判定质检拦截、作废后不可判定均通过 |
| 汇总结论 | `technicalPass=false` | 不得送签 |

本轮实际证据：

- `fq-signoff-api-check/report.json` / `report.md`
- `fq-signoff-api-check/human-items-report.json` / `human-items-report.md`
- `fq-signoff-api-check/all-summary.json`
- `fq-signoff-all.log`

以上文件位于独立证据根目录，不受 Maven `clean` 影响。

## 阻塞项

以下旧样例在当前 `erp_codex_runtime` 和本机恢复演练库中均不存在：

- 费用/红冲、手工凭证/红冲（F1–F4）；
- 销售发货/收款/退货（F5–F7）；
- 采购入库/付款/退货（F8–F9）；
- IQC 正向样例（Q1–Q3）。

因此旧签字单 [FQ-SIGNOFF-FINAL-2026-07-16.md](FQ-SIGNOFF-FINAL-2026-07-16.md) 只能作为历史模板，不能直接签署本轮 RC。

## 下一轮执行要求

1. 在受控验收库重新创建 F1–F9、F12、Q1–Q3 的全新样例，并保留实际业务单号、凭证号、应收应付和库存/质检状态。
2. 使用 `ERP_FQ_EVIDENCE_DIRECTORY` 指向 RC 独立证据目录，重新运行 `node scripts/fq-signoff-all.cjs`。
3. 只有 `all-summary.json` 明确为 `technicalPass=true` 后，才把新样例编号填入签字表，交由财务 F1–F12、质检 Q1–Q5 逐项勾选并签名。
4. 人工签字扫描件归档到同一 RC 证据根目录的 `signed/` 子目录；不得存入 `backend/target` 作为唯一副本。

> 技术预检和自动化负例不能替代财务/质检真人签字。
