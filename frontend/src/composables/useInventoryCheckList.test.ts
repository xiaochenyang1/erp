import { describe, expect, it, vi } from 'vitest'

import type { InventoryCheck } from '@/api/inventory'
import { useInventoryCheckList } from './useInventoryCheckList'

const t = (key: string) => key

const createList = (overrides: Partial<Parameters<typeof useInventoryCheckList>[1]> = {}) =>
  useInventoryCheckList(t, {
    getChecks: vi.fn(async () => ({
      records: [{ id: 'c1', checkNo: 'CK001', status: 'COUNTED' }],
      total: 1
    } as any)),
    getCheck: vi.fn(async () => ({ id: 'c1', checkNo: 'CK001' } as InventoryCheck)),
    completeCheck: vi.fn(async () => ({})),
    cancelCheck: vi.fn(async () => ({})),
    getWarehouses: vi.fn(async () => ({ records: [{ id: 1 }], total: 1 } as any)),
    getProducts: vi.fn(async () => ({ records: [{ id: 'p1' }], total: 1 } as any)),
    getLocations: vi.fn(async () => ({ records: [{ id: 'l1' }], total: 1 } as any)),
    printCheck: vi.fn(),
    confirm: vi.fn(async () => true),
    onSuccess: vi.fn(),
    onError: vi.fn(),
    ...overrides
  })

describe('inventory check list', () => {
  it('applies the date range and resets paging on query', async () => {
    const getChecks = vi.fn(async () => ({ records: [], total: 7 } as any))
    const list = createList({ getChecks })

    list.queryParams.pageNo = 4
    list.queryParams.checkNo = 'CK009'
    list.dateRange.value = ['2026-07-01', '2026-07-26']
    await list.handleQuery()

    expect(getChecks).toHaveBeenCalledWith(expect.objectContaining({
      checkNo: 'CK009',
      startDate: '2026-07-01',
      endDate: '2026-07-26',
      pageNo: 1
    }))
    expect(list.total.value).toBe(7)

    list.dateRange.value = null
    await list.handleQuery()
    expect(getChecks).toHaveBeenLastCalledWith(expect.objectContaining({
      startDate: '',
      endDate: ''
    }))
  })

  it('keeps the current page when paginating and clears filters on reset', async () => {
    const getChecks = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getChecks })

    list.queryParams.pageNo = 3
    await list.handlePageChange()
    expect(getChecks).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 3 }))

    list.queryParams.warehouseId = 5
    list.queryParams.status = 'ADJUSTED'
    list.dateRange.value = ['2026-07-01', '2026-07-02']
    await list.handleReset()

    expect(list.queryParams.checkNo).toBe('')
    expect(list.queryParams.warehouseId).toBeUndefined()
    expect(list.queryParams.status).toBe('')
    expect(list.dateRange.value).toBeNull()
    expect(list.queryParams.pageNo).toBe(1)
  })

  it('decorates rows and reports load failures', async () => {
    const onError = vi.fn()
    const decorateRows = vi.fn((rows: InventoryCheck[]) =>
      rows.map((row) => ({ ...row, warehouseName: '主仓' })))
    const list = createList({ decorateRows })

    await list.loadData()
    expect(decorateRows).toHaveBeenCalled()
    expect(list.tableData.value[0].warehouseName).toBe('主仓')
    expect(list.loading.value).toBe(false)

    const failing = createList({
      getChecks: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await failing.loadData()
    expect(onError).toHaveBeenCalledWith('inventoryChecks.message.loadFailed')
    expect(failing.loading.value).toBe(false)
  })

  it('prints the fetched detail rather than the list row', async () => {
    const printCheck = vi.fn()
    const getCheck = vi.fn(async () => ({
      id: 'c5',
      checkNo: 'CK005',
      items: [{ productId: 'p1', bookQuantity: 2 }]
    } as InventoryCheck))
    const list = createList({ printCheck, getCheck })

    await list.handlePrint({ id: 'c5' } as InventoryCheck)
    expect(getCheck).toHaveBeenCalledWith('c5')
    expect(printCheck).toHaveBeenCalledWith(expect.objectContaining({ checkNo: 'CK005' }))

    const onError = vi.fn()
    const failing = createList({
      getCheck: vi.fn(async () => { throw new Error('boom') }),
      printCheck,
      onError
    })
    await failing.handlePrint({ id: 'c5' } as InventoryCheck)
    expect(onError).toHaveBeenCalledWith('inventoryChecks.message.printLoadFailed')
  })

  it('adjusts and cancels after confirmation, then reloads', async () => {
    const completeCheck = vi.fn(async () => ({}))
    const cancelCheck = vi.fn(async () => ({}))
    const getChecks = vi.fn(async () => ({ records: [], total: 0 } as any))
    const onSuccess = vi.fn()
    const list = createList({ completeCheck, cancelCheck, getChecks, onSuccess })

    await list.handleComplete({ id: 'c1' } as InventoryCheck)
    await list.handleCancel({ id: 'c2' } as InventoryCheck)

    expect(completeCheck).toHaveBeenCalledWith('c1')
    expect(cancelCheck).toHaveBeenCalledWith('c2')
    expect(getChecks).toHaveBeenCalledTimes(2)
    expect(onSuccess).toHaveBeenCalledTimes(2)
  })

  it('stays silent when the user dismisses a confirmation', async () => {
    const onError = vi.fn()
    const completeCheck = vi.fn(async () => ({}))
    const list = createList({
      confirm: vi.fn(async () => { throw 'cancel' }),
      completeCheck,
      onError
    })

    await list.handleComplete({ id: 'c1' } as InventoryCheck)
    expect(completeCheck).not.toHaveBeenCalled()
    expect(onError).not.toHaveBeenCalled()

    const failing = createList({
      completeCheck: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await failing.handleComplete({ id: 'c1' } as InventoryCheck)
    expect(onError).toHaveBeenCalledWith('inventoryChecks.message.failed')
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
    expect(onError).toHaveBeenCalledWith('inventoryChecks.message.warehousesLoadFailed')
    expect(onError).toHaveBeenCalledWith('inventoryChecks.message.productsLoadFailed')
    expect(partial.locations.value).toEqual([])
  })
})
