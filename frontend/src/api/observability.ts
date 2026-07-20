import { request } from '@/utils/request'

export interface SystemHealth {
  status: string
}

export interface BusinessHealthCheck {
  code: string
  name: string
  status: 'UP' | 'WARN' | string
  count: number
  threshold: number
  summary: string
}

export interface BusinessHealth {
  overallStatus: 'UP' | 'WARN' | string
  generatedAt: string
  checks: BusinessHealthCheck[]
}

export const getBusinessHealth = () => {
  return request.get<BusinessHealth>('/system/observability/business-health').then((health) => ({
    ...health,
    checks: health.checks || []
  }))
}

export const getSystemHealth = () => {
  return request.get<SystemHealth>('/health').then((health) => ({
    ...health,
    status: health.status || 'UNKNOWN'
  }))
}
