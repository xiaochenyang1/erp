# 数据范围运营验收矩阵

**版本**：B8 / 2026-08-28
**账号**：`ADMIN_ALL`（`ALL` 对照）与 `LIMITED_SCOPE`（非管理员，按场景授权 `SELF` 或单仓）

| 入口 | 数据对象 | 列表/查询 | 详情 | 导出/报表 | 越权期望 | 自动化证据 |
|---|---|---|---|---|---|---|
| 采购 | 采购订单 | scope wrapper | `assertCanViewPurchaseOrder` | 订单 CSV、订单报表 | 空列表或 403；不得加载明细 | `PurchaseOrderQueryServiceTest`、`OrderReportQueryServiceTest`、API smoke S6PO/S7PO |
| 采购 | 采购入库 | scope wrapper | `assertCanViewPurchaseReceipt` | 入库 CSV | 空列表或 403 | `PurchaseReceiptQueryServiceTest`、API smoke S6PR |
| 采购 | 采购退货 | scope wrapper | `assertCanViewPurchaseReturn` | 退货 CSV | 空列表或 403 | `PurchaseReturnQueryServiceTest`、API smoke S6PT |
| 销售 | 销售订单 | scope wrapper | `assertCanViewSalesOrder` | 销售订单报表、CSV | 空列表或 403 | 销售查询测试、`OrderReportQueryServiceTest`、API smoke S6SO/S7SO |
| 销售 | 销售出库 | scope wrapper | `assertCanViewSalesDelivery` | 页面列表 | 空列表或 403 | 销售查询测试、API smoke S6SD |
| 销售 | 销售退货 | scope wrapper | `assertCanViewSalesReturn` | 页面列表 | 空列表或 403 | 销售查询测试、API smoke S6ST |
| 库存 | 库存余额/流水 | 仓库 scope wrapper | 仓库断言 | 库存报表/CSV | 未授权仓库不得出现 | `ReportQueryServiceInventoryScopeTest`、API smoke S9BA/S9TX/S9BD/S9TD |
| 报表 | 订单报表 | 与页面相同 wrapper | 不提供隐藏详情 | CSV 与分页同口径 | 不得通过导出绕过 | `OrderReportQueryServiceTest` |
| 报表 | 业务追踪 | 可见业务号关联 | 关联对象逐项受限 | 不新增绕过入口 | 输入隐藏编号返回空 | `BusinessTraceServiceTest` |
| 首页 | 运营看板 | 复用订单/财务 scope wrapper，库存预警与 TOP SKU 下推仓库/创建人范围 | N/A | 聚合指标 | SELF/DEPT/POST/WAREHOUSE/ALL 按当前 principal 快照过滤；无范围默认空结果 | `OperationsDashboardServiceTest`、`InventoryAlertQueryServiceTest` |

## 负例清单

- `LIMITED_SCOPE` 读取 `ADMIN_ALL` 创建的采购订单、销售订单、入库/出库/退货详情：`403`（S6* b）。
- `LIMITED_SCOPE` 对未授权仓库的库存余额、流水详情和导出：`403` 或空结果（S9BD/S9TD）。
- `LIMITED_SCOPE` 对他人草稿执行作废：`403`，且状态、明细和库存流水不变（S8）。
- `LIMITED_SCOPE` 直接按隐藏采购单号追踪：不返回单据、日志或二级关联；ALL 对照账号仍可见（S7TR）。
