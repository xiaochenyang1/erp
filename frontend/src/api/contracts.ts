import { request } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'
import type { Attachment } from '@/api/attachment'

export interface ContractLine {
  id?: string
  lineNo?: number
  productId: string
  productCode?: string
  productName?: string
  quantity: number
  committedQuantity?: number
  fulfilledQuantity?: number
  unitPrice: number
  amount?: number
  remark?: string
}

export interface ContractRecord {
  id: string
  contractNo: string
  contractType: 'SALES' | 'PURCHASE'
  customerId?: string
  customerName?: string
  supplierId?: string
  supplierName?: string
  contractName: string
  signedDate: string
  effectiveFrom: string
  effectiveTo?: string
  status: 'DRAFT' | 'SUBMITTED' | 'REJECTED' | 'ACTIVE' | 'CLOSED' | 'CANCELLED'
  totalAmount: number
  remark?: string
  lines: ContractLine[]
}

export interface ContractQuery extends PageQuery {
  keyword?: string
  contractType?: string
  status?: string
  customerId?: string
  supplierId?: string
  effectiveFrom?: string
  effectiveTo?: string
}

export interface ContractAlertRecord {
  contractId: string
  contractNo: string
  contractName: string
  contractType: 'SALES' | 'PURCHASE'
  effectiveTo?: string
  daysToExpiry: number
  executionRate: number
  alertTypes: string[]
}

export interface ContractSaveRequest {
  contractType: 'SALES' | 'PURCHASE'
  customerId?: string
  supplierId?: string
  contractName: string
  signedDate: string
  effectiveFrom: string
  effectiveTo?: string
  remark?: string
  lines: Array<{ productId: string; quantity: number; unitPrice: number; remark?: string }>
}

export interface ContractVersionLine {
  lineNo: number
  productId: string
  quantity: number
  fulfilledQuantity?: number
  unitPrice: number
  amount: number
  remark?: string
}

export interface ContractVersionRecord {
  id: string
  contractId: string
  versionNo: number
  eventType: string
  status: string
  header: {
    contractNo: string
    contractType: 'SALES' | 'PURCHASE'
    customerId?: string
    supplierId?: string
    contractName: string
    signedDate: string
    effectiveFrom: string
    effectiveTo?: string
    totalAmount: number
    remark?: string
  }
  lines: ContractVersionLine[]
  changedFields: string[]
  createdBy?: string
  createdTime?: string
}

const normalizeContract = (record: ContractRecord): ContractRecord => ({
  ...record,
  id: String(record.id),
  customerId: record.customerId != null ? String(record.customerId) : undefined,
  supplierId: record.supplierId != null ? String(record.supplierId) : undefined,
  totalAmount: Number(record.totalAmount || 0),
  lines: (record.lines || []).map((line) => ({
    ...line,
    id: line.id != null ? String(line.id) : undefined,
    productId: String(line.productId),
    quantity: Number(line.quantity || 0),
    committedQuantity: Number(line.committedQuantity || 0),
    fulfilledQuantity: Number(line.fulfilledQuantity || 0),
    unitPrice: Number(line.unitPrice || 0),
    amount: Number(line.amount || 0)
  }))
})

export const getContracts = (params: ContractQuery) =>
  request.get<PageResponse<ContractRecord>>('/contracts', { params }).then((page) => ({
    ...page,
    records: (page.records || []).map(normalizeContract)
  }))

export const getContract = (id: string | number) =>
  request.get<ContractRecord>(`/contracts/${id}`).then(normalizeContract)

export const createContract = (data: ContractSaveRequest) =>
  request.post<ContractRecord>('/contracts', data).then(normalizeContract)

export const updateContract = (id: string | number, data: ContractSaveRequest) =>
  request.put<ContractRecord>(`/contracts/${id}`, data).then(normalizeContract)

export const submitContract = (id: string | number) => request.post<ContractRecord>(`/contracts/${id}/submit`).then(normalizeContract)
export const approveContract = (id: string | number) => request.post<ContractRecord>(`/contracts/${id}/approve`).then(normalizeContract)
export const rejectContract = (id: string | number) => request.post<ContractRecord>(`/contracts/${id}/reject`).then(normalizeContract)
export const closeContract = (id: string | number) => request.post<ContractRecord>(`/contracts/${id}/close`).then(normalizeContract)
export const cancelContract = (id: string | number) => request.post<ContractRecord>(`/contracts/${id}/cancel`).then(normalizeContract)
export const exportContracts = (params: ContractQuery) => request.get<Blob>('/contracts/export', { params, responseType: 'blob' })
export const getContractAlerts = (expirationWarningDays = 30, lowExecutionRate = 0.5) =>
  request.get<ContractAlertRecord[]>('/contracts/alerts', { params: { expirationWarningDays, lowExecutionRate } }).then((records) => records.map((record) => ({ ...record, contractId: String(record.contractId), daysToExpiry: Number(record.daysToExpiry || 0), executionRate: Number(record.executionRate || 0) })))

export const getContractAttachments = (contractId: string | number, params: PageQuery = { pageNo: 1, pageSize: 50 }) =>
  request.get<PageResponse<Attachment>>(`/contracts/${contractId}/attachments`, { params }).then((page) => ({ ...page, records: page.records.map((item) => ({ ...item, id: String(item.id), businessId: String(item.businessId), createdBy: String(item.createdBy || '') })) }))

export const uploadContractAttachment = (contractId: string | number, file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<Attachment>(`/contracts/${contractId}/attachments`, formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}

export const downloadContractAttachment = (contractId: string | number, attachmentId: string | number) =>
  request.get(`/contracts/${contractId}/attachments/${attachmentId}/download`, { responseType: 'blob' })

export const deleteContractAttachment = (contractId: string | number, attachmentId: string | number) =>
  request.delete(`/contracts/${contractId}/attachments/${attachmentId}`)

const normalizeContractVersion = (record: ContractVersionRecord): ContractVersionRecord => ({
  ...record,
  id: String(record.id),
  contractId: String(record.contractId),
  versionNo: Number(record.versionNo || 0),
  createdBy: record.createdBy != null ? String(record.createdBy) : undefined,
  header: {
    ...record.header,
    customerId: record.header?.customerId != null ? String(record.header.customerId) : undefined,
    supplierId: record.header?.supplierId != null ? String(record.header.supplierId) : undefined,
    totalAmount: Number(record.header?.totalAmount || 0)
  },
  lines: (record.lines || []).map((line) => ({
    ...line,
    productId: String(line.productId),
    quantity: Number(line.quantity || 0),
    fulfilledQuantity: Number(line.fulfilledQuantity || 0),
    unitPrice: Number(line.unitPrice || 0),
    amount: Number(line.amount || 0)
  }))
})

export const getContractVersions = (contractId: string | number) =>
  request.get<ContractVersionRecord[]>(`/contracts/${contractId}/versions`).then((records) => records.map(normalizeContractVersion))

export const getContractVersion = (contractId: string | number, versionId: string | number) =>
  request.get<ContractVersionRecord>(`/contracts/${contractId}/versions/${versionId}`).then(normalizeContractVersion)

export const restoreContractVersion = (contractId: string | number, versionId: string | number) =>
  request.post<ContractRecord>(`/contracts/${contractId}/versions/${versionId}/restore`).then(normalizeContract)
