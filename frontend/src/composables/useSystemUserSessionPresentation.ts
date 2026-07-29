import type { UserSession } from '@/api/userSession'
import { formatLocalizedDateTime } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info'

/** Pure labels, tags and timestamp formatting for the user session page. */
export const useSystemUserSessionPresentation = (t: Translate) => {
  const isActive = (status?: string | null) => status === 'ACTIVE'

  const statusLabel = (status?: string | null) => {
    const labels: Record<string, string> = {
      ACTIVE: t('userSessions.statusValue.active'),
      REVOKED: t('userSessions.statusValue.revoked')
    }
    return (status && labels[status]) || status || ''
  }

  const statusTagType = (status?: string | null): TagType =>
    isActive(status) ? 'success' : 'info'

  const formatDateTime = (value?: string | null) =>
    formatLocalizedDateTime(value) || '-'

  const realNameLabel = (realName?: string | null) => realName || '-'

  const sessionUserLabel = (
    session?: Pick<UserSession, 'userId' | 'username'> | null
  ) => session?.username || String(session?.userId ?? '')

  return {
    formatDateTime,
    isActive,
    realNameLabel,
    sessionUserLabel,
    statusLabel,
    statusTagType
  }
}
