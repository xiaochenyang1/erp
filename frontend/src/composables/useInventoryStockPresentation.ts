import { computed, ref, type Ref } from 'vue'

import type { Location, Product, Warehouse } from '@/api/masterdata'
import { formatLocalizedNumber } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info' | 'warning' | 'danger'

export const useInventoryStockPresentation = (
  warehouses: Ref<Warehouse[]>,
  products: Ref<Product[]>,
  t: Translate,
  locations: Ref<Location[]> = ref([])
) => {
  const warehouseMap = computed(() => new Map(
    warehouses.value.map((item) => [String(item.id), item.name])
  ))
  const productMap = computed(() => new Map(
    products.value.map((item) => [
      String(item.id),
      `${item.code || item.productCode || item.id} - ${item.name || item.productName || '-'}`
    ])
  ))
  const locationMap = computed(() => new Map(
    locations.value.map((item) => [
      String(item.id),
      `${item.locationCode} ${item.locationName}`
    ])
  ))

  const translatedLabel = (value: string | undefined, labels: Record<string, string>) => (
    value ? labels[value] || value : '-'
  )
  const tagType = (value: string | undefined, types: Record<string, TagType>) => (
    value ? types[value] || 'info' : 'info'
  )

  const warehouseName = (id: string) => warehouseMap.value.get(String(id))
    || t('inventoryStocks.warehouseFallback', { id })
  const productName = (id: string) => productMap.value.get(String(id))
    || t('inventoryStocks.productFallback', { id })
  const locationName = (id?: string | number | null) => {
    if (id == null || id === '') return '-'
    return locationMap.value.get(String(id))
      || t('inventoryStocks.locationFallback', { id })
  }
  const formatNumber = (value?: number) => formatLocalizedNumber(
    Number(value ?? 0),
    { maximumFractionDigits: 4 }
  )
  const formatOptionalNumber = (value?: number) => value == null ? '-' : formatNumber(value)
  const formatMoney = (value?: number) => formatLocalizedNumber(Number(value ?? 0), {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
  const sourceTypeLabel = (value?: string) => translatedLabel(value, {
    SALES_ORDER: t('inventoryStocks.sourceTypeValue.salesOrder')
  })
  const reservationStatusLabel = (value?: string) => translatedLabel(value, {
    ACTIVE: t('inventoryStocks.reservationStatus.active'),
    RELEASED: t('inventoryStocks.reservationStatus.released'),
    CANCELLED: t('inventoryStocks.reservationStatus.cancelled')
  })
  const reservationStatusType = (value?: string) => tagType(value, {
    ACTIVE: 'success',
    RELEASED: 'info',
    CANCELLED: 'warning'
  })
  const reservationEventLabel = (value?: string) => translatedLabel(value, {
    RESERVE: t('inventoryStocks.reservationEvent.reserve'),
    RELEASE: t('inventoryStocks.reservationEvent.release'),
    MANUAL_RELEASE: t('inventoryStocks.reservationEvent.manualRelease')
  })
  const directionLabel = (value?: string) => translatedLabel(value, {
    IN: t('inventoryStocks.directionValue.inbound'),
    OUT: t('inventoryStocks.directionValue.outbound')
  })
  const expiryStatusLabel = (value?: string) => translatedLabel(value, {
    EXPIRED: t('inventoryStocks.expiryStatus.expired'),
    EXPIRING: t('inventoryStocks.expiryStatus.expiring'),
    NORMAL: t('inventoryStocks.expiryStatus.normal')
  })
  const expiryStatusType = (value?: string) => tagType(value, {
    EXPIRED: 'danger',
    EXPIRING: 'warning',
    NORMAL: 'success'
  })
  const severityLabel = (value?: string) => translatedLabel(value, {
    ERROR: t('inventoryStocks.severityValue.error'),
    WARNING: t('inventoryStocks.severityValue.warning')
  })

  return {
    directionLabel,
    expiryStatusLabel,
    expiryStatusType,
    formatMoney,
    formatNumber,
    formatOptionalNumber,
    locationName,
    productName,
    reservationEventLabel,
    reservationStatusLabel,
    reservationStatusType,
    severityLabel,
    sourceTypeLabel,
    warehouseName
  }
}
