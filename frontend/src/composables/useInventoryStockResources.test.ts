import { describe, expect, it, vi } from 'vitest'

import type {
  InventoryLotBalance,
  InventoryLotExpiryAlert,
  InventoryLotTrace,
  InventoryReservation,
  InventoryReservationSummary,
  InventoryStock,
  InventoryTransaction
} from '@/api/inventory'
import { useInventoryStockQueries } from './useInventoryStockQueries'
import {
  useInventoryStockResources,
  type InventoryStockResourceDependencies
} from './useInventoryStockResources'

const page = <T>(record: T) => ({ records: [record], total: 1, pageNo: 1, pageSize: 10, pages: 1 })
const stock = { id: 'stock-1' } as InventoryStock
const reservation = { id: 'reservation-1' } as InventoryReservation
const summary = { productId: 'product-1' } as InventoryReservationSummary
const lot = { id: 'lot-1' } as InventoryLotBalance
const transaction = { id: 'transaction-1' } as InventoryTransaction
const alert = { id: 'alert-1' } as InventoryLotExpiryAlert
const trace = { id: 'trace-1' } as InventoryLotTrace

const createDependencies = () => ({
  getStocks: vi.fn(async () => page(stock)),
  getReservations: vi.fn(async () => page(reservation)),
  getReservationSummary: vi.fn(async () => [summary]),
  getLotBalances: vi.fn(async () => page(lot)),
  getTransactions: vi.fn(async () => page(transaction)),
  getLotAlerts: vi.fn(async () => page(alert)),
  getLotTrace: vi.fn(async () => page(trace))
}) as unknown as InventoryStockResourceDependencies

const createResources = (onError = vi.fn(), dependencies = createDependencies()) => {
  const queries = useInventoryStockQueries({ warehouseId: 'warehouse-1', productId: 'product-1' })
  Object.assign(queries.reservationQuery, {
    warehouseId: 'warehouse-1', productId: 'product-1', status: 'ACTIVE'
  })
  return {
    dependencies,
    onError,
    resources: useInventoryStockResources({
      stock: queries.queryParams,
      reservations: queries.reservationQuery,
      lotBalances: queries.lotBalanceQuery,
      transactions: queries.transactionQuery,
      lotAlerts: queries.lotAlertQuery,
      lotTrace: queries.lotTraceQuery
    }, onError, dependencies)
  }
}

describe('inventory stock resources', () => {
  it('loads all seven resources and stores their records and totals', async () => {
    const { dependencies, resources } = createResources()

    await Promise.all([
      resources.loadData(),
      resources.loadReservations(),
      resources.loadReservationSummary(),
      resources.loadLotBalances(),
      resources.loadTransactions(),
      resources.loadLotAlerts(),
      resources.loadLotTrace()
    ])

    expect(resources.tableData.value[0]?.id).toBe('stock-1')
    expect(resources.reservationData.value[0]?.id).toBe('reservation-1')
    expect(resources.reservationSummaryData.value[0]?.productId).toBe('product-1')
    expect(resources.lotBalanceData.value[0]?.id).toBe('lot-1')
    expect(resources.transactionData.value[0]?.id).toBe('transaction-1')
    expect(resources.lotAlertData.value[0]?.id).toBe('alert-1')
    expect(resources.lotTraceData.value[0]?.id).toBe('trace-1')
    expect(resources.total.value).toBe(1)
    expect(resources.reservationTotal.value).toBe(1)
    expect(resources.lotBalanceTotal.value).toBe(1)
    expect(resources.transactionTotal.value).toBe(1)
    expect(resources.lotAlertTotal.value).toBe(1)
    expect(resources.lotTraceTotal.value).toBe(1)
    expect(dependencies.getReservationSummary).toHaveBeenCalledWith({
      warehouseId: 'warehouse-1', productId: 'product-1', status: 'ACTIVE'
    })
  })

  it.each([
    ['loadData', 'getStocks', 'loading', 'inventoryStocks.message.stockLoadFailed'],
    ['loadReservations', 'getReservations', 'reservationLoading', 'inventoryStocks.message.reservationsLoadFailed'],
    ['loadReservationSummary', 'getReservationSummary', 'reservationSummaryLoading', 'inventoryStocks.message.reservationSummaryLoadFailed'],
    ['loadLotBalances', 'getLotBalances', 'lotBalanceLoading', 'inventoryStocks.message.lotStockLoadFailed'],
    ['loadTransactions', 'getTransactions', 'transactionLoading', 'inventoryStocks.message.transactionsLoadFailed'],
    ['loadLotAlerts', 'getLotAlerts', 'lotAlertLoading', 'inventoryStocks.message.expiryAlertsLoadFailed'],
    ['loadLotTrace', 'getLotTrace', 'lotTraceLoading', 'inventoryStocks.message.lotTraceLoadFailed']
  ] as const)('reports and resets loading when %s fails', async (
    loaderName, dependencyName, loadingName, messageKey
  ) => {
    const dependencies = createDependencies()
    vi.mocked(dependencies[dependencyName]).mockRejectedValueOnce(new Error('network'))
    const { onError, resources } = createResources(vi.fn(), dependencies)

    await resources[loaderName]()

    expect(resources[loadingName].value).toBe(false)
    expect(onError).toHaveBeenCalledWith(messageKey, expect.any(Error))
  })
})
