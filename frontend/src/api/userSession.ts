import { request } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

export interface UserSession {
  id: string
  userId: string
  username?: string
  realName?: string
  status: 'ACTIVE' | 'REVOKED' | string
  loginIp?: string
  userAgent?: string
  issuedAt?: string
  lastUsedAt?: string
  expiresAt?: string
  revokedAt?: string
}

export interface UserSessionQuery extends PageQuery {
  userId?: string | number
  username?: string
  status?: string
  issuedAtFrom?: string
  issuedAtTo?: string
}

export const getUserSessions = (params: UserSessionQuery) => {
  return request.get<PageResponse<UserSession>>('/system/user-sessions', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeUserSession)
  }))
}

export const revokeUserSession = (id: string | number) => {
  return request.post<void>(`/system/user-sessions/${id}/revoke`)
}

export const revokeUserSessionsByUser = (userId: string | number) => {
  return request.post<void>(`/system/users/${userId}/sessions/revoke`)
}

const normalizeUserSession = (session: UserSession): UserSession => ({
  ...session,
  id: String(session.id),
  userId: String(session.userId),
  username: session.username || '',
  realName: session.realName || '',
  loginIp: session.loginIp || '-',
  userAgent: session.userAgent || '-'
})
