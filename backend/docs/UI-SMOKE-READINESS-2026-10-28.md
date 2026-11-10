**浏览器回归启动就绪修复（2026-10-28）**

当前候选 `e033223` 的云端 UI Smoke 在准备账号阶段登录返回 401，页面和工作流没有执行。失败运行：https://github.com/xiaochenyang1/erp/actions/runs/40279951188 。

`LocalBootstrapService` 在事务性的 `ApplicationRunner` 中设置管理员密码。Web 服务能够处理 HTTP 请求的时间早于该事务提交；原脚本使用 `/actuator/health` 判断可登录，未检查应用初始化是否完成。

新增 MySQL 集成测试在真实密码更新事务的 `beforeCommit` 暂停启动，证明其他 HTTP 请求此时登录返回 401。修复前，就绪端点返回 401，测试失败；修复后，初始化期间返回 503 / OUT_OF_SERVICE，事务提交及启动完成后返回 200 / UP，真实管理员登录成功。该测试同时验证错误密码、匿名业务接口、Prometheus 和数据库健康详情仍被拒绝。

变更：

- 所有 profile 启用 Spring Boot 就绪探针，只新增匿名访问精确路径 `/actuator/health/readiness`。
- 账号准备、浏览器回归、本地隔离验收和 GitHub UI Smoke 等待 readiness；初始化期间不循环尝试登录。
- 请求和总等待均有期限；探针 401 / 403 / 404、异常成功响应直接失败；真正的登录失败不重试、不跳过。
- 新增 10 项 Node 行为测试，覆盖账号准备顺序、错误凭据、启动拒绝、请求/响应体超时、探针配置错误和状态校验；接入本地及云端回归。
- 新增 `BootstrapReadinessMysqlIntegrationTest`，通过 `-Ptestcontainers -Derp.testcontainers.enabled=true` 纳入完整发布检查。

开发期验证：就绪/账号脚本 10 项通过；启动 MySQL 集成测试、原初始化测试、安全配置及监控配置测试共 9 项通过。全量候选与云端结论须以修复提交对应的实际结果为准。

复验入口：

```bash
cd backend
node --test scripts/ui-smoke-readiness.test.mjs
./scripts/test-local.sh -Ptestcontainers -Derp.testcontainers.enabled=true \
  -Dtest=BootstrapReadinessMysqlIntegrationTest,LocalBootstrapServiceTest,SecurityConfigTest,ObservabilityConfigurationTest test
# 干净候选完整检查，输出目录必须不存在且位于仓库外：
./scripts/release-candidate-local.sh /absolute/new/evidence-directory
```

后续交付继续按 [预生产交接](PREPROD-HANDOFF-2026-10-21.md) 和 [财务/质检执行包](P0-HUMAN-GATES-EXECUTION-PACKAGE-2026-10-16.md) 推进。真实环境地址、部署方式、验收账套/岗位、业务数据及负责人需要实际绑定；技术通过不代表业务签字或发布 GO。
