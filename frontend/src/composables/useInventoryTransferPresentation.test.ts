import { describe, expect, it } from 'vitest'
import { ref } from 'vue'

import type { InventoryTransfer } from '@/api/inventory'
import type { Location, Product, Warehouse } from '@/api/masterdata'
import {
  useInventoryTransferFormPresentation,
  useInventoryTransferPresentation
} from './useInventoryTransferPresentation'

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

describe('inventory transfer presentation', () => {
  it('labels warehouses and products with fallbacks', () => {
    const resources = createResources()
    const presentation = useInventoryTransferPresentation(t, resources)

    expect(presentation.warehouseLabel(resources.warehouses.value[0])).toBe('主仓')
    expect(presentation.warehouseLabel(resources.warehouses.value[1])).toBe('Second WH')
    expect(presentation.warehouseLabel(resources.warehouses.value[2]))
      .toBe('inventoryTransfers.warehouseFallback:3')

    expect(presentation.warehouseNameById('2')).toBe('Second WH')
    expect(presentation.warehouseNameById(99)).toBe('')
    expect(presentation.warehouseNameById(undefined)).toBe('')

    expect(presentation.productLabel(resources.products.value[0])).toBe('P001 - 螺丝')
    expect(presentation.productLabel(resources.products.value[1])).toBe('P002 - Bolt')
    expect(presentation.productLabel(resources.products.value[2]))
      .toBe('inventoryTransfers.productFallback:p3')
    expect(presentation.findProduct('p2')?.productCode).toBe('P002')
    expect(presentation.findProduct(undefined)).toBeUndefined()
  })

  it('maps status labels and tag types, unknown status falls back to raw value', () => {
    const presentation = useInventoryTransferPresentation(t, createResources())

    expect(presentation.statusLabel('DRAFT')).toBe('inventoryTransfers.status.draft')
    expect(presentation.statusLabel('COMPLETED')).toBe('inventoryTransfers.status.completed')
    expect(presentation.statusLabel('CANCELLED')).toBe('inventoryTransfers.status.cancelled')
    expect(presentation.statusLabel('POSTED')).toBe('POSTED')
    expect(presentation.statusLabel(undefined)).toBe('')

    expect(presentation.statusTagType('DRAFT')).toBe('info')
    expect(presentation.statusTagType('COMPLETED')).toBe('success')
    expect(presentation.statusTagType('CANCELLED')).toBe('danger')
    expect(presentation.statusTagType('POSTED')).toBe('info')
  })

  it('filters locations by warehouse and labels them', () => {
    const resources = createResources()
    const presentation = useInventoryTransferPresentation(t, resources)

    expect(presentation.locationsForWarehouse(1).map((item) => item.id)).toEqual(['l1'])
    expect(presentation.locationsForWarehouse('2').map((item) => item.id)).toEqual(['l2'])
    expect(presentation.locationsForWarehouse(0)).toHaveLength(2)
    expect(presentation.locationsForWarehouse(undefined)).toHaveLength(2)
    expect(presentation.locationLabel(resources.locations.value[0])).toBe('A-01 货架A')
  })

  it('backfills both warehouse names only when the response omits them', () => {
    const presentation = useInventoryTransferPresentation(t, createResources())

    const rows = presentation.withWarehouseNames([
      { id: 't1', fromWarehouseId: 1, toWarehouseId: 2 } as InventoryTransfer,
      {
        id: 't2',
        fromWarehouseId: 1,
        toWarehouseId: 2,
        fromWarehouseName: '接口调出仓',
        toWarehouseName: '接口调入仓'
      } as InventoryTransfer
    ])

    expect(rows[0].fromWarehouseName).toBe('主仓')
    expect(rows[0].toWarehouseName).toBe('Second WH')
    expect(rows[1].fromWarehouseName).toBe('接口调出仓')
    expect(rows[1].toWarehouseName).toBe('接口调入仓')
  })

  it('tracks source and target warehouses separately for line location options', () => {
    const resources = createResources()
    const fromWarehouseId = ref<string | number | undefined>(undefined)
    const toWarehouseId = ref<string | number | undefined>(undefined)
    const presentation = useInventoryTransferFormPresentation(t, resources, {
      fromWarehouseId,
      toWarehouseId
    })

    expect(presentation.locationsForFromWarehouse.value).toHaveLength(2)
    expect(presentation.locationsForToWarehouse.value).toHaveLength(2)

    fromWarehouseId.value = 1
    toWarehouseId.value = 2
    expect(presentation.locationsForFromWarehouse.value.map((item) => item.id)).toEqual(['l1'])
    expect(presentation.locationsForToWarehouse.value.map((item) => item.id)).toEqual(['l2'])

    resources.locations.value = [
      ...resources.locations.value,
      { id: 'l3', warehouseId: 2, locationCode: 'B-02', locationName: '货架C' } as Location
    ]
    expect(presentation.locationsForToWarehouse.value.map((item) => item.id)).toEqual(['l2', 'l3'])
    expect(presentation.locationsForFromWarehouse.value.map((item) => item.id)).toEqual(['l1'])
  })
})
