# 前端权限按钮收口 + 动态菜单 — 权限真值矩阵与实施

**日期**: 2026-07-12
**范围**: `erpServer`(V94 菜单种子)+ `erp-frontend`(动态菜单 + 按钮权限)
**决策**: 补后端菜单种子 → 完整后端 runtime-menu-tree 驱动侧边栏;按钮收口先做首批高风险写操作页。

## 已核实关键事实(load-bearing)

- 后端已有 `GET /api/auth/runtime-menu-tree`:SUPER_ADMIN 走 `buildTree(allMenus)` 返回**全树、不读 role_menu**;普通角色按 `role_menu + 祖先` 过滤。
- admin(user 4001)=SUPER_ADMIN(role 3001);ERP_ADMIN(3002)走 role_menu,历史种子都给 3002 绑。
- **因此:任何缺 MENU 节点的页面,admin 侧边栏也会消失** → V94 必须补齐全部缺失 MENU,不止首批。
- 权限来源:普通角色按钮码 = 其分配 BUTTON 节点的 `permission`;SUPER_ADMIN = `PermissionCodes.allPermissions()` 全集 → **给按钮加 `v-permission` 对 admin 一定放行,收口安全**。
- `sys_menu`/`sys_role_menu` 无租户列,全局共享。
- 号段:menu id 已占用至 5120 → V94 用 **5130+**;role_menu id 占用至 7164 → V94 用 **7170+**。
- 迁移写法:`INSERT ... ON DUPLICATE KEY UPDATE`(幂等),参照 V39。

## Path 不一致(以前端路由为准,V94 校正后端)

| 页面 | 前端路由 path | 后端现有 path | 处理 |
|---|---|---|---|
| 会计科目 | `/finance/subjects` | `/finance/account-subjects` | 校正后端 5031 → `/finance/subjects`,component `finance/subjects/index` |
| 导入任务 | `/system/imports` | `/imports/initial`(独立 CATALOG 5070) | 前端在 system 下;保留后端导入中心,**另补** system 下 imports MENU 指向前端路由 |

## 缺失 MENU/CATALOG 节点(admin 可见性硬需求 — V94 全补)

**缺失 CATALOG**:`MASTERDATA`(/masterdata)、`PURCHASE`(/purchase)、`SALES`(/sales)

**缺失 MENU**(path / component 对齐前端真实路由):
- masterdata:products、customers、suppliers、warehouses
- purchase:orders、receipts、returns
- sales:orders、deliveries、returns
- inventory(挂 5009):stocks、adjustments、checks、alerts
- finance(挂 5030):payments;校正 subjects path
- system(挂 5001):configs、imports
- reports:`/reports`(index)、`/reports/traces`
- dashboard 作为免权限首页,**不入菜单种子**(前端 layout 固定置顶,见 Phase 2)

## 首批按钮权限矩阵(Phase 3)

后端 `@PreAuthorize` 码即前端 `v-permission` 码,三方一致。

### sales/orders — `SalesOrderController`
create / update / submit / approve / unapprove / reject / cancel
→ `sales:order:{create,update,submit,approve,unapprove,reject,cancel}`

### sales/deliveries — `SalesDeliveryController`
create / update / cancel / post → `sales:delivery:{create,update,cancel,post}`

### sales/returns — `SalesReturnController`
create / update / cancel / post → `sales:return:{create,update,cancel,post}`

### purchase/receipts — `PurchaseReceiptController`
create / update / cancel / post(+export=view) → `purchase:receipt:{create,update,cancel,post}`

### purchase/returns — `PurchaseReturnController`
create / update / cancel / post → `purchase:return:{create,update,cancel,post}`

### inventory/adjustments — `InventoryAdjustmentController`
create / post / cancel → `inventory:adjustment:{create,post,cancel}`

### inventory/transfers — `InventoryTransferController`
create / post / cancel → `inventory:transfer:{create,post,cancel}`

### inventory/stocks — `InventoryStockQueryController`(纯查询)
仅 view;**只读页,不加按钮 guard**(reservation 手工释放属另一 controller,本轮不动)

### production/orders — `ProductionOrderController`
create / update / release / cancel / issue / complete / reverse-completion / return-materials
→ `production:order:{create,update,release,cancel,issue,complete,reverse-completion,return}`

### system/users — `UserController`
create / update / enable / disable / reset-password / assign-role
→ `system:user:{create,update,enable,disable,reset-password,assign-role}`

### system/roles — `RoleController`
create / update / enable / disable / assign-menu
→ `system:role:{create,update,enable,disable,assign-menu}`

## 首批 BUTTON 种子(V94,供 ERP_ADMIN/自定义角色授权)

对上述写操作码补 BUTTON 节点并绑 3002。masterdata/purchase-order 等其余模块 BUTTON 可后续批次补(不阻塞 admin,因 admin 走全集)。

## 验证口径

- 后端:`.\mvnw.cmd -B test` 全绿;新增 `MenuSeedCompletionConfigurationTest` 校验关键 path/component。
- 前端:`npm run lint / type-check / build / check:contracts`。
- 手动(干净库 `erp_codex_runtime`):admin 看全菜单+全按钮;造受限角色验证裁剪一致。

## 残留(后续批次)

- 剩余 ~30 页按钮收口;masterdata/purchase-order/finance 其余页 BUTTON 种子。
- `/reports` 与 exception 页的按钮细分。
- last-verified: 待 Phase 4 回填。

## Phase 4 验证结果（2026-07-12，last-verified）

**后端**：`./mvnw -B test` → **919 tests, 0 failures**（基线 916 + 新增 3 个 V94 校验）。V94 通过 `FlywayMigrationSmokeTest`（H2 全量迁移）。

**前端**：`npm run lint` / `type-check` / `build` / `check:contracts` 全部通过。

**运行时端到端**（干净联调库 `erp_codex_runtime`，local profile，V94 增量应用成功）：
- admin(SUPER_ADMIN)：`runtime-menu-tree` 68 个 path 节点，含 masterdata/purchase/sales/`/finance/subjects`(已校正)/`/finance/payments`(新增)；权限 192（全集）。
- 受限角色端到端：建 `SALES_VIEWER_T` 只分配 销售管理→销售订单→创建按钮 →
  - `runtime-menu-tree` 精确返回 `CATALOG /sales → MENU /sales/orders → BUTTON sales:order:create`，其余模块全部裁掉。
  - `permissions = [sales:order:create, sales:order:view]`，无 approve/submit/cancel。
  - 证明动态菜单白名单裁剪 + 按钮 `v-permission` 收口端到端生效。
- 联调用户/角色已清理（软删 + 清 user_role/role_menu）。

**发现并记录**：前端 `POST /system/users` 不接受 roleIds，角色须经 `PUT /users/{id}/roles` 单独分配（后端 `UserCreateRequest` 无 roleIds 字段）。前端用户管理页已用独立"角色"按钮走该接口，符合此约束。

## 首批实际改动清单

- 后端：`V94__menu_seed_completion.sql`（3 CATALOG + 21 MENU + 38 BUTTON + role_menu 绑定 3002 + 校正 FINANCE_SUBJECT path）；`FlywayMigrationSmokeTest` +3 测试方法。
- 前端：`api/auth.ts`(getRuntimeMenuTree)、`store/modules/menu.ts`(新建)、`layout/index.vue`(后端菜单白名单裁剪 + 回退)、`store/modules/user.ts`(登出 reset 菜单)。
- 前端按钮收口 11 页：sales/orders·deliveries·returns、purchase/receipts·returns、inventory/adjustments·transfers、production/orders、system/users·roles（inventory/stocks 为只读页，无写按钮）。
