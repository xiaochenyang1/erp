import { request } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

// 采购订单
export interface PurchaseOrder {
  id: string | number
  orderNo: string
  supplierId: string | number
  supplierName: string
  orderDate: string
  expectedDate?: string
  deliveryDate?: string
  totalAmount: number
  totalTaxAmount?: number
  totalQuantity?: number
  status: 'DRAFT' | 'SUBMITTED' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'CLOSED' | 'COMPLETED' | 'CANCELLED'
  approvalStatus?: 'NOT_SUBMITTED' | 'IN_APPROVAL' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | string
  receiptStatus?: 'NOT_RECEIVED' | 'PARTIAL_RECEIVED' | 'RECEIVED' | string
  sourceInquiryId?: string | number | null
  sourceInquiryNo?: string | null
  sourceQuoteId?: string | number | null
  items: PurchaseOrderItem[]
  lines?: PurchaseOrderItem[]
  remark?: string
  createdBy: string
  createdAt: string
  updatedAt: string
}

// 采购订单明细
export interface PurchaseOrderItem {
  auxQty?: number | null
  auxUnitName?: string
  conversionFactor?: number | null
  id?: string | number
  productId: string | number
  productCode?: string
  productName?: string
  qty?: number
  quantity: number
  price: number
  taxRate?: number
  amount: number
  taxAmount?: number
  receivedQty?: number
  sourceInquiryId?: string | number | null
  sourceInquiryLineId?: string | number | null
  remark?: string
}

export interface PurchaseOrderExecutionInfo {
  orderedQty: number
  receivedQty: number
  remainingReceiptQty: number
  receiptStatus: string
}

export interface PurchaseOrderDocumentSummary {
  id: string
  documentNo: string
  documentType: string
  documentDate?: string
  status: string
  amount: number
}

export interface PurchaseOrderRelatedDocs {
  receipts: PurchaseOrderDocumentSummary[]
  returns: PurchaseOrderDocumentSummary[]
  payables: PurchaseOrderDocumentSummary[]
  payments: PurchaseOrderDocumentSummary[]
  vouchers: PurchaseOrderDocumentSummary[]
}

export interface PurchaseOrderTrace {
  order: PurchaseOrder
  approvalInfo?: unknown
  executionInfo: PurchaseOrderExecutionInfo
  relatedDocs: PurchaseOrderRelatedDocs
}

// 采购订单查询参数
export interface PurchaseOrderQuery extends PageQuery {
  orderNo?: string
  keyword?: string
  supplierId?: string | number
  status?: string
  approvalStatus?: string
  startDate?: string
  endDate?: string
}

// 采购订单保存请求
export interface PurchaseOrderSaveRequest {
  supplierId: string | number
  orderDate: string
  expectedDate?: string
  deliveryDate?: string
  items: PurchaseOrderItem[]
  remark?: string
}

/**
 * 获取采购订单列表
 */
export const getPurchaseOrders = (params: PurchaseOrderQuery) => {
  return request.get<PageResponse<PurchaseOrder>>('/purchase/orders', {
    params: toPurchaseOrderQueryParams(params)
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizePurchaseOrder)
  }))
}

/**
 * 获取采购订单详情
 */
export const getPurchaseOrder = (id: string | number) => {
  return request.get<PurchaseOrder>(`/purchase/orders/${id}`).then(normalizePurchaseOrder)
}

/**
 * 创建采购订单
 */
export const createPurchaseOrder = (data: PurchaseOrderSaveRequest) => {
  return request.post<PurchaseOrder>('/purchase/orders', toPurchaseOrderPayload(data)).then(normalizePurchaseOrder)
}

/**
 * 更新采购订单
 */
export const updatePurchaseOrder = (id: string | number, data: PurchaseOrderSaveRequest) => {
  return request.put<PurchaseOrder>(`/purchase/orders/${id}`, toPurchaseOrderPayload(data)).then(normalizePurchaseOrder)
}

/**
 * 提交审批
 */
export const submitPurchaseOrder = (id: string | number) => {
  return request.post(`/purchase/orders/${id}/submit`)
}

/**
 * 审批通过
 */
export const approvePurchaseOrder = (id: string | number) => {
  return request.post(`/purchase/orders/${id}/approve`)
}

/**
 * 反审核
 */
export const unapprovePurchaseOrder = (id: string | number) => {
  return request.post(`/purchase/orders/${id}/unapprove`)
}

/**
 * 审批驳回
 */
export const rejectPurchaseOrder = (id: string | number, reason: string) => {
  return request.post(`/purchase/orders/${id}/reject`, { reason })
}

/**
 * 取消采购订单
 */
export const cancelPurchaseOrder = (id: string | number) => {
  return request.post<PurchaseOrder>(`/purchase/orders/${id}/cancel`).then(normalizePurchaseOrder)
}

/**
 * 关闭采购订单
 */
export const closePurchaseOrder = (id: string | number) => {
  return request.post<PurchaseOrder>(`/purchase/orders/${id}/close`).then(normalizePurchaseOrder)
}

/**
 * 采购订单跟踪
 */
export const tracePurchaseOrder = (id: string | number) => {
  return request.get<PurchaseOrderTrace>(`/purchase/orders/${id}/trace`).then(normalizePurchaseOrderTrace)
}

/**
 * 导出采购订单
 */
export const exportPurchaseOrders = (params: PurchaseOrderQuery) => {
  return request.get<Blob>('/purchase/orders/export', {
    params: toPurchaseOrderQueryParams(params),
    responseType: 'blob'
  })
}

const toPurchaseOrderQueryParams = (params: PurchaseOrderQuery) => {
  const { orderNo, startDate, endDate, ...rest } = params
  return {
    ...rest,
    keyword: params.keyword || orderNo || undefined,
    dateFrom: startDate || undefined,
    dateTo: endDate || undefined
  }
}

const toPurchaseOrderPayload = (data: PurchaseOrderSaveRequest) => ({
  supplierId: data.supplierId,
  orderDate: data.orderDate,
  deliveryDate: data.deliveryDate || data.expectedDate || undefined,
  remark: data.remark,
  lines: data.items.map((item) => ({
    productId: item.productId,
    qty: item.qty ?? item.quantity,
    auxQty: item.auxQty ?? undefined,
    auxUnitName: item.auxUnitName || undefined,
    conversionFactor: item.conversionFactor ?? undefined,
    price: item.price,
    taxRate: item.taxRate ?? 0,
    remark: item.remark
  }))
})

const normalizePurchaseOrderLine = (item: PurchaseOrderItem): PurchaseOrderItem => ({
  ...item,
  id: item.id != null ? String(item.id) : undefined,
  productId: item.productId != null ? String(item.productId) : item.productId,
  quantity: Number(item.quantity ?? item.qty ?? 0),
  qty: Number(item.qty ?? item.quantity ?? 0),
  auxQty: item.auxQty != null ? Number(item.auxQty) : item.auxQty,
  conversionFactor: item.conversionFactor != null ? Number(item.conversionFactor) : item.conversionFactor,
  price: Number(item.price ?? 0),
  taxRate: Number(item.taxRate ?? 0),
  amount: Number(item.amount ?? 0),
  taxAmount: Number(item.taxAmount ?? 0),
  receivedQty: Number(item.receivedQty ?? 0),
  sourceInquiryId: item.sourceInquiryId != null ? String(item.sourceInquiryId) : item.sourceInquiryId,
  sourceInquiryLineId: item.sourceInquiryLineId != null ? String(item.sourceInquiryLineId) : item.sourceInquiryLineId
})

const normalizePurchaseOrder = (order: PurchaseOrder): PurchaseOrder => {
  const lines = (order.lines || order.items || []).map(normalizePurchaseOrderLine)
  return {
    ...order,
    id: String(order.id),
    supplierId: String(order.supplierId),
    sourceInquiryId: order.sourceInquiryId != null ? String(order.sourceInquiryId) : order.sourceInquiryId,
    sourceQuoteId: order.sourceQuoteId != null ? String(order.sourceQuoteId) : order.sourceQuoteId,
    expectedDate: order.expectedDate || order.deliveryDate,
    totalAmount: Number(order.totalAmount ?? 0),
    totalTaxAmount: Number(order.totalTaxAmount ?? 0),
    totalQuantity: Number(order.totalQuantity ?? 0),
    items: lines,
    lines,
    createdAt: order.createdAt || '',
    updatedAt: order.updatedAt || ''
  }
}

const normalizePurchaseOrderDocumentSummary = (document: PurchaseOrderDocumentSummary): PurchaseOrderDocumentSummary => ({
  ...document,
  id: String(document.id),
  amount: Number(document.amount ?? 0)
})

const normalizePurchaseOrderTrace = (trace: PurchaseOrderTrace): PurchaseOrderTrace => ({
  ...trace,
  order: normalizePurchaseOrder(trace.order),
  executionInfo: {
    ...trace.executionInfo,
    orderedQty: Number(trace.executionInfo?.orderedQty ?? 0),
    receivedQty: Number(trace.executionInfo?.receivedQty ?? 0),
    remainingReceiptQty: Number(trace.executionInfo?.remainingReceiptQty ?? 0),
    receiptStatus: trace.executionInfo?.receiptStatus || ''
  },
  relatedDocs: {
    receipts: (trace.relatedDocs?.receipts || []).map(normalizePurchaseOrderDocumentSummary),
    returns: (trace.relatedDocs?.returns || []).map(normalizePurchaseOrderDocumentSummary),
    payables: (trace.relatedDocs?.payables || []).map(normalizePurchaseOrderDocumentSummary),
    payments: (trace.relatedDocs?.payments || []).map(normalizePurchaseOrderDocumentSummary),
    vouchers: (trace.relatedDocs?.vouchers || []).map(normalizePurchaseOrderDocumentSummary)
  }
})

// ==================== 采购收货 ====================

// 采购收货单
export interface PurchaseReceipt {
  id: string | number
  receiptNo: string
  orderId: string | number
  orderNo: string
  supplierId?: string | number
  supplierName: string
  warehouseId: string | number
  warehouseName: string
  receiptDate: string
  status: 'DRAFT' | 'POSTED' | 'COMPLETED' | 'CANCELLED'
  totalAmount?: number
  totalTaxAmount?: number
  totalQuantity?: number
  items: PurchaseReceiptItem[]
  lines?: PurchaseReceiptItem[]
  remark?: string
  createdBy: string
  createdAt: string
  updatedAt: string
}

// 采购收货明细
export interface PurchaseReceiptItem {
  id?: string | number
  orderItemId?: string | number
  orderLineId?: string | number
  productId: string | number
  productCode?: string
  productName?: string
  orderedQuantity?: number
  receivedQuantity?: number
  qty?: number
  quantity: number
  price?: number
  taxRate?: number
  amount?: number
  taxAmount?: number
  returnedQty?: number
  availableReturnQty?: number
  lotNo?: string
  productionDate?: string
  expiryDate?: string
  locationId?: string | number | null
  serialNos?: string
  remark?: string
}

// 采购收货查询参数
export interface PurchaseReceiptQuery extends PageQuery {
  receiptNo?: string
  keyword?: string
  orderId?: string | number
  supplierId?: string | number
  warehouseId?: string | number
  status?: string
  startDate?: string
  endDate?: string
  receiptDateFrom?: string
  receiptDateTo?: string
}

// 采购收货创建请求
export interface PurchaseReceiptCreateRequest {
  orderId: string | number
  warehouseId: string | number
  receiptDate: string
  items: PurchaseReceiptItem[]
  remark?: string
}

/**
 * 获取采购收货列表
 */
export const getPurchaseReceipts = (params: PurchaseReceiptQuery) => {
  return request.get<PageResponse<PurchaseReceipt>>('/purchase/receipts', {
    params: toPurchaseReceiptQueryParams(params)
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizePurchaseReceipt)
  }))
}

/**
 * 获取采购收货详情
 */
export const getPurchaseReceipt = (id: string | number) => {
  return request.get<PurchaseReceipt>(`/purchase/receipts/${id}`).then(normalizePurchaseReceipt)
}

/**
 * 创建采购收货
 */
export const createPurchaseReceipt = (data: PurchaseReceiptCreateRequest) => {
  return request.post<PurchaseReceipt>('/purchase/receipts', toPurchaseReceiptPayload(data)).then(normalizePurchaseReceipt)
}

/**
 * 更新采购收货（仅草稿）
 */
export const updatePurchaseReceipt = (id: string | number, data: PurchaseReceiptCreateRequest) => {
  return request.put<PurchaseReceipt>(`/purchase/receipts/${id}`, toPurchaseReceiptPayload(data)).then(normalizePurchaseReceipt)
}

/**
 * 完成收货（过账）
 */
export const completePurchaseReceipt = (id: string | number) => {
  return request.post(`/purchase/receipts/${id}/post`)
}

/**
 * 取消收货
 */
export const cancelPurchaseReceipt = (id: string | number) => {
  return request.post(`/purchase/receipts/${id}/cancel`)
}

/**
 * 导出采购收货
 */
export const exportPurchaseReceipts = (params: PurchaseReceiptQuery) => {
  return request.get<Blob>('/purchase/receipts/export', {
    params: toPurchaseReceiptQueryParams(params),
    responseType: 'blob'
  })
}

const toPurchaseReceiptQueryParams = (params: PurchaseReceiptQuery) => {
  const { receiptNo, startDate, endDate, supplierId: _supplierId, ...rest } = params
  return {
    ...rest,
    keyword: params.keyword || receiptNo || undefined,
    receiptDateFrom: params.receiptDateFrom || startDate || undefined,
    receiptDateTo: params.receiptDateTo || endDate || undefined
  }
}

const toPurchaseReceiptPayload = (data: PurchaseReceiptCreateRequest) => ({
  orderId: data.orderId,
  warehouseId: data.warehouseId,
  receiptDate: data.receiptDate,
  remark: data.remark,
  lines: data.items.map((item) => ({
    orderLineId: item.orderLineId || item.orderItemId,
    qty: item.quantity,
    lotNo: item.lotNo || undefined,
    productionDate: item.productionDate || undefined,
    expiryDate: item.expiryDate || undefined,
    locationId: item.locationId || undefined,
    serialNos: item.serialNos || undefined,
    remark: item.remark
  }))
})

const normalizePurchaseReceiptLine = (item: PurchaseReceiptItem): PurchaseReceiptItem => ({
  ...item,
  id: item.id != null ? String(item.id) : undefined,
  orderItemId: item.orderItemId != null ? String(item.orderItemId) : item.orderLineId != null ? String(item.orderLineId) : undefined,
  orderLineId: item.orderLineId != null ? String(item.orderLineId) : item.orderItemId != null ? String(item.orderItemId) : undefined,
  productId: item.productId != null ? String(item.productId) : item.productId,
  locationId: item.locationId != null ? String(item.locationId) : item.locationId,
  quantity: Number(item.quantity ?? item.qty ?? 0),
  qty: Number(item.qty ?? item.quantity ?? 0),
  price: Number(item.price ?? 0),
  taxRate: Number(item.taxRate ?? 0),
  amount: Number(item.amount ?? 0),
  taxAmount: Number(item.taxAmount ?? 0)
})

const normalizePurchaseReceipt = (receipt: PurchaseReceipt): PurchaseReceipt => {
  const lines = (receipt.lines || receipt.items || []).map(normalizePurchaseReceiptLine)
  return {
    ...receipt,
    id: String(receipt.id),
    orderId: String(receipt.orderId),
    warehouseId: String(receipt.warehouseId),
    orderNo: receipt.orderNo || String(receipt.orderId || ''),
    supplierName: receipt.supplierName || '',
    warehouseName: receipt.warehouseName || String(receipt.warehouseId || ''),
    totalAmount: Number(receipt.totalAmount ?? 0),
    totalTaxAmount: Number(receipt.totalTaxAmount ?? 0),
    totalQuantity: Number(receipt.totalQuantity ?? 0),
    items: lines,
    lines,
    createdAt: receipt.createdAt || '',
    updatedAt: receipt.updatedAt || ''
  }
}

// ==================== 采购退货 ====================

// 采购退货单
export interface PurchaseReturn {
  id: string | number
  returnNo: string
  receiptId: string | number
  receiptNo?: string
  orderNo?: string
  supplierId?: string | number
  supplierName?: string
  warehouseId: string | number
  warehouseName: string
  returnDate: string
  totalAmount: number
  totalQuantity?: number
  totalTaxAmount?: number
  status: 'DRAFT' | 'POSTED' | 'COMPLETED' | 'CANCELLED'
  items: PurchaseReturnItem[]
  lines?: PurchaseReturnLineResponse[]
  remark?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

// 采购退货明细
export interface PurchaseReturnItem {
  id?: string | number
  receiptLineId?: string | number
  orderLineId?: string | number
  productId: string | number
  productCode?: string
  productName?: string
  receiptQty?: number
  returnedQty?: number
  availableReturnQty?: number
  qty?: number
  quantity: number
  price: number
  taxRate?: number
  amount: number
  taxAmount?: number
  lotNo?: string
  productionDate?: string
  expiryDate?: string
  locationId?: string | number | null
  serialNos?: string
  reason?: string
  remark?: string
}

export interface PurchaseReturnLineResponse {
  id?: string | number
  lineNo?: number
  receiptLineId?: string | number
  orderLineId?: string | number
  productId: string | number
  productCode?: string
  productName?: string
  qty?: number
  quantity?: number
  price?: number
  taxRate?: number
  amount?: number
  taxAmount?: number
  receiptQty?: number
  returnedQty?: number
  availableReturnQty?: number
  lotNo?: string
  productionDate?: string
  expiryDate?: string
  locationId?: string | number | null
  serialNos?: string
  reason?: string
  remark?: string
}

// 采购退货查询参数
export interface PurchaseReturnQuery extends PageQuery {
  returnNo?: string
  keyword?: string
  receiptId?: string | number
  supplierId?: string | number
  warehouseId?: string | number
  status?: string
  startDate?: string
  endDate?: string
  returnDateFrom?: string
  returnDateTo?: string
}

// 采购退货创建请求
export interface PurchaseReturnCreateRequest {
  receiptId: string | number
  returnDate: string
  items: PurchaseReturnItem[]
  remark?: string
}

/**
 * 获取采购退货列表
 */
export const getPurchaseReturns = (params: PurchaseReturnQuery) => {
  return request.get<PageResponse<PurchaseReturn>>('/purchase/returns', {
    params: toPurchaseReturnQueryParams(params)
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizePurchaseReturn)
  }))
}

/**
 * 获取采购退货详情
 */
export const getPurchaseReturn = (id: string | number) => {
  return request.get<PurchaseReturn>(`/purchase/returns/${id}`).then(normalizePurchaseReturn)
}

/**
 * 创建采购退货
 */
export const createPurchaseReturn = (data: PurchaseReturnCreateRequest) => {
  return request.post<PurchaseReturn>('/purchase/returns', toPurchaseReturnPayload(data)).then(normalizePurchaseReturn)
}

/**
 * 更新采购退货（草稿）
 */
export const updatePurchaseReturn = (id: string | number, data: PurchaseReturnCreateRequest) => {
  return request.put<PurchaseReturn>(`/purchase/returns/${id}`, toPurchaseReturnPayload(data)).then(normalizePurchaseReturn)
}

/**
 * 过账退货
 */
export const postPurchaseReturn = (id: string | number) => {
  return request.post<PurchaseReturn>(`/purchase/returns/${id}/post`).then(normalizePurchaseReturn)
}

/**
 * 取消退货
 */
export const cancelPurchaseReturn = (id: string | number) => {
  return request.post<PurchaseReturn>(`/purchase/returns/${id}/cancel`).then(normalizePurchaseReturn)
}

/**
 * 导出采购退货
 */
export const exportPurchaseReturns = (params: PurchaseReturnQuery) => {
  const exportParams: Record<string, unknown> = {
    ...toPurchaseReturnQueryParams(params)
  }
  delete exportParams.returnNo
  delete exportParams.startDate
  delete exportParams.endDate

  return request.get<Blob>('/purchase/returns/export', {
    params: exportParams,
    responseType: 'blob'
  })
}

const toPurchaseReturnQueryParams = (params: PurchaseReturnQuery) => {
  const { returnNo, startDate, endDate, supplierId: _supplierId, ...rest } = params
  return {
    ...rest,
    keyword: params.keyword || returnNo || undefined,
    status: params.status === 'COMPLETED' ? 'POSTED' : params.status || undefined,
    returnDateFrom: params.returnDateFrom || startDate || undefined,
    returnDateTo: params.returnDateTo || endDate || undefined
  }
}

const normalizePurchaseReturnItem = (item: PurchaseReturnItem | PurchaseReturnLineResponse): PurchaseReturnItem => ({
  ...item,
  id: item.id != null ? String(item.id) : undefined,
  receiptLineId: item.receiptLineId,
  orderLineId: item.orderLineId,
  productId: String(item.productId),
  productCode: item.productCode || '',
  productName: item.productName || '',
  receiptQty: Number(item.receiptQty ?? 0),
  returnedQty: Number(item.returnedQty ?? 0),
  availableReturnQty: Number(item.availableReturnQty ?? item.receiptQty ?? 0),
  quantity: Number(item.quantity ?? item.qty ?? 0),
  price: Number(item.price ?? 0),
  amount: Number(item.amount ?? 0),
  taxRate: Number(item.taxRate ?? 0),
  taxAmount: Number(item.taxAmount ?? 0),
  locationId: item.locationId != null ? String(item.locationId) : item.locationId,
  reason: item.reason || item.remark || ''
})

const normalizePurchaseReturn = (purchaseReturn: PurchaseReturn): PurchaseReturn => {
  const items = (purchaseReturn.items || purchaseReturn.lines || []).map(normalizePurchaseReturnItem)
  return {
    ...purchaseReturn,
    id: String(purchaseReturn.id),
    receiptId: String(purchaseReturn.receiptId),
    warehouseId: String(purchaseReturn.warehouseId),
    supplierName: purchaseReturn.supplierName || '',
    totalAmount: Number(purchaseReturn.totalAmount ?? 0),
    totalTaxAmount: Number(purchaseReturn.totalTaxAmount ?? 0),
    totalQuantity: Number(purchaseReturn.totalQuantity ?? 0),
    items,
    lines: items,
    createdAt: purchaseReturn.createdAt || '',
    updatedAt: purchaseReturn.updatedAt || ''
  }
}

const toPurchaseReturnPayload = (data: PurchaseReturnCreateRequest) => ({
  receiptId: data.receiptId,
  returnDate: data.returnDate,
  remark: data.remark,
  lines: data.items.map((item) => ({
    receiptLineId: item.receiptLineId,
    qty: item.quantity,
    lotNo: item.lotNo,
    productionDate: item.productionDate,
    expiryDate: item.expiryDate,
    locationId: item.locationId || undefined,
    serialNos: item.serialNos || undefined,
    remark: item.reason || item.remark
  }))
})

// ==================== 采购询价(RFQ) ====================

export interface PurchaseInquiryLine {
  id?: string | number
  lineNo?: number
  productId: string | number
  qty: number
  remark?: string
}

export interface PurchaseInquiryQuote {
  id: string | number
  supplierId: string | number
  unitPrice?: number | null
  taxRate?: number | null
  status: 'PENDING' | 'SELECTED' | string
  remark?: string
  lines?: PurchaseInquiryQuoteLine[]
}

export interface PurchaseInquiryQuoteLine {
  id: string | number
  inquiryLineId: string | number
  unitPrice: number
  taxRate?: number | null
}

export interface PurchaseInquiry {
  id: string | number
  inquiryNo: string
  inquiryDate: string
  status: 'DRAFT' | 'SUBMITTED' | 'CLOSED' | 'CONVERTED' | 'CANCELLED' | string
  selectedSupplierId?: string | number | null
  selectedQuoteId?: string | number | null
  convertedOrderId?: string | number | null
  convertedOrderNo?: string | null
  convertedBy?: string | number | null
  convertedTime?: string | null
  title?: string
  remark?: string
  lines: PurchaseInquiryLine[]
  quotes: PurchaseInquiryQuote[]
}

export interface PurchaseInquiryQuery extends PageQuery {
  keyword?: string
  status?: string
  inquiryDateFrom?: string
  inquiryDateTo?: string
}

export interface PurchaseInquirySaveRequest {
  inquiryDate: string
  title?: string
  remark?: string
  lines: Array<{ productId: string | number; qty: number; remark?: string }>
}

export interface PurchaseInquiryQuoteRequest {
  supplierId: string | number
  unitPrice?: number
  taxRate?: number
  lines?: PurchaseInquiryQuoteLineRequest[]
  remark?: string
}

export interface PurchaseInquiryQuoteLineRequest {
  inquiryLineId: string | number
  unitPrice: number
  taxRate?: number
}

export interface PurchaseInquiryPoPrefill {
  inquiryId: string | number
  inquiryNo: string
  supplierId: string | number
  orderDate: string
  remark?: string
  lines: Array<{
    productId: string | number
    qty: number
    price: number
    taxRate: number
    remark?: string
  }>
}

const normalizeInquiryLine = (line: PurchaseInquiryLine): PurchaseInquiryLine => ({
  ...line,
  id: line.id != null ? String(line.id) : line.id,
  productId: line.productId != null ? String(line.productId) : line.productId,
  qty: Number(line.qty ?? 0)
})

const normalizeInquiryQuote = (quote: PurchaseInquiryQuote): PurchaseInquiryQuote => ({
  ...quote,
  id: String(quote.id),
  supplierId: String(quote.supplierId),
  unitPrice: quote.unitPrice != null ? Number(quote.unitPrice) : quote.unitPrice,
  taxRate: quote.taxRate != null ? Number(quote.taxRate) : quote.taxRate,
  lines: (quote.lines || []).map((line) => ({
    ...line,
    id: String(line.id),
    inquiryLineId: String(line.inquiryLineId),
    unitPrice: Number(line.unitPrice ?? 0),
    taxRate: line.taxRate != null ? Number(line.taxRate) : line.taxRate
  }))
})

const normalizeInquiry = (inquiry: PurchaseInquiry): PurchaseInquiry => ({
  ...inquiry,
  id: String(inquiry.id),
  selectedSupplierId: inquiry.selectedSupplierId != null ? String(inquiry.selectedSupplierId) : inquiry.selectedSupplierId,
  selectedQuoteId: inquiry.selectedQuoteId != null ? String(inquiry.selectedQuoteId) : inquiry.selectedQuoteId,
  convertedOrderId: inquiry.convertedOrderId != null ? String(inquiry.convertedOrderId) : inquiry.convertedOrderId,
  convertedBy: inquiry.convertedBy != null ? String(inquiry.convertedBy) : inquiry.convertedBy,
  lines: (inquiry.lines || []).map(normalizeInquiryLine),
  quotes: (inquiry.quotes || []).map(normalizeInquiryQuote)
})

export const getPurchaseInquiries = (params: PurchaseInquiryQuery) => {
  return request.get<PageResponse<PurchaseInquiry>>('/purchase/inquiries', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeInquiry)
  }))
}

export const getPurchaseInquiry = (id: string | number) => {
  return request.get<PurchaseInquiry>(`/purchase/inquiries/${id}`).then(normalizeInquiry)
}

export const createPurchaseInquiry = (data: PurchaseInquirySaveRequest) => {
  return request.post<PurchaseInquiry>('/purchase/inquiries', data).then(normalizeInquiry)
}

export const updatePurchaseInquiry = (id: string | number, data: PurchaseInquirySaveRequest) => {
  return request.put<PurchaseInquiry>(`/purchase/inquiries/${id}`, data).then(normalizeInquiry)
}

export const submitPurchaseInquiry = (id: string | number) => {
  return request.post<PurchaseInquiry>(`/purchase/inquiries/${id}/submit`).then(normalizeInquiry)
}

export const addPurchaseInquiryQuote = (id: string | number, data: PurchaseInquiryQuoteRequest) => {
  return request.post<PurchaseInquiry>(`/purchase/inquiries/${id}/quotes`, data).then(normalizeInquiry)
}

export const selectPurchaseInquiryQuote = (id: string | number, quoteId: string | number) => {
  return request.post<PurchaseInquiry>(`/purchase/inquiries/${id}/select-quote`, { quoteId }).then(normalizeInquiry)
}

export const getPurchaseInquiryPoPrefill = (id: string | number) => {
  return request.get<PurchaseInquiryPoPrefill>(`/purchase/inquiries/${id}/po-prefill`).then((prefill) => ({
    ...prefill,
    inquiryId: String(prefill.inquiryId),
    supplierId: String(prefill.supplierId),
    lines: (prefill.lines || []).map((line) => ({
      ...line,
      productId: String(line.productId),
      qty: Number(line.qty ?? 0),
      price: Number(line.price ?? 0),
      taxRate: Number(line.taxRate ?? 0)
    }))
  }))
}

export const convertPurchaseInquiryToPurchaseOrder = (id: string | number) => {
  return request
    .post<PurchaseOrder>(`/purchase/inquiries/${id}/convert-to-purchase-order`)
    .then(normalizePurchaseOrder)
}

export const cancelPurchaseInquiry = (id: string | number) => {
  return request.post<PurchaseInquiry>(`/purchase/inquiries/${id}/cancel`).then(normalizeInquiry)
}

export interface PurchasePrice {
  id: string | number
  supplierId?: string | number | null
  supplierName?: string
  productId: string | number
  productCode?: string
  productName?: string
  listPrice: number
  maxPrice: number
  effectiveFrom: string
  effectiveTo?: string | null
  status: 'ACTIVE' | 'INACTIVE' | string
  remark?: string
}

export interface PurchasePriceQuery extends PageQuery {
  supplierId?: string | number
  productId?: string | number
  status?: string
  keyword?: string
}

export interface PurchasePriceSaveRequest {
  supplierId?: string | number | null
  productId: string | number
  listPrice: number
  maxPrice: number
  effectiveFrom: string
  effectiveTo?: string | null
  status?: string
  remark?: string
}

export interface PurchasePriceResolve {
  productId: string | number
  supplierId?: string | number | null
  bizDate: string
  matched: boolean
  matchLevel: 'SUPPLIER' | 'PRODUCT' | 'NONE' | string
  priceId?: string | number | null
  listPrice?: number | null
  maxPrice?: number | null
}

const normalizePurchasePrice = (price: PurchasePrice): PurchasePrice => ({
  ...price,
  id: String(price.id),
  supplierId: price.supplierId != null ? String(price.supplierId) : price.supplierId,
  productId: String(price.productId),
  listPrice: Number(price.listPrice ?? 0),
  maxPrice: Number(price.maxPrice ?? 0)
})

export const getPurchasePrices = (params: PurchasePriceQuery) => {
  return request.get<PageResponse<PurchasePrice>>('/purchase/prices', { params }).then((page) => ({
    ...page,
    records: (page.records || []).map(normalizePurchasePrice)
  }))
}

export const getPurchasePrice = (id: string | number) => {
  return request.get<PurchasePrice>(`/purchase/prices/${id}`).then(normalizePurchasePrice)
}

export const createPurchasePrice = (data: PurchasePriceSaveRequest) => {
  return request.post<PurchasePrice>('/purchase/prices', data).then(normalizePurchasePrice)
}

export const updatePurchasePrice = (id: string | number, data: PurchasePriceSaveRequest) => {
  return request.put<PurchasePrice>(`/purchase/prices/${id}`, data).then(normalizePurchasePrice)
}

export const enablePurchasePrice = (id: string | number) => {
  return request.post<PurchasePrice>(`/purchase/prices/${id}/enable`).then(normalizePurchasePrice)
}

export const disablePurchasePrice = (id: string | number) => {
  return request.post<PurchasePrice>(`/purchase/prices/${id}/disable`).then(normalizePurchasePrice)
}

export const resolvePurchasePrice = (params: {
  productId: string | number
  supplierId?: string | number
  bizDate?: string
}) => {
  return request.get<PurchasePriceResolve>('/purchase/prices/resolve', { params }).then((res) => ({
    ...res,
    productId: String(res.productId),
    supplierId: res.supplierId != null ? String(res.supplierId) : res.supplierId,
    priceId: res.priceId != null ? String(res.priceId) : res.priceId,
    listPrice: res.listPrice != null ? Number(res.listPrice) : res.listPrice,
    maxPrice: res.maxPrice != null ? Number(res.maxPrice) : res.maxPrice
  }))
}


export interface PurchaseRequisitionLine {
  id?: string | number
  lineNo?: number
  productId: string | number
  productCode?: string
  productName?: string
  qty: number
  remark?: string
}

export interface PurchaseRequisition {
  id: string | number
  requisitionNo: string
  requisitionDate: string
  neededDate?: string
  status: string
  approvalStatus?: string | null
  supplierId?: string | number | null
  convertedOrderId?: string | number | null
  convertedOrderNo?: string | null
  remark?: string
  lines?: PurchaseRequisitionLine[]
}

const normalizeRequisition = (item: PurchaseRequisition): PurchaseRequisition => ({
  ...item,
  id: String(item.id),
  supplierId: item.supplierId != null ? String(item.supplierId) : item.supplierId,
  convertedOrderId: item.convertedOrderId != null ? String(item.convertedOrderId) : item.convertedOrderId,
  lines: (item.lines || []).map((line) => ({
    ...line,
    id: line.id != null ? String(line.id) : line.id,
    productId: String(line.productId),
    qty: Number(line.qty ?? 0),
    remark: line.remark || ''
  }))
})

export const getPurchaseRequisitions = (params?: any) =>
  request.get<PageResponse<PurchaseRequisition>>('/purchase/requisitions', { params }).then((page) => ({
    ...page,
    records: (page.records || []).map(normalizeRequisition)
  }))

export const getPurchaseRequisition = (id: string | number) =>
  request.get<PurchaseRequisition>(`/purchase/requisitions/${id}`).then(normalizeRequisition)

export const createPurchaseRequisition = (data: any) =>
  request.post<PurchaseRequisition>('/purchase/requisitions', data).then(normalizeRequisition)

export const updatePurchaseRequisition = (id: string | number, data: any) =>
  request.put<PurchaseRequisition>(`/purchase/requisitions/${id}`, data).then(normalizeRequisition)

export const submitPurchaseRequisition = (id: string | number) =>
  request.post<PurchaseRequisition>(`/purchase/requisitions/${id}/submit`).then(normalizeRequisition)

export const approvePurchaseRequisition = (id: string | number) =>
  request.post<PurchaseRequisition>(`/purchase/requisitions/${id}/approve`).then(normalizeRequisition)

export const rejectPurchaseRequisition = (id: string | number) =>
  request.post<PurchaseRequisition>(`/purchase/requisitions/${id}/reject`).then(normalizeRequisition)

export const cancelPurchaseRequisition = (id: string | number) =>
  request.post<PurchaseRequisition>(`/purchase/requisitions/${id}/cancel`).then(normalizeRequisition)

export const convertPurchaseRequisition = (id: string | number) =>
  request.post<PurchaseRequisition>(`/purchase/requisitions/${id}/convert-to-purchase-order`).then(normalizeRequisition)
