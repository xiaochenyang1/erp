# ERP Server 生产部署说明

本文档描述当前后端服务的最小生产部署路径。上线前必须先通过构建打包、真实环境冒烟和人工业务验收，并确认真实生产密钥没有提交到 Git。

## 必需环境变量

从 `.env.prod.example` 复制出 `.env.prod` 后替换所有 `CHANGE_ME` 值：

- `MYSQL_ROOT_PASSWORD`：MySQL root 密码。
- `MYSQL_PASSWORD`、`ERP_DATASOURCE_PASSWORD`：MySQL 应用账号密码；使用仓库 Compose 创建数据库时两者必须保持一致。
- `ERP_REDIS_PASSWORD`：Redis 密码。
- `ERP_REDIS_TIMEOUT`：后端访问 Redis 的连接/命令超时时间，默认 `5s`。
- `ERP_JWT_SECRET`：生产 JWT 签名密钥，至少 48 字节，不要使用可猜测短语。
- `ERP_JWT_REFRESH_TOKEN_TTL_SECONDS`：刷新令牌有效秒数，默认 `1209600`。
- `ERP_CORS_ALLOWED_ORIGINS`：允许访问后端的前端 Origin，多个地址用英文逗号分隔。必须填写 `http://host[:port]` 或 `https://host[:port]` 这种纯 Origin，不能包含 `*`、路径、查询串或片段。
- `ERP_TRUSTED_PROXIES`：可信反向代理 IP 或 CIDR，多个值用英文逗号分隔。只有请求来源命中这里时，后端才会信任 `X-Forwarded-For` / `X-Real-IP`；直连部署保持空值。
- `ERP_BOOTSTRAP_ADMIN_PASSWORD`：首次生产启动时初始化 `admin` 密码，必须符合系统密码策略：12 到 72 位、包含字母和数字、不含空白字符，且 UTF-8 编码后不超过 72 字节。首次启动成功后，数据库中的 `erp.bootstrap.admin-password-initialized` 会被置为 `true`。
- `ERP_FLYWAY_BASELINE_ON_MIGRATE`：默认 `false`。只有接管已有非空旧库且已人工确认迁移边界时才允许设为 `true`。

## 运行时阈值

`.env.prod.example` 还暴露了一组可按生产容量调整的运行时阈值。默认值按当前最小发布门禁设置，正式调整前要结合网关限制、数据库连接池、文件上传策略和人工验收结果一起看，别单独把某个数字拧大就当扩容。

- `ERP_IDEMPOTENCY_ENABLED`：是否启用写接口幂等保护，生产默认保持 `true`。
- `ERP_IDEMPOTENCY_TTL_SECONDS`：幂等请求记录保留秒数，默认 `86400`。
- `ERP_IDEMPOTENCY_MAX_REPLAY_BODY_BYTES`：允许缓存并重放的响应体大小上限，默认 `1048576`。
- `ERP_IDEMPOTENCY_MAX_REQUEST_BODY_BYTES`：进入幂等处理的请求体大小上限，默认 `1048576`。
- `ERP_ATTACHMENT_MAX_FILE_SIZE_BYTES`：附件上传大小上限，默认 `20971520`。
- `ERP_IMPORT_MAX_FILE_SIZE_BYTES`、`ERP_IMPORT_MAX_ROWS`、`ERP_IMPORT_MAX_CELL_LENGTH`、`ERP_IMPORT_COMMIT_BATCH_SIZE`：CSV 导入文件大小、行数、单元格长度和提交批量上限。
- `ERP_REPORT_MAX_EXPORT_ROWS`、`ERP_REPORT_EXPORT_BATCH_SIZE`：报表导出最大行数和分批读取大小。
- `ERP_PRINCIPAL_CACHE_INVALIDATION_MODE`：权限主体缓存失效模式，生产默认 `redis`，多实例部署不要改回 `local`。

也可以先用脚本生成一份本地 `.env.prod` 起点，再人工确认域名和密钥托管策略：

```powershell
.\scripts\new-prod-env.ps1 -CorsAllowedOrigins https://erp.example.com
```

脚本会为 `MYSQL_PASSWORD` 和 `ERP_DATASOURCE_PASSWORD` 写入同一个随机应用数据库密码，并拒绝覆盖已有 `.env.prod`，除非显式加 `-Force`。生成后的文件仍然不能提交到 Git。

## 本地构建

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" clean package
```

Maven Wrapper 固定 Maven 3.9.11 下载包 SHA-256；Maven `package` 阶段会运行当前最小自动化回归，并生成 CycloneDX SBOM：`target/classes/META-INF/sbom/application.cdx.json` 和 `target/bom.json`。

当前仓库已恢复发布检查脚本和少量关键自动化测试，覆盖迁移、登录冒烟、初始导入、收付款作废、应收应付入口、财务报表数据范围、会计期间、库存财务对账、销售成本结转、生产批量领料/完工和生产退料。这个测试集只能证明关键入口没有明显断裂，不能冒充完整 ERP 业务回归。上线前仍必须把 Docker Compose 配置、真实数据库迁移、登录接口、受保护接口和核心业务闭环放到预生产环境人工验收，别拿几个绿色测试当尚方宝剑。

## CI 发布门禁

仓库根目录的 `.github/workflows/pr-verify.yml` 在 PR 和 `main` / `master` 推送时启动 MySQL 8.4 service，并在 `backend` 工作目录用 `pwsh` 执行 `./scripts/release-check.ps1 -IncludeTestcontainers`，让普通 Spring Boot 测试有独立数据库，同时让带标签的 Testcontainers 测试继续用 Docker 自建隔离的 MySQL/Redis 容器。CI 因而会走同一套 PowerShell 脚本语法门禁、Testcontainers Maven 构建和发布产物校验。`release-check.ps1` 自身会在报告落盘后做自复验：`PASSED` 报告按正式发布规则校验，`FAILED` 报告只在失败现场用 `-AllowFailed` 校验结构和 failureReason，然后仍然保留原始失败。release-check 成功后，CI 会立即执行 `Verify release check report` 步骤，运行 `./scripts/verify-release-check-report.ps1 -ReportDirectory target` 自动复验刚生成的 `PASSED` 报告；复验不过就让 CI 继续红灯，别让坏报告混进发布证据。该 workflow 通过 `actions/upload-artifact` 留存 `target/erp-server-1.0.0.jar`、两份 SBOM、`target/release-check-report.json`、`target/release-check-report.md` 和 `target/surefire-reports`。上传步骤使用 `if: always()`，所以 release-check 失败时也会尽量上传 `status=FAILED` 的门禁报告和 Surefire 失败明细；jar 或 SBOM 没生成时不会让上传步骤再把现场覆盖成二次失败。门禁报告还会写入环境指纹，记录 PowerShell、Java、Maven Wrapper、Docker 和 GitHub Actions / runner 信息；遇到“本机过、CI 挂”时先对比这些字段，别上来就怀疑业务代码。以后查发布证据，先看 CI artifact，别只翻本机 `target`，那玩意儿一清理就没影。

下载 CI artifact 或本地重新生成报告后，先运行 `.\scripts\verify-release-check-report.ps1 -ReportDirectory target` 复验 `release-check-report.json` / `.md`。该脚本会校验 schema、报告状态、环境指纹、Markdown 和 artifacts 的长度 / SHA-256；正式发布默认只接受 `PASSED` 报告，并且默认拒绝 `allowDirtyWorktree=true` 的 `PASSED` 报告。正式发布复验不得追加 `-AllowDirtyWorktree`；只有本地非发布排查才允许追加 `-AllowDirtyWorktree` 复核这类报告。如果复验失败现场的 `FAILED` 报告，必须显式追加 `-AllowFailed`，别把失败报告当成发布通过证据往归档包里塞。

## 发布边界确认

正式构建镜像或打 tag 前，先在仓库根目录执行：

```powershell
git status --short --branch
git diff --stat
git ls-files --others --exclude-standard
.\scripts\release-check.ps1
```

`git status` 必须被人工逐项确认：已删除文件、未跟踪新文件和脚本删改都要明确属于本次发布范围。确认完成后创建发布 commit，再以该 commit 的 `release-check` 输出作为发布证据。当前脚本不替你判断工作树边界，别指望它像保姆一样把发布纪律也包了。

建议把发布边界确认结果写成一条发布记录，至少包含：

- 发布 commit / tag。
- `git diff --stat` 摘要。
- 已删除测试、脚本、workflow 是否属于本次发布范围。
- 未跟踪源码、迁移脚本、测试、文档是否已经纳入 commit。
- `release-check` 输出中的测试数量、jar 路径和 SBOM 路径。
- 预生产验收记录链接或文件路径。

如果当前候选仍依赖未提交文件，不要打 tag，也不要构建生产镜像。先整理 commit，再重新跑门禁。上线不是抽奖，不能靠“我本机刚才能跑”当发布证据。

## Docker Compose 启动

启动前人工确认 `.env.prod` 已存在、必填变量齐全、没有残留 `CHANGE_ME`，并且 Docker Compose 可用。

## V28 / V29 / V30 迁移前数据检查

`V28__tenant_columns_and_indexes.sql` 会把主数据和库存余额的唯一索引改成租户内唯一索引，`V29__system_role_tenant_columns.sql` 会把角色编码改成公司内唯一，`V30__system_dept_post_tenant_columns.sql` 会把部门编码和岗位编码改成公司内唯一。生产库上线前必须先检查旧数据和旧索引状态，否则迁移跑到一半失败，那就属于自己给自己挖坑。

迁移预检脚本已删除，生产库上线前需要用数据库客户端人工检查以下阻塞项：

- `md_product.product_code` 重复。
- `md_customer.customer_code` 重复。
- `md_supplier.supplier_code` 重复。
- `md_warehouse.warehouse_code` 重复。
- `inv_balance(warehouse_id, product_id)` 重复。
- `V28` / `V29` / `V30` 需要删除的旧唯一索引是否存在。

```powershell
docker compose --env-file .env.prod --profile core up -d --build
docker compose ps
```

`docker-compose.yml` 默认把 MySQL、Redis 和后端端口绑定到 `127.0.0.1`。生产环境建议由反向代理或内网服务发现暴露后端，不要直接把 MySQL/Redis 端口甩到公网。确实需要临时从宿主机访问数据库或 Redis 时，再显式使用 `--profile expose-infra`，并把 `MYSQL_BIND_HOST`、`REDIS_BIND_HOST` 的取值控制在可信网卡上。

Compose 服务默认读取 `.env.prod`。仓库还提供 `.dockerignore`，用于避免真实 `.env`、Git 元数据、本地 Maven 仓库和构建产物进入 Docker build context。

Docker 构建阶段使用仓库内 Maven Wrapper，并依赖 `.gitattributes` 固定 `mvnw` 为 LF 换行。Windows 上开发没问题，但别手贱把 `mvnw` 改成 CRLF；Linux 容器里脚本换行炸了，那报错看着可埋汰。

服务启动后，人工访问 `/actuator/health`、`/api/health`、`/api/auth/login` 和 `/api/system/profile`，确认健康检查、登录和受保护接口都能按预期工作。

## 可观测性检查

生产 profile 暴露 `/actuator/prometheus`，用于 Prometheus 以认证方式抓取 JVM、HTTP、Tomcat、Hikari 和 Redis 等基础指标。该端点不得匿名公开；生产环境应通过内网、网关认证或抓取侧凭证控制访问。

上线前还要用已登录账号访问 `/api/system/observability/business-health`，确认 readiness、导入失败、负库存和开放会计期间摘要能返回。该接口只返回聚合数量和状态，不替代具体业务验收。

业务健康摘要也会输出 Prometheus Gauge：`erp_business_health_overall_status` 表示整体状态，`erp_business_health_check_count` 按 `check` 标签输出 readiness、导入、库存和期间检查数量，`erp_business_health_check_status` 输出每个检查项状态。预生产验收要确认 `/actuator/prometheus` 响应包含 `erp_business_health_overall_status` 和 `erp_business_health_check_count`。

仓库提供最小告警规则模板 `docs/monitoring/prometheus-alert-rules.yml`，覆盖 P0/P1 readiness 未通过、最近导入失败、负库存、无开放会计期间和整体业务健康 WARN。该文件是基础模板，不负责部署 Prometheus、Alertmanager 或通知渠道；生产环境加载后还要按平台规范补充接收人和路由。

建议按下面顺序记录预生产启动证据：

```powershell
docker compose --env-file .env.prod --profile core up -d --build
docker compose ps
docker compose logs --tail 200 erp-server
```

记录时保留 HTTP 状态码、响应摘要、容器状态和后端启动日志中的 Flyway 版本。日志里不要贴真实密钥，谁把密码贴进验收记录，谁就得负责擦屁股。

有 Docker 的预生产机器上，优先用一键预生产验收脚本把基础验收、业务只读冒烟、采购到付款、销售到收款和生产制造补偿回滚串起来：

```powershell
.\scripts\preprod-full-acceptance.ps1 -EnvFile .env.prod -BaseUrl http://127.0.0.1:8080 -Username admin -Password "<预生产密码>" -WarehouseId 1 -MaterialWarehouseId 1 -FinishedWarehouseId 2 -BusinessDate "2026-05-18" -RollbackAfterSuccess -DisableCreatedMasterData
```

该脚本会在没有传 `-ReadinessRunId` 时自动调用 `preprod-acceptance.ps1 -CreateReadinessRun` 创建 readiness 运行单，再把解析出的 `Readiness run ID` 传给后续四个验收脚本，最后输出一份 `Go / No-Go` 汇总报告。基础验收成功后、业务脚本造数前，脚本会先做前置校验：确认 `WarehouseId`、`MaterialWarehouseId`、`FinishedWarehouseId` 对应仓库为 `ACTIVE`，`BusinessDate` 落在 `OPEN` 会计期间内，并检查登录返回的关键权限列表。正式验收建议使用 `-Username` / `-Password`，因为 `-AccessToken` 模式无法读取登录响应里的权限集合，脚本会把权限列表检查记录为 `SKIPPED`，再通过只读端点和后续写入脚本暴露权限问题。总报告会登记为 `PREPROD_FULL_ACCEPTANCE` 总判定项并上传 Markdown 附件，系统 readiness 里能直接看到一键预生产验收的最终结论。默认某一步失败就停止后续写业务数据；只有排障需要继续收集诊断证据时才追加 `-ContinueAfterFailure`，别把失败链路硬跑完当验收通过。

一键验收跑完后，上线审批前优先执行总门禁脚本：

```powershell
.\scripts\verify-preprod-acceptance-gate.ps1 -EvidenceDirectory <证据目录> -Username admin -Password "<预生产密码>"
```

总门禁会串联证据索引一致性校验、fallback 补传 `-ValidateOnly` 校验和系统 readiness 证据对账，写出 `preprod-acceptance-gate.md`，并同步写出 `preprod-acceptance-gate.json` 机器可读 sidecar，记录 verdict、readiness 状态、失败原因、步骤数和每个 gate step 的命令、退出码与失败信息；如果 readiness 登记失败，第二次保存会把 JSON 更新成最终 `BLOCKED`。脚本会把 Markdown 报告登记为系统 readiness 的 `PREPROD_APPROVAL_GATE` 验收项和附件。报告结论必须是 `READY_FOR_APPROVAL`，JSON 里的 `verdict` 也必须一致，且系统内 `PREPROD_APPROVAL_GATE=PASSED`，才能继续签 Go；如果是 `BLOCKED` 或登记失败，先按报告里的失败步骤处理，别一边红灯一边往生产推。

审批前总门禁报告可以独立复验：

```powershell
.\scripts\verify-preprod-acceptance-gate-report.ps1 -EvidenceDirectory <证据目录>
```

审批前总门禁报告复验会读取 `preprod-acceptance-gate.md` 和 `preprod-acceptance-gate.json`，校验 JSON schema、verdict、readiness 状态、stepCount、failedStepCount，以及 Markdown 中的 verdict 和 gate step 摘要是否与 JSON 一致，并写出 `preprod-acceptance-gate.verify-report.json` / `preprod-acceptance-gate.verify-report.md`。正式审批默认只接受 `READY_FOR_APPROVAL`；复核 `BLOCKED` 失败现场时才追加 `-AllowBlocked`，别把阻塞报告当成可发布证据。

总门禁通过后，再执行最终发布决策脚本，把系统 readiness 运行单从 `PENDING` 正式签成 `GO/PASSED`：

```powershell
.\scripts\decide-readiness-release.ps1 -EvidenceDirectory <证据目录> -Username admin -Password "<预生产密码>"
```

该脚本会从 `evidence-index.json` 读取 `ReadinessRunId` 和 `baseUrl`，也可以显式传 `-ReadinessRunId` / `-BaseUrl`。它会先拉取 `/api/system/readiness/runs/<运行单ID>`，确认所有 P0/P1 验收项都是 `PASSED`，并且 `PREPROD_APPROVAL_GATE=PASSED` 且带有 Markdown 附件，然后才调用 `/api/system/readiness/runs/<运行单ID>/decision` 写入 `GO/PASSED`。输出 `readiness-release-decision.md` 的 `Decision status` 必须是 `DECIDED_GO`；如果只想预演，追加 `-DryRun`，此时只能得到 `READY_TO_DECIDE`，不会写最终决策。这里别手欠绕过脚本直接点 Go，最后一哆嗦最容易把前面证据链白整。

最终发布决策完成后，导出发布证据归档包：

```powershell
.\scripts\export-release-evidence-bundle.ps1 -EvidenceDirectory <证据目录>
```

发布证据归档脚本会再次确认 `goNoGoVerdict=GO`、`preprod-acceptance-gate.md` 包含 `READY_FOR_APPROVAL`、`preprod-acceptance-gate.json` 的 `verdict=READY_FOR_APPROVAL`、`preprod-acceptance-gate.verify-report.json` 的 `status=PASSED`、`preprod-acceptance-gate.verify-report.md` 存在审批前总门禁报告复验结论且总门禁报告复验 Markdown Status 与 JSON 状态一致、`readiness-release-decision.md` 包含 `DECIDED_GO`，并要求所有 offline fallback manifest 都是 `uploadStatus=UPLOADED`。它还会从 `target` 或 `-ReleaseCheckReportDirectory` 指定目录读取 `release-check-report.json` 和 `release-check-report.md`，要求发布门禁报告状态为 `PASSED` 且不是 `allowDirtyWorktree=true` 的脏工作树通过报告；这类报告会被默认拒绝，发布证据归档和归档包复验都会拒绝把它作为正式发布证据。通过后生成 `release-evidence-bundle-<时间>.zip`、旁边的 `.sha256` 校验文件、`<zip>.summary.md` 发布证据包摘要、`<zip>.summary.json` 机器可读摘要、`release-evidence-artifacts-index.json` / `release-evidence-artifacts-index.md` 发布证据目录索引，以及压缩包内的 `release-evidence-bundle-manifest.json`，manifest 会列出每个证据文件的相对路径、大小和 SHA-256。发布证据包摘要会稳定列出 `bundleStatus`、zip 路径、zip SHA-256、sourceFiles 数、失败前置检查数、release-check 状态、发布候选 commit 和 `Release check allow dirty worktree`；发布证据目录索引会列出 zip、`.sha256`、摘要、归档包复验报告、`preprod-acceptance-gate.json`、`preprod-acceptance-gate.verify-report.json` 和 `preprod-acceptance-gate.verify-report.md` 的路径、大小、SHA-256 与状态，并在 summary 字段中保留 `Release check allow dirty worktree`。JSON 摘要使用同一批字段并带 `schemaVersion`，给 CI、审计平台或后续门禁脚本消费，别再让脚本去啃 Markdown。如果运行在 GitHub Actions 且存在 `GITHUB_STEP_SUMMARY`，脚本也会把 Markdown 摘要追加到步骤摘要里。脚本写完 `.sha256` 和两份摘要后会立即做归档包自复验，校验 zip、`.sha256`、manifest、包内文件长度 / SHA-256、额外文件清单、归档内 release-check 脏工作树策略，以及发布证据包摘要复验；自复验通过后会写发布证据目录索引，并立即运行发布证据目录索引复验，生成 `release-evidence-artifacts-index.verify-report.json` / `release-evidence-artifacts-index.verify-report.md`。只有归档失败现场时才显式加 `-AllowBlocked`，并且自复验也会用同一开关校验 `BLOCKED` 包。正式发布归档别加，别把半截验收包包装成胜利果实。

发布证据目录索引可以独立复验：

```powershell
.\scripts\verify-release-evidence-artifacts-index.ps1 -EvidenceDirectory <证据目录>
```

发布证据目录索引复验会读取 `release-evidence-artifacts-index.json` 和 `release-evidence-artifacts-index.md`，重新计算索引中每个 artifact 的文件名、UTC 修改时间、长度和 SHA-256，核对 `artifactCount`、`missingArtifactCount`、`verificationStatus`、必需 artifact role、重复 artifact role、artifact role 状态语义、artifact JSON 内容语义、summary JSON 关键字段语义、bundleSha256 sidecar 内容语义、summaryMarkdown 内容语义、verificationReportMarkdown 内容语义、preprodAcceptanceGateVerificationMarkdown 内容语义、顶层 evidenceDirectory / bundlePath 一致性、artifact 文件名和 UTC 修改时间一致性、Markdown summary 字段一致性、Markdown artifact 行一致性和 Markdown 中的关键条目，并写出 `release-evidence-artifacts-index.verify-report.json` / `release-evidence-artifacts-index.verify-report.md`。必需 artifact role 至少包含 zip、`.sha256`、摘要、归档包复验报告、`preprodAcceptanceGateJson`、`preprodAcceptanceGateVerificationJson` 和 `preprodAcceptanceGateVerificationMarkdown`，少一个都算索引不合格；重复 artifact role 检查会拒绝同一 role 出现多条，避免审批和 CI 各读一条；artifact role 状态语义要求 zip 状态匹配 `bundleStatus`、归档包复验报告状态匹配 `verificationStatus`、gate 复验 JSON / Markdown 都是 `PASSED`；artifact JSON 内容语义会解析摘要 JSON 的 `bundleStatus`、归档包复验报告 JSON 的 `status`、gate JSON 的 `verdict` 和 gate 复验 JSON 的 `status`；summary JSON 关键字段语义会核对 `bundleSha256` 与真实 bundle artifact 的 SHA-256 一致，并核对 `releaseCheck.status`、`releaseCheck.releaseCandidateCommit`、`releaseCheck.allowDirtyWorktree` 与索引顶层 releaseCheck 一致；bundleSha256 sidecar 内容语义会解析 `.sha256` 文件内容，要求其中记录的 hash 与 `bundle` artifact 的 SHA-256 一致；summaryMarkdown 内容语义会解析 `.summary.md` 摘要表，核对 Bundle status、Bundle SHA-256、Release check status、Release candidate commit 和 Release check allow dirty worktree 与索引及 `bundle` artifact 一致；verificationReportMarkdown 内容语义会解析 `<zip>.verify-report.md` 报告表，核对 Status 与复验报告 JSON 一致，并核对 Bundle path、SHA-256 file 与 `bundle` / `bundleSha256` artifact 路径等价；preprodAcceptanceGateVerificationMarkdown 内容语义会解析 `preprod-acceptance-gate.verify-report.md` 报告表，核对 Status 与 gate 复验 JSON 一致；顶层 evidenceDirectory / bundlePath 一致性要求 `evidenceDirectory` 等于索引所在目录，并要求 `bundlePath` 等于 `bundle` artifact 的 path；artifact 文件名和 UTC 修改时间一致性要求 `fileName` 等于 artifact path 的文件名，并要求 `lastWriteTimeUtc` 等于真实 sidecar 文件的 UTC 修改时间；Markdown summary 字段一致性要求顶部摘要表里的 bundle status、verification status、release-check 状态、发布候选 commit、release-check 脏工作树标志、artifact 数量、缺失数量和生成时间与 JSON 索引一致；Markdown artifact 行一致性要求每个 artifact 的 role、status、SHA-256、bytes 和 path 表格行与 JSON 索引一致。这个脚本不会改 zip、摘要或 bundle 复验报告，别整出“复验完把自己校验对象改了”的离谱循环。

归档包生成后立即做发布证据复验：

```powershell
.\scripts\verify-release-evidence-bundle.ps1 -BundlePath <归档包zip路径>
```

发布证据复验脚本会读取 zip 旁边的 `.sha256`，重新计算归档包 SHA-256，解包后解析 `release-evidence-bundle-manifest.json`，逐个校验证据文件长度和 SHA-256，并确认包内没有未登记在 manifest 的额外文件。它还会做关键证据语义复验，确认 `preprod-acceptance-gate.md` / `.json` 是 `READY_FOR_APPROVAL`、`preprod-acceptance-gate.verify-report.json` 是 `PASSED`、`preprod-acceptance-gate.verify-report.md` 的总门禁报告复验 Markdown Status 与 JSON 状态一致、`readiness-release-decision.md` 是 `DECIDED_GO`，并确认归档内 `release-check/release-check-report.json` 和 Markdown 报告存在、状态可发布且不是 `allowDirtyWorktree=true` 的脏工作树通过报告；随后做发布证据包摘要复验，读取 `<zip>.summary.json` 和 `<zip>.summary.md`，确认摘要里的 `bundleStatus`、zip SHA-256、sourceFiles 数、失败前置检查数、release-check 状态、发布候选 commit 和 `Release check allow dirty worktree` 与真实归档包一致。复验结束后会写 `<zip>.verify-report.json` 和 `<zip>.verify-report.md`，记录每个 check 的状态和失败原因；失败时也会先落报告再抛错，别让现场证据只剩控制台滚屏。导出脚本已经自动执行一次归档包自复验；这里的独立命令用于复核历史包、移动后的包或审计抽查。正式发布复验默认要求 `bundleStatus=READY` 且 manifest 里的前置检查全是 `PASSED`；只有复验失败现场归档时才显式追加 `-AllowBlocked`，此时 `BLOCKED` 包会跳过严格 READY 语义复验。

脚本启动后会先写入 `Parameter self-check` 参数自检 section，包含 `Sanitized command` 脱敏命令、认证方式、仓库参数、`BusinessDate` 格式和修复示例。密码和 token 会显示为 `<redacted>`，可以把这段脱敏命令贴进验收记录；真实密钥还往文档里贴，那就不是验收，是给自己挖坑。

汇总报告还会生成 `Failure triage index` 失败定位索引：`P0` 行优先处理真实失败步骤，`P1` 行通常是下游被跳过的步骤或诊断模式停止点；每行都会给出证据文件、失败原因和 `Recommended rerun command` 复跑命令。失败后先照这个索引定位，别从几份 Markdown 附件里徒手刨线索，费劲还容易看串。

证据目录会额外生成 `evidence-index.json` 证据索引，集中列出汇总报告、各子脚本报告、步骤状态、`ReadinessRunId`、`Go / No-Go` 结论、离线 fallback 待补传包和补传校验命令。上线审批时先跑证据索引一致性校验，再回看具体 Markdown；别拿资源管理器当审计系统，翻错一个文件就够你喝一壶。

```powershell
.\scripts\verify-preprod-evidence-index.ps1 -EvidenceDirectory <证据目录> -RequireUploadedFallback
```

该脚本会校验 `summary.md`、各子报告、`ReadinessRunId`、`Go / No-Go`、步骤状态、离线 fallback manifest 和 Markdown 原文是否齐全；`-RequireUploadedFallback` 会要求所有 fallback manifest 都已回写为 `uploadStatus=UPLOADED`。校验结束会在同一证据目录写出 `evidence-index.verify-report.json` 和 `evidence-index.verify-report.md`，成功、失败都会记录每个 check、失败数量和失败原因；失败时先落报告再抛错，别让上线审批只剩控制台滚屏。这一步过不了就别签 Go，索引都对不上还上线，那不叫发布，叫碰运气。

如果只想确认环境能不能开始造数，可以先追加 `-PreflightOnly` 跑诊断模式。该模式完成基础验收和前置校验后就停止，跳过业务只读冒烟、采购、销售和生产脚本，不写入业务数据；报告会记录仓库、会计期间、权限的排障建议，并把后续业务步骤标为 `SKIPPED`。注意它不是正式 `GO`，只是告诉你预生产环境有没有资格进入完整验收，别拿诊断报告冒充上线签字。

如果需要分段排查，也可以只用基础脚本自动收集预生产验收证据：

```powershell
.\scripts\preprod-acceptance.ps1 -EnvFile .env.prod -BaseUrl http://127.0.0.1:8080 -Username admin -Password "<预生产密码>" -CreateReadinessRun
```

该脚本会运行含 Testcontainers 的发布门禁、Docker / Compose 版本检查、Compose 启动、容器状态、`erp-server` 最近日志、`/actuator/health`、`/api/health`、登录和 `/api/system/profile` 冒烟，并输出 Markdown 格式的预生产验收证据。脚本会脱敏 `.env.prod` 中的密码、密钥、令牌类变量，并且不会把登录 token 写入报告。如果脚本挂着 readiness 运行单执行，还会同步回填默认验收项 `RELEASE_GATE`、`DOCKER_COMPOSE_HEALTH`、`AUTH_SMOKE` 和总项 `PREPROD_ACCEPTANCE`；用了 `-SkipReleaseCheck` 或 `-SkipComposeUp` 就会把对应项诚实记成 `BLOCKED`，别拿跳过版报告硬说自己已经过独立预生产。

追加 `-CreateReadinessRun` 后，预生产脚本会自动创建 readiness 运行单，生成默认验收项并记录迁移前 preflight 证据；输出中的 `Readiness run ID` 后续传给业务冒烟和三条业务闭环脚本的 `-ReadinessRunId <运行单ID>`。如果运行单已经由页面或 `POST /api/system/readiness/runs` 创建，也可以直接传 `-ReadinessRunId`。脚本会调用 readiness API 复用或创建对应验收项，上传 Markdown 原文附件，写入 Markdown 摘要证据并标记 `PASSED`、`FAILED` 或 `BLOCKED`；执行账号必须拥有 `system:readiness:manage` 和 `system:attachment:manage` 权限。登记失败会让脚本失败，别让证据只躺在文件夹里，最后上线会开成“凭感觉大会”。

如果 readiness API 网络、权限或附件上传短暂异常，脚本会在证据目录生成离线 fallback 待补传包：`<ITEM_CODE>-readiness-evidence-pending-upload.json` 和 `<ITEM_CODE>-readiness-evidence.md`。JSON 里保留 readiness 运行单、验收项、状态、失败原因、目标 API 和原始登记失败信息；Markdown 保留原文证据。故障恢复后先按这个待补传包补登记，再复核系统内 readiness 是否出现对应附件和证据，别因为 API 抖一下就把已经跑出来的证据整丢。

补传前先做离线校验，确认 JSON 能读、Markdown 原文存在：

```powershell
.\scripts\replay-readiness-evidence.ps1 -ManifestDirectory <证据目录> -ValidateOnly
```

校验通过后再补传，可以使用用户名密码，也可以用已有 token：

```powershell
.\scripts\replay-readiness-evidence.ps1 -ManifestDirectory <证据目录> -Username admin -Password "<预生产密码>"
```

补传成功后脚本会把 manifest 回写为 `uploadStatus=UPLOADED`，并记录 `uploadedAt`、`uploadedEvidenceId`、`uploadedAttachmentId` 和最终 readiness 状态。补传脚本只是把已经生成的证据补进系统，不会重新跑业务链路；这点挺关键，别一看脚本名就又把采购销售生产重新造一遍，嫌数据不够热闹啊。

补传完成后还要跑一次系统 readiness 证据对账，确认本地 manifest 记录的 `uploadedItemId`、`uploadedEvidenceId`、`uploadedAttachmentId` 和状态，真的能在系统运行单详情里查到：

```powershell
.\scripts\verify-readiness-evidence-upload.ps1 -ManifestDirectory <证据目录> -Username admin -Password "<预生产密码>"
```

这个脚本会读取已 `UPLOADED` 的 fallback manifest，调用 `/api/system/readiness/runs/<运行单ID>` 对账 item、evidence 和附件业务 ID。对账不过就别拿“补传成功”糊弄上线审批，系统里没证据，文件夹里唱得再响也白搭。

预生产环境启动并完成基础验收后，再运行业务只读冒烟脚本覆盖核心模块查询入口：

```powershell
.\scripts\business-smoke.ps1 -BaseUrl http://127.0.0.1:8080 -Username admin -Password "<预生产密码>"
```

该脚本会登录 `/api/auth/login`，用 Bearer Token 只读访问主数据、采购、销售、库存、财务、生产、流程、导入和报表入口，并生成 Markdown 格式的业务只读冒烟证据。脚本不会记录密码或 token 明文；默认有接口失败就报错，但会先写出证据文件。本地排查时可以追加 `-AllowFailures` 收集完整失败清单，正式验收别拿这个参数糊弄自己，失败就是失败。

只读冒烟通过后，再跑采购到付款补偿回滚验收脚本。该脚本会真实创建供应商、商品、采购订单、采购入库单、应付和付款单，并可通过付款作废、采购退货过账和停用本轮主数据做业务补偿回滚；这不是数据库级回滚，预生产执行前必须确认业务日期所在会计期间为 `OPEN`，并传入一个活跃仓库 ID。

```powershell
.\scripts\purchase-to-payment-acceptance.ps1 -BaseUrl http://127.0.0.1:8080 -Username admin -Password "<预生产密码>" -WarehouseId 1 -BusinessDate "2026-05-18" -RollbackAfterSuccess -DisableCreatedMasterData
```

正式验收建议先不加 `-RollbackAfterSuccess`，保留本轮单号给业务人员核对；核对完成后再单独执行补偿动作或在下一轮脚本中追加该参数。发现失败时脚本默认会尝试业务补偿回滚，除非显式指定 `-SkipRollbackOnFailure`。别把这玩意儿当清库神器，它只是把业务影响尽量冲回去。

采购到付款通过后，再跑销售到收款补偿回滚验收脚本。销售出库需要库存前置，所以脚本会先创建一笔采购入库作为铺货，再执行客户、销售订单、销售出库、应收和收款核销，最后可通过收款作废、销售退货、采购退货和停用本轮主数据做业务补偿回滚。

```powershell
.\scripts\sales-to-cash-acceptance.ps1 -BaseUrl http://127.0.0.1:8080 -Username admin -Password "<预生产密码>" -WarehouseId 1 -BusinessDate "2026-05-18" -RollbackAfterSuccess -DisableCreatedMasterData
```

该脚本同样要求业务日期所在会计期间为 `OPEN`，并传入活跃仓库 ID。销售脚本会真实写库存、应收、收款、凭证和退货补偿单据；正式验收如果要让业务人员核对单号，先别加 `-RollbackAfterSuccess`，别验完一秒钟就全冲回去了，业务同事看啥，看空气啊。

销售到收款通过后，再跑生产制造补偿回滚验收脚本。该脚本会先采购入库铺材料库存，再创建材料、成品、BOM 和生产工单，执行工单释放、生产领料、生产完工入库，并记录工单/BOM 明细证据。

```powershell
.\scripts\production-manufacturing-acceptance.ps1 -BaseUrl http://127.0.0.1:8080 -Username admin -Password "<预生产密码>" -MaterialWarehouseId 1 -FinishedWarehouseId 2 -BusinessDate "2026-05-18" -RollbackAfterSuccess -DisableCreatedMasterData
```

该脚本同样要求业务日期所在会计期间为 `OPEN`，并传入活跃材料仓库 ID 和成品仓库 ID。制造补偿回滚不是数据库回滚：脚本会调用生产反完工接口冲回成品库存和生产完工凭证，再执行生产退料、铺货采购退货和本轮主数据停用。默认 `CompletionQty` 等于 `PlannedQty`，用于覆盖完整完工、反完工、退料和采购退货闭环；如果反完工失败，别继续往后凑数，先查成品库存、期间状态和生产凭证。

`docker-compose.yml` 中 MySQL、Redis 和 `erp-server` 都配置了 `healthcheck`。后端容器通过 `curl http://127.0.0.1:8080/actuator/health` 判断应用是否真正可用，不只看进程是否还活着。`Dockerfile` 也带镜像级 `HEALTHCHECK` 和 `STOPSIGNAL SIGTERM`，入口使用 `exec java ...`，确保容器停止时 Java 进程能收到信号。

## 生产日志

生产环境使用 `src/main/resources/logback-spring.xml`：

- 控制台日志用于容器平台采集。
- 应用日志写入 `${LOG_PATH:-logs}/erp-server.log`，按日期和大小滚动。
- 错误日志单独写入 `${LOG_PATH:-logs}/erp-server-error.log`。
- 生产默认保留应用日志 30 天、错误日志 60 天，并限制总大小。

容器内默认日志目录为 `/app/logs`。如果生产环境不挂载日志目录，也必须确认容器平台已经采集标准输出，否则日志丢了，排障就跟闭眼拆炸弹没啥区别。

Compose 还为 MySQL、Redis、后端服务统一配置 Docker `json-file` 日志滚动，默认 `ERP_DOCKER_LOG_MAX_SIZE=50m`、`ERP_DOCKER_LOG_MAX_FILE=5`。生产平台如果有自己的日志采集和轮转策略，可以按平台规范调整，但别放任容器日志无限膨胀，那玩意儿撑爆磁盘时可不跟你打招呼。

## 上线检查清单

- `.\scripts\release-check.ps1` 成功，并生成 `target/erp-server-1.0.0.jar`、`target/classes/META-INF/sbom/application.cdx.json` 和 `target/bom.json`。
- 当前最小自动化测试通过；上线回归仍必须以预生产冒烟和人工业务验收记录兜底，别拿有限测试覆盖当业务正确性的完整证据。
- `.\scripts\new-prod-env.ps1` 可生成 `.env.prod`，并会拒绝不安全 CORS 配置。
- `docker-compose.yml` 中 MySQL、Redis、`erp-server` 的 `healthcheck` 均生效。
- Docker 镜像自带 `HEALTHCHECK`，入口使用 `exec java ...`，并能响应 `SIGTERM`。
- Docker 构建阶段使用仓库内 Maven Wrapper，且 `.gitattributes` 固定 `mvnw` / `*.sh` 为 LF。
- Docker Compose 日志滚动参数已确认适合当前磁盘容量。
- Flyway `baseline-on-migrate` 保持默认 `false`，除非本次发布明确是旧库接管。
- 生产日志目录或容器日志采集链路已确认可用。
- 数据库迁移阻塞项已人工检查，没有重复键或缺失索引问题。
- `.env.prod` 已替换全部 `CHANGE_ME` 值，且未提交到 Git。
- `spring.profiles.active` 使用 `prod`。
- `spring.profiles.active` 不能把 `prod` 和 `dev`、`test` 或 `local` 混用。
- `/actuator/health` 返回 `UP`。
- `/actuator/prometheus` 已通过认证访问，响应包含 Prometheus 文本指标。
- `/actuator/prometheus` 响应包含 `erp_business_health_overall_status` 和 `erp_business_health_check_count`。
- `/api/system/observability/business-health` 已通过认证访问，返回 readiness、导入、库存和期间检查项。
- 告警规则模板 `docs/monitoring/prometheus-alert-rules.yml` 已按生产监控平台加载或转换。
- `admin` 首次密码已通过 `ERP_BOOTSTRAP_ADMIN_PASSWORD` 初始化。
- 冒烟检查通过登录和受保护接口访问。
- 数据库和 Redis 已配置持久化卷、备份策略和监控告警。

## 回滚

应用镜像回滚优先使用上一版镜像重新部署。数据库迁移由 Flyway 正向管理；涉及破坏性 DDL 的版本必须在上线前准备数据库备份和人工回滚脚本。

上线前回滚口径必须写清楚：

- 上一版镜像 tag / digest。
- 当前数据库备份位置和恢复负责人。
- 本次 Flyway 迁移是否包含破坏性 DDL 或不可逆数据修正。
- 回滚触发条件，例如启动失败、登录失败、核心链路阻断、错误率异常或数据库迁移失败。
- 回滚后验证项：健康检查、登录、核心查询、日志错误和数据库版本。
