import { ref } from 'vue'
import { describe, expect, it } from 'vitest'

import type { Location } from '@/api/masterdata'
import {
  usePurchaseReceiptPresentation,
  usePurchaseReceiptSummary
} from './usePurchaseReceiptPresentation'

describe('purchase receipt presentation', () => {
  it('formats money and resolves location labels', () => {
    const locations = ref<Location[]>([
      {
        id: 'loc-1',
        warehouseId: 'w-1',
        locationCode: 'A01',
        locationName: 'Dock'
      } as Location
    ])
    const presentation = usePurchaseReceiptPresentation(locations)

    expect(presentation.formatMoney(12.3)).toMatch(/12\.30/)
    expect(presentation.locationLabel('loc-1')).toBe('A01 Dock')
    expect(presentation.locationLabel('missing')).toBe('missing')
    expect(presentation.locationLabel()).toBe('-')
  })

  it('filters locations by warehouse and counts draft/posted rows', () => {
    const locations = ref<Location[]>([
      { id: 'loc-1', warehouseId: 'w-1', locationCode: 'A01', locationName: 'Dock' } as Location,
      { id: 'loc-2', warehouseId: 'w-2', locationCode: 'B01', locationName: 'Shelf' } as Location
    ])
    const presentation = usePurchaseReceiptPresentation(locations)

    expect(presentation.locationsForWarehouse('w-2').map((item) => item.id)).toEqual(['loc-2'])
    expect(presentation.draftCount([
      { status: 'DRAFT' },
      { status: 'POSTED' },
      { status: 'DRAFT' }
    ])).toBe(2)
    expect(presentation.completedCount([
      { status: 'POSTED' },
      { status: 'COMPLETED' },
      { status: 'CANCELLED' }
    ])).toBe(2)
  })

  it('exposes reactive summary counts and warehouse locations', () => {
    const locations = ref<Location[]>([
      { id: 'loc-1', warehouseId: 'w-1', locationCode: 'A01', locationName: 'Dock' } as Location,
      { id: 'loc-2', warehouseId: 'w-9', locationCode: 'Z01', locationName: 'Yard' } as Location
    ])
    const tableData = ref([
      { status: 'DRAFT' },
      { status: 'POSTED' },
      { status: 'COMPLETED' }
    ])
    const warehouseId = ref<string | number | undefined>('w-9')
    const summary = usePurchaseReceiptSummary(tableData, locations, warehouseId)

    expect(summary.draftCount.value).toBe(1)
    expect(summary.completedCount.value).toBe(2)
    expect(summary.warehouseLocations.value.map((item) => item.id)).toEqual(['loc-2'])

    warehouseId.value = 'w-1'
    expect(summary.warehouseLocations.value.map((item) => item.id)).toEqual(['loc-1'])
  })
})
