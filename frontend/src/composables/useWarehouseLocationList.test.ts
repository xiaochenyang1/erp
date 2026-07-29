import { describe, expect, it, vi } from 'vitest'

import type { Location, Warehouse } from '@/api/masterdata'
import type { PageResponse } from '@/types/common'
import { useWarehouseLocationList } from './useWarehouseLocationList'

const t = (key: string) => key

const warehouse = (overrides: Partial<Warehouse> = {}): Warehouse => ({
  id: 'w1',
  warehouseCode: 'W001',
  warehouseName: 'Main',
  status: 'ACTIVE',
  ...overrides
})

const location = (overrides: Partial<Location> = {}): Location => ({
  id: 'l1',
  warehouseId: 'w1',
  warehouseName: 'Main',
  locationCode: 'A01',
  locationName: 'Shelf A',
  isDefault: false,
  status: 'ACTIVE',
  ...overrides
})

const page = <T>(records: T[], total = records.length): PageResponse<T> => ({
  records,
  total,
  pageNo: 1,
  pageSize: 50
})

const createList = (
  overrides: Partial<Parameters<typeof useWarehouseLocationList>[1]> = {}
) => useWarehouseLocationList(t, {
  getWarehouses: vi.fn(async () => page([warehouse()])),
  getLocations: vi.fn(async () => page([location()])),
  enableLocation: vi.fn(async () => ({})),
  disableLocation: vi.fn(async () => ({})),
  onError: vi.fn(),
  onSuccess: vi.fn(),
  ...overrides
})

describe('warehouse location list', () => {
  it('loads only ACTIVE warehouses and reports option failures', async () => {
    const getWarehouses = vi.fn()
      .mockResolvedValueOnce(page([warehouse()]))
      .mockRejectedValueOnce(new Error('boom'))
    const onError = vi.fn()
    const list = createList({ getWarehouses, onError })

    expect(await list.loadOptions()).toBe(true)
    expect(getWarehouses).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 200,
      status: 'ACTIVE'
    })
    expect(list.warehouses.value).toEqual([warehouse()])

    expect(await list.loadOptions()).toBe(false)
    expect(list.warehouses.value).toEqual([])
    expect(onError).toHaveBeenCalledWith('warehouseLocation.message.optionsLoadFailed')
  })

  it('loads total and resets the page when querying', async () => {
    const getLocations = vi.fn(async () => page([location()], 73))
    const list = createList({ getLocations })
    list.query.pageNo = 4
    list.query.warehouseId = 'w1'
    list.query.status = 'ACTIVE'
    list.query.keyword = 'A01'

    expect(await list.handleQuery()).toBe(true)

    expect(list.query.pageNo).toBe(1)
    expect(list.rows.value).toEqual([location()])
    expect(list.total.value).toBe(73)
    expect(getLocations).toHaveBeenLastCalledWith({
      pageNo: 1,
      pageSize: 50,
      warehouseId: 'w1',
      status: 'ACTIVE',
      keyword: 'A01'
    })
  })

  it('changes pages and resets filters and paging independently', async () => {
    const getLocations = vi.fn(async () => page([], 0))
    const list = createList({ getLocations })

    await list.handlePageChange(3)
    expect(list.query.pageNo).toBe(3)
    expect(getLocations).toHaveBeenLastCalledWith(expect.objectContaining({
      pageNo: 3,
      pageSize: 50
    }))

    await list.handleSizeChange(100)
    expect(list.query.pageNo).toBe(1)
    expect(list.query.pageSize).toBe(100)
    expect(getLocations).toHaveBeenLastCalledWith(expect.objectContaining({
      pageNo: 1,
      pageSize: 100
    }))

    list.query.warehouseId = 'w1'
    list.query.status = 'INACTIVE'
    list.query.keyword = 'Shelf'
    list.query.pageNo = 2
    await list.handleReset()
    expect(list.query).toEqual(expect.objectContaining({
      warehouseId: '',
      status: '',
      keyword: '',
      pageNo: 1,
      pageSize: 100
    }))
    expect(getLocations).toHaveBeenLastCalledWith({
      pageNo: 1,
      pageSize: 100,
      warehouseId: undefined,
      status: undefined,
      keyword: undefined
    })
  })

  it('enables and disables locations, notifies success and refreshes', async () => {
    const getLocations = vi.fn(async () => page([location()]))
    const enableLocation = vi.fn(async () => ({}))
    const disableLocation = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({
      disableLocation,
      enableLocation,
      getLocations,
      onSuccess
    })

    expect(await list.toggle(location({ status: 'INACTIVE' }), true)).toBe(true)
    expect(enableLocation).toHaveBeenCalledWith('l1')
    expect(onSuccess).toHaveBeenCalledWith('warehouseLocation.message.enabled')

    expect(await list.toggle(location(), false)).toBe(true)
    expect(disableLocation).toHaveBeenCalledWith('l1')
    expect(onSuccess).toHaveBeenCalledWith('warehouseLocation.message.disabled')
    expect(getLocations).toHaveBeenCalledTimes(2)
  })

  it('reports toggle failures without refreshing', async () => {
    const getLocations = vi.fn(async () => page([location()]))
    const enableLocation = vi.fn(async () => {
      throw new Error('boom')
    })
    const onError = vi.fn()
    const list = createList({ enableLocation, getLocations, onError })

    expect(await list.toggle(location({ status: 'INACTIVE' }), true)).toBe(false)
    expect(onError).toHaveBeenCalledWith('warehouseLocation.message.toggleFailed')
    expect(getLocations).not.toHaveBeenCalled()
  })

  it('reports query failures and always clears loading', async () => {
    let rejectRequest: ((reason?: unknown) => void) | undefined
    const getLocations = vi.fn(() => new Promise<PageResponse<Location>>((_, reject) => {
      rejectRequest = reject
    }))
    const onError = vi.fn()
    const list = createList({ getLocations, onError })

    const pending = list.loadData()
    expect(list.loading.value).toBe(true)
    rejectRequest?.(new Error('boom'))

    expect(await pending).toBe(false)
    expect(list.loading.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('warehouseLocation.message.loadFailed')
  })
})
