import { reactive, ref } from 'vue'

import type {
  InventoryReplenishmentSuggestion,
  InventoryReplenishmentSuggestionQuery
} from '@/api/inventory'
import type { Product, Supplier, Warehouse } from '@/api/masterdata'
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
type Prompt = (
  message: string,
  title: string,
  options?: {
    confirmButtonText?: string
    cancelButtonText?: string
    inputType?: string
    inputPlaceholder?: string
  }
) => Promise<{ value: string }>

/**
 * Query, options, cancel and convert for replenishment suggestions.
 * Purchase-order navigation stays on the page so it can use the router.
 */
export const useInventoryReplenishmentList = (
  t: Translate,
  options: {
    getSuggestions: (
      params: InventoryReplenishmentSuggestionQuery
    ) => Promise<PageResponse<InventoryReplenishmentSuggestion>>
    getWarehouses: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<Warehouse>>
    getProducts: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<Product>>
    getSuppliers: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<Supplier>>
    cancelSuggestion: (id: string | number, reason?: string) => Promise<unknown>
    convertSuggestion: (id: string | number) => Promise<InventoryReplenishmentSuggestion>
    confirm: Confirm
    prompt: Prompt
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
  }
) => {
  const loading = ref(false)
  const tableData = ref<InventoryReplenishmentSuggestion[]>([])
  const total = ref(0)
  const warehouses = ref<Warehouse[]>([])
  const products = ref<Product[]>([])
  const suppliers = ref<Supplier[]>([])
  const createdRange = ref<[string, string]>()

  const queryParams = reactive<InventoryReplenishmentSuggestionQuery>({
    pageNo: 1,
    pageSize: 20,
    suggestionNo: '',
    status: '',
    warehouseId: undefined,
    productId: undefined,
    supplierId: undefined,
    createdTimeFrom: undefined,
    createdTimeTo: undefined
  })

  const loadData = async () => {
    loading.value = true
    try {
      const response = await options.getSuggestions({
        pageNo: queryParams.pageNo,
        pageSize: queryParams.pageSize,
        suggestionNo: queryParams.suggestionNo || undefined,
        status: queryParams.status || undefined,
        warehouseId: queryParams.warehouseId,
        productId: queryParams.productId,
        supplierId: queryParams.supplierId,
        createdTimeFrom: queryParams.createdTimeFrom,
        createdTimeTo: queryParams.createdTimeTo
      })
      tableData.value = response.records || []
      total.value = response.total || 0
    } catch {
      options.onError?.(t('inventoryReplenishment.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const loadOptions = async () => {
    try {
      // Keep the shared ACTIVE option-page contract used across inventory pages.
      const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }
      const [warehousePage, productPage, supplierPage] = await Promise.all([
        options.getWarehouses(optionPageQuery),
        options.getProducts(optionPageQuery),
        options.getSuppliers(optionPageQuery)
      ])
      warehouses.value = warehousePage.records || []
      products.value = productPage.records || []
      suppliers.value = supplierPage.records || []
    } catch {
      options.onWarning?.(t('inventoryReplenishment.message.optionsLoadFailed'))
    }
  }

  const handleQuery = async () => {
    queryParams.pageNo = 1
    await loadData()
  }

  const handleReset = async () => {
    Object.assign(queryParams, {
      pageNo: 1,
      pageSize: 20,
      suggestionNo: '',
      status: '',
      warehouseId: undefined,
      productId: undefined,
      supplierId: undefined,
      createdTimeFrom: undefined,
      createdTimeTo: undefined
    })
    createdRange.value = undefined
    await loadData()
  }

  const handlePageChange = async (page: number) => {
    queryParams.pageNo = page
    await loadData()
  }

  const handleSizeChange = async (size: number) => {
    queryParams.pageSize = size
    queryParams.pageNo = 1
    await loadData()
  }

  const handleCreatedRangeChange = (value: [string, string] | null) => {
    queryParams.createdTimeFrom = value?.[0]
    queryParams.createdTimeTo = value?.[1]
  }

  const handleCancel = async (row: InventoryReplenishmentSuggestion) => {
    try {
      const { value } = await options.prompt(
        t('inventoryReplenishment.message.cancelConfirm', { no: row.suggestionNo }),
        t('inventoryReplenishment.message.cancelTitle'),
        {
          confirmButtonText: t('inventoryReplenishment.message.confirm'),
          cancelButtonText: t('inventoryReplenishment.cancel'),
          inputType: 'textarea',
          inputPlaceholder: t('inventoryReplenishment.message.cancelReason')
        }
      )
      await options.cancelSuggestion(row.id, value || undefined)
      options.onSuccess?.(t('inventoryReplenishment.message.cancelled'))
      await loadData()
      return true
    } catch (error) {
      if (error !== 'cancel') {
        options.onError?.(t('inventoryReplenishment.message.cancelFailed'))
      }
      return false
    }
  }

  const handleConvert = async (row: InventoryReplenishmentSuggestion) => {
    if (!row.supplierId) {
      options.onWarning?.(t('inventoryReplenishment.message.supplierRequired'))
      return false
    }
    try {
      await options.confirm(
        t('inventoryReplenishment.message.convertConfirm', { no: row.suggestionNo }),
        t('inventoryReplenishment.message.convertTitle'),
        {
          confirmButtonText: t('inventoryReplenishment.message.confirm'),
          cancelButtonText: t('inventoryReplenishment.cancel'),
          type: 'warning'
        }
      )
      const response = await options.convertSuggestion(row.id)
      options.onSuccess?.(
        t('inventoryReplenishment.message.converted', { no: response.purchaseOrderNo })
      )
      await loadData()
      return true
    } catch (error) {
      if (error !== 'cancel') {
        options.onError?.(t('inventoryReplenishment.message.convertFailed'))
      }
      return false
    }
  }

  return {
    createdRange,
    handleCancel,
    handleConvert,
    handleCreatedRangeChange,
    handlePageChange,
    handleQuery,
    handleReset,
    handleSizeChange,
    loadData,
    loadOptions,
    loading,
    products,
    queryParams,
    suppliers,
    tableData,
    total,
    warehouses
  }
}
