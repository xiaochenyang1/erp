# V168 → V169 升级、联合恢复与全栈回退演练

[Deployment Verify](../../.github/workflows/deployment-verify.yml) 在完整部署及采销检查之后，执行独立的历史版本升级、恢复和回退演练。它运行固定旧提交的后端生成历史单据，停写后建立数据库和附件的同一份备份，再恢复到新卷并启动本次验收的 V169 制品。随后将升级前备份恢复到第三组独立卷，运行固定 V168 前后端，验证浏览器登录、业务写入和重启持久性，并保留升级后的数据。

入口为 [deployment-recovery.mjs](../scripts/deployment-recovery.mjs)。它只创建随机 `erp-preview-recovery-*` 项目，不接收既有环境地址或业务凭据；历史、升级恢复和回退环境各有独立的 MySQL、Redis 和附件卷，均为一次性资源。

应用、数据库和 Redis 使用内部网络，Nginx 同时连接单独的入口网络，仅在宿主机 `127.0.0.1` 发布 HTTP 端口。演练同时检查容器内部健康与 runner 到入口的实际可达性。

## 历史来源与检查范围

旧版前后端固定为 `8766ef0ac966da8628bbdfc2b9bbb12c6e93ba17`，该提交的最高迁移是 V168。工具从 Git 归档源码，在独立目录构建旧 JAR，核对打包迁移与旧源码；前端执行 `npm ci --audit=false` 和 `npm run build`，构建前后逐文件核对 Git blob 字节，封存静态文件清单。记录提交、构建日志、JAR 和前端清单 SHA-256，回退时逐个核对实际 HTTP 返回的静态文件字节。旧版构建跳过测试，用于历史样例和隔离回退；待发布的新候选仍须通过完整后端（含 Testcontainers）和前端检查。不会修改迁移历史或调用 Flyway repair。

14 个检查项覆盖：

1. 用旧 JAR 启动真正的 `prod` 后端，确认 MySQL 8.4 上最高迁移为 V168、JAR 字节一致、数据库和 Redis 无对外端口。
2. 通过旧版业务 API 生成已过账调整单、已调整盘点、未提交调整草稿、已取消调整单、尚未调整的盘点及中文/四字节字符客户，并上传两个大于 1 MB 的附件。另保留自定义菜单和审批配置。
3. 停止应用写入，记录全部表行数、按主键排序的数据转储摘要，以及历史库存/凭证和自定义配置快照。调用原有 `backup-database.sh`，备份附件并封存统一清单。
4. 重启旧应用，在备份点后新增客户及附件，删除一个原附件，再次停写，证明源数据确实发生变化。
5. 实际恢复脚本拒绝损坏 SQL 备份，附件工具拒绝损坏归档，统一清单拒绝不匹配的数据库/附件组合；失败后恢复库和附件卷必须仍为空。
6. 在独立 MySQL 和全新附件卷中恢复，所有表行数、全库数据摘要、附件路径/字节/权限/属主必须与备份点一致，此时数据库仍为 V168。
7. 把已通过验收的当前 JAR 指向恢复库、新 Redis 和恢复附件卷，正常启动 Flyway 升级到 V169。检查 JAR、迁移成功状态及 V169 只执行一次。
8. 核对全部历史库存及凭证数据、自定义配置未被迁移改写；新增权限绕开已有菜单 ID，默认审批节点绑定正确的 ERP_ADMIN 角色。
9. 从恢复后的 API 读取旧单据、客户及两个原附件，备份点后的客户和附件不能出现在恢复环境。
10. 旧草稿必须经过提交及他人审批才能过账；自审和重复过账被拒绝。旧盘点生成待审批调整，过账前保持 `ADJUSTMENT_PENDING`。核对库存分别为 12 件 / 120 元及 3 件 / 30 元。
11. 恢复后上传新附件并重启应用，原附件和新附件仍可读取；旧库及旧附件卷维持恢复前状态，证明恢复操作没有写入源数据。
12. 停止 V169 应用写入，保存升级库与附件快照。重新校验升级前备份，在第三组空库及空附件卷中恢复，确认全库和附件与原 V168 备份点完全一致。
13. 将应用指向回退专用 MySQL、Redis 和附件卷，运行固定 V168 JAR 和前端。确认没有 V169 迁移，旧单据和原附件可读，备份点后的附件不存在；通过浏览器登录并刷新首页、销售订单、库存和会计期间页面，无运行时异常。
14. 使用 V168 规则将旧草稿直接过账，核对库存 12 件 / 120 元，上传新附件。重启后重新登录，复核单据、库存数量金额及附件；升级库、升级附件和历史源数据摘要均保持不变。

原完整部署的 8 项检查和采销的 11 项检查继续执行，升级恢复是独立报告。

回退使用升级前的共同备份点。旧应用只连接新恢复的 V168 库，不直接连接已升级到 V169 的数据库。升级后新增的业务保留在升级环境，不会自动带回 V168；目标环境采用这一方式前，需要明确停写点和新增业务的处置方案。

## 数据库与附件的同一备份点

[recovery-checkpoint.mjs](../scripts/recovery-checkpoint.mjs) 把 SQL 压缩文件、SQL 校验文件、附件归档和附件清单的文件名、大小、SHA-256，以及源/目标版本和数据库快照绑定到 `backup-manifest.json`。清单自身的摘要单独记录；恢复命令执行前必须通过完整预检，不能把分别有效但不属于同一备份点的文件混用。

[recovery-attachments.py](../scripts/recovery-attachments.py) 只接受普通文件和目录，拒绝软链接、硬链接、特殊文件、重复路径及目录越界。它在写入目标前核对完整归档，目标目录必须为空；恢复时保留原文件属主和普通权限，使非 root 应用仍可读写。该工具需要 Python 3.11+，CI 使用 Python 3.14 容器操作附件卷。

这些入口要求停写。它们不声称提供业务写入期间跨 MySQL 与文件系统的在线一致性快照。

## 执行与证据

推荐使用 CI 的干净检出。手动执行需要 Docker Compose、Java 17、Node.js 22、Python 3.11+、Chrome 或 Edge、完整 Git 历史，以及已通过验证的部署包；Linux 通过 `CHROME_PATH` 指定可执行文件。输出目录应位于仓库外且尚不存在：

```bash
node backend/scripts/deployment-recovery.mjs prepare-legacy --output /absolute/new/legacy

node backend/scripts/deployment-recovery.mjs run \
  --bundle /absolute/verified/bundle \
  --expected-manifest-sha256 <部署清单摘要> \
  --legacy /absolute/new/legacy \
  --expected-legacy-manifest-sha256 <prepare-legacy 输出的 manifestSha256> \
  --evidence /absolute/new/recovery

node backend/scripts/deployment-recovery.mjs verify \
  --bundle /absolute/verified/bundle \
  --expected-manifest-sha256 <部署清单摘要> \
  --evidence /absolute/new/recovery \
  --expected-revision <本次完整提交>
```

Linux 浏览器需要中文字体，才能生成可读的验收截图。部署和浏览器 CI 均安装 `fonts-noto-cjk` 并检查字体匹配；手动环境同样应确认中文字形可用。DOM 文字断言通过不能代替字体渲染检查。

CI 在运行后始终补充清理，只按本次报告的严格项目名和提交删除容器、卷、网络、镜像标签及私有临时目录。任一步失败、报告缺项、快照不一致或清理失败都会使流水线失败。演练限时 15 分钟，收到 TERM 后额外保留 120 秒供退出。

`erp-deployment-evidence-<run_id>-<attempt>` 新增：

- `recovery/report.json`：14 项演练结果（11 项升级恢复、3 项回退）、历史及当前制品身份、备份点和清理状态。
- `recovery/backup-manifest.json`、`legacy-artifact.json`、`legacy-frontend.json`：备份清单及历史前后端来源。
- `recovery/database-*.json`、`inventory-*.json`、`configuration-*.json`、`attachments*.json`：恢复和迁移前后快照。
- `recovery/rollback-compose-redacted.json`、`browser.png`：回退环境配置和旧版浏览器截图；浏览器失败时保留 `browser-failure.png`。
- `recovery/commands.log`、`legacy/build.log`、`legacy/frontend-install.log`、`legacy/frontend-build.log`：脱敏命令日志和旧版构建日志。
- `recovery-verification.json`、`recovery-cleanup.json`：独立报告复验及补充清理结果。

原始 SQL 备份、附件归档、私有配置及临时账号密码不上传。它们在一次性演练结束时删除。验收应同时核对同一提交的工作流结论、上述报告及部署包；本地脚本单元测试不能代替 Docker 实跑结果。

本次入口覆盖固定 V168 版本生成的合成历史数据及该版本的全栈回退。真实生产数据副本的兼容性、目标环境的备份存储与恢复耗时、TLS/代理、实际上一版制品的回退和真实岗位业务验收仍需要目标环境单独完成。
