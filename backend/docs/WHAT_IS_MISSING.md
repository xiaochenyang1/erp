# 项目当前完成度与缺口盘点

**更新时间**：2026-08-28（提交收口后）

**范围**：本仓库 `backend` + `frontend`

**分支**：`refactor/e1-workflow-split`

**当前 HEAD**：以 `git rev-parse --short HEAD` 为准；相对远端同名分支的领先数以 `git rev-list --count origin/refactor/e1-workflow-split..HEAD` 为准。

## 结论

ERP 主干业务和本轮新增功能已形成可追溯提交，功能实现阶段已接近闭环。

当前状态是：

- **功能验证通过**；
- **工程收口已完成（当前工作树干净）**；
- **不是正式 RC，也不能创建 release tag**；
- **财务、质检、运维和真实预生产人工门禁仍未完成**。

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
- 客户商品、供应商商品关系；
- 销售发货签收字段和签收附件闸门；
- 多个后端巨石 Service 的 Query、Command、Assembly、Posting/Persistence 拆分；
- 相应前端页面、API、路由、国际化和测试。

## 2026-08-27 自动化验证

### 后端

执行：

```bash
cd backend
./scripts/test-local.sh test
```

结果：

- 2105 项测试；
- 0 failures；
- 0 errors；
- 2 skipped；
- 一次性 MySQL 8.4 环境验证通过。

本轮合同权限、租户表登记、菜单路径、合同附件闸门等针对性回归共 22 项，也已全部通过。

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
- 218 个测试文件、946 个测试全部通过；
- OpenAPI/前后端契约检查通过；
- ESLint 通过；
- 生产构建通过。

### 工作树检查

```bash
git diff --check
```

结果通过，没有空白错误。

以上结果覆盖提交收口后的当前 HEAD；全量测试在 disposable MySQL 8.4 上通过，当前 HEAD 已再次通过 `git diff --check`，但尚未重新生成独立发布证据包。

## 当前提交收口

本轮新增的收口提交：

- `94f490c feat: harden data scope operations`：业务追踪数据范围、双账号 API smoke、运营验收矩阵和设计说明；
- `ecd3e34 test: cover period close tenant and evidence guards`：月结检查跨账套拒绝、快照回归及测试清理。
- `900b090 fix: mark production cost report constructor for injection`：修复报表服务 Spring 构造器注入，恢复完整上下文测试；
- `b9d4839`、`d8340b2`、`6dd6476`：同步提交状态、readiness 证据回填说明和最终验证结果。

`git status --short -uall` 当前为空；提交领先数请使用上述命令实时确认。

## 建议提交序列

建议以每个提交可编译、可回滚为目标，按以下顺序整理：

1. `refactor: split backend service responsibilities`
   - E-1 Query/Command/Assembly/Posting/Persistence 拆分；
   - 对应分解结构、租户边界和行为回归测试。
2. `feat: add finance budget management`
   - V146、预算后端、权限、费用预算维度、预算前端和 smoke。
3. `feat: persist period-close evidence and add cost reports`
   - V147、月结快照、期间服务、库存估值/生产成本报表及前端。
4. `feat: add commercial contract lifecycle`
   - V148–V150、合同后端/测试/权限/附件闸门、订单绑定与履约、合同前端。
5. `feat: add party-product relations and delivery acceptance`
   - V151–V152、主数据关系、采购规则校验、发货签收及前端接线。
6. `docs: refresh data-scope and release status evidence`
   - 数据范围脚本/矩阵、执行板和本状态页。

如果某个共享文件同时包含多个主题，应按补丁块暂存；若无法保证中间提交可通过编译，应合并相邻主题，不为追求提交数量破坏构建完整性。

## 正式发布仍缺什么

### 工程门禁

1. 在当前 HEAD 上确认没有遗漏的未跟踪源码、迁移或测试；
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
