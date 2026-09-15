# 项目当前完成度与缺口盘点

**更新时间**：2026-09-15（多币种下游收口复核后）

**范围**：本仓库 `backend` + `frontend`

**分支**：`refactor/postrelease-sql-gate`

**当前 HEAD**：以 `git rev-parse --short HEAD` 为准；相对远端同名分支的领先数以 `git rev-list --count origin/refactor/postrelease-sql-gate..HEAD` 为准。

## 结论

ERP 主干业务和本轮新增功能已形成可追溯提交，功能实现阶段已接近闭环。

当前状态是：

- **功能验证通过**；
- **本轮可编码收尾已实现并验证，当前工作区含待提交变更**；
- **不是正式 RC，也不能创建 release tag**；
- **财务、质检、运维和真实预生产人工门禁仍未完成**。

### 2026-09-15 多币种下游收口状态

当前工作区已完成一轮多币种下游改造，覆盖账套本位币/币种生命周期、采购与销售订单、收付款、应收应付、收货、发货和退货的结算币种及本位币金额。

- 后端多币种专项测试：5 项通过，0 failures，0 errors；使用 `scripts/test-local.sh` 的一次性 MySQL 8.4 实际执行并通过 V161/V162 迁移。
- 前端多币种/收付款相关测试：19 项通过；type-check、契约检查和生产构建通过。
- `git diff --check` 通过，但本轮改动仍待提交，不能把上述结果当作正式 release evidence。
- 多币种仍需在干净候选 commit 上补跑全量回归及订单/收付款/收发货/退货业务 smoke；正式发布门禁状态不变。

详细功能执行板见 [未完成.md](未完成.md)，历史 RC 冻结边界见 [RC-FREEZE-2026-08-24.md](RC-FREEZE-2026-08-24.md)。

## 当前已具备的业务能力

主干 ERP 已覆盖：

- 系统基础、多租户、权限、数据范围、附件、通知、审计、可观测性和预生产验收；
- 客户、供应商、商品、仓库、库位及客户商品/供应商商品关系；
- 采购请购、询价、订单、收货、退货和供应商结算；
- 销售报价、订单、发货、退货、收款和客户信用控制；
- 库存余额、批次、序列号、预占、调拨、调整、盘点、补货、MRP-lite 和批次谱系；
- 生产 BOM、工艺路线、工单、领料、退料、报工、完工和反完工；
- IQC、IPQC、OQC 及相关业务闸门；
- 应收、应付、收付款核销、费用、发票、凭证、总账、明细账、资金对账、账龄和毛利；
- 工作流、异常工单、异常规则、SLA、运营看板、业务追踪和报表导出。

本轮工作区新增或补强：

- 财务预算管理和预算执行查询；
- 月结检查快照、月结顺序控制及证据展示；
- 库存估值和生产成本报表；
- 商务合同台账、订单绑定、履约回写、提醒、附件和版本历史；
- 客户商品、供应商商品关系（含客户/供应商列表页的关系维护弹窗）；
- 销售发货签收字段和签收附件闸门；
- 多个后端巨石 Service 的 Query、Command、Assembly、Posting/Persistence 拆分；
- 相应前端页面、API、路由、国际化和测试；
- 预算与合同页面补齐双语反馈、真实动作确认和本地化导出文件名，并增加硬编码/键完整性回归；
- 合同列表纯销售/纯采购往来方映射修复，合同预警调度改为显式发现租户作用域并逐账套建立系统身份；
- 合同版本历史面板移入详情弹窗并支持查看单版本快照，客户/供应商商品关系补齐前端维护入口；
- 后台调度和缓存 TTL 等 `erp.*` 运行时配置项统一在 `application.yml` 显式声明，并增加“代码读取的配置项必须已声明”回归；
- 15 个走附件闸门的单据页新增页内附件入口（共享弹窗 + 查看/上传/删除三档权限），并增加“每个闸门业务类型都必须有页内入口、文案与后端 GATED 集合一致”门禁；
- 合同页与预算页拆出 8 个 composable（展示/列表/表单/详情或执行查询），顺带修掉预算详情与执行面板直接显示枚举码、科目部门树和详情加载失败无提示、改每页条数不回到第一页、年月默认值用浏览器时区等既有缺陷；
- 预算、合同 API smoke 接入本地扩展回归，统一 UI smoke 增加预算、合同和新报表路由。

## 2026-09-07 当前候选自动化验证

本节是当前候选的权威自动化统计；下面的 2026-09-04 和更早记录保留为历史快照，不再作为当前候选的测试数量依据。

### 后端

在一次性 MySQL 8.4 环境执行默认测试组，并在发布 profile 中补跑 Testcontainers 组：

```bash
cd backend
./scripts/test-local.sh test
./scripts/test-local.sh -Ptestcontainers -Derp.testcontainers.enabled=true clean package
```

结果：

- 默认测试组 `2189` 项，`0` failures，`0` errors，`3` skipped；
- 含 Testcontainers 的发布 profile `2194` 项，`0` failures，`0` errors，`3` skipped。

本轮还覆盖了异常工单数据库编号、异常规则扫描并发回读、调度租约、V154–V156 迁移和通知外发相关专项回归。

### 前端

执行：

```bash
cd frontend
npm test -- --reporter=dot
npm run type-check
npm run lint
npm run check:contracts
npm run build
```

结果：

- `240` 个测试文件、`1103` 个测试全部通过；
- TypeScript 类型检查、ESLint、OpenAPI/前后端契约检查和生产构建全部通过，Vite 转换 `2866` 个模块。

### 发布边界

`git diff --check` 通过，但当前工作区仍包含本轮待提交的源码、迁移、测试和文档变更。最终发布门禁必须在整理后的干净候选 commit 上重新执行，并重新归档 JAR、SBOM、迁移、smoke 和 release-check 证据；财务签字、质检签字、监控上线/演练和独立真实预生产复验仍未完成，因此不能创建正式 release tag。

## 2026-09-04 自动化验证

> 历史快照：本节记录 2026-09-04 当次工作区的命令输出，当前候选统计以 2026-09-07 节为准。

### 后端

执行：

```bash
cd backend
./scripts/test-local.sh test
```

结果：

- 真实 MySQL 8.4 一次性环境下默认测试组 2186 项测试；
- 0 failures；
- 0 errors；
- 3 skipped；
- 修复 `WorkflowNotificationIntegrationTest` 在 V154 通知 recipient 外键下的清理顺序后通过。

随后执行 `./scripts/test-local.sh -Ptestcontainers -Derp.testcontainers.enabled=true clean package`，含 Testcontainers 组共 2191 项测试，0 failures、0 errors、3 skipped，并成功生成 JAR 与 CycloneDX SBOM。

### 前端

执行：

```bash
cd frontend
npm test -- --reporter=dot
npm run type-check
npm run lint
npm run check:contracts
npm run build
```

结果：

- 236 个测试文件、1092 个测试全部通过（拆分合同与预算页 composable 后重跑，上一次记录为 228 文件/1028 项）；
- TypeScript 类型检查、ESLint、OpenAPI/前后端契约检查和生产构建全部通过（Vite 转换 2866 个模块）。

### 工作树检查

`git diff --check` 通过；根目录 `core` 崩溃转储已加入忽略规则但保留在本机，未进入 Git。当前环境未安装 PowerShell 7，`release-check.ps1` 尚未执行，因此正式发布证据包和人工/运维门禁仍不能宣称完成。

## 2026-08-28 自动化验证

### 后端

执行：

```bash
cd backend
./scripts/test-local.sh test
```

结果：

- 2124 项测试；
- 0 failures；
- 0 errors；
- 2 skipped；
- 一次性 MySQL 8.4 环境验证通过。

另在本轮专用 MySQL 8.4 联调库启动本地后端后执行：

- `contract-api-smoke.cjs`：11/11，通过创建、编辑、版本、提交、审批、详情、列表、CSV 导出、关闭和状态回读；
- `budget-api-smoke.cjs`：9/9，通过权限、创建、重复维度拒绝、提交、审批、执行预览、汇总、关闭和状态回读；
- 合同查询、调度器和原生 SQL 租户标注定向回归 7/7。

### 前端

执行：

```bash
cd frontend
npm run type-check
npm test
npm run check:contracts
npm run lint
npm run build
```

结果：

- TypeScript 类型检查通过；
- 219 个测试文件、950 个测试全部通过；
- OpenAPI/前后端契约检查通过；
- ESLint 通过；
- 生产构建通过。

### 工作树检查

```bash
git diff --check
```

结果通过，没有空白错误。`test-new-pages.sh` 已改为仓库相对路径的 12 页静态注册检查，12/12 通过；PowerShell 7.5 容器解析 `local-extension-regression.ps1` 通过。

以上结果覆盖当前工作区，但这些变更尚未提交，也未重新生成独立发布证据包。`backend/target` 下的本地 smoke 报告会被 Maven `clean` 删除，只能作为开发联调证据，不能代替正式发布归档。

## 提交状态

最近已提交的收口包括：

- `94f490c feat: harden data scope operations`：业务追踪数据范围、双账号 API smoke、运营验收矩阵和设计说明；
- `ecd3e34 test: cover period close tenant and evidence guards`：月结检查跨账套拒绝、快照回归及测试清理。
- `900b090 fix: mark production cost report constructor for injection`：修复报表服务 Spring 构造器注入，恢复完整上下文测试；
- `b9d4839`、`d8340b2`、`6dd6476`：同步提交状态、readiness 证据回填说明和最终验证结果。

当前工作区包含预算/合同国际化、API/UI smoke、合同运行时修复、并发幂等/通知外发加固及文档更新，尚未提交。提交领先数请使用上述命令实时确认。

## 建议提交序列

1. 按功能边界拆分并提交当前工作区变更，至少覆盖合同/预算收口、并发幂等与通知外发加固、对应迁移和测试。
2. 在新提交的干净 HEAD 上重新生成独立发布证据包，并保留后端、前端及 smoke 报告。

## 正式发布仍缺什么

### 工程门禁

1. 提交本轮收尾变更，并在新的干净 HEAD 上确认没有遗漏的未跟踪源码、迁移或测试；
2. 在干净 HEAD 上重新执行后端全量测试、Testcontainers 发布构建、前端全量检查和 `git diff --check`；
3. 基于该 HEAD 重新生成 JAR、SBOM、迁移报告、smoke 报告和发布门禁报告；
4. 将证据保存到独立归档目录，不能只放在会被 Maven `clean` 删除的 `backend/target`。

对既有 readiness 运行单补录人工证据时使用 `.\scripts\register-readiness-item-result.ps1`；可回填 `FINANCE_LEDGER`、`PERIOD_LOCK`、`INVENTORY_FINANCE_RECONCILIATION`、`INITIAL_IMPORT`、`BACKUP_ROLLBACK`，无法证明的项目保持 `BLOCKED`。

### 人工和环境门禁

1. 财务按最新候选样例完成 F1–F12 真人复核和签字；
2. 质检按最新候选样例完成 Q1–Q5 真人复核和签字；
3. Prometheus/Grafana/Alertmanager 在目标平台落地，并完成告警触发与恢复演练；
4. 在独立真实预生产环境使用真实 MySQL、Redis、密钥和部署拓扑复验迁移、健康检查及关键业务链路；
5. 所有 P0/P1 门禁通过后，由授权人作出 GO 决策，再创建正式 release tag。

## 历史证据使用边界

[FQ-SIGNOFF-RC-989FBC4-2026-08-24.md](FQ-SIGNOFF-RC-989FBC4-2026-08-24.md) 和既有 RC 证据证明的是较早候选提交的技术预检结果。当前工作区已经新增业务功能、迁移和重构，因此旧证据只能用于格式和历史追溯，不能直接作为当前候选的签字或发布依据。

当前权威状态以本文件、[未完成.md](未完成.md)、干净候选提交上的命令输出以及新生成的独立证据包为准。
