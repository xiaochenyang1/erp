# 发布验收证据中心设计

**日期**: 2026-07-07
**范围**: `erpServer` 后端 + `erp-frontend` 前端
**目标**: 在现有预生产验收模块基础上，补齐自动证据导入、验收报告导出和 Go/No-Go 门禁规则，让上线判断可执行、可追溯、可复核。

## 背景

当前系统已经有 `sys_readiness_run`、`sys_readiness_item`、`sys_readiness_evidence` 三张表，也有 `/api/system/readiness` 运行单、验收项、证据和决策接口。前端 `/system/readiness` 已能创建运行单、记录预检证据、登记人工证据和做 Go/No-Go 决策。

缺口不在 CRUD，而在正式交付证据链：

- 后端 `release-check.ps1` 已能生成 `target/release-check-report.json` 和 Markdown 报告，但不能直接导入 readiness 运行单。
- 前端 `lint`、`type-check`、`build` 和 UI smoke 结果还停留在命令行或脚本输出，无法形成统一验收证据。
- Go 决策只检查 P0/P1 验收项是否通过，尚未固化“必须有 release gate、migration preflight、frontend build、core smoke”等证据类别。
- 运行单详情可以查看证据，但缺少一份面向交付的汇总报告导出。

## 非目标

- 不做完整 CI/CD 平台。
- 不做定时任务调度和多环境流水线编排。
- 不在后端直接执行 `npm`、`mvn`、Docker 或浏览器 smoke；这些检查仍由外部脚本执行，系统只接收和校验证据。
- 不改造现有 `release-check.ps1` 的核心构建逻辑，只新增面向 readiness 的报告导入入口或配套脚本。

## 方案

推荐在现有 readiness 模块上做四个增强：

1. **自动证据导入**
   新增接口接收 release check、前端检查和 UI smoke 的结构化结果，将它们映射到固定验收项和证据记录。

2. **门禁规则固化**
   Go 决策前强制检查 P0/P1 验收项状态，并额外要求关键验收项必须存在有效证据。

3. **验收报告导出**
   新增运行单报告接口，返回 Markdown 或 CSV，包含运行单、验收项、证据摘要、失败原因、决策信息和操作者。

4. **前端验收工作台增强**
   在现有 `/system/readiness` 页面增加证据导入入口、关键门禁状态摘要和报告下载按钮。

## 后端设计

### 新增证据导入模型

新增 `ReadinessEvidenceImportRequest`：

- `sourceType`: `RELEASE_CHECK`、`FRONTEND_CHECK`、`UI_SMOKE`、`MANUAL_SCRIPT`
- `sourceName`: 命令或脚本名，如 `release-check.ps1`
- `status`: `PASSED`、`FAILED`、`BLOCKED`
- `summary`: 摘要
- `detail`: 原始报告摘要或 JSON 文本，长度按数据库字段约束裁剪
- `reportPath`: 本地或 CI artifact 相对路径
- `artifactSha256`: 可选，用于 JAR、SBOM、前端构建包等产物追踪
- `checkedAt`: 检查时间
- `items`: 结构化子项列表，每项包含 `code`、`name`、`status`、`summary`、`detail`

新增 `ReadinessEvidenceImportItemRequest`：

- `code`: 固定验收项编码
- `name`: 展示名称
- `category`: 分类
- `priority`: `P0`、`P1`、`P2`
- `status`: `PASSED`、`FAILED`、`BLOCKED`、`SKIPPED`
- `summary`: 子项摘要
- `detail`: 子项明细

### 新增接口

在 `ReadinessController` 增加：

- `POST /api/system/readiness/runs/{id}/evidence-imports`
  - 权限：`system:readiness:manage`
  - 行为：导入一批结构化证据；不存在的验收项按 `item.code` 创建，已存在的验收项更新状态和结果。

- `GET /api/system/readiness/runs/{id}/report.md`
  - 权限：`system:readiness:view`
  - 行为：返回 `text/markdown`，文件名 `readiness-<runNo>.md`。

- `GET /api/system/readiness/runs/{id}/report.csv`
  - 权限：`system:readiness:view`
  - 行为：返回验收项和证据摘要 CSV，便于交付归档。

### 固定验收项编码

继续保留现有默认项，并补齐自动证据项：

- `RELEASE_GATE`: 后端 release check、JAR、SBOM、release report。
- `MIGRATION_PREFLIGHT`: Flyway 和本地库迁移预检。
- `FRONTEND_LINT`: 前端 ESLint。
- `FRONTEND_TYPE_CHECK`: 前端 `vue-tsc`。
- `FRONTEND_BUILD`: 前端 `vite build`。
- `CORE_UI_SMOKE`: 核心业务 UI smoke。
- `BUSINESS_ACCEPTANCE`: 人工业务验收清单。
- `BACKUP_ROLLBACK`: 备份与回滚确认。

### Go 决策规则

`ReadinessService.decide()` 保持现有 P0/P1 必须通过规则，并新增关键证据检查：

- `RELEASE_GATE` 必须为 `PASSED` 且至少有一条 `LOG` 或 `ATTACHMENT` 证据。
- `MIGRATION_PREFLIGHT` 必须为 `PASSED`。
- `FRONTEND_BUILD` 必须为 `PASSED`。
- `CORE_UI_SMOKE` 必须为 `PASSED`。
- `BUSINESS_ACCEPTANCE` 必须为 `PASSED` 或不存在；如果存在且为 P0/P1，则按现有规则要求通过。
- 任一 P0/P1 项为 `FAILED`、`BLOCKED`、`PENDING` 时禁止 Go。
- No-Go 仍要求填写原因。

### 数据库迁移

优先不改原三表结构。如果 `detail` 的 2048 长度不足以保存摘要，新增迁移将 `sys_readiness_evidence.detail` 扩展到 `TEXT`。不新增独立导入批次表，避免过度设计；导入批次信息写入证据 `summary/detail`。

建议新增 Flyway：

- `V92__readiness_evidence_report_import.sql`
  - 扩展 `sys_readiness_evidence.detail` 为 `TEXT`。
  - 种子菜单不需要新增，复用现有 `system:readiness:view/manage/decide`。

## 前端设计

### API

在 `src/api/readiness.ts` 增加：

- `importReadinessEvidence(runId, data)`
- `downloadReadinessMarkdownReport(runId)`
- `downloadReadinessCsvReport(runId)`

继续保持 Long ID 字符串归一化。

### 页面

在 `/system/readiness` 增加：

- 运行单操作列：
  - `导入证据`
  - `下载报告`
- 详情抽屉顶部增加门禁摘要：
  - Release Gate
  - Migration Preflight
  - Frontend Build
  - Core UI Smoke
  - Go/No-Go
- 导入证据弹窗：
  - 选择证据类型
  - 粘贴报告摘要或 JSON
  - 填写报告路径和摘要
  - 支持按模板生成固定子项

前端不负责执行命令，只负责登记结果和下载报告。

## 配套脚本

后端新增或增强脚本：

- `scripts/import-readiness-evidence.ps1`
  - 参数：`-RunId`、`-BaseUrl`、`-Token`、`-ReleaseReportPath`、`-FrontendReportPath`、`-SmokeReportPath`
  - 行为：读取本地报告文件，调用 readiness 导入接口。

如果前端 smoke 当前只输出控制台，应先让 `scripts/ui-smoke.mjs` 支持可选 `--report-json <path>`，输出结构化报告，供导入脚本读取。

## 错误处理

- 导入不存在运行单：返回业务错误。
- 已关闭运行单禁止导入证据。
- `sourceType`、`status`、`priority` 不合法时拒绝。
- Go 决策缺少关键证据时返回明确错误，例如：`缺少 FRONTEND_BUILD 通过证据，不能标记 Go`。
- 报告导出时运行单不存在返回业务错误。

## 测试计划

后端：

- `ReadinessEvidenceImportServiceTest`
  - 导入 release check 证据会创建或更新 `RELEASE_GATE`。
  - 导入失败证据会把验收项标记为 `FAILED`。
  - 关闭运行单后导入被拒绝。
  - Go 决策缺少关键证据被拒绝。
  - Go 决策关键证据齐全且 P0/P1 通过时成功。

- `ReadinessControllerTest`
  - 导入接口权限和成功响应。
  - Markdown 报告下载响应头和内容。
  - CSV 报告下载响应头和内容。

- `FlywayMigrationSmokeTest`
  - `V92` 可应用。

前端：

- `npm run type-check`
- `npm run build`
- 如果已有 lint 规则，运行 `npm run lint`

集成：

- 后端 `.\mvnw.cmd -B test`
- 后端 `.\scripts\release-check.ps1 -AllowDirtyWorktree`
- 前端 `npm run build`

## 验收标准

- 可以从现有 readiness 页面创建运行单。
- 可以导入 release check、前端 build/type-check/lint、UI smoke 结果。
- 导入后运行单详情展示关键门禁摘要。
- 关键门禁缺失或失败时不能 Go。
- 关键门禁全部通过且 P0/P1 验收项全部通过时可以 Go。
- 可以下载 Markdown 和 CSV 验收报告。
- 两个仓库最终 `git status --short` 干净。
