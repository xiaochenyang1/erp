import { describe, expect, it, vi } from 'vitest'

import type {
  InventoryLotBalance,
  InventoryLotTrace,
  InventoryReservation,
  InventoryReservationSummary
} from '@/api/inventory'
import { useInventoryStockQueries } from './useInventoryStockQueries'
import {
  useInventoryStockResources,
  type InventoryStockResourceDependencies
} from './useInventoryStockResources'

const page = <T>(record: T) => ({ records: [record], total: 1, pageNo: 1, pageSize: 10, pages: 1 })
const reservation = { id: 'reservation-1' } as InventoryReservation
const summary = { productId: 'product-1' } as InventoryReservationSummary
const lot = { id: 'lot-1' } as InventoryLotBalance
const trace = { id: 'trace-1' } as InventoryLotTrace

const createDependencies = () => ({
  getReservations: vi.fn(async () => page(reservation)),
  getReservationSummary: vi.fn(async () => [summary]),
  getLotBalances: vi.fn(async () => page(lot)),
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
      reservations: queries.reservationQuery,
      lotBalances: queries.lotBalanceQuery,
      lotTrace: queries.lotTraceQuery
    }, onError, dependencies)
  }
}

describe('inventory stock resources', () => {
  it('loads the remaining four dialog resources and stores their records and totals', async () => {
    const { dependencies, resources } = createResources()

    await Promise.all([
      resources.loadReservations(),
      resources.loadReservationSummary(),
      resources.loadLotBalances(),
      resources.loadLotTrace()
    ])

    expect(resources.reservationData.value[0]?.id).toBe('reservation-1')
    expect(resources.reservationSummaryData.value[0]?.productId).toBe('product-1')
    expect(resources.lotBalanceData.value[0]?.id).toBe('lot-1')
    expect(resources.lotTraceData.value[0]?.id).toBe('trace-1')
    expect(resources.reservationTotal.value).toBe(1)
    expect(resources.lotBalanceTotal.value).toBe(1)
    expect(resources.lotTraceTotal.value).toBe(1)
    expect(dependencies.getReservationSummary).toHaveBeenCalledWith({
      warehouseId: 'warehouse-1', productId: 'product-1', status: 'ACTIVE'
    })
  })

  it.each([
    ['loadReservations', 'getReservations', 'reservationLoading', 'inventoryStocks.message.reservationsLoadFailed'],
    ['loadReservationSummary', 'getReservationSummary', 'reservationSummaryLoading', 'inventoryStocks.message.reservationSummaryLoadFailed'],
    ['loadLotBalances', 'getLotBalances', 'lotBalanceLoading', 'inventoryStocks.message.lotStockLoadFailed'],
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
