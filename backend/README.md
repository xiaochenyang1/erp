# ERP Server

当前开发状态见 [2026-11-11 项目状态](docs/PROJECT-STATUS-2026-11-11.md)。一期 PR #18 已于 2026-11-10 合并到 `main`（`1e1f6c5`），但尚无 tag、GitHub Release 或真实预生产签字。当前二期分支已实现试算平衡、资产负债表、利润表、直接法现金流量表、账套级映射、CSV 和 V177 月结不可变快照，V178 补齐默认报表映射引用的税费科目；它们属于管理报表最小版，间接法和完整法定格式不在本期范围。候选 `560080e` 已通过 MySQL 8.4/Testcontainers 后端 2,649 项测试、前端 252 个测试文件/1,201 项测试、31/31 路由、43/43 工作流、完整部署、V168 → V178 升级恢复/旧版回退及同一 SHA 的 Monitoring Verify。本轮代码评审又修复了只读总账/预算角色加载科目树被拒，以及映射界面展示停用科目的问题；该评审提交仍须在新 SHA 上重跑全部门禁。当前剩余外部代码评审、[FS-1–FS-13 财务报表验收](docs/FINANCIAL-STATEMENTS-ACCEPTANCE-2026-11-11.md)和原有业务/运维签字。

一期制造与运维验收继续使用 [V170–V174 人工验收包](docs/P0-HUMAN-GATES-EXECUTION-PACKAGE-2026-11-09.md)；排程预览和执行账号必须具备 `ALL` 数据范围，V168→V174 升级恢复与旧版回退的历史完整证据基线为 `3b70299`，不能直接证明当前财务报表候选通过。

前后端统一部署入口见 [完整部署包](docs/FULLSTACK-DEPLOYMENT.md)：复用已通过验证的 JAR 和前端制品，提供 Nginx、同源 API 代理、私有配置初始化及隔离部署回归。

最新制造扩展包括 [多层分仓 MRP](docs/MRP-MULTILEVEL-2026-11-07.md)、[生产成本核算 1.0](docs/PRODUCTION-COST-ACCOUNTING-1.0.md)和[有限产能排程一期](docs/FINITE-CAPACITY-SCHEDULING-2026-11-08.md)。库存调整与盘点审批见 [开发记录](docs/INVENTORY-APPROVAL-2026-11-06.md)，完整部署 CI 见 [验证记录](docs/DEPLOYMENT-CI-2026-11-06.md)。

部署 CI 还包含 [V168 → V174 升级与数据库、附件恢复](docs/UPGRADE-RECOVERY-2026-11-06.md)：旧版后端通过正常 API 生成历史单据，停写后共同备份，恢复到新卷并验证升级后的审批、库存、附件、MRP、生产成本和排程迁移；同一流程还恢复 V168 检查点并运行旧版前后端验证回退。

当前启动修复见 [2026-10-28 浏览器回归就绪检查](docs/UI-SMOKE-READINESS-2026-10-28.md)：账号准备和浏览器回归等待管理员初始化事务提交，避免健康接口已可访问时提前登录；候选结果以同一提交的实际报告为准。

当前交付收尾见 [2026-10-23 浏览器 CI 退出修复](docs/UI-SMOKE-CLEANUP-2026-10-23.md)：修复 Linux 下遗留的 npm 后代进程，增加有期限的退出等待与清理证据；后续继续独立预生产和真实业务验收。

最新 F/Q 验收修复与复验见 [F10 月结前置条件修复](docs/F10-ACCEPTANCE-FIX-2026-10-21.md)：补齐未来期间的调汇准备、完整负例及清理证据；候选结论以实际归档为准。

最新交接见 [2026-10-21 预生产交接](docs/PREPROD-HANDOFF-2026-10-21.md)：隔离生产恢复演练 14/14 通过，F/Q 预检支持显式预生产账号及部署版本记录；真实环境与业务签字待绑定。

这是一个 Java 17 / Spring Boot / Maven 后端项目。当前仓库路径里出现的
`python/erpServer` 是历史遗留目录名，不代表项目使用 Python 运行，也不代表入口在
Python 脚本里。

当前监控与发布验证收口见 [2026-10-21 候选说明](docs/RELEASE-CANDIDATE-2026-10-21.md)，完整本地验收入口为 `scripts/release-candidate-local.sh /absolute/new/evidence-directory`。

候选通过后可继续执行 [生产配置与数据库恢复演练](docs/PRODUCTION-REHEARSAL-2026-10-21.md)，验证真实 `prod` 服务、监控及独立数据库恢复。

此前 MRP 修复、候选验证入口和预生产交接见 [2026-10-15 发布候选记录](docs/RELEASE-CANDIDATE-2026-10-15.md)。

上一轮多币种开发与验证进展见 [2026-10-14 优化与回归记录](docs/OPTIMIZATION-2026-10-14.md)；模块范围及审计基线见 [项目现状报告](docs/PROJECT-STATUS-2026-10-14.md)。

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

完整 PowerShell 7 安装必须包含 `Modules/`。可设置 `PWSH` 为可执行文件绝对路径；`test-local.sh --release-check` 会预检基础命令，发布脚本会将自身运行时传给 JVM 行为测试，避免因 PATH 缺失而跳过测试。

两个入口都会启动一个临时 MySQL 8.4，自动注入 `ERP_TEST_DATASOURCE_*`，测试结束后删除容器；普通测试使用 `test` profile 的 Redis 替身，标记为 `testcontainers` 的测试仍由 Maven/Testcontainers 自己启动隔离容器。脚本会自动把当前 Docker CLI context 的 endpoint 传给 Testcontainers（含 Colima 的 Ryuk socket 映射）。可用 `ERP_LOCAL_TEST_MYSQL_*` 环境变量覆盖镜像、库名和账号。未传 Maven goal 时默认执行 `test`；需要完整发布构建时可直接传入 Maven参数和 goal，例如：

```bash
./scripts/test-local.sh -Ptestcontainers -Derp.testcontainers.enabled=true clean package
```

macOS/Linux 浏览器回归可在构建完成后运行：

```bash
./scripts/ui-smoke-local.sh
```

该入口使用一次性 MySQL，初始化管理员及审批提交账号，再运行页面与业务流程；结束后自动删除测试容器，不依赖本机业务库。需要 Docker、Java 17、Node.js、已安装的前端依赖及 Chrome/Edge（也可设置 `CHROME_PATH`）。默认后端端口 19381、前端 19373；可用 `ERP_UI_SMOKE_BACKEND_PORT`、`UI_SMOKE_FRONTEND_PORT` 覆盖。仅复跑多币种专项时使用 `UI_SMOKE_ROUTES=0 UI_SMOKE_WORKFLOW=currency ./scripts/ui-smoke-local.sh`。报告位于 `target/ui-smoke-report.json`，记录源码提交及工作区是否有未提交改动；正式发布仍需干净候选和独立归档。

F/Q 技术预检默认兼容写入 `target/fq-signoff-api-check/`。作为 RC 或发布证据时，应通过 `ERP_FQ_EVIDENCE_DIRECTORY`（或 `ERP_EVIDENCE_DIRECTORY`）指定不会被 Maven `clean` 删除的独立目录，并把该目录纳入发布证据包。

Track B 扩展能力（询价/发票/OQC/信用 + 数据范围）可单独复跑：

```powershell
.\scripts\local-extension-regression.ps1
# 或：
node scripts\extension-features-api-smoke.cjs
node scripts\data-scope-api-smoke.cjs
node scripts\financial-statements-api-smoke.mjs
```

约定详见 `docs/local-runtime-integration.md`。找联调缺口请扫 Controller 写端点 → 前端 `src/api` → 页面按钮，不要按 6 月旧 md 开补洞任务。

## 监控部署

运行 `./scripts/verify-monitoring.sh` 校验监控配置、告警触发/恢复时序及接收器路由；部署与 JWT 轮换见 [监控说明](docs/monitoring/README.md)。Compose 叠加文件为 `docker-compose.monitoring.yml`，Grafana 数据源和看板会自动加载。

## 关键文档

- `docs/PROJECT-STATUS-2026-11-11.md`：当前财务报表二期候选的完成度、验证证据、范围边界和执行顺序
- `docs/FINANCIAL-STATEMENTS-ACCEPTANCE-2026-11-11.md`：V175–V178 财务报表、月结快照及默认映射修正 FS-1–FS-13 预生产验收清单
- `docs/PROJECT-STATUS-2026-11-09.md`：一期合并前的历史候选状态与完整 CI 基线
- `docs/P0-HUMAN-GATES-EXECUTION-PACKAGE-2026-11-09.md`：V170–V174 真实预生产与人工签字包
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
