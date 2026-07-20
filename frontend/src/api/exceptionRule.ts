import { request } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

export type ExceptionRuleType = 'LOW_STOCK' | 'RECEIVABLE_OVERDUE' | 'PAYABLE_OVERDUE' | 'OPERATION_FAILURE'
export type ExceptionRulePriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
export type ExceptionRuleScanStatus = 'SUCCESS' | 'FAILED' | 'SKIPPED'

export interface ExceptionRuleQuery extends PageQuery {
  keyword?: string
  ruleType?: string
  enabled?: boolean
}

export interface ExceptionRuleHitQuery extends PageQuery {
  ruleId?: string
  ruleType?: string
  sourceNo?: string
  ticketId?: string | number
}

export interface ExceptionRuleUpdateRequest {
  thresholdValue?: number
  thresholdUnit?: string
  priority?: string
  assigneeUserId?: string | number
  scheduleIntervalMinutes?: number
  remark?: string
}

export interface ExceptionRule {
  id: string
  ruleCode: string
  ruleName: string
  ruleType: ExceptionRuleType | string
  category: string
  priority: ExceptionRulePriority | string
  thresholdValue: number
  thresholdUnit: string
  enabled: boolean
  assigneeUserId?: string
  scheduleIntervalMinutes?: number
  nextScanTime?: string
  remark?: string
  lastScanTime?: string
  lastScanStatus?: ExceptionRuleScanStatus | string
  lastHitCount?: number
  lastTicketCreatedCount?: number
  lastErrorMessage?: string
  updatedTime?: string
}

export interface ExceptionRuleHit {
  id: string
  ruleId: string
  ruleCode: string
  ruleType: ExceptionRuleType | string
  sourceType: string
  sourceId?: string
  sourceNo?: string
  sourceRoute?: string
  hitKey: string
  title: string
  description?: string
  triggerValue?: string
  thresholdValue?: string
  ticketId?: string
  hitCount?: number
  firstHitTime?: string
  lastHitTime?: string
}

export interface ExceptionRuleScanResult {
  ruleId: string
  ruleCode: string
  ruleType: ExceptionRuleType | string
  status: ExceptionRuleScanStatus | string
  hitCount: number
  ticketCreatedCount: number
  duplicateTicketCount: number
  message?: string
  scannedAt?: string
}

export const getExceptionRules = (params: ExceptionRuleQuery) => {
  return request.get<PageResponse<ExceptionRule>>('/exception-rules', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeExceptionRule)
  }))
}

export const updateExceptionRule = (id: string | number, data: ExceptionRuleUpdateRequest) => {
  return request.put<ExceptionRule>(`/exception-rules/${id}`, data).then(normalizeExceptionRule)
}

export const enableExceptionRule = (id: string | number) => {
  return request.post<ExceptionRule>(`/exception-rules/${id}/enable`).then(normalizeExceptionRule)
}

export const disableExceptionRule = (id: string | number) => {
  return request.post<ExceptionRule>(`/exception-rules/${id}/disable`).then(normalizeExceptionRule)
}

export const scanExceptionRule = (id: string | number) => {
  return request.post<ExceptionRuleScanResult>(`/exception-rules/${id}/scan`).then(normalizeExceptionRuleScanResult)
}

export const scanAllExceptionRules = () => {
  return request.post<ExceptionRuleScanResult[]>('/exception-rules/scan-all')
    .then((results) => results.map(normalizeExceptionRuleScanResult))
}

export const getExceptionRuleHits = (params: ExceptionRuleHitQuery) => {
  return request.get<PageResponse<ExceptionRuleHit>>('/exception-rules/hits', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeExceptionRuleHit)
  }))
}

const normalizeExceptionRule = (rule: ExceptionRule): ExceptionRule => ({
  ...rule,
  id: String(rule.id),
  assigneeUserId: rule.assigneeUserId != null ? String(rule.assigneeUserId) : undefined
})

const normalizeExceptionRuleHit = (hit: ExceptionRuleHit): ExceptionRuleHit => ({
  ...hit,
  id: String(hit.id),
  ruleId: String(hit.ruleId),
  sourceId: hit.sourceId != null ? String(hit.sourceId) : undefined,
  ticketId: hit.ticketId != null ? String(hit.ticketId) : undefined
})

const normalizeExceptionRuleScanResult = (result: ExceptionRuleScanResult): ExceptionRuleScanResult => ({
  ...result,
  ruleId: String(result.ruleId)
})
