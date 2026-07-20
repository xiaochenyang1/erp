# 生产工序报工（2026-07-17）

## 口径

| 项 | 规则 |
|----|------|
| 生成时机 | 工单 **下达(release)** 时，按 BOM 绑定的 **ACTIVE 工艺路线** 复制工序 |
| 无工艺路线 | 不生成工序，完工行为与原来一致 |
| 报工 | 累加 reported/qualified/scrap；合格量 ≥ 计划量 → 工序 DONE |
| 完工闸门 | 若存在工序：全部 DONE，且各工序合格量 ≥ 本次完工数量 |

## API

- `GET /api/production/orders/{id}/operations`
- `POST /api/production/orders/{id}/operations/{operationId}/report`
- 权限：`production:order:report`（V113 按钮种子绑 ERP_ADMIN）

## 前端

生产工单列表 → **工序报工** 对话框。

## 测试

`ProductionOperationServiceTest` 4 绿；既有 TenantBoundary 5 绿。
