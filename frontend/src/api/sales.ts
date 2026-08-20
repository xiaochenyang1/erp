import { request } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

// ==================== 销售订单 ====================

export interface SalesOrder {
  id: string
  orderNo: string
  customerId: string
  customerName: string
  warehouseId?: string
  orderDate: string
  deliveryDate?: string
  totalAmount: number
  totalQuantity?: number
  totalTaxAmount?: number
  status: 'DRAFT' | 'SUBMITTED' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'DELIVERING' | 'COMPLETED' | 'CANCELLED' | 'CONFIRMED' | 'CLOSED'
  approvalStatus?: string
  deliveryStatus?: string
  items: SalesOrderItem[]
  lines?: SalesOrderItem[]
  remark?: string
  carrierName?: string
  trackingNo?: string
  logisticsStatus?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface SalesOrderItem {
  id?: string
  lineNo?: number
  productId: string | number
  productCode?: string
  productName?: string
  quantity: number
  qty?: number
  auxQty?: number | null
  auxUnitName?: string
  conversionFactor?: number | null
  price: number
  taxRate?: number
  amount: number
  taxAmount?: number
  deliveredQuantity?: number
  deliveredQty?: number
  remark?: string
}

export interface SalesOrderQuery extends PageQuery {
  keyword?: string
  orderNo?: string
  customerId?: string | number
  status?: string
  approvalStatus?: string
  startDate?: string
  endDate?: string
}

export interface SalesOrderSaveRequest {
  customerId: string | number
  warehouseId?: string | number
  orderDate: string
  deliveryDate?: string
  items: SalesOrderItem[]
  remark?: string
  carrierName?: string
  trackingNo?: string
  logisticsStatus?: string
}

export interface SalesOrderCreditPreview {
  customerId: string
  creditLimit: number
  outstandingReceivable: number
  openOrderExposure: number
  currentExposure: number
  orderAmount: number
  projectedExposure: number
  availableCredit?: number
  projectedAvailableCredit?: number
  unlimited: boolean
  exceeded: boolean
}

// 销售订单API
export const getSalesOrders = (params: SalesOrderQuery) => {
  return request.get<PageResponse<SalesOrder>>('/sales/orders', {
    params: {
      ...params,
      keyword: params.keyword || params.orderNo || undefined
    }
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizeSalesOrder)
  }))
}

export const getSalesOrder = (id: string | number) => {
  return request.get<SalesOrder>(`/sales/orders/${id}`).then(normalizeSalesOrder)
}

export const createSalesOrder = (data: SalesOrderSaveRequest) => {
  return request.post<SalesOrder>('/sales/orders', toSalesOrderPayload(data)).then(normalizeSalesOrder)
}

export const updateSalesOrder = (id: string | number, data: SalesOrderSaveRequest) => {
  return request.put<SalesOrder>(`/sales/orders/${id}`, toSalesOrderPayload(data)).then(normalizeSalesOrder)
}

export const previewSalesOrderCredit = (customerId: string | number, items: SalesOrderItem[]) => {
  return request.post<SalesOrderCreditPreview>('/sales/orders/credit-preview', {
    customerId,
    lines: toSalesOrderLinePayload(items)
  }).then(normalizeSalesOrderCreditPreview)
}

export const submitSalesOrder = (id: string | number, remark?: string) => {
  return request.post<SalesOrder>(`/sales/orders/${id}/submit`, { remark }).then(normalizeSalesOrder)
}

export const approveSalesOrder = (id: string | number, remark?: string) => {
  return request.post<SalesOrder>(`/sales/orders/${id}/approve`, { remark }).then(normalizeSalesOrder)
}

export const rejectSalesOrder = (id: string | number, reason: string) => {
  return request.post<SalesOrder>(`/sales/orders/${id}/reject`, { reason }).then(normalizeSalesOrder)
}

export const unapproveSalesOrder = (id: string | number) => {
  return request.post<SalesOrder>(`/sales/orders/${id}/unapprove`).then(normalizeSalesOrder)
}

export const cancelSalesOrder = (id: string | number) => {
  return request.post<SalesOrder>(`/sales/orders/${id}/cancel`).then(normalizeSalesOrder)
}

const normalizeSalesOrderItem = (item: SalesOrderItem): SalesOrderItem => ({
  ...item,
  id: item.id != null ? String(item.id) : undefined,
  productId: String(item.productId),
  quantity: item.quantity ?? item.qty ?? 0,
  auxQty: item.auxQty != null ? Number(item.auxQty) : item.auxQty,
  conversionFactor: item.conversionFactor != null ? Number(item.conversionFactor) : item.conversionFactor,
  deliveredQuantity: item.deliveredQuantity ?? item.deliveredQty ?? 0
})

const normalizeSalesOrder = (order: SalesOrder): SalesOrder => {
  const items = (order.items || order.lines || []).map(normalizeSalesOrderItem)
  return {
    ...order,
    id: String(order.id),
    customerId: String(order.customerId),
    warehouseId: order.warehouseId != null ? String(order.warehouseId) : undefined,
    items,
    lines: items
  }
}

const normalizeSalesOrderCreditPreview = (preview: SalesOrderCreditPreview): SalesOrderCreditPreview => ({
  ...preview,
  customerId: String(preview.customerId)
})

const toSalesOrderPayload = (data: SalesOrderSaveRequest) => ({
  customerId: data.customerId,
  warehouseId: data.warehouseId,
  orderDate: data.orderDate,
  deliveryDate: data.deliveryDate,
  remark: data.remark,
    carrierName: data.carrierName,
    trackingNo: data.trackingNo,
    logisticsStatus: data.logisticsStatus,
  lines: toSalesOrderLinePayload(data.items)
})

const toSalesOrderLinePayload = (items: SalesOrderItem[]) => items.map((item, index) => ({
  productId: item.productId,
  lineNo: item.lineNo ?? index + 1,
  qty: item.quantity,
  auxQty: item.auxQty ?? undefined,
  auxUnitName: item.auxUnitName || undefined,
  conversionFactor: item.conversionFactor ?? undefined,
  price: item.price,
  taxRate: item.taxRate ?? 0,
  remark: item.remark
}))

// ==================== 销售发货 ====================

export interface SalesDelivery {
  id: string
  deliveryNo: string
  orderId: string | number
  orderNo?: string
  customerId: string | number
  customerName?: string
  warehouseId: string | number
  warehouseName?: string
  deliveryDate: string
  status: 'DRAFT' | 'POSTED' | 'COMPLETED' | 'CANCELLED'
  totalQuantity?: number
  totalAmount?: number
  totalTaxAmount?: number
  items: SalesDeliveryItem[]
  lines?: SalesDeliveryItem[]
  remark?: string
  carrierName?: string
  trackingNo?: string
  logisticsStatus?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface SalesDeliveryItem {
  id?: string
  orderItemId?: string | number
  orderLineId?: string | number
  productId: string | number
  productCode?: string
  productName?: string
  orderedQuantity?: number
  deliveredQuantity?: number
  quantity: number
  qty?: number
  price?: number
  amount?: number
  taxRate?: number
  taxAmount?: number
  returnedQty?: number
  lotNo?: string
  productionDate?: string
  expiryDate?: string
  locationId?: string | number | null
  serialNos?: string
  remark?: string
}

export interface SalesDeliveryQuery extends PageQuery {
  deliveryNo?: string
  orderId?: string | number
  customerId?: string | number
  status?: string
  logisticsStatus?: string
  trackingNo?: string
  startDate?: string
  endDate?: string
}

export interface SalesDeliveryCreateRequest {
  orderId: string | number
  warehouseId: string | number
  deliveryDate: string
  items: SalesDeliveryItem[]
  remark?: string
  carrierName?: string
  trackingNo?: string
  logisticsStatus?: string
}

// 销售发货API
export const getSalesDeliveries = (params: SalesDeliveryQuery) => {
  return request.get<PageResponse<SalesDelivery>>('/sales/deliveries', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeSalesDelivery)
  }))
}

export const getSalesDelivery = (id: string | number) => {
  return request.get<SalesDelivery>(`/sales/deliveries/${id}`).then(normalizeSalesDelivery)
}

export const createSalesDelivery = (data: SalesDeliveryCreateRequest) => {
  return request.post<SalesDelivery>('/sales/deliveries', toSalesDeliveryPayload(data)).then(normalizeSalesDelivery)
}

export const updateSalesDelivery = (id: string | number, data: SalesDeliveryCreateRequest) => {
  return request.put<SalesDelivery>(`/sales/deliveries/${id}`, toSalesDeliveryPayload(data)).then(normalizeSalesDelivery)
}

export const cancelSalesDelivery = (id: string | number) => {
  return request.post<SalesDelivery>(`/sales/deliveries/${id}/cancel`).then(normalizeSalesDelivery)
}

export const postSalesDelivery = (id: string | number) => {
  return request.post<SalesDelivery>(`/sales/deliveries/${id}/post`).then(normalizeSalesDelivery)
}

const normalizeSalesDeliveryItem = (item: SalesDeliveryItem): SalesDeliveryItem => ({
  ...item,
  id: item.id != null ? String(item.id) : undefined,
  orderItemId: item.orderItemId ?? item.orderLineId,
  orderLineId: item.orderLineId ?? item.orderItemId,
  productId: String(item.productId),
  locationId: item.locationId != null ? String(item.locationId) : item.locationId,
  quantity: item.quantity ?? item.qty ?? 0
})

const normalizeSalesDelivery = (delivery: SalesDelivery): SalesDelivery => {
  const items = (delivery.items || delivery.lines || []).map(normalizeSalesDeliveryItem)
  return {
    ...delivery,
    id: String(delivery.id),
    orderId: String(delivery.orderId),
    warehouseId: String(delivery.warehouseId),
    items,
    lines: items
  }
}

const toSalesDeliveryPayload = (data: SalesDeliveryCreateRequest) => ({
  orderId: data.orderId,
  warehouseId: data.warehouseId,
  deliveryDate: data.deliveryDate,
  remark: data.remark,
  carrierName: data.carrierName,
  trackingNo: data.trackingNo,
  logisticsStatus: data.logisticsStatus,
  lines: data.items.map((item) => ({
    orderLineId: item.orderLineId ?? item.orderItemId,
    qty: item.quantity,
    lotNo: item.lotNo || undefined,
    productionDate: item.productionDate || undefined,
    expiryDate: item.expiryDate || undefined,
    locationId: item.locationId || undefined,
    serialNos: item.serialNos || undefined,
    remark: item.remark
  }))
})

// ==================== 销售退货 ====================

export interface SalesReturn {
  id: string
  returnNo: string
  deliveryId: string | number
  deliveryNo?: string
  orderNo?: string
  customerId?: string | number
  customerName?: string
  warehouseId: string | number
  warehouseName?: string
  returnDate: string
  totalAmount: number
  totalQuantity?: number
  totalTaxAmount?: number
  status: 'DRAFT' | 'POSTED' | 'COMPLETED' | 'CANCELLED'
  items: SalesReturnItem[]
  lines?: SalesReturnLineResponse[]
  remark?: string
  carrierName?: string
  trackingNo?: string
  logisticsStatus?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface SalesReturnItem {
  id?: string
  deliveryLineId?: string | number
  orderLineId?: string | number
  productId: string | number
  productCode?: string
  productName?: string
  quantity: number
  qty?: number
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

export interface SalesReturnLineResponse {
  id?: string | number
  lineNo?: number
  deliveryLineId?: string | number
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
  lotNo?: string
  productionDate?: string
  expiryDate?: string
  locationId?: string | number | null
  serialNos?: string
  reason?: string
  remark?: string
}

export interface SalesReturnQuery extends PageQuery {
  returnNo?: string
  keyword?: string
  deliveryId?: string | number
  warehouseId?: string | number
  status?: string
  startDate?: string
  endDate?: string
  returnDateFrom?: string
  returnDateTo?: string
}

export interface SalesReturnCreateRequest {
  deliveryId: string | number
  returnDate: string
  items: SalesReturnItem[]
  remark?: string
  carrierName?: string
  trackingNo?: string
  logisticsStatus?: string
}

// 销售退货API
export const getSalesReturns = (params: SalesReturnQuery) => {
  return request.get<PageResponse<SalesReturn>>('/sales/returns', {
    params: toSalesReturnQueryParams(params)
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizeSalesReturn)
  }))
}

export const getSalesReturn = (id: string | number) => {
  return request.get<SalesReturn>(`/sales/returns/${id}`).then(normalizeSalesReturn)
}

export const createSalesReturn = (data: SalesReturnCreateRequest) => {
  return request.post<SalesReturn>('/sales/returns', toSalesReturnPayload(data)).then(normalizeSalesReturn)
}

export const updateSalesReturn = (id: string | number, data: SalesReturnCreateRequest) => {
  return request.put<SalesReturn>(`/sales/returns/${id}`, toSalesReturnPayload(data)).then(normalizeSalesReturn)
}

export const postSalesReturn = (id: string | number) => {
  return request.post<SalesReturn>(`/sales/returns/${id}/post`).then(normalizeSalesReturn)
}

export const cancelSalesReturn = (id: string | number) => {
  return request.post<SalesReturn>(`/sales/returns/${id}/cancel`).then(normalizeSalesReturn)
}

const toSalesReturnQueryParams = (params: SalesReturnQuery) => {
  const { startDate, endDate, returnNo, ...rest } = params
  return {
    ...rest,
    keyword: params.keyword || returnNo || undefined,
    returnDateFrom: startDate || params.returnDateFrom || undefined,
    returnDateTo: endDate || params.returnDateTo || undefined
  }
}

const normalizeSalesReturnItem = (item: SalesReturnItem | SalesReturnLineResponse): SalesReturnItem => ({
  ...item,
  id: item.id != null ? String(item.id) : undefined,
  deliveryLineId: item.deliveryLineId,
  orderLineId: item.orderLineId,
  productId: String(item.productId),
  productCode: item.productCode || '',
  productName: item.productName || '',
  quantity: Number(item.quantity ?? item.qty ?? 0),
  price: Number(item.price ?? 0),
  amount: Number(item.amount ?? 0),
  taxRate: Number(item.taxRate ?? 0),
  taxAmount: Number(item.taxAmount ?? 0),
  locationId: item.locationId != null ? String(item.locationId) : item.locationId,
  reason: item.reason || item.remark || ''
})

const normalizeSalesReturn = (salesReturn: SalesReturn): SalesReturn => {
  const items = (salesReturn.items || salesReturn.lines || []).map(normalizeSalesReturnItem)
  return {
    ...salesReturn,
    id: String(salesReturn.id),
    deliveryId: String(salesReturn.deliveryId),
    warehouseId: String(salesReturn.warehouseId),
    status: salesReturn.status === 'POSTED' ? 'COMPLETED' : salesReturn.status,
    items,
    lines: items
  }
}

const toSalesReturnPayload = (data: SalesReturnCreateRequest) => ({
  deliveryId: data.deliveryId,
  returnDate: data.returnDate,
  remark: data.remark,
  carrierName: data.carrierName,
  trackingNo: data.trackingNo,
  logisticsStatus: data.logisticsStatus,
  lines: data.items.map((item) => ({
    deliveryLineId: item.deliveryLineId,
    qty: item.quantity,
    lotNo: item.lotNo || undefined,
    productionDate: item.productionDate || undefined,
    expiryDate: item.expiryDate || undefined,
    locationId: item.locationId || undefined,
    serialNos: item.serialNos || undefined,
    remark: item.reason || item.remark
  }))
})


// ==================== 销售价目 ====================

export interface SalesPrice {
  id: string | number
  customerId?: string | number | null
  customerName?: string
  productId: string | number
  productCode?: string
  productName?: string
  listPrice: number
  minPrice: number
  effectiveFrom: string
  effectiveTo?: string | null
  status: 'ACTIVE' | 'INACTIVE' | string
  remark?: string
}

export interface SalesPriceQuery extends PageQuery {
  customerId?: string | number
  productId?: string | number
  status?: string
  keyword?: string
}

export interface SalesPriceSaveRequest {
  customerId?: string | number | null
  productId: string | number
  listPrice: number
  minPrice: number
  effectiveFrom: string
  effectiveTo?: string | null
  status?: string
  remark?: string
}

export interface SalesPriceResolve {
  productId: string | number
  customerId?: string | number | null
  bizDate: string
  matched: boolean
  matchLevel: 'CUSTOMER' | 'PRODUCT' | 'NONE' | string
  priceId?: string | number | null
  listPrice?: number | null
  minPrice?: number | null
}

const normalizeSalesPrice = (price: SalesPrice): SalesPrice => ({
  ...price,
  id: String(price.id),
  customerId: price.customerId != null ? String(price.customerId) : price.customerId,
  productId: String(price.productId),
  listPrice: Number(price.listPrice ?? 0),
  minPrice: Number(price.minPrice ?? 0)
})

export const getSalesPrices = (params: SalesPriceQuery) => {
  return request.get<PageResponse<SalesPrice>>('/sales/prices', { params }).then((page) => ({
    ...page,
    records: (page.records || []).map(normalizeSalesPrice)
  }))
}

export const getSalesPrice = (id: string | number) => {
  return request.get<SalesPrice>(`/sales/prices/${id}`).then(normalizeSalesPrice)
}

export const createSalesPrice = (data: SalesPriceSaveRequest) => {
  return request.post<SalesPrice>('/sales/prices', data).then(normalizeSalesPrice)
}

export const updateSalesPrice = (id: string | number, data: SalesPriceSaveRequest) => {
  return request.put<SalesPrice>(`/sales/prices/${id}`, data).then(normalizeSalesPrice)
}

export const enableSalesPrice = (id: string | number) => {
  return request.post<SalesPrice>(`/sales/prices/${id}/enable`).then(normalizeSalesPrice)
}

export const disableSalesPrice = (id: string | number) => {
  return request.post<SalesPrice>(`/sales/prices/${id}/disable`).then(normalizeSalesPrice)
}

export const resolveSalesPrice = (params: {
  productId: string | number
  customerId?: string | number
  bizDate?: string
}) => {
  return request.get<SalesPriceResolve>('/sales/prices/resolve', { params }).then((res) => ({
    ...res,
    productId: String(res.productId),
    customerId: res.customerId != null ? String(res.customerId) : res.customerId,
    priceId: res.priceId != null ? String(res.priceId) : res.priceId,
    listPrice: res.listPrice != null ? Number(res.listPrice) : res.listPrice,
    minPrice: res.minPrice != null ? Number(res.minPrice) : res.minPrice
  }))
}

// ==================== 销售报价 ====================

export interface SalesQuoteLine {
  id?: string | number
  lineNo?: number
  productId: string | number
  qty: number
  price: number
  taxRate?: number
  amount?: number
  taxAmount?: number
  remark?: string
}

export interface SalesQuote {
  id: string | number
  quoteNo: string
  customerId: string | number
  customerName?: string
  quoteDate: string
  validUntil?: string
  status: string
  totalAmount: number
  totalTaxAmount?: number
  convertedOrderId?: string | number
  remark?: string
  lines?: SalesQuoteLine[]
}

export interface SalesQuoteQuery extends PageQuery {
  keyword?: string
  status?: string
  customerId?: string | number
}

export interface SalesQuoteSaveRequest {
  customerId: string | number
  quoteDate: string
  validUntil?: string
  remark?: string
  lines: Array<{ productId: string | number; qty: number; price: number; taxRate?: number; remark?: string }>
}

const normalizeQuote = (q: SalesQuote): SalesQuote => ({
  ...q,
  id: String(q.id),
  customerId: String(q.customerId),
  convertedOrderId: q.convertedOrderId != null ? String(q.convertedOrderId) : q.convertedOrderId,
  totalAmount: Number(q.totalAmount ?? 0),
  totalTaxAmount: Number(q.totalTaxAmount ?? 0),
  lines: (q.lines || []).map((l) => ({
    ...l,
    id: l.id != null ? String(l.id) : l.id,
    productId: String(l.productId),
    qty: Number(l.qty ?? 0),
    price: Number(l.price ?? 0),
    taxRate: Number(l.taxRate ?? 0),
    amount: Number(l.amount ?? 0),
    taxAmount: Number(l.taxAmount ?? 0)
  }))
})

export const getSalesQuotes = (params: SalesQuoteQuery) =>
  request.get<PageResponse<SalesQuote>>('/sales/quotes', { params }).then((page) => ({
    ...page,
    records: (page.records || []).map(normalizeQuote)
  }))

export const getSalesQuote = (id: string | number) =>
  request.get<SalesQuote>(`/sales/quotes/${id}`).then(normalizeQuote)

export const createSalesQuote = (data: SalesQuoteSaveRequest) =>
  request.post<SalesQuote>('/sales/quotes', data).then(normalizeQuote)

export const updateSalesQuote = (id: string | number, data: SalesQuoteSaveRequest) =>
  request.put<SalesQuote>(`/sales/quotes/${id}`, data).then(normalizeQuote)

export const confirmSalesQuote = (id: string | number) =>
  request.post<SalesQuote>(`/sales/quotes/${id}/confirm`).then(normalizeQuote)

export const cancelSalesQuote = (id: string | number) =>
  request.post<SalesQuote>(`/sales/quotes/${id}/cancel`).then(normalizeQuote)

export const convertSalesQuoteToOrder = (id: string | number, warehouseId: string | number) =>
  request.post<SalesOrder>(`/sales/quotes/${id}/convert-to-order`, { warehouseId }).then((order) => ({
    ...order,
    id: String(order.id),
    orderNo: order.orderNo
  }))


export const updateSalesDeliveryLogistics = (
  id: string | number,
  data: { logisticsStatus: string; carrierName?: string; trackingNo?: string; remark?: string }
) => {
  return request.post<SalesDelivery>(`/sales/deliveries/${id}/logistics`, data).then(normalizeSalesDelivery)
}
