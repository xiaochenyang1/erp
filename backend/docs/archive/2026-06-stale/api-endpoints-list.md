# ERP系统API接口清单

**生成日期**: 2026-06-12  
**后端API总数**: 264个端点  
**Controller数量**: 49个  

---

## 📊 模块分布

### 1. 认证授权模块（Auth）
- `/api/auth/login` - 登录
- `/api/auth/logout` - 登出
- `/api/auth/refresh` - 刷新Token
- `/api/auth/change-password` - 修改密码

### 2. 用户管理模块（User）
- `/api/system/users` - 用户列表
- `/api/system/users/{id}` - 用户详情
- `/api/system/users` - 创建用户
- `/api/system/users/{id}` - 更新用户
- `/api/system/users/{id}` - 删除用户
- `/api/system/users/{id}/enable` - 启用用户
- `/api/system/users/{id}/disable` - 禁用用户
- `/api/system/users/{id}/reset-password` - 重置密码
- `/api/system/users/{id}/roles` - 分配角色

### 3. 角色管理模块（Role）
- `/api/system/roles` - 角色列表
- `/api/system/roles/{id}` - 角色详情
- `/api/system/roles` - 创建角色
- `/api/system/roles/{id}` - 更新角色
- `/api/system/roles/{id}` - 删除角色
- `/api/system/roles/{id}/permissions` - 分配权限

### 4. 部门管理模块（Dept）
- `/api/system/depts` - 部门列表（树形）
- `/api/system/depts/{id}` - 部门详情
- `/api/system/depts` - 创建部门
- `/api/system/depts/{id}` - 更新部门
- `/api/system/depts/{id}` - 删除部门

### 5. 岗位管理模块（Post）
- `/api/system/posts` - 岗位列表
- `/api/system/posts/{id}` - 岗位详情
- `/api/system/posts` - 创建岗位
- `/api/system/posts/{id}` - 更新岗位
- `/api/system/posts/{id}` - 删除岗位

### 6. 菜单管理模块（Menu）
- `/api/system/menus` - 菜单列表（树形）
- `/api/system/menus/{id}` - 菜单详情
- `/api/system/menus` - 创建菜单
- `/api/system/menus/{id}` - 更新菜单
- `/api/system/menus/{id}` - 删除菜单

### 7. 数据字典模块（SystemDict）
- `/api/system/dicts` - 字典类型列表
- `/api/system/dicts/{type}` - 字典详情
- `/api/system/dicts` - 创建字典
- `/api/system/dicts/{type}` - 更新字典
- `/api/system/dicts/{type}/items` - 字典项列表

### 8. 系统配置模块（SystemConfig）
- `/api/system/configs` - 配置列表
- `/api/system/configs/{code}` - 配置详情
- `/api/system/configs` - 创建配置
- `/api/system/configs/{code}` - 更新配置

### 9. 系统日志模块（SystemLog）
- `/api/system/logs` - 操作日志列表
- `/api/system/logs/{id}` - 日志详情
- `/api/system/logs/export` - 导出日志

### 10. 采购订单模块（PurchaseOrder）
- `/api/purchase/orders` - 订单列表
- `/api/purchase/orders/{id}` - 订单详情
- `/api/purchase/orders` - 创建订单
- `/api/purchase/orders/{id}` - 更新订单
- `/api/purchase/orders/{id}` - 删除订单
- `/api/purchase/orders/{id}/submit` - 提交审批
- `/api/purchase/orders/{id}/approve` - 审批通过
- `/api/purchase/orders/{id}/unapprove` - 反审核采购订单（权限：`purchase:order:unapprove`；仅允许已审核且未入库订单）
- `/api/purchase/orders/{id}/reject` - 审批驳回
- `/api/purchase/orders/{id}/cancel` - 取消订单
- `/api/purchase/orders/{id}/close` - 关闭订单
- `/api/purchase/orders/{id}/trace` - 订单跟踪
- `/api/purchase/orders/export` - 导出订单
- `/api/purchase/orders/import` - 导入订单

### 11. 采购收货模块（PurchaseReceipt）
- `/api/purchase/receipts` - 收货单列表
- `/api/purchase/receipts/{id}` - 收货单详情
- `/api/purchase/receipts` - 创建收货单
- `/api/purchase/receipts/{id}` - 更新收货单

### 12. 采购退货模块（PurchaseReturn）
- `/api/purchase/returns` - 退货单列表
- `/api/purchase/returns/{id}` - 退货单详情
- `/api/purchase/returns` - 创建退货单

### 13. 销售订单模块（SalesOrder）
- `/api/sales/orders` - 订单列表
- `/api/sales/orders/{id}` - 订单详情
- `/api/sales/orders` - 创建订单
- `/api/sales/orders/{id}` - 更新订单
- `/api/sales/orders/{id}` - 删除订单
- `/api/sales/orders/{id}/submit` - 提交审批
- `/api/sales/orders/{id}/approve` - 审批通过
- `/api/sales/orders/{id}/unapprove` - 反审核销售订单（权限：`sales:order:unapprove`；仅允许已审核且未出库订单）
- `/api/sales/orders/{id}/reject` - 审批驳回
- `/api/sales/orders/{id}/cancel` - 取消订单
- `/api/sales/orders/export` - 导出订单

### 14. 销售发货模块（SalesDelivery）
- `/api/sales/deliveries` - 发货单列表
- `/api/sales/deliveries/{id}` - 发货单详情
- `/api/sales/deliveries` - 创建发货单

### 15. 销售退货模块（SalesReturn）
- `/api/sales/returns` - 退货单列表
- `/api/sales/returns/{id}` - 退货单详情
- `/api/sales/returns` - 创建退货单

### 16. 库存查询模块（InventoryStockQuery）
- `/api/inventory/stocks` - 库存列表
- `/api/inventory/stocks/{productId}` - 商品库存详情
- `/api/inventory/stocks/movements` - 出入库记录
- `/api/inventory/stocks/export` - 导出库存

### 17. 库存调整模块（InventoryAdjustment）
- `/api/inventory/adjustments` - 调整单列表
- `/api/inventory/adjustments/{id}` - 调整单详情
- `/api/inventory/adjustments` - 创建调整单

### 18. 库存盘点模块（InventoryStockCheck）
- `/api/inventory/checks` - 盘点单列表
- `/api/inventory/checks/{id}` - 盘点单详情
- `/api/inventory/checks` - 创建盘点单
- `/api/inventory/checks/{id}/complete` - 完成盘点

### 19. 库存调拨模块（InventoryTransfer）
- `/api/inventory/transfers` - 调拨单列表
- `/api/inventory/transfers/{id}` - 调拨单详情
- `/api/inventory/transfers` - 创建调拨单

### 20. 库存预警模块（InventoryAlert）
- `/api/inventory/alerts` - 库存预警列表
- `/api/inventory/alerts/low-stock` - 低库存预警

### 21. 应收账款模块（Receivable）
- `/api/finance/receivables` - 应收账款列表
- `/api/finance/receivables/{id}` - 应收详情
- `/api/finance/receivables/summary` - 应收汇总

### 22. 应付账款模块（Payable）
- `/api/finance/payables` - 应付账款列表
- `/api/finance/payables/{id}` - 应付详情
- `/api/finance/payables/summary` - 应付汇总

### 23. 收款单模块（Receipt）
- `/api/finance/receipts` - 收款单列表
- `/api/finance/receipts/{id}` - 收款单详情
- `/api/finance/receipts` - 创建收款单
- `/api/finance/receipts/{id}/approve` - 审批收款单

### 24. 付款单模块（Payment）
- `/api/finance/payments` - 付款单列表
- `/api/finance/payments/{id}` - 付款单详情
- `/api/finance/payments` - 创建付款单
- `/api/finance/payments/{id}/approve` - 审批付款单

### 25. 总账模块（FinanceLedger）
- `/api/finance/ledger` - 总账列表
- `/api/finance/ledger/summary` - 总账汇总
- `/api/finance/ledger/export` - 导出总账

### 26. 凭证模块（Voucher）
- `/api/finance/vouchers` - 凭证列表
- `/api/finance/vouchers/{id}` - 凭证详情
- `/api/finance/vouchers` - 创建凭证
- `/api/finance/vouchers/{id}/approve` - 审批凭证

### 27. 会计期间模块（AccountPeriod）
- `/api/finance/periods` - 会计期间列表
- `/api/finance/periods/current` - 当前期间
- `/api/finance/periods/{id}/close` - 关闭期间
- `/api/finance/periods/{id}/reopen` - 重开期间

### 28. 会计科目模块（AccountSubject）
- `/api/finance/subjects` - 科目列表（树形）
- `/api/finance/subjects/{id}` - 科目详情
- `/api/finance/subjects` - 创建科目

### 29. 费用报销模块（Expense）
- `/api/finance/expenses` - 报销单列表
- `/api/finance/expenses/{id}` - 报销单详情
- `/api/finance/expenses` - 创建报销单
- `/api/finance/expenses/{id}/submit` - 提交审批

### 30. 资金管理模块（Fund）
- `/api/finance/funds` - 资金账户列表
- `/api/finance/funds/{id}` - 账户详情
- `/api/finance/funds/{id}/balance` - 账户余额

### 31. 生产订单模块（ProductionOrder）
- `/api/production/orders` - 生产订单列表
- `/api/production/orders/{id}` - 订单详情
- `/api/production/orders` - 创建订单
- `/api/production/orders/{id}` - 更新订单
- `/api/production/orders/{id}/start` - 开始生产
- `/api/production/orders/{id}/complete` - 完成生产

### 32. BOM管理模块（ProductionBom）
- `/api/production/boms` - BOM列表
- `/api/production/boms/{id}` - BOM详情
- `/api/production/boms` - 创建BOM
- `/api/production/boms/{id}` - 更新BOM

### 33. 商品管理模块（Product）
- `/api/master/products` - 商品列表
- `/api/master/products/{id}` - 商品详情
- `/api/master/products` - 创建商品
- `/api/master/products/{id}` - 更新商品
- `/api/master/products/{id}` - 删除商品
- `/api/master/products/export` - 导出商品
- `/api/master/products/import` - 导入商品

### 34. 客户管理模块（Customer）
- `/api/master/customers` - 客户列表
- `/api/master/customers/{id}` - 客户详情
- `/api/master/customers` - 创建客户
- `/api/master/customers/{id}` - 更新客户
- `/api/master/customers/{id}` - 删除客户

### 35. 供应商管理模块（Supplier）
- `/api/master/suppliers` - 供应商列表
- `/api/master/suppliers/{id}` - 供应商详情
- `/api/master/suppliers` - 创建供应商
- `/api/master/suppliers/{id}` - 更新供应商

### 36. 仓库管理模块（Warehouse）
- `/api/master/warehouses` - 仓库列表
- `/api/master/warehouses/{id}` - 仓库详情
- `/api/master/warehouses` - 创建仓库
- `/api/master/warehouses/{id}` - 更新仓库

### 37. 工作流模块（Workflow）
- `/api/workflow/tasks` - 我的待办
- `/api/workflow/tasks/{id}` - 任务详情
- `/api/workflow/tasks/{id}/approve` - 审批通过
- `/api/workflow/tasks/{id}/reject` - 审批驳回
- `/api/workflow/instances/{id}` - 流程实例
- `/api/workflow/instances/{id}/history` - 审批历史

### 38. 工作流配置模块（WorkflowApprovalConfig）
- `/api/workflow/configs` - 流程配置列表
- `/api/workflow/configs/{id}` - 配置详情
- `/api/workflow/configs` - 创建配置

### 39. 导入管理模块（Import）
- `/api/import/templates/{type}` - 下载模板
- `/api/import/jobs/{type}/preview` - 预览导入
- `/api/import/jobs` - 导入任务列表
- `/api/import/jobs/{id}` - 任务详情
- `/api/import/jobs/{id}/commit` - 提交导入
- `/api/import/jobs/{id}/error-rows/export` - 导出错误行

### 40. 报表模块（Report）
- `/api/reports/purchase` - 采购报表
- `/api/reports/sales` - 销售报表
- `/api/reports/inventory` - 库存报表
- `/api/reports/finance` - 财务报表

### 41. 附件管理模块（Attachment）
- `/api/attachments` - 附件列表
- `/api/attachments/upload` - 上传附件
- `/api/attachments/{id}` - 下载附件
- `/api/attachments/{id}` - 删除附件

### 42. 通知管理模块（Notification）
- `/api/notifications` - 通知列表
- `/api/notifications/{id}` - 通知详情
- `/api/notifications/{id}/read` - 标记已读
- `/api/notifications/read-all` - 全部已读

### 43. 序列号管理模块（SequenceRule）
- `/api/system/sequences` - 序列规则列表
- `/api/system/sequences/{type}` - 规则详情
- `/api/system/sequences` - 创建规则

### 44. 用户会话模块（UserSession）
- `/api/system/sessions` - 在线用户列表
- `/api/system/sessions/{id}/kick` - 强制下线

### 45. 个人中心模块（Profile）
- `/api/profile` - 个人信息
- `/api/profile` - 更新个人信息
- `/api/profile/password` - 修改密码
- `/api/profile/avatar` - 上传头像

### 46. 监控模块（Observability/Health）
- `/actuator/health` - 健康检查
- `/actuator/prometheus` - Prometheus指标
- `/actuator/health/business` - 业务健康检查

### 47. 生产就绪检查模块（Readiness）
- `/api/readiness/checks` - 就绪检查

---

## 📊 API统计

| 模块类型 | Controller数量 | 预估API数量 |
|---------|---------------|-------------|
| 系统管理 | 10 | ~60 |
| 采购管理 | 3 | ~30 |
| 销售管理 | 3 | ~30 |
| 库存管理 | 5 | ~40 |
| 财务管理 | 9 | ~60 |
| 生产管理 | 2 | ~15 |
| 主数据 | 4 | ~25 |
| 工作流 | 2 | ~15 |
| 其他 | 11 | ~25 |
| **总计** | **49** | **~264** |

---

## 🎯 前端开发优先级

### P0 - 必需（第1-3批）
- ✅ 认证授权
- ✅ 用户管理
- ✅ 采购订单
- ✅ 销售订单
- ✅ 库存查询

### P1 - 重要（第4-6批）
- ✅ 财务管理
- ✅ 客户/供应商
- ✅ 商品管理
- ✅ 仓库管理
- ✅ 工作流审批

### P2 - 可选（第7-8批）
- 生产管理
- 报表查询
- 系统配置
- 数据字典

### P3 - 增强（第9-10批）
- 通知中心
- 个人中心
- 在线用户
- 附件管理

---

## 📝 备注

- 本清单基于Controller扫描生成
- 具体API参数和返回值需参考后端代码
- 建议使用Swagger/OpenAPI自动生成详细文档

---

**生成时间**: 2026-06-12  
**文档版本**: v1.0
