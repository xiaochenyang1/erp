# 发布门禁推进记录（2026-07-17）

**范围**: 按「签字 → Docker/Testcontainers → 备份回滚 → DECIDED_GO」顺序推进  
**候选 commit**: `094d835`  
**结论**: 技术侧本机 Docker 等价预生产已达成 **`DECIDED_GO`** 并归档证据包；**财务/质检业务签字仍缺真人签字**。

---

## 已完成

| 项 | 结果 | 证据 |
|----|------|------|
| 安装并启动 Docker Desktop | OK `29.6.1` | 本机 |
| `release-check -IncludeTestcontainers` 干净工作树 | **PASSED** | `target/release-check-report.md` |
| 数据库备份 + 恢复演练（101/101 表） | **PASSED** | `target/backup-rollback-drill-20260717-091000/` |
| Docker Compose core（MySQL/Redis/erp-server） | **healthy** | 端口 `13306/16379/18080`，`.env.preprod-local` |
| 采/销/产链路 + 补偿回滚 | **PASSED** | `target/docker-preprod-full-20260717b/03~05-*.md` |
| 审批前总门禁 | **READY_FOR_APPROVAL** | `.../preprod-acceptance-gate.md` |
| 最终发布决策 | **DECIDED_GO** | `.../readiness-release-decision.md`，run `2077928548899852290` |
| 发布证据包 | **READY** | `.../release-evidence-bundle-20260717-093444.zip` |
| F/Q 技术预检（local `erp_codex_runtime`） | **technicalPass=true** | `target/fq-signoff-api-check/all-summary.json`，run `RDY20260717013534335` |

### Docker 栈要点

- 官方 `Dockerfile` 会在镜像内完整 Maven 构建；本机用 `Dockerfile.prebuilt` + `docker-compose.prebuilt.yml` 注入已通过 release-check 的 jar（`.dockerignore` 排除了 `target/`）。
- JDBC 必须用 `characterEncoding=UTF-8`（`utf8mb4` 作 Java charset 会启动失败）。
- 验收前关闭限流：`SPRING_APPLICATION_JSON={"erp":{"rate-limit":{"enabled":false}}}`。
- Compose 默认 `env_file: .env.prod` 含 `CHANGE_ME`；本机用 override 强制 `.env.preprod-local` 密钥。

### Readiness run（Docker PREPROD）

- Run ID: `2077928548899852290` / `RDY20260717012424435`（创建时）→ 决策后 **GO/PASSED**
- 全部默认 P0/P1 项 **PASSED**（含 `RELEASE_GATE` / `DOCKER_COMPOSE_HEALTH` / `BACKUP_ROLLBACK` / `PREPROD_ACCEPTANCE` / `PREPROD_APPROVAL_GATE`）

---

## 仍缺（不能技术伪造）

1. **财务/质检业务签字**  
   表单：`docs/FQ-SIGNOFF-FINAL-2026-07-16.md`  
   点击路径：`docs/FQ-SIGNOFF-CLICKPATH-2026-07-17.md`  
   技术预检已再次全 PASS，业务勾选/签字栏仍空。

2. **独立机房预生产**（若组织要求与本机 Docker 区分）  
   本机 Docker Desktop + compose core 已形成等价技术证据；若政策要求「独立机房」，需在目标环境复跑同一脚本链。

3. **工作区 WIP（与门禁基线分离）**  
   详见 `docs/WORKTREE_SAFETY_INVENTORY_2026-07-17.md`。  
   安全备份 stash（工作区已 apply，stash 仍保留）：  
   - 后端：`SAFETY-BACKUP-code-wip-20260717-before-safe-cleanup`  
   - 前端：`SAFETY-BACKUP-frontend-wip-20260717`  
   **未**自动 commit 业务大重构；`docker-dist/` 已加入 `.gitignore`。

---

## 复跑命令（摘要）

```powershell
# 1) 干净树 + Testcontainers 发布门禁
.\scripts\release-check.ps1 -IncludeTestcontainers -MavenRepoLocal E:\tuowei\python\erpServer\.m2-release

# 2) Docker core（预构建 jar）
docker compose --env-file .env.preprod-local -f docker-compose.yml -f docker-compose.prebuilt.yml --profile core up -d

# 3) 业务链路（示例）
.\scripts\purchase-to-payment-acceptance.ps1 -BaseUrl http://127.0.0.1:18080 -Username admin -Password LocalPreprodAdmin1 -WarehouseId 4501 -BusinessDate yyyy-MM-dd -RollbackAfterSuccess
# sales / production 同理

# 4) 决策
.\scripts\decide-readiness-release.ps1 -EvidenceDirectory target\docker-preprod-full-20260717b -BaseUrl http://127.0.0.1:18080 -Username admin -Password LocalPreprodAdmin1

# 5) 归档
.\scripts\export-release-evidence-bundle.ps1 -EvidenceDirectory target\docker-preprod-full-20260717b -ReleaseCheckReportDirectory target

# 6) F/Q 技术预检（local profile / LocalAdmin123）
node scripts\fq-signoff-all.cjs
```

---

## 业务签字下一步

打印或打开 `docs/FQ-SIGNOFF-FINAL-2026-07-16.md`，财务签 F1–F12，质检签 Q1–Q5，扫描件建议归档到：

`target/docker-preprod-full-20260717b/signed/`
