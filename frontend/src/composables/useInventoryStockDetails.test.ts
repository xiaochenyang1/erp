import { describe, expect, it, vi } from 'vitest'

import type {
  InventoryLotBalance,
  InventoryReservation,
  InventoryReservationDetail,
  InventoryReservationSource,
  InventoryStock
} from '@/api/inventory'
import {
  useInventoryStockDetails,
  type InventoryStockDetailDependencies
} from './useInventoryStockDetails'

const stock = { id: 'stock-1', warehouseId: 'w-1', productId: 'p-1' } as InventoryStock
const lot = { id: 'lot-1' } as InventoryLotBalance
const reservation = {
  id: 'reservation-1',
  sourceType: 'SALES_ORDER',
  sourceId: 'source-1',
  sourceNo: 'SO-1'
} as InventoryReservation

const createDependencies = (): InventoryStockDetailDependencies => ({
  getStock: vi.fn(async () => stock),
  getLotBalance: vi.fn(async () => lot),
  getReservation: vi.fn(async () => ({ reservation, events: [] } as InventoryReservationDetail)),
  getReservationSource: vi.fn(async () => ({ sourceNo: reservation.sourceNo } as InventoryReservationSource))
})

describe('inventory stock detail loading', () => {
  it('loads each detail and exposes it only after the request succeeds', async () => {
    const dependencies = createDependencies()
    const details = useInventoryStockDetails(vi.fn(), dependencies)

    await details.handleViewStock(stock)
    await details.handleViewLotBalance(lot)
    await details.handleViewReservation(reservation)
    await details.handleViewReservationSource(reservation)

    expect(details.selectedStock.value?.id).toBe(stock.id)
    expect(details.selectedLotBalance.value?.id).toBe(lot.id)
    expect(details.reservationDetail.value?.reservation.id).toBe(reservation.id)
    expect(details.reservationSourceDetail.value?.sourceNo).toBe('SO-1')
    expect(details.stockDetailVisible.value).toBe(true)
    expect(details.lotBalanceDetailVisible.value).toBe(true)
    expect(details.reservationDetailVisible.value).toBe(true)
    expect(details.reservationSourceVisible.value).toBe(true)
    expect(dependencies.getReservationSource).toHaveBeenCalledWith({
      sourceType: 'SALES_ORDER', sourceId: 'source-1', sourceNo: 'SO-1'
    })
  })

  it.each([
    ['handleViewStock', 'getStock', stock, 'stockDetailVisible', 'inventoryStocks.message.stockDetailLoadFailed'],
    ['handleViewLotBalance', 'getLotBalance', lot, 'lotBalanceDetailVisible', 'inventoryStocks.message.lotStockDetailLoadFailed'],
    ['handleViewReservation', 'getReservation', reservation, 'reservationDetailVisible', 'inventoryStocks.message.reservationDetailLoadFailed'],
    ['handleViewReservationSource', 'getReservationSource', reservation, 'reservationSourceVisible', 'inventoryStocks.message.sourceReservationLoadFailed']
  ] as const)('closes the dialog and reports the proper key when %s fails', async (
    handlerName, dependencyName, row, visibleName, messageKey
  ) => {
    const dependencies = createDependencies()
    vi.mocked(dependencies[dependencyName]).mockRejectedValueOnce(new Error('network'))
    const onError = vi.fn()
    const details = useInventoryStockDetails(onError, dependencies)

    await details[handlerName](row as never)

    expect(details[visibleName].value).toBe(false)
    expect(onError).toHaveBeenCalledWith(messageKey)
    expect(details.detailLoading.value).toBe(false)
    expect(details.reservationSourceLoading.value).toBe(false)
  })
})
