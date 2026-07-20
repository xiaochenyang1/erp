# 销售价目（2026-07-17）

## 口径

| 项 | 规则 |
|----|------|
| 表 | `md_sales_price`（V111） |
| 通用价 | `customer_id IS NULL` |
| 客户专价 | `customer_id` 非空 |
| 取价优先级 | 客户专价 > 商品通用价 > 无匹配（不校验） |
| 校验时机 | 销售订单 **创建 / 编辑 / 提交** |
| 规则 | 单价 `< min_price` 则拒绝；`list_price` 仅参考，须 ≥ `min_price` |
| 权限 | `sales:price:view` / `sales:price:manage` |
| 菜单 | `/sales/prices`，绑 ERP_ADMIN(3002) |

## 入口

- 后端：`/api/sales/prices`、`/api/sales/prices/resolve`
- 前端：`erp-frontend/src/views/sales/prices/index.vue`
- 单测：`SalesPriceEvaluatorTest`（4）

## 本地 API 冒烟（已通）

客户专价 min=45：下单价 40 被拒，45 创建 SO 成功。

## 前端订单联动

销售订单选商品时调用 `/sales/prices/resolve` 带出标准价，行下显示最低价与价目类型；保存前预检。
