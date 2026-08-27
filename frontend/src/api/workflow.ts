import { request } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

// ==================== 工作流任务 ====================

export interface WorkflowTask {
  id: string
  instanceId?: string
  businessType: string
  businessId: string
  businessNo: string
  title: string
  approverUserId?: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
  dueTime?: string
  overdue?: boolean
  escalatedTime?: string
  escalationCount?: number
  createdTime: string
  updatedTime?: string
}

export interface WorkflowTaskQuery extends PageQuery {
  businessType?: string
  businessId?: string | number
  businessNo?: string
  status?: string
  overdueOnly?: boolean
}

export interface WorkflowApproveRequest {
  taskId: string | number
  comment?: string
}

export interface WorkflowRejectRequest {
  taskId: string | number
  reason: string
}

export interface WorkflowWithdrawRequest {
  businessType: string
  businessId: string | number
  comment?: string
}

// 工作流任务API
export const getWorkflowTasks = (params: WorkflowTaskQuery) => {
  return request.get<PageResponse<WorkflowTask>>('/workflow/tasks', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeWorkflowTask)
  }))
}

export const getWorkflowTask = (id: string | number) => {
  return request.get<WorkflowTask>(`/workflow/tasks/${id}`).then(normalizeWorkflowTask)
}

export const approveWorkflowTask = (data: WorkflowApproveRequest) => {
  return request.post(`/workflow/tasks/${data.taskId}/approve`, { comment: data.comment })
}

export const rejectWorkflowTask = (data: WorkflowRejectRequest) => {
  return request.post(`/workflow/tasks/${data.taskId}/reject`, { comment: data.reason })
}

export const transferWorkflowTask = (data: { taskId: string | number; targetUserId: string | number; comment?: string }) => {
  return request.post(`/workflow/tasks/${data.taskId}/transfer`, {
    targetUserId: data.targetUserId,
    comment: data.comment
  })
}

export const escalateWorkflowTask = (data: { taskId: string | number; targetUserId: string | number; comment?: string }) => {
  return request.post<WorkflowTask>(`/workflow/tasks/${data.taskId}/escalate`, {
    targetUserId: data.targetUserId,
    comment: data.comment
  }).then(normalizeWorkflowTask)
}

export const withdrawWorkflow = (data: WorkflowWithdrawRequest) => {
  return request.post(`/workflow/${data.businessType}/${data.businessId}/withdraw`, { comment: data.comment })
}

// ==================== 工作流配置 ====================

export interface WorkflowApprovalApprover {
  id?: string
  approverType: 'USER' | 'ROLE' | string
  approverId: string
}

export interface WorkflowApprovalNode {
  id?: string
  nodeName: string
  nodeOrder: number
  approvalMode: 'ANY' | 'ALL' | string
  status?: 'ACTIVE' | 'DISABLED' | string
  approvers: WorkflowApprovalApprover[]
}

export interface WorkflowApprovalConfig {
  id?: string
  businessType: string
  configName?: string
  status: 'ACTIVE' | 'DISABLED' | string
  taskTimeoutHours: number
  remark?: string
  nodes: WorkflowApprovalNode[]
}

export interface WorkflowApprovalConfigRequest {
  configName: string
  status?: string
  taskTimeoutHours: number
  remark?: string
  nodes: Array<{
    nodeName: string
    nodeOrder: number
    approvalMode?: string
    approvers: Array<{
      approverType: string
      approverId: string | number
    }>
  }>
}

export const getWorkflowApprovalConfig = (businessType: string) => {
  return request.get<WorkflowApprovalConfig>(`/workflow/configs/${businessType}`)
    .then(normalizeWorkflowApprovalConfig)
}

export const saveWorkflowApprovalConfig = (businessType: string, data: WorkflowApprovalConfigRequest) => {
  return request.put<WorkflowApprovalConfig>(`/workflow/configs/${businessType}`, data)
    .then(normalizeWorkflowApprovalConfig)
}

// ==================== 工作流记录 ====================

export interface WorkflowRecord {
  id: string
  instanceId?: string
  businessType: string
  businessId: string
  businessNo: string
  operatorUserId: string
  action: 'SUBMIT' | 'APPROVE' | 'REJECT' | 'WITHDRAW'
  comment?: string
  actionTime: string
}

export interface WorkflowRecordQuery extends PageQuery {
  businessType?: string
  businessId?: string | number
  businessNo?: string
  action?: string
}

// 工作流记录API
export const getWorkflowRecords = (params: WorkflowRecordQuery) => {
  return request.get<PageResponse<WorkflowRecord>>('/workflow/records', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeWorkflowRecord)
  }))
}

export const getBusinessWorkflowRecords = (businessType: string, businessId: string | number) => {
  return request.get<PageResponse<WorkflowRecord>>('/workflow/records', {
    params: { businessType, businessId, pageNo: 1, pageSize: 1000 }
  }).then((res) => res.records.map(normalizeWorkflowRecord))
}

const normalizeWorkflowTask = (task: WorkflowTask): WorkflowTask => ({
  ...task,
  id: String(task.id),
  instanceId: task.instanceId != null ? String(task.instanceId) : undefined,
  businessId: String(task.businessId),
  approverUserId: task.approverUserId != null ? String(task.approverUserId) : undefined
})

const normalizeWorkflowApprovalApprover = (approver: WorkflowApprovalApprover): WorkflowApprovalApprover => ({
  ...approver,
  id: approver.id != null ? String(approver.id) : undefined,
  approverId: String(approver.approverId)
})

const normalizeWorkflowApprovalNode = (node: WorkflowApprovalNode): WorkflowApprovalNode => ({
  ...node,
  id: node.id != null ? String(node.id) : undefined,
  nodeOrder: Number(node.nodeOrder ?? 0),
  approvers: (node.approvers || []).map(normalizeWorkflowApprovalApprover)
})

const normalizeWorkflowApprovalConfig = (config: WorkflowApprovalConfig): WorkflowApprovalConfig => ({
  ...config,
  id: config.id != null ? String(config.id) : undefined,
  taskTimeoutHours: Number(config.taskTimeoutHours || 24),
  nodes: (config.nodes || []).map(normalizeWorkflowApprovalNode)
})

const normalizeWorkflowRecord = (record: WorkflowRecord): WorkflowRecord => ({
  ...record,
  id: String(record.id),
  instanceId: record.instanceId != null ? String(record.instanceId) : undefined,
  businessId: String(record.businessId),
  operatorUserId: String(record.operatorUserId)
})

// ==================== 报表中心 ====================
// 注意：生产订单和BOM的API已移至 production.ts 文件中
// 此文件只保留工作流相关的API

export interface ReportQuery {
  pageNo?: number
  pageSize?: number
  [key: string]: any
}

export interface OrderReportRow {
  id: string
  bizNo: string
  partnerId: string
  bizDate: string
  status: string
  approvalStatus?: string
  fulfillmentStatus?: string
  totalQuantity: number
  totalAmount: number
  totalTaxAmount: number
}

export interface InventoryBalanceReportRow {
  id: string
  warehouseId: string
  productId: string
  qtyOnHand: number
  qtyReserved: number
  qtyAvailable: number
  amountOnHand: number
  updatedTime: string
}

export interface InventoryTransactionReportRow {
  id: string
  warehouseId: string
  productId: string
  bizType: string
  bizNo: string
  bizLineId?: string
  direction: string
  qty: number
  amount: number
  unitCost: number
  occurredTime: string
  remark?: string
}

export interface FinanceSettlementReportRow {
  id: string
  direction: 'RECEIVABLE' | 'PAYABLE'
  bizNo: string
  partnerId: string
  bizDate: string
  sourceType: string
  sourceNo: string
  originalAmount: number
  settledAmount: number
  remainingAmount: number
  status: string
}

export interface InventoryValuationReportRow {
  rowKey: string
  periodStart: string
  asOfDate: string
  warehouseId: string
  warehouseCode: string
  warehouseName: string
  productId: string
  productCode: string
  productName: string
  openingQty: number
  openingAmount: number
  inboundQty: number
  inboundAmount: number
  outboundQty: number
  outboundAmount: number
  closingQty: number
  closingAmount: number
  averageUnitCost: number
}

export interface ProductionCostReportRow {
  orderId: string
  orderNo: string
  productId: string
  productCode?: string
  productName?: string
  status: string
  plannedStartDate?: string
  plannedFinishDate?: string
  plannedQty: number
  completedQty: number
  completionRate: number
  materialCost: number
  finishedGoodsCost: number
  workInProgressCost: number
  completionUnitCost: number
  costStatus: string
}

// 报表API
export const getPurchaseOrderReport = (params: ReportQuery) => {
  return request.get<PageResponse<OrderReportRow>>('/reports/purchase-orders', { params }).then((page) => normalizeReportPage(page, normalizeOrderReportRow))
}

export const getSalesOrderReport = (params: ReportQuery) => {
  return request.get<PageResponse<OrderReportRow>>('/reports/sales-orders', { params }).then((page) => normalizeReportPage(page, normalizeOrderReportRow))
}

export const getInventoryBalanceReport = (params: ReportQuery) => {
  return request.get<PageResponse<InventoryBalanceReportRow>>('/reports/inventory-balances', { params }).then((page) => normalizeReportPage(page, normalizeInventoryBalanceReportRow))
}

export const getInventoryTransactionReport = (params: ReportQuery) => {
  return request.get<PageResponse<InventoryTransactionReportRow>>('/reports/inventory-transactions', { params }).then((page) => normalizeReportPage(page, normalizeInventoryTransactionReportRow))
}

export const getFinanceSettlementReport = (params: ReportQuery) => {
  return request.get<PageResponse<FinanceSettlementReportRow>>('/reports/finance-settlements', { params }).then((page) => normalizeReportPage(page, normalizeFinanceSettlementReportRow))
}

export const getInventoryValuationReport = (params: ReportQuery) => {
  return request.get<PageResponse<InventoryValuationReportRow>>('/reports/inventory-valuations', { params })
    .then((page) => normalizeReportPage(page, normalizeInventoryValuationReportRow))
}

export const getProductionCostReport = (params: ReportQuery) => {
  return request.get<PageResponse<ProductionCostReportRow>>('/reports/production-costs', { params })
    .then((page) => normalizeReportPage(page, normalizeProductionCostReportRow))
}

const normalizeReportPage = <T>(page: PageResponse<T>, normalize: (row: T) => T): PageResponse<T> => ({
  ...page,
  records: page.records.map(normalize)
})

const normalizeOrderReportRow = (row: OrderReportRow): OrderReportRow => ({
  ...row,
  id: String(row.id),
  partnerId: String(row.partnerId)
})

const normalizeInventoryBalanceReportRow = (row: InventoryBalanceReportRow): InventoryBalanceReportRow => ({
  ...row,
  id: String(row.id),
  warehouseId: String(row.warehouseId),
  productId: String(row.productId)
})

const normalizeInventoryTransactionReportRow = (row: InventoryTransactionReportRow): InventoryTransactionReportRow => ({
  ...row,
  id: String(row.id),
  warehouseId: String(row.warehouseId),
  productId: String(row.productId),
  bizLineId: row.bizLineId != null ? String(row.bizLineId) : undefined
})

const normalizeFinanceSettlementReportRow = (row: FinanceSettlementReportRow): FinanceSettlementReportRow => ({
  ...row,
  id: String(row.id),
  partnerId: String(row.partnerId)
})

const normalizeInventoryValuationReportRow = (row: InventoryValuationReportRow): InventoryValuationReportRow => ({
  ...row,
  rowKey: String(row.rowKey),
  warehouseId: String(row.warehouseId),
  productId: String(row.productId)
})

const normalizeProductionCostReportRow = (row: ProductionCostReportRow): ProductionCostReportRow => ({
  ...row,
  orderId: String(row.orderId),
  productId: String(row.productId)
})

export const exportPurchaseOrderReport = (params: ReportQuery) => {
  return request.get<Blob>('/reports/purchase-orders/export', {
    params,
    responseType: 'blob'
  })
}

export const exportSalesOrderReport = (params: ReportQuery) => {
  return request.get<Blob>('/reports/sales-orders/export', {
    params,
    responseType: 'blob'
  })
}

export const exportInventoryBalanceReport = (params: ReportQuery) => {
  return request.get<Blob>('/reports/inventory-balances/export', {
    params,
    responseType: 'blob'
  })
}

export const exportInventoryTransactionReport = (params: ReportQuery) => {
  return request.get<Blob>('/reports/inventory-transactions/export', {
    params,
    responseType: 'blob'
  })
}

export const exportFinanceSettlementReport = (params: ReportQuery) => {
  return request.get<Blob>('/reports/finance-settlements/export', {
    params,
    responseType: 'blob'
  })
}

export const exportInventoryValuationReport = (params: ReportQuery) => {
  return request.get<Blob>('/reports/inventory-valuations/export', { params, responseType: 'blob' })
}

export const exportProductionCostReport = (params: ReportQuery) => {
  return request.get<Blob>('/reports/production-costs/export', { params, responseType: 'blob' })
}
