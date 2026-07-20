# ERP 后端 13 项优化路线图设计

## 背景

`erpServer` 项目自 2026-04-28 启动以来经过约 4 周高速迭代，已达到 587 个 Java 文件、52 个 Flyway 迁移脚本、47 个 Controller 和 80 个 Mapper 的规模。在 2026-05-27 的全量项目复盘中，识别出 13 项可优化点，涵盖配置安全、横切基础设施、可维护性重构、性能与缓存四类，按风险/收益维度排出 A/B/C 优先级。

本设计不是对单个业务能力的增强，而是为接下来 6 周的工程化治理工作提供一份**路线图（meta-spec）**：明确分阶段顺序、每项 scope 边界、跨项依赖、工作流仪式和验收标准。每项具体实施再由该项自己的 `docs/superpowers/specs/` 设计和 `docs/superpowers/plans/` 计划承载。

## 目标

- 把 13 项优化按"风险/收益"重排成 4 个 Phase，并钉死跨项依赖顺序。
- 给每项定义清晰的 scope 边界（改什么、不改什么、验收标准），防止"修一处顺手清理一片"。
- 统一工作流仪式：每项都走 `worktree → spec → plan → TDD → mvn verify → commit`。
- 在动手前书面列出 #2 Redis 缓存、#3 巨石 Service 拆分、#9 Testcontainers 三项大改造的风险与缓解。

## 非目标

- 不在本文档内为 13 项写各自的实施细节（那是各自 spec/plan 的事）。
- 不变更项目的多租户/审计/JWT/幂等等核心约定。
- 不引入新业务能力。
- 不重命名仓库目录（`python/erpServer`）。

## Phase 分解与执行顺序

按风险/收益重排成 4 个阶段。

### Phase 1 · 快胜小改

无外部依赖、每项 ~1 天工作量、纯加固。

1. **#5 dev profile 危险默认值收紧**
   - 去掉 `application-dev.yml` 中 JWT secret 默认值、关闭 expose-message、关闭 swagger 默认开关，改成必须从 env 注入。
2. **#13 `ProductionStartupValidator` 加强**
   - 启动时校验 JWT secret ≥32 字节、prod 下 CORS 白名单非空、prod 下 `erp.error.expose-unexpected-message=false`。
3. **#4 224 个 `@Transactional` 批量补 `readOnly=true`**
   - 仅扫描 `*QueryService`、`Report*` 等纯查询服务；写操作不动。
4. **#11 结构化日志 + MDC traceId**
   - 加 `MdcTraceFilter` 放在 `JwtAuthenticationFilter` 之前；logback pattern 加 `%X{traceId}`；响应头回 `X-Trace-Id`。

### Phase 2 · 质量门铺路

为后面大改造提供安全网。

5. **#1 CI workflows 落地**
   - 新增 `.github/workflows/pr-verify.yml`：mvn verify + CycloneDX SBOM upload + Flyway dry-run。
6. **#9 Testcontainers MySQL 替换 H2**
   - 加 `testcontainers-mysql` 依赖；新增 `MysqlIntegrationTestBase`；把对 MySQL 特性敏感的测试（`ON DUPLICATE KEY`、`CHECK` 约束）切过去；纯单元测试继续用 H2。

### Phase 3 · 可维护性重构

互相独立，可并行或乱序。

7. **#6 `GlobalExceptionHandler` 表驱动**
   - 30+ 个 if-else 唯一键映射改成 `Map<String, String>` 常量。
8. **#7 `PermissionCodes.java` 拆分**
   - 按 `system/inventory/finance/...` 拆成子接口；原 `PermissionCodes` 用 `extends` 聚合保持兼容。
9. **#10 `ApiResponse.code` 语义文档化**
   - 写 `docs/api-conventions.md` 钉死 `code` 字符串约定；当前 code="0" 暂不动。
10. **#8 Flyway 版本号跳号备忘**
    - 新增 `docs/migrations-history.md` 记录 V21/V51 缺号原因。
11. **#12 README 顶部说明**
    - README 顶部加"本项目是 Java/Maven，目录名是历史遗留"提示。

### Phase 4 · 大手术

最重，依赖前面所有基础设施。

12. **#2 Redis 缓存层引入**
    - 新增 `common/cache/CacheService` 抽象（Cache-Aside 模式）；先接入字典/菜单/权限三处只读热点；key 格式 `erp:{company_id}:{module}:{...}`，租户隔离强制。
13. **#3 巨石 Service 拆分**
    - 仅拆 `InventoryPostingService` 959 行一个文件；按"过账/扣减/批次/预留"切成 4 个 collaborator；对外 API 与事务边界不变。

## 每项 Scope 边界

为防止扩散，每项必须严格遵守"改什么、不改什么"。

| # | 项目 | 改 | 不改 | 验收 |
|---|---|---|---|---|
| 5 | dev profile 收紧 | `application-dev.yml` 危险默认值改成 `${...}` 必填 | dev 用的 mysql/redis 连接字符串 | 删 env 启动失败、补 env 正常 |
| 13 | 启动校验加强 | `ProductionStartupValidator` 加 3 条校验 | 其他配置项校验 | 单测 + 故意配错启动失败 |
| 4 | readOnly 补齐 | `*QueryService`、`Report*` 内的 `@Transactional` 加 `(readOnly=true)` | 业务逻辑、写操作 | grep 校验、`mvn verify` 绿 |
| 11 | MDC traceId | 新增 `MdcTraceFilter`、升级 logback pattern、加响应头 | 现存 355 条 log 调用消息 | filter 单测、本地起服务看到 traceId |
| 1 | CI workflows | 新增 `.github/workflows/pr-verify.yml` | `scripts/release-check.ps1` 保留作本地脚本 | 任意 PR 能跑通 |
| 9 | Testcontainers | 加依赖、新增 `MysqlIntegrationTestBase`、切对 MySQL 敏感的测试 | 纯单测继续 H2、不动现有断言 | 双跑都绿、CI 上能起 |
| 6 | 异常表驱动 | `GlobalExceptionHandler` 内 if-else 改 `Map`；`resolveDuplicateMessage` 精简 | 唯一键命名、抛异常位置 | 现有 duplicate key 测试不变绿 |
| 7 | PermissionCodes 拆分 | 按业务域拆子接口，原类 `extends` 聚合 | 所有 `@PreAuthorize` 字符串引用 | 编译过、引用数不变 |
| 10 | ApiResponse 文档化 | 写 `docs/api-conventions.md` | 当前 code="0" 实现 | 文档评审通过 |
| 8 | 迁移备忘 | 新增 `docs/migrations-history.md` | SQL 文件 | 文档存在 |
| 12 | README 说明 | README 顶部加说明段 | 目录名不重命名 | 文档存在 |
| 2 | Redis 缓存 | 新增 `common/cache/CacheService`、接入字典/菜单/权限三处 | 业务写路径不做 write-through、其他模块不动 | 命中率指标、失效一致性测试 |
| 3 | InventoryPostingService 拆分 | 仅拆这一个文件，切 4 个 collaborator，主类 ~300 行 | 对外 API、事务边界、其他 Service | 现有库存测试全绿 + 协作单元单测 |

## 跨项依赖

```
#5 ─┐
#13 ┴──→ Phase 1 完整 ───→ #1 CI（CI 要校验 prod 配置安全）
#11 ────→ #1（CI 里要校验日志格式不破）
#9  ────→ #2/#3（重测试要 testcontainers 兜底）
#1  ────→ #2/#3（大改造必须有 PR CI 验证）
```

Phase 3 内 5 项互相独立，可任意顺序或并行。

## 工作流仪式

每项严格走 7 步：

1. `git checkout master && git pull`
2. `EnterWorktree` 创建独立 worktree（命名 `opt-<NN>-<topic>`）
3. `superpowers:brainstorming` 写 `docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md`，commit
4. `superpowers:writing-plans` 写 `docs/superpowers/plans/YYYY-MM-DD-<topic>.md`，commit
5. 实施：代码改动项走 `superpowers:test-driven-development`；纯文档项（#8 #10 #12）跳过 TDD，直接按 plan 写文档
6. `superpowers:verification-before-completion` 跑 `mvn verify` 实测，贴输出（文档项也要确认 verify 不被打破）
7. commit + `ExitWorktree(keep)` 退出，由用户决定 merge

### 命名约定

- worktree/分支：`opt-01-dev-profile-tighten`、`opt-02-startup-validator`、…
- spec：`docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md`
- plan：`docs/superpowers/plans/YYYY-MM-DD-<topic>.md`
- commit：沿用 `docs:` / `feat:` / `fix:` / `refactor:` / `chore:` 前缀；代码改动项至少 3 个 commit（spec / plan / impl），纯文档项 2 个即可（spec / impl，因为 plan 与 impl 可合并）

### 验收标准（统一）

每项实施完成必须满足：

1. `mvn verify` 全绿。
2. 对改动点补 unit / integration 测试。
3. 在 spec 中明确列出补出的测试点。

## 关键风险与缓解

### #2 Redis 缓存

- **风险**：多租户 key 隔离漏写 → 跨公司数据泄露。
- **缓解**：spec 强制要求 key 必带 `company_id` 前缀；写一个 `CacheKeyBuilder` 作唯一入口；单测覆盖 key 生成。

### #3 InventoryPostingService 拆分

- **风险**：库存过账是业务核心，重构破坏并发正确性（乐观锁 8 次重试逻辑）。
- **缓解**：拆分前先在 spec 阶段盘点测试覆盖缺口并补齐；保持公共方法签名不变；分多次小 commit。

### #9 Testcontainers

- **风险**：CI 启动时间显著拉长。
- **缓解**：reuse 模式 + docker layer cache；纯单测继续 H2。

### #6 异常表驱动

- **风险**：30+ 唯一键映射迁移时漏项，部分场景退化为通用消息。
- **缓解**：spec 列出全部唯一键清单，单测每条都验证。

## 节奏估算

- Phase 1：约 1 周（4 项 × ~1 天 + 间隔）
- Phase 2：约 1 周（#1 ~2 天、#9 ~3 天）
- Phase 3：约 1-2 周（5 项可并行，文档类很快）
- Phase 4：约 2-3 周（#2 ~1 周、#3 ~1-2 周）
- 整体 6 周左右。

## 验证策略（meta）

- 每完成一个 Phase，跑一次 `mvn verify`，确认 Phase 内累积改动不互相破坏。
- Phase 1 完成后开启 #1 CI，从此每项 PR 自动跑 verify。
- Phase 2 完成后，#2 和 #3 这两项大改造才允许进入 worktree。
- 整体收尾时跑一次 docker compose up 端到端冒烟。

## 后续

本 spec 通过后，按 Phase 1 第一项 #5 dev profile 收紧开始进入各自的 `brainstorming → writing-plans → 实施` 循环。每项的设计文档落在 `docs/superpowers/specs/`，实施计划落在 `docs/superpowers/plans/`，与本路线图并列归档。
