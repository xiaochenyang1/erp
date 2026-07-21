import { request } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

// ==================== 产品管理 ====================

export interface Product {
  id: string
  productCode: string           // 后端字段
  productName: string           // 后端字段
  productType?: string          // 后端字段
  categoryName?: string
  specification?: string        // 后端字段（注意：单数）
  unitName?: string            // 后端字段
  salePrice?: number           // 后端字段（销售单价）
  purchasePrice?: number       // 后端字段（采购单价/成本单价）
  taxRate?: number             // 税率
  barcode?: string
  status: 'ACTIVE' | 'INACTIVE'
  inspectionRequired?: boolean // 采购来料是否需检验
  remark?: string
  createdTime?: string
  updatedTime?: string

  // 为了兼容模板，添加别名
  code?: string
  name?: string
  specifications?: string      // 别名
  unit?: string               // 别名
  unitPrice?: number          // 别名
  costPrice?: number          // 别名
}

export interface ProductQuery extends PageQuery {
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
  salePrice?: number
  purchasePrice?: number
  unitPrice?: number
  costPrice?: number
  barcode?: string
  status?: string
  inspectionRequired?: boolean
  remark?: string
}

// 产品API
export const getProducts = (params: ProductQuery) => {
  return request.get<PageResponse<Product>>('/masterdata/products', {
    params: toProductQueryParams(params)
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizeProduct)
  }))
}

export const getProduct = (id: string | number) => {
  return request.get<Product>(`/masterdata/products/${id}`).then(normalizeProduct)
}

export const getProductByBarcode = (barcode: string) => {
  const normalizedBarcode = barcode.trim()
  if (!normalizedBarcode) {
    return Promise.reject(new Error('商品条码不能为空'))
  }
  return request.get<Product>('/masterdata/products/by-barcode', {
    params: { barcode: normalizedBarcode }
  }).then(normalizeProduct)
}

export const createProduct = (data: ProductSaveRequest) => {
  return request.post<Product>('/masterdata/products', data).then(normalizeProduct)
}

export const updateProduct = (id: string | number, data: ProductSaveRequest) => {
  return request.put<Product>(`/masterdata/products/${id}`, data).then(normalizeProduct)
}

export const deleteProduct = (id: string | number) => {
  return request.post<Product>(`/masterdata/products/${id}/disable`).then(normalizeProduct)
}

export const enableProduct = (id: string | number) => {
  return request.post<Product>(`/masterdata/products/${id}/enable`).then(normalizeProduct)
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

const normalizeProduct = (product: Product): Product => ({
  ...product,
  id: String(product.id),
  code: product.code || product.productCode,
  name: product.name || product.productName,
  specifications: product.specifications || product.specification,
  unit: product.unit || product.unitName,
  unitPrice: product.unitPrice ?? product.salePrice,
  costPrice: product.costPrice ?? product.purchasePrice
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
  status?: string
  remark?: string
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
