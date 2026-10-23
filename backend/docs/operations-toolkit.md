# 生产运维工具包

本文档汇总所有生产环境运维工具和配置。

## 📦 工具清单

### 1. 告警配置

**文件**: `monitoring/alert-rules.yml`

**包含规则**:
- ✅ 基础设施告警（服务可用性、JVM内存、磁盘）
- ✅ 应用告警（HTTP 5xx、数据库连接池）
- ✅ 业务健康告警（readiness、导入失败、负库存、开放期间、整体 WARN）

**使用方法**:
```bash
# 1. 复制到Prometheus配置目录
cp monitoring/alert-rules.yml /etc/prometheus/rules/

# 2. 在prometheus.yml中引用
rule_files:
  - "/etc/prometheus/rules/*.yml"

# 3. 重载配置
curl -X POST http://localhost:9090/-/reload

# 4. 校验规则和抓取配置
promtool check config /etc/prometheus/prometheus.yml
promtool check rules /etc/prometheus/rules/alert-rules.yml
```

`monitoring/prometheus.yml` 默认抓取 `http://erp-server:8080/actuator/prometheus`，并使用 `/etc/prometheus/secrets/erp-metrics-token` 中的 JWT Bearer token。应用没有启用 HTTP Basic，不能填写 `basic_auth`。如果生产通过 HTTPS 反向代理访问，必须在同一 overlay 中同步修改 `scheme` 和 target，并重新验证实际 HTTP 状态码。

规则文件路径必须与抓取配置保持一致：将 `monitoring/alert-rules.yml` 安装为 `/etc/prometheus/rules/alert-rules.yml`。`docs/monitoring/prometheus-alert-rules.yml` 仅是业务规则摘录/转换参考，不要把两份文件同时加载。

`monitoring/alertmanager.yml` 的 `erp-default` 和 `erp-critical` receiver 均使用 `url_file` secret：

```text
/etc/alertmanager/secrets/erp-default-webhook-url
/etc/alertmanager/secrets/erp-critical-webhook-url
```

两个文件都应由 secret manager 挂载、权限设为 `0600`，并通过 `amtool check-config` 校验。没有 receiver 的配置会静默丢弃告警，不得作为生产配置。

审批待办通知还可以通过应用内 webhook 外发。应用配置由以下环境变量控制：

```text
ERP_NOTIFICATION_WEBHOOK_ENABLED=true
ERP_NOTIFICATION_WEBHOOK_POOL_SIZE=2
ERP_NOTIFICATION_WEBHOOK_QUEUE_CAPACITY=500
ERP_NOTIFICATION_WEBHOOK_REQUEST_TIMEOUT=5s
ERP_NOTIFICATION_WEBHOOK_ALLOWED_HOSTS=hooks.example.com
ERP_NOTIFICATION_WEBHOOK_ALLOW_PRIVATE_ADDRESSES=false
```

实际地址存放在当前公司/账套的系统配置 `notification.webhook.url`，空值表示关闭。外发只在事务提交后排队，使用有界线程池；队列满或目标返回非 2xx 时只记录告警，不回滚已经完成的业务操作。默认拒绝回环、内网、链路本地、CGNAT 和 metadata 地址，并禁用 HTTP 重定向；生产应使用精确主机白名单，只有完成网络评审后才可打开 `ALLOW_PRIVATE_ADDRESSES`。该通道不替代站内通知，验收时应分别验证站内通知和 webhook 失败隔离。

**告警级别**:
- **Critical** (严重): 服务不可用、健康检查失败 - 立即处理
- **Warning** (警告): 资源使用率高、错误率上升 - 及时关注
- **Info** (信息): 缓存命中率低等 - 参考优化

### 2. 数据库备份

**文件**: `scripts/backup-database.sh`

**功能特性**:
- ✅ 自动备份MySQL数据库
- ✅ GZIP压缩节省空间
- ✅ SHA256校验和验证
- ✅ 自动清理30天前的旧备份
- ✅ 磁盘空间检查
- ✅ 备份完整性验证
- ✅ 使用临时 MySQL option file，密码不出现在进程命令行
- ✅ 备份文件和校验文件原子写入，异常中断不会留下可误用的半截备份

**使用方法**:
```bash
# 手动备份
./scripts/backup-database.sh production

# 配置定时备份（每天凌晨2点）
crontab -e
0 2 * * * /opt/erp-server/scripts/backup-database.sh production >> /data/erp/logs/backup.log 2>&1
```

**环境变量**:
```bash
export MYSQL_HOST=localhost
export MYSQL_PORT=3306
export MYSQL_DATABASE=erp_server
export MYSQL_USER=root
export MYSQL_PASSWORD=your_password
# 可选：默认 /data/backups/mysql；保留天数默认 30
export BACKUP_DIR=/data/backups/mysql
export BACKUP_RETENTION_DAYS=30
```

**备份文件位置**:
```
/data/backups/mysql/
├── erp_server_production_20260612_020000.sql.gz
├── erp_server_production_20260612_020000.sql.gz.sha256
├── erp_server_production_20260611_020000.sql.gz
└── ...
```

### 3. 数据库恢复

**文件**: `scripts/restore-database.sh`

**功能特性**:
- ✅ 恢复GZIP压缩的备份
- ✅ 校验和验证
- ✅ 二次确认防止误操作
- ✅ 完整性检查
- ✅ 默认拒绝缺失 `.sha256` 的备份
- ✅ 使用临时 MySQL option file，密码不出现在进程命令行

**使用方法**:
```bash
# 恢复备份（会提示确认）
./scripts/restore-database.sh /data/backups/mysql/erp_server_production_20260612_020000.sql.gz

# 确认提示
警告：即将恢复数据库
数据库: erp_server@localhost:3306
备份文件: /data/backups/mysql/erp_server_production_20260612_020000.sql.gz
当前数据库数据将被覆盖！
确认继续吗？(输入 YES 继续): YES
```

恢复前必须存在与备份同名的 `.sql.gz.sha256` 文件且只包含一条有效 SHA-256 记录。遗留备份确实没有校验文件时，只有在隔离恢复演练中由负责人明确批准后才可使用 `ALLOW_MISSING_CHECKSUM=true`；该模式必须记录为例外，不能作为发布回滚证据。

**恢复演练**（推荐每月一次）:
```bash
# 1. 在测试环境恢复生产备份
export MYSQL_DATABASE=erp_test
./scripts/restore-database.sh /data/backups/mysql/erp_server_production_latest.sql.gz

# 2. 验证数据完整性
mysql -u root -p erp_test -e "SELECT COUNT(*) FROM sys_user;"

# 3. 记录演练结果
```

### 4. 生产部署检查清单

**文件**: `docs/production-deployment-checklist.md`

**包含内容**:
- ✅ 环境准备（服务器、软件依赖）
- ✅ 数据库准备（创建数据库、用户、优化配置）
- ✅ Redis准备（密码、内存策略）
- ✅ 环境变量配置（.env文件模板）
- ✅ 目录和权限设置
- ✅ 应用构建和部署
- ✅ Systemd服务配置
- ✅ 备份配置
- ✅ 监控配置
- ✅ Nginx反向代理配置
- ✅ 防火墙配置
- ✅ 启动后验证步骤
- ✅ 故障排查指南

**使用场景**:
- 首次生产部署
- 灾难恢复后重建
- 迁移到新服务器
- 培训新运维人员

## 🔄 日常运维流程

### 每日检查

```bash
# 1. 检查服务状态
systemctl status erp-server

# 2. 检查健康状态
curl http://localhost:8080/actuator/health

# 3. 查看最近的错误日志
tail -100 /data/erp/logs/erp-server-error.log

# 4. 检查磁盘空间
df -h /data

# 5. 检查备份是否成功
ls -lh /data/backups/mysql/ | head -5
```

### 每周检查

```bash
# 1. 查看Prometheus告警历史
curl http://localhost:9090/api/v1/alerts

# 2. 分析慢查询日志
mysql -u root -p -e "SELECT * FROM mysql.slow_log ORDER BY start_time DESC LIMIT 10;"

# 3. 查看审计日志统计
grep "审计日志" /data/erp/logs/audit.log | awk -F'模块=' '{print $2}' | awk -F' | ' '{print $1}' | sort | uniq -c

# 4. 检查系统资源趋势
# （通过Prometheus/Grafana图表）
```

### 每月检查

```bash
# 1. 备份恢复演练
./scripts/restore-database.sh /data/backups/mysql/erp_server_production_latest.sql.gz

# 2. 清理旧日志
find /data/erp/logs/archive -name "*.log.gz" -mtime +90 -delete

# 3. 更新依赖包（测试环境验证后）
./mvnw.cmd versions:display-dependency-updates

# 4. 性能基准测试
ab -n 10000 -c 100 http://localhost:8080/api/health
```

## 📊 监控指标说明

### 关键指标

| 指标 | 说明 | 正常范围 | 告警阈值 |
|------|------|----------|----------|
| `system_cpu_usage` | CPU使用率 | < 60% | > 80% |
| `jvm_memory_used_bytes` | JVM内存使用 | < 80% | > 85% |
| `http_server_requests_seconds` | 请求响应时间 | P95 < 1s | P95 > 5s |
| `up{job="erp-server"}` | Prometheus抓取状态 | 1 (UP) | 0 (DOWN) |
| `hikaricp_connections_active` | 数据库连接数 | < 80% | > 80% |
| `erp_business_health_overall_status` | 业务健康整体状态（0=UP, 1=WARN） | 0 (UP) | > 0 (WARN) |

### 业务指标

| 指标 | 说明 | 监控目的 |
|------|------|----------|
| `erp_business_health_check_count{check="READINESS_UNPASSED_P0_P1"}` | 未通过的P0/P1验收项数量 | 发布阻断 |
| `erp_business_health_check_count{check="IMPORT_FAILED_RECENT"}` | 最近24小时失败导入任务数量 | 导入故障发现 |
| `erp_business_health_check_count{check="NEGATIVE_INVENTORY_BALANCE"}` | 负库存余额数量 | 库存数据质量 |
| `erp_business_health_check_count{check="OPEN_PERIOD_COUNT"}` | 开放会计期间数量 | 防止期间写入被阻断 |

## 🚨 故障响应流程

### 1. 服务不可用

```bash
# 检查服务状态
systemctl status erp-server

# 查看最近日志
journalctl -u erp-server -n 100 --no-pager

# 如果服务挂了，重启
systemctl restart erp-server

# 检查健康状态
curl http://localhost:8080/actuator/health
```

### 2. 数据库连接失败

```bash
# 检查数据库状态
systemctl status mysql

# 测试连接
mysql -h localhost -u erp_app -p erp

# 检查连接数
mysql -u root -p -e "SHOW STATUS LIKE 'Threads_connected';"
mysql -u root -p -e "SHOW VARIABLES LIKE 'max_connections';"
```

### 3. 内存溢出

```bash
# 查看堆转储文件
ls -lh /data/erp/logs/heap-dump.hprof

# 使用MAT工具分析（下载到本地）
# https://www.eclipse.org/mat/

# 临时增加内存后重启
# 修改 /etc/systemd/system/erp-server.service
# -Xms4g -Xmx8g
systemctl daemon-reload
systemctl restart erp-server
```

### 4. 磁盘空间不足

```bash
# 查看磁盘使用
df -h

# 清理旧日志
find /data/erp/logs/archive -name "*.log.gz" -mtime +30 -delete

# 清理旧备份
find /data/backups/mysql -name "*.sql.gz" -mtime +30 -delete
find /data/backups/mysql -name "*.sql.gz.sha256" -mtime +30 -delete

# 查看大文件
du -sh /data/* | sort -h
```

## 📞 联系方式

### 告警通知配置

在 `monitoring/alertmanager.yml` 中已经配置了有实际通知出口的模板，需要根据实际情况把两个 `url_file` secret 挂载到 Alertmanager：

**邮件通知**:
```yaml
receivers:
  - name: 'team-email'
    email_configs:
      - to: 'team@example.com'
        from: 'alertmanager@example.com'
```

**钉钉通知**:
```yaml
receivers:
  - name: 'dingding'
    webhook_configs:
      - url: 'https://oapi.dingtalk.com/robot/send?access_token=YOUR_TOKEN'
```

**企业微信通知**:
```yaml
receivers:
  - name: 'wechat'
    wechat_configs:
      - corp_id: 'YOUR_CORP_ID'
        to_party: 'YOUR_PARTY_ID'
        agent_id: 'YOUR_AGENT_ID'
        api_secret: 'YOUR_SECRET'
```

## 🎓 培训材料

### 新运维人员入职检查清单

- [ ] 阅读 `docs/production-deployment-checklist.md`
- [ ] 熟悉备份脚本使用
- [ ] 完成一次备份恢复演练
- [ ] 了解监控指标含义
- [ ] 熟悉告警响应流程
- [ ] 获得生产环境访问权限
- [ ] 获得Prometheus/Grafana访问权限
- [ ] 添加到告警通知组

---

**创建日期**: 2026-06-12  
**版本**: 1.0  
**维护者**: 开发团队
