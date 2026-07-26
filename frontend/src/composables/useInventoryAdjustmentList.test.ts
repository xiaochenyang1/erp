import { describe, expect, it, vi } from 'vitest'

import type { InventoryAdjustment } from '@/api/inventory'
import { useInventoryAdjustmentList } from './useInventoryAdjustmentList'

const t = (key: string) => key

const createList = (overrides: Partial<Parameters<typeof useInventoryAdjustmentList>[1]> = {}) =>
  useInventoryAdjustmentList(t, {
    getAdjustments: vi.fn(async () => ({
      records: [{ id: 'a1', adjustmentNo: 'ADJ001', status: 'DRAFT' }],
      total: 1
    } as any)),
    getAdjustment: vi.fn(async () => ({ id: 'a1', adjustmentNo: 'ADJ001' } as InventoryAdjustment)),
    postAdjustment: vi.fn(async () => ({})),
    cancelAdjustment: vi.fn(async () => ({})),
    getWarehouses: vi.fn(async () => ({ records: [{ id: 1 }], total: 1 } as any)),
    getProducts: vi.fn(async () => ({ records: [{ id: 'p1' }], total: 1 } as any)),
    getLocations: vi.fn(async () => ({ records: [{ id: 'l1' }], total: 1 } as any)),
    printAdjustment: vi.fn(),
    confirm: vi.fn(async () => true),
    onSuccess: vi.fn(),
    onError: vi.fn(),
    ...overrides
  })

describe('inventory adjustment list', () => {
  it('applies the date range and resets paging on query', async () => {
    const getAdjustments = vi.fn(async () => ({ records: [], total: 9 } as any))
    const list = createList({ getAdjustments })

    list.queryParams.pageNo = 5
    list.queryParams.adjustmentNo = 'ADJ009'
    list.queryParams.type = 'LOSS'
    list.dateRange.value = ['2026-07-01', '2026-07-26']
    await list.handleQuery()

    expect(getAdjustments).toHaveBeenCalledWith(expect.objectContaining({
      adjustmentNo: 'ADJ009',
      type: 'LOSS',
      startDate: '2026-07-01',
      endDate: '2026-07-26',
      pageNo: 1
    }))
    expect(list.total.value).toBe(9)

    list.dateRange.value = null
    await list.handleQuery()
    expect(getAdjustments).toHaveBeenLastCalledWith(expect.objectContaining({
      startDate: '',
      endDate: ''
    }))
  })

  it('keeps the current page when paginating and clears filters on reset', async () => {
    const getAdjustments = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getAdjustments })

    list.queryParams.pageNo = 3
    await list.handlePageChange()
    expect(getAdjustments).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 3 }))

    list.queryParams.warehouseId = 4
    list.queryParams.type = 'GAIN'
    list.queryParams.status = 'POSTED'
    list.dateRange.value = ['2026-07-01', '2026-07-02']
    await list.handleReset()

    expect(list.queryParams.adjustmentNo).toBe('')
    expect(list.queryParams.warehouseId).toBeUndefined()
    expect(list.queryParams.type).toBe('')
    expect(list.queryParams.status).toBe('')
    expect(list.dateRange.value).toBeNull()
    expect(list.queryParams.pageNo).toBe(1)
  })

  it('decorates rows and reports load failures', async () => {
    const decorateRows = vi.fn((rows: InventoryAdjustment[]) =>
      rows.map((row) => ({ ...row, warehouseName: '主仓' })))
    const list = createList({ decorateRows })

    await list.loadData()
    expect(decorateRows).toHaveBeenCalled()
    expect(list.tableData.value[0].warehouseName).toBe('主仓')
    expect(list.loading.value).toBe(false)

    const onError = vi.fn()
    const failing = createList({
      getAdjustments: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await failing.loadData()
    expect(onError).toHaveBeenCalledWith('inventoryAdjustments.message.loadFailed')
    expect(failing.loading.value).toBe(false)
  })

  it('prints the fetched detail rather than the list row', async () => {
    const printAdjustment = vi.fn()
    const getAdjustment = vi.fn(async () => ({
      id: 'a5',
      adjustmentNo: 'ADJ005',
      items: [{ productId: 'p1', quantity: 2 }]
    } as InventoryAdjustment))
    const list = createList({ printAdjustment, getAdjustment })

    await list.handlePrint({ id: 'a5' } as InventoryAdjustment)
    expect(getAdjustment).toHaveBeenCalledWith('a5')
    expect(printAdjustment).toHaveBeenCalledWith(expect.objectContaining({ adjustmentNo: 'ADJ005' }))

    const onError = vi.fn()
    const failing = createList({
      getAdjustment: vi.fn(async () => { throw new Error('boom') }),
      printAdjustment,
      onError
    })
    await failing.handlePrint({ id: 'a5' } as InventoryAdjustment)
    expect(onError).toHaveBeenCalledWith('inventoryAdjustments.message.printLoadFailed')
  })

  it('posts and cancels after confirmation, then reloads', async () => {
    const postAdjustment = vi.fn(async () => ({}))
    const cancelAdjustment = vi.fn(async () => ({}))
    const getAdjustments = vi.fn(async () => ({ records: [], total: 0 } as any))
    const onSuccess = vi.fn()
    const list = createList({ postAdjustment, cancelAdjustment, getAdjustments, onSuccess })

    await list.handleComplete({ id: 'a1' } as InventoryAdjustment)
    await list.handleCancel({ id: 'a2' } as InventoryAdjustment)

    expect(postAdjustment).toHaveBeenCalledWith('a1')
    expect(cancelAdjustment).toHaveBeenCalledWith('a2')
    expect(getAdjustments).toHaveBeenCalledTimes(2)
    expect(onSuccess).toHaveBeenCalledTimes(2)
  })

  it('stays silent when the user dismisses a confirmation', async () => {
    const onError = vi.fn()
    const postAdjustment = vi.fn(async () => ({}))
    const list = createList({
      confirm: vi.fn(async () => { throw 'cancel' }),
      postAdjustment,
      onError
    })

    await list.handleComplete({ id: 'a1' } as InventoryAdjustment)
    expect(postAdjustment).not.toHaveBeenCalled()
    expect(onError).not.toHaveBeenCalled()

    const failing = createList({
      postAdjustment: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await failing.handleComplete({ id: 'a1' } as InventoryAdjustment)
    expect(onError).toHaveBeenCalledWith('inventoryAdjustments.message.failed')
  })

  it('loads option lists with active-only filters and tolerates location failures', async () => {
    const getWarehouses = vi.fn(async () => ({ records: [{ id: 1 }], total: 1 } as any))
    const getProducts = vi.fn(async () => ({ records: [{ id: 'p1' }], total: 1 } as any))
    const getLocations = vi.fn(async () => ({ records: [{ id: 'l1' }], total: 1 } as any))
    const list = createList({ getWarehouses, getProducts, getLocations })

    await list.loadOptions()

    expect(getWarehouses).toHaveBeenCalledWith({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    expect(getProducts).toHaveBeenCalledWith({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    expect(getLocations).toHaveBeenCalledWith({ pageNo: 1, pageSize: 500, status: 'ACTIVE' })
    expect(list.warehouses.value).toHaveLength(1)
    expect(list.products.value).toHaveLength(1)
    expect(list.locations.value).toHaveLength(1)

    const onError = vi.fn()
    const partial = createList({
      getWarehouses: vi.fn(async () => { throw new Error('boom') }),
      getProducts: vi.fn(async () => { throw new Error('boom') }),
      getLocations: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await partial.loadOptions()
    expect(onError).toHaveBeenCalledWith('inventoryAdjustments.message.warehousesLoadFailed')
    expect(onError).toHaveBeenCalledWith('inventoryAdjustments.message.productsLoadFailed')
    expect(partial.locations.value).toEqual([])
  })
})
