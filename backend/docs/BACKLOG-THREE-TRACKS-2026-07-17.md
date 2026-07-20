# ERP 三线 Backlog（2026-07-17）

**范围**: `erpServer` + 相邻 `erp-frontend`  
**原则**: 以代码与最新 smoke/门禁证据为准；不按 6 月旧 md 开补洞任务。  
**当前总判断**: 主干业务与联调缺口已清；**唯一发布阻塞 = 财务/质检业务签字**（技术 `DECIDED_GO` 已齐）。

状态标记：

| 标记 | 含义 |
|------|------|
| `READY` | 可立即执行，入口明确 |
| `BLOCKED_HUMAN` | 依赖真人签字/机房/运维确认 |
| `NEEDS_SPEC` | 先写口径/设计，再编码 |
| `DONE` | 已完成，仅作基线索引 |

---

## Track A · 上线最小集（Release Path）

> 目标：把「技术 GO」变成「可签字发布候选」。**不新开业务功能**。

### A1. 财务/质检业务签字  `BLOCKED_HUMAN` · P0

| 项 | 内容 |
|----|------|
| **做什么** | 财务勾选 F1–F12、质检勾选 Q1–Q5，填 GO/NO-GO 与签字 |
| **入口** | `docs/FQ-SIGNOFF-FINAL-2026-07-16.md` |
| **点击路径** | `docs/FQ-SIGNOFF-CLICKPATH-2026-07-17.md` |
| **技术预检复跑** | `node scripts/fq-signoff-all.cjs` → `target/fq-signoff-api-check/all-summary.json` |
| **证据参考** | `docs/GATE_PROGRESS_2026-07-17.md`；Docker 证据 `target/docker-preprod-full-20260717b/` |
| **验收** | 签字栏非空；扫描件建议：`target/docker-preprod-full-20260717b/signed/` |
| **禁止** | 用脚本自动勾选冒充业务签字 |

### A2. 本机证据包与门禁再确认（签字前可选复跑） `READY` · P0

| 步骤 | 命令 / 入口 |
|------|-------------|
| 干净树 + Testcontainers | `.\scripts\release-check.ps1 -IncludeTestcontainers -MavenRepoLocal .\.m2-release` |
| Docker core | `docker compose --env-file .env.preprod-local -f docker-compose.yml -f docker-compose.prebuilt.yml --profile core up -d` |
| 采/销/产回滚样例 | `scripts/purchase-to-payment-acceptance.ps1` / `sales-to-cash-acceptance.ps1` / `production-manufacturing-acceptance.ps1`（BaseUrl `18080`） |
| 决策 | `.\scripts\decide-readiness-release.ps1 -EvidenceDirectory target\docker-preprod-full-20260717b ...` |
| 归档 | `.\scripts\export-release-evidence-bundle.ps1 ...` |
| 浏览器回归 | `UI_SMOKE_ROUTES=0 node scripts/ui-smoke.mjs`（34 workflow） |
| 本地干净库验收 | `.\scripts\local-runtime-acceptance.ps1`（默认 `erp_codex_runtime`） |

**验收**: 报告 `PASSED` / smoke 0 fail / 证据 zip 可复验。  
**说明**: 2026-07-17 已有一轮全绿；仅在变更发布候选 commit 后必须重跑。

### A3. Readiness 子项人工回填 `READY` · P1

| 项 | 入口 |
|----|------|
| 脚本 | `scripts/register-readiness-item-result.ps1` |
| 关注码 | `FINANCE_LEDGER` / `PERIOD_LOCK` / `INVENTORY_FINANCE_RECONCILIATION` / `INITIAL_IMPORT` / `BACKUP_ROLLBACK` |
| UI | `erp-frontend/src/views/system/readiness/index.vue` |
| API | `system/readiness/**`（`ReadinessController` / `ReadinessService`） |

**验收**: 能证则 `PASSED`+附件；不能证保持 `BLOCKED`，不伪造。

### A4. 运维/监控上线核对 `BLOCKED_HUMAN` · P1

| 项 | 入口 |
|----|------|
| 清单 | `docs/business-readiness-checklist.md`、`docs/production-deployment.md` |
| 告警模板 | `docs/monitoring/prometheus-alert-rules.yml` |
| 指标 | `/actuator/prometheus`、`/api/system/observability/business-health` |
| 代码 | `system/observability/**`、`erp-frontend/src/views/system/observability/index.vue` |

**验收**: 生产监控已加载等价规则；备份/回滚责任人、发布窗口已记录。

### A5. 独立机房预生产（按政策） `BLOCKED_HUMAN` · P1/可选

复跑 A2 全套，换 BaseUrl/环境变量；政策不强制则可用本机 Docker 等价证据。

### A6. 发布边界冻结 `READY` · 流程

- 发布候选必须是**干净工作树明确 commit**（禁止 `-AllowDirtyWorktree` 作正式证据）。
- 入口：`scripts/release-check.ps1`、`scripts/verify-release-check-report.ps1`。
- 主状态文档：`docs/WHAT_IS_MISSING.md`（更新结论时只改此文 + GATE 记录）。

### Track A 推荐执行顺序

```text
A2（若 commit 变了） → A1 业务签字 → A3 回填 → A4 运维确认 → [A5 若政策要求] → tag/发布
```

---

## Track B · 下一期业务扩展（Product）

> 目标：在现有内核上增强业务深度。**每项先定口径再写代码**（`NEEDS_SPEC`）。  
> 不做：多公司多账套 SaaS 化、自由 BPM、移动端、复杂 BI（除非单独立项改边界）。

### B1. 数据范围运营打磨  `READY` · 业务安全增强

| | |
|--|--|
| **现状** | 后端数据范围 + 用户/角色分配 UI 已存在 |
| **后端** | `common/security/DataScopeService.java`；`system/datascope/**`；`RoleDataScopeService` / `UserDataScopeService`；迁移 `V106__data_scope_assign_button_permission_seed.sql`；smoke `scripts/data-scope-api-smoke.cjs` |
| **前端** | `views/system/users/index.vue`、`views/system/roles/index.vue`；`api/system.ts`（data-scope）；测试 `api/system.data-scope.test.ts` |
| **建议补** | ① 非 admin 角色端到端：仅可见授权仓库单据；② 数据范围与列表/导出/报表一致性矩阵；③ ui-smoke 增加 data-scope 双账号 workflow |
| **验收** | 受限账号跨仓读写均 403/空列表符合预期；矩阵文档落 `docs/` |

### B2. 销售信用与价格策略（最小） `NEEDS_SPEC` · 销售

| | |
|--|--|
| **触点** | `sales/order/**`、`masterdata/customer/**`；前端 `views/sales/orders`、`views/masterdata/customers` |
| **最小范围** | 客户信用额度/超限拦截；简单价目或最低价校验（二选一先做） |
| **不做** | 完整 CRM、渠道价、促销引擎 |
| **验收** | 下单超限被拒；审批/出库链路不被破坏；单测 + 一条 ui-smoke |

### B3. 采购询价/比价（最小） `NEEDS_SPEC` · 采购

| | |
|--|--|
| **触点** | 新建 `purchase/inquiry/**` 或挂在 `purchase/order` 前序；菜单 Flyway 必补 |
| **最小范围** | 询价单 → 多供应商报价行 → 选用生成采购订单草稿 |
| **验收** | 选用后 PO 明细可追溯询价来源 |

### B4. 质检扩展：OQC / 过程检  `NEEDS_SPEC` · 质量

| | |
|--|--|
| **现状** | 仅 IQC：`qc/inspection/**`；前端 `views/qc/inspection`；闸门在采购入库过账 |
| **扩展方向** | OQC 挂销售发货前；或 IPQC 挂生产完工前（先做一个） |
| **复用** | 检验单状态机、合格数量回写模式参考 IQC |
| **验收** | 未判定禁止下游过账；判定后数量口径与库存一致 |

### B5. 生产深化（MRP 轻量 / 工序报工） `NEEDS_SPEC` · 生产

| | |
|--|--|
| **现状** | BOM/工单/领料/完工；工艺与工作中心主数据：`production/routing`、`production/workcenter` |
| **可选 A** | 按 BOM+需求生成建议采购/生产（基于现有 `inventory/replenishment` 扩展） |
| **可选 B** | 工序报工（routing 节点 + 工时记录，不做排产甘特） |
| **入口参考** | `production/order/**`、`InventoryReplenishmentSuggestionService`、前端 `views/production/**` |
| **验收** | 不影响现有「下达→领料→完工」smoke |

### B6. 财务深化（受控） `NEEDS_SPEC` · 财务

| 候选 | 说明 | 入口 |
|------|------|------|
| 发票登记（进项/销项最小） | 关联收付款，不做税务申报 | `finance/receipt` `finance/payment` |
| 收付款部分核销增强 UX | 多应收一收款已有则补齐引导 | `views/finance/payments` |
| 期间结账向导 | 检查清单 UI 化 | `finance/period/**`、`views/finance/periods` |

**禁止硬扩**: 跨期间复杂调整、固定资产、合并报表、外部财务自动对接——见 `WHAT_IS_MISSING.md`「不建议现在硬补」。

### B7. 通知外发（邮件/企微 webhook 最小） `NEEDS_SPEC` · 系统

| | |
|--|--|
| **现状** | 站内通知 `system/notification/**`；`views/system/notifications` |
| **最小** | 审批待办/异常 SLA 超时外发一条通道 |
| **验收** | 失败可降级为站内；不阻塞主事务 |

### B8. 经营看板指标增强  `READY/浅` · 报表

| | |
|--|--|
| **入口** | `dashboard/**`、`report/**`；前端 `views/dashboard`、`views/reports` |
| **建议** | 今日/本月采销金额、应收应付账龄简表、库存金额 TOP；只读聚合 |
| **验收** | 权限 `report:*` / dashboard 权限控制；大数据量分页或限制区间 |

### Track B 建议排序

```text
B1（安全/范围已半成品） → B8（低风险只读） → B2 或 B4（按业务痛点二选一）
→ B6 受控项 → B3/B5/B7（需完整 spec）
```

---

## Track C · 工程债清理（Engineering）

> 目标：可维护性、安全一致性、测试厚度。**不改变业务口径**。

### C1. 过时文档归档  `READY` · P0（低风险）

| 动作 | 路径 |
|------|------|
| 权威状态 | 只维护 `docs/WHAT_IS_MISSING.md` + 本 backlog + 最新 GATE_* |
| 迁入 archive | `docs/BACKEND_API_DEVELOPMENT_PROGRESS.md`、`docs/GLOBAL_PROJECT_AUDIT.md`、`docs/BACKEND_FRONTEND_GAP_ANALYSIS.md`、6 月 `TEST_REPORT_*`、`PROJECT_FINAL_SUMMARY.md` 等标注「历史快照」后移 `docs/archive/2026-06-stale/` |
| 前端 | `erp-frontend/docs/*` 过时 integration 报告同样处理 |
| **验收** | README「关键文档」只链权威文；archive 文首有「不代表当前状态」 |

### C2. 前端测试加厚  `READY` · P1

| | |
|--|--|
| **现状** | `auth.test.ts`、`production.test.ts`、`system.data-scope.test.ts`、`user.test.ts`（约 4 文件） |
| **优先补** | ① API 雪花 ID 归一化（`api/finance.ts` `api/inventory.ts` `api/sales.ts` `api/purchase.ts`）；② `directives/permission.ts`；③ 关键 store |
| **配置** | `erp-frontend/vitest.config.ts`、`.github/workflows/frontend-verify.yml` |
| **验收** | `npm test` 用例数显著增加且全绿；CI 门禁保持 |

### C3. 剩余只读页权限/导出一致性  `READY` · P2

无 `v-permission` 的页面（多为只读/导出，写操作已收口）：

- `finance/ledger|payables|receivables|vouchers`
- `system/imports|logs|observability|document-state-rules`
- `reports|reports/traces`、`workflow/records`、`dashboard`

参考：`docs/superpowers/plans/2026-07-13-button-permission-closure-checklist.md` 第二批。  
**验收**: 导出按钮与后端 `@PreAuthorize` 一致；或明确文档「与 view 同码，不单列」。

### C4. 巨石 Service / 大 Vue 拆分  `NEEDS_SPEC` · P2

| 热点 | 约行数 | 路径 |
|------|--------|------|
| PurchaseOrderService | ~840 | `purchase/order/service/PurchaseOrderService.java` |
| SalesDeliveryService | ~794 | `sales/delivery/service/SalesDeliveryService.java` |
| SalesReturnService | ~791 | `sales/returnorder/service/SalesReturnService.java` |
| ReportQueryService | ~787 | `report/service/ReportQueryService.java` |
| FinancePostingService | ~692 | `finance/posting/FinancePostingService.java` |
| InventoryPostingService | ~559 | `inventory/stock/service/InventoryPostingService.java` |
| stocks/index.vue | ~1268 | `erp-frontend/src/views/inventory/stocks/index.vue` |
| production/orders | ~1081 | `erp-frontend/src/views/production/orders/index.vue` |
| readiness/index.vue | ~1011 | `erp-frontend/src/views/system/readiness/index.vue` |
| api/inventory.ts / finance.ts | ~1100 | 前端 API 聚合文件 |

**规则**: 一次只拆一个热点；对外 API 与事务边界不变；全量相关测试必须绿。  
参考路线图：`docs/superpowers/specs/2026-05-27-optimization-roadmap-design.md`。

### C5. Redis 缓存深化  `NEEDS_SPEC` · P2

| | |
|--|--|
| **入口** | `common/cache/**` |
| **优先** | 字典 / 菜单树 / 权限快照 Cache-Aside；key 强制租户/账套隔离 |
| **验收** | 失效一致性测试；命中不影响权限变更即时性（或明确 TTL+主动失效） |

### C6. OpenAPI / 前后端契约  `READY` · P2

| | |
|--|--|
| **后端** | springdoc 已依赖；巩固 `docs/api-conventions.md` |
| **前端** | 现有 `npm run check:contracts`；可考虑从 OpenAPI 生成类型（分阶段） |
| **验收** | 契约检查进 CI；破坏性字段变更失败可阻断 |

### C7. 迁移与种子安全网  `READY` · P1

| | |
|--|--|
| **教训** | 单号规则/菜单显式 id 碰撞（V99/V100） |
| **动作** | ① 新增迁移前查 `sys_sequence_rule`/`sys_menu`/`sys_role_menu` 已占用 id；② 保留/扩展 `ManualVoucherSeedCollisionMigrationTest` 类模式到菜单种子；③ `docs/migrations-history.md` 持续更新 |
| **入口** | `src/main/resources/db/migration/`、`src/main/java/db/migration/` |

### C8. 联调库与环境卫生  `READY` · 运维工程

| | |
|--|--|
| **默认** | `application-local.yml` → `erp_codex_runtime`（不要用脏库 `erp`） |
| **文档** | `docs/local-runtime-integration.md`、`README.md` |
| **账号** | admin / LocalAdmin123；ui-smoke 用 `runtime_smoke`（重建库后手工赋 ERP_ADMIN=3002） |
| **验收** | 新成员按 README 能 30 分钟内登录 + 打开菜单 |

### Track C 建议排序

```text
C1 文档归档 → C7 迁移安全网 → C2 前端测试 → C3 只读权限一致
→ C6 契约 → C4 按痛点拆巨石 → C5 缓存
```

---

## 跨轨依赖与「不要做」

```text
Track A 未签字完成前
  ├─ 可以做：C1/C2/C7、B1 测试加固（不改口径）
  ├─ 谨慎做：B2–B7 新业务（扩大签字面）
  └─ 不要做：为「看起来完整」而开多账套/自由流程/移动端
```

**明确不做（除非改产品边界单独立项）**

- 多公司 / 多账套运营产品化  
- 多币种汇率  
- 自定义流程引擎  
- 固定资产 / 税务申报 / 合并报表  
- 移动端 APP  
- 复杂 BI  

**新增菜单/页面纪律**（两仓都要）

1. 后端 API + 权限码 + Flyway 菜单种子绑角色（如 3002）  
2. 前端 `router` + `api` + `views` + 写按钮 `v-permission`  
3. 需要时补 ui-smoke workflow  
4. 更新 `docs/WHAT_IS_MISSING.md` 一句结论  

---

## 本周可执行切片（建议）

| 日序 | 项 | 产出 |
|------|----|------|
| Day 1 | A2 确认证据仍有效 + 启动 A1 对接财务/质检 | 预检报告路径发给业务 |
| Day 1–2 | C1 归档过时 md | `docs/archive/...` + README 链接收敛 |
| Day 2 | C7 写「占用 id 检查」脚本或测试模板 | 防再撞种子 |
| Day 3 | B1 data-scope 双账号矩阵 + 可选 smoke | 安全基线加固 |
| Day 3–4 | C2 补 3–5 个 API 归一化测试 | 前端回归网 |
| Day 5 | A1 催签字；A3/A4 清单勾选 | 发布候选或 NO-GO 原因 |

---

## 立刻开始时默认队列

若未指定项，执行顺序固定为：

1. **A1/A2** 发布路径（人机协作）  
2. **C1** 文档归档（纯工程、零业务风险）  
3. **B1** 数据范围矩阵（半成品收口）  
4. 再开会选 B2 vs B4  

---

*生成：2026-07-17。状态变更时改本文件日期与对应条目标记，并同步 `WHAT_IS_MISSING.md` 结论段。*
