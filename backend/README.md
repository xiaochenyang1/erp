# ERP Server

这是一个 Java 17 / Spring Boot / Maven 后端项目。当前仓库路径里出现的
`python/erpServer` 是历史遗留目录名，不代表项目使用 Python 运行，也不代表入口在
Python 脚本里。

## 技术栈

- Java 17
- Spring Boot
- Maven Wrapper
- Flyway 数据库迁移
- MyBatis-Plus

## 常用命令

Windows 本地优先使用 Maven Wrapper：

```powershell
.\mvnw.cmd -B test
```

构建发布包：

```powershell
.\mvnw.cmd -B clean package
```

正式发布门禁（有 Docker 的 CI、预生产和最终发布环境必须包含 Testcontainers）：

```powershell
.\scripts\release-check.ps1 -IncludeTestcontainers
```

本地非发布排查当前脏工作树时，才显式允许未提交改动：

```powershell
.\scripts\release-check.ps1 -AllowDirtyWorktree
```

本地非发布排查且需要覆盖 Testcontainers 时：

```powershell
.\scripts\release-check.ps1 -AllowDirtyWorktree -IncludeTestcontainers
```

不要把 `-AllowDirtyWorktree` 用作正式发布证据；正式发布必须来自明确 commit，并用默认复验规则拒绝脏工作树生成的通过报告。

## 本地登录与联调库

`local` profile 仅供本机联调使用。`application-local.yml` **默认连接干净库 `erp_codex_runtime`**（不要用历史脏库 `erp`）。

全新本地库首次启动后会初始化内置管理员：

- 用户名：`admin`
- 默认密码：`LocalAdmin123`

如需覆盖默认本地密码，启动前设置 `ERP_LOCAL_ADMIN_PASSWORD`。生产环境不使用该逻辑，生产首次启动仍必须通过 `ERP_BOOTSTRAP_ADMIN_PASSWORD` 初始化管理员密码。

ui-smoke / 验收脚本还需要手工账号 `runtime_smoke / RuntimeSmoke123`（角色 ERP_ADMIN=3002，重建库后需补建）。

本机无 Docker 时，用干净库跑与预生产同构的全链路脚本：

```powershell
.\scripts\local-runtime-acceptance.ps1
```

本地后端全量测试建议使用一次性 MySQL，避免依赖本机已有数据库账号或历史库：

```bash
./scripts/test-local.sh
```

Windows PowerShell：

```powershell
.\scripts\test-local.ps1
```

两个入口都会启动一个临时 MySQL 8.4，自动注入 `ERP_TEST_DATASOURCE_*`，测试结束后删除容器；普通测试使用 `test` profile 的 Redis 替身，标记为 `testcontainers` 的测试仍由 Maven/Testcontainers 自己启动隔离容器。脚本会自动把当前 Docker CLI context 的 endpoint 传给 Testcontainers（含 Colima 的 Ryuk socket 映射）。可用 `ERP_LOCAL_TEST_MYSQL_*` 环境变量覆盖镜像、库名和账号。未传 Maven goal 时默认执行 `test`；需要完整发布构建时可直接传入 Maven参数和 goal，例如：

```bash
./scripts/test-local.sh -Ptestcontainers -Derp.testcontainers.enabled=true clean package
```

F/Q 技术预检默认兼容写入 `target/fq-signoff-api-check/`。作为 RC 或发布证据时，应通过 `ERP_FQ_EVIDENCE_DIRECTORY`（或 `ERP_EVIDENCE_DIRECTORY`）指定不会被 Maven `clean` 删除的独立目录，并把该目录纳入发布证据包。

Track B 扩展能力（询价/发票/OQC/信用 + 数据范围）可单独复跑：

```powershell
.\scripts\local-extension-regression.ps1
# 或：
node scripts\extension-features-api-smoke.cjs
node scripts\data-scope-api-smoke.cjs
```

约定详见 `docs/local-runtime-integration.md`。找联调缺口请扫 Controller 写端点 → 前端 `src/api` → 页面按钮，不要按 6 月旧 md 开补洞任务。

## 关键文档

- `docs/WHAT_IS_MISSING.md`：当前完成度与缺口（以代码+smoke 为准）
- `docs/未完成.md`：需要但尚未具备的功能总清单（P0 签字 / P1 业务 / P2 体验与工程债）
- `docs/BACKLOG-THREE-TRACKS-2026-07-17.md`：上线最小集 / 业务扩展 / 工程债 三线 backlog
- `docs/EXECUTION-BOARD-2026-07-17.md`：三线执行总看板（终态）
- `docs/EXTENSION-FEATURES-SMOKE-2026-07-17.md`：扩展能力 API smoke 证据
- `docs/TRACK-A-COMPLETION-2026-07-17.md`：发布路径技术收口
- `docs/local-runtime-integration.md`：日常联调库 / 账号 / 本机验收
- `docs/finance-qc-acceptance-runbook.md`：财务/质检签字级验收
- `docs/GATE_PROGRESS_2026-07-17.md`：发布门禁推进记录
- `docs/production-deployment.md`：生产部署与环境配置
- `docs/business-readiness-checklist.md`：上线前人工验收清单
- `docs/api-conventions.md`：API 响应协议
- `docs/migrations-history.md`：Flyway 迁移版本历史说明
