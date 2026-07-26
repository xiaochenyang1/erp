import type { Warehouse } from '@/api/masterdata'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

/** Display helpers for system role status and warehouse options. */
export const useSystemRolePresentation = (t: Translate) => {
  const statusText = (status?: string) => {
    if (status === 'ACTIVE') return t('systemRoles.active')
    if (status === 'INACTIVE') return t('systemRoles.inactive')
    return status || ''
  }

  const statusType = (status?: string): TagType =>
    status === 'ACTIVE' ? 'success' : 'danger'

  const warehouseOptionLabel = (warehouse: Warehouse) => {
    const name = warehouse.name || warehouse.warehouseName || t('systemRoles.warehouseFallback', { id: warehouse.id })
    const code = warehouse.code || warehouse.warehouseCode
    return code ? t('systemRoles.warehouseOption', { name, code }) : name
  }

  const permissionCount = (permissions?: unknown[]) => permissions?.length || 0

  return {
    permissionCount,
    statusText,
    statusType,
    warehouseOptionLabel
  }
}
