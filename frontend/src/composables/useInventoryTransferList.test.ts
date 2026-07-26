import { describe, expect, it, vi } from 'vitest'

import type { InventoryTransfer } from '@/api/inventory'
import { useInventoryTransferList } from './useInventoryTransferList'

const t = (key: string) => key

const createList = (overrides: Partial<Parameters<typeof useInventoryTransferList>[1]> = {}) =>
  useInventoryTransferList(t, {
    getTransfers: vi.fn(async () => ({
      records: [{ id: 't1', transferNo: 'TR001', status: 'DRAFT' }],
      total: 1
    } as any)),
    getTransfer: vi.fn(async () => ({ id: 't1', transferNo: 'TR001' } as InventoryTransfer)),
    postTransfer: vi.fn(async () => ({})),
    cancelTransfer: vi.fn(async () => ({})),
    getWarehouses: vi.fn(async () => ({ records: [{ id: 1 }], total: 1 } as any)),
    getProducts: vi.fn(async () => ({ records: [{ id: 'p1' }], total: 1 } as any)),
    getLocations: vi.fn(async () => ({ records: [{ id: 'l1' }], total: 1 } as any)),
    printTransfer: vi.fn(),
    confirm: vi.fn(async () => true),
    onSuccess: vi.fn(),
    onError: vi.fn(),
    ...overrides
  })

describe('inventory transfer list', () => {
  it('applies the date range and resets paging on query', async () => {
    const getTransfers = vi.fn(async () => ({ records: [], total: 9 } as any))
    const list = createList({ getTransfers })

    list.queryParams.pageNo = 5
    list.queryParams.transferNo = 'TR009'
    list.dateRange.value = ['2026-07-01', '2026-07-26']
    await list.handleQuery()

    expect(getTransfers).toHaveBeenCalledWith(expect.objectContaining({
      transferNo: 'TR009',
      startDate: '2026-07-01',
      endDate: '2026-07-26',
      pageNo: 1
    }))
    expect(list.total.value).toBe(9)

    list.dateRange.value = null
    await list.handleQuery()
    expect(getTransfers).toHaveBeenLastCalledWith(expect.objectContaining({
      startDate: '',
      endDate: ''
    }))
  })

  it('keeps the current page when paginating and clears both warehouse filters on reset', async () => {
    const getTransfers = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getTransfers })

    list.queryParams.pageNo = 3
    await list.handlePageChange()
    expect(getTransfers).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 3 }))

    list.queryParams.fromWarehouseId = 1
    list.queryParams.toWarehouseId = 2
    list.queryParams.status = 'COMPLETED'
    list.dateRange.value = ['2026-07-01', '2026-07-02']
    await list.handleReset()

    expect(list.queryParams.transferNo).toBe('')
    expect(list.queryParams.fromWarehouseId).toBeUndefined()
    expect(list.queryParams.toWarehouseId).toBeUndefined()
    expect(list.queryParams.status).toBe('')
    expect(list.dateRange.value).toBeNull()
    expect(list.queryParams.pageNo).toBe(1)
  })

  it('decorates rows and reports load failures', async () => {
    const decorateRows = vi.fn((rows: InventoryTransfer[]) =>
      rows.map((row) => ({ ...row, fromWarehouseName: '主仓', toWarehouseName: '二仓' })))
    const list = createList({ decorateRows })

    await list.loadData()
    expect(decorateRows).toHaveBeenCalled()
    expect(list.tableData.value[0].fromWarehouseName).toBe('主仓')
    expect(list.tableData.value[0].toWarehouseName).toBe('二仓')
    expect(list.loading.value).toBe(false)

    const onError = vi.fn()
    const failing = createList({
      getTransfers: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await failing.loadData()
    expect(onError).toHaveBeenCalledWith('inventoryTransfers.message.loadFailed')
    expect(failing.loading.value).toBe(false)
  })

  it('prints the fetched detail rather than the list row', async () => {
    const printTransfer = vi.fn()
    const getTransfer = vi.fn(async () => ({
      id: 't5',
      transferNo: 'TR005',
      items: [{ productId: 'p1', quantity: 2 }]
    } as InventoryTransfer))
    const list = createList({ printTransfer, getTransfer })

    await list.handlePrint({ id: 't5' } as InventoryTransfer)
    expect(getTransfer).toHaveBeenCalledWith('t5')
    expect(printTransfer).toHaveBeenCalledWith(expect.objectContaining({ transferNo: 'TR005' }))

    const onError = vi.fn()
    const failing = createList({
      getTransfer: vi.fn(async () => { throw new Error('boom') }),
      printTransfer,
      onError
    })
    await failing.handlePrint({ id: 't5' } as InventoryTransfer)
    expect(onError).toHaveBeenCalledWith('inventoryTransfers.message.printLoadFailed')
  })

  it('posts and cancels after confirmation, then reloads', async () => {
    const postTransfer = vi.fn(async () => ({}))
    const cancelTransfer = vi.fn(async () => ({}))
    const getTransfers = vi.fn(async () => ({ records: [], total: 0 } as any))
    const onSuccess = vi.fn()
    const list = createList({ postTransfer, cancelTransfer, getTransfers, onSuccess })

    await list.handlePost({ id: 't1' } as InventoryTransfer)
    await list.handleCancel({ id: 't2' } as InventoryTransfer)

    expect(postTransfer).toHaveBeenCalledWith('t1')
    expect(cancelTransfer).toHaveBeenCalledWith('t2')
    expect(getTransfers).toHaveBeenCalledTimes(2)
    expect(onSuccess).toHaveBeenCalledTimes(2)
  })

  it('stays silent when the user dismisses a confirmation', async () => {
    const onError = vi.fn()
    const postTransfer = vi.fn(async () => ({}))
    const list = createList({
      confirm: vi.fn(async () => { throw 'cancel' }),
      postTransfer,
      onError
    })

    await list.handlePost({ id: 't1' } as InventoryTransfer)
    expect(postTransfer).not.toHaveBeenCalled()
    expect(onError).not.toHaveBeenCalled()

    const failing = createList({
      postTransfer: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await failing.handlePost({ id: 't1' } as InventoryTransfer)
    expect(onError).toHaveBeenCalledWith('inventoryTransfers.message.failed')
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
    expect(onError).toHaveBeenCalledWith('inventoryTransfers.message.warehousesLoadFailed')
    expect(onError).toHaveBeenCalledWith('inventoryTransfers.message.productsLoadFailed')
    expect(partial.locations.value).toEqual([])
  })
})
