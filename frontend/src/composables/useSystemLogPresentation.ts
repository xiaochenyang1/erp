type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

/** Display helpers for operation / login / audit logs. */
export const useSystemLogPresentation = () => {
  const getExecutionTimeType = (time?: number): TagType => {
    const value = Number(time ?? 0)
    if (value < 500) return 'success'
    if (value < 2000) return 'warning'
    return 'danger'
  }

  const isSuccess = (result?: string) => result === 'SUCCESS'

  const formatJson = (jsonStr?: string | object) => {
    if (jsonStr == null || jsonStr === '') return ''
    try {
      const obj = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr
      return JSON.stringify(obj, null, 2)
    } catch {
      return String(jsonStr)
    }
  }

  const toStartDateTime = (date?: string) => (date ? `${date}T00:00:00` : undefined)
  const toEndDateTime = (date?: string) => (date ? `${date}T23:59:59` : undefined)

  return {
    formatJson,
    getExecutionTimeType,
    isSuccess,
    toEndDateTime,
    toStartDateTime
  }
}
