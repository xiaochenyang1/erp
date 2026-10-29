import { describe, expect, it, vi } from 'vitest'

import type { ProductionOrder } from '@/api/production'
import type { PageResponse } from '@/types/common'
import { useProductionOrderList } from './useProductionOrderList'

const t = (key: string) => key

const page = <T>(records: T[], total = records.length): PageResponse<T> => ({
  records,
  total,
  pageNo: 1,
  pageSize: 20
})

const deferred = <T>() => {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, reject, resolve }
}

const listOptions = (overrides: Record<string, unknown> = {}) => ({
  getOrders: vi.fn(async () => page<ProductionOrder>([])),
  getOrder: vi.fn(),
  getProducts: vi.fn(),
  getWarehouses: vi.fn(),
  getBoms: vi.fn(),
  getLocations: vi.fn(),
  printOrder: vi.fn(),
  ...overrides
})

describe('production order list', () => {
  it('loads option catalogs for products, warehouses, and boms', async () => {
    const list = useProductionOrderList(t, listOptions({
      getProducts: vi.fn(async () => ({ records: [{ id: 'p1' }], total: 1 } as any)),
      getWarehouses: vi.fn(async () => ({ records: [{ id: 'w1' }], total: 1 } as any)),
      getBoms: vi.fn(async () => ({ records: [{ id: 'b1' }], total: 1 } as any))
    }) as any)

    await list.loadOptions()

    expect(list.productOptions.value).toEqual([{ id: 'p1' }])
    expect(list.warehouseOptions.value).toEqual([{ id: 'w1' }])
    expect(list.allBomOptions.value).toEqual([{ id: 'b1' }])
  })

  it('maps the order number to keyword and resets supported filters', async () => {
    const getOrders = vi.fn(async () => page([{
      id: 'mo-1',
      orderNo: 'MO001'
    } as ProductionOrder]))
    const list = useProductionOrderList(t, listOptions({ getOrders }) as any)
    list.queryForm.orderNo = 'MO001'
    list.queryForm.productId = 'p-1'
    list.queryForm.status = 'RELEASED'
    list.pagination.page = 3

    await list.handleQuery()

    expect(list.pagination.page).toBe(1)
    expect(getOrders).toHaveBeenLastCalledWith({
      keyword: 'MO001',
      productId: 'p-1',
      status: 'RELEASED',
      pageNo: 1,
      pageSize: 20
    })
    expect(list.tableData.value).toEqual([{ id: 'mo-1', orderNo: 'MO001' }])
    expect(list.pagination.total).toBe(1)

    await list.handleReset()

    expect(list.queryForm).toMatchObject({
      orderNo: '',
      productId: undefined,
      status: ''
    })
    expect(list.queryForm).not.toHaveProperty('priority')
    expect(getOrders).toHaveBeenLastCalledWith({
      keyword: undefined,
      productId: undefined,
      status: undefined,
      pageNo: 1,
      pageSize: 20
    })
  })

  it('handles page and page-size changes with one request each', async () => {
    const getOrders = vi.fn(async () => page<ProductionOrder>([]))
    const list = useProductionOrderList(t, listOptions({ getOrders }) as any)

    await list.handlePageChange(3)
    expect(list.pagination.page).toBe(3)
    expect(getOrders).toHaveBeenLastCalledWith(expect.objectContaining({
      pageNo: 3,
      pageSize: 20
    }))

    await list.handleSizeChange(50)
    expect(list.pagination).toMatchObject({ page: 1, size: 50 })
    expect(getOrders).toHaveBeenLastCalledWith(expect.objectContaining({
      pageNo: 1,
      pageSize: 50
    }))
    expect(getOrders).toHaveBeenCalledTimes(2)
  })

  it('keeps the latest list result when requests finish out of order', async () => {
    const first = deferred<PageResponse<ProductionOrder>>()
    const second = deferred<PageResponse<ProductionOrder>>()
    const latestOrder = { id: 'mo-2', orderNo: 'MO002' } as ProductionOrder
    const getOrders = vi.fn()
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise)
    const list = useProductionOrderList(t, listOptions({ getOrders }) as any)

    const firstLoad = list.loadData()
    const secondLoad = list.loadData()
    second.resolve(page([latestOrder]))
    await secondLoad
    first.resolve(page([{ id: 'mo-1', orderNo: 'MO001' } as ProductionOrder]))
    await firstLoad

    expect(list.tableData.value).toEqual([latestOrder])
    expect(list.pagination.total).toBe(1)
    expect(list.loading.value).toBe(false)
  })

  it('ignores a stale list failure without changing the latest loading or error state', async () => {
    const first = deferred<PageResponse<ProductionOrder>>()
    const second = deferred<PageResponse<ProductionOrder>>()
    const latestOrder = { id: 'mo-2', orderNo: 'MO002' } as ProductionOrder
    const onError = vi.fn()
    const getOrders = vi.fn()
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise)
    const list = useProductionOrderList(t, listOptions({ getOrders, onError }) as any)

    const firstLoad = list.loadData()
    const secondLoad = list.loadData()
    first.reject(new Error('stale failure'))
    await firstLoad

    expect(list.loading.value).toBe(true)
    expect(onError).not.toHaveBeenCalled()

    second.resolve(page([latestOrder]))
    await secondLoad

    expect(list.tableData.value).toEqual([latestOrder])
    expect(list.loading.value).toBe(false)
    expect(onError).not.toHaveBeenCalled()
  })

  it('loads finished and material locations by warehouse', async () => {
    const getLocations = vi.fn(async ({ warehouseId }) => ({
      records: [{ id: `loc-${warehouseId}` }],
      total: 1
    } as any))
    const list = useProductionOrderList(t, listOptions({ getLocations }) as any)

    await list.loadFinishedLocations('fw-1')
    await list.loadMaterialLocations('mw-1')

    expect(list.finishedLocations.value).toEqual([{ id: 'loc-fw-1' }])
    expect(list.materialLocations.value).toEqual([{ id: 'loc-mw-1' }])
  })

  it('keeps the latest detail and ignores a stale detail failure', async () => {
    const first = deferred<ProductionOrder>()
    const second = deferred<ProductionOrder>()
    const latestOrder = { id: 'mo-2', orderNo: 'MO002' } as ProductionOrder
    const onError = vi.fn()
    const getOrder = vi.fn()
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise)
    const list = useProductionOrderList(t, listOptions({ getOrder, onError }) as any)

    const firstLoad = list.handleView({ id: 'mo-1' } as ProductionOrder)
    const secondLoad = list.handleView(latestOrder)
    first.reject(new Error('stale detail failure'))
    await firstLoad

    expect(list.viewLoading.value).toBe(true)
    expect(list.viewDialogVisible.value).toBe(true)
    expect(onError).not.toHaveBeenCalled()

    second.resolve(latestOrder)
    await secondLoad

    expect(list.viewData.value).toEqual(latestOrder)
    expect(list.viewLoading.value).toBe(false)
    expect(list.viewDialogVisible.value).toBe(true)
    expect(onError).not.toHaveBeenCalled()
  })

  it('closes the detail dialog and reports the latest detail failure', async () => {
    const onError = vi.fn()
    const list = useProductionOrderList(t, listOptions({
      getOrder: vi.fn(async () => Promise.reject(new Error('latest failure'))),
      onError
    }) as any)

    await list.handleView({ id: 'mo-9' } as ProductionOrder)

    expect(list.viewDialogVisible.value).toBe(false)
    expect(list.viewLoading.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('productionOrder.message.detailLoadFailed')
  })

  it('prints the latest loaded order detail', async () => {
    const order = {
      id: 'mo-9',
      orderNo: 'MO009',
      planQuantity: 5
    } as ProductionOrder
    const getOrder = vi.fn(async () => order)
    const printOrder = vi.fn()
    const list = useProductionOrderList(t, listOptions({ getOrder, printOrder }) as any)

    await list.handlePrint({ id: 'mo-9' } as ProductionOrder)

    expect(printOrder).toHaveBeenCalledWith(order)
  })
})
