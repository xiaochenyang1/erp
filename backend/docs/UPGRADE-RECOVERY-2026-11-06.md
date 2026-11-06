# V168 → V169 升级与数据库、附件恢复演练

[Deployment Verify](../../.github/workflows/deployment-verify.yml) 在完整部署及采销检查之后，新增独立的历史版本升级与恢复演练。它运行固定旧提交的后端生成历史单据，停写后建立数据库和附件的同一份备份，再恢复到新卷并启动本次验收的 V169 制品。

入口为 [deployment-recovery.mjs](../scripts/deployment-recovery.mjs)。它只创建随机 `erp-preview-recovery-*` 项目，不接收既有环境地址或业务凭据；历史库、恢复库、Redis、附件卷均为一次性资源。

## 历史来源与检查范围

旧版后端固定为 `8766ef0ac966da8628bbdfc2b9bbb12c6e93ba17`，该提交的最高迁移是 V168。工具从 Git 归档源码，在独立目录构建旧 JAR，核对打包迁移与旧源码，记录构建日志、提交及 JAR SHA-256。旧版构建跳过测试，仅用于产生升级样例；待发布的新候选仍须通过完整后端（含 Testcontainers）和前端检查。不会修改迁移历史、调用 Flyway repair 或用新代码伪装旧版单据。

11 个检查项覆盖：

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

原完整部署的 8 项检查和采销的 11 项检查继续执行，升级恢复是独立报告。

## 数据库与附件的同一备份点

[recovery-checkpoint.mjs](../scripts/recovery-checkpoint.mjs) 把 SQL 压缩文件、SQL 校验文件、附件归档和附件清单的文件名、大小、SHA-256，以及源/目标版本和数据库快照绑定到 `backup-manifest.json`。清单自身的摘要单独记录；恢复命令执行前必须通过完整预检，不能把分别有效但不属于同一备份点的文件混用。

[recovery-attachments.py](../scripts/recovery-attachments.py) 只接受普通文件和目录，拒绝软链接、硬链接、特殊文件、重复路径及目录越界。它在写入目标前核对完整归档，目标目录必须为空；恢复时保留原文件属主和普通权限，使非 root 应用仍可读写。该工具需要 Python 3.11+，CI 使用 Python 3.14 容器操作附件卷。

这些入口要求停写。它们不声称提供业务写入期间跨 MySQL 与文件系统的在线一致性快照。

## 执行与证据

推荐使用 CI 的干净检出。手动执行需要 Docker Compose、Java 17、Node.js 22、Python 3.11+、完整 Git 历史，以及已通过验证的部署包；输出目录应位于仓库外且尚不存在：

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

CI 在运行后始终补充清理，只按本次报告的严格项目名和提交删除容器、卷、网络、镜像标签及私有临时目录。任一步失败、报告缺项、快照不一致或清理失败都会使流水线失败。演练限时 15 分钟，收到 TERM 后额外保留 120 秒供退出。

`erp-deployment-evidence-<run_id>-<attempt>` 新增：

- `recovery/report.json`：11 项演练结果、历史及当前制品身份、备份点和清理状态。
- `recovery/backup-manifest.json`、`legacy-artifact.json`：备份清单及历史 JAR 来源。
- `recovery/database-*.json`、`inventory-*.json`、`configuration-*.json`、`attachments*.json`：恢复和迁移前后快照。
- `recovery/commands.log`、`legacy/build.log`：脱敏命令日志和旧版构建日志。
- `recovery-verification.json`、`recovery-cleanup.json`：独立报告复验及补充清理结果。

原始 SQL 备份、附件归档、私有配置及临时账号密码不上传。它们在一次性演练结束时删除。验收应同时核对同一提交的工作流结论、上述报告及部署包；本地脚本单元测试不能代替 Docker 实跑结果。

本次入口覆盖固定 V168 版本生成的合成历史数据。真实生产数据副本的兼容性、目标环境的备份存储与恢复耗时、TLS/代理、上一版应用回退和真实岗位业务验收仍需要目标环境单独完成。
