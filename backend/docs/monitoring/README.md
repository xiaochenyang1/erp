# ERP 监控部署与验收

部署配置以 `backend/monitoring/` 为准。当前目录的 `prometheus-scrape.yml`、`alertmanager.yml` 是相同内容的文档副本，`scripts/verify-monitoring.sh` 会检查一致性。`prometheus-alert-rules.yml` 是五项业务规则摘录，不能与完整的 `monitoring/alert-rules.yml` 同时加载。完整规则已包含 `ServiceDown`。

## 先验证配置

在 `backend` 执行：

```bash
./scripts/verify-monitoring.sh
```

需要 Docker、Node.js。脚本使用 Compose 中固定版本的 Prometheus/Alertmanager，验证语法、告警等待时间和恢复、critical/warning 接收器路由及面板结构。容器使用 `--network none`，不会向业务接口或通知接收人发消息；验证不会启动部署，也不能代替真实环境的通知送达验收。

再执行 `node scripts/monitoring-runtime-smoke.mjs`，会启动一次性 Prometheus/Alertmanager/Grafana 和合成指标/通知接收器，验证 JWT 抓取、面板自动加载和数据查询、warning/critical 的触发/接收/恢复、令牌文件轮换。测试使用独立 Docker 内部网络，HTTP 检查在容器内执行，结束删除容器和数据卷；不会连接业务库或真实通知通道。为缩短运行时间，该测试的等待时间调为 2 秒，生产规则的实际等待时间由前述 `promtool` 用例验证。报告和日志保存在 `target/monitoring-runtime-*/`，正式候选运行后应复制到独立证据目录。两个检查已接入 Monitoring Verify CI。

## 配置凭据和账套范围

候选验收完成后，再使用 [生产配置演练](../PRODUCTION-REHEARSAL-2026-10-21.md) 核对真实 ERP 指标、Grafana 查询及真实停机/恢复告警。该入口保留生产规则时间，通知仅投递到内部接收器。

ERP 已关闭 HTTP Basic，指标端点要求 JWT Bearer。网络隔离是额外保护，不能代替应用认证。生产 profile 默认暴露 `/actuator/prometheus`；其他 profile 需显式开启该端点。

在受控目录准备四个文件，内容分别是：有效的 ERP 服务账号 access token（不带 `Bearer ` 前缀）、warning webhook URL、critical webhook URL、Grafana 初始管理员密码。通过以下环境变量提供绝对路径，不把文件内容写入仓库或命令参数：

- `ERP_MONITORING_TOKEN_FILE`
- `ERP_MONITORING_WARNING_WEBHOOK_FILE`
- `ERP_MONITORING_CRITICAL_WEBHOOK_FILE`
- `ERP_GRAFANA_PASSWORD_FILE`

宿主机文件应限制访问，并允许对应容器用户读取。普通 Docker Compose 的文件型 secret 是只读挂载，不提供独立的 secret manager；部署到其他平台时应使用该平台的 secret 机制。Grafana 密码文件用于首次初始化，已有数据卷中的账号密码须通过 Grafana 自身管理。

业务健康指标按抓取账号的公司/账套统计。当前模板抓取一个配置好的账套；多个账套需配置各自凭据/抓取任务并调整面板筛选，不应把单账套结果当成全公司汇总。

令牌到期前轮换：取得同一公司/账套账号的新 access token，更新受控文件，再执行 `docker compose ... up -d --force-recreate --no-deps prometheus`，保证原子替换文件后挂载也更新。确认 `up{job="erp-server"}=1` 且业务指标能读取；只更新 Prometheus 进程内缓存不足以处理旧文件挂载。不要在日志里输出 token。

## 启动监控

在已有 ERP Compose 项目中，使用同一项目名、环境文件和网络：

```bash
docker compose --env-file /absolute/path/to/.env.prod \
  -f docker-compose.yml -f docker-compose.monitoring.yml config --quiet
docker compose --env-file /absolute/path/to/.env.prod \
  -f docker-compose.yml -f docker-compose.monitoring.yml \
  up -d --no-deps prometheus alertmanager grafana
```

使用预构建后端时，按顺序传入 `-f docker-compose.yml -f docker-compose.prebuilt.yml -f docker-compose.monitoring.yml`，prebuilt 文件是覆盖层，不能单独替代主文件。目标 ERP 服务须已运行，并在 Compose 内可用 `http://erp-server:8080` 访问；如果经过 TLS 代理，须同时调整 Prometheus 的 `scheme` 和 `target`。

默认仅绑定本机：Prometheus `9090`、Alertmanager `9093`、Grafana `3000`，可分别用 `ERP_PROMETHEUS_PORT`、`ERP_ALERTMANAGER_PORT`、`ERP_GRAFANA_PORT` 覆盖。远程访问走 SSH 隧道或已有认证代理。Grafana 自动配置 `ERP Prometheus` 数据源和 `ERP` 文件夹下的业务健康面板。

Grafana 告警表直接读取 Prometheus 的 `ALERTS` 指标，展示 `pending`/`firing`；空表表示没有活动告警。通知投递、静默、抑制状态应到 Alertmanager 核实。目标健康显示 0 或无数据时，不能仅凭业务告警表为空判断服务正常。

## 目标环境验收

1. 确认未认证访问 ERP 指标端点为 401；JWT 抓取正常，连续 5 分钟 `up=1`；轮换令牌后仍正常。
2. 确认四类业务健康检查和 JVM/HTTP 指标存在，Prometheus 完整规则载入成功，Grafana 能显示同一数据源的指标和告警。
3. 在独立测试/预生产数据上制造并恢复一项 critical、一项 warning，记录触发、firing、通知送达、恢复和 resolved 的时间及截图。真实通知通道演练需由负责人员安排。
4. 核对服务不可达或 JWT 失效时 `ServiceDown` 能触发。不要以抓取中断后业务指标消失当成恢复。
5. 归档候选 SHA、配置校验结果、图表/通知截图、轮换及演练时间线，交运维确认。预生产、财务/质检签字仍按 P0 执行包分别验收。
