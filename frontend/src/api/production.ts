import { request } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

// ==================== BOM管理 ====================

// BOM物料清单
export interface BOM {
  id: string
  bomCode: string
  bomNo?: string
  productId: string
  productCode?: string
  productName?: string
  quantity?: number
  baseQty: number
  status: 'ACTIVE' | 'DISABLED' | string
  items: BOMItem[]
  lines?: BOMItem[]
  remark?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

// BOM明细
export interface BOMItem {
  id?: string | number
  materialId: string | number
  materialProductId?: string | number
  materialCode?: string
  materialName?: string
  quantity: number
  qtyPer?: number
  unit: string
  materialUnit?: string
  scrapRate?: number
  lossRate?: number
  remark?: string
}

// BOM查询参数
export interface BOMQuery extends PageQuery {
  bomCode?: string
  keyword?: string
  productId?: string | number
  status?: string
}

// BOM创建/更新请求
export interface BOMRequest {
  productId: string | number
  baseQty: number
  items: BOMItem[]
  remark?: string
}

/**
 * 创建BOM
 */
export const createBOM = (data: BOMRequest) => {
  return request.post<BOM>('/production/boms', toBomPayload(data)).then(normalizeBom)
}

/**
 * 更新BOM
 */
export const updateBOM = (id: string | number, data: BOMRequest) => {
  return request.put<BOM>(`/production/boms/${id}`, toBomPayload(data)).then(normalizeBom)
}

/**
 * BOM列表(分页)
 */
export const getBOMs = (params: BOMQuery) => {
  return request.get<PageResponse<BOM>>('/production/boms', {
    params: {
      ...params,
      keyword: params.keyword ?? params.bomCode
    }
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizeBom)
  }))
}

/**
 * BOM详情
 */
export const getBOM = (id: string | number) => {
  return request.get<BOM>(`/production/boms/${id}`).then(normalizeBom)
}

const normalizeBomItem = (item: BOMItem): BOMItem => ({
  ...item,
  id: item.id != null ? String(item.id) : undefined,
  materialId: String(item.materialId ?? item.materialProductId ?? ''),
  materialProductId: item.materialProductId ?? item.materialId,
  quantity: item.quantity ?? item.qtyPer ?? 0,
  scrapRate: item.scrapRate ?? item.lossRate ?? 0,
  unit: item.unit || item.materialUnit || ''
})

const normalizeBom = (bom: BOM): BOM => ({
  ...bom,
  id: String(bom.id),
  bomCode: bom.bomCode ?? bom.bomNo ?? '',
  productId: String(bom.productId),
  quantity: bom.quantity ?? bom.baseQty ?? 0,
  baseQty: bom.baseQty ?? bom.quantity ?? 0,
  items: (bom.items ?? bom.lines ?? []).map(normalizeBomItem)
})

const toBomPayload = (data: BOMRequest) => ({
  productId: data.productId,
  baseQty: data.baseQty,
  remark: data.remark,
  lines: data.items.map((item) => ({
    materialProductId: item.materialProductId ?? item.materialId,
    qtyPer: item.qtyPer ?? item.quantity,
    lossRate: item.lossRate ?? item.scrapRate,
    remark: item.remark
  }))
})

// ==================== 生产订单管理 ====================

// 生产订单
export interface ProductionOrder {
  id: string
  orderNo: string
  productId: string
  productCode?: string
  productName?: string
  bomId?: string
  bomCode?: string
  planQuantity: number
  completedQuantity: number
  scrapQuantity?: number
  warehouseId: string
  finishedWarehouseId?: string
  materialWarehouseId?: string
  plannedQty?: number
  completedQty?: number
  plannedStartDate?: string
  plannedFinishDate?: string
  issuedAmount?: number
  laborAmount?: number
  overheadAmount?: number
  finishedAmount?: number
  finishedMaterialAmount?: number
  finishedLaborAmount?: number
  finishedOverheadAmount?: number
  costCloseStatus?: 'OPEN' | 'CLOSED'
  settledVarianceAmount?: number
  costCloseEntryId?: string
  costClosedDate?: string
  warehouseName?: string
  planStartDate: string
  planEndDate: string
  actualStartDate?: string
  actualEndDate?: string
  status: 'DRAFT' | 'RELEASED' | 'MATERIAL_ISSUED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'
  materials?: ProductionOrderMaterial[]
  remark?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

// 生产订单物料
export interface ProductionOrderMaterial {
  id: string
  materialId: string
  materialProductId?: string | number
  materialCode?: string
  materialName?: string
  requiredQuantity: number
  requiredQty?: number
  issuedQuantity: number
  issuedQty?: number
  issuedAmount?: number
  returnedQuantity: number
  unit: string
}

// 生产订单查询参数
export interface ProductionOrderQuery extends PageQuery {
  keyword?: string
  productId?: string | number
  status?: string
  planStartDate?: string
  planEndDate?: string
}

// 生产订单创建请求
export interface ProductionOrderRequest {
  productId: string | number
  bomId?: string | number
  planQuantity: number
  warehouseId?: string | number
  finishedWarehouseId?: string | number
  materialWarehouseId?: string | number
  planStartDate: string
  planEndDate: string
  priority?: string
  remark?: string
}

// 生产订单更新请求
export interface ProductionOrderUpdateRequest {
  planQuantity?: number
  warehouseId?: string | number
  finishedWarehouseId?: string | number
  materialWarehouseId?: string | number
  planStartDate?: string
  planEndDate?: string
  priority?: string
  remark?: string
}

// 生产领料请求
export interface ProductionIssueRequest {
  issueDate?: string
  materials?: {
    materialId: string | number
    quantity: number
    lotNumber?: string
  }[]
  lines?: {
    orderMaterialId: string | number
    issueQty: number
    lotNo?: string
    productionDate?: string
    expiryDate?: string
    locationId?: string | number
    serialNos?: string
    remark?: string
  }[]
  remark?: string
}

// 生产完工请求
export interface ProductionCompleteRequest {
  completionDate?: string
  completedQuantity: number
  completedQty?: number
  scrapQuantity?: number
  lotNumber?: string
  lotNo?: string
  productionDate?: string
  expiryDate?: string
  locationId?: string | number
  serialNos?: string
  remark?: string
}

// 生产退料请求
export interface ProductionReturnRequest {
  returnDate?: string
  lines: {
    orderMaterialId: string | number
    returnQty: number
    lotNo?: string
    productionDate?: string
    expiryDate?: string
    locationId?: string | number
    serialNos?: string
    remark?: string
  }[]
  remark?: string
}

// 完工红冲请求
export interface ProductionCompletionReversalRequest {
  reversalDate?: string
  reversedQty?: number
  remark?: string
}

/**
 * 创建生产订单
 */
export const createProductionOrder = (data: ProductionOrderRequest) => {
  return request.post<ProductionOrder>('/production/orders', toProductionOrderPayload(data)).then(normalizeProductionOrder)
}

/**
 * 更新生产订单
 */
export const updateProductionOrder = (id: string | number, data: ProductionOrderUpdateRequest) => {
  return request.put<ProductionOrder>(`/production/orders/${id}`, toProductionOrderPayload(data)).then(normalizeProductionOrder)
}

/**
 * 生产订单列表(分页)
 */
export const getProductionOrders = (params: ProductionOrderQuery) => {
  return request.get<PageResponse<ProductionOrder>>('/production/orders', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeProductionOrder)
  }))
}

/**
 * 生产订单详情
 */
export const getProductionOrder = (id: string | number) => {
  return request.get<ProductionOrder>(`/production/orders/${id}`).then(normalizeProductionOrder)
}

/**
 * 下达生产订单
 */
export const releaseProductionOrder = (id: string | number) => {
  return request.post<ProductionOrder>(`/production/orders/${id}/release`).then(normalizeProductionOrder)
}

/**
 * 取消生产订单
 */
export const cancelProductionOrder = (id: string | number, reason?: string) => {
  return request.post<ProductionOrder>(`/production/orders/${id}/cancel`, { reason }).then(normalizeProductionOrder)
}

/**
 * 生产领料
 */
export const issueProductionOrder = (id: string | number, data: ProductionIssueRequest) => {
  return request.post<ProductionOrder>(`/production/orders/${id}/issue`, toProductionIssuePayload(data)).then(normalizeProductionOrder)
}

/**
 * 生产完工
 */
export const completeProductionOrder = (id: string | number, data: ProductionCompleteRequest) => {
  return request.post<ProductionOrder>(`/production/orders/${id}/complete`, toProductionCompletePayload(data)).then(normalizeProductionOrder)
}

/**
 * 红冲完工
 */
export const reverseProductionCompletion = (id: string | number, data: ProductionCompletionReversalRequest) => {
  return request.post<ProductionOrder>(`/production/orders/${id}/reverse-completion`, data).then(normalizeProductionOrder)
}

/**
 * 生产退料
 */
export const returnProductionMaterials = (id: string | number, data: ProductionReturnRequest) => {
  return request.post<ProductionOrder>(`/production/orders/${id}/return-materials`, data).then(normalizeProductionOrder)
}

const normalizeProductionOrderMaterial = (material: ProductionOrderMaterial): ProductionOrderMaterial => ({
  ...material,
  id: String(material.id),
  materialId: String(material.materialId ?? material.materialProductId ?? ''),
  requiredQuantity: material.requiredQuantity ?? material.requiredQty ?? 0,
  issuedQuantity: material.issuedQuantity ?? material.issuedQty ?? 0,
  returnedQuantity: material.returnedQuantity ?? 0,
  unit: material.unit || ''
})

const normalizeProductionOrder = (order: ProductionOrder): ProductionOrder => ({
  ...order,
  id: String(order.id),
  productId: String(order.productId),
  bomId: order.bomId != null ? String(order.bomId) : undefined,
  warehouseId: String(order.warehouseId ?? order.finishedWarehouseId ?? ''),
  finishedWarehouseId: order.finishedWarehouseId != null ? String(order.finishedWarehouseId) : undefined,
  materialWarehouseId: order.materialWarehouseId != null ? String(order.materialWarehouseId) : undefined,
  planQuantity: order.planQuantity ?? order.plannedQty ?? 0,
  completedQuantity: order.completedQuantity ?? order.completedQty ?? 0,
  scrapQuantity: order.scrapQuantity ?? 0,
  planStartDate: order.planStartDate ?? order.plannedStartDate ?? '',
  planEndDate: order.planEndDate ?? order.plannedFinishDate ?? '',
  priority: order.priority ?? 'NORMAL',
  issuedAmount: Number(order.issuedAmount ?? 0),
  laborAmount: Number(order.laborAmount ?? 0),
  overheadAmount: Number(order.overheadAmount ?? 0),
  finishedAmount: Number(order.finishedAmount ?? 0),
  finishedMaterialAmount: Number(order.finishedMaterialAmount ?? 0),
  finishedLaborAmount: Number(order.finishedLaborAmount ?? 0),
  finishedOverheadAmount: Number(order.finishedOverheadAmount ?? 0),
  costCloseStatus: order.costCloseStatus ?? 'OPEN',
  settledVarianceAmount: Number(order.settledVarianceAmount ?? 0),
  costCloseEntryId: order.costCloseEntryId != null ? String(order.costCloseEntryId) : undefined,
  materials: (order.materials || []).map(normalizeProductionOrderMaterial)
})

const toProductionOrderPayload = (data: Partial<ProductionOrderRequest & ProductionOrderUpdateRequest>) => ({
  bomId: data.bomId,
  plannedQty: data.planQuantity,
  finishedWarehouseId: data.finishedWarehouseId ?? data.warehouseId,
  materialWarehouseId: data.materialWarehouseId ?? data.warehouseId,
  plannedStartDate: data.planStartDate,
  plannedFinishDate: data.planEndDate,
  remark: data.remark
})

const toProductionIssuePayload = (data: ProductionIssueRequest) => ({
  issueDate: data.issueDate,
  remark: data.remark,
  lines: data.lines || data.materials?.map((material) => ({
    orderMaterialId: material.materialId,
    issueQty: material.quantity,
    lotNo: material.lotNumber
  }))
})

const toProductionCompletePayload = (data: ProductionCompleteRequest) => ({
  completionDate: data.completionDate,
  completedQty: data.completedQty ?? data.completedQuantity,
  lotNo: data.lotNo ?? data.lotNumber,
  productionDate: data.productionDate,
  expiryDate: data.expiryDate,
  locationId: data.locationId || undefined,
  serialNos: data.serialNos || undefined,
  remark: data.remark
})

// ==================== 工作中心管理 ====================

// 工作中心
export interface WorkCenter {
  id: string
  workCenterCode: string
  workCenterName: string
  dailyCapacityMinutes: number
  workdayStartTime: string
  status: 'ACTIVE' | 'DISABLED' | string
  remark?: string
}

// 工作中心查询参数
export interface WorkCenterQuery extends PageQuery {
  keyword?: string
  status?: string
}

// 工作中心创建请求
export interface WorkCenterCreateRequest {
  workCenterCode: string
  workCenterName: string
  dailyCapacityMinutes: number
  workdayStartTime: string
  remark?: string
}

// 工作中心更新请求
export interface WorkCenterUpdateRequest {
  workCenterName: string
  dailyCapacityMinutes: number
  workdayStartTime: string
  remark?: string
}

const normalizeWorkCenter = (workCenter: WorkCenter): WorkCenter => ({
  ...workCenter,
  id: String(workCenter.id),
  dailyCapacityMinutes: Number(workCenter.dailyCapacityMinutes ?? 480),
  workdayStartTime: workCenter.workdayStartTime || '08:00:00'
})

/**
 * 工作中心列表(分页)
 */
export const getWorkCenters = (params: WorkCenterQuery) => {
  return request.get<PageResponse<WorkCenter>>('/production/work-centers', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeWorkCenter)
  }))
}

/**
 * 工作中心详情
 */
export const getWorkCenter = (id: string | number) => {
  return request.get<WorkCenter>(`/production/work-centers/${id}`).then(normalizeWorkCenter)
}

/**
 * 创建工作中心
 */
export const createWorkCenter = (data: WorkCenterCreateRequest) => {
  return request.post<WorkCenter>('/production/work-centers', data).then(normalizeWorkCenter)
}

/**
 * 更新工作中心
 */
export const updateWorkCenter = (id: string | number, data: WorkCenterUpdateRequest) => {
  return request.put<WorkCenter>(`/production/work-centers/${id}`, data).then(normalizeWorkCenter)
}

/**
 * 启用工作中心
 */
export const enableWorkCenter = (id: string | number) => {
  return request.post<WorkCenter>(`/production/work-centers/${id}/enable`).then(normalizeWorkCenter)
}

/**
 * 停用工作中心
 */
export const disableWorkCenter = (id: string | number) => {
  return request.post<WorkCenter>(`/production/work-centers/${id}/disable`).then(normalizeWorkCenter)
}

// ==================== 工艺路线管理 ====================

// 工艺路线工序
export interface RoutingOperation {
  id?: string | number
  lineNo?: number
  operationCode: string
  operationName: string
  workCenterId: string | number
  workCenterCode?: string
  workCenterName?: string
  standardMinutes: number
  remark?: string
}

// 工艺路线
export interface Routing {
  id: string
  routingCode: string
  routingName: string
  bomId: string
  bomNo?: string
  productId?: string
  status: 'ACTIVE' | 'DISABLED' | string
  remark?: string
  operations: RoutingOperation[]
}

// 工艺路线查询参数
export interface RoutingQuery extends PageQuery {
  keyword?: string
  status?: string
  bomId?: string | number
}

// 工艺路线创建请求
export interface RoutingCreateRequest {
  routingCode: string
  routingName: string
  bomId: string | number
  remark?: string
  operations: RoutingOperationRequest[]
}

// 工艺路线更新请求
export interface RoutingUpdateRequest {
  routingName: string
  remark?: string
  operations: RoutingOperationRequest[]
}

// 工序请求
export interface RoutingOperationRequest {
  operationCode: string
  operationName: string
  workCenterId: string | number
  standardMinutes: number
  remark?: string
}

const normalizeRoutingOperation = (operation: RoutingOperation): RoutingOperation => ({
  ...operation,
  id: operation.id != null ? String(operation.id) : undefined,
  workCenterId: String(operation.workCenterId ?? ''),
  standardMinutes: Number(operation.standardMinutes ?? 0)
})

const normalizeRouting = (routing: Routing): Routing => ({
  ...routing,
  id: String(routing.id),
  bomId: String(routing.bomId ?? ''),
  productId: routing.productId != null ? String(routing.productId) : undefined,
  operations: (routing.operations || []).map(normalizeRoutingOperation)
})

/**
 * 工艺路线列表(分页)
 */
export const getRoutings = (params: RoutingQuery) => {
  return request.get<PageResponse<Routing>>('/production/routings', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeRouting)
  }))
}

/**
 * 工艺路线详情
 */
export const getRouting = (id: string | number) => {
  return request.get<Routing>(`/production/routings/${id}`).then(normalizeRouting)
}

/**
 * 创建工艺路线
 */
export const createRouting = (data: RoutingCreateRequest) => {
  return request.post<Routing>('/production/routings', data).then(normalizeRouting)
}

/**
 * 更新工艺路线
 */
export const updateRouting = (id: string | number, data: RoutingUpdateRequest) => {
  return request.put<Routing>(`/production/routings/${id}`, data).then(normalizeRouting)
}

/**
 * 启用工艺路线
 */
export const enableRouting = (id: string | number) => {
  return request.post<Routing>(`/production/routings/${id}/enable`).then(normalizeRouting)
}

/**
 * 停用工艺路线
 */
export const disableRouting = (id: string | number) => {
  return request.post<Routing>(`/production/routings/${id}/disable`).then(normalizeRouting)
}

// ==================== 工序报工 ====================

export interface ProductionOrderOperation {
  id: string | number
  orderId: string | number
  lineNo: number
  operationCode: string
  operationName: string
  workCenterId?: string | number
  workCenterName?: string
  standardMinutes: number
  plannedQty: number
  reportedQty: number
  qualifiedQty: number
  scrapQty: number
  status: 'PENDING' | 'IN_PROGRESS' | 'DONE' | string
  scheduledMinutes?: number
  scheduledStartTime?: string
  scheduledEndTime?: string
  remark?: string
}

export interface ProductionOperationReportRequest {
  reportQty: number
  qualifiedQty: number
  scrapQty?: number
  reportDate?: string
  remark?: string
}

const normalizeOperation = (op: ProductionOrderOperation): ProductionOrderOperation => ({
  ...op,
  id: String(op.id),
  orderId: String(op.orderId),
  workCenterId: op.workCenterId != null ? String(op.workCenterId) : op.workCenterId,
  standardMinutes: Number(op.standardMinutes ?? 0),
  plannedQty: Number(op.plannedQty ?? 0),
  reportedQty: Number(op.reportedQty ?? 0),
  qualifiedQty: Number(op.qualifiedQty ?? 0),
  scrapQty: Number(op.scrapQty ?? 0),
  scheduledMinutes: op.scheduledMinutes == null ? undefined : Number(op.scheduledMinutes)
})

export const getProductionOrderOperations = (orderId: string | number) => {
  return request
    .get<ProductionOrderOperation[]>(`/production/orders/${orderId}/operations`)
    .then((list) => (list || []).map(normalizeOperation))
}

export const reportProductionOperation = (
  orderId: string | number,
  operationId: string | number,
  data: ProductionOperationReportRequest
) => {
  return request
    .post<ProductionOrderOperation>(`/production/orders/${orderId}/operations/${operationId}/report`, data)
    .then(normalizeOperation)
}

// ==================== 有限产能排程 ====================

export interface ProductionScheduleRequest {
  startDate: string
  endDate: string
}

export interface ProductionScheduleItem {
  operationId: string
  orderId: string
  orderNo: string
  productId: string
  dueDate?: string
  lineNo: number
  operationCode: string
  operationName: string
  workCenterId?: string
  workCenterCode?: string
  workCenterName?: string
  remainingQty: number
  standardMinutes: number
  requiredMinutes: number
  scheduledStartTime?: string
  scheduledEndTime?: string
  capacityWaitMinutes: number
  delayed: boolean
  lateMinutes: number
  scheduleStatus: 'SCHEDULED' | 'UNSCHEDULED' | string
  reason?: string
}

export interface ProductionScheduleWorkCenter {
  workCenterId: string
  workCenterCode: string
  workCenterName: string
  dailyCapacityMinutes: number
  workdayStartTime: string
  horizonCapacityMinutes: number
  scheduledMinutes: number
  utilizationPercent: number
}

export interface ProductionScheduleResult {
  startDate: string
  endDate: string
  committed: boolean
  orderCount: number
  operationCount: number
  scheduledCount: number
  unscheduledCount: number
  delayedCount: number
  generatedTime: string
  items: ProductionScheduleItem[]
  workCenters: ProductionScheduleWorkCenter[]
}

const normalizeScheduleResult = (result: ProductionScheduleResult): ProductionScheduleResult => ({
  ...result,
  orderCount: Number(result.orderCount ?? 0),
  operationCount: Number(result.operationCount ?? 0),
  scheduledCount: Number(result.scheduledCount ?? 0),
  unscheduledCount: Number(result.unscheduledCount ?? 0),
  delayedCount: Number(result.delayedCount ?? 0),
  items: (result.items || []).map((item) => ({
    ...item,
    operationId: String(item.operationId),
    orderId: String(item.orderId),
    productId: String(item.productId),
    workCenterId: item.workCenterId == null ? undefined : String(item.workCenterId),
    remainingQty: Number(item.remainingQty ?? 0),
    standardMinutes: Number(item.standardMinutes ?? 0),
    requiredMinutes: Number(item.requiredMinutes ?? 0),
    capacityWaitMinutes: Number(item.capacityWaitMinutes ?? 0),
    lateMinutes: Number(item.lateMinutes ?? 0)
  })),
  workCenters: (result.workCenters || []).map((center) => ({
    ...center,
    workCenterId: String(center.workCenterId),
    dailyCapacityMinutes: Number(center.dailyCapacityMinutes ?? 0),
    horizonCapacityMinutes: Number(center.horizonCapacityMinutes ?? 0),
    scheduledMinutes: Number(center.scheduledMinutes ?? 0),
    utilizationPercent: Number(center.utilizationPercent ?? 0)
  }))
})

export const previewProductionSchedule = (data: ProductionScheduleRequest) =>
  request.post<ProductionScheduleResult>('/production/schedules/preview', data).then(normalizeScheduleResult)

export const runProductionSchedule = (data: ProductionScheduleRequest) =>
  request.post<ProductionScheduleResult>('/production/schedules/run', data).then(normalizeScheduleResult)

// ==================== 生产成本归集 ====================

export type ProductionCostType = 'LABOR' | 'OVERHEAD'
export type ProductionCostEntryKind = 'ORIGINAL' | 'REVERSAL' | 'CORRECTION'

export interface ProductionCostEntry {
  id: string | number
  costNo: string
  orderId: string | number
  operationId?: string | number
  costType: ProductionCostType
  businessDate: string
  amount: number
  status: string
  entryKind: ProductionCostEntryKind
  reversalOfEntryId?: string | number
  reversalOfCostNo?: string
  correctionOfEntryId?: string | number
  correctionOfCostNo?: string
  reversalEntryId?: string | number
  reversalCostNo?: string
  correctionEntryId?: string | number
  correctionCostNo?: string
  remark?: string
  createdTime?: string
}

export interface ProductionCostEntryRequest {
  costType: ProductionCostType
  operationId?: string | number
  businessDate: string
  amount: number
  remark?: string
}

export interface ProductionCostEntryReversalRequest {
  businessDate: string
  reason: string
}

export interface ProductionCostEntryCorrectionRequest extends ProductionCostEntryRequest {
  reason: string
}

export type ProductionCostCloseEntryKind = 'CLOSE' | 'REVERSAL'

export interface ProductionCostCloseEntry {
  id: string | number
  closeNo: string
  orderId: string | number
  businessDate: string
  amount: number
  status: string
  entryKind: ProductionCostCloseEntryKind
  reversalOfCloseId?: string | number
  reversalOfCloseNo?: string
  reversalEntryId?: string | number
  reversalCloseNo?: string
  reason?: string
  remark?: string
  createdTime?: string
}

export interface ProductionCostCloseRequest {
  businessDate: string
  remark?: string
}

export interface ProductionCostReopenRequest {
  businessDate: string
  reason: string
}

export interface ProductionCostPeriodCloseItem {
  orderId: string
  orderNo: string
  productId: string
  completionDate: string
  latestActivityDate?: string
  issuedAmount: number
  laborAmount: number
  overheadAmount: number
  finishedAmount: number
  unsettledVarianceAmount: number
}

export interface ProductionCostPeriod {
  id: string
  periodYear: number
  periodMonth: string
  startDate: string
  endDate: string
  status: string
}

export interface ProductionCostPeriodClosePreview {
  periodId: string
  periodMonth: string
  startDate: string
  endDate: string
  periodStatus: string
  candidateCount: number
  positiveVarianceAmount: number
  negativeVarianceAmount: number
  netVarianceAmount: number
  items: ProductionCostPeriodCloseItem[]
}

export interface ProductionCostPeriodCloseBatchRequest {
  orderIds: Array<string | number>
  remark?: string
}

export interface ProductionCostPeriodCloseBatchItem {
  orderId: string
  orderNo?: string
  success: boolean
  closeId?: string
  closeNo?: string
  varianceAmount?: number
  errorCode?: string
  errorMessage?: string
}

export interface ProductionCostPeriodCloseBatch {
  periodId: string
  periodMonth: string
  businessDate: string
  requestedCount: number
  successCount: number
  failureCount: number
  closedVarianceAmount: number
  operatorId: string
  executedTime: string
  results: ProductionCostPeriodCloseBatchItem[]
}

export interface ProductionCostPeriodCloseEvidence {
  id: string
  operatorId?: string
  operatorName?: string
  auditTime: string
  message: string
  batch: ProductionCostPeriodCloseBatch
}

const normalizeCostEntry = (entry: ProductionCostEntry): ProductionCostEntry => ({
  ...entry,
  id: String(entry.id),
  orderId: String(entry.orderId),
  operationId: entry.operationId != null ? String(entry.operationId) : undefined,
  reversalOfEntryId: entry.reversalOfEntryId != null ? String(entry.reversalOfEntryId) : undefined,
  correctionOfEntryId: entry.correctionOfEntryId != null ? String(entry.correctionOfEntryId) : undefined,
  reversalEntryId: entry.reversalEntryId != null ? String(entry.reversalEntryId) : undefined,
  correctionEntryId: entry.correctionEntryId != null ? String(entry.correctionEntryId) : undefined,
  amount: Number(entry.amount ?? 0)
})

export const getProductionOrderCosts = (orderId: string | number) => {
  return request
    .get<ProductionCostEntry[]>(`/production/orders/${orderId}/costs`)
    .then((list) => (list || []).map(normalizeCostEntry))
}

export const postProductionOrderCost = (
  orderId: string | number,
  data: ProductionCostEntryRequest
) => {
  return request
    .post<ProductionCostEntry>(`/production/orders/${orderId}/costs`, data)
    .then(normalizeCostEntry)
}

export const reverseProductionOrderCost = (
  orderId: string | number,
  costEntryId: string | number,
  data: ProductionCostEntryReversalRequest
) => {
  return request
    .post<ProductionCostEntry>(`/production/orders/${orderId}/costs/${costEntryId}/reverse`, data)
    .then(normalizeCostEntry)
}

export const correctProductionOrderCost = (
  orderId: string | number,
  costEntryId: string | number,
  data: ProductionCostEntryCorrectionRequest
) => {
  return request
    .post<ProductionCostEntry>(`/production/orders/${orderId}/costs/${costEntryId}/correct`, data)
    .then(normalizeCostEntry)
}

const normalizeCostCloseEntry = (entry: ProductionCostCloseEntry): ProductionCostCloseEntry => ({
  ...entry,
  id: String(entry.id),
  orderId: String(entry.orderId),
  reversalOfCloseId: entry.reversalOfCloseId != null ? String(entry.reversalOfCloseId) : undefined,
  reversalEntryId: entry.reversalEntryId != null ? String(entry.reversalEntryId) : undefined,
  amount: Number(entry.amount ?? 0)
})

export const getProductionOrderCostCloses = (orderId: string | number) => {
  return request
    .get<ProductionCostCloseEntry[]>(`/production/orders/${orderId}/cost-closes`)
    .then((list) => (list || []).map(normalizeCostCloseEntry))
}

export const closeProductionOrderCost = (
  orderId: string | number,
  data: ProductionCostCloseRequest
) => {
  return request
    .post<ProductionCostCloseEntry>(`/production/orders/${orderId}/cost-close`, data)
    .then(normalizeCostCloseEntry)
}

export const reopenProductionOrderCost = (
  orderId: string | number,
  data: ProductionCostReopenRequest
) => {
  return request
    .post<ProductionCostCloseEntry>(`/production/orders/${orderId}/cost-reopen`, data)
    .then(normalizeCostCloseEntry)
}

const normalizePeriodCloseItem = (item: ProductionCostPeriodCloseItem): ProductionCostPeriodCloseItem => ({
  ...item,
  orderId: String(item.orderId),
  productId: String(item.productId),
  issuedAmount: Number(item.issuedAmount ?? 0),
  laborAmount: Number(item.laborAmount ?? 0),
  overheadAmount: Number(item.overheadAmount ?? 0),
  finishedAmount: Number(item.finishedAmount ?? 0),
  unsettledVarianceAmount: Number(item.unsettledVarianceAmount ?? 0)
})

export const getProductionCostPeriods = () => {
  return request
    .get<ProductionCostPeriod[]>('/production/cost-close-periods')
    .then((periods) => periods.map((period) => ({ ...period, id: String(period.id) })))
}

const normalizePeriodCloseBatch = (batch: ProductionCostPeriodCloseBatch): ProductionCostPeriodCloseBatch => ({
  ...batch,
  periodId: String(batch.periodId),
  operatorId: String(batch.operatorId),
  closedVarianceAmount: Number(batch.closedVarianceAmount ?? 0),
  results: (batch.results || []).map((item) => ({
    ...item,
    orderId: String(item.orderId),
    closeId: item.closeId != null ? String(item.closeId) : undefined,
    varianceAmount: item.varianceAmount == null ? undefined : Number(item.varianceAmount)
  }))
})

export const getProductionCostPeriodClosePreview = (periodId: string | number) => {
  return request
    .get<ProductionCostPeriodClosePreview>(`/production/cost-close-periods/${periodId}/preview`)
    .then((preview) => ({
      ...preview,
      periodId: String(preview.periodId),
      positiveVarianceAmount: Number(preview.positiveVarianceAmount ?? 0),
      negativeVarianceAmount: Number(preview.negativeVarianceAmount ?? 0),
      netVarianceAmount: Number(preview.netVarianceAmount ?? 0),
      items: (preview.items || []).map(normalizePeriodCloseItem)
    }))
}

export const batchCloseProductionCosts = (
  periodId: string | number,
  data: ProductionCostPeriodCloseBatchRequest
) => {
  return request
    .post<ProductionCostPeriodCloseBatch>(`/production/cost-close-periods/${periodId}/batch-close`, data)
    .then(normalizePeriodCloseBatch)
}

export const getProductionCostPeriodCloseEvidence = (
  periodId: string | number,
  params?: PageQuery
) => {
  return request
    .get<PageResponse<ProductionCostPeriodCloseEvidence>>(`/production/cost-close-periods/${periodId}/evidence`, { params })
    .then((page) => ({
      ...page,
      records: page.records.map((evidence) => ({
        ...evidence,
        id: String(evidence.id),
        operatorId: evidence.operatorId != null ? String(evidence.operatorId) : undefined,
        batch: normalizePeriodCloseBatch(evidence.batch)
      }))
    }))
}
