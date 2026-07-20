# Track A 执行收口（2026-07-17 当晚）

**发布候选基线（历史）**: `094d835` 曾通过 Testcontainers + Docker DECIDED_GO  
**当前 HEAD**: 以 `git rev-parse --short HEAD` 为准；含 backlog/文档归档等未入候选的 WIP 时，正式发布须重新 `release-check`。

---

## A2 技术复验 · DONE

| 项 | 结果 | 证据 |
|----|------|------|
| F/Q 全量预检 `node scripts/fq-signoff-all.cjs` | **technicalPass=true** | `target/fq-signoff-api-check/all-summary.json` |
| 正向 F1–F9/F12/Q1–Q3 | autoPass | `report.json` / `report.md` |
| 负例 F10/F11/Q4/Q5 | 4/4 PASS | `human-items-report.json` |
| Readiness 技术登记 | 已写 PASSED 项 | run `RDY20260717081136879` / item 见 summary |
| 历史 Docker 证据包 | 仍在盘 | `target/docker-preprod-full-20260717b/` |
| release-check 报告 | 历史 PASSED | `target/release-check-report.md` |

复跑时间：`2026-07-17T08:11:36Z`（UTC）。

---

## A1 业务签字 · TECH_READY / 等人

| 项 | 状态 |
|----|------|
| 技术勾选依据 | 全 PASS，不可用脚本代签业务 ☐ |
| 签字单 | `docs/FQ-SIGNOFF-FINAL-2026-07-16.md` |
| 点击路径 | `docs/FQ-SIGNOFF-CLICKPATH-2026-07-17.md` |
| 建议归档 | `target/docker-preprod-full-20260717b/signed/` |

**下一步（仅人类）**: 财务签 F1–F12，质检签 Q1–Q5，总判定 GO/NO-GO。

---

## A3 Readiness 回填 · PARTIAL_DONE

- F/Q 技术项：本轮 `fq-signoff-all.cjs` 已登记 PASSED。
- 其余码（`FINANCE_LEDGER` / `PERIOD_LOCK` / `INVENTORY_FINANCE_RECONCILIATION` / `INITIAL_IMPORT` / `BACKUP_ROLLBACK`）：
  - Docker 预生产目录已有对应证据文件时，用  
    `.\scripts\register-readiness-item-result.ps1`  
    按项挂附件；**无独立证据则保持 BLOCKED，禁止伪造**。
- UI：`/system/readiness`

---

## A4 运维监控 · CHECKLIST_READY

| 核对 | 技术侧 | 人工 |
|------|--------|------|
| Prometheus 模板 | `docs/monitoring/prometheus-alert-rules.yml` | 是否加载到监控平台 ☐ |
| 业务健康 API | `/api/system/observability/business-health` | 生产可达性 ☐ |
| 指标出口 | `/actuator/prometheus`（需认证） | 抓取配置 ☐ |
| 备份恢复 | 历史演练 `target/backup-rollback-drill-20260717-091000/` | 责任人/窗口 ☐ |
| 部署手册 | `docs/production-deployment.md` | 环境密钥非 CHANGE_ME ☐ |

---

## A5 独立机房 · POLICY_OPTIONAL

本机 Docker Compose core + 采销产回滚 + DECIDED_GO 已构成等价技术证据。  
若组织政策要求与开发机分离 → 在目标环境复跑 `docs/GATE_PROGRESS_2026-07-17.md` 命令摘要。

---

## A6 发布冻结 · DOCUMENTED

1. 仅从**干净工作树**明确 commit 发布；禁止把 `-AllowDirtyWorktree` 当正式证据。  
2. 正式：`.\scripts\release-check.ps1 -IncludeTestcontainers`  
3. 复验：`.\scripts\verify-release-check-report.ps1 -ReportDirectory target`  
4. 决策与归档：`decide-readiness-release.ps1` → `export-release-evidence-bundle.ps1`  
5. 权威状态文：`docs/WHAT_IS_MISSING.md` + 本文件 + `BACKLOG-THREE-TRACKS-2026-07-17.md`

---

## Track A 结论

| ID | 状态 |
|----|------|
| A2 | **DONE**（本轮复跑 PASS） |
| A1 | **BLOCKED_HUMAN**（包已齐） |
| A3 | **PARTIAL**（F/Q 已登；其余按证据回填） |
| A4 | **READY_FOR_OPS** |
| A5 | **OPTIONAL** |
| A6 | **DONE**（流程已文档化） |

**Track A 可自动化部分已完成。剩余唯一硬门禁：真人业务签字（A1）。**
