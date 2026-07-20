# 工作流设计说明

## 当前实现方式

当前系统采用**嵌入式审批流程**设计，审批功能直接集成在各业务模块中，而非使用独立的工作流引擎。

## 已实现的审批功能

### 1. 采购订单审批
**控制器**: `PurchaseOrderController`  
**审批接口**:
- `POST /api/purchase/orders/{id}/submit` - 提交审批
- `POST /api/purchase/orders/{id}/approve` - 审批通过
- `POST /api/purchase/orders/{id}/reject` - 审批驳回

**状态流转**:
```
DRAFT → PENDING → APPROVED → COMPLETED
         ↓
      REJECTED
```

### 2. 销售订单审批
**控制器**: `SalesOrderController`  
**审批接口**:
- `POST /api/sales/orders/{id}/submit` - 提交审批
- `POST /api/sales/orders/{id}/approve` - 审批通过
- `POST /api/sales/orders/{id}/reject` - 审批驳回

**状态流转**:
```
DRAFT → PENDING → APPROVED → DELIVERING → COMPLETED
         ↓
      REJECTED
```

### 3. 费用审批
**控制器**: `ExpenseController`  
**审批接口**:
- `POST /api/finance/expenses/{id}/submit` - 提交审批
- `POST /api/finance/expenses/{id}/approve` - 审批通过
- `POST /api/finance/expenses/{id}/reject` - 审批驳回

**状态流转**:
```
DRAFT → PENDING → APPROVED → PAID
         ↓
      REJECTED
```

### 4. 凭证审批
**控制器**: `VoucherController`  
**审批接口**:
- `POST /api/finance/vouchers/{id}/approve` - 审批通过
- `POST /api/finance/vouchers/{id}/post` - 过账（相当于最终确认）

**状态流转**:
```
DRAFT → APPROVED → POSTED
         ↓
      CANCELLED
```

## 前端工作流API

前端有 `src/api/workflow.ts` 文件，定义了通用的工作流接口，但后端目前没有对应的统一工作流控制器。

这个API文件可能是为将来扩展准备的，或者用于调用各业务模块中分散的审批接口。

## 设计优势

### 当前嵌入式设计的优点

1. **简单直接** - 审批逻辑与业务逻辑紧密结合，易于理解和维护
2. **性能好** - 无需额外的工作流引擎开销
3. **灵活** - 每个业务模块可以根据自身特点定制审批流程
4. **开发快** - 不需要学习和集成复杂的工作流引擎

### 当前设计的局限

1. **重复代码** - 审批逻辑在各模块中重复
2. **扩展性差** - 添加新的审批环节需要修改多处代码
3. **缺乏统一监控** - 无法在一个地方查看所有待审批事项
4. **不支持复杂流程** - 难以实现多级审批、条件分支等复杂场景

## 升级方案建议

如果未来需要支持更复杂的审批场景，可以考虑以下方案：

### 方案A：轻量级工作流服务（推荐）

创建一个轻量级的工作流服务层，保留现有业务接口，内部调用工作流服务：

```java
@Service
public class WorkflowService {
    // 统一的审批流程管理
    public void submit(String businessType, Long businessId, String businessNo);
    public void approve(String businessType, Long businessId, String approver);
    public void reject(String businessType, Long businessId, String reason);
    
    // 查询待办
    public List<PendingTask> getMyPendingTasks();
}
```

**优点**:
- 保持现有API不变，前端无需改动
- 统一管理审批流程
- 支持更复杂的审批场景
- 可以添加审批历史、待办列表等功能

**工作量**: 中等（2-3周）

### 方案B：集成成熟工作流引擎

集成 Activiti、Flowable 或 Camunda 等工作流引擎。

**优点**:
- 功能强大，支持复杂流程
- 有可视化流程设计器
- 社区成熟，文档丰富

**缺点**:
- 学习曲线陡峭
- 系统复杂度增加
- 可能过度设计（对于当前需求）

**工作量**: 大（4-6周）

### 方案C：保持现状，逐步优化

保持当前嵌入式设计，通过以下方式优化：

1. 提取公共的审批基类和工具类
2. 添加统一的待办列表接口
3. 添加审批历史记录
4. 前端统一待办中心页面

**优点**:
- 改动最小
- 风险低
- 满足当前需求

**工作量**: 小（1周）

## 建议

**当前阶段建议**: 保持现有嵌入式设计（方案C），因为：
1. 系统刚开发完成，业务流程还在磨合中
2. 当前的审批需求相对简单
3. 避免过度设计

**未来升级时机**: 当出现以下情况时，考虑升级到方案A：
1. 需要多级审批（如：经理审批 → 总监审批 → 财务审批）
2. 需要条件分支（如：金额大于10万需要额外审批）
3. 需要统一的待办中心和审批监控
4. 审批逻辑变更频繁，需要可配置化

## 前端开发注意事项

前端调用审批接口时，应根据业务类型调用对应的接口：

```typescript
// 采购订单审批
import { submitPurchaseOrder, approvePurchaseOrder, rejectPurchaseOrder } from '@/api/purchase'

// 销售订单审批
import { submitSalesOrder, approveSalesOrder, rejectSalesOrder } from '@/api/sales'

// 费用审批
import { submitExpense, approveExpense, rejectExpense } from '@/api/finance'
```

**不要使用** `@/api/workflow.ts` 中的接口，因为后端没有对应实现。

## 总结

当前系统采用嵌入式审批设计，满足基本业务需求。各业务模块（采购、销售、费用、凭证）都实现了完整的审批流程。

前端的 `workflow.ts` 文件是预留的扩展接口，目前无需使用。建议在业务需求明确后，再决定是否升级到统一的工作流引擎。
