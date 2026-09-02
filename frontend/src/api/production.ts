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
  finishedAmount?: number
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
  remark?: string
}

// 工作中心更新请求
export interface WorkCenterUpdateRequest {
  workCenterName: string
  remark?: string
}

const normalizeWorkCenter = (workCenter: WorkCenter): WorkCenter => ({
  ...workCenter,
  id: String(workCenter.id)
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
  plannedQty: number
  reportedQty: number
  qualifiedQty: number
  scrapQty: number
  status: 'PENDING' | 'IN_PROGRESS' | 'DONE' | string
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
  plannedQty: Number(op.plannedQty ?? 0),
  reportedQty: Number(op.reportedQty ?? 0),
  qualifiedQty: Number(op.qualifiedQty ?? 0),
  scrapQty: Number(op.scrapQty ?? 0)
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
