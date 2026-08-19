import { reactive } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type { InventoryStock, InventoryStockQuery } from '@/api/inventory'
import type { Location, Product, Warehouse } from '@/api/masterdata'
import {
  useInventoryStockBalanceList,
  type InventoryStockBalanceListDependencies
} from './useInventoryStockBalanceList'

const page = <T>(records: T[], pageNo = 1, pageSize = 20) => ({
  records,
  total: records.length,
  pageNo,
  pageSize
})

const stock = { id: 'stock-1', warehouseId: '1', productId: 'product-1' } as InventoryStock
const warehouse = { id: '1', name: 'Main' } as Warehouse
const product = { id: 'product-1', name: 'Widget' } as Product
const locations = [
  { id: 'location-1', warehouseId: 1, locationCode: 'A', locationName: 'A' },
  { id: 'location-2', warehouseId: 2, locationCode: 'B', locationName: 'B' }
] as Location[]

const createDependencies = (): InventoryStockBalanceListDependencies => ({
  download: vi.fn(),
  exportStocks: vi.fn(async () => new Blob(['stock'])),
  getLocations: vi.fn(async () => page(locations, 1, 500)),
  getProducts: vi.fn(async () => page([product], 1, 200)),
  getStocks: vi.fn(async () => page([stock])),
  getWarehouses: vi.fn(async () => page([warehouse], 1, 200)),
  now: () => 123456
})

const createHarness = () => {
  const queryParams = reactive<InventoryStockQuery>({
    pageNo: 3,
    pageSize: 20,
    warehouseId: '1',
    productId: 'product-1',
    locationId: 'location-1'
  })
  const dependencies = createDependencies()
  const onError = vi.fn()
  const onOptionsError = vi.fn()
  const onSuccess = vi.fn()
  const list = useInventoryStockBalanceList(queryParams, {
    onError,
    onOptionsError,
    onSuccess,
    t: (key, params) => params?.timestamp ? `${key}-${params.timestamp}` : key
  }, dependencies)
  return { dependencies, list, onError, onOptionsError, onSuccess, queryParams }
}

describe('inventory stock balance list', () => {
  it('loads balances and reports failures while resetting loading', async () => {
    const { dependencies, list, onError } = createHarness()

    await list.loadData()
    expect(list.tableData.value).toEqual([stock])
    expect(list.total.value).toBe(1)
    expect(list.loading.value).toBe(false)

    vi.mocked(dependencies.getStocks).mockRejectedValueOnce(new Error('network'))

    await list.loadData()
    expect(list.loading.value).toBe(false)
    expect(onError).toHaveBeenCalledWith(
      'inventoryStocks.message.stockLoadFailed',
      expect.any(Error)
    )
  })

  it('loads active options and filters locations using normalized warehouse ids', async () => {
    const { dependencies, list, queryParams } = createHarness()

    expect(await list.loadOptions()).toBe(true)
    expect(dependencies.getWarehouses).toHaveBeenCalledWith({
      pageNo: 1, pageSize: 200, status: 'ACTIVE'
    })
    expect(dependencies.getProducts).toHaveBeenCalledWith({
      pageNo: 1, pageSize: 200, status: 'ACTIVE'
    })
    expect(dependencies.getLocations).toHaveBeenCalledWith({
      pageNo: 1, pageSize: 500, status: 'ACTIVE'
    })
    expect(list.warehouses.value).toEqual([warehouse])
    expect(list.products.value).toEqual([product])
    expect(list.locationsForQuery.value).toEqual([locations[0]])

    queryParams.warehouseId = undefined
    expect(list.locationsForQuery.value).toEqual(locations)
  })

  it('reports option failures without routing them through the user-facing error callback', async () => {
    const { dependencies, list, onError, onOptionsError } = createHarness()
    vi.mocked(dependencies.getWarehouses).mockRejectedValueOnce(new Error('network'))

    expect(await list.loadOptions()).toBe(false)
    expect(onOptionsError).toHaveBeenCalledWith(
      'inventoryStocks.message.optionsLoadFailed',
      expect.any(Error)
    )
    expect(onError).not.toHaveBeenCalled()
  })

  it('resets filters and handles page and page-size changes', async () => {
    const { dependencies, list, queryParams } = createHarness()

    await list.handleQuery()
    expect(queryParams.pageNo).toBe(1)

    queryParams.pageNo = 4
    await list.handlePageChange(2)
    expect(queryParams.pageNo).toBe(2)

    await list.handleSizeChange(50)
    expect(queryParams).toMatchObject({ pageNo: 1, pageSize: 50 })

    await list.handleReset()
    expect(queryParams).toMatchObject({ pageNo: 1, pageSize: 50 })
    expect(queryParams.warehouseId).toBeUndefined()
    expect(queryParams.productId).toBeUndefined()
    expect(queryParams.locationId).toBeUndefined()
    expect(dependencies.getStocks).toHaveBeenCalledTimes(4)
  })

  it('exports the current query with a deterministic filename and reports failures', async () => {
    const { dependencies, list, onError, onSuccess, queryParams } = createHarness()

    expect(await list.handleExport()).toBe(true)
    expect(dependencies.exportStocks).toHaveBeenCalledWith(queryParams)
    expect(dependencies.download).toHaveBeenCalledWith(
      expect.any(Blob),
      'inventoryStocks.file.stockBalances-123456'
    )
    expect(onSuccess).toHaveBeenCalledWith('inventoryStocks.message.exported')

    vi.mocked(dependencies.exportStocks).mockRejectedValueOnce(new Error('network'))

    expect(await list.handleExport()).toBe(false)
    expect(onError).toHaveBeenCalledWith('inventoryStocks.message.exportFailed')
    expect(dependencies.download).toHaveBeenCalledTimes(1)
  })
})
