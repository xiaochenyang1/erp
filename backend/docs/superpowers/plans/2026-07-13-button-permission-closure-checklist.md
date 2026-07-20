# 按钮级权限收口 — 分批清单与跟踪

**创建**: 2026-07-13
**范围**: `erp-frontend` 视图动作按钮 `v-permission` 收口 + `erpServer` 对应 BUTTON 菜单种子
**性质**: 跟踪文档。本文件只列清单与依赖,**不含代码改动**;按批次执行时逐条勾选。

## 背景

首批高风险页已完成 `v-permission` 收口(sales orders/deliveries/returns、purchase receipts/returns、inventory adjustments/transfers、production/orders、system users/roles,以及 masterdata×4、finance expenses/periods/manual-vouchers、inventory alerts/checks/replenishment、purchase/orders、system depts/menus、exception rules/tickets 等已 DONE)。剩余约 **21 个页面** 的动作按钮仍未挂权限指令。本文件把剩余项整理为可分批执行的清单。

## 进度更新(2026-07-13 追加)

本轮完成"后端种子已就绪且已绑 ERP_ADMIN(3002)"的安全子批,前端已挂 `v-permission`:

- ✅ `system/user-sessions`(#12)撤销/撤销该用户 → `system:user-session:revoke`
- ✅ `system/attachments`(#9)上传 → `system:attachment:manage`;删除 → `system:attachment:delete`
- ✅ `production/boms`(#5)新增/编辑 → `production:bom:manage`
- ✅ `workflow/configs`(#13)保存配置 → `workflow:config:update`
- ✅ `system/readiness`(#11)新建运行单/记录预检/证据/结果/验收项 → `system:readiness:manage`;决策 → `system:readiness:decide`
- 另:新增的 `production/work-centers`、`production/routings` 两页自带按钮级 `v-permission`(随页面一并落地)。

已复核这些页对应的 BUTTON 菜单节点均已在 V94/V95/V98 播种并绑 3002。收口后 admin(SUPER_ADMIN)不受影响,ERP_ADMIN 按已绑权限正常显示。

**2026-07-14 收尾(批 1c + #4,写操作全部收口)**:
- ✅ #10 `system/notifications`(markAllRead/markRead):后端补 `system:notification:manage` 裸码+`@PreAuthorize`,V102 补 BUTTON 种子(5321)绑 3002,前端两按钮已挂 `v-permission`。
- ✅ #14 `workflow/tasks`(approve/reject):后端补 `workflow:approve`/`workflow:reject` 裸码+`@PreAuthorize`,V102 补 BUTTON 种子(5322/5323)绑 3002,前端列表+详情弹窗按钮已挂。
- ✅ #4 `inventory/stocks` 手工释放预留:裸码/`@PreAuthorize`/BUTTON 节点(5018,V33 起)本就存在,只是从未绑 3002;V103 补 role_menu 绑定(7334),前端列表+详情弹窗两处释放按钮已挂 `v-permission`。
- ⏸️ 维持不做:`system/imports`、`system/logs`、`finance/payables|receivables|ledger`、`reports` 的导出/单动作权限码与路由 `view` 码相同,再挂无更细收益;#21 SLA 策略后端仅 manage/view 两码未细分。

至此**所有真实写操作按钮已完成 `v-permission` 收口**,剩余仅为与 view 码等价的导出/查询动作。

## 两个前提口径(执行时必读)

1. **admin 不受影响**:`admin`=SUPER_ADMIN(role 3001) 走 runtime-menu-tree 全树、`PermissionCodes.allPermissions()` 全放行,任何 `v-permission` 对 admin 一定通过。收口影响的是 **ERP_ADMIN(3002) 及普通角色**。
2. **前端挂指令前,后端必须先有 BUTTON 种子并绑 3002**:普通角色的按钮权限码来自其分配的 BUTTON 菜单节点(`UserPermissionService.loadPermissions`)。若某页权限码尚无 BUTTON 种子,给它挂 `v-permission` 会让 3002 误隐藏该按钮。故下表 **「后端种子」列为 `待补`** 的页,必须在同批次先补迁移(menu id 顺延 5290+ 之后的下一段、role_menu 顺延 7300+ 之后,QC 迁移 V96/V97 用完后接续),再改前端。

> 权限码来源:后端 `common/security/*PermissionCodes.java` 现有裸码。V94/V95 已补种子的模块见「后端种子=已就绪」。

---

## 第一批 — 完全缺失、含真实写操作(14 项,最高优先级)

| # | 视图文件 | 动作按钮 | 建议权限码(后端现有裸码) | 后端种子 |
|---|---|---|---|---|
| 1 | `finance/funds/index.vue` | submitAccount/Statement、match、unmatch | `finance:fund:manage`、`finance:fund:reconcile` | ✅ 已补(V98) |
| 2 | `finance/payments/index.vue` | createPayment/Receipt、submit、cancel | `finance:payment:create`/`:cancel`、`finance:receipt:create`/`:cancel` | ✅ 已补(V98) |
| 3 | `finance/subjects/index.vue` | add/addChild/edit/enable/disable | `finance:subject:manage` | ✅ 已补(V98) |
| 4 | `inventory/stocks/index.vue` | 手工释放预留 release | `inventory:reservation:release` | 待补 |
| 5 | `production/boms/index.vue` | add/edit/addItem/deleteItem/submit | `production:bom:manage` | 待补 |
| 6 | `system/configs/index.vue` | 配置 CRUD + 流水号规则 CRUD | `system:config:*`、`system:sequence-rule:*` | **已就绪(V95)** ✅ 前端已挂 |
| 7 | `system/dicts/index.vue` | 字典/字典项 CRUD | `system:dict:*` | **已就绪(V95)** ✅ 前端已挂 |
| 8 | `system/imports/index.vue` | commit、exportErrors、downloadTemplate | `import:init:manage` | 待补 |
| 9 | `system/attachments/index.vue` | delete、upload | `system:attachment:delete`、`system:attachment:manage` | 待补 |
| 10 | `system/notifications/index.vue` | markAllRead、markRead | 需新增码(现仅 `system:notification:view`) | **码+种子均待补** |
| 11 | `system/readiness/index.vue` | recordPreflight/Evidence/Item/Result/Run、submitDecision | `system:readiness:manage`、`system:readiness:decide` | 待补 |
| 12 | `system/user-sessions/index.vue` | revoke、revokeUser | `system:user-session:revoke` | 待补 |
| 13 | `workflow/configs/index.vue` | submitConfig、addNode/removeNode、addApprover | `workflow:config:update` | 待补 |
| 14 | `workflow/tasks/index.vue` + `workflow/records/index.vue` | approve/reject/confirm、withdraw | 需新增 approve/reject 码(现仅 `workflow:view`/`workflow:withdraw`) | **码+种子均待补** |

> 注 #10/#14:后端权限码尚不完整(通知无 read 码;审批无 approve/reject 码,`WorkflowController` 的 approve/reject 端点目前仅 `workflow:view` 或方法级校验)。这两项收口需先在 `NotificationPermissionCodes`/`WorkflowPermissionCodes` 补裸码 + `@PreAuthorize`,再补种子,最后改前端。

## 第二批 — 仅导出/单动作(5 项,中优先级)

| # | 视图文件 | 动作 | 权限码 | 后端种子 |
|---|---|---|---|---|
| 15 | `finance/payables/index.vue` | export | `finance:payable:view` | 待补 |
| 16 | `finance/receivables/index.vue` | export | `finance:receivable:view` | 待补 |
| 17 | `finance/ledger/index.vue` | export | `finance:ledger:view` | 待补 |
| 18 | `system/logs/index.vue` | export | `system:log:view` | 待补 |
| 19 | `reports/index.vue`(+`reports/traces` 的 submitTimelineComment) | export、评论 | `report:view` | 待补 |

> 导出/评论按钮多数已被 `view` 码覆盖,可用 view 码收口;是否单列 export 码按批次决定。

## 第三批 — 部分覆盖补齐(2 项,低优先级)

| # | 视图文件 | 现状 | 补齐权限码 | 后端种子 |
|---|---|---|---|---|
| 20 | `system/posts/index.vue` | 8 按钮仅 1 gated | `system:post:create`/`update`/`enable`/`disable` | **已就绪(V95)** ✅ 前端已挂 |
| 21 | `exception-sla-policies/index.vue` | 5 按钮仅 1 gated | `exception-sla-policy:manage` | 待补(仅 manage/view 两码,BUTTON 种子未细分) |

## 无需处理

`finance/vouchers`(只读)、`system/observability`(只读)、`dashboard`(纯导航)、`login`、`error/404`。

---

## 建议执行节奏

1. **批 1a(零后端改动)**:#6/#7/#20 —— 种子已就绪,直接前端挂 `v-permission`。
2. **批 1b(补种子即可)**:#1–#5、#8/#9/#11/#12/#13、#15–#19、#21 —— 后端权限码已有,只需补 BUTTON 菜单种子迁移 + 绑 3002,再改前端。
3. **批 1c(需补后端码)**:#10 通知 read、#14 审批 approve/reject —— 先补 `*PermissionCodes` 裸码 + `@PreAuthorize`,再补种子,再改前端。

每批完成后:前端 `npm run type-check && npm run build`;后端 `.\mvnw.cmd -B test`;干净库启动确认 runtime-menu-tree 对 3002 正确返回新 BUTTON 节点。
