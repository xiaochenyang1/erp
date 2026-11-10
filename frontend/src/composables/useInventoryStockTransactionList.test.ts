import { reactive } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type {
  InventoryStock,
  InventoryStockQuery,
  InventoryTransaction
} from '@/api/inventory'
import {
  useInventoryStockTransactionList,
  type InventoryStockTransactionListDependencies
} from './useInventoryStockTransactionList'

const page = (records: InventoryTransaction[]) => ({
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

const transaction = {
  id: 'transaction-1',
  warehouseId: 'W-2',
  productId: 'P-2',
  bizNo: 'SO-1'
} as InventoryTransaction

const createDependencies = (): InventoryStockTransactionListDependencies => ({
  getTransaction: vi.fn(async () => transaction),
  getTransactions: vi.fn(async () => page([transaction]))
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
  const list = useInventoryStockTransactionList(stockQuery, {
    onDetailError,
    onListError
  }, dependencies)
  return { dependencies, list, onDetailError, onListError, stockQuery }
}

describe('inventory stock transaction list', () => {
  it('opens with row scope, clears filters, and preserves the current page size', async () => {
    const { dependencies, list } = createHarness()
    Object.assign(list.transactionQuery, {
      pageNo: 4,
      pageSize: 50,
      bizNo: 'OLD',
      direction: 'OUT'
    })

    await list.handleOpenTransactions({
      warehouseId: 'W-2',
      productId: 'P-2'
    } as InventoryStock)

    expect(list.transactionDialogVisible.value).toBe(true)
    expect(list.transactionQuery).toMatchObject({
      warehouseId: 'W-2',
      productId: 'P-2',
      pageNo: 1,
      pageSize: 50,
      bizNo: undefined,
      direction: undefined
    })
    expect(dependencies.getTransactions).toHaveBeenCalledTimes(1)
  })

  it('uses the current stock scope when opened without a row', async () => {
    const { dependencies, list } = createHarness()

    await list.handleOpenTransactions()

    expect(list.transactionQuery).toMatchObject({
      warehouseId: 'W-1',
      productId: 'P-1',
      pageNo: 1
    })
    expect(list.transactionQuery).not.toHaveProperty('locationId')
    expect(dependencies.getTransactions).toHaveBeenCalledWith(list.transactionQuery)
  })

  it('loads transactions and reports list failures while resetting loading', async () => {
    const { dependencies, list, onListError } = createHarness()

    await list.loadTransactions()
    expect(list.transactionData.value).toEqual([transaction])
    expect(list.transactionTotal.value).toBe(1)
    expect(list.transactionLoading.value).toBe(false)

    vi.mocked(dependencies.getTransactions).mockRejectedValueOnce(new Error('network'))

    await list.loadTransactions()
    expect(list.transactionLoading.value).toBe(false)
    expect(onListError).toHaveBeenCalledWith(
      'inventoryStocks.message.transactionsLoadFailed',
      expect.any(Error)
    )
  })

  it('keeps the latest transaction list result when requests finish out of order', async () => {
    const { dependencies, list } = createHarness()
    const first = deferred<ReturnType<typeof page>>()
    const second = deferred<ReturnType<typeof page>>()
    const latestTransaction = {
      ...transaction,
      id: 'transaction-2',
      bizNo: 'SO-2'
    } as InventoryTransaction
    vi.mocked(dependencies.getTransactions)
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise)

    const firstLoad = list.loadTransactions()
    const secondLoad = list.loadTransactions()

    second.resolve(page([latestTransaction]))
    await secondLoad
    first.resolve(page([transaction]))
    await firstLoad

    expect(list.transactionData.value).toEqual([latestTransaction])
    expect(list.transactionTotal.value).toBe(1)
    expect(list.transactionLoading.value).toBe(false)
  })

  it('ignores a stale transaction list failure after a newer request succeeds', async () => {
    const { dependencies, list, onListError } = createHarness()
    const first = deferred<ReturnType<typeof page>>()
    const second = deferred<ReturnType<typeof page>>()
    vi.mocked(dependencies.getTransactions)
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise)

    const firstLoad = list.loadTransactions()
    const secondLoad = list.loadTransactions()

    second.resolve(page([transaction]))
    await secondLoad
    first.reject(new Error('stale network failure'))
    await firstLoad

    expect(onListError).not.toHaveBeenCalled()
    expect(list.transactionData.value).toEqual([transaction])
    expect(list.transactionLoading.value).toBe(false)
  })

  it('handles search, reset, page, and page-size changes with one request each', async () => {
    const { dependencies, list } = createHarness()
    Object.assign(list.transactionQuery, {
      pageNo: 5,
      pageSize: 20,
      bizNo: 'SO-9',
      direction: 'IN'
    })

    await list.handleTransactionQuery()
    expect(list.transactionQuery.pageNo).toBe(1)

    await list.handleTransactionPageChange(3)
    expect(list.transactionQuery.pageNo).toBe(3)

    await list.handleTransactionSizeChange(50)
    expect(list.transactionQuery).toMatchObject({ pageNo: 1, pageSize: 50 })

    list.transactionQuery.pageNo = 4
    await list.resetTransactionQuery()
    expect(list.transactionQuery).toMatchObject({ pageNo: 1, pageSize: 50 })
    expect(list.transactionQuery.bizNo).toBeUndefined()
    expect(list.transactionQuery.direction).toBeUndefined()
    expect(dependencies.getTransactions).toHaveBeenCalledTimes(4)
  })

  it('loads transaction detail and closes the detail dialog on failure', async () => {
    const { dependencies, list, onDetailError } = createHarness()

    await list.handleViewTransaction(transaction)
    expect(dependencies.getTransaction).toHaveBeenCalledWith('transaction-1')
    expect(list.selectedTransaction.value).toEqual(transaction)
    expect(list.transactionDetailVisible.value).toBe(true)
    expect(list.transactionDetailLoading.value).toBe(false)

    vi.mocked(dependencies.getTransaction).mockRejectedValueOnce(new Error('network'))

    await list.handleViewTransaction(transaction)
    expect(list.selectedTransaction.value).toBeUndefined()
    expect(list.transactionDetailVisible.value).toBe(false)
    expect(list.transactionDetailLoading.value).toBe(false)
    expect(onDetailError).toHaveBeenCalledWith(
      'inventoryStocks.message.transactionDetailLoadFailed'
    )
  })

  it('keeps the latest transaction detail when requests finish out of order', async () => {
    const { dependencies, list } = createHarness()
    const first = deferred<InventoryTransaction>()
    const second = deferred<InventoryTransaction>()
    const latestTransaction = {
      ...transaction,
      id: 'transaction-2',
      bizNo: 'SO-2'
    } as InventoryTransaction
    vi.mocked(dependencies.getTransaction)
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise)

    const firstLoad = list.handleViewTransaction(transaction)
    const secondLoad = list.handleViewTransaction(latestTransaction)

    second.resolve(latestTransaction)
    await secondLoad
    first.resolve(transaction)
    await firstLoad

    expect(list.selectedTransaction.value).toEqual(latestTransaction)
    expect(list.transactionDetailVisible.value).toBe(true)
    expect(list.transactionDetailLoading.value).toBe(false)
  })

  it('ignores a stale transaction detail failure after a newer request succeeds', async () => {
    const { dependencies, list, onDetailError } = createHarness()
    const first = deferred<InventoryTransaction>()
    const second = deferred<InventoryTransaction>()
    const latestTransaction = {
      ...transaction,
      id: 'transaction-2',
      bizNo: 'SO-2'
    } as InventoryTransaction
    vi.mocked(dependencies.getTransaction)
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise)

    const firstLoad = list.handleViewTransaction(transaction)
    const secondLoad = list.handleViewTransaction(latestTransaction)

    second.resolve(latestTransaction)
    await secondLoad
    first.reject(new Error('stale network failure'))
    await firstLoad

    expect(onDetailError).not.toHaveBeenCalled()
    expect(list.selectedTransaction.value).toEqual(latestTransaction)
    expect(list.transactionDetailVisible.value).toBe(true)
    expect(list.transactionDetailLoading.value).toBe(false)
  })
})
