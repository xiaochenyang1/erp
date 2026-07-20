import { request } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

export interface ReadinessPreflightItem {
  code: string
  status: 'PASS' | 'WARN' | 'FAIL' | string
  severity: 'P0' | 'P1' | 'P2' | string
  summary: string
  count: number
  sample: string[]
}

export interface ReadinessPreflight {
  overallStatus: 'PASS' | 'WARN' | 'FAIL' | string
  checkedAt: string
  items: ReadinessPreflightItem[]
}

export interface ReadinessRun {
  id: string
  runNo: string
  releaseCommit: string
  releaseVersion?: string
  environment: string
  databaseInstance?: string
  redisInstance?: string
  dockerProfile?: string
  status: 'DRAFT' | 'IN_PROGRESS' | 'PASSED' | 'FAILED' | 'BLOCKED' | 'NO_GO' | string
  decision: 'PENDING' | 'GO' | 'NO_GO' | string
  decisionComment?: string
  remark?: string
  startedBy?: string
  startedTime?: string
  decidedBy?: string
  decidedTime?: string
  createdTime?: string
}

export interface ReadinessItem {
  id: string
  runId: string
  itemCode: string
  itemName: string
  category: string
  priority: 'P0' | 'P1' | 'P2' | string
  status: 'PENDING' | 'PASSED' | 'FAILED' | 'BLOCKED' | 'SKIPPED' | string
  expectedResult?: string
  actualResult?: string
  failureReason?: string
  executedBy?: string
  executedTime?: string
  createdTime?: string
  evidence: ReadinessEvidence[]
}

export interface ReadinessEvidence {
  id: string
  runId: string
  itemId: string
  evidenceType: 'API' | 'BUSINESS_NO' | 'LOG' | 'SCREENSHOT' | 'NOTE' | 'ATTACHMENT' | string
  requestMethod?: string
  requestUri?: string
  httpStatus?: number
  businessType?: string
  businessId?: string
  businessNo?: string
  summary: string
  detail?: string
  attachmentBusinessType?: string
  attachmentBusinessId?: string
  recordedBy?: string
  recordedTime?: string
}

export interface ReadinessRunDetail {
  run: ReadinessRun
  items: ReadinessItem[]
}

export interface ReadinessRunQuery extends PageQuery {
  releaseCommit?: string
  environment?: string
  status?: string
  decision?: string
  createdTimeFrom?: string
  createdTimeTo?: string
}

export interface ReadinessRunCreateRequest {
  releaseCommit: string
  releaseVersion?: string
  environment: string
  databaseInstance?: string
  redisInstance?: string
  dockerProfile?: string
  generateDefaultItems?: boolean
  recordPreflightEvidence?: boolean
  remark?: string
}

export interface ReadinessItemCreateRequest {
  itemCode: string
  itemName: string
  category: string
  priority: string
  expectedResult?: string
}

export interface ReadinessEvidenceCreateRequest {
  evidenceType: string
  requestMethod?: string
  requestUri?: string
  httpStatus?: number
  businessType?: string
  businessId?: string | number
  businessNo?: string
  summary: string
  detail?: string
  attachmentBusinessType?: string
  attachmentBusinessId?: string | number
}

export interface ReadinessItemResultRequest {
  status: string
  actualResult?: string
  failureReason?: string
}

export interface ReadinessDecisionRequest {
  decision: string
  status: string
  decisionComment?: string
}

export const getReadinessPreflight = () => {
  return request.get<ReadinessPreflight>('/system/readiness/preflight').then(normalizePreflight)
}

export const recordReadinessPreflightEvidence = (runId: string | number) => {
  return request
    .post<ReadinessPreflight>(`/system/readiness/runs/${runId}/preflight-evidence`)
    .then(normalizePreflight)
}

export const createReadinessRun = (data: ReadinessRunCreateRequest) => {
  return request.post<ReadinessRun>('/system/readiness/runs', data).then(normalizeRun)
}

export const getReadinessRuns = (params: ReadinessRunQuery) => {
  return request.get<PageResponse<ReadinessRun>>('/system/readiness/runs', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeRun)
  }))
}

export const getReadinessRunDetail = (id: string | number) => {
  return request.get<ReadinessRunDetail>(`/system/readiness/runs/${id}`).then(normalizeDetail)
}

export const addReadinessItem = (runId: string | number, data: ReadinessItemCreateRequest) => {
  return request.post<ReadinessItem>(`/system/readiness/runs/${runId}/items`, data).then(normalizeItem)
}

export const addReadinessEvidence = (itemId: string | number, data: ReadinessEvidenceCreateRequest) => {
  return request.post<ReadinessEvidence>(`/system/readiness/items/${itemId}/evidence`, data).then(normalizeEvidence)
}

export const markReadinessItemResult = (itemId: string | number, data: ReadinessItemResultRequest) => {
  return request.post<ReadinessItem>(`/system/readiness/items/${itemId}/result`, data).then(normalizeItem)
}

export const decideReadinessRun = (runId: string | number, data: ReadinessDecisionRequest) => {
  return request.post<ReadinessRun>(`/system/readiness/runs/${runId}/decision`, data).then(normalizeRun)
}

const normalizePreflight = (preflight: ReadinessPreflight): ReadinessPreflight => ({
  ...preflight,
  items: (preflight.items || []).map((item) => ({
    ...item,
    count: Number(item.count ?? 0),
    sample: item.sample || []
  }))
})

const normalizeRun = (run: ReadinessRun): ReadinessRun => ({
  ...run,
  id: String(run.id),
  startedBy: run.startedBy != null ? String(run.startedBy) : undefined,
  decidedBy: run.decidedBy != null ? String(run.decidedBy) : undefined
})

const normalizeDetail = (detail: ReadinessRunDetail): ReadinessRunDetail => ({
  run: normalizeRun(detail.run),
  items: (detail.items || []).map(normalizeItem)
})

const normalizeItem = (item: ReadinessItem): ReadinessItem => ({
  ...item,
  id: String(item.id),
  runId: String(item.runId),
  executedBy: item.executedBy != null ? String(item.executedBy) : undefined,
  evidence: (item.evidence || []).map(normalizeEvidence)
})

const normalizeEvidence = (evidence: ReadinessEvidence): ReadinessEvidence => ({
  ...evidence,
  id: String(evidence.id),
  runId: String(evidence.runId),
  itemId: String(evidence.itemId),
  businessId: evidence.businessId != null ? String(evidence.businessId) : undefined,
  attachmentBusinessId: evidence.attachmentBusinessId != null ? String(evidence.attachmentBusinessId) : undefined,
  recordedBy: evidence.recordedBy != null ? String(evidence.recordedBy) : undefined
})
