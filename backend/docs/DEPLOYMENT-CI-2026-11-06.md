# 完整部署验证 CI（2026-11-06）

新增 [Deployment Verify](../../.github/workflows/deployment-verify.yml)，将现有完整部署演练接入 GitHub Actions。它在独立 runner 上构建并验证候选，使用实际上传后重新下载的部署包启动 MySQL 8.4、Redis、生产配置后端和 Nginx，执行浏览器与采销业务样例，最后复验报告并清理资源。

## 触发和执行顺序

- PR 修改后端、前端、此工作流或根目录 Git 配置时触发；`main` / `master` 推送同样触发。工作流进入默认分支后也可从 Actions 手动执行。
- PR 使用明确的 head SHA，推送和手动执行使用对应提交。令牌仅有 `contents: read`，不使用业务环境密钥。
- 后端调用既有 `release-check.ps1 -IncludeTestcontainers` 和报告复验器，普通集成测试连接 runner 的独立 MySQL 8.4 service。
- 前端执行类型、lint、单元测试、契约、制品测试、生产依赖审计，再从干净检出重新构建发布制品。
- [deployment-ci.mjs](../scripts/deployment-ci.mjs) 的 `prepare` 检查后端通过报告、Testcontainers 标记、源码版本、JAR/SBOM 摘要和前端制品，生成现有部署打包器需要的候选索引。
- 原有 `deployment-bundle.mjs` 生成部署包；工作流上传归档后按 artifact ID 下载，先核对独立保存的归档 SHA-256，再复验部署清单，使用下载包执行演练。

工作流单独完成其构建前置检查，避免拿其他工作流的旧制品或不同提交组合成候选。这会增加一次后端和前端检查的运行成本；Backend Verify、Frontend Verify、UI Smoke 和 Monitoring Verify 仍保留各自职责。

## 演练和通过条件

部署运行沿用 [deployment-smoke.mjs](../scripts/deployment-smoke.mjs) 的 8 个检查项，覆盖 Compose 隔离、实际制品字节、静态缓存和深链接、API 认证及错误边界、2 MB 附件、浏览器登录、后端更换 IP 后的代理恢复与附件保留，以及采销业务样例。

采销子报告包含 11 项检查：采购 10 件 × 10 元，分次付款 40 + 60 元；销售 4 件 × 15 元，分次收款 20 + 40 元；剩余库存 6 件 / 60 元，应收应付结清，并验证录单账号的权限。

`verify` 要求父报告和采销子报告均完整通过，应用、打包工具、运行工具及子报告版本与 CI 提交一致，部署清单摘要一致，浏览器进程关闭且资源清理成功。缺项、重复项、版本混用、未完成步骤或清理失败均不接受。

## 超时、清理与归档

- 演练在 20 分钟后收到 TERM，额外保留 120 秒供退出；对应步骤上限 23 分钟，整个作业上限 50 分钟。
- 部署报告原子写入，避免中断时留下半份 JSON。收集容器日志失败仍会执行 Compose 清理。
- 演练步骤结束后，`always()` 清理步骤按报告中的随机项目名检查并清除其容器、IP 占位容器、卷、网络和镜像标签。一个资源删除失败不会跳过其他资源；清理结果单独保存，失败会使 CI 失败。
- 清理使用精确项目标签和名称，不执行全局 Docker prune。原演练失败不会因补充清理成功而被改成通过。
- 部署包不包含运行时 `.env`。诊断上传列出具体报告和日志路径，不上传临时账号密码、私有配置、Chrome 配置目录或数据库卷。

每次运行保留两类制品，期限 90 天：

| 制品名 | 内容 |
|---|---|
| `erp-deployment-bundle-<run_id>-<attempt>` | 部署归档、归档摘要、部署清单摘要、候选说明、源码和制品索引 |
| `erp-deployment-evidence-<run_id>-<attempt>` | 后端发布报告、Surefire 结果、前端检查日志、父部署报告、采销子报告、脱敏配置/日志、浏览器截图、清理和复验结果 |

部署包在演练前上传，因此存在可下载的包不代表部署通过。验收时同时核对工作流结论和 `verification.json`、`cleanup-report.json`、`runtime/report.json`、`runtime/business-trial/report.json`。

## 本次验证

本地新增及相关脚本回归 45 项通过，覆盖制品改写、报告缺项、版本错配、部分清理失败及其他项目资源保留；测试使用临时 Git 仓库和模拟 Docker 响应。Actionlint 1.7.7 校验、13 段 Bash / PowerShell 工作流命令的语法检查及 `git diff --check` 均通过。

当前本机没有 Docker；完整容器演练需由此次提交的 GitHub Actions 执行。本文件的本地测试结果不代表 Docker 已运行。

此工作流验证全新隔离部署。V168 → V169 历史库升级、数据库及附件备份恢复、目标环境 TLS/代理、真实岗位验收和业务签字仍是独立后续事项。
