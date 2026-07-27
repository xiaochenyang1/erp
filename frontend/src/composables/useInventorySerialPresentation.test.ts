import { ref } from 'vue'
import { describe, expect, it } from 'vitest'

import { useInventorySerialPresentation } from './useInventorySerialPresentation'

const t = (key: string) => key

describe('inventory serial presentation', () => {
  it('labels warehouses, locations, products and statuses', () => {
    const warehouses = ref([
      { id: 'w1', warehouseName: 'Main' } as any,
      { id: 'w2', name: 'Spare' } as any
    ])
    const locations = ref([
      { id: 'l1', warehouseId: 'w1', locationCode: 'A1', locationName: 'Bin A' } as any,
      { id: 'l2', warehouseId: 'w2', locationCode: 'B1', locationName: 'Bin B' } as any
    ])
    const presentation = useInventorySerialPresentation(t, { warehouses, locations })

    expect(presentation.warehouseLabel('w1')).toBe('Main')
    expect(presentation.warehouseLabel('missing')).toBe('missing')
    expect(presentation.locationLabel('l1')).toBe('A1 Bin A')
    expect(presentation.productLabel({
      id: 'p1',
      productCode: 'SKU',
      productName: 'Item'
    } as any)).toBe('SKU Item')
    expect(presentation.statusLabel('IN_STOCK')).toBe('inventorySerial.statusValue.inStock')
    expect(presentation.statusType('SCRAPPED')).toBe('warning')
    expect(presentation.locationsForWarehouse('w1')).toHaveLength(1)
    expect(presentation.locationsForWarehouse('')).toHaveLength(2)
  })
})
