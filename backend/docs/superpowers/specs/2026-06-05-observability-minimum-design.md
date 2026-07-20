# ERP 后端最小可观测性设计

## 背景

当前 ERP 后端已经具备生产部署材料、发布门禁、预生产验收脚本、readiness 证据中心、Docker healthcheck、`/actuator/health`、`/api/health`、生产日志滚动和 MDC `traceId`。这些能力能证明服务是否启动、基础依赖是否可用、单次发布证据是否完整，但上线后仍缺少持续拉取的指标出口和面向 ERP 业务风险的健康摘要。

本设计做一轮最小可观测性增强：让运维系统能抓取标准指标，让业务负责人能快速看到关键异常数量。重点是“上线后能看见系统状态”，不是自研监控平台。

## 目标

- 暴露 Prometheus 格式指标，覆盖 JVM、HTTP、Tomcat、Hikari、Redis 等 Spring Boot / Micrometer 默认指标。
- 新增一个受权限保护的 ERP 业务健康摘要接口，返回少量高价值异常计数和总体状态。
- 把 Prometheus 和业务健康摘要纳入生产部署文档、业务验收清单和预生产基础验收脚本。
- 保持当前安全边界：健康检查继续匿名，指标和业务健康摘要不匿名公开。
- 增加自动化测试，防止依赖、配置、权限和业务健康口径被后续改坏。

## 非目标

- 不部署 Prometheus、Grafana、Alertmanager 或任何外部监控组件。
- 不保存指标历史，不实现告警发送，不做 dashboard JSON。
- 不开放 `/actuator/prometheus` 给匿名公网访问。
- 不建立复杂规则引擎，不引入可配置告警表达式。
- 不扫描全量业务风险，只覆盖当前上线前最值得看的少量计数。

## 当前现状

已有能力：

- `spring-boot-starter-actuator` 已引入。
- `application-prod.yml` 仅暴露 `health,info`。
- `/actuator/health` 和 `/api/health` 已允许匿名访问。
- Dockerfile 和 `docker-compose.yml` 使用 `/actuator/health` 做健康检查。
- `MdcTraceFilter` 生成 / 透传 `X-Trace-Id`，日志 pattern 已包含 `%X{traceId:-}`。
- 生产日志有控制台、应用日志和错误日志滚动。
- `preprod-acceptance.ps1` 已覆盖 `/actuator/health`、`/api/health`、登录和受保护接口。

缺口：

- 没有 `micrometer-registry-prometheus`，因此没有 `/actuator/prometheus` 输出。
- 没有业务健康摘要入口，运维只能知道服务 UP，不能快速知道 readiness、导入、库存、期间等 ERP 风险。
- 文档只要求确认“日志采集、监控告警入口”，没有具体后端检查项。

## 方案概览

采用“标准指标出口 + 业务健康摘要”的最小组合。

### 标准指标出口

新增 Maven 依赖：

- `io.micrometer:micrometer-registry-prometheus`

生产配置调整：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

`/actuator/prometheus` 保持在 Spring Security 保护之下，不加入匿名白名单。生产抓取由反向代理、内网网络策略或 Prometheus 自身认证配置处理。本期只保证后端有指标出口，不把基础设施接入写死在应用里。

### 业务健康摘要

新增系统治理模块：

- `src/main/java/com/tuowei/erp/system/observability`

推荐分层：

- `controller`：REST API 入口。
- `service`：聚合健康项和总体状态。
- `web`：响应 DTO。

接口：

`GET /api/system/observability/business-health`

权限：

- 使用新增权限 `system:observability:view`。

响应建议：

```json
{
  "overallStatus": "WARN",
  "generatedAt": "2026-06-05T08:00:00Z",
  "checks": [
    {
      "code": "READINESS_UNPASSED_P0_P1",
      "name": "未通过 P0/P1 验收项",
      "status": "WARN",
      "count": 2,
      "threshold": 0,
      "summary": "存在未通过或未执行的 P0/P1 readiness 项"
    }
  ]
}
```

总体状态：

- 所有检查项为 `UP` 时，`overallStatus=UP`。
- 任一检查项为 `WARN` 时，`overallStatus=WARN`。
- 本期不引入 `DOWN`，避免业务异常摘要影响基础服务健康检查语义。

## 第一版检查项

第一版只选低风险、能用现有表聚合、不需要复杂业务解释的检查项。

| code | 口径 | 状态规则 |
|---|---|---|
| `READINESS_UNPASSED_P0_P1` | 最近一个非删除 readiness 运行单中，P0/P1 且状态非 `PASSED` 的验收项数量 | count > 0 为 `WARN` |
| `IMPORT_FAILED_RECENT` | 最近 24 小时失败的导入任务数量 | count > 0 为 `WARN` |
| `NEGATIVE_INVENTORY_BALANCE` | 当前租户库存余额 `qty < 0` 的数量 | count > 0 为 `WARN` |
| `OPEN_PERIOD_COUNT` | 当前租户 `OPEN` 会计期间数量 | count = 0 为 `WARN` |

选择这些项的原因：

- 都能用当前已有表直接查询。
- 都和上线后阻塞较强：readiness 没过、导入失败、负库存、没有开放期间。
- 不需要引入新业务规则，也不要求外部监控系统参与。

## 数据访问

推荐在 `ObservabilityBusinessHealthService` 中通过现有 Mapper 查询：

- `ReadinessRunMapper`
- `ReadinessItemMapper`
- `ImportJobMapper`
- `InventoryBalanceMapper`
- `AccountPeriodMapper`

所有查询必须按当前 `AuditMetadataFactory.current()` 的 `companyId`、`accountBookId` 过滤。库存余额、readiness、导入和会计期间都已纳入租户表或显式租户字段，不能只依赖 MyBatis 租户拦截器吃老本。

服务只返回聚合数量，不返回业务单号、客户、供应商、金额、附件内容等敏感明细。

## 权限和菜单

新增权限码：

- `system:observability:view`

新增菜单种子：

- 可作为系统治理菜单下的按钮/页面权限，名称建议“可观测性查看”。

接口权限：

- `GET /api/system/observability/business-health` 使用 `system:observability:view`。

`/actuator/prometheus` 不新增权限码，由 Spring Security 认证保护即可；如果未来需要细分 Actuator 认证，应另开设计，不在本期混进去。

## 脚本和文档

`preprod-acceptance.ps1` 增加基础检查：

- 登录后访问 `/actuator/prometheus`，确认返回 200 且内容包含基础指标名，例如 `jvm_memory_used_bytes` 或 `http_server_requests_seconds`。
- 登录后访问 `/api/system/observability/business-health`，记录响应摘要。

`business-smoke.ps1` 增加只读入口：

- 访问 `/api/system/observability/business-health`。

文档更新：

- `docs/production-deployment.md` 说明 Prometheus 指标出口、认证边界和业务健康摘要。
- `docs/business-readiness-checklist.md` 把指标出口和业务健康摘要纳入上线前监控/告警确认。
- `.env.prod.example` 如有必要补充监控相关说明；本期不新增强制环境变量。

## 错误处理

业务健康摘要接口应避免因为单个聚合查询失败导致整接口 500，建议：

- 单个检查项查询失败时，该项返回 `WARN`，`summary` 说明查询失败。
- 总体状态为 `WARN`。
- 记录后端日志，带 `traceId`。

这样做是为了让运维至少看到“健康摘要本身有问题”，而不是接口直接炸掉只剩一条 500。基础 `/actuator/health` 仍由 Actuator 独立承担服务健康语义。

## 测试策略

配置测试：

- `pom.xml` 包含 `micrometer-registry-prometheus`。
- `application-prod.yml` 暴露 `health,info,prometheus`。
- `SecurityConfig` 没有把 `/actuator/prometheus` 加入匿名白名单。

Controller 测试：

- 无登录访问业务健康摘要返回 `401`。
- 登录但缺少 `system:observability:view` 返回 `403`。
- 有权限访问返回 `overallStatus` 和 `checks`。

Service 测试：

- readiness P0/P1 未通过时 `overallStatus=WARN`。
- 有失败导入任务时对应检查项为 `WARN`。
- 有负库存余额时对应检查项为 `WARN`。
- 没有开放会计期间时对应检查项为 `WARN`。
- 全部口径正常时 `overallStatus=UP`。

脚本配置测试：

- `preprod-acceptance.ps1` 包含 `/actuator/prometheus` 和 `/api/system/observability/business-health`。
- `business-smoke.ps1` 包含业务健康摘要入口。
- 文档包含 `/actuator/prometheus` 和业务健康摘要说明。

## 验收标准

- `GET /actuator/prometheus` 在认证后可访问，并输出 Prometheus 文本格式指标。
- `GET /api/system/observability/business-health` 在有权限时返回业务健康摘要。
- 指标端点不匿名公开。
- 业务健康摘要只返回聚合数量和状态，不返回敏感业务明细。
- 预生产基础验收和业务冒烟脚本覆盖新入口。
- 本地 `.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" test` 通过。
- 本地 `.\scripts\release-check.ps1` 通过。

## 后续扩展

后续可独立设计这些增强，不在本期实现：

- Prometheus / Grafana 部署模板。
- Alertmanager 告警规则。
- 慢接口、慢 SQL 和业务单据积压指标。
- readiness、导入、库存、会计期间的明细 drill-down 页面。
- JSON 结构化日志输出和日志采集平台接入。

## 实施顺序建议

1. 先补 Prometheus 依赖和生产配置测试。
2. 再实现业务健康摘要 service/controller 和权限。
3. 再补预生产脚本与文档。
4. 最后跑聚焦测试、默认测试和发布门禁。

这个顺序能保证标准指标出口先落地，业务摘要按小范围闭合，不会把监控改造成大杂烩。
