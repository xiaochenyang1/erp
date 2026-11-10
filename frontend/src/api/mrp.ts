import { request } from '@/utils/request'
import type { PageResponse } from '@/types/common'

type Id = string | number
export interface MrpSource {
  type: string
  documentId?: Id | null
  documentNo?: string | null
  lineId?: Id | null
  qty: number
  parentProductId?: Id | null
  parentWarehouseId?: Id | null
  bomId?: Id | null
  parentNetQty?: number | null
  qtyPer?: number | null
  baseQty?: number | null
  lossRate?: number | null
}
export interface MrpNodeTrace {
  productId: Id
  productCode: string
  productName: string
  warehouseId: Id
  warehouseName: string
  supplyType: string
  materialWarehouseId?: Id | null
  materialWarehouseName?: string | null
  bomId?: Id | null
  bomNo?: string | null
  bomVersion?: number | null
  level: number
  demandQty: number
  onHandQty: number
  purchaseSupplyQty: number
  productionSupplyQty: number
  plannedSupplyQty: number
  netQty: number
  sources: MrpSource[]
  supplies: Array<Pick<MrpSource, 'type' | 'documentId' | 'documentNo' | 'lineId' | 'qty'>>
  materials: Array<{ productId: Id; qtyPer: number; baseQty: number; lossRate: number }>
}
export interface MrpWarning {
  code: string
  documentId: Id
  documentNo: string
  productId: Id
  qty: number
  message: string
}
export interface MrpSuggestionLine {
  id?: Id
  runId?: Id
  lineNo?: number
  productId: Id
  productCode?: string
  productName?: string
  suggestionType: string
  demandQty: number
  onHandQty: number
  openSupplyQty: number
  netQty: number
  bomId?: Id | null
  reason?: string
  status?: string
  convertedBizType?: string | null
  convertedBizId?: Id | null
  convertedBizNo?: string | null
  convertedTime?: string | null
  warehouseId?: Id | null
  materialWarehouseId?: Id | null
  bomVersion?: number | null
  bomLevel?: number | null
  trace?: MrpNodeTrace | null
}
export interface MrpRunResult {
  id?: Id
  runNo?: string
  asOfDate: string
  status?: string
  purchaseCount: number
  productionCount: number
  createdTime?: string
  purchaseLines: MrpSuggestionLine[]
  productionLines: MrpSuggestionLine[]
  algorithmVersion?: number
  calculationSnapshot?: { algorithmVersion: number; nodes: MrpNodeTrace[]; warnings: MrpWarning[] } | null
}
export interface MrpRunSummary {
  id: Id
  runNo: string
  asOfDate: string
  status: string
  purchaseCount: number
  productionCount: number
  createdTime?: string
}

const nullableId = (id?: Id | null) => id == null ? id : String(id)
const nullableNumber = (value?: number | null) => value == null ? value : Number(value)
const normalizeSource = (source: MrpSource): MrpSource => ({
  ...source, documentId: nullableId(source.documentId), lineId: nullableId(source.lineId),
  parentProductId: nullableId(source.parentProductId), parentWarehouseId: nullableId(source.parentWarehouseId),
  bomId: nullableId(source.bomId), qty: Number(source.qty), parentNetQty: nullableNumber(source.parentNetQty),
  qtyPer: nullableNumber(source.qtyPer), baseQty: nullableNumber(source.baseQty), lossRate: nullableNumber(source.lossRate)
})
const normalizeNode = (node: MrpNodeTrace): MrpNodeTrace => ({
  ...node, productId: String(node.productId), warehouseId: String(node.warehouseId),
  materialWarehouseId: nullableId(node.materialWarehouseId), bomId: nullableId(node.bomId), level: Number(node.level),
  demandQty: Number(node.demandQty), onHandQty: Number(node.onHandQty), purchaseSupplyQty: Number(node.purchaseSupplyQty),
  productionSupplyQty: Number(node.productionSupplyQty), plannedSupplyQty: Number(node.plannedSupplyQty), netQty: Number(node.netQty),
  sources: (node.sources || []).map(normalizeSource), supplies: (node.supplies || []).map(normalizeSource),
  materials: (node.materials || []).map(m => ({ ...m, productId: String(m.productId), qtyPer: Number(m.qtyPer), baseQty: Number(m.baseQty), lossRate: Number(m.lossRate) }))
})
const normalizeLine = (line: MrpSuggestionLine): MrpSuggestionLine => ({
  ...line, id: nullableId(line.id) ?? undefined, runId: nullableId(line.runId) ?? undefined,
  productId: String(line.productId), bomId: nullableId(line.bomId), warehouseId: nullableId(line.warehouseId),
  materialWarehouseId: nullableId(line.materialWarehouseId), convertedBizId: nullableId(line.convertedBizId),
  demandQty: Number(line.demandQty ?? 0), onHandQty: Number(line.onHandQty ?? 0),
  openSupplyQty: Number(line.openSupplyQty ?? 0), netQty: Number(line.netQty ?? 0),
  trace: line.trace ? normalizeNode(line.trace) : line.trace
})
const normalizeRun = (run: MrpRunResult): MrpRunResult => ({
  ...run, id: nullableId(run.id) ?? undefined, purchaseCount: Number(run.purchaseCount ?? 0), productionCount: Number(run.productionCount ?? 0),
  purchaseLines: (run.purchaseLines || []).map(normalizeLine), productionLines: (run.productionLines || []).map(normalizeLine),
  calculationSnapshot: run.calculationSnapshot ? {
    ...run.calculationSnapshot, nodes: (run.calculationSnapshot.nodes || []).map(normalizeNode),
    warnings: (run.calculationSnapshot.warnings || []).map(w => ({ ...w, documentId: String(w.documentId), productId: String(w.productId), qty: Number(w.qty) }))
  } : run.calculationSnapshot
})

export const runMrpPlan = () => request.post<MrpRunResult>('/inventory/mrp/run').then(normalizeRun)
export const getMrpRun = (id: Id) => request.get<MrpRunResult>(`/inventory/mrp/runs/${id}`).then(normalizeRun)
export const getMrpRuns = (params?: { pageNo?: number; pageSize?: number; status?: string }) =>
  request.get<PageResponse<MrpRunSummary>>('/inventory/mrp/runs', { params }).then(page => ({
    ...page, records: (page.records || []).map(item => ({ ...item, id: String(item.id), purchaseCount: Number(item.purchaseCount ?? 0), productionCount: Number(item.productionCount ?? 0) }))
  }))
export const convertMrpLine = (runId: Id, lineId: Id,
  data?: { supplierId?: Id; finishedWarehouseId?: Id; materialWarehouseId?: Id }) =>
  request.post<MrpSuggestionLine>(`/inventory/mrp/runs/${runId}/lines/${lineId}/convert`, data || {}).then(normalizeLine)
