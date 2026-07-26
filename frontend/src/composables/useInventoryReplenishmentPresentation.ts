import { computed } from 'vue'

import { formatLocalizedDateTime, formatLocalizedNumber } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

/** Display helpers for replenishment suggestion status tags and numbers. */
export const useInventoryReplenishmentPresentation = (t: Translate) => {
  const fulfillmentStatusMap = computed<Record<string, { label: string; type: TagType }>>(() => ({
    SUGGESTED: { label: t('inventoryReplenishment.fulfillment.suggested'), type: 'warning' },
    PURCHASE_CREATED: { label: t('inventoryReplenishment.fulfillment.purchaseCreated'), type: 'primary' },
    PARTIAL_RECEIVED: { label: t('inventoryReplenishment.fulfillment.partialReceived'), type: 'warning' },
    REPLENISHED: { label: t('inventoryReplenishment.fulfillment.replenished'), type: 'success' },
    PURCHASE_CLOSED: { label: t('inventoryReplenishment.fulfillment.purchaseClosed'), type: 'info' },
    CANCELLED: { label: t('inventoryReplenishment.fulfillment.cancelled'), type: 'info' }
  }))

  const fulfillmentStatusMeta = (status?: string) =>
    fulfillmentStatusMap.value[status || ''] || {
      label: status || '-',
      type: 'info' as const
    }

  const formatNumber = (value?: number) =>
    formatLocalizedNumber(Number(value ?? 0), { maximumFractionDigits: 4 })

  const formatDateTime = (value?: string) => formatLocalizedDateTime(value) || '-'

  return {
    formatDateTime,
    formatNumber,
    fulfillmentStatusMeta
  }
}
