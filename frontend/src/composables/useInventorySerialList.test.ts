import { describe, expect, it, vi } from 'vitest'

import type { InventorySerial } from '@/api/inventory'
import { useInventorySerialList } from './useInventorySerialList'

const t = (key: string) => key

const serial = (overrides: Partial<InventorySerial> = {}): InventorySerial => ({
  id: '1',
  productId: 'p1',
  serialNo: 'SN1',
  status: 'IN_STOCK',
  warehouseId: 'w1',
  locationId: 'l1',
  ...overrides
})

const createList = (overrides: Partial<Parameters<typeof useInventorySerialList>[1]> = {}) =>
  useInventorySerialList(t, {
    getInventorySerials: vi.fn(async () => ({ records: [serial()], total: 1 })),
    issueInventorySerial: vi.fn(async () => ({})),
    scrapInventorySerial: vi.fn(async () => ({})),
    getWarehouses: vi.fn(async () => ({
      records: [{ id: 'w1', warehouseName: 'Main' } as any],
      total: 1
    })),
    getLocations: vi.fn(async () => ({
      records: [
        { id: 'l1', warehouseId: 'w1', locationCode: 'A1', locationName: 'Bin' } as any
      ],
      total: 1
    })),
    locationsForWarehouse: (warehouseId, all = []) =>
      warehouseId
        ? all.filter((location) => String(location.warehouseId) === String(warehouseId))
        : all,
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('inventory serial list', () => {
  it('loads options and data, clears location when warehouse changes', async () => {
    const list = createList()
    expect(await list.loadOptions()).toBe(true)
    expect(list.warehouses.value).toHaveLength(1)
    expect(await list.loadData()).toBe(true)
    expect(list.rows.value).toHaveLength(1)

    list.query.warehouseId = 'w1'
    list.query.locationId = 'l1'
    list.handleQueryWarehouseChange()
    expect(list.query.locationId).toBe('')

    list.query.keyword = 'SN'
    await list.handleSearch()
    expect(list.query.pageNo).toBe(1)
  })

  it('issues and scraps in-stock serials', async () => {
    const issueInventorySerial = vi.fn(async () => ({}))
    const scrapInventorySerial = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({ issueInventorySerial, scrapInventorySerial, onSuccess })

    expect(await list.issue(serial())).toBe(true)
    expect(issueInventorySerial).toHaveBeenCalledWith('1')
    expect(onSuccess).toHaveBeenCalledWith('inventorySerial.message.issued')

    expect(await list.scrap(serial())).toBe(true)
    expect(scrapInventorySerial).toHaveBeenCalledWith('1')
    expect(onSuccess).toHaveBeenCalledWith('inventorySerial.message.scrapped')
  })

  it('surfaces options load failure', async () => {
    const onError = vi.fn()
    const list = createList({
      getWarehouses: vi.fn(async () => {
        throw new Error('boom')
      }),
      onError
    })
    expect(await list.loadOptions()).toBe(false)
    expect(onError).toHaveBeenCalledWith('inventorySerial.message.optionsLoadFailed')
  })
})
