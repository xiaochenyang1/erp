# ERP Server 生产就绪审计状态

> 更新日期：2026-05-28
> 结论：当前改动已拆分为明确提交，默认自动化测试已恢复通过；生产发布仍必须补齐含 Testcontainers 的发布门禁、真实 Docker/MySQL/Redis 冒烟和人工业务验收。

## 已完成的上线加固

- 添加生产部署材料：`Dockerfile`、`docker-compose.yml`、`.env.prod.example`、`docs/production-deployment.md`。
- 添加 `.dockerignore`，Docker build context 排除真实 `.env`、Git 元数据、本地 Maven 仓库和构建产物。
- Docker 镜像使用非 root 用户、`STOPSIGNAL SIGTERM`、镜像级 `HEALTHCHECK`，入口通过 `exec java ...` 接收停机信号。
- Docker 构建阶段使用仓库内 Maven Wrapper，不再走独立的 `maven:*` 镜像和裸 `mvn` 命令，构建工具版本与发布门禁保持一致。
- Docker Compose 为 MySQL、Redis、后端服务统一配置 `json-file` 日志滚动，避免容器日志无限长胖。
- 添加 `.gitattributes` 固定 `mvnw` / `*.sh` 为 LF、Windows 脚本为 CRLF，避免 Windows checkout 后 Linux 容器执行 wrapper 脚本翻车。
- 发布校验脚本已恢复为 `scripts/release-check.ps1`；如启用 GitHub Actions，必须确认工作流文件已纳入发布范围并保留 CI 结果。
- 添加 `application-prod.yml`，生产环境使用显式环境变量加载数据库、Redis、JWT、CORS 等关键配置。
- 生产启动阶段强制校验数据库、Redis、JWT、CORS、Swagger 开关等配置，禁止 `CHANGE_ME` 占位符、通配 CORS，以及 `prod` 混用 `dev` / `test` / `local` profile。
- 生产 CORS 只允许标准 Origin，不允许路径、query、fragment、userinfo 等错误写法。
- 首次生产启动通过 `ERP_BOOTSTRAP_ADMIN_PASSWORD` 重置 admin 密码，并禁止使用占位符。
- 添加登录限流、生产日志配置、健康检查依赖检查、多租户 schema/索引迁移。
- 已恢复最小 `src/test` 自动化回归，覆盖迁移、登录冒烟、初始导入、收付款作废、应收应付入口、财务报表数据范围、会计期间、库存财务对账、销售成本结转和期间写入守卫；它是发布门禁，不是完整业务验收。
- 升级 Spring Boot 到 `3.5.14`，同步升级 springdoc 到 `2.8.17`，避免继续停在已过维护期的 `3.3.5`。
- 添加 Maven Wrapper，固定 Maven 3.9.11 下载包 SHA-256，发布检查优先使用 `mvnw.cmd`，减少本机 Maven 版本漂移和供应链下载风险。
- 添加 Maven Enforcer 校验 Java/Maven 版本，并在 package 阶段生成 CycloneDX SBOM；发布门禁同时检查 Spring Boot 内嵌 SBOM 和 `target/bom.json`。
- 生产 profile 开启 forwarded headers 和 HTTP compression，适配 Nginx/网关反向代理部署。
- 生产 Flyway `baseline-on-migrate` 默认关闭，只能通过 `ERP_FLYWAY_BASELINE_ON_MIGRATE=true` 显式启用旧库接管模式。
- GitHub Actions PR 验证工作流已恢复；Compose 配置仍需要在预生产环境人工校验。
- 新增会计期间锁账能力：年度期间生成、锁定/结账/反开、月结检查、库存财务对账、锁定期间写入拦截，以及销售出库/退货库存成本凭证分录。
- 生产制造已接入期间锁账和财务凭证：生产领料写 `5001/1001`，生产完工写 `1001/5001`，生产库存流水业务日期可参与库存财务对账。

## 当前本地验证

本地已执行：

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" test
```

最近一次结果：

- `BUILD SUCCESS`
- 默认 `mvn test` 已通过；测试数量、失败数以最新命令输出为准，发布记录不得沿用本文档中的历史数字。
- 默认测试集排除 `testcontainers` 分组；发布脚本已支持 `-IncludeTestcontainers`，有 Docker 的 CI / 预生产机器必须用该参数补齐真实 MySQL/Redis 容器测试。
- 完整发布门禁 `.\scripts\release-check.ps1 -IncludeTestcontainers` 仍需在有 Docker 的最终发布环境重新执行，并记录测试数量、失败数、jar 与 SBOM 产物；脚本现在会默认拒绝脏工作树，本地非发布检查才允许显式使用 `-AllowDirtyWorktree`。

## 当前工作树边界

- 本轮改动已按发布门禁、生产配置、报表导出、日志租户隔离、测试体系优化和文档收口拆分为明确提交；进入预生产前仍需以最终 `HEAD` 作为候选并确认工作树干净。
- 当前 `src/test` 自动化基线只代表最小回归范围，不代表完整业务验收；测试文件数和测试方法数以最新 `.\mvnw.cmd -B test` 或 `.\scripts\release-check.ps1 -IncludeTestcontainers` 输出为准。
- 发布候选必须在最终 `HEAD` 上重新运行 `.\scripts\release-check.ps1 -IncludeTestcontainers` 并通过；进入预生产前仍应用 `git rev-parse --short HEAD` 记录最终 commit。
- CI 工作流如纳入发布范围，应保留对应运行结果；没有 CI 证据时，预生产执行人需要人工完成 Docker Compose、真实数据库迁移和业务验收记录。

## 上线 Go / No-Go 摘要

当前状态：可进入预生产验证准备；生产发布仍是 No-Go。默认测试已经恢复通过，但含 Testcontainers 的完整发布门禁尚未在有 Docker 的最终发布环境重跑，真实 Docker/MySQL/Redis 预生产证据也还没闭环。

进入 Go 至少需要补齐这些证据：

- 发布候选 commit / tag 明确，且工作树中已修改、已删除、未跟踪文件都被纳入或排除并有记录。当前本轮代码改动已拆分提交，进入预生产前仍需记录最终 `HEAD`。
- 最终发布 commit 上重新执行 `.\scripts\release-check.ps1 -IncludeTestcontainers`，保留测试通过数量、jar 和 SBOM 产物路径。当前本地已完成默认 `mvn test` 验证，但本机没有 Docker，仍需在有 Docker 的最终发布环境重跑完整发布门禁。
- 有 Docker 的预生产环境完成 `docker compose --env-file .env.prod --profile core up -d --build`，并记录服务健康状态。
- 预生产人工验收优先执行 `.\scripts\preprod-full-acceptance.ps1` 一键预生产验收；脚本会自动创建或复用 readiness 运行单，把 `Readiness run ID` 传给基础验收、业务只读冒烟、采购到付款、销售到收款和生产制造补偿回滚脚本，让证据摘要和 Markdown 原文附件进入 `/api/system/readiness`，并把总报告登记为 `PREPROD_FULL_ACCEPTANCE` 总判定项。脚本会先输出 `Parameter self-check` 参数自检和 `Sanitized command` 脱敏命令，用来核对认证、仓库、业务日期和修复示例。基础验收成功后、业务写入脚本启动前必须通过前置校验：`WarehouseId`、`MaterialWarehouseId`、`FinishedWarehouseId` 对应仓库为 `ACTIVE`，`BusinessDate` 落在 `OPEN` 会计期间内，执行账号具备 readiness 登记和业务链路权限。`-PreflightOnly` 诊断模式只跑基础验收和前置校验，不写入业务数据，后续业务脚本会记录为 `SKIPPED`，不能作为正式 `GO`。`-AccessToken` 模式下权限列表不可见，只能记录为 `SKIPPED` 并靠后续接口兜底，所以正式验收应优先使用用户名密码。该总判定覆盖 `docs/business-readiness-checklist.md` 的 Go / No-Go 判定项；分步脚本保留给失败排障和人工复核，复跑时必须追加 `-ReadinessRunId <验收运行单ID>`。
- 一键预生产验收总报告必须包含 `Failure triage index` 失败定位索引，按 `P0` 失败项和 `P1` 跳过项列出证据文件、失败原因和复跑命令；生产发布复核时先按该索引处理阻塞项，再回看对应子脚本附件。
- 一键预生产验收证据目录必须包含 `evidence-index.json` 证据索引，列出汇总报告、子脚本报告、步骤状态、`ReadinessRunId`、`Go / No-Go` 结论、离线 fallback 包和补传命令；发布复核应先执行 `.\scripts\verify-preprod-evidence-index.ps1 -EvidenceDirectory <证据目录> -RequireUploadedFallback` 做证据索引一致性校验，再抽查 Markdown 原文。该校验会在证据目录保留 `evidence-index.verify-report.json` 和 `evidence-index.verify-report.md`，成功、失败都记录 check 清单和失败原因，别把证据索引复核降级成口头确认。
- 发布复核优先执行 `.\scripts\verify-preprod-acceptance-gate.ps1 -EvidenceDirectory <证据目录> -Username admin -Password "<预生产密码>"` 审批前总门禁，统一串联证据索引一致性、fallback 补传校验和系统 readiness 证据对账；`preprod-acceptance-gate.md` 必须输出 `READY_FOR_APPROVAL`，`preprod-acceptance-gate.json` 必须记录同一 verdict、readiness 状态、失败原因和 gate steps，并且必须再执行 `.\scripts\verify-preprod-acceptance-gate-report.ps1 -EvidenceDirectory <证据目录>` 做审批前总门禁报告复验，保留 `preprod-acceptance-gate.verify-report.json` / `.md`；系统 readiness 必须出现 `PREPROD_APPROVAL_GATE=PASSED` 和同一份 Markdown 附件，否则本轮生产发布保持 No-Go。
- 审批前总门禁通过后，必须执行 `.\scripts\decide-readiness-release.ps1 -EvidenceDirectory <证据目录> -Username admin -Password "<预生产密码>"` 最终发布决策脚本；脚本会确认所有 P0/P1 readiness 项和 `PREPROD_APPROVAL_GATE` 均为 `PASSED` 后调用 decision API 写入 `GO/PASSED`，`readiness-release-decision.md` 必须输出 `DECIDED_GO`。
- 最终发布决策完成后，必须执行 `.\scripts\export-release-evidence-bundle.ps1 -EvidenceDirectory <证据目录>` 发布证据归档；归档包必须包含 `release-evidence-bundle-manifest.json`、`release-check/release-check-report.json`、`release-check/release-check-report.md`、`preprod-acceptance-gate.json`、`preprod-acceptance-gate.verify-report.json` 和 `preprod-acceptance-gate.verify-report.md`，并保留 zip 旁边的 SHA-256 校验文件、`<zip>.summary.md` 发布证据包摘要、`<zip>.summary.json` 机器可读摘要，以及 `release-evidence-artifacts-index.json` / `release-evidence-artifacts-index.md` 发布证据目录索引，作为发布审计交付物。导出脚本会在归档前确认总门禁报告复验 Markdown Status 与 JSON 状态一致。导出脚本写完 `.sha256` 和摘要后会立即做归档包自复验，包含发布证据包摘要复验；自复验通过后发布证据目录索引会统一列出 zip、`.sha256`、摘要、归档包复验报告、`preprod-acceptance-gate.json`、`preprod-acceptance-gate.verify-report.json` 和 `preprod-acceptance-gate.verify-report.md` 的路径、大小、SHA-256 与状态，并执行发布证据目录索引复验，保留 `release-evidence-artifacts-index.verify-report.json` / `release-evidence-artifacts-index.verify-report.md`。GitHub Actions 中还会追加到 `GITHUB_STEP_SUMMARY`。
- 发布证据目录索引需要独立抽查时，执行 `.\scripts\verify-release-evidence-artifacts-index.ps1 -EvidenceDirectory <证据目录>` 做发布证据目录索引复验，确认索引 JSON、Markdown、artifact 数量、缺失数量、必需 artifact role、重复 artifact role、artifact role 状态语义、artifact JSON 内容语义、summary JSON 关键字段语义、bundleSha256 sidecar 内容语义、summaryMarkdown 内容语义、verificationReportMarkdown 内容语义、preprodAcceptanceGateVerificationMarkdown 内容语义、顶层 evidenceDirectory / bundlePath 一致性、artifact 文件名和 UTC 修改时间一致性、Markdown summary 字段一致性、Markdown artifact 行一致性，以及每个 sidecar 的文件名、UTC 修改时间和 SHA-256 都与真实文件一致。
- 发布证据归档完成后，也可以执行 `.\scripts\verify-release-evidence-bundle.ps1 -BundlePath <归档包zip路径>` 做发布证据复验，对已有归档包做独立复核；复验必须确认 `.sha256`、manifest、包内证据文件 SHA-256、额外文件检查、关键证据语义复验和发布证据包摘要复验全部通过，其中审批前总门禁必须是 `READY_FOR_APPROVAL`，总门禁报告复验和归档内 release-check 报告必须是 `PASSED`，归档内 release-check 报告不得是 `allowDirtyWorktree=true` 的脏工作树通过报告，总门禁报告复验 Markdown Status 必须与 JSON 状态一致，最终发布决策必须是 `DECIDED_GO`，并保留 `<zip>.verify-report.json` / `<zip>.verify-report.md`，正式发布不得使用 `-AllowBlocked`。
- readiness 登记或附件上传失败时，各验收脚本必须在证据目录保留离线 fallback 待补传包，包含 `<ITEM_CODE>-readiness-evidence-pending-upload.json` 和 Markdown 原文；发布复核必须执行 `.\scripts\replay-readiness-evidence.ps1 -ManifestDirectory <证据目录> -ValidateOnly` 校验，并在恢复网络/权限后用同脚本完成补传。补传后还必须执行 `.\scripts\verify-readiness-evidence-upload.ps1 -ManifestDirectory <证据目录> -Username admin -Password "<预生产密码>"` 做系统 readiness 证据对账，确认 manifest 已标记 `UPLOADED` 且系统内 readiness 出现对应 item、evidence、附件和最终状态，不能拿“本地文件夹里有”冒充系统证据。
- 数据库备份、迁移窗口、回滚负责人、上一版镜像和日志/监控入口已经确认。

当前可以说“代码侧已收口、默认测试已恢复”，但还不能说“可以生产发版”。测试绿了只是说明车能点火，不代表刹车、方向盘和路都没问题。

## 计划文档状态

- `2026-05-22-inventory-finance-reconciliation-period-lock.md`、`2026-05-22-production-finance-period-closure.md`、`2026-05-22-production-batch-execution-return.md` 对应实现已落地，并已纳入当前最小回归和发布门禁。
- `2026-05-18-initial-data-import.md` 的后端实现已落地；其中模板下载、预览、提交、重复提交和失败状态由自动化覆盖，真实 CSV 全类型人工验证仍属于预生产验收项。
- `2026-05-14-inventory-reservation-ops.md`、`2026-05-18-finance-ar-ap-entrypoints.md`、`2026-05-18-finance-settlement-cancel.md` 已有实现文件和当前最小测试覆盖，但原计划内的细粒度测试文件大量被删除；这些计划现在只能作为实现记录，不能当作仍待逐项执行的 backlog。
- 旧计划中残留的“Commit”步骤不代表当前要求自动提交；本轮发布前应统一整理一次发布 commit，避免几十个历史计划各自要求的 commit 粒度把仓库切碎。

## 仍需上线前确认

- 本机没有 Docker，`docker --version` 和 `docker compose version` 均无法执行；`.\scripts\release-check.ps1 -IncludeTestcontainers`、镜像构建和 `docker-compose` 启动必须移到有 Docker 的预生产环境执行。
- 需要用真实 MySQL/Redis 跑一次预生产人工冒烟，确认 Flyway、连接池、健康检查、登录限流都正常。
- 当前自动化测试覆盖有限，上线前必须补足人工业务验收记录，不能拿最小测试集当完整业务回归证据。
- 批次/效期商品必须覆盖采购入库、销售出库自动 FEFO/FIFO、库存调拨、生产领料/完工、期初导入和批次库存查询验收。
- `.env.prod.example` 中所有 `CHANGE_ME` 必须替换为高强度真实密钥，且不得提交真实 `.env.prod`。
- 生产数据库发布前要完成备份、迁移预检、回滚预案和只读窗口确认。
- Spring Boot 4.x 属于大版本升级，应单独开升级分支验证，本轮只做 3.x 补丁线升级。

## 建议发布顺序

1. 在预生产机器拉取当前发布候选 commit，并执行 `git status --short --branch` 确认工作树干净。
2. 复制 `.env.prod.example` 为 `.env.prod` 并替换所有占位符，真实 `.env.prod` 不得提交。
3. 使用数据库客户端人工检查数据库迁移前置条件，并完成生产库备份。
4. 执行 `.\scripts\preprod-full-acceptance.ps1 -EnvFile .env.prod -BaseUrl http://127.0.0.1:8080 -Username admin -Password "<预生产密码>" -WarehouseId <活跃仓库ID> -MaterialWarehouseId <活跃材料仓库ID> -FinishedWarehouseId <活跃成品仓库ID> -BusinessDate "<开放期间日期>"`，用脚本统一记录 `release-check -IncludeTestcontainers`、Docker Compose、健康检查、登录冒烟、容器日志、业务只读冒烟、采购到付款、销售到收款和生产制造补偿回滚证据。
5. 确认一键预生产验收汇总报告为 `GO`，系统内 readiness 运行单的候选 commit、默认验收项、前置校验 / preflight 证据、各子脚本 Markdown 附件和 `PREPROD_FULL_ACCEPTANCE` 总判定项已经生成；审批前总门禁通过后还必须生成 `PREPROD_APPROVAL_GATE` 总门禁项，并由最终发布决策脚本把 readiness 运行单签成 `GO/PASSED`，随后导出发布证据归档包。前置校验必须记录 `WarehouseId`、`BusinessDate`、`OPEN` 会计期间和关键权限结论。若使用 `-PreflightOnly` 诊断模式，只能证明环境具备进入完整验收的基础条件，不能进入生产发布。
6. 如果一键脚本输出 `NO-GO`，先按汇总报告中的失败步骤分段复跑 `.\scripts\preprod-acceptance.ps1`、`.\scripts\business-smoke.ps1`、`.\scripts\purchase-to-payment-acceptance.ps1`、`.\scripts\sales-to-cash-acceptance.ps1` 或 `.\scripts\production-manufacturing-acceptance.ps1`，不要直接进入生产发布。
7. 按 `docs/business-readiness-checklist.md` 完成库存、财务、生产、期初导入等其余业务验收，并把业务单号和阻塞项补进系统内 readiness 验收记录。
8. 预生产 Go 后再打 tag、构建镜像并按发布文档部署生产。
