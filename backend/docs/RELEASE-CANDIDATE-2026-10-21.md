# 2026-10-21 监控与发布验证收口

本次工作继续推进 [项目分析](PROJECT-STATUS-2026-10-21.md) 的第一、二步。候选提交及最终结果以独立证据目录的 `candidate-sha.txt`、`summary.json` 和逐项报告为准。

已实现：

- Prometheus 文档副本与部署模板统一为 JWT 文件鉴权，Alertmanager 使用 webhook URL 文件，不依赖不存在的环境变量展开参数。
- 新增 `docker-compose.monitoring.yml`，提供固定版本监控组件、Grafana 数据源/看板自动加载和受控文件挂载。
- Grafana 告警表直接读取 Prometheus `ALERTS`；补齐 JWT 轮换、账套范围和监控部署说明。
- 新增监控配置/规则时序测试和隔离运行测试，覆盖 warning/critical 的触发、接收、恢复、Grafana 查询和 JWT 轮换，接入 Monitoring Verify CI。
- 发布入口预检 PowerShell 基础命令，并把当前运行时路径传给 JVM 行为测试。macOS 本机已安装经官方 SHA256 校验的完整 PowerShell 7.6.5，路径为 `~/.local/bin/pwsh`；旧临时残缺目录保留。
- 本地候选入口要求干净提交，自动收集后端完整 Testcontainers、前端、监控、业务 API、受限角色和完整 UI 回归证据。

开发期专项结果：PowerShell 与发布配置测试 21/21 通过；监控配置校验及告警时序/恢复测试通过；隔离监控运行 8/8 通过；残缺 PowerShell 的负例在创建测试数据库前被拒绝。最终候选的完整结果由下述入口实际生成，不预填通过状态。

## 本地候选验证

需要 Docker、Java 17、Node 22、完整 PowerShell 7、前端依赖、Chrome/Edge。使用新的仓库外证据目录，已有目录不会覆盖：

```bash
cd backend
PWSH="$HOME/.local/bin/pwsh" ./scripts/release-candidate-local.sh \
  /absolute/new/erp-validation/evidence-directory
```

后端和 API/UI 使用一次性 MySQL，监控使用内部 Docker 网络和合成指标，不连接业务库或真实通知通道。后端最终制品、两份 SBOM、前端构建、测试 XML、API/UI 与监控日志/报告、源码和制品 SHA256 均归档到该目录。失败时保存已生成诊断材料，修复后使用新候选和新目录复验。

## 后续需要目标环境的工作

独立预生产地址、部署配置、业务岗位及期初数据尚未绑定。取得环境信息后，按 [监控部署与验收](monitoring/README.md)、[生产部署](production-deployment.md) 和 P0 执行包完成迁移/兼容、真实岗位、期初对账、备份恢复/回滚、真实告警送达与恢复，以及财务/质检签字。

本地全部技术门禁通过只意味着具备进一步验收的技术证据。正式环境放行和 release tag 仍等待对应业务/运维验收及上线决定。
