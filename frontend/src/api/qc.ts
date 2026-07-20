import { request } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

// ==================== 质检(IQC 来料 / OQC 出库) ====================

export type QcInspectionType = 'IQC' | 'OQC' | 'IPQC'

export interface QcInspectionLine {
  id: string | number
  lineNo: number
  receiptLineId?: string | number | null
  deliveryLineId?: string | number | null
  productId: string | number
  inspectedQty: number
  qualifiedQty: number
  unqualifiedQty: number
  defectReason?: string
  remark?: string
}

export interface QcInspection {
  id: string | number
  inspectionNo: string
  inspectionType: QcInspectionType
  receiptId?: string | number | null
  deliveryId?: string | number
  productionOrderId?: string | number | null
  orderId?: string | number
  warehouseId?: string | number
  supplierId?: string | number
  inspectionDate: string
  status: 'DRAFT' | 'SUBMITTED' | 'JUDGED' | 'CANCELLED'
  totalQty: number
  qualifiedQty: number
  unqualifiedQty: number
  remark?: string
  lines: QcInspectionLine[]
}

export interface QcInspectionQuery extends PageQuery {
  keyword?: string
  receiptId?: string | number
  deliveryId?: string | number
  productionOrderId?: string | number
  inspectionType?: QcInspectionType | ''
  status?: string
  inspectionDateFrom?: string
  inspectionDateTo?: string
}

export interface QcInspectionCreateRequest {
  inspectionType?: QcInspectionType
  receiptId?: string | number
  deliveryId?: string | number
  productionOrderId?: string | number
  inspectionDate: string
  remark?: string
}

export interface QcInspectionUpdateLine {
  lineId: string | number
  inspectedQty: number
  defectReason?: string
  remark?: string
}

export interface QcInspectionUpdateRequest {
  inspectionDate: string
  remark?: string
  lines?: QcInspectionUpdateLine[]
}

export interface QcInspectionJudgeLine {
  lineId: string | number
  qualifiedQty: number
  unqualifiedQty: number
  defectReason?: string
}

export interface QcInspectionJudgeRequest {
  lines: QcInspectionJudgeLine[]
}

const normalizeLine = (line: QcInspectionLine): QcInspectionLine => ({
  ...line,
  id: line.id != null ? String(line.id) : line.id,
  receiptLineId: line.receiptLineId != null ? String(line.receiptLineId) : line.receiptLineId,
  deliveryLineId: line.deliveryLineId != null ? String(line.deliveryLineId) : line.deliveryLineId,
  productId: line.productId != null ? String(line.productId) : line.productId,
  inspectedQty: Number(line.inspectedQty ?? 0),
  qualifiedQty: Number(line.qualifiedQty ?? 0),
  unqualifiedQty: Number(line.unqualifiedQty ?? 0)
})

const normalize = (inspection: QcInspection): QcInspection => ({
  ...inspection,
  id: String(inspection.id),
  inspectionType: (inspection.inspectionType || 'IQC') as QcInspectionType,
  receiptId: inspection.receiptId != null ? String(inspection.receiptId) : inspection.receiptId,
  deliveryId: inspection.deliveryId != null ? String(inspection.deliveryId) : inspection.deliveryId,
  orderId: inspection.orderId != null ? String(inspection.orderId) : inspection.orderId,
  warehouseId: inspection.warehouseId != null ? String(inspection.warehouseId) : inspection.warehouseId,
  supplierId: inspection.supplierId != null ? String(inspection.supplierId) : inspection.supplierId,
  totalQty: Number(inspection.totalQty ?? 0),
  qualifiedQty: Number(inspection.qualifiedQty ?? 0),
  unqualifiedQty: Number(inspection.unqualifiedQty ?? 0),
  lines: (inspection.lines || []).map(normalizeLine)
})

/**
 * 获取检验单列表
 */
export const getQcInspections = (params: QcInspectionQuery) => {
  return request.get<PageResponse<QcInspection>>('/qc/inspections', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalize)
  }))
}

/**
 * 获取检验单详情
 */
export const getQcInspection = (id: string | number) => {
  return request.get<QcInspection>(`/qc/inspections/${id}`).then(normalize)
}

/**
 * 创建检验单
 * - IQC: 引用 DRAFT 采购入库单
 * - OQC: 引用 DRAFT 销售出库单
 */
export const createQcInspection = (data: QcInspectionCreateRequest) => {
  return request.post<QcInspection>('/qc/inspections', data).then(normalize)
}

/**
 * 修改检验单(仅 DRAFT)
 */
export const updateQcInspection = (id: string | number, data: QcInspectionUpdateRequest) => {
  return request.put<QcInspection>(`/qc/inspections/${id}`, data).then(normalize)
}

/**
 * 提交检验单(DRAFT → SUBMITTED)
 */
export const submitQcInspection = (id: string | number) => {
  return request.post<QcInspection>(`/qc/inspections/${id}/submit`).then(normalize)
}

/**
 * 判定检验单(SUBMITTED → JUDGED)，录入每行合格/不合格数量
 */
export const judgeQcInspection = (id: string | number, data: QcInspectionJudgeRequest) => {
  return request.post<QcInspection>(`/qc/inspections/${id}/judge`, data).then(normalize)
}

/**
 * 作废检验单(DRAFT/SUBMITTED → CANCELLED)
 */
export const cancelQcInspection = (id: string | number) => {
  return request.post<QcInspection>(`/qc/inspections/${id}/cancel`).then(normalize)
}

/**
 * 导出检验单
 */
export const exportQcInspections = (params: QcInspectionQuery) => {
  return request.get<Blob>('/qc/inspections/export', {
    params,
    responseType: 'blob'
  })
}
