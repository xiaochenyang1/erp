# B2 销售信用额度 · 完成确认（2026-07-17）

## 结论：**DONE**（代码已存在，非空壳）

| 能力 | 入口 |
|------|------|
| 客户信用额度主数据 | `CustomerEntity.creditLimit`；前端 `views/masterdata/customers` |
| 审批闸门 | `SalesCreditEvaluator.assertWithinCreditLimit` |
| 提交闸门 | `SalesOrderService` 提交路径调用 |
| 接入点 | `SalesOrderService` 提交/审批路径调用 |
| 表单预览 | `POST /api/sales/orders/credit-preview`；前端 `views/sales/orders` |
| 单测 | `src/test/java/com/tuowei/erp/sales/SalesCreditEvaluatorTest.java` |

## 口径

- 敞口 = 净未结应收 + 其它已审批未发货金额 + 本单含税金额
- `creditLimit` 空或 ≤0：**不限额**（跳过）
- 超限：抛 `IllegalArgumentException`，阻断提交/审批
- 销售订单表单预览显示当前敞口、本单金额、提交后敞口与可用额度

## 验收

- 单测覆盖超限/不限额/部分发货折算与预览口径（见现有 test）
- 业务验收：将客户额度设为很小，销售订单页应显示超限预警；提交与审批大额 SO 都应失败

无需再开新模块。
