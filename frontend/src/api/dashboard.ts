import { request } from '@/utils/request'

export interface OperationsDashboardSummary {
  pendingApprovals: number
  lowStockAlerts: number
  openReceivables: number
  openReceivableAmount: number
  openPayables: number
  openPayableAmount: number
  todayPurchaseOrders: number
  todaySalesAmount: number
}

export interface OperationsDashboardTodo {
  id: string
  type: 'WORKFLOW' | 'LOW_STOCK' | 'RECEIVABLE_OVERDUE' | 'PAYABLE_OVERDUE' | 'FAILED_OPERATION' | string
  title: string
  description?: string
  priority: 'HIGH' | 'MEDIUM' | 'LOW' | string
  route: string
  occurredAt?: string
}

export interface OperationsDashboardLowStock {
  ruleId: string
  warehouseId: string
  productId: string
  qtyOnHand: number
  minQty: number
  shortageQty: number
  remark?: string
}

export interface OperationsDashboardFailedOperation {
  id: string
  module?: string
  operation?: string
  bizNo?: string
  message?: string
  requestUri?: string
  operationTime?: string
}

export interface OperationsDashboard {
  summary: OperationsDashboardSummary
  todos: OperationsDashboardTodo[]
  lowStock: OperationsDashboardLowStock[]
  failedOperations: OperationsDashboardFailedOperation[]
  generatedAt: string
}

export const getOperationsDashboard = () => {
  return request.get<OperationsDashboard>('/dashboard/operations').then(normalizeOperationsDashboard)
}

const normalizeOperationsDashboard = (dashboard: OperationsDashboard): OperationsDashboard => ({
  ...dashboard,
  todos: dashboard.todos || [],
  lowStock: (dashboard.lowStock || []).map(normalizeOperationsDashboardLowStock),
  failedOperations: (dashboard.failedOperations || []).map(normalizeOperationsDashboardFailedOperation)
})

const normalizeOperationsDashboardLowStock = (
  item: OperationsDashboardLowStock
): OperationsDashboardLowStock => ({
  ...item,
  ruleId: String(item.ruleId),
  warehouseId: String(item.warehouseId),
  productId: String(item.productId)
})

const normalizeOperationsDashboardFailedOperation = (
  item: OperationsDashboardFailedOperation
): OperationsDashboardFailedOperation => ({
  ...item,
  id: String(item.id)
})
