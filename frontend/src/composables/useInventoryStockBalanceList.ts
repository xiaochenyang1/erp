import { computed, ref } from 'vue'

import {
  exportInventoryStocks,
  getInventoryStocks,
  type InventoryStock,
  type InventoryStockQuery
} from '@/api/inventory'
import {
  getLocations,
  getProducts,
  getWarehouses,
  type Location,
  type Product,
  type Warehouse
} from '@/api/masterdata'
import { downloadBlob } from '@/utils/download'

type Translate = (key: string, params?: Record<string, unknown>) => string

export interface InventoryStockBalanceListDependencies {
  download: typeof downloadBlob
  exportStocks: typeof exportInventoryStocks
  getLocations: typeof getLocations
  getProducts: typeof getProducts
  getStocks: typeof getInventoryStocks
  getWarehouses: typeof getWarehouses
  now: () => number
}

export interface InventoryStockBalanceListCallbacks {
  onError: (messageKey: string, error?: unknown) => void
  onOptionsError: (messageKey: string, error: unknown) => void
  onSuccess: (messageKey: string) => void
  t: Translate
}

const defaultDependencies: InventoryStockBalanceListDependencies = {
  download: downloadBlob,
  exportStocks: exportInventoryStocks,
  getLocations,
  getProducts,
  getStocks: getInventoryStocks,
  getWarehouses,
  now: Date.now
}

/** Main stock-balance table, filter options, pagination, and export. */
export const useInventoryStockBalanceList = (
  queryParams: InventoryStockQuery,
  callbacks: InventoryStockBalanceListCallbacks,
  dependencies: InventoryStockBalanceListDependencies = defaultDependencies
) => {
  const loading = ref(false)
  const tableData = ref<InventoryStock[]>([])
  const total = ref(0)
  const warehouses = ref<Warehouse[]>([])
  const products = ref<Product[]>([])
  const locations = ref<Location[]>([])

  const locationsForQuery = computed(() => {
    if (!queryParams.warehouseId) return locations.value
    return locations.value.filter(
      (location) => String(location.warehouseId) === String(queryParams.warehouseId)
    )
  })

  const loadData = async () => {
    loading.value = true
    try {
      const page = await dependencies.getStocks(queryParams)
      tableData.value = page.records || []
      total.value = page.total || 0
    } catch (error) {
      callbacks.onError('inventoryStocks.message.stockLoadFailed', error)
    } finally {
      loading.value = false
    }
  }

  const loadOptions = async () => {
    const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }
    try {
      const [warehousePage, productPage, locationPage] = await Promise.all([
        dependencies.getWarehouses(optionPageQuery),
        dependencies.getProducts(optionPageQuery),
        dependencies.getLocations({ pageNo: 1, pageSize: 500, status: 'ACTIVE' })
      ])
      warehouses.value = warehousePage.records || []
      products.value = productPage.records || []
      locations.value = locationPage.records || []
      return true
    } catch (error) {
      callbacks.onOptionsError('inventoryStocks.message.optionsLoadFailed', error)
      return false
    }
  }

  const handleQuery = () => {
    queryParams.pageNo = 1
    return loadData()
  }

  const handleReset = () => {
    queryParams.warehouseId = undefined
    queryParams.productId = undefined
    queryParams.locationId = undefined
    queryParams.pageNo = 1
    return loadData()
  }

  const handlePageChange = (page: number) => {
    queryParams.pageNo = page
    return loadData()
  }

  const handleSizeChange = (pageSize: number) => {
    queryParams.pageSize = pageSize
    queryParams.pageNo = 1
    return loadData()
  }

  const handleExport = async () => {
    try {
      const blob = await dependencies.exportStocks(queryParams)
      dependencies.download(blob, callbacks.t('inventoryStocks.file.stockBalances', {
        timestamp: dependencies.now()
      }))
      callbacks.onSuccess('inventoryStocks.message.exported')
      return true
    } catch {
      callbacks.onError('inventoryStocks.message.exportFailed')
      return false
    }
  }

  return {
    handleExport,
    handlePageChange,
    handleQuery,
    handleReset,
    handleSizeChange,
    loadData,
    loadOptions,
    loading,
    locations,
    locationsForQuery,
    products,
    tableData,
    total,
    warehouses
  }
}
