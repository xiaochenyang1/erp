import { reactive, ref } from 'vue'

import type { InventoryCheck, InventoryCheckQuery } from '@/api/inventory'
import type { Location, Product, Warehouse } from '@/api/masterdata'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title: string,
  options?: {
    confirmButtonText?: string
    cancelButtonText?: string
    type?: string
  }
) => Promise<unknown>
type OptionPageQuery = {
  pageNo?: number
  pageSize?: number
  status?: string
}

export const useInventoryCheckList = (
  t: Translate,
  options: {
    getChecks: (params: InventoryCheckQuery) => Promise<PageResponse<InventoryCheck>>
    getCheck: (id: string | number) => Promise<InventoryCheck>
    completeCheck: (id: string | number) => Promise<unknown>
    cancelCheck: (id: string | number) => Promise<unknown>
    getWarehouses: (params: OptionPageQuery) => Promise<PageResponse<Warehouse>>
    getProducts: (params: OptionPageQuery) => Promise<PageResponse<Product>>
    getLocations: (params: OptionPageQuery) => Promise<PageResponse<Location>>
    printCheck: (doc: InventoryCheck) => void
    confirm: Confirm
    decorateRows?: (rows: InventoryCheck[]) => InventoryCheck[]
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const queryParams = reactive<InventoryCheckQuery>({
    pageNo: 1,
    pageSize: 10,
    checkNo: '',
    warehouseId: undefined,
    status: '',
    startDate: '',
    endDate: ''
  })
  const dateRange = ref<[string, string] | null>(null)
  const tableData = ref<InventoryCheck[]>([])
  const total = ref(0)
  const loading = ref(false)
  const warehouses = ref<Warehouse[]>([])
  const products = ref<Product[]>([])
  const locations = ref<Location[]>([])

  const loadData = async () => {
    loading.value = true
    try {
      const response = await options.getChecks(queryParams)
      const records = response.records || []
      tableData.value = options.decorateRows ? options.decorateRows(records) : records
      total.value = response.total || 0
    } catch {
      options.onError?.(t('inventoryChecks.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const handleQuery = async () => {
    if (dateRange.value) {
      queryParams.startDate = dateRange.value[0]
      queryParams.endDate = dateRange.value[1]
    } else {
      queryParams.startDate = ''
      queryParams.endDate = ''
    }
    queryParams.pageNo = 1
    await loadData()
  }

  const handlePageChange = async () => {
    await loadData()
  }

  const handleReset = async () => {
    queryParams.checkNo = ''
    queryParams.warehouseId = undefined
    queryParams.status = ''
    dateRange.value = null
    queryParams.startDate = ''
    queryParams.endDate = ''
    await handleQuery()
  }

  const handlePrint = async (row: InventoryCheck) => {
    try {
      const detail = await options.getCheck(row.id)
      options.printCheck(detail)
    } catch {
      options.onError?.(t('inventoryChecks.message.printLoadFailed'))
    }
  }

  const handleComplete = async (row: InventoryCheck) => {
    try {
      await options.confirm(
        t('inventoryChecks.message.adjustConfirm'),
        t('inventoryChecks.prompt'),
        {
          confirmButtonText: t('inventoryChecks.action.confirm'),
          cancelButtonText: t('inventoryChecks.action.cancel'),
          type: 'warning'
        }
      )
      await options.completeCheck(row.id)
      options.onSuccess?.(t('inventoryChecks.message.success'))
      await loadData()
    } catch (error: any) {
      if (error !== 'cancel') {
        options.onError?.(t('inventoryChecks.message.failed'))
      }
    }
  }

  const handleCancel = async (row: InventoryCheck) => {
    try {
      await options.confirm(
        t('inventoryChecks.message.cancelConfirm'),
        t('inventoryChecks.prompt'),
        {
          confirmButtonText: t('inventoryChecks.action.confirm'),
          cancelButtonText: t('inventoryChecks.action.cancel'),
          type: 'warning'
        }
      )
      await options.cancelCheck(row.id)
      options.onSuccess?.(t('inventoryChecks.message.success'))
      await loadData()
    } catch (error: any) {
      if (error !== 'cancel') {
        options.onError?.(t('inventoryChecks.message.failed'))
      }
    }
  }

  const loadWarehouses = async () => {
    try {
      const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }
      const response = await options.getWarehouses(optionPageQuery)
      warehouses.value = response.records || []
    } catch {
      options.onError?.(t('inventoryChecks.message.warehousesLoadFailed'))
    }
  }

  const loadProducts = async () => {
    try {
      const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }
      const response = await options.getProducts(optionPageQuery)
      products.value = response.records || []
    } catch {
      options.onError?.(t('inventoryChecks.message.productsLoadFailed'))
    }
  }

  const loadLocations = async () => {
    try {
      const response = await options.getLocations({ pageNo: 1, pageSize: 500, status: 'ACTIVE' })
      locations.value = response.records || []
    } catch {
      locations.value = []
    }
  }

  const loadOptions = async () => {
    await Promise.all([loadWarehouses(), loadProducts(), loadLocations()])
  }

  return {
    dateRange,
    handleCancel,
    handleComplete,
    handlePageChange,
    handlePrint,
    handleQuery,
    handleReset,
    loadData,
    loadLocations,
    loadOptions,
    loadProducts,
    loadWarehouses,
    loading,
    locations,
    products,
    queryParams,
    tableData,
    total,
    warehouses
  }
}
