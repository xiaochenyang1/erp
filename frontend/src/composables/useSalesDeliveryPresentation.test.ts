import { ref } from 'vue'
import { describe, expect, it } from 'vitest'

import type { Location } from '@/api/masterdata'
import { useSalesDeliveryPresentation } from './useSalesDeliveryPresentation'

const t = (key: string) => key

describe('sales delivery presentation', () => {
  it('maps logistics labels/types and filters warehouse locations', () => {
    const locations = ref<Location[]>([
      { id: 'l1', warehouseId: 'w1', locationCode: 'A', locationName: 'A1' },
      { id: 'l2', warehouseId: 'w2', locationCode: 'B', locationName: 'B1' }
    ] as Location[])
    const warehouseId = ref<string | number | undefined>('w2')
    const presentation = useSalesDeliveryPresentation(t, locations, warehouseId)

    expect(presentation.logisticsStatusLabel('IN_TRANSIT')).toBe('salesDelivery.logistics.inTransit')
    expect(presentation.logisticsStatusType('DELIVERED')).toBe('success')
    expect(presentation.locationsForWarehouse.value.map((item) => item.id)).toEqual(['l2'])
    expect(presentation.deliveryQuantityTotal([
      { quantity: 2 },
      { quantity: 3 }
    ] as any)).toBe(5)
  })
})
