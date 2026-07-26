import { reactive, ref } from 'vue'

import type { InventoryTransfer, InventoryTransferQuery } from '@/api/inventory'
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

export const useInventoryTransferList = (
  t: Translate,
  options: {
    getTransfers: (params: InventoryTransferQuery) => Promise<PageResponse<InventoryTransfer>>
    getTransfer: (id: string | number) => Promise<InventoryTransfer>
    postTransfer: (id: string | number) => Promise<unknown>
    cancelTransfer: (id: string | number) => Promise<unknown>
    getWarehouses: (params: OptionPageQuery) => Promise<PageResponse<Warehouse>>
    getProducts: (params: OptionPageQuery) => Promise<PageResponse<Product>>
    getLocations: (params: OptionPageQuery) => Promise<PageResponse<Location>>
    printTransfer: (doc: InventoryTransfer) => void
    confirm: Confirm
    decorateRows?: (rows: InventoryTransfer[]) => InventoryTransfer[]
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const queryParams = reactive<InventoryTransferQuery>({
    pageNo: 1,
    pageSize: 10,
    transferNo: '',
    fromWarehouseId: undefined,
    toWarehouseId: undefined,
    status: '',
    startDate: '',
    endDate: ''
  })
  const dateRange = ref<[string, string] | null>(null)
  const tableData = ref<InventoryTransfer[]>([])
  const total = ref(0)
  const loading = ref(false)
  const warehouses = ref<Warehouse[]>([])
  const products = ref<Product[]>([])
  const locations = ref<Location[]>([])

  const loadData = async () => {
    loading.value = true
    try {
      const response = await options.getTransfers(queryParams)
      const records = response.records || []
      tableData.value = options.decorateRows ? options.decorateRows(records) : records
      total.value = response.total || 0
    } catch {
      options.onError?.(t('inventoryTransfers.message.loadFailed'))
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
    queryParams.transferNo = ''
    queryParams.fromWarehouseId = undefined
    queryParams.toWarehouseId = undefined
    queryParams.status = ''
    dateRange.value = null
    queryParams.startDate = ''
    queryParams.endDate = ''
    await handleQuery()
  }

  const handlePrint = async (row: InventoryTransfer) => {
    try {
      const detail = await options.getTransfer(row.id)
      options.printTransfer(detail)
    } catch {
      options.onError?.(t('inventoryTransfers.message.printLoadFailed'))
    }
  }

  const confirmThen = async (
    messageKey: string,
    action: (id: string | number) => Promise<unknown>,
    row: InventoryTransfer
  ) => {
    try {
      await options.confirm(
        t(messageKey),
        t('inventoryTransfers.prompt'),
        {
          confirmButtonText: t('inventoryTransfers.action.confirm'),
          cancelButtonText: t('inventoryTransfers.action.cancel'),
          type: 'warning'
        }
      )
      await action(row.id)
      options.onSuccess?.(t('inventoryTransfers.message.success'))
      await loadData()
    } catch (error: any) {
      if (error !== 'cancel') {
        options.onError?.(t('inventoryTransfers.message.failed'))
      }
    }
  }

  const handlePost = (row: InventoryTransfer) =>
    confirmThen('inventoryTransfers.message.postConfirm', options.postTransfer, row)

  const handleCancel = (row: InventoryTransfer) =>
    confirmThen('inventoryTransfers.message.cancelConfirm', options.cancelTransfer, row)

  const loadWarehouses = async () => {
    try {
      const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }
      const response = await options.getWarehouses(optionPageQuery)
      warehouses.value = response.records || []
    } catch {
      options.onError?.(t('inventoryTransfers.message.warehousesLoadFailed'))
    }
  }

  const loadProducts = async () => {
    try {
      const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }
      const response = await options.getProducts(optionPageQuery)
      products.value = response.records || []
    } catch {
      options.onError?.(t('inventoryTransfers.message.productsLoadFailed'))
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
    handlePageChange,
    handlePost,
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
