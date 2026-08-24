# 财务/质检签字交接记录（RC `989fbc4`）

## 结论

本轮 RC 已达到“技术证据可送财务/质检人工签字”条件，当前状态为 **WAITING_FOR_BUSINESS_SIGNOFF**。技术预检已通过，但真人签字尚未完成；不得把技术通过当作业务放行。

- RC commit：`989fbc4`
- 执行时间：2026-08-24（Asia/Shanghai）
- 环境：本机 `erp_codex_runtime`，MySQL 8.4，local profile
- Readiness run：`RDY20260824081832459`（run ID `2091802323809300482`）
- 独立证据根目录：`/Users/xiao/Desktop/python/release-evidence/rc-989fbc4/`

## 技术预检结果

| 范围 | 结果 | 说明 |
|---|---:|---|
| F1–F12 / Q1–Q5 正向样例 | 13 自动通过，4 待人工，0 自动失败 | 脚本会优先使用旧编号；旧编号不存在或已取消时，选择最新满足状态/关联条件的样例 |
| F10 / F11 / Q4 / Q5 负例 | 4 / 4 通过 | 锁定期间拦截、权限 403、未判定质检拦截、作废后不可判定均通过 |
| 汇总结论 | `technicalPass=true` | 可送财务/质检人工签字 |

本轮实际证据：

- `fq-signoff-api-check-final-20260824/report.json` / `report.md`
- `fq-signoff-api-check-final-20260824/human-items-report.json` / `human-items-report.md`
- `fq-signoff-api-check-final-20260824/all-summary.json`
- `fq-signoff-all-final-20260824.log`

以上文件位于独立证据根目录，不受 Maven `clean` 影响。

## 本轮新样例

| 项 | 实际编号/状态 |
|---|---|
| F1/F2 费用过账与红冲 | `FE202608240017`，POSTED，含 `voucherId` 与 `reversalVoucherId` |
| F3/F4 手工凭证过账与作废 | `MV202608240010`，CANCELLED，含 posted/reversal voucher |
| F5 发货生成应收 | `SD202608240038` → `AR-SALES_DELIVERY-2091760196870057985` |
| F6 收款核销应收 | `FR202608240014`，对应 SETTLED 应收 |
| F7 销售退货 | `SRT202608240012`，POSTED |
| F8 采购入库付款 | `PR202608240038` / `FP202608240013` / `AP-PURCHASE_RECEIPT-2091760040590290946`，SETTLED |
| F9 采购退货 | `PRT202608240018`，POSTED |
| F12 资金流水取消匹配 | `MV202608240012`，UNMATCHED 且有取消原因 |
| Q1–Q3 IQC | `QC202608240007`，JUDGED，4=3 合格+1 不合格，草稿入库行回写 3 |

旧签字单 [FQ-SIGNOFF-FINAL-2026-07-16.md](FQ-SIGNOFF-FINAL-2026-07-16.md) 仍可作为格式模板，但本轮签字必须以表中 RC 新样例和独立证据目录为准。

## 下一轮执行要求

1. 财务按 F1–F12 对照上表新样例逐项复核并签名；质检按 Q1–Q5 对照上表和负例报告逐项复核并签名。
2. F10、F11、Q4、Q5 的 UI/权限确认必须保留人工勾选或截图，自动化负例报告不能替代签字。
3. 人工签字扫描件归档到同一 RC 证据根目录的 `signed/` 子目录；不得存入 `backend/target` 作为唯一副本。
4. 签字完成后，由授权人根据该 RC 证据包推进监控落地；在此之前保持 No-Go。

> 技术预检和自动化负例不能替代财务/质检真人签字。
