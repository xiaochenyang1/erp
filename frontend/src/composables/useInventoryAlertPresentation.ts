import type { InventoryAlert } from '@/api/inventory'
import { formatLocalizedNumber } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info' | 'warning' | 'danger'

export const useInventoryAlertPresentation = (t: Translate) => {
  const formatNumber = (value?: number) =>
    formatLocalizedNumber(Number(value || 0), { maximumFractionDigits: 4 })

  const alertTypeText = (type?: string) =>
    ({
      OUT_OF_STOCK: t('inventoryAlerts.alertType.outOfStock'),
      LOW_STOCK: t('inventoryAlerts.alertType.lowStock')
    }[String(type || '')] || type || '-')

  const alertTypeTag = (type?: string): TagType =>
    ({
      OUT_OF_STOCK: 'danger',
      LOW_STOCK: 'warning'
    }[String(type || '')] || 'info') as TagType

  const statusText = (status?: string) =>
    ({
      ACTIVE: t('inventoryAlerts.status.active'),
      IGNORED: t('inventoryAlerts.status.ignored'),
      RESOLVED: t('inventoryAlerts.status.resolved')
    }[String(status || '')] || status || '-')

  const statusTag = (status?: string): TagType =>
    ({
      ACTIVE: 'warning',
      IGNORED: 'info',
      RESOLVED: 'success'
    }[String(status || '')] || 'info') as TagType

  const buildStatistics = (rows: InventoryAlert[], total: number) => ({
    total,
    outOfStock: rows.filter((item) => item.alertType === 'OUT_OF_STOCK').length,
    lowStock: rows.filter((item) => item.alertType === 'LOW_STOCK').length,
    shortageQty: rows.reduce((sum, item) => sum + Number(item.shortageQty || 0), 0)
  })

  return {
    alertTypeTag,
    alertTypeText,
    buildStatistics,
    formatNumber,
    statusTag,
    statusText
  }
}
