import { computed, type Ref } from 'vue'

import type { Location } from '@/api/masterdata'
import type { PurchaseReceipt, PurchaseReturn } from '@/api/purchase'

export type PurchaseReturnLike = Pick<PurchaseReturn, 'status'>

export const usePurchaseReturnPresentation = (
  locations: Ref<Location[]>,
  selectedReceipt?: Ref<PurchaseReceipt | undefined>
) => {
  const draftCount = (rows: PurchaseReturnLike[]) =>
    rows.filter((item) => item.status === 'DRAFT').length

  const completedCount = (rows: PurchaseReturnLike[]) =>
    rows.filter((item) => item.status === 'POSTED' || item.status === 'COMPLETED').length

  const locationsForReceipt = (warehouseId?: string | number | null) => {
    if (warehouseId == null || warehouseId === '') return locations.value
    return locations.value.filter((location) => String(location.warehouseId) === String(warehouseId))
  }

  const locationsForSelectedReceipt = computed(() =>
    locationsForReceipt(selectedReceipt?.value?.warehouseId)
  )

  return {
    completedCount,
    draftCount,
    locationsForReceipt,
    locationsForSelectedReceipt
  }
}

export const usePurchaseReturnSummary = (
  tableData: Ref<PurchaseReturnLike[]>,
  locations: Ref<Location[]>,
  selectedReceipt?: Ref<PurchaseReceipt | undefined>
) => {
  const presentation = usePurchaseReturnPresentation(locations, selectedReceipt)
  const draftCount = computed(() => presentation.draftCount(tableData.value))
  const completedCount = computed(() => presentation.completedCount(tableData.value))

  return {
    ...presentation,
    completedCount,
    draftCount
  }
}
