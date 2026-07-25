import { request } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

// ==================== 库存查询 ====================

export interface InventoryStock {
  locationId?: string

  id: string
  warehouseId: string
  warehouseName?: string
  productId: string
  productCode?: string
  productName?: string
  quantity: number
  availableQuantity: number
  reservedQuantity: number
  qtyOnHand?: number
  qtyAvailable?: number
  qtyReserved?: number
  amountOnHand?: number
  unit?: string
  lastUpdated?: string
  updatedTime?: string
}

export interface InventoryStockQuery extends PageQuery {
  warehouseId?: string | number
  productId?: string | number
  locationId?: string | number
  productCode?: string
  productName?: string
}

// 库存查询API
export const getInventoryStocks = (params: InventoryStockQuery) => {
  return request.get<PageResponse<InventoryStock>>('/inventory/balances', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeInventoryStock)
  }))
}

export const getInventoryStock = (id: string | number) => {
  return request.get<InventoryStock>(`/inventory/balances/${id}`).then(normalizeInventoryStock)
}

export const exportInventoryStocks = (params: InventoryStockQuery) => {
  return request.get<Blob>('/inventory/balances/export', {
    params,
    responseType: 'blob'
  })
}

const normalizeInventoryStock = (stock: InventoryStock): InventoryStock => ({
  ...stock,
  id: String(stock.id),
  warehouseId: String(stock.warehouseId),
  locationId: stock.locationId != null ? String(stock.locationId) : stock.locationId,
  productId: String(stock.productId),
  quantity: stock.quantity ?? stock.qtyOnHand ?? 0,
  availableQuantity: stock.availableQuantity ?? stock.qtyAvailable ?? 0,
  reservedQuantity: stock.reservedQuantity ?? stock.qtyReserved ?? 0,
  lastUpdated: stock.lastUpdated ?? stock.updatedTime ?? ''
})

// ==================== 批次库存与库存流水 ====================

export interface InventoryLotBalance {
  locationId?: string

  id: string
  warehouseId: string
  productId: string
  lotNo: string
  productionDate?: string
  expiryDate?: string
  firstInboundTime?: string
  qtyOnHand: number
  qtyReserved: number
  qtyAvailable: number
  amountOnHand: number
  updatedTime?: string
}

export interface InventoryLotTrace {
  locationId?: string

  id: string
  warehouseId: string
  productId: string
  lotNo: string
  productionDate?: string
  expiryDate?: string
  bizType: string
  bizNo: string
  bizLineId?: string
  direction: string
  qty: number
  amount: number
  unitCost: number
  occurredTime?: string
  remark?: string
  documentRoute?: string | null
  documentLabel?: string | null
}

export interface InventoryLotExpiryAlert {
  id: string
  warehouseId: string
  productId: string
  lotNo: string
  productionDate?: string
  expiryDate?: string
  firstInboundTime?: string
  qtyOnHand: number
  qtyReserved: number
  qtyAvailable: number
  amountOnHand: number
  expiryStatus: string
  daysToExpiry?: number
  updatedTime?: string
}

export interface InventoryTransaction {
  locationId?: string

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
  occurredTime?: string
  remark?: string
}

export interface InventoryLotBalanceQuery extends PageQuery {
  warehouseId?: string | number
  productId?: string | number
  lotNo?: string
  expiryDateFrom?: string
  expiryDateTo?: string
  expiringWithinDays?: number
}

export interface InventoryLotTraceQuery extends PageQuery {
  warehouseId?: string | number
  productId?: string | number
  lotNo?: string
  direction?: string
  occurredTimeFrom?: string
  occurredTimeTo?: string
}

export interface InventoryLotExpiryAlertQuery extends PageQuery {
  warehouseId?: string | number
  productId?: string | number
  lotNo?: string
  warningDays?: number
  status?: string
}

export interface InventoryTransactionQuery extends PageQuery {
  warehouseId?: string | number
  productId?: string | number
  bizType?: string
  bizNo?: string
  direction?: string
  occurredTimeFrom?: string
  occurredTimeTo?: string
}

export const getInventoryLotBalances = (params: InventoryLotBalanceQuery) => {
  return request.get<PageResponse<InventoryLotBalance>>('/inventory/lot-balances', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeInventoryLotBalance)
  }))
}

export const getInventoryLotBalance = (id: string | number) => {
  return request.get<InventoryLotBalance>(`/inventory/lot-balances/${id}`).then(normalizeInventoryLotBalance)
}

export const getInventoryLotTrace = (params: InventoryLotTraceQuery) => {
  return request.get<PageResponse<InventoryLotTrace>>('/inventory/lots/trace', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeInventoryLotTrace)
  }))
}

export const getInventoryLotExpiryAlerts = (params: InventoryLotExpiryAlertQuery) => {
  return request.get<PageResponse<InventoryLotExpiryAlert>>('/inventory/lots/alerts', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeInventoryLotExpiryAlert)
  }))
}

export const getInventoryTransactions = (params: InventoryTransactionQuery) => {
  return request.get<PageResponse<InventoryTransaction>>('/inventory/transactions', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeInventoryTransaction)
  }))
}

export const getInventoryTransaction = (id: string | number) => {
  return request.get<InventoryTransaction>(`/inventory/transactions/${id}`).then(normalizeInventoryTransaction)
}

const normalizeInventoryLotBalance = (item: InventoryLotBalance): InventoryLotBalance => ({
  ...item,
  id: String(item.id),
  warehouseId: String(item.warehouseId),
  locationId: item.locationId != null ? String(item.locationId) : item.locationId,
  productId: String(item.productId),
  qtyOnHand: Number(item.qtyOnHand ?? 0),
  qtyReserved: Number(item.qtyReserved ?? 0),
  qtyAvailable: Number(item.qtyAvailable ?? 0),
  amountOnHand: Number(item.amountOnHand ?? 0)
})

const normalizeInventoryLotTrace = (item: InventoryLotTrace): InventoryLotTrace => ({
  ...item,
  id: String(item.id),
  warehouseId: String(item.warehouseId),
  locationId: item.locationId != null ? String(item.locationId) : item.locationId,
  productId: String(item.productId),
  bizLineId: item.bizLineId != null ? String(item.bizLineId) : undefined,
  qty: Number(item.qty ?? 0),
  amount: Number(item.amount ?? 0),
  unitCost: Number(item.unitCost ?? 0),
  documentRoute: item.documentRoute || null,
  documentLabel: item.documentLabel || null
})

const normalizeInventoryLotExpiryAlert = (item: InventoryLotExpiryAlert): InventoryLotExpiryAlert => ({
  ...item,
  id: String(item.id),
  warehouseId: String(item.warehouseId),
  locationId: item.locationId != null ? String(item.locationId) : item.locationId,
  productId: String(item.productId),
  qtyOnHand: Number(item.qtyOnHand ?? 0),
  qtyReserved: Number(item.qtyReserved ?? 0),
  qtyAvailable: Number(item.qtyAvailable ?? 0),
  amountOnHand: Number(item.amountOnHand ?? 0),
  daysToExpiry: item.daysToExpiry != null ? Number(item.daysToExpiry) : undefined
})

const normalizeInventoryTransaction = (item: InventoryTransaction): InventoryTransaction => ({
  ...item,
  id: String(item.id),
  warehouseId: String(item.warehouseId),
  locationId: item.locationId != null ? String(item.locationId) : item.locationId,
  productId: String(item.productId),
  bizLineId: item.bizLineId != null ? String(item.bizLineId) : undefined,
  qty: Number(item.qty ?? 0),
  amount: Number(item.amount ?? 0),
  unitCost: Number(item.unitCost ?? 0)
})

// ==================== 库存预留 ====================

export interface InventoryReservation {
  id: string
  warehouseId: string
  productId: string
  sourceType: string
  sourceId?: string
  sourceNo?: string
  sourceLineId?: string
  reservedQty: number
  releasedQty: number
  remainingQty: number
  status: 'ACTIVE' | 'RELEASED' | 'CANCELLED' | string
  remark?: string
  createdTime?: string
  updatedTime?: string
}

export interface InventoryReservationEvent {
  id: string
  reservationId: string
  eventType: string
  eventQty: number
  remainingQtyBefore: number
  remainingQtyAfter: number
  reason?: string
  createdBy?: string
  createdTime?: string
}

export interface InventoryReservationDetail {
  reservation: InventoryReservation
  events: InventoryReservationEvent[]
}

export interface InventoryReservationSummary {
  warehouseId: string
  productId: string
  sourceType?: string
  status?: string
  reservedQty: number
  releasedQty: number
  remainingQty: number
  qtyOnHand: number
  qtyReserved: number
  qtyAvailable: number
  reservationCount: number
}

export interface InventoryReservationSource {
  sourceType?: string
  sourceId?: string
  sourceNo?: string
  reservations: InventoryReservationDetail[]
}

export interface InventoryReservationCheckIssue {
  issueType: string
  severity: 'ERROR' | 'WARN' | string
  reservationId?: string
  warehouseId?: string
  productId?: string
  sourceType?: string
  sourceId?: string
  sourceNo?: string
  expectedQty?: number
  actualQty?: number
  message?: string
}

export interface InventoryReservationQuery extends PageQuery {
  warehouseId?: string | number
  productId?: string | number
  sourceType?: string
  sourceNo?: string
  status?: string
  createdTimeFrom?: string
  createdTimeTo?: string
}

export interface InventoryReservationSummaryQuery {
  warehouseId?: string | number
  productId?: string | number
  sourceType?: string
  status?: string
}

export interface InventoryReservationSourceQuery {
  sourceType?: string
  sourceId?: string | number
  sourceNo?: string
}

export interface InventoryReservationCheckQuery {
  warehouseId?: string | number
  productId?: string | number
}

export interface InventoryReservationManualReleaseRequest {
  qty: number
  reason: string
}

export const getInventoryReservations = (params: InventoryReservationQuery) => {
  return request.get<PageResponse<InventoryReservation>>('/inventory/reservations', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeInventoryReservation)
  }))
}

export const getInventoryReservation = (id: string | number) => {
  return request.get<InventoryReservationDetail>(`/inventory/reservations/${id}`)
    .then(normalizeInventoryReservationDetail)
}

export const getInventoryReservationSummary = (params: InventoryReservationSummaryQuery) => {
  return request.get<InventoryReservationSummary[]>('/inventory/reservations/summary', { params })
    .then((items) => items.map(normalizeInventoryReservationSummary))
}

export const getInventoryReservationSource = (params: InventoryReservationSourceQuery) => {
  return request.get<InventoryReservationSource>('/inventory/reservations/source', { params })
    .then(normalizeInventoryReservationSource)
}

export const checkInventoryReservations = (params: InventoryReservationCheckQuery) => {
  return request.get<InventoryReservationCheckIssue[]>('/inventory/reservations/checks', { params })
    .then((items) => items.map(normalizeInventoryReservationCheckIssue))
}

export const manualReleaseInventoryReservation = (
  id: string | number,
  data: InventoryReservationManualReleaseRequest
) => {
  return request.post<InventoryReservationDetail>(`/inventory/reservations/${id}/manual-release`, data)
    .then(normalizeInventoryReservationDetail)
}

const normalizeInventoryReservation = (reservation: InventoryReservation): InventoryReservation => ({
  ...reservation,
  id: String(reservation.id),
  warehouseId: String(reservation.warehouseId),
  productId: String(reservation.productId),
  sourceId: reservation.sourceId != null ? String(reservation.sourceId) : undefined,
  sourceLineId: reservation.sourceLineId != null ? String(reservation.sourceLineId) : undefined,
  reservedQty: Number(reservation.reservedQty ?? 0),
  releasedQty: Number(reservation.releasedQty ?? 0),
  remainingQty: Number(reservation.remainingQty ?? 0)
})

const normalizeInventoryReservationEvent = (event: InventoryReservationEvent): InventoryReservationEvent => ({
  ...event,
  id: String(event.id),
  reservationId: String(event.reservationId),
  createdBy: event.createdBy != null ? String(event.createdBy) : undefined,
  eventQty: Number(event.eventQty ?? 0),
  remainingQtyBefore: Number(event.remainingQtyBefore ?? 0),
  remainingQtyAfter: Number(event.remainingQtyAfter ?? 0)
})

const normalizeInventoryReservationDetail = (detail: InventoryReservationDetail): InventoryReservationDetail => ({
  reservation: normalizeInventoryReservation(detail.reservation),
  events: (detail.events || []).map(normalizeInventoryReservationEvent)
})

const normalizeInventoryReservationSummary = (summary: InventoryReservationSummary): InventoryReservationSummary => ({
  ...summary,
  warehouseId: String(summary.warehouseId),
  productId: String(summary.productId),
  reservedQty: Number(summary.reservedQty ?? 0),
  releasedQty: Number(summary.releasedQty ?? 0),
  remainingQty: Number(summary.remainingQty ?? 0),
  qtyOnHand: Number(summary.qtyOnHand ?? 0),
  qtyReserved: Number(summary.qtyReserved ?? 0),
  qtyAvailable: Number(summary.qtyAvailable ?? 0),
  reservationCount: Number(summary.reservationCount ?? 0)
})

const normalizeInventoryReservationSource = (source: InventoryReservationSource): InventoryReservationSource => ({
  ...source,
  sourceId: source.sourceId != null ? String(source.sourceId) : undefined,
  reservations: (source.reservations || []).map(normalizeInventoryReservationDetail)
})

const normalizeInventoryReservationCheckIssue = (
  issue: InventoryReservationCheckIssue
): InventoryReservationCheckIssue => ({
  ...issue,
  reservationId: issue.reservationId != null ? String(issue.reservationId) : undefined,
  warehouseId: issue.warehouseId != null ? String(issue.warehouseId) : undefined,
  productId: issue.productId != null ? String(issue.productId) : undefined,
  sourceId: issue.sourceId != null ? String(issue.sourceId) : undefined,
  expectedQty: issue.expectedQty != null ? Number(issue.expectedQty) : undefined,
  actualQty: issue.actualQty != null ? Number(issue.actualQty) : undefined
})

// ==================== 库存调整 ====================

export interface InventoryAdjustment {
  id: string | number
  adjustmentNo: string
  warehouseId: string | number
  warehouseName: string
  adjustmentDate: string
  type: 'GAIN' | 'LOSS' | 'OTHER'
  status: 'DRAFT' | 'POSTED' | 'COMPLETED' | 'CANCELLED'
  items: InventoryAdjustmentItem[]
  lines?: InventoryAdjustmentLineResponse[]
  remark?: string
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface InventoryAdjustmentItem {
  id?: string | number
  productId: string | number
  productCode?: string
  productName?: string
  direction?: 'IN' | 'OUT'
  quantity: number
  qty?: number
  unitCost?: number
  lotNo?: string
  productionDate?: string
  expiryDate?: string
  locationId?: string | number | null
  serialNos?: string
  reason?: string
  remark?: string
}

interface InventoryAdjustmentLineResponse {
  id?: string | number
  productId: string | number
  productCode?: string
  productName?: string
  direction?: 'IN' | 'OUT'
  qty?: number
  quantity?: number
  unitCost?: number
  lotNo?: string
  productionDate?: string
  expiryDate?: string
  locationId?: string | number | null
  serialNos?: string
  reason?: string
  remark?: string
}

export interface InventoryAdjustmentQuery extends PageQuery {
  adjustmentNo?: string
  warehouseId?: string | number
  type?: string
  status?: string
  startDate?: string
  endDate?: string
}

export interface InventoryAdjustmentCreateRequest {
  warehouseId: string | number
  adjustmentDate: string
  type: string
  items: InventoryAdjustmentItem[]
  remark?: string
}

// 库存调整API
export const getInventoryAdjustments = (params: InventoryAdjustmentQuery) => {
  return request.get<PageResponse<InventoryAdjustment>>('/inventory/adjustments', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeInventoryAdjustment)
  }))
}

export const getInventoryAdjustment = (id: string | number) => {
  return request.get<InventoryAdjustment>(`/inventory/adjustments/${id}`).then(normalizeInventoryAdjustment)
}

export const createInventoryAdjustment = (data: InventoryAdjustmentCreateRequest) => {
  return request.post<InventoryAdjustment>('/inventory/adjustments', toAdjustmentPayload(data)).then(normalizeInventoryAdjustment)
}

export const completeInventoryAdjustment = (id: string | number) => {
  return request.post<InventoryAdjustment>(`/inventory/adjustments/${id}/post`).then(normalizeInventoryAdjustment)
}

export const cancelInventoryAdjustment = (id: string | number) => {
  return request.post(`/inventory/adjustments/${id}/cancel`)
}

const adjustmentDirection = (type: string): 'IN' | 'OUT' => type === 'LOSS' ? 'OUT' : 'IN'

const adjustmentType = (items: InventoryAdjustmentItem[], type?: string): 'GAIN' | 'LOSS' | 'OTHER' => {
  if (type === 'GAIN' || type === 'LOSS' || type === 'OTHER') return type
  return items.some((item) => item.direction === 'OUT') ? 'LOSS' : 'GAIN'
}

const normalizeInventoryAdjustmentItem = (item: InventoryAdjustmentItem | InventoryAdjustmentLineResponse): InventoryAdjustmentItem => ({
  ...item,
  id: item.id,
  productId: String(item.productId),
  productCode: item.productCode || '',
  productName: item.productName || '',
  direction: item.direction,
  quantity: Number(item.quantity ?? item.qty ?? 0),
  unitCost: Number(item.unitCost ?? 0),
  locationId: item.locationId != null ? String(item.locationId) : item.locationId,
  reason: item.reason || item.remark || ''
})

const normalizeInventoryAdjustment = (adjustment: InventoryAdjustment): InventoryAdjustment => {
  const items = (adjustment.items ?? adjustment.lines ?? []).map(normalizeInventoryAdjustmentItem)
  return {
    ...adjustment,
    id: adjustment.id,
    warehouseId: String(adjustment.warehouseId),
    status: adjustment.status === 'POSTED' ? 'COMPLETED' : adjustment.status,
    type: adjustmentType(items, adjustment.type),
    items
  }
}

const toAdjustmentPayload = (data: InventoryAdjustmentCreateRequest) => ({
  warehouseId: data.warehouseId,
  adjustmentDate: data.adjustmentDate,
  remark: data.remark,
  lines: data.items.map((item) => ({
    productId: item.productId,
    direction: item.direction ?? adjustmentDirection(data.type),
    qty: item.quantity,
    unitCost: item.unitCost ?? 0,
    lotNo: item.lotNo || undefined,
    productionDate: item.productionDate || undefined,
    expiryDate: item.expiryDate || undefined,
    locationId: item.locationId || undefined,
    serialNos: item.serialNos || undefined,
    reason: item.reason
  }))
})

// ==================== 库存盘点 ====================

export interface InventoryCheck {
  id: string | number
  checkNo: string
  warehouseId: string | number
  warehouseName?: string
  checkDate: string
  status: 'COUNTED' | 'ADJUSTED' | 'CANCELLED'
  items: InventoryCheckItem[]
  lines?: InventoryCheckLineResponse[]
  generatedAdjustmentId?: string | number
  generatedAdjustmentNo?: string
  remark?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface InventoryCheckItem {
  id?: string | number
  productId: string | number
  productCode?: string
  productName?: string
  locationId?: string | number | null
  bookQuantity: number
  actualQuantity?: number
  difference?: number
  unitCost?: number
  differenceAmount?: number
  lotNo?: string
  productionDate?: string
  expiryDate?: string
  remark?: string
}

export interface InventoryCheckLineResponse {
  id?: string | number
  lineNo?: number
  productId: string | number
  locationId?: string | number | null
  bookQty: number
  actualQty?: number
  differenceQty?: number
  unitCost?: number
  differenceAmount?: number
  lotNo?: string
  productionDate?: string
  expiryDate?: string
  remark?: string
}

export interface InventoryCheckQuery extends PageQuery {
  checkNo?: string
  warehouseId?: string | number
  status?: string
  startDate?: string
  endDate?: string
  dateFrom?: string
  dateTo?: string
}

export interface InventoryCheckCreateRequest {
  warehouseId: string | number
  checkDate: string
  items: InventoryCheckItem[]
  remark?: string
}

export interface InventoryCheckUpdateRequest {
  items: InventoryCheckItem[]
}

// 库存盘点API
export const getInventoryChecks = (params: InventoryCheckQuery) => {
  return request.get<PageResponse<InventoryCheck>>('/inventory/checks', {
    params: toStockCheckQueryParams(params)
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizeInventoryCheck)
  }))
}

export const getInventoryCheck = (id: string | number) => {
  return request.get<InventoryCheck>(`/inventory/checks/${id}`).then(normalizeInventoryCheck)
}

export const createInventoryCheck = (data: InventoryCheckCreateRequest) => {
  return request.post<InventoryCheck>('/inventory/checks', toStockCheckCreatePayload(data))
    .then(normalizeInventoryCheck)
}

export const updateInventoryCheck = (id: string | number, data: InventoryCheckUpdateRequest) => {
  return request.put<InventoryCheck>(`/inventory/checks/${id}`, toStockCheckUpdatePayload(data))
    .then(normalizeInventoryCheck)
}

export const completeInventoryCheck = (id: string | number) => {
  return request.post(`/inventory/checks/${id}/adjust`)
}

export const cancelInventoryCheck = (id: string | number) => {
  return request.post(`/inventory/checks/${id}/cancel`)
}

const toStockCheckQueryParams = (params: InventoryCheckQuery) => {
  const { startDate, endDate, ...rest } = params
  return {
    ...rest,
    dateFrom: startDate || params.dateFrom || undefined,
    dateTo: endDate || params.dateTo || undefined
  }
}

const toStockCheckCreatePayload = (data: InventoryCheckCreateRequest) => ({
  warehouseId: data.warehouseId,
  checkDate: data.checkDate,
  remark: data.remark,
  lines: data.items.map(toStockCheckLinePayload)
})

const toStockCheckUpdatePayload = (data: InventoryCheckUpdateRequest) => ({
  items: data.items.map(toStockCheckLinePayload)
})

const toStockCheckLinePayload = (item: InventoryCheckItem) => ({
  id: item.id,
  productId: item.productId,
  actualQty: item.actualQuantity ?? item.bookQuantity ?? 0,
  unitCost: item.unitCost ?? 0,
  lotNo: item.lotNo,
  productionDate: item.productionDate,
  expiryDate: item.expiryDate,
  locationId: item.locationId || undefined,
  remark: item.remark
})

const normalizeInventoryCheck = (check: InventoryCheck): InventoryCheck => {
  const lines = check.lines || []
  return {
    ...check,
    items: check.items || lines.map((line) => ({
      id: line.id,
      productId: line.productId,
      locationId: line.locationId != null ? String(line.locationId) : line.locationId,
      bookQuantity: line.bookQty ?? 0,
      actualQuantity: line.actualQty,
      difference: line.differenceQty,
      unitCost: line.unitCost,
      differenceAmount: line.differenceAmount,
      lotNo: line.lotNo,
      productionDate: line.productionDate,
      expiryDate: line.expiryDate,
      remark: line.remark
    }))
  }
}

// ==================== 库存调拨 ====================

export interface InventoryTransfer {
  id: string | number
  transferNo: string
  fromWarehouseId: string | number
  fromWarehouseName?: string
  toWarehouseId: string | number
  toWarehouseName?: string
  transferDate: string
  status: 'DRAFT' | 'POSTED' | 'COMPLETED' | 'CANCELLED'
  items: InventoryTransferItem[]
  lines?: InventoryTransferLineResponse[]
  remark?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface InventoryTransferItem {
  id?: string | number
  productId: string | number
  productCode?: string
  productName?: string
  quantity: number
  qty?: number
  unitCost?: number
  lotNo?: string
  productionDate?: string
  expiryDate?: string
  fromLocationId?: string | number | null
  toLocationId?: string | number | null
  serialNos?: string
  remark?: string
}

interface InventoryTransferLineResponse {
  id?: string | number
  lineNo?: number
  productId: string | number
  productCode?: string
  productName?: string
  qty?: number
  quantity?: number
  unitCost?: number
  amount?: number
  lotNo?: string
  productionDate?: string
  expiryDate?: string
  fromLocationId?: string | number | null
  toLocationId?: string | number | null
  serialNos?: string
  remark?: string
}

export interface InventoryTransferQuery extends PageQuery {
  transferNo?: string
  fromWarehouseId?: string | number
  toWarehouseId?: string | number
  status?: string
  startDate?: string
  endDate?: string
  dateFrom?: string
  dateTo?: string
}

export interface InventoryTransferCreateRequest {
  fromWarehouseId: string | number
  toWarehouseId: string | number
  transferDate: string
  items: InventoryTransferItem[]
  remark?: string
}

// 库存调拨API
export const getInventoryTransfers = (params: InventoryTransferQuery) => {
  return request.get<PageResponse<InventoryTransfer>>('/inventory/transfers', {
    params: toTransferQueryParams(params)
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizeInventoryTransfer)
  }))
}

export const getInventoryTransfer = (id: string | number) => {
  return request.get<InventoryTransfer>(`/inventory/transfers/${id}`).then(normalizeInventoryTransfer)
}

export const createInventoryTransfer = (data: InventoryTransferCreateRequest) => {
  return request.post<InventoryTransfer>('/inventory/transfers', toTransferPayload(data))
    .then(normalizeInventoryTransfer)
}

export const shipInventoryTransfer = (id: string | number) => {
  return request.post<InventoryTransfer>(`/inventory/transfers/${id}/post`).then(normalizeInventoryTransfer)
}

export const cancelInventoryTransfer = (id: string | number) => {
  return request.post(`/inventory/transfers/${id}/cancel`)
}

const toTransferQueryParams = (params: InventoryTransferQuery) => {
  const { startDate, endDate, ...rest } = params
  return {
    ...rest,
    dateFrom: startDate || params.dateFrom || undefined,
    dateTo: endDate || params.dateTo || undefined
  }
}

const normalizeInventoryTransferItem = (item: InventoryTransferItem | InventoryTransferLineResponse): InventoryTransferItem => ({
  ...item,
  id: item.id,
  productId: item.productId,
  productCode: item.productCode || '',
  productName: item.productName || '',
  quantity: Number(item.quantity ?? item.qty ?? 0),
  unitCost: Number(item.unitCost ?? 0),
  fromLocationId: item.fromLocationId != null ? String(item.fromLocationId) : item.fromLocationId,
  toLocationId: item.toLocationId != null ? String(item.toLocationId) : item.toLocationId,
  remark: item.remark || ''
})

const normalizeInventoryTransfer = (transfer: InventoryTransfer): InventoryTransfer => {
  const items = (transfer.items ?? transfer.lines ?? []).map(normalizeInventoryTransferItem)
  return {
    ...transfer,
    id: transfer.id,
    fromWarehouseId: transfer.fromWarehouseId,
    toWarehouseId: transfer.toWarehouseId,
    status: transfer.status === 'POSTED' ? 'COMPLETED' : transfer.status,
    items
  }
}

const toTransferPayload = (data: InventoryTransferCreateRequest) => ({
  fromWarehouseId: data.fromWarehouseId,
  toWarehouseId: data.toWarehouseId,
  transferDate: data.transferDate,
  remark: data.remark,
  lines: data.items.map((item) => ({
    productId: item.productId,
    qty: item.quantity,
    unitCost: item.unitCost ?? 0,
    lotNo: item.lotNo || undefined,
    productionDate: item.productionDate || undefined,
    expiryDate: item.expiryDate || undefined,
    fromLocationId: item.fromLocationId || undefined,
    toLocationId: item.toLocationId || undefined,
    serialNos: item.serialNos || undefined,
    remark: item.remark
  }))
})

// ==================== 库存预警 ====================

export interface InventoryAlert {
  id: string
  ruleId: string
  warehouseId: string
  warehouseName: string
  productId: string
  productCode: string
  productName: string
  currentQuantity: number
  qtyOnHand?: number
  minQuantity: number
  minQty?: number
  shortageQty: number
  maxQuantity?: number | null
  alertType: 'LOW_STOCK' | 'OUT_OF_STOCK'
  alertDate?: string
  status: 'ACTIVE' | 'IGNORED' | 'RESOLVED'
  remark?: string
}

export interface InventoryAlertQuery extends PageQuery {
  warehouseId?: string | number
  productId?: string | number
  alertType?: string
  status?: string
}

export interface InventoryAlertRule {
  id: string
  warehouseId: string
  productId: string
  minQty: number
  enabled: boolean
  remark?: string
}

export interface InventoryAlertRuleCreateRequest {
  warehouseId: string | number
  productId: string | number
  minQty: number
  remark?: string
}

export interface InventoryAlertHandleRequest {
  warehouseId: string | number
  productId: string | number
  remark?: string
}

// 库存预警API
export const getInventoryAlerts = (params: InventoryAlertQuery) => {
  return request.get<InventoryAlert[]>('/inventory/low-stock', {
    params: {
      warehouseId: params.warehouseId,
      productId: params.productId
    }
  }).then((items) => paginateInventoryAlerts(items.map(normalizeInventoryAlert), params))
}

export const createInventoryAlertRule = (data: InventoryAlertRuleCreateRequest) => {
  return request.post<InventoryAlertRule>('/inventory/alert-rules', data).then(normalizeInventoryAlertRule)
}

// 忽略某仓某商品的当前低库存命中（后端写入处置记录 status=IGNORED）
export const ignoreInventoryAlert = (data: InventoryAlertHandleRequest) => {
  return request.post<void>('/inventory/low-stock/ignore', data)
}

// 标记某仓某商品的低库存命中为已处理（后端写入处置记录 status=RESOLVED）
export const resolveInventoryAlert = (data: InventoryAlertHandleRequest) => {
  return request.post<void>('/inventory/low-stock/resolve', data)
}

// 撤销处置记录，让该仓商品低库存命中重新显示为待处置
export const reactivateInventoryAlert = (data: InventoryAlertHandleRequest) => {
  return request.post<void>('/inventory/low-stock/reactivate', data)
}

// ==================== 补货建议 ====================

export interface InventoryReplenishmentSuggestion {
  id: string
  suggestionNo: string
  sourceType: string
  sourceRuleId: string
  warehouseId: string
  warehouseName?: string
  productId: string
  productCode?: string
  productName?: string
  supplierId?: string
  supplierName?: string
  suggestedQty: number
  shortageQtySnapshot: number
  expectedArrivalDate?: string
  status: 'DRAFT' | 'CONVERTED' | 'CANCELLED'
  fulfillmentStatus: 'SUGGESTED' | 'PURCHASE_CREATED' | 'PARTIAL_RECEIVED' | 'REPLENISHED' | 'PURCHASE_CLOSED' | 'CANCELLED' | string
  purchaseOrderId?: string
  purchaseOrderNo?: string
  remark?: string
  createdTime?: string
}

export interface InventoryReplenishmentSuggestionQuery extends PageQuery {
  suggestionNo?: string
  status?: string
  warehouseId?: string | number
  productId?: string | number
  supplierId?: string | number
  createdTimeFrom?: string
  createdTimeTo?: string
}

export interface InventoryReplenishmentSuggestionCreateRequest {
  ruleId: string | number
  warehouseId: string | number
  productId: string | number
  supplierId?: string | number
  suggestedQty: number
  expectedArrivalDate?: string
  remark?: string
}

export interface InventoryReplenishmentSuggestionUpdateRequest {
  supplierId?: string | number
  suggestedQty: number
  expectedArrivalDate?: string
  remark?: string
}

export const getInventoryReplenishmentSuggestions = (params: InventoryReplenishmentSuggestionQuery) => {
  return request.get<PageResponse<InventoryReplenishmentSuggestion>>('/inventory/replenishment-suggestions', { params })
    .then((page) => ({
      ...page,
      records: page.records.map(normalizeInventoryReplenishmentSuggestion)
    }))
}

export const createInventoryReplenishmentSuggestion = (
  data: InventoryReplenishmentSuggestionCreateRequest
) => {
  return request.post<InventoryReplenishmentSuggestion>('/inventory/replenishment-suggestions', data)
    .then(normalizeInventoryReplenishmentSuggestion)
}

export const updateInventoryReplenishmentSuggestion = (
  id: string | number,
  data: InventoryReplenishmentSuggestionUpdateRequest
) => {
  return request.put<InventoryReplenishmentSuggestion>(`/inventory/replenishment-suggestions/${id}`, data)
    .then(normalizeInventoryReplenishmentSuggestion)
}

export const cancelInventoryReplenishmentSuggestion = (id: string | number, reason?: string) => {
  return request.post<InventoryReplenishmentSuggestion>(
    `/inventory/replenishment-suggestions/${id}/cancel`,
    { reason }
  ).then(normalizeInventoryReplenishmentSuggestion)
}

export const convertInventoryReplenishmentSuggestion = (id: string | number) => {
  return request.post<InventoryReplenishmentSuggestion>(
    `/inventory/replenishment-suggestions/${id}/convert-to-purchase-order`
  ).then(normalizeInventoryReplenishmentSuggestion)
}

const normalizeInventoryReplenishmentSuggestion = (
  suggestion: InventoryReplenishmentSuggestion
): InventoryReplenishmentSuggestion => ({
  ...suggestion,
  id: String(suggestion.id),
  sourceRuleId: String(suggestion.sourceRuleId),
  warehouseId: String(suggestion.warehouseId),
  productId: String(suggestion.productId),
  supplierId: suggestion.supplierId != null ? String(suggestion.supplierId) : undefined,
  purchaseOrderId: suggestion.purchaseOrderId != null ? String(suggestion.purchaseOrderId) : undefined,
  suggestedQty: Number(suggestion.suggestedQty ?? 0),
  shortageQtySnapshot: Number(suggestion.shortageQtySnapshot ?? 0),
  fulfillmentStatus: suggestion.fulfillmentStatus || (
    suggestion.status === 'DRAFT'
      ? 'SUGGESTED'
      : suggestion.status === 'CANCELLED'
        ? 'CANCELLED'
        : 'PURCHASE_CREATED'
  )
})

const normalizeInventoryAlertRule = (rule: InventoryAlertRule): InventoryAlertRule => ({
  ...rule,
  id: String(rule.id),
  warehouseId: String(rule.warehouseId),
  productId: String(rule.productId),
  minQty: Number(rule.minQty ?? 0),
  enabled: Boolean(rule.enabled)
})

const normalizeInventoryAlert = (alert: InventoryAlert): InventoryAlert => {
  const currentQuantity = Number(alert.currentQuantity ?? alert.qtyOnHand ?? 0)
  const minQuantity = Number(alert.minQuantity ?? alert.minQty ?? 0)

  return {
    ...alert,
    id: String(alert.id ?? alert.ruleId),
    ruleId: String(alert.ruleId ?? alert.id),
    warehouseId: String(alert.warehouseId),
    productId: String(alert.productId),
    warehouseName: alert.warehouseName || `仓库 ${alert.warehouseId}`,
    productCode: alert.productCode || String(alert.productId),
    productName: alert.productName || `产品 ${alert.productId}`,
    currentQuantity,
    minQuantity,
    shortageQty: Number(alert.shortageQty ?? Math.max(minQuantity - currentQuantity, 0)),
    maxQuantity: alert.maxQuantity ?? null,
    alertType: currentQuantity <= 0 ? 'OUT_OF_STOCK' : 'LOW_STOCK',
    // 后端派生处置状态：ACTIVE（未处置/缺口恶化后失效）/ IGNORED / RESOLVED
    status: alert.status === 'IGNORED' || alert.status === 'RESOLVED' ? alert.status : 'ACTIVE'
  }
}

const paginateInventoryAlerts = (
  items: InventoryAlert[],
  params: InventoryAlertQuery
): PageResponse<InventoryAlert> => {
  const pageNo = Number(params.pageNo ?? params.page ?? 1)
  const pageSize = Number(params.pageSize ?? params.size ?? 10)
  const filtered = items.filter((item) => {
    if (params.status && item.status !== params.status) {
      return false
    }
    if (params.alertType === 'OVER_STOCK') {
      return false
    }
    if (params.alertType && item.alertType !== params.alertType) {
      return false
    }
    return true
  })
  const start = (pageNo - 1) * pageSize

  return {
    records: filtered.slice(start, start + pageSize),
    total: filtered.length,
    pageNo,
    pageSize
  }
}

// ==================== 轻量 MRP ====================

export interface MrpSuggestionLine {
  productId: string | number
  productCode?: string
  productName?: string
  suggestionType: string
  demandQty: number
  onHandQty: number
  openSupplyQty: number
  netQty: number
  bomId?: string | number | null
  reason?: string
}

export interface MrpSuggestionLine {
  id?: string | number
  runId?: string | number
  lineNo?: number
  productId: string | number
  productCode?: string
  productName?: string
  suggestionType: string
  demandQty: number
  onHandQty: number
  openSupplyQty: number
  netQty: number
  bomId?: string | number | null
  reason?: string
  status?: string
  convertedBizType?: string | null
  convertedBizId?: string | number | null
  convertedBizNo?: string | null
  convertedTime?: string | null
}

export interface MrpRunResult {
  id?: string | number
  runNo?: string
  asOfDate: string
  status?: string
  purchaseCount: number
  productionCount: number
  createdTime?: string
  purchaseLines: MrpSuggestionLine[]
  productionLines: MrpSuggestionLine[]
}

export interface MrpRunSummary {
  id: string | number
  runNo: string
  asOfDate: string
  status: string
  purchaseCount: number
  productionCount: number
  createdTime?: string
}

const normalizeMrpLine = (line: MrpSuggestionLine): MrpSuggestionLine => ({
  ...line,
  id: line.id != null ? String(line.id) : line.id,
  runId: line.runId != null ? String(line.runId) : line.runId,
  productId: String(line.productId),
  bomId: line.bomId != null ? String(line.bomId) : line.bomId,
  demandQty: Number(line.demandQty ?? 0),
  onHandQty: Number(line.onHandQty ?? 0),
  openSupplyQty: Number(line.openSupplyQty ?? 0),
  netQty: Number(line.netQty ?? 0),
  convertedBizId: line.convertedBizId != null ? String(line.convertedBizId) : line.convertedBizId
})

const normalizeMrpRun = (r: MrpRunResult): MrpRunResult => ({
  ...r,
  id: r.id != null ? String(r.id) : r.id,
  purchaseCount: Number(r.purchaseCount ?? 0),
  productionCount: Number(r.productionCount ?? 0),
  purchaseLines: (r.purchaseLines || []).map(normalizeMrpLine),
  productionLines: (r.productionLines || []).map(normalizeMrpLine)
})

export const runMrpPlan = () => {
  return request.post<MrpRunResult>('/inventory/mrp/run').then(normalizeMrpRun)
}

export const getMrpRuns = (params?: { pageNo?: number; pageSize?: number; status?: string }) => {
  return request.get<PageResponse<MrpRunSummary>>('/inventory/mrp/runs', { params }).then((page) => ({
    ...page,
    records: (page.records || []).map((item) => ({
      ...item,
      id: String(item.id),
      purchaseCount: Number(item.purchaseCount ?? 0),
      productionCount: Number(item.productionCount ?? 0)
    }))
  }))
}

export const getMrpRun = (id: string | number) => {
  return request.get<MrpRunResult>(`/inventory/mrp/runs/${id}`).then(normalizeMrpRun)
}

export const convertMrpLine = (
  runId: string | number,
  lineId: string | number,
  data?: { supplierId?: string | number; finishedWarehouseId?: string | number; materialWarehouseId?: string | number }
) => {
  return request
    .post<MrpSuggestionLine>(`/inventory/mrp/runs/${runId}/lines/${lineId}/convert`, data || {})
    .then(normalizeMrpLine)
}


export interface InventorySerial {
  id: string | number
  productId: string | number
  productCode?: string
  productName?: string
  warehouseId?: string | number | null
  locationId?: string | number | null
  serialNo: string
  status: string
  inboundBizType?: string
  inboundBizNo?: string
  outboundBizType?: string
  outboundBizNo?: string
  remark?: string
  updatedTime?: string
}

export const getInventorySerials = (params: PageQuery & { productId?: string | number; warehouseId?: string | number; locationId?: string | number; status?: string; keyword?: string }) => {
  return request.get<PageResponse<InventorySerial>>('/inventory/serials', { params }).then((page) => ({
    ...page,
    records: (page.records || []).map((item) => ({
      ...item,
      id: String(item.id),
      productId: String(item.productId),
      warehouseId: item.warehouseId != null ? String(item.warehouseId) : item.warehouseId,
      locationId: item.locationId != null ? String(item.locationId) : item.locationId
    }))
  }))
}

export const createInventorySerial = (data: { productId: string | number; warehouseId?: string | number; locationId?: string | number; serialNo: string; inboundBizType?: string; inboundBizNo?: string; remark?: string }) => {
  return request.post<InventorySerial>('/inventory/serials', data)
}

export const issueInventorySerial = (id: string | number, data?: { outboundBizType?: string; outboundBizNo?: string }) => {
  return request.post<InventorySerial>(`/inventory/serials/${id}/issue`, data || {})
}

export const scrapInventorySerial = (id: string | number) => {
  return request.post<InventorySerial>(`/inventory/serials/${id}/scrap`)
}
