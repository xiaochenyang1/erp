type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

const STATUS_KEYS: Record<string, string> = {
  ACTIVE: 'productionWorkCenter.status.active',
  DISABLED: 'productionWorkCenter.status.disabled'
}

const STATUS_TAG_TYPES: Record<string, TagType> = {
  ACTIVE: 'success',
  DISABLED: 'danger'
}

/** Status labels/tags for production work centers. */
export const useProductionWorkCenterPresentation = (t: Translate) => {
  const getStatusLabel = (status?: string) => {
    if (!status) return ''
    const key = STATUS_KEYS[status]
    return key ? t(key) : status
  }

  const getStatusType = (status?: string): TagType =>
    (status && STATUS_TAG_TYPES[status]) || 'info'

  return {
    getStatusLabel,
    getStatusType
  }
}
