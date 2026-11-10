# 期末月结证据、库存估值与生产成本增强

## 交付范围

1. 期间锁定和结账在服务端重新执行月结检查，并把检查清单、动作、操作人、时间和指标固化到 `fin_period_close_snapshot` / `fin_period_close_snapshot_item`。
2. 结账要求更早期间已经结账，避免跳期结账；失败检查不能通过前端确认绕过。
3. 新增库存估值报表：按期间开始日、截止日、仓库、商品和关键字查询期初、入库、出库、期末数量/金额及平均单位成本。估值基于库存流水重算，带账套和仓库数据范围，支持 CSV 导出。
4. 新增生产成本报表：按生产工单展示材料实际成本、完工成本、在制金额、完工单位成本和成本状态，沿用生产工单的数据范围，支持 CSV 导出行数门禁。

## 口径

- 库存估值金额直接使用 `inv_txn.amount` 的入出库方向净额，不在本阶段引入历史成本重算、FIFO 层或标准成本差异分摊。
- 生产材料成本取工单材料行 `issued_amount`，完工成本取工单 `finished_amount`，在制金额为材料成本减完工成本；`COST_VARIANCE` 只提示事实差异，不自动调账。
- 期初和期末按流水发生时间计算，截止日包含当天；没有仓库范围的数据范围用户返回空集。

## 验收证据

- 后端期间快照、迁移和租户门禁：`AccountPeriodControllerTest`、`FlywayMigrationSmokeTest`、`TenantTableCoverageConfigurationTest`。
- 后端报表权限、数据范围、导出和服务拆分回归：报告模块专项测试。
- 前端 `useFinancePeriodActions`、`useReportList`、`useReportPresentation` 专项测试，type-check 和 production build 通过。
