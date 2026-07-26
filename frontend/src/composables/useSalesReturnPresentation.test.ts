import { ref } from 'vue'
import { describe, expect, it } from 'vitest'

import type { Location } from '@/api/masterdata'
import type { SalesDelivery } from '@/api/sales'
import { useSalesReturnPresentation } from './useSalesReturnPresentation'

describe('sales return presentation', () => {
  it('resolves delivery labels and warehouse locations', () => {
    const deliveries = ref<SalesDelivery[]>([
      {
        id: 'd1',
        deliveryNo: 'SD001',
        customerName: 'Acme',
        warehouseName: 'Main',
        warehouseId: 'w2'
      } as SalesDelivery
    ])
    const locations = ref<Location[]>([
      { id: 'l1', warehouseId: 'w1', locationCode: 'A', locationName: 'A1' },
      { id: 'l2', warehouseId: 'w2', locationCode: 'B', locationName: 'B1' }
    ] as Location[])
    const selectedDeliveryId = ref<string | number | undefined>('d1')
    const presentation = useSalesReturnPresentation(deliveries, locations, selectedDeliveryId)

    expect(presentation.deliveryLabelById('d1')).toContain('SD001')
    expect(presentation.deliveryNoById('d1')).toBe('SD001')
    expect(presentation.selectedDelivery.value?.id).toBe('d1')
    expect(presentation.locationsForSelectedDelivery.value.map((item) => item.id)).toEqual(['l2'])
  })
})
