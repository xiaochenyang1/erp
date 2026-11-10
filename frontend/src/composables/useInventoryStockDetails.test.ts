import { describe, expect, it, vi } from 'vitest'

import type {
  InventoryStock
} from '@/api/inventory'
import {
  useInventoryStockDetails,
  type InventoryStockDetailDependencies
} from './useInventoryStockDetails'

const stock = { id: 'stock-1', warehouseId: 'w-1', productId: 'p-1' } as InventoryStock

const createDependencies = (): InventoryStockDetailDependencies => ({
  getStock: vi.fn(async () => stock)
})

describe('inventory stock detail loading', () => {
  it('loads stock detail and exposes it only after the request succeeds', async () => {
    const dependencies = createDependencies()
    const details = useInventoryStockDetails(vi.fn(), dependencies)

    await details.handleViewStock(stock)
    expect(details.selectedStock.value?.id).toBe(stock.id)
    expect(details.stockDetailVisible.value).toBe(true)
  })

  it('closes the dialog and reports the stock detail key when loading fails', async () => {
    const dependencies = createDependencies()
    vi.mocked(dependencies.getStock).mockRejectedValueOnce(new Error('network'))
    const onError = vi.fn()
    const details = useInventoryStockDetails(onError, dependencies)

    await details.handleViewStock(stock)

    expect(details.stockDetailVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('inventoryStocks.message.stockDetailLoadFailed')
    expect(details.detailLoading.value).toBe(false)
  })
})
