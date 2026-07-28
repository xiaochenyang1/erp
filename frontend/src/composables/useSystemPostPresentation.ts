type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

/** Display helpers for system post status labels. */
export const useSystemPostPresentation = (t: Translate) => {
  const statusText = (status?: string) => {
    if (status === 'ACTIVE') return t('systemPost.active')
    if (status === 'INACTIVE') return t('systemPost.inactive')
    return status || ''
  }

  const statusType = (status?: string): TagType =>
    status === 'ACTIVE' ? 'success' : 'danger'

  return {
    statusText,
    statusType
  }
}
