# 后端已实现但前端未对接的功能清单

**发现日期**: 2026-06-16  
**状态**: 待补充

---

## 📋 发现的功能

### 1. 采购订单模块

#### 后端已实现
```java
POST /api/purchase/orders/{id}/close
  - 关闭采购订单
  - 权限：HAS_PURCHASE_ORDER_CLOSE

GET /api/purchase/orders/{id}/trace
  - 采购订单跟踪
  - 权限：HAS_PURCHASE_ORDER_VIEW
  - 返回：PurchaseOrderTraceResponse
```

#### 前端状态
- ❌ 前端 `purchase.ts` 中**没有**定义这两个方法
- 需要添加

---

### 2. 生产订单模块

#### 后端已实现（完整）
```java
POST /api/production/orders                创建生产订单
PUT  /api/production/orders/{id}           更新生产订单
GET  /api/production/orders                列表查询
GET  /api/production/orders/{id}           详情查询
POST /api/production/orders/{id}/release   下达生产订单
POST /api/production/orders/{id}/cancel    取消生产订单
POST /api/production/orders/{id}/issue     生产领料 ✅
POST /api/production/orders/{id}/complete  生产完工 ✅
POST /api/production/orders/{id}/reverse-completion  完工红冲 ✅
POST /api/production/orders/{id}/return-materials    生产退料 ✅
```

#### 前端状态
- ✅ 前端 `production.ts` 中**已定义**所有方法
- ✅ API路径匹配
- ⚠️ 需要验证参数类型是否完全匹配

---

### 3. 导出功能

#### 前端已定义但后端未实现

**采购模块**:
```typescript
❌ GET /api/purchase/orders/export
❌ GET /api/purchase/receipts/export
❌ GET /api/purchase/returns/export
```

**库存模块**:
```typescript
❌ GET /api/inventory/balances/export
```

**财务模块**:
```typescript
❌ GET /api/finance/receivables/export
❌ GET /api/finance/payables/export
❌ GET /api/finance/ledger/export
```

**主数据模块**:
```typescript
❌ GET /api/masterdata/products/export
❌ GET /api/masterdata/customers/export
❌ GET /api/masterdata/suppliers/export
```

---

## 🎯 优先级建议

### P1 - 立即补充（工作量小，影响大）

**1. 采购订单关闭和跟踪**
- 工作量：10分钟
- 影响：提升采购管理能力
- 操作：在 `purchase.ts` 中添加2个方法

**2. 验证生产模块对接**
- 工作量：30分钟
- 影响：确保生产模块正常工作
- 操作：对比前后端参数类型，必要时调整

### P2 - 按需开发（工作量中等）

**3. 导出功能**
- 工作量：2-3小时（批量实现）
- 影响：提升用户体验
- 操作：
  - 选择最常用的3-5个导出功能
  - 使用统一的CSV导出工具类
  - 优先实现：采购订单、库存查询、应收应付

---

## 📝 实施计划

### 第一步：快速补充（30分钟）

1. ✅ 添加采购订单close和trace方法到前端
2. ✅ 验证生产模块的参数匹配度

### 第二步：导出功能（可选，2-3小时）

根据业务需求选择实现：
- 采购订单导出
- 库存查询导出
- 财务报表导出

---

## 🔍 详细对比

### 生产订单参数对比

#### 前端 production.ts 定义
```typescript
// 生产领料
export const issueProductionOrder = (id: number, data: ProductionIssueRequest)
  
// 生产完工
export const completeProductionOrder = (id: number, data: ProductionCompleteRequest)

// 红冲完工
export const reverseProductionCompletion = (id: number, data: { quantity: number; remark?: string })

// 生产退料
export const returnProductionMaterials = (id: number, data: ProductionReturnRequest)
```

#### 后端 ProductionOrderController 定义
```java
// 生产领料
@PostMapping("/{id}/issue")
public ApiResponse<ProductionOrderResponse> issue(
    @PathVariable Long id,
    @RequestBody(required = false) ProductionIssueRequest request
)

// 生产完工
@PostMapping("/{id}/complete")
public ApiResponse<ProductionOrderResponse> complete(
    @PathVariable Long id,
    @RequestBody(required = false) ProductionCompletionRequest request
)

// 红冲完工
@PostMapping("/{id}/reverse-completion")
public ApiResponse<ProductionOrderResponse> reverseCompletion(
    @PathVariable Long id,
    @RequestBody(required = false) ProductionCompletionReversalRequest request
)

// 生产退料
@PostMapping("/{id}/return-materials")
public ApiResponse<ProductionOrderResponse> returnMaterials(
    @PathVariable Long id,
    @RequestBody ProductionReturnRequest request
)
```

**结论**: 
- ✅ 路径完全匹配
- ⚠️ 参数类型名称略有差异，需要验证字段是否匹配
- 后端部分参数是 `required = false`，前端调用时需要注意

---

**最后更新**: 2026-06-16  
**状态**: 📋 待处理
