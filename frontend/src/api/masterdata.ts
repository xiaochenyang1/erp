import { request } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'
import type { components as ProductApiComponents } from '@/api/generated/product'

// ==================== 产品管理 ====================

type ProductContract = ProductApiComponents['schemas']['ProductResponse']
export type ProductCreateContract = ProductApiComponents['schemas']['ProductCreateRequest'] & {
  serialControlled?: boolean
}
export type ProductUpdateContract = ProductApiComponents['schemas']['ProductUpdateRequest'] & {
  serialControlled?: boolean
}
type ProductQueryContract = ProductApiComponents['schemas']['ProductPageQuery']

export interface Product extends Omit<ProductContract, 'id' | 'status'> {
  id: string                    // Jackson 将雪花 ID 序列化为字符串
  status: 'ACTIVE' | 'INACTIVE'
  createdTime?: string
  updatedTime?: string

  // 为了兼容模板，添加别名
  code?: string
  name?: string
  specifications?: string      // 别名
  unit?: string               // 别名
  unitPrice?: number          // 别名
  costPrice?: number          // 别名
  // Generated OpenAPI contract may lag behind backend product control flags.
  lotControlled?: boolean
  shelfLifeControlled?: boolean
  inspectionRequired?: boolean
  serialControlled?: boolean
  auxUnitName?: string
  conversionFactor?: number | null
}

export interface ProductQuery extends PageQuery, Omit<ProductQueryContract, 'pageNo' | 'pageSize'> {
  code?: string
  name?: string
  keyword?: string
  categoryName?: string
  status?: string
}

export interface ProductSaveRequest {
  code?: string
  name?: string
  productCode?: string
  productName?: string
  categoryName?: string
  specification?: string
  specifications?: string
  unitName?: string
  unit?: string
  auxUnitName?: string
  conversionFactor?: number | null
  salePrice?: number
  purchasePrice?: number
  unitPrice?: number
  costPrice?: number
  barcode?: string
  productType?: string
  taxRate?: number
  lotControlled?: boolean
  shelfLifeControlled?: boolean
  status?: string
  inspectionRequired?: boolean
  serialControlled?: boolean
  remark?: string
}

export interface ProductStockSummary {
  productId: string
  warehouseCount: number
  qtyOnHand: number
  qtyReserved: number
  qtyAvailable: number
  amountOnHand: number
}

// 产品API
export const getProducts = (params: ProductQuery) => {
  return request.get<PageResponse<ProductContract>>('/masterdata/products', {
    params: toProductQueryParams(params)
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizeProduct)
  }))
}

export const getProduct = (id: string | number) => {
  return request.get<ProductContract>(`/masterdata/products/${id}`).then(normalizeProduct)
}

export const getProductStockSummary = (id: string | number) => {
  return request.get<ProductStockSummary>(`/masterdata/products/${id}/stock-summary`).then((summary) => ({
    ...summary,
    productId: String(summary.productId)
  }))
}

export const getProductByBarcode = (barcode: string) => {
  const normalizedBarcode = barcode.trim()
  if (!normalizedBarcode) {
    return Promise.reject(new Error('商品条码不能为空'))
  }
  return request.get<ProductContract>('/masterdata/products/by-barcode', {
    params: { barcode: normalizedBarcode }
  }).then(normalizeProduct)
}

export const createProduct = (data: ProductSaveRequest) => {
  return request.post<ProductContract>('/masterdata/products', toProductCreateContract(data)).then(normalizeProduct)
}

export const updateProduct = (id: string | number, data: ProductSaveRequest) => {
  return request.put<ProductContract>(`/masterdata/products/${id}`, toProductUpdateContract(data)).then(normalizeProduct)
}

export const deleteProduct = (id: string | number) => {
  return request.post<ProductContract>(`/masterdata/products/${id}/disable`).then(normalizeProduct)
}

export const enableProduct = (id: string | number) => {
  return request.post<ProductContract>(`/masterdata/products/${id}/enable`).then(normalizeProduct)
}

export const exportProducts = (params: ProductQuery) => {
  return request.get<Blob>('/masterdata/products/export', {
    params: toProductQueryParams(params),
    responseType: 'blob'
  })
}

const toProductQueryParams = (params: ProductQuery) => {
  const { code, name, ...rest } = params
  return {
    ...rest,
    keyword: params.keyword || code || name || undefined
  }
}

const requiredText = (value: string | undefined, field: string) => {
  const normalized = value?.trim()
  if (!normalized) throw new Error(`${field}不能为空`)
  return normalized
}

const requiredNumber = (value: number | undefined, field: string) => {
  if (value === undefined || value === null || !Number.isFinite(Number(value))) {
    throw new Error(`${field}不能为空`)
  }
  return Number(value)
}

const toProductCommonContract = (data: ProductSaveRequest): ProductUpdateContract => ({
  productName: requiredText(data.productName || data.name, '产品名称'),
  categoryName: requiredText(data.categoryName, '产品分类'),
  specification: data.specification || data.specifications,
  unitName: requiredText(data.unitName || data.unit, '单位'),
  auxUnitName: data.auxUnitName?.trim() || undefined,
  conversionFactor: data.conversionFactor != null && data.conversionFactor !== undefined
    ? Number(data.conversionFactor)
    : undefined,
  salePrice: requiredNumber(data.salePrice ?? data.unitPrice, '销售单价'),
  purchasePrice: requiredNumber(data.purchasePrice ?? data.costPrice, '成本单价'),
  taxRate: requiredNumber(data.taxRate, '税率'),
  barcode: data.barcode?.trim() || undefined,
  lotControlled: Boolean(data.lotControlled),
  shelfLifeControlled: Boolean(data.shelfLifeControlled),
  inspectionRequired: Boolean(data.inspectionRequired),
  serialControlled: Boolean(data.serialControlled),
  remark: data.remark?.trim() || undefined
})

export const toProductCreateContract = (data: ProductSaveRequest): ProductCreateContract => ({
  ...toProductCommonContract(data),
  productCode: requiredText(data.productCode || data.code, '产品编码'),
  productType: requiredText(data.productType, '商品类型')
})

export const toProductUpdateContract = (data: ProductSaveRequest): ProductUpdateContract =>
  toProductCommonContract(data)

const normalizeProduct = (product: ProductContract): Product => ({
  ...product,
  id: String(product.id),
  status: product.status === 'INACTIVE' ? 'INACTIVE' : 'ACTIVE',
  code: product.productCode,
  name: product.productName,
  specifications: product.specification,
  unit: product.unitName,
  unitPrice: product.salePrice,
  costPrice: product.purchasePrice,
  auxUnitName: product.auxUnitName || undefined,
  conversionFactor: product.conversionFactor != null ? Number(product.conversionFactor) : undefined
})

// ==================== 客户管理 ====================

export interface Customer {
  id: string
  customerCode: string          // 后端字段
  customerName: string          // 后端字段
  contactName?: string          // 后端字段
  contactPhone?: string         // 后端字段
  settlementMethod?: string     // 结算方式
  creditLimit?: number
  creditPeriod?: number
  address?: string
  status: 'ACTIVE' | 'INACTIVE'
  remark?: string
  createdTime?: string          // 后端字段
  updatedTime?: string          // 后端字段

  // 兼容别名
  code?: string
  name?: string
  type?: 'COMPANY' | 'INDIVIDUAL'
  contact?: string
  mobile?: string
  email?: string
}

export interface CustomerQuery extends PageQuery {
  code?: string
  name?: string
  keyword?: string
  type?: string
  status?: string
  settlementMethod?: string
}

export interface CustomerSaveRequest {
  code?: string
  name?: string
  customerCode?: string
  customerName?: string
  type?: string
  contactName?: string
  contactPhone?: string
  settlementMethod?: string
  contact?: string
  mobile?: string
  email?: string
  address?: string
  creditLimit?: number
  creditPeriod?: number
  status?: string
  remark?: string
}

export interface CustomerCreditExposure {
  customerId: string
  creditLimit: number
  outstandingReceivable: number
  openOrderExposure: number
  totalExposure: number
  availableCredit?: number
  unlimited: boolean
  exceeded: boolean
}

// 客户API
export const getCustomers = (params: CustomerQuery) => {
  return request.get<PageResponse<Customer>>('/masterdata/customers', {
    params: toCustomerQueryParams(params)
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizeCustomer)
  }))
}

export const getCustomer = (id: string | number) => {
  return request.get<Customer>(`/masterdata/customers/${id}`).then(normalizeCustomer)
}

export const getCustomerCreditExposure = (id: string | number) => {
  return request.get<CustomerCreditExposure>(`/masterdata/customers/${id}/credit-exposure`).then((exposure) => ({
    ...exposure,
    customerId: String(exposure.customerId)
  }))
}

export const createCustomer = (data: CustomerSaveRequest) => {
  return request.post<Customer>('/masterdata/customers', data).then(normalizeCustomer)
}

export const updateCustomer = (id: string | number, data: CustomerSaveRequest) => {
  return request.put<Customer>(`/masterdata/customers/${id}`, data).then(normalizeCustomer)
}

export const deleteCustomer = (id: string | number) => {
  return request.post<Customer>(`/masterdata/customers/${id}/disable`).then(normalizeCustomer)
}

export const enableCustomer = (id: string | number) => {
  return request.post<Customer>(`/masterdata/customers/${id}/enable`).then(normalizeCustomer)
}

export const exportCustomers = (params: CustomerQuery) => {
  return request.get<Blob>('/masterdata/customers/export', {
    params: toCustomerQueryParams(params),
    responseType: 'blob'
  })
}

export interface CustomerProductRelation {
  id: string
  customerId: string
  productId: string
  productCode?: string
  productName?: string
  customerProductCode?: string
  customerProductName?: string
  deliveryPreference?: string
  packagingPreference?: string
  remark?: string
  status: 'ACTIVE' | 'INACTIVE'
}

export const getCustomerProductRelations = (customerId: string | number) =>
  request.get<CustomerProductRelation[]>(`/masterdata/customers/${customerId}/products`).then((rows) => rows.map((row) => ({ ...row, id: String(row.id), customerId: String(row.customerId), productId: String(row.productId) })))

export const saveCustomerProductRelation = (customerId: string | number, data: Omit<CustomerProductRelation, 'id' | 'customerId' | 'productCode' | 'productName' | 'status'>) =>
  request.post<CustomerProductRelation>(`/masterdata/customers/${customerId}/products`, data).then((row) => ({ ...row, id: String(row.id), customerId: String(row.customerId), productId: String(row.productId) }))

export const deleteCustomerProductRelation = (customerId: string | number, id: string | number) =>
  request.delete(`/masterdata/customers/${customerId}/products/${id}`)

const toCustomerQueryParams = (params: CustomerQuery) => {
  const { code, name, ...rest } = params
  return {
    ...rest,
    keyword: params.keyword || code || name || undefined
  }
}

const normalizeCustomer = (customer: Customer): Customer => ({
  ...customer,
  id: String(customer.id),
  code: customer.code || customer.customerCode,
  name: customer.name || customer.customerName,
  contact: customer.contact || customer.contactName,
  mobile: customer.mobile || customer.contactPhone
})

// ==================== 供应商管理 ====================

export interface Supplier {
  id: string
  supplierCode: string          // 后端字段
  supplierName: string          // 后端字段
  contactName?: string          // 后端字段
  contactPhone?: string         // 后端字段
  settlementMethod?: string     // 结算方式
  address?: string
  status: 'ACTIVE' | 'INACTIVE'
  remark?: string
  createdTime?: string          // 后端字段
  updatedTime?: string          // 后端字段

  // 兼容别名
  code?: string
  name?: string
  contact?: string
  mobile?: string
  email?: string
  creditPeriod?: number
}

export interface SupplierQuery extends PageQuery {
  code?: string
  name?: string
  keyword?: string
  status?: string
  settlementMethod?: string
}

export interface SupplierSaveRequest {
  code?: string
  name?: string
  supplierCode?: string
  supplierName?: string
  contact?: string
  contactName?: string
  contactPhone?: string
  settlementMethod?: string
  mobile?: string
  email?: string
  address?: string
  creditPeriod?: number
  status?: string
  remark?: string
}

export interface SupplierPayableExposure {
  supplierId: string
  outstandingPayable: number
  openPurchaseOrderAmount: number
  totalExposure: number
}

// 供应商API
export const getSuppliers = (params: SupplierQuery) => {
  return request.get<PageResponse<Supplier>>('/masterdata/suppliers', {
    params: toSupplierQueryParams(params)
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizeSupplier)
  }))
}

export const getSupplier = (id: string | number) => {
  return request.get<Supplier>(`/masterdata/suppliers/${id}`).then(normalizeSupplier)
}

export const getSupplierPayableExposure = (id: string | number) => {
  return request.get<SupplierPayableExposure>(`/masterdata/suppliers/${id}/payable-exposure`).then((exposure) => ({
    ...exposure,
    supplierId: String(exposure.supplierId)
  }))
}

export const createSupplier = (data: SupplierSaveRequest) => {
  return request.post<Supplier>('/masterdata/suppliers', data).then(normalizeSupplier)
}

export const updateSupplier = (id: string | number, data: SupplierSaveRequest) => {
  return request.put<Supplier>(`/masterdata/suppliers/${id}`, data).then(normalizeSupplier)
}

export const deleteSupplier = (id: string | number) => {
  return request.post<Supplier>(`/masterdata/suppliers/${id}/disable`).then(normalizeSupplier)
}

export const enableSupplier = (id: string | number) => {
  return request.post<Supplier>(`/masterdata/suppliers/${id}/enable`).then(normalizeSupplier)
}

export const exportSuppliers = (params: SupplierQuery) => {
  return request.get<Blob>('/masterdata/suppliers/export', {
    params: toSupplierQueryParams(params),
    responseType: 'blob'
  })
}

export interface SupplierProductRelation {
  id: string
  supplierId: string
  productId: string
  productCode?: string
  productName?: string
  supplierProductCode?: string
  supplierProductName?: string
  minPurchaseQty: number
  leadTimeDays: number
  defaultSupplier: boolean
  remark?: string
  status: 'ACTIVE' | 'INACTIVE'
}

export const getSupplierProductRelations = (supplierId: string | number) =>
  request.get<SupplierProductRelation[]>(`/masterdata/suppliers/${supplierId}/products`).then((rows) => rows.map((row) => ({ ...row, id: String(row.id), supplierId: String(row.supplierId), productId: String(row.productId), minPurchaseQty: Number(row.minPurchaseQty || 0), leadTimeDays: Number(row.leadTimeDays || 0), defaultSupplier: Boolean(row.defaultSupplier) })))

export const saveSupplierProductRelation = (supplierId: string | number, data: Omit<SupplierProductRelation, 'id' | 'supplierId' | 'productCode' | 'productName' | 'status'>) =>
  request.post<SupplierProductRelation>(`/masterdata/suppliers/${supplierId}/products`, data).then((row) => ({ ...row, id: String(row.id), supplierId: String(row.supplierId), productId: String(row.productId), minPurchaseQty: Number(row.minPurchaseQty || 0), leadTimeDays: Number(row.leadTimeDays || 0), defaultSupplier: Boolean(row.defaultSupplier) }))

export const deleteSupplierProductRelation = (supplierId: string | number, id: string | number) =>
  request.delete(`/masterdata/suppliers/${supplierId}/products/${id}`)

const toSupplierQueryParams = (params: SupplierQuery) => {
  const { code, name, ...rest } = params
  return {
    ...rest,
    keyword: params.keyword || code || name || undefined
  }
}

// ==================== 仓库管理 ====================

export interface Warehouse {
  id: string
  warehouseCode: string         // 后端字段
  warehouseName: string         // 后端字段
  deptId?: string               // 所属部门
  managerUserId?: string        // 仓库管理员
  address?: string
  status: 'ACTIVE' | 'INACTIVE'
  remark?: string
  createdTime?: string          // 后端字段
  updatedTime?: string          // 后端字段

  // 兼容别名
  code?: string
  name?: string
  type?: 'RAW' | 'PRODUCT' | 'SEMI' | 'OTHER'
  manager?: string
  contact?: string
}

export interface WarehouseQuery extends PageQuery {
  code?: string
  name?: string
  keyword?: string
  type?: string
  status?: string
  deptId?: string | number
  managerUserId?: string | number
}

export interface WarehouseSaveRequest {
  code?: string
  name?: string
  warehouseCode?: string
  warehouseName?: string
  deptId?: string | number
  managerUserId?: string | number
  type?: string
  address?: string
  manager?: string
  contact?: string
  status?: string
  remark?: string
}

export interface WarehouseStockSummary {
  warehouseId: string
  skuCount: number
  qtyOnHand: number
  qtyReserved: number
  qtyAvailable: number
  amountOnHand: number
}

// 仓库API
export const getWarehouses = (params: WarehouseQuery) => {
  return request.get<PageResponse<Warehouse>>('/masterdata/warehouses', {
    params: toWarehouseQueryParams(params)
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizeWarehouse)
  }))
}

export const getWarehouse = (id: string | number) => {
  return request.get<Warehouse>(`/masterdata/warehouses/${id}`).then(normalizeWarehouse)
}

export const getWarehouseStockSummary = (id: string | number) => {
  return request.get<WarehouseStockSummary>(`/masterdata/warehouses/${id}/stock-summary`).then((summary) => ({
    ...summary,
    warehouseId: String(summary.warehouseId)
  }))
}

export const createWarehouse = (data: WarehouseSaveRequest) => {
  return request.post<Warehouse>('/masterdata/warehouses', data).then(normalizeWarehouse)
}

export const updateWarehouse = (id: string | number, data: WarehouseSaveRequest) => {
  return request.put<Warehouse>(`/masterdata/warehouses/${id}`, data).then(normalizeWarehouse)
}

export const deleteWarehouse = (id: string | number) => {
  return request.post<Warehouse>(`/masterdata/warehouses/${id}/disable`).then(normalizeWarehouse)
}

export const enableWarehouse = (id: string | number) => {
  return request.post<Warehouse>(`/masterdata/warehouses/${id}/enable`).then(normalizeWarehouse)
}

export const exportWarehouses = (params: WarehouseQuery) => {
  return request.get<Blob>('/masterdata/warehouses/export', {
    params: toWarehouseQueryParams(params),
    responseType: 'blob'
  })
}

const toWarehouseQueryParams = (params: WarehouseQuery) => {
  const { code, name, ...rest } = params
  return {
    ...rest,
    keyword: params.keyword || code || name || undefined
  }
}

const normalizeWarehouse = (warehouse: Warehouse): Warehouse => ({
  ...warehouse,
  id: String(warehouse.id),
  deptId: warehouse.deptId != null ? String(warehouse.deptId) : undefined,
  managerUserId: warehouse.managerUserId != null ? String(warehouse.managerUserId) : undefined,
  code: warehouse.code || warehouse.warehouseCode,
  name: warehouse.name || warehouse.warehouseName
})

const normalizeSupplier = (supplier: Supplier): Supplier => ({
  ...supplier,
  id: String(supplier.id),
  code: supplier.code || supplier.supplierCode,
  name: supplier.name || supplier.supplierName,
  contact: supplier.contact || supplier.contactName,
  mobile: supplier.mobile || supplier.contactPhone
})


export interface Location {
  id: string | number
  warehouseId: string | number
  warehouseName?: string
  locationCode: string
  locationName: string
  isDefault: boolean
  status: string
  remark?: string
}

export interface LocationQuery extends PageQuery {
  warehouseId?: string | number
  status?: string
  keyword?: string
}

export interface LocationSaveRequest {
  warehouseId?: string | number
  locationCode: string
  locationName: string
  isDefault?: boolean
  status?: string
  remark?: string
}

const normalizeLocation = (item: Location): Location => ({
  ...item,
  id: String(item.id),
  warehouseId: String(item.warehouseId),
  isDefault: Boolean(item.isDefault)
})

export const getLocations = (params: LocationQuery) => {
  return request.get<PageResponse<Location>>('/masterdata/locations', { params }).then((page) => ({
    ...page,
    records: (page.records || []).map(normalizeLocation)
  }))
}

export const createLocation = (data: LocationSaveRequest) => {
  return request.post<Location>('/masterdata/locations', data).then(normalizeLocation)
}

export const updateLocation = (id: string | number, data: LocationSaveRequest) => {
  return request.put<Location>(`/masterdata/locations/${id}`, data).then(normalizeLocation)
}

export const enableLocation = (id: string | number) => {
  return request.post<Location>(`/masterdata/locations/${id}/enable`).then(normalizeLocation)
}

export const disableLocation = (id: string | number) => {
  return request.post<Location>(`/masterdata/locations/${id}/disable`).then(normalizeLocation)
}
