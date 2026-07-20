# 日常联调与验收约定（erp_codex_runtime）

**更新时间**: 2026-07-16  
**适用范围**: 本机前后端联调、ui-smoke、预生产脚本的本机替代跑法

---

## 1. 固定联调库

| 库名 | 用途 |
|------|------|
| **`erp_codex_runtime`** | **日常联调唯一推荐库**（`application-local.yml` 默认） |
| `erp` | 历史脏库：V78 checksum 漂移 + 残留 V79，**禁止日常联调** |
| `erp_codex_test` 等 | 临时/专项验证库，勿当默认 |

建库：

```sql
CREATE DATABASE IF NOT EXISTS erp_codex_runtime
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

启动后端（任选）：

```powershell
# 推荐：jar
java -jar target\erp-server-1.0.0.jar --spring.profiles.active=local

# 或 Maven
.\mvnw.cmd spring-boot:run

# 或一键 bat（会尝试建库）
.\start-backend.bat
```

前端：`erp-frontend` Vite 5173 代理 `/api` → `8080`，无需改库配置。

---

## 2. 账号

| 用户 | 密码 | 角色 | 说明 |
|------|------|------|------|
| `admin` | `LocalAdmin123` | SUPER_ADMIN | local profile bootstrap；可覆盖 `ERP_LOCAL_ADMIN_PASSWORD` |
| `runtime_smoke` | `RuntimeSmoke123` | ERP_ADMIN(3002) | **不自动播种**；ui-smoke 提交人，防自审拒绝 |

重建干净库后补 `runtime_smoke`：

1. 登录 admin  
2. `POST /api/system/users` 创建用户  
3. `PUT /api/system/users/{id}/roles` 赋 `roleIds: [3002]`  

---

## 3. 本机「预生产全链路」验收（无 Docker）

真预生产需要独立环境 + 业务签字。本机可先用干净库跑通同一套脚本，作为技术证据：

```powershell
# 后端已启动并连 erp_codex_runtime 后：
.\scripts\local-runtime-acceptance.ps1
```

默认带 **RollbackAfterSuccess**（生产补偿：反完工→退料→取消工单释放预留→采购退货）。仅排障可加 `-NoRollback`。

等价于：

```powershell
.\scripts\preprod-full-acceptance.ps1 `
  -SkipReleaseCheck `
  -SkipComposeUp `
  -BaseUrl http://127.0.0.1:8080 `
  -Username admin `
  -Password LocalAdmin123 `
  -WarehouseId 4501 `
  -MaterialWarehouseId <材料仓 ACTIVE id> `
  -FinishedWarehouseId <成品仓 ACTIVE id> `
  -BusinessDate "yyyy-MM-dd" `
  -RollbackAfterSuccess
```

说明：

- `-SkipReleaseCheck`：跳过 Docker/Testcontainers 发布门禁（本机无 Docker 时必须）  
- `-SkipComposeUp`：不拉 docker-compose，并对已运行 BaseUrl 做 health/login 冒烟  
- 材料仓/成品仓 **不要** 与主仓相同；`local-runtime-acceptance.ps1` 会尽量自动选  
- 采购/销售/生产脚本在 `admin` 审批时会自动用 `runtime_smoke` 作提交人（防自审拒绝）  
- 商品类型字典仅 `PHYSICAL/GOODS/SERVICE`（脚本已对齐）  
- local profile 关闭 `erp.rate-limit.enabled`，避免验收连登 429  
- 生产退料会 `restoreReservation`，回滚须 **取消 RELEASED 工单释放预留** 后再采购退货（2026-07-16 已修）  
- 证据目录：`target/local-runtime-acceptance-<timestamp>/`  
- readiness 默认项里，`RELEASE_GATE` 和 `DOCKER_COMPOSE_HEALTH` 会因为 `-SkipReleaseCheck` / `-SkipComposeUp` 被登记成 `BLOCKED`，`AUTH_SMOKE` 只按本机实际登录与受保护接口结果登记；所以本机 GO 只是技术链路通了，不等于独立预生产放行。  
- 无 Docker 时下一步别硬冲 `DECIDED_GO`；先把当前 run 里剩余默认项按证据回填成真实状态。仓库提供 `.\scripts\register-readiness-item-result.ps1`，专门给 `FINANCE_LEDGER`、`PERIOD_LOCK`、`INVENTORY_FINANCE_RECONCILIATION`、`INITIAL_IMPORT`、`BACKUP_ROLLBACK` 这 5 个已有默认项补证据。能证明的记 `PASSED`，证明不了就老实记 `BLOCKED`。  
- 示例：`.\scripts\register-readiness-item-result.ps1 -BaseUrl http://127.0.0.1:8080 -Username admin -Password LocalAdmin123 -ReadinessRunId <runId> -ItemCode FINANCE_LEDGER -Status PASSED -EvidenceSummary "F/Q finance technical evidence recorded" -EvidenceDetailPath target\fq-signoff-api-check\report.md -EvidenceRequestUri target\fq-signoff-api-check\report.md`  
- 业务签字清单见 `docs/finance-qc-acceptance-runbook.md`（F1–F12 / Q1–Q5）——脚本 GO ≠ 财务签字  

**2026-07-16 本机结果**: clean schema `erp_codex_acceptance_20260716_1603` 默认带回滚 → **GO**（`target/local-runtime-acceptance-20260716-160441/summary.md`）。同一套证据里已回填 `FINANCE_LEDGER=PASSED`、`PERIOD_LOCK=PASSED`、`INVENTORY_FINANCE_RECONCILIATION=PASSED`、`INITIAL_IMPORT=PASSED`、`PREPROD_APPROVAL_GATE=PASSED`、`BACKUP_ROLLBACK=BLOCKED`；`PERIOD_LOCK` 细证据见 `target/local-runtime-acceptance-20260716-160441/period-lock/period-lock-readiness.md`，`INITIAL_IMPORT` 细证据见 `target/local-runtime-acceptance-20260716-160441/initial-import/initial-import-readiness.md`，`BACKUP_ROLLBACK` 细证据见 `target/local-runtime-acceptance-20260716-160441/backup-rollback/backup-rollback-readiness.md`，审批前总门禁报告见 `target/local-runtime-acceptance-20260716-160441/preprod-acceptance-gate.md`，其复验报告见 `target/local-runtime-acceptance-20260716-160441/preprod-acceptance-gate.verify-report.md`，当前 dry-run 决策报告见 `target/local-runtime-acceptance-20260716-160441/readiness-release-decision.md`。

---

## 4. 找联调缺口的正确姿势（不要读 6 月 md）

```
后端 *Controller 写端点 (POST/PUT/DELETE/PATCH)
  + @PreAuthorize 权限码
  + Flyway BUTTON 菜单种子
      ↓
前端 src/api/*.ts 是否封装
      ↓
views 是否有按钮/handler + v-permission
```

缺任一环才是真缺口。  
2026-07-16 全量扫描结论：写端点 **194/194 已封装，MISSING_API=0**；原 IQC 草稿 PUT ORPHAN 已接页面（见 `docs/controller-frontend-write-endpoint-gap-2026-07-16.md`）。  
过时文档（`frontend-backend-integration-summary.md`、`API_FIX_REPORT.md` 等 6 月快照）仅作历史，**不得**据此开补洞任务。

现状结论以 `docs/WHAT_IS_MISSING.md` 与 `target/ui-smoke-report.json` 为准。

---

## 5. 相关文档

- `docs/finance-qc-acceptance-runbook.md` — 财务/质检签字级验收  
- `docs/inventory-alert-lifecycle-decision.md` — 库存预警不新建实例表  
- `docs/WHAT_IS_MISSING.md` — 完成度与缺口总盘  
