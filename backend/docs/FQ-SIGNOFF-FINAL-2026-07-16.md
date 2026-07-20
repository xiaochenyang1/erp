# ERP 财务/质检验收签字单（终版 · 可打印）

**日期**: 2026-07-16（表单定稿） / **技术证据刷新**: 2026-07-17  
**环境**: 本机干净库 `erp_codex_runtime` + Docker Compose 等价预生产（`18080`）  
**技术证据目录**:
- 全链路 GO（历史 local）: `target/local-runtime-acceptance-20260716-160441/`（含补偿回滚）
- Docker 预生产 DECIDED_GO: `target/docker-preprod-full-20260717b/`（含采销产回滚 + 证据包 zip）
- F/Q 正向预检: `target/fq-signoff-api-check/report.json`（2026-07-17 复跑）
- F/Q 负例预检: `target/fq-signoff-api-check/human-items-report.json`
- 一键汇总: `target/fq-signoff-api-check/all-summary.json`（`technicalPass=true`，run `RDY20260717013534335`）
- 过程说明: `docs/GATE_PROGRESS_2026-07-17.md`

**技术结论（研发）**: F1–F12、Q1–Q5 **库内状态与负例闸门均已 API 核实通过**（2026-07-17 复跑仍 PASS）。  
**本页仅供财务/质检业务确认后签字**；技术通过 ≠ 业务放行。

---

## A. 财务核对（F1–F12）

| # | 核对项 | 样例/证据 | 技术 | 业务勾选 |
|---|--------|-----------|------|----------|
| F1 | 费用过账分录借贷平衡 | `FE202607150005` 凭证 `2077392021701861378` | ✅ | ☐ |
| F2 | 费用红冲净额抵销 | 红冲凭证 `2077392024029700098` | ✅ | ☐ |
| F3 | 手工凭证过账 | `MV202607150003` posted `2077392264883412994` | ✅ | ☐ |
| F4 | 手工凭证作废红冲 | reversal `2077392267282554882` CANCELLED | ✅ | ☐ |
| F5 | 发货生成应收 | `SD202607160004` + `AR-…6994` | ✅ | ☐ |
| F6 | 收款核销 SETTLED | `FR202607160004` remain=0 | ✅ | ☐ |
| F7 | 销售退货冲应收 | `SRT202607150005` POSTED | ✅ | ☐ |
| F8 | 采购付款应付 SETTLED | `PR…009` / `FP…004` / `AP-…4210` | ✅ | ☐ |
| F9 | 采购退货冲应付 | `PRT202607150005` POSTED | ✅ | ☐ |
| F10 | 锁定期间不可过账 | `2099-01` 锁定后调整过账拒绝并 reopen | ✅ | ☐ |
| F11 | 无凭证权限不可过账 | 临时用户 `nofin_*` 403；无 post/approve | ✅ | ☐ |
| F12 | 资金取消匹配原因落库 | `MV202607150006` UNMATCHED + unmatchReason | ✅ | ☐ |

**财务结论**: ☐ GO　☐ NO-GO  

签字: ________________　姓名: ________________　日期: ________  

备注: ________________________________________________

---

## B. 质检核对（Q1–Q5）

| # | 核对项 | 样例/证据 | 技术 | 业务勾选 |
|---|--------|-----------|------|----------|
| Q1 | 需检可建 IQC | `QC202607150003` | ✅ | ☐ |
| Q2 | 判定数量正确 | JUDGED 合格3+不合格1=4 | ✅ | ☐ |
| Q3 | 仅合格品回写入库 | `PR202607150013` 行数量=3 | ✅ | ☐ |
| Q4 | 未判定禁止入库过账 | `PR202607160020` 拒绝「尚未完成质检」 | ✅ | ☐ |
| Q5 | 作废后不可判定 | `QC202607160002` CANCELLED 判定拒绝 | ✅ | ☐ |

**质检结论**: ☐ GO　☐ NO-GO  

签字: ________________　姓名: ________________　日期: ________  

备注: ________________________________________________

---

## C. 整体放行

| 门禁 | 状态 |
|------|------|
| 浏览器 smoke 34/34 | ✅ |
| preprod-full 无 Docker + 回滚 | ✅ GO |
| F/Q 技术预检 | ✅ 全 PASS（2026-07-17 复跑） |
| 财务签字 | ☐ |
| 质检签字 | ☐ |
| Docker Testcontainers release-check | ✅ PASSED |
| Docker Compose core 健康 + 采销产回滚 | ✅ |
| readiness DECIDED_GO + 证据包 READY | ✅ 本机 Docker 等价预生产 |
| 独立机房预生产（若政策强制） | ☐ 可选/按组织要求 |

**总判定（业务）**: ☐ 允许进入发布候选　☐ 驳回  

会签: 研发 ________　财务 ________　质检 ________　日期 ________  

扫描件归档目录建议:  
`target/local-runtime-acceptance-20260716-114617/signed/`

---

## D. 复跑命令（研发）

```powershell
java -jar target\erp-server-1.0.0.jar --spring.profiles.active=local
.\scripts\local-runtime-acceptance.ps1
node scripts\fq-signoff-all.cjs
```
