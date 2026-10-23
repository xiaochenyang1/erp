import { reactive } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type {
  InventoryLotBalance,
  InventoryLotTrace,
  InventoryStock,
  InventoryStockQuery
} from '@/api/inventory'
import {
  useInventoryStockLotBalanceList,
  type InventoryStockLotBalanceListDependencies
} from './useInventoryStockLotBalanceList'

const page = <T>(records: T[]) => ({
  records,
  total: records.length,
  pageNo: 1,
  pageSize: 10
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

const lot = {
  id: 'lot-1',
  warehouseId: 'W-2',
  productId: 'P-2',
  lotNo: 'LOT-1'
} as InventoryLotBalance
const trace = {
  id: 'trace-1',
  warehouseId: 'W-2',
  productId: 'P-2',
  lotNo: 'LOT-1'
} as InventoryLotTrace

const createDependencies = (): InventoryStockLotBalanceListDependencies => ({
  getLotBalance: vi.fn(async () => lot),
  getLotBalances: vi.fn(async () => page([lot])),
  getLotTrace: vi.fn(async () => page([trace]))
})

const createHarness = () => {
  const stockQuery = reactive<InventoryStockQuery>({
    pageNo: 3,
    pageSize: 20,
    warehouseId: 'W-1',
    productId: 'P-1',
    locationId: 'L-1'
  })
  const dependencies = createDependencies()
  const onDetailError = vi.fn()
  const onListError = vi.fn()
  const list = useInventoryStockLotBalanceList(stockQuery, {
    onDetailError,
    onListError
  }, dependencies)
  return { dependencies, list, onDetailError, onListError, stockQuery }
}

describe('inventory stock lot balance list', () => {
  it('opens balances with row scope, clears lot number, and preserves other filters', async () => {
    const { dependencies, list } = createHarness()
    Object.assign(list.lotBalanceQuery, {
      pageNo: 4,
      pageSize: 50,
      lotNo: 'OLD',
      expiringWithinDays: 60
    })

    await list.handleOpenLotBalances({
      warehouseId: 'W-2',
      productId: 'P-2',
      locationId: 'L-2'
    } as InventoryStock)

    expect(list.lotBalanceDialogVisible.value).toBe(true)
    expect(list.lotBalanceQuery).toMatchObject({
      warehouseId: 'W-2',
      productId: 'P-2',
      pageNo: 1,
      pageSize: 50,
      lotNo: undefined,
      expiringWithinDays: 60
    })
    expect(list.lotBalanceQuery).not.toHaveProperty('locationId')
    expect(dependencies.getLotBalances).toHaveBeenCalledTimes(1)
  })

  it('uses the current stock scope when opening balances without a row', async () => {
    const { dependencies, list } = createHarness()

    await list.handleOpenLotBalances()

    expect(list.lotBalanceQuery).toMatchObject({ warehouseId: 'W-1', productId: 'P-1' })
    expect(dependencies.getLotBalances).toHaveBeenCalledWith(list.lotBalanceQuery)
  })

  it('loads balances and reports the latest failure while resetting loading', async () => {
    const { dependencies, list, onListError } = createHarness()

    await list.loadLotBalances()
    expect(list.lotBalanceData.value).toEqual([lot])
    expect(list.lotBalanceTotal.value).toBe(1)
    expect(list.lotBalanceLoading.value).toBe(false)

    vi.mocked(dependencies.getLotBalances).mockRejectedValueOnce(new Error('network'))
    await list.loadLotBalances()
    expect(list.lotBalanceLoading.value).toBe(false)
    expect(onListError).toHaveBeenCalledWith(
      'inventoryStocks.message.lotStockLoadFailed',
      expect.any(Error)
    )
  })

  it('keeps the latest balance result and ignores a stale failure', async () => {
    const { dependencies, list, onListError } = createHarness()
    const first = deferred<ReturnType<typeof page<InventoryLotBalance>>>()
    const second = deferred<ReturnType<typeof page<InventoryLotBalance>>>()
    const latestLot = { ...lot, id: 'lot-2' } as InventoryLotBalance
    vi.mocked(dependencies.getLotBalances)
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise)

    const firstLoad = list.loadLotBalances()
    const secondLoad = list.loadLotBalances()
    second.resolve(page([latestLot]))
    await secondLoad
    first.reject(new Error('stale failure'))
    await firstLoad

    expect(list.lotBalanceData.value).toEqual([latestLot])
    expect(list.lotBalanceLoading.value).toBe(false)
    expect(onListError).not.toHaveBeenCalled()
  })

  it('handles balance search, reset, page, and page-size changes once each', async () => {
    const { dependencies, list } = createHarness()
    Object.assign(list.lotBalanceQuery, {
      pageNo: 5,
      pageSize: 20,
      lotNo: 'LOT-9',
      expiringWithinDays: 90
    })

    await list.handleLotBalanceQuery()
    await list.handleLotBalancePageChange(3)
    expect(list.lotBalanceQuery.pageNo).toBe(3)
    await list.handleLotBalanceSizeChange(50)
    expect(list.lotBalanceQuery).toMatchObject({ pageNo: 1, pageSize: 50 })
    await list.resetLotBalanceQuery()

    expect(list.lotBalanceQuery).toMatchObject({
      pageNo: 1,
      pageSize: 50,
      lotNo: undefined,
      expiringWithinDays: undefined
    })
    expect(dependencies.getLotBalances).toHaveBeenCalledTimes(4)
  })

  it('loads balance detail and closes the dialog on the latest failure', async () => {
    const { dependencies, list, onDetailError } = createHarness()

    await list.handleViewLotBalance(lot)
    expect(dependencies.getLotBalance).toHaveBeenCalledWith('lot-1')
    expect(list.selectedLotBalance.value).toEqual(lot)
    expect(list.lotBalanceDetailVisible.value).toBe(true)
    expect(list.lotBalanceDetailLoading.value).toBe(false)

    vi.mocked(dependencies.getLotBalance).mockRejectedValueOnce(new Error('network'))
    await list.handleViewLotBalance(lot)
    expect(list.selectedLotBalance.value).toBeUndefined()
    expect(list.lotBalanceDetailVisible.value).toBe(false)
    expect(list.lotBalanceDetailLoading.value).toBe(false)
    expect(onDetailError).toHaveBeenCalledWith('inventoryStocks.message.lotStockDetailLoadFailed')
  })

  it('keeps the latest balance detail when requests finish out of order', async () => {
    const { dependencies, list, onDetailError } = createHarness()
    const first = deferred<InventoryLotBalance>()
    const second = deferred<InventoryLotBalance>()
    const latestLot = { ...lot, id: 'lot-2' } as InventoryLotBalance
    vi.mocked(dependencies.getLotBalance)
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise)

    const firstLoad = list.handleViewLotBalance(lot)
    const secondLoad = list.handleViewLotBalance(latestLot)
    second.resolve(latestLot)
    await secondLoad
    first.reject(new Error('stale failure'))
    await firstLoad

    expect(list.selectedLotBalance.value).toEqual(latestLot)
    expect(list.lotBalanceDetailVisible.value).toBe(true)
    expect(list.lotBalanceDetailLoading.value).toBe(false)
    expect(onDetailError).not.toHaveBeenCalled()
  })

  it('opens trace from a lot row and preserves the current page size', async () => {
    const { dependencies, list } = createHarness()
    Object.assign(list.lotTraceQuery, { pageNo: 4, pageSize: 50, direction: 'OUT' })

    await list.handleOpenLotTrace(lot)

    expect(list.lotTraceDialogVisible.value).toBe(true)
    expect(list.lotTraceQuery).toMatchObject({
      warehouseId: 'W-2',
      productId: 'P-2',
      lotNo: 'LOT-1',
      pageNo: 1,
      pageSize: 50,
      direction: undefined
    })
    expect(list.lotTraceQuery).not.toHaveProperty('locationId')
    expect(dependencies.getLotTrace).toHaveBeenCalledTimes(1)
  })

  it('loads trace rows and reports the latest failure while resetting loading', async () => {
    const { dependencies, list, onListError } = createHarness()

    await list.loadLotTrace()
    expect(list.lotTraceData.value).toEqual([trace])
    expect(list.lotTraceTotal.value).toBe(1)
    expect(list.lotTraceLoading.value).toBe(false)

    vi.mocked(dependencies.getLotTrace).mockRejectedValueOnce(new Error('network'))
    await list.loadLotTrace()
    expect(list.lotTraceLoading.value).toBe(false)
    expect(onListError).toHaveBeenCalledWith(
      'inventoryStocks.message.lotTraceLoadFailed',
      expect.any(Error)
    )
  })

  it('keeps the latest trace result and ignores a stale failure', async () => {
    const { dependencies, list, onListError } = createHarness()
    const first = deferred<ReturnType<typeof page<InventoryLotTrace>>>()
    const second = deferred<ReturnType<typeof page<InventoryLotTrace>>>()
    const latestTrace = { ...trace, id: 'trace-2' } as InventoryLotTrace
    vi.mocked(dependencies.getLotTrace)
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise)

    const firstLoad = list.loadLotTrace()
    const secondLoad = list.loadLotTrace()
    second.resolve(page([latestTrace]))
    await secondLoad
    first.reject(new Error('stale failure'))
    await firstLoad

    expect(list.lotTraceData.value).toEqual([latestTrace])
    expect(list.lotTraceLoading.value).toBe(false)
    expect(onListError).not.toHaveBeenCalled()
  })

  it('handles trace page and page-size changes with one request each', async () => {
    const { dependencies, list } = createHarness()
    list.lotTraceQuery.pageNo = 5

    await list.handleLotTracePageChange(3)
    expect(list.lotTraceQuery.pageNo).toBe(3)
    await list.handleLotTraceSizeChange(50)

    expect(list.lotTraceQuery).toMatchObject({ pageNo: 1, pageSize: 50 })
    expect(dependencies.getLotTrace).toHaveBeenCalledTimes(2)
  })
})
