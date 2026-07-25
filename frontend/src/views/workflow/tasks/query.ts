import type { WorkflowTaskQuery } from '@/api/workflow'

export type WorkflowTaskRouteQuery = Record<string, unknown>

const firstQueryValue = (value: unknown): unknown => (
  Array.isArray(value) ? value[0] : value
)

const readQueryString = (query: WorkflowTaskRouteQuery, key: string): string => {
  const value = firstQueryValue(query[key])
  if (value === undefined || value === null) return ''
  return String(value)
}

export const readQueryBoolean = (query: WorkflowTaskRouteQuery, key: string): boolean => {
  const value = firstQueryValue(query[key])
  return value === true || (typeof value === 'string' && value.trim().toLowerCase() === 'true')
}

export const createWorkflowTaskQueryFromRoute = (
  query: WorkflowTaskRouteQuery
): WorkflowTaskQuery => ({
  pageNo: 1,
  pageSize: 10,
  businessType: readQueryString(query, 'businessType'),
  businessId: readQueryString(query, 'businessId'),
  businessNo: readQueryString(query, 'businessNo'),
  status: readQueryString(query, 'status') || 'PENDING',
  overdueOnly: readQueryBoolean(query, 'overdueOnly')
})
