import { reactive, ref } from 'vue'

import type { InventoryAdjustment, InventoryAdjustmentQuery } from '@/api/inventory'
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

export const useInventoryAdjustmentList = (
  t: Translate,
  options: {
    getAdjustments: (params: InventoryAdjustmentQuery) => Promise<PageResponse<InventoryAdjustment>>
    getAdjustment: (id: string | number) => Promise<InventoryAdjustment>
    postAdjustment: (id: string | number) => Promise<unknown>
    cancelAdjustment: (id: string | number) => Promise<unknown>
    getWarehouses: (params: OptionPageQuery) => Promise<PageResponse<Warehouse>>
    getProducts: (params: OptionPageQuery) => Promise<PageResponse<Product>>
    getLocations: (params: OptionPageQuery) => Promise<PageResponse<Location>>
    printAdjustment: (doc: InventoryAdjustment) => void
    confirm: Confirm
    decorateRows?: (rows: InventoryAdjustment[]) => InventoryAdjustment[]
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const queryParams = reactive<InventoryAdjustmentQuery>({
    pageNo: 1,
    pageSize: 10,
    adjustmentNo: '',
    warehouseId: undefined,
    type: '',
    status: '',
    startDate: '',
    endDate: ''
  })
  const dateRange = ref<[string, string] | null>(null)
  const tableData = ref<InventoryAdjustment[]>([])
  const total = ref(0)
  const loading = ref(false)
  const warehouses = ref<Warehouse[]>([])
  const products = ref<Product[]>([])
  const locations = ref<Location[]>([])

  const loadData = async () => {
    loading.value = true
    try {
      const response = await options.getAdjustments(queryParams)
      const records = response.records || []
      tableData.value = options.decorateRows ? options.decorateRows(records) : records
      total.value = response.total || 0
    } catch {
      options.onError?.(t('inventoryAdjustments.message.loadFailed'))
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
    queryParams.adjustmentNo = ''
    queryParams.warehouseId = undefined
    queryParams.type = ''
    queryParams.status = ''
    dateRange.value = null
    queryParams.startDate = ''
    queryParams.endDate = ''
    await handleQuery()
  }

  const handlePrint = async (row: InventoryAdjustment) => {
    try {
      const detail = await options.getAdjustment(row.id)
      options.printAdjustment(detail)
    } catch {
      options.onError?.(t('inventoryAdjustments.message.printLoadFailed'))
    }
  }

  const confirmThen = async (
    messageKey: string,
    action: (id: string | number) => Promise<unknown>,
    row: InventoryAdjustment
  ) => {
    try {
      await options.confirm(
        t(messageKey),
        t('inventoryAdjustments.prompt'),
        {
          confirmButtonText: t('inventoryAdjustments.action.confirm'),
          cancelButtonText: t('inventoryAdjustments.action.cancel'),
          type: 'warning'
        }
      )
      await action(row.id)
      options.onSuccess?.(t('inventoryAdjustments.message.success'))
      await loadData()
    } catch (error: any) {
      if (error !== 'cancel') {
        options.onError?.(t('inventoryAdjustments.message.failed'))
      }
    }
  }

  const handleComplete = (row: InventoryAdjustment) =>
    confirmThen('inventoryAdjustments.message.postConfirm', options.postAdjustment, row)

  const handleCancel = (row: InventoryAdjustment) =>
    confirmThen('inventoryAdjustments.message.cancelConfirm', options.cancelAdjustment, row)

  const loadWarehouses = async () => {
    try {
      const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }
      const response = await options.getWarehouses(optionPageQuery)
      warehouses.value = response.records || []
    } catch {
      options.onError?.(t('inventoryAdjustments.message.warehousesLoadFailed'))
    }
  }

  const loadProducts = async () => {
    try {
      const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }
      const response = await options.getProducts(optionPageQuery)
      products.value = response.records || []
    } catch {
      options.onError?.(t('inventoryAdjustments.message.productsLoadFailed'))
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
