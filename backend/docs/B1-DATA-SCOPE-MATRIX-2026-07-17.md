# B1 数据范围验收矩阵（2026-07-17）

**脚本**: `node scripts/data-scope-api-smoke.cjs`  
**报告**: `target/data-scope-api-smoke/report-*.json`  
**本轮结果**: **12/12 PASS**

## 矩阵

| ID | 场景 | 期望 | 结果 |
|----|------|------|------|
| S0 | admin GET 用户 data-scope | hasAll / effectiveAll | PASS |
| S1 | 建受限角色并复制菜单 | 绑定成功；脏绑定可被过滤 | PASS |
| S2 | 角色 scope = SELF | self=true, all=false | PASS |
| S3 | 建用户赋角色 | effectiveSelf=true | PASS |
| S4 | 受限用户登录 | 成功 | PASS |
| S5 | admin 采购列表对照 | 可列出 | PASS |
| S6 | SELF 列表不见他人 PO | seesForeign=false | PASS |
| S6b | SELF 可见本人 PO | seesOwn=true | PASS |
| S7 | SELF 详他人 PO | HTTP 403 | PASS |
| S8 | 角色 WAREHOUSE=4501 库存 | 仅主仓 | PASS |
| S8b | 受限库存无其它仓 | limitedHasOther=false | PASS |
| S9 | 恢复角色 ALL | 成功 | PASS |

## 代码入口

| 层 | 路径 |
|----|------|
| 规则引擎 | `common/security/DataScopeService.java` |
| 用户/角色分配 | `system/user/service/UserDataScopeService.java`、`system/role/service/RoleDataScopeService.java` |
| 权限种子 | `V106__data_scope_assign_button_permission_seed.sql` |
| 前端 UI | `erp-frontend/src/views/system/users/index.vue`、`roles/index.vue` |
| API | `erp-frontend/src/api/system.ts` |
| 单测 | `erp-frontend/src/api/system.data-scope.test.ts` |

## 验收结论

B1 **DONE**。数据范围在采购单据 SELF 与库存 WAREHOUSE 维度具备可重复 smoke；非 admin 跨权访问被拒绝。
