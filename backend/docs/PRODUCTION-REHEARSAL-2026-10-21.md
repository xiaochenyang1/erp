# 本地生产配置与数据库恢复演练

第三步先补可重复执行的生产配置演练。当前未绑定真实预生产地址、凭据和业务负责人；本入口始终创建自己的临时环境，只使用合成业务数据。运行结果以独立证据目录的 `report.json` 为准。

## 执行

需要 Node.js 22、Docker Compose、一个已通过 `release-candidate-local.sh` 的候选证据目录，以及干净工作区。在 `backend` 执行：

```bash
node scripts/production-rehearsal.mjs \
  /absolute/accepted-candidate-directory \
  /absolute/new/rehearsal-evidence-directory
```

脚本先核对候选报告、JAR SHA256 和所有 `src/main`/`pom.xml` 的源码哈希，再使用候选 JAR 构建 `Dockerfile.prebuilt`。应用源码或构建输入变化后，必须先重新完成候选验收。演练工具提交与制品候选提交分别记录，不会把旧测试改标为新提交。

证据目录必须在仓库外且尚不存在；备份脚本要求该目录所在文件系统至少有 5 GB 可用空间。Docker 还需要容纳两个临时 MySQL、两个 Redis、ERP 和监控组件；不要通过清理无关容器或卷来腾空间。ERP 容器内存上限为 1.5 GB，仅用于本地资源控制，不能据此判断生产容量。

## 验证范围

1. 从 `docker-compose.yml`、`.env.prod.example` 和监控 Compose 渲染配置，启用真正的 `prod` profile、Redis 和 Flyway。保留通用限流默认值，不加载会关闭限流的 `docker-compose.prebuilt.yml` 覆盖层。
2. 检查首次管理员密码初始化、非 root 运行、健康状态、JWT 鉴权、匿名/无效 JWT/Basic Auth 被拒绝，以及生产环境 OpenAPI 关闭。
3. 使用原始 Prometheus、Alertmanager 和 Grafana 配置采集真实 ERP；四项业务指标与业务健康 API 核对，验证看板及数据源查询。`probe` 容器仅作 HTTP 客户端和本地通知接收器，Prometheus 的目标仍是 ERP。
4. 经业务 API 新建客户，停止应用写入，调用原有 `backup-database.sh` 保存压缩备份及 SHA256。记录所有表行数和按主键排序的数据转储 SHA256。
5. 重启应用并新增第二条客户数据，确认备份之后确有变化。再次停机，验证损坏备份被恢复脚本拒绝且目标仍为空库，再调用原有 `restore-database.sh` 恢复到独立 MySQL，逐表行数和全库数据 SHA256 必须与备份前一致。
6. 使用原始告警等待时间，验证真实停机触发 `ServiceDown` 并送达本地 critical 接收器。把同一候选应用指向恢复后的 MySQL 和新 Redis，重新登录、轮换指标 token，确认备份前客户可读、备份后客户不存在、原库仍保留后者、指标重新采集且恢复通知送达。

生产告警保持 `for: 1m`、`group_wait: 30s` 和 `group_interval: 5m`，因此恢复通知可能需要等待数分钟。不得为了提前通过而缩短本入口的规则时间。此前合成监控测试仍承担更广的 warning/critical 规则回归。

## 隔离、证据与交接

所有容器和卷使用随机项目名，清除主 Compose 的固定容器名和端口发布。整个运行网络设为 internal，HTTP 探针在网络内执行，不连接宿主机既有 ERP、数据库或真实通知接收人。临时密码由脚本生成；不接收真实环境配置。执行结束清理本次容器、卷、网络、镜像和临时凭据，清理失败会使报告失败。

证据包括候选/工具提交、JAR 和镜像标识、脱敏 Compose 配置、构建/应用日志、数据库备份与校验文件、恢复前后表行数/数据哈希、损坏备份拒绝日志和真实告警事件。证据目录权限为 0700，仍应按受控验收材料保存。

本入口验证的是**同一候选应用在数据库恢复后的启动与查询**，不包含上一版镜像回退、历史生产数据兼容、附件/对象存储备份、TLS 代理、容量、真实期初对账或岗位签字。真实预生产仍按 [生产部署](production-deployment.md)、[监控验收](monitoring/README.md) 和 [人工执行包](P0-HUMAN-GATES-EXECUTION-PACKAGE-2026-10-16.md) 交接，由财务、质检和运维完成对应确认。
