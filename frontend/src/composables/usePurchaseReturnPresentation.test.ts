import { ref } from 'vue'
import { describe, expect, it } from 'vitest'

import type { Location } from '@/api/masterdata'
import type { PurchaseReceipt, PurchaseReturn } from '@/api/purchase'
import {
  usePurchaseReturnPresentation,
  usePurchaseReturnSummary
} from './usePurchaseReturnPresentation'

describe('purchase return presentation', () => {
  it('counts draft/completed rows and filters locations by warehouse', () => {
    const locations = ref<Location[]>([
      { id: 'l1', warehouseId: 'w1', locationCode: 'A', locationName: 'A1' },
      { id: 'l2', warehouseId: 'w2', locationCode: 'B', locationName: 'B1' }
    ] as Location[])
    const presentation = usePurchaseReturnPresentation(locations)

    expect(presentation.draftCount([
      { status: 'DRAFT' },
      { status: 'POSTED' },
      { status: 'DRAFT' }
    ] as PurchaseReturn[])).toBe(2)
    expect(presentation.completedCount([
      { status: 'POSTED' },
      { status: 'COMPLETED' },
      { status: 'CANCELLED' }
    ] as PurchaseReturn[])).toBe(2)
    expect(presentation.locationsForReceipt('w2').map((item) => item.id)).toEqual(['l2'])
  })

  it('exposes reactive summary counts and selected-receipt locations', () => {
    const locations = ref<Location[]>([
      { id: 'l1', warehouseId: 'w1', locationCode: 'A', locationName: 'A1' },
      { id: 'l2', warehouseId: 'w9', locationCode: 'Z', locationName: 'Z1' }
    ] as Location[])
    const tableData = ref([
      { status: 'DRAFT' },
      { status: 'POSTED' }
    ] as PurchaseReturn[])
    const selectedReceipt = ref<PurchaseReceipt | undefined>({
      id: 'r1',
      warehouseId: 'w9'
    } as PurchaseReceipt)
    const summary = usePurchaseReturnSummary(tableData, locations, selectedReceipt)

    expect(summary.draftCount.value).toBe(1)
    expect(summary.completedCount.value).toBe(1)
    expect(summary.locationsForSelectedReceipt.value.map((item) => item.id)).toEqual(['l2'])
  })
})
