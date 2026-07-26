type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

/** Display helpers for system configs and sequence rules. */
export const useSystemConfigPresentation = (t: Translate) => {
  const statusText = (status?: string) => {
    if (status === 'ACTIVE') return t('systemConfigs.active')
    if (status === 'DISABLED' || status === 'INACTIVE') return t('systemConfigs.disabled')
    return status || ''
  }

  const statusType = (status?: string): TagType =>
    status === 'ACTIVE' ? 'success' : 'info'

  const validatePositiveInteger = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
    if (!/^[1-9]\d*$/.test(value || '')) {
      callback(new Error(t('systemConfigs.validation.positiveInteger')))
      return
    }
    callback()
  }

  const validateNonNegativeInteger = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
    if (!/^\d+$/.test(value || '')) {
      callback(new Error(t('systemConfigs.validation.nonNegativeInteger')))
      return
    }
    callback()
  }

  return {
    statusText,
    statusType,
    validateNonNegativeInteger,
    validatePositiveInteger
  }
}
