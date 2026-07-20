# 应收/应付账龄分析（2026-07-17）

## 口径

| 项 | 规则 |
|----|------|
| 未结单据 | status ∉ SETTLED/CANCELLED/CLOSED，且剩余金额 > 0 |
| 账龄天 | asOfDate - bizDate（负值按 0） |
| 分段 | 0-30 / 31-60 / 61-90 / 90+ |
| 列表 | 应收/应付各逾期 TOP20（按账龄天、金额倒序） |

## 接口

`GET /api/finance/aging?asOfDate=yyyy-MM-dd`  
权限：`finance:aging:view`  
菜单：`/finance/aging`（V112，绑 ERP_ADMIN）

## 前端

`erp-frontend/src/views/finance/aging/index.vue`

## 测试

`FinanceAgingServiceTest` 1 case 绿。
