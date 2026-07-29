import type { Warehouse } from '@/api/masterdata'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info'

/** Pure display helpers for the warehouse location page. */
export const useWarehouseLocationPresentation = (t: Translate) => {
  const warehouseLabel = (warehouse?: Warehouse | null) => {
    if (!warehouse) return ''
    return `${warehouse.warehouseCode || ''} ${
      warehouse.warehouseName || warehouse.name || ''
    }`.trim()
  }

  const isActive = (status?: string | null) => status === 'ACTIVE'

  const statusLabel = (status?: string | null) =>
    isActive(status)
      ? t('warehouseLocation.active')
      : t('warehouseLocation.inactive')

  const statusTagType = (status?: string | null): TagType =>
    isActive(status) ? 'success' : 'info'

  const isDefaultLocation = (isDefault?: boolean | null) => Boolean(isDefault)

  const defaultLabel = (isDefault?: boolean | null) =>
    isDefaultLocation(isDefault)
      ? t('warehouseLocation.yes')
      : t('warehouseLocation.no')

  return {
    defaultLabel,
    isActive,
    isDefaultLocation,
    statusLabel,
    statusTagType,
    warehouseLabel
  }
}
