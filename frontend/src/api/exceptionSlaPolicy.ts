import { request } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

export type ExceptionSlaPolicyPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

export interface ExceptionSlaPolicyQuery extends PageQuery {
  category?: string
  priority?: string
  enabled?: boolean
}

export interface ExceptionSlaPolicyUpdateRequest {
  dueHours?: number
  escalationEnabled?: boolean
  escalateToPriority?: string
  enabled?: boolean
  remark?: string
}

export interface ExceptionSlaPolicy {
  id: string
  category: string
  priority: ExceptionSlaPolicyPriority | string
  dueHours: number
  escalationEnabled: boolean
  escalateToPriority: ExceptionSlaPolicyPriority | string
  enabled: boolean
  remark?: string
  updatedTime?: string
}

export const getExceptionSlaPolicies = (params: ExceptionSlaPolicyQuery) => {
  return request.get<PageResponse<ExceptionSlaPolicy>>('/exception-sla-policies', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeExceptionSlaPolicy)
  }))
}

export const updateExceptionSlaPolicy = (id: string, data: ExceptionSlaPolicyUpdateRequest) => {
  return request.put<ExceptionSlaPolicy>(`/exception-sla-policies/${id}`, data).then(normalizeExceptionSlaPolicy)
}

const normalizeExceptionSlaPolicy = (policy: ExceptionSlaPolicy): ExceptionSlaPolicy => ({
  ...policy,
  id: String(policy.id)
})
