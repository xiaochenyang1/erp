import { reactive } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type { InventoryLotExpiryAlert, InventoryStockQuery, InventoryStock } from '@/api/inventory'
import {
  useInventoryStockExpiryAlertList,
  type InventoryStockExpiryAlertListDependencies
} from './useInventoryStockExpiryAlertList'

const page = (records: InventoryLotExpiryAlert[]) => ({
  records,
  total: records.length,
  pageNo: 1,
  pageSize: 10
})

const alert = {
  id: 'alert-1',
  warehouseId: 'W-2',
  productId: 'P-2',
  lotNo: 'LOT-1',
  expiryStatus: 'EXPIRING'
} as InventoryLotExpiryAlert

const deferred = <T>() => {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, reject, resolve }
}

const createDependencies = (): InventoryStockExpiryAlertListDependencies => ({
  getLotAlerts: vi.fn(async () => page([alert]))
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
  const onError = vi.fn()
  const list = useInventoryStockExpiryAlertList(stockQuery, { onError }, dependencies)
  return { dependencies, list, onError, stockQuery }
}

describe('inventory stock expiry alert list', () => {
  it('opens with row scope, clears the lot filter, and preserves page size', async () => {
    const { dependencies, list } = createHarness()
    Object.assign(list.lotAlertQuery, { pageNo: 4, pageSize: 50, lotNo: 'OLD' })

    await list.handleOpenLotAlerts({ warehouseId: 'W-2', productId: 'P-2' } as InventoryStock)

    expect(list.lotAlertDialogVisible.value).toBe(true)
    expect(list.lotAlertQuery).toMatchObject({
      warehouseId: 'W-2',
      productId: 'P-2',
      pageNo: 1,
      pageSize: 50,
      lotNo: undefined
    })
    expect(dependencies.getLotAlerts).toHaveBeenCalledTimes(1)
  })

  it('uses the current stock scope when opened without a row', async () => {
    const { dependencies, list } = createHarness()

    await list.handleOpenLotAlerts()

    expect(list.lotAlertQuery).toMatchObject({ warehouseId: 'W-1', productId: 'P-1', pageNo: 1 })
    expect(list.lotAlertQuery).not.toHaveProperty('locationId')
    expect(dependencies.getLotAlerts).toHaveBeenCalledWith(list.lotAlertQuery)
  })

  it('loads alerts and reports failures while resetting loading', async () => {
    const { dependencies, list, onError } = createHarness()

    await list.loadLotAlerts()
    expect(list.lotAlertData.value).toEqual([alert])
    expect(list.lotAlertTotal.value).toBe(1)
    expect(list.lotAlertLoading.value).toBe(false)

    vi.mocked(dependencies.getLotAlerts).mockRejectedValueOnce(new Error('network'))
    await list.loadLotAlerts()
    expect(list.lotAlertLoading.value).toBe(false)
    expect(onError).toHaveBeenCalledWith(
      'inventoryStocks.message.expiryAlertsLoadFailed',
      expect.any(Error)
    )
  })

  it('handles search, reset, page, and page-size changes with one request each', async () => {
    const { dependencies, list } = createHarness()
    Object.assign(list.lotAlertQuery, {
      pageNo: 5,
      pageSize: 20,
      lotNo: 'LOT-9',
      warningDays: 90,
      status: 'EXPIRED'
    })

    await list.handleLotAlertQuery()
    expect(list.lotAlertQuery.pageNo).toBe(1)
    await list.handleLotAlertPageChange(3)
    expect(list.lotAlertQuery.pageNo).toBe(3)
    await list.handleLotAlertSizeChange(50)
    expect(list.lotAlertQuery).toMatchObject({ pageNo: 1, pageSize: 50 })

    await list.resetLotAlertQuery()
    expect(list.lotAlertQuery).toMatchObject({
      pageNo: 1,
      pageSize: 50,
      lotNo: undefined,
      warningDays: 30,
      status: undefined
    })
    expect(dependencies.getLotAlerts).toHaveBeenCalledTimes(4)
  })

  it('keeps the latest result when requests finish out of order', async () => {
    const { dependencies, list } = createHarness()
    const first = deferred<ReturnType<typeof page>>()
    const second = deferred<ReturnType<typeof page>>()
    const latestAlert = { ...alert, id: 'alert-2' } as InventoryLotExpiryAlert
    vi.mocked(dependencies.getLotAlerts)
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise)

    const firstLoad = list.loadLotAlerts()
    const secondLoad = list.loadLotAlerts()
    second.resolve(page([latestAlert]))
    await secondLoad
    first.resolve(page([alert]))
    await firstLoad

    expect(list.lotAlertData.value).toEqual([latestAlert])
    expect(list.lotAlertLoading.value).toBe(false)
  })

  it('ignores a stale failure after a newer request succeeds', async () => {
    const { dependencies, list, onError } = createHarness()
    const first = deferred<ReturnType<typeof page>>()
    const second = deferred<ReturnType<typeof page>>()
    vi.mocked(dependencies.getLotAlerts)
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise)

    const firstLoad = list.loadLotAlerts()
    const secondLoad = list.loadLotAlerts()
    second.resolve(page([alert]))
    await secondLoad
    first.reject(new Error('stale network failure'))
    await firstLoad

    expect(onError).not.toHaveBeenCalled()
    expect(list.lotAlertData.value).toEqual([alert])
    expect(list.lotAlertLoading.value).toBe(false)
  })
})
