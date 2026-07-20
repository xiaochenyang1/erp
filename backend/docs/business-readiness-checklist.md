# ERP Server 业务验收清单

本文档用于上线前确认 ERP 后端核心业务链路已经可验收。当前仓库已恢复最小自动化回归和发布检查脚本，但这些检查只能证明关键接口、迁移和发布产物没有明显断裂；真实业务链路仍必须在预生产人工验收。

## 发布门禁

发布前先运行：

```powershell
.\scripts\release-check.ps1 -IncludeTestcontainers
```

该脚本会先执行 PowerShell 发布脚本语法门禁，自动发现 `scripts` 目录下的 `.ps1`，解析 `release-check.ps1`、预生产验收脚本、readiness 证据脚本和发布证据归档/复验脚本；随后执行 Maven `clean package`，并检查应用 jar、Spring Boot 内嵌 SBOM 和 `target/bom.json` 是否生成。通过后会生成发布门禁报告 `target/release-check-report.json` 和 `target/release-check-report.md`，记录发布候选 commit、脚本语法门禁、Maven 参数、脏工作树条目、jar/SBOM SHA-256 和人工验收清单路径，并由 `release-check.ps1` 立即做自复验，正式发布时一并归档。
脚本默认会拒绝脏工作树；只有本地非发布排查才允许追加 `-AllowDirtyWorktree`，预生产和生产发布不得使用该参数。有 Docker 的 CI / 预生产机器必须追加 `-IncludeTestcontainers`，把真实 MySQL/Redis 容器测试纳入发布门禁。
CI 里的 `.github/workflows/pr-verify.yml` 会用 `pwsh` 运行 `./scripts/release-check.ps1 -IncludeTestcontainers`；release-check 成功后会立即执行 `Verify release check report` 步骤，运行 `./scripts/verify-release-check-report.ps1 -ReportDirectory target` 自动复验刚生成的 `PASSED` 报告。随后 workflow 通过 `actions/upload-artifact` 上传 jar、SBOM、`release-check-report.json`、`release-check-report.md` 和 `target/surefire-reports`。上传步骤使用 `if: always()`，release-check 成功时报告状态为 `PASSED`，失败时也会尽量留下 `FAILED` 报告和测试失败明细。门禁报告会额外写入环境指纹，包含 PowerShell、Java、Maven Wrapper、Docker 和 GitHub Actions / runner 信息；如果 CI 与预生产结果不一致，先对比这些字段。上线审批优先引用 CI artifact，再结合预生产证据归档包；别让证据只躺在开发机 `target` 里，机器一换就开始现场考古。
拿到 CI artifact 或本地 `target` 报告后，运行 `.\scripts\verify-release-check-report.ps1 -ReportDirectory target` 复验 `release-check-report.json` / `.md`，确认 schema、报告状态、环境指纹、Markdown 和 artifacts 长度 / SHA-256 都一致。正式发布复验默认只接受 `PASSED`，并且默认拒绝 `allowDirtyWorktree=true` 的 `PASSED` 报告；正式发布复验不得追加 `-AllowDirtyWorktree`。只有本地非发布排查才允许追加 `-AllowDirtyWorktree` 复核这类报告。排查失败现场的 `FAILED` 报告才允许加 `-AllowFailed`。

## 预生产执行顺序

1. 冻结一个发布候选 Git commit，并确认 `git status --short --branch` 没有未解释的已修改、已删除或未跟踪文件。
2. 在有 Docker 的预生产机器上生成或准备 `.env.prod`，确认所有 `CHANGE_ME` 都已替换，且 `ERP_CORS_ALLOWED_ORIGINS` 只包含真实 Origin。
3. 用数据库客户端人工完成迁移前检查：主数据编码重复、库存余额重复、旧唯一索引状态和数据库备份。
4. 优先运行 `.\scripts\preprod-full-acceptance.ps1 -EnvFile .env.prod -BaseUrl http://127.0.0.1:8080 -Username admin -Password "<预生产密码>" -WarehouseId <活跃仓库ID> -MaterialWarehouseId <活跃材料仓库ID> -FinishedWarehouseId <活跃成品仓库ID> -BusinessDate "<开放期间日期>"`，一键生成预生产基础验收、业务只读冒烟、采购到付款、销售到收款和生产制造补偿回滚证据，输出 `Go / No-Go` 汇总，并把总报告登记为 `PREPROD_FULL_ACCEPTANCE` 总判定项。
5. 一键脚本会在基础验收成功后、业务脚本造数前执行前置校验：`WarehouseId`、`MaterialWarehouseId`、`FinishedWarehouseId` 必须指向 `ACTIVE` 仓库，`BusinessDate` 必须落在 `OPEN` 会计期间内，登录返回的权限集合必须包含 readiness 附件登记和业务链路写入所需权限。这个校验不过就别往后造数，省得把预生产数据搅成一锅粥。
6. 先看汇总报告里的 `Parameter self-check` 参数自检，确认 `Sanitized command` 脱敏命令、认证模式、仓库 ID、`BusinessDate` 和修复示例都符合本轮验收记录；别让参数填错这种破事拖到业务造数阶段才爆。
7. 如果汇总不是 `GO`，先看 `Failure triage index` 失败定位索引：优先处理 `P0` 失败行，再按证据文件和复跑命令复核对应脚本；`P1` 多数是下游跳过项，别把被跳过当成独立故障瞎修。
8. 优先运行 `.\scripts\verify-preprod-acceptance-gate.ps1 -EvidenceDirectory <证据目录> -Username admin -Password "<预生产密码>"` 做审批前总门禁，确认输出 `preprod-acceptance-gate.md` 且结论为 `READY_FOR_APPROVAL`，并确认 `preprod-acceptance-gate.json` 里的 `verdict`、失败原因和 gate steps 与 Markdown 一致；随后运行 `.\scripts\verify-preprod-acceptance-gate-report.ps1 -EvidenceDirectory <证据目录>` 做审批前总门禁报告复验，保留 `preprod-acceptance-gate.verify-report.json` / `.md`。再确认系统 readiness 里 `PREPROD_APPROVAL_GATE=PASSED` 且挂有同一份 Markdown 附件；再打开证据目录里的 `evidence-index.json` 证据索引，确认汇总报告、各子脚本报告、步骤状态、`ReadinessRunId`、离线 fallback 待补传包和补传命令都有记录。需要分段排障时，可单独运行 `.\scripts\verify-preprod-evidence-index.ps1 -EvidenceDirectory <证据目录>` 做证据索引一致性校验；脚本会写出 `evidence-index.verify-report.json` 和 `evidence-index.verify-report.md`，失败时也会先落报告再退出。如果 fallback 已完成补传，签字前追加 `-RequireUploadedFallback`，别靠人工记忆补验收结论。
9. 总门禁通过后，运行 `.\scripts\decide-readiness-release.ps1 -EvidenceDirectory <证据目录> -Username admin -Password "<预生产密码>"` 做最终发布决策，确认 `readiness-release-decision.md` 的 `Decision status` 为 `DECIDED_GO`。这个脚本会要求所有 P0/P1 readiness 项为 `PASSED`，并要求 `PREPROD_APPROVAL_GATE=PASSED` 且带 Markdown 附件，才会调用系统 decision API 写入 `GO/PASSED`；只想预演时追加 `-DryRun`，得到 `READY_TO_DECIDE` 不等于正式签 Go。
10. 最终发布决策完成后，运行 `.\scripts\export-release-evidence-bundle.ps1 -EvidenceDirectory <证据目录>` 做发布证据归档，保留生成的 zip、`.sha256`、`<zip>.summary.md` 发布证据包摘要、`<zip>.summary.json` 机器可读摘要，以及 `release-evidence-artifacts-index.json` / `release-evidence-artifacts-index.md` 发布证据目录索引。归档脚本会要求 `GO` 汇总、`READY_FOR_APPROVAL` 总门禁、`preprod-acceptance-gate.json` 的 `verdict=READY_FOR_APPROVAL`、`preprod-acceptance-gate.verify-report.json` 的 `status=PASSED`、`preprod-acceptance-gate.verify-report.md` 已生成且总门禁报告复验 Markdown Status 与 JSON 状态一致、`DECIDED_GO` 最终发布决策、所有 fallback `UPLOADED` 和发布门禁报告 `release-check-report.json` / `release-check-report.md` 都满足；默认从 `target` 读取门禁报告，需要时可传 `-ReleaseCheckReportDirectory <报告目录>`。发布门禁报告必须是 `PASSED` 且不是 `allowDirtyWorktree=true` 的脏工作树通过报告；这类报告会被默认拒绝，发布证据归档和归档包复验都会拒绝把它作为正式发布证据。压缩包内的 `release-evidence-bundle-manifest.json` 会列出每个证据文件的 SHA-256，摘要会列出 `bundleStatus`、zip SHA-256、sourceFiles 数、release-check 状态、发布候选 commit 和 `Release check allow dirty worktree`；发布证据目录索引会列出 zip、`.sha256`、摘要、归档包复验报告、`preprod-acceptance-gate.json`、`preprod-acceptance-gate.verify-report.json` 和 `preprod-acceptance-gate.verify-report.md` 的路径、大小、SHA-256 与状态，并在 summary 字段中保留 `Release check allow dirty worktree`。脚本写完 `.sha256` 和摘要后会立即做归档包自复验，包含归档内 release-check 脏工作树策略和发布证据包摘要复验；自复验通过后再写发布证据目录索引，并立即执行发布证据目录索引复验，生成 `release-evidence-artifacts-index.verify-report.json` / `release-evidence-artifacts-index.verify-report.md`。
11. 需要复核既有索引时，运行 `.\scripts\verify-release-evidence-artifacts-index.ps1 -EvidenceDirectory <证据目录>` 做发布证据目录索引复验，确认索引 JSON、Markdown、必需 artifact role、重复 artifact role、artifact role 状态语义、artifact JSON 内容语义、summary JSON 关键字段语义、bundleSha256 sidecar 内容语义、summaryMarkdown 内容语义、verificationReportMarkdown 内容语义、preprodAcceptanceGateVerificationMarkdown 内容语义、顶层 evidenceDirectory / bundlePath 一致性、artifact 文件名和 UTC 修改时间一致性、Markdown summary 字段一致性、Markdown artifact 行一致性、每个 artifact 的文件名、UTC 修改时间、长度、SHA-256 和 `Release check allow dirty worktree` 都与真实 sidecar 文件一致。
12. 归档包生成后，也可以运行 `.\scripts\verify-release-evidence-bundle.ps1 -BundlePath <归档包zip路径>` 做发布证据复验，独立复核既有发布证据包，确认 `.sha256` 匹配、manifest 可解析、每个证据文件长度和 SHA-256 都一致，且没有未登记在 manifest 的额外文件。复验还会做关键证据语义复验，要求审批前总门禁 `READY_FOR_APPROVAL`、总门禁报告复验 `PASSED`、总门禁报告复验 Markdown Status 与 JSON 状态一致、最终发布决策 `DECIDED_GO`、归档内 release-check 报告 `PASSED` 且不是 `allowDirtyWorktree=true` 的脏工作树通过报告；随后读取 `<zip>.summary.json` 和 `<zip>.summary.md` 做发布证据包摘要复验，确认摘要字段与真实归档包一致，并生成 `<zip>.verify-report.json` / `<zip>.verify-report.md` 留存复验结果。正式发布不要加 `-AllowBlocked`；只有复验失败现场归档时才用它。
13. 如果 readiness 登记、附件上传或权限临时失败，去证据目录找离线 fallback 待补传包：`<ITEM_CODE>-readiness-evidence-pending-upload.json` 和对应 Markdown 原文；恢复后先运行 `.\scripts\replay-readiness-evidence.ps1 -ManifestDirectory <证据目录> -ValidateOnly` 校验，再去掉 `-ValidateOnly` 并加认证参数完成补传。补传完成后必须运行 `.\scripts\verify-readiness-evidence-upload.ps1 -ManifestDirectory <证据目录> -Username admin -Password "<预生产密码>"` 做系统 readiness 证据对账，确认 item、evidence 和附件 ID 都能在运行单详情里查到；系统内 readiness 证据补齐后，才能继续 Go / No-Go 签字。
14. 如果只是想判断预生产环境有没有资格开始造数，先运行同一命令并追加 `-PreflightOnly` 进入诊断模式；它只跑基础验收和前置校验，跳过后续业务脚本，不写入业务数据，也不能作为正式 `GO` 结论。
14. 一键脚本可用 `-Username` / `-Password` 登录，也可改用 `-AccessToken`；脚本只记录 token 是否拿到，不记录 token 明文。正式验收优先用用户名密码，因为 `-AccessToken` 模式拿不到登录返回的权限集合，权限列表检查会记录为 `SKIPPED`，后续只读端点和写入脚本继续兜底。没有传 `-ReadinessRunId` 时，它会自动创建系统内 readiness 运行单并把 `Readiness run ID` 传给后续脚本。
15. 正式验收如果要保留业务单据给人工核对，先不要加 `-RollbackAfterSuccess`；核对完成后再补偿回滚。排障才使用 `-ContinueAfterFailure`，失败后继续造数不是验收，是给自己找活儿。
16. 如果一键脚本失败或需要分段复核，再手动运行 `.\scripts\preprod-acceptance.ps1 -CreateReadinessRun` 生成预生产验收证据，记录 `Readiness run ID`；如果没有使用一键脚本或 `-CreateReadinessRun`，则在系统内预生产验收记录中手工创建本次 readiness 运行单。后续脚本统一追加 `-ReadinessRunId <验收运行单ID>`，让证据摘要和 Markdown 原文附件进入 `/api/system/readiness`。
17. 手动运行 `.\scripts\business-smoke.ps1 -BaseUrl http://127.0.0.1:8080 -Username admin -Password "<预生产密码>" -ReadinessRunId <验收运行单ID>` 生成业务只读冒烟证据，覆盖主数据、采购、销售、库存、财务、生产、流程、导入和报表查询入口。
18. 手动运行 `.\scripts\purchase-to-payment-acceptance.ps1 -BaseUrl http://127.0.0.1:8080 -Username admin -Password "<预生产密码>" -WarehouseId <活跃仓库ID> -BusinessDate "<开放期间日期>" -ReadinessRunId <验收运行单ID>` 生成采购到付款补偿回滚验收证据；该脚本会真实造数和过账，失败时默认尝试付款作废与采购退货补偿。
19. 手动运行 `.\scripts\sales-to-cash-acceptance.ps1 -BaseUrl http://127.0.0.1:8080 -Username admin -Password "<预生产密码>" -WarehouseId <活跃仓库ID> -BusinessDate "<开放期间日期>" -ReadinessRunId <验收运行单ID>` 生成销售到收款补偿回滚验收证据；该脚本会先采购入库铺货，再执行销售出库、应收和收款核销，失败时默认尝试收款作废、销售退货和采购退货补偿。
20. 手动运行 `.\scripts\production-manufacturing-acceptance.ps1 -BaseUrl http://127.0.0.1:8080 -Username admin -Password "<预生产密码>" -MaterialWarehouseId <活跃材料仓库ID> -FinishedWarehouseId <活跃成品仓库ID> -BusinessDate "<开放期间日期>" -ReadinessRunId <验收运行单ID>` 生成生产制造补偿回滚验收证据；该脚本会采购铺材料库存，再执行 BOM、工单释放、领料、完工入库、生产反完工、生产退料和采购退货补偿。
20. 记录本次验收的 Git commit、构建时间、`.env.prod` 关键非密配置、数据库实例、Redis 实例、系统内 readiness 运行单 ID、一键汇总报告路径、证据索引路径、各子证据文件路径和执行人。
21. 按下方其余业务链路逐项执行；任何一项失败都先停下来定位，不要一边失败一边继续往后凑数。

本地没有 Docker 时，只能完成发布门禁、配置检查和脚本文档核验；真实启动、迁移和业务链路必须移到预生产机器执行。

## 当前候选状态

- 当前本轮改动已拆分为明确提交；预生产执行人必须先用 `git rev-parse --short HEAD` 记录最终 commit，并用 `git status --short --branch` 确认工作树干净。
- 当前本地默认 `mvn test` 已通过；测试数量、失败数以最新命令输出为准，不得沿用历史文档数字。完整发布门禁仍需在有 Docker 的最终发布环境重新执行 `.\scripts\release-check.ps1 -IncludeTestcontainers`，并记录测试数量、失败数、`target/erp-server-1.0.0.jar`、`target/classes/META-INF/sbom/application.cdx.json` 和 `target/bom.json`。
- 当前本机没有 Docker，不能完成真实 Docker Compose 启动、MySQL/Redis 依赖和业务链路验收；这些项必须在预生产机器补齐。

## 发布边界冻结

- 发布候选必须来自一个明确 Git commit，不能从当前脏工作树直接打包上线。
- 进入预生产前执行 `git status --short --branch`，逐项确认已修改、已删除和未跟踪文件都属于本次发布范围。
- 当前仓库保留的是最小自动化回归，不是历史全量测试集；如果大量旧测试删除是有意策略调整，需要在发布记录中写明批准人和替代验收方式。
- 创建 release tag 或镜像前，必须在最终发布 commit 上重新运行 `.\scripts\release-check.ps1 -IncludeTestcontainers`。
- 预生产失败后不得在同一 release tag 上“顺手改一把再试”，必须产生新的候选 commit 并重新记录验收。

## 预生产验收记录模板

每轮预生产验收至少记录这些字段，别靠一句“已验证”糊弄自己：

| 项目 | 记录 |
|---|---|
| Git commit / tag | |
| 构建时间 / 构建人 | |
| `release-check` 结果 | |
| Docker 镜像 tag / digest | |
| `.env.prod` 非密配置确认人 | |
| MySQL 实例 / 数据库名 | |
| Redis 实例 | |
| Flyway 最新版本 | |
| 数据库备份位置 | |
| 回滚方案确认人 | |
| 业务验收数据批次 | |
| 验收结论 / 阻塞项 | |

## Go / No-Go 判定

满足以下条件才允许进入生产发布：

- 发布候选来自明确 Git commit 或 tag，且本次发布范围已经人工确认。
- `.\scripts\release-check.ps1 -IncludeTestcontainers` 在该发布候选上通过，测试数量、构建产物和 SBOM 产物记录完整。
- 预生产 Docker Compose 启动成功，MySQL、Redis、后端服务健康检查通过。
- 指标出口 `/actuator/prometheus` 已通过认证访问，响应包含 Prometheus 文本指标。
- 指标出口 `/actuator/prometheus` 响应包含 `erp_business_health_overall_status` 和 `erp_business_health_check_count`。
- 业务健康摘要 `/api/system/observability/business-health` 已通过认证访问，返回 readiness、导入、库存和期间检查项。
- 告警规则模板 `docs/monitoring/prometheus-alert-rules.yml` 已加载到生产监控平台，或已在发布记录中写明由监控平台转换后的等价规则。
- 一键预生产验收的前置校验已通过，`WarehouseId`、`MaterialWarehouseId`、`FinishedWarehouseId` 均为 `ACTIVE`，`BusinessDate` 所属会计期间为 `OPEN`，关键权限校验没有失败项。
- 登录、受保护接口、采购到付款、销售到收款、财务账簿、期间锁账、库存财务对账、生产制造和期初导入至少各完成一组有效样例验收。
- 批次/效期商品必须覆盖采购入库、销售出库自动 FEFO/FIFO、库存调拨、生产领料/完工、期初导入和批次库存查询验收。
- 数据库备份、回滚方案、日志采集、监控告警和发布窗口已确认。
- 验收记录中没有未关闭的 P0 / P1 阻塞项。

出现以下任一情况直接 No-Go：

- 工作树仍有未解释的大量改动、删除或未跟踪文件。
- `release-check`、容器启动、Flyway 迁移、登录或健康检查失败。
- 核心业务链路出现库存、应收应付、凭证、期间锁账或租户隔离数据错误。
- 预生产验收只有口头结论，没有 commit、环境、接口、单号或日志证据。
- 数据库未备份、回滚方案未确认，或者真实生产密钥仍然使用占位符。

## 核心 API 冒烟

预生产基础验收完成后，先运行只读脚本收集核心 API 入口证据：

```powershell
.\scripts\business-smoke.ps1 -BaseUrl http://127.0.0.1:8080 -Username admin -Password "<预生产密码>"
```

- 登录接口能返回 Bearer Token、用户信息、权限集合和数据范围。
- 未登录访问受保护接口应返回 `401`。
- 已登录但缺少权限访问核心接口应返回 `403`。
- 主数据、采购、销售、库存、财务、报表接口至少有一条成功路径需要在预生产冒烟或人工验收中覆盖。
- 受保护接口至少覆盖 `/api/system/profile`；健康检查至少覆盖 `/actuator/health` 和 `/api/health`。
- 可观测性至少覆盖 `/actuator/prometheus` 和 `/api/system/observability/business-health`，并确认两者都不能匿名访问。
- 可观测性指标至少覆盖 `erp_business_health_overall_status`、`erp_business_health_check_count` 和 `erp_business_health_check_status`；告警模板至少覆盖 readiness、导入失败、负库存、开放会计期间和整体 WARN。

建议记录每个冒烟请求的接口、HTTP 状态码、响应中的业务编号或主键，以及失败时的后端日志片段。别只写“已验证”，这四个字跟没验差不多。

## 采购到付款链路

可先用脚本跑一轮真实写入验收，再由业务人员复核单据、库存、应付和凭证：

```powershell
.\scripts\purchase-to-payment-acceptance.ps1 -BaseUrl http://127.0.0.1:8080 -Username admin -Password "<预生产密码>" -WarehouseId <活跃仓库ID> -BusinessDate "<开放期间日期>"
```

如果本轮只想验证脚本闭环并尽量冲回业务影响，可以追加 `-RollbackAfterSuccess -DisableCreatedMasterData`；这属于业务补偿，不是数据库回滚。

- 可基于供应商、商品、仓库创建采购订单。
- 采购订单可提交并审批通过。
- 可基于采购订单创建入库单并过账。
- 入库过账后库存余额增加，库存流水方向为 `IN`。
- 入库过账后生成应付单、财务凭证和凭证分录。
- 可基于应付单创建付款单并核销应付金额。

## 销售到收款链路

可先用脚本跑一轮真实写入验收，再由业务人员复核销售订单、出库、库存、应收、收款和凭证：

```powershell
.\scripts\sales-to-cash-acceptance.ps1 -BaseUrl http://127.0.0.1:8080 -Username admin -Password "<预生产密码>" -WarehouseId <活跃仓库ID> -BusinessDate "<开放期间日期>"
```

如果本轮只想验证脚本闭环并尽量冲回业务影响，可以追加 `-RollbackAfterSuccess -DisableCreatedMasterData`；脚本会先用采购入库铺货，所以补偿时也会用采购退货把铺货冲掉。

- 可基于客户、商品、仓库和库存创建销售订单。
- 销售订单可提交并审批通过。
- 可基于销售订单创建出库单并过账。
- 出库过账后库存余额减少，库存流水方向为 `OUT`。
- 出库过账后生成应收单、财务凭证和凭证分录。
- 可基于应收单创建收款单并核销应收金额。

## 生产制造链路

可先用脚本跑一轮真实写入验收，再由业务人员复核采购铺货、BOM、生产工单、领料、完工入库、库存和凭证：

```powershell
.\scripts\production-manufacturing-acceptance.ps1 -BaseUrl http://127.0.0.1:8080 -Username admin -Password "<预生产密码>" -MaterialWarehouseId <活跃材料仓库ID> -FinishedWarehouseId <活跃成品仓库ID> -BusinessDate "<开放期间日期>"
```

如果本轮只想验证脚本闭环并尽量冲回业务影响，可以追加 `-RollbackAfterSuccess -DisableCreatedMasterData`；脚本会先用生产反完工冲回成品库存和生产完工凭证，再退回生产领料，并用采购退货冲回铺货材料。注意这是业务补偿，不是数据库回滚；业务人员仍要复核反完工单、库存流水、生产凭证和工单状态。别把“补偿回滚”念成“时光倒流”，那可真容易出幺蛾子。

- 可基于成品、材料和 BOM 创建生产工单。
- 生产工单可释放并锁定材料库存。
- 可按工单材料行执行生产领料并生成材料出库流水、生产领料凭证。
- 可执行生产完工入库并生成成品入库流水、生产完工凭证。
- 已完工成品可通过生产反完工冲回成品库存和生产完工凭证。
- 完工冲回后，已领材料可通过生产退料回到材料仓库。

## 财务账簿闭环

- 会计科目可查询、维护、启停，并能提供树形视图。
- 费用单可创建、作废、过账，过账后生成借贷平衡凭证分录。
- 业务凭证和费用凭证可查询凭证头与凭证分录。
- 总账和明细账可按科目、日期范围查询。

## 期间锁账与库存财务对账

- 可按年度生成 12 个会计期间，重复生成不产生重复期间。
- 采购入库、销售出库、退货、库存调整、库存调拨、收付款、费用、生产领料、生产完工和期初导入提交都必须校验业务日期所属期间为打开状态。
- 锁定或已结账期间的历史写入必须返回业务冲突，不能继续改库存流水、库存余额、应收应付、收付款或凭证事实。
- 期间锁定前必须通过月结检查，库存财务对账差异、凭证缺分录、凭证借贷不平、收付款核销不一致、应收应付结清金额非法和负库存余额都必须阻断锁定。
- 库存财务对账应按期间汇总 `inv_txn` 库存净额与 `fin_voucher_entry` 库存科目净额，并能列出金额不一致、仅库存、仅财务差异。
- 销售出库过账除生成收入/应收凭证外，还必须按实际出库成本生成 `6402` 借方、`1001` 贷方成本结转分录；销售退货必须生成相反方向的成本冲回分录。
- 关闭后的期间应视为最终历史账期，只能按授权流程反开锁定期间，不能直接写历史业务事实。

## 生产制造链路

- 可基于启用 BOM 创建生产工单，并按计划数量展开材料需求。
- 生产工单释放后预占材料仓库存，可用量不足时拒绝释放。
- 生产领料后释放对应预占、扣减材料库存，并写入生产领料库存流水。
- 生产完工后增加成品仓库存，工单记录完工数量和完工金额。
- 支持按明细分批领料、分批完工，且每批生产执行单独生成库存流水和财务凭证，避免复用工单材料行导致幂等键串账。
- 生产退料应回收入库、冲回生产成本、恢复对应材料预占，并允许后续再次领料。
- 生产领料应生成 `5001` 借方、`1001` 贷方生产成本分录；生产完工应生成 `1001` 借方、`5001` 贷方完工入库分录。
- 生产领料、生产退料和生产完工的业务日期应进入库存流水与财务凭证，锁定期间必须拒绝继续写入。
- 已完工、已领料、已取消的生产工单不能重复执行不合法状态动作。

## 租户与权限边界

- 当前公司只能查询本公司业务数据。
- 库存报表必须受仓库数据范围限制。
- 跨公司主数据、仓库、商品、客户、供应商引用必须被拒绝。
- 关键写接口必须校验明确权限，不能只依赖登录态。

## 上线前人工验收

- 在预生产使用真实 MySQL、Redis 和 Docker Compose 完成构建、启动和人工冒烟。
- 用真实 `.env.prod` 验证启动、登录、健康检查、日志、反向代理 Header 和 CORS。
- 使用一组真实业务样例执行采购入库付款、销售出库收款两条链路。
- 使用一组真实业务样例执行生产 BOM、生产工单、释放、分批领料、分批完工和退料再领料链路。
- 使用一组真实财务样例执行会计期间生成、库存财务对账、锁定、锁定期间写入拦截、结账和反开。
- 使用一组真实导入样例执行模板下载、错误预览、有效预览、提交、重复提交拒绝和晚期期初导入拒绝。
- 对照财务和库存结果，确认金额精度、税额、库存数量、单据状态、凭证分录和账簿查询符合业务预期。

## 验收失败处理

- 发布门禁失败：不进入预生产部署，先修构建、测试或产物问题。
- 容器启动失败：保留容器日志、`.env.prod` 非密配置和 Flyway 输出，确认不是占位符、端口、数据库权限或 Redis 密码问题。
- 业务链路失败：记录失败接口、请求体、响应体、相关单号和数据库关键行；修复后从干净数据重新执行该链路。
- 数据口径不一致：优先确认业务规则和测试样例，不要直接改库把结果“调平”。这活儿要是靠手搓 SQL 找平，那 ERP 就开始玄学记账了。
