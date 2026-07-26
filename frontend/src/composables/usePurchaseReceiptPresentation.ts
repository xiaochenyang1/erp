import { computed, type Ref } from 'vue'

import type { Location } from '@/api/masterdata'
import { formatLocalizedNumber } from '@/utils/locale'

export type PurchaseReceiptLike = {
  status?: string
}

export const usePurchaseReceiptPresentation = (
  locations: Ref<Location[]>
) => {
  const formatMoney = (value?: number) => formatLocalizedNumber(Number(value ?? 0), {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })

  const locationLabel = (locationId?: string | number | null) => {
    if (locationId == null || locationId === '') return '-'
    const location = locations.value.find((item) => String(item.id) === String(locationId))
    return location ? `${location.locationCode} ${location.locationName}` : String(locationId)
  }

  const locationsForWarehouse = (warehouseId?: string | number | null) => {
    if (warehouseId == null || warehouseId === '') return locations.value
    return locations.value.filter((location) => String(location.warehouseId) === String(warehouseId))
  }

  const draftCount = (rows: PurchaseReceiptLike[]) =>
    rows.filter((item) => item.status === 'DRAFT').length

  const completedCount = (rows: PurchaseReceiptLike[]) =>
    rows.filter((item) => item.status === 'POSTED' || item.status === 'COMPLETED').length

  return {
    completedCount,
    draftCount,
    formatMoney,
    locationLabel,
    locationsForWarehouse
  }
}

export const usePurchaseReceiptSummary = (
  tableData: Ref<PurchaseReceiptLike[]>,
  locations: Ref<Location[]>,
  warehouseId?: Ref<string | number | undefined | null> | (() => string | number | undefined | null)
) => {
  const presentation = usePurchaseReceiptPresentation(locations)
  const resolveWarehouseId = () => {
    if (!warehouseId) return undefined
    return typeof warehouseId === 'function' ? warehouseId() : warehouseId.value
  }

  const draftCount = computed(() => presentation.draftCount(tableData.value))
  const completedCount = computed(() => presentation.completedCount(tableData.value))
  const warehouseLocations = computed(() => presentation.locationsForWarehouse(resolveWarehouseId()))

  return {
    ...presentation,
    completedCount,
    draftCount,
    warehouseLocations
  }
}
