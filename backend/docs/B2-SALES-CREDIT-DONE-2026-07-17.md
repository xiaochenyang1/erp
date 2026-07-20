# B2 销售信用额度 · 完成确认（2026-07-17）

## 结论：**DONE**（代码已存在，非空壳）

| 能力 | 入口 |
|------|------|
| 客户信用额度主数据 | `CustomerEntity.creditLimit`；前端 `views/masterdata/customers` |
| 审批闸门 | `SalesCreditEvaluator.assertWithinCreditLimit` |
| 接入点 | `SalesOrderService` 审批路径调用 |
| 单测 | `src/test/java/com/tuowei/erp/sales/SalesCreditEvaluatorTest.java` |

## 口径

- 敞口 = 净未结应收 + 其它已审批未发货金额 + 本单含税金额
- `creditLimit` 空或 ≤0：**不限额**（跳过）
- 超限：抛 `IllegalArgumentException`，阻断审批

## 验收

- 单测覆盖超限/不限额/部分发货折算（见现有 test）
- 业务验收：将客户额度设为很小，提交并审批大额 SO 应失败

无需再开新模块。
