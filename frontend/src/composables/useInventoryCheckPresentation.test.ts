import { describe, expect, it } from 'vitest'
import { ref } from 'vue'

import type { InventoryCheck } from '@/api/inventory'
import type { Location, Product, Warehouse } from '@/api/masterdata'
import {
  useInventoryCheckFormPresentation,
  useInventoryCheckPresentation
} from './useInventoryCheckPresentation'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.id != null ? `${key}:${params.id}` : key

const createResources = () => ({
  warehouses: ref<Warehouse[]>([
    { id: '1', name: '主仓' } as unknown as Warehouse,
    { id: '2', warehouseName: 'Second WH' } as Warehouse,
    { id: '3' } as Warehouse
  ]),
  products: ref<Product[]>([
    { id: 'p1', code: 'P001', name: '螺丝' } as Product,
    { id: 'p2', productCode: 'P002', productName: 'Bolt' } as Product,
    { id: 'p3' } as Product
  ]),
  locations: ref<Location[]>([
    { id: 'l1', warehouseId: 1, locationCode: 'A-01', locationName: '货架A' } as Location,
    { id: 'l2', warehouseId: 2, locationCode: 'B-01', locationName: '货架B' } as Location
  ])
})

describe('inventory check presentation', () => {
  it('labels warehouses and products with fallbacks', () => {
    const resources = createResources()
    const presentation = useInventoryCheckPresentation(t, resources)

    expect(presentation.warehouseLabel(resources.warehouses.value[0])).toBe('主仓')
    expect(presentation.warehouseLabel(resources.warehouses.value[1])).toBe('Second WH')
    expect(presentation.warehouseLabel(resources.warehouses.value[2]))
      .toBe('inventoryChecks.warehouseFallback:3')

    expect(presentation.warehouseNameById(2)).toBe('Second WH')
    expect(presentation.warehouseNameById('99')).toBe('')
    expect(presentation.warehouseNameById(undefined)).toBe('')

    expect(presentation.productLabel(resources.products.value[0])).toBe('P001 - 螺丝')
    expect(presentation.productLabel(resources.products.value[1])).toBe('P002 - Bolt')
    expect(presentation.productLabel(resources.products.value[2]))
      .toBe('inventoryChecks.productFallback:p3')
    expect(presentation.findProduct('p2')?.productCode).toBe('P002')
    expect(presentation.findProduct(undefined as any)).toBeUndefined()
  })

  it('maps status labels and tag types, unknown status falls back to raw value', () => {
    const presentation = useInventoryCheckPresentation(t, createResources())

    expect(presentation.statusLabel('COUNTED')).toBe('inventoryChecks.status.counted')
    expect(presentation.statusLabel('ADJUSTED')).toBe('inventoryChecks.status.adjusted')
    expect(presentation.statusLabel('CANCELLED')).toBe('inventoryChecks.status.cancelled')
    expect(presentation.statusLabel('WEIRD')).toBe('WEIRD')
    expect(presentation.statusLabel(undefined)).toBe('')

    expect(presentation.statusTagType('COUNTED')).toBe('warning')
    expect(presentation.statusTagType('ADJUSTED')).toBe('success')
    expect(presentation.statusTagType('CANCELLED')).toBe('danger')
    expect(presentation.statusTagType('WEIRD')).toBe('info')
  })

  it('filters locations by warehouse and formats difference styling', () => {
    const resources = createResources()
    const presentation = useInventoryCheckPresentation(t, resources)

    expect(presentation.locationsForWarehouse(1).map((item) => item.id)).toEqual(['l1'])
    expect(presentation.locationsForWarehouse('2').map((item) => item.id)).toEqual(['l2'])
    expect(presentation.locationsForWarehouse(0)).toHaveLength(2)
    expect(presentation.locationsForWarehouse(undefined)).toHaveLength(2)
    expect(presentation.locationLabel(resources.locations.value[0])).toBe('A-01 货架A')

    expect(presentation.differenceClass(-1)).toEqual({ 'text-danger': true, 'text-success': false })
    expect(presentation.differenceClass(2)).toEqual({ 'text-danger': false, 'text-success': true })
    expect(presentation.differenceClass(0)).toEqual({ 'text-danger': false, 'text-success': false })
    expect(presentation.differenceClass(undefined)).toEqual({
      'text-danger': false,
      'text-success': false
    })
  })

  it('backfills warehouse names only when the response omits them', () => {
    const presentation = useInventoryCheckPresentation(t, createResources())

    const rows = presentation.withWarehouseName([
      { id: 'c1', warehouseId: 1 } as InventoryCheck,
      { id: 'c2', warehouseId: 1, warehouseName: '接口返回仓' } as InventoryCheck
    ])

    expect(rows[0].warehouseName).toBe('主仓')
    expect(rows[1].warehouseName).toBe('接口返回仓')
  })

  it('tracks the selected warehouse for form location options', () => {
    const resources = createResources()
    const selected = ref<string | number | undefined>(undefined)
    const presentation = useInventoryCheckFormPresentation(t, resources, selected)

    expect(presentation.locationsForSelectedWarehouse.value).toHaveLength(2)

    selected.value = 2
    expect(presentation.locationsForSelectedWarehouse.value.map((item) => item.id)).toEqual(['l2'])

    resources.locations.value = [
      ...resources.locations.value,
      { id: 'l3', warehouseId: 2, locationCode: 'B-02', locationName: '货架C' } as Location
    ]
    expect(presentation.locationsForSelectedWarehouse.value.map((item) => item.id))
      .toEqual(['l2', 'l3'])
  })
})
