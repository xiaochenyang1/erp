import { reactive, ref } from 'vue'

import type {
  SalesDelivery,
  SalesReturn,
  SalesReturnQuery
} from '@/api/sales'
import type { Location, Product } from '@/api/masterdata'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title?: string,
  options?: {
    confirmButtonText?: string
    cancelButtonText?: string
    type?: string
  }
) => Promise<unknown>
type PageQuery = {
  pageNo?: number
  pageSize?: number
  status?: string
}

export const useSalesReturnList = (
  t: Translate,
  options: {
    getReturns: (params: SalesReturnQuery) => Promise<PageResponse<SalesReturn>>
    getReturn: (id: string | number) => Promise<SalesReturn>
    postReturn: (id: string | number) => Promise<unknown>
    cancelReturn: (id: string | number) => Promise<unknown>
    getDeliveries: (params: PageQuery) => Promise<PageResponse<SalesDelivery>>
    getProducts: (params: PageQuery) => Promise<PageResponse<Product>>
    getLocations: (params: PageQuery) => Promise<PageResponse<Location>>
    printReturn: (doc: SalesReturn) => void
    confirm: Confirm
    enrichReturnRow?: (row: SalesReturn) => SalesReturn
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const queryParams = reactive<SalesReturnQuery>({
    pageNo: 1,
    pageSize: 10,
    returnNo: '',
    deliveryId: undefined,
    status: '',
    startDate: '',
    endDate: ''
  })
  const dateRange = ref<[string, string] | null>(null)
  const loading = ref(false)
  const tableData = ref<SalesReturn[]>([])
  const total = ref(0)
  const deliveries = ref<SalesDelivery[]>([])
  const products = ref<Product[]>([])
  const locations = ref<Location[]>([])

  const loadData = async () => {
    loading.value = true
    try {
      const response = await options.getReturns(queryParams)
      tableData.value = (response.records || []).map((item) =>
        options.enrichReturnRow ? options.enrichReturnRow(item) : item
      )
      total.value = response.total || 0
    } catch {
      options.onError?.(t('salesReturnOps.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const loadDeliveries = async () => {
    try {
      const response = await options.getDeliveries({ pageNo: 1, pageSize: 200, status: 'POSTED' })
      deliveries.value = response.records || []
    } catch {
      options.onError?.(t('salesReturnOps.message.deliveriesLoadFailed'))
    }
  }

  const loadProducts = async () => {
    try {
      const response = await options.getProducts({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
      products.value = response.records || []
    } catch {
      options.onError?.(t('salesReturnOps.message.productsLoadFailed'))
    }
  }

  const loadLocations = async () => {
    try {
      const page = await options.getLocations({ pageNo: 1, pageSize: 500, status: 'ACTIVE' })
      locations.value = page.records || []
    } catch {
      locations.value = []
    }
  }

  const loadOptions = async () => {
    await Promise.all([loadDeliveries(), loadProducts(), loadLocations()])
  }

  const handleQuery = () => {
    if (dateRange.value) {
      queryParams.startDate = dateRange.value[0]
      queryParams.endDate = dateRange.value[1]
    } else {
      queryParams.startDate = ''
      queryParams.endDate = ''
    }
    queryParams.pageNo = 1
    void loadData()
  }

  const handleReset = () => {
    queryParams.returnNo = ''
    queryParams.deliveryId = undefined
    queryParams.status = ''
    dateRange.value = null
    queryParams.startDate = ''
    queryParams.endDate = ''
    handleQuery()
  }

  const handlePrint = async (row: SalesReturn) => {
    try {
      const detail = await options.getReturn(row.id)
      options.printReturn(detail)
    } catch {
      options.onError?.(t('salesReturnOps.message.printLoadFailed'))
    }
  }

  const handleCancel = async (row: SalesReturn) => {
    try {
      await options.confirm(
        t('salesReturnOps.message.cancelConfirm'),
        t('salesReturnOps.prompt'),
        {
          confirmButtonText: t('salesReturnOps.action.confirm'),
          cancelButtonText: t('salesReturnOps.action.cancel'),
          type: 'warning'
        }
      )
      await options.cancelReturn(row.id)
      options.onSuccess?.(t('salesReturnOps.message.success'))
      await loadData()
    } catch (error: any) {
      if (error !== 'cancel') {
        options.onError?.(t('salesReturnOps.message.failed'))
      }
    }
  }

  const handlePost = async (row: SalesReturn) => {
    try {
      await options.confirm(
        t('salesReturnOps.message.postConfirm'),
        t('salesReturnOps.prompt'),
        {
          confirmButtonText: t('salesReturnOps.action.confirm'),
          cancelButtonText: t('salesReturnOps.action.cancel'),
          type: 'warning'
        }
      )
      await options.postReturn(row.id)
      options.onSuccess?.(t('salesReturnOps.message.success'))
      await loadData()
    } catch (error: any) {
      if (error !== 'cancel') {
        options.onError?.(t('salesReturnOps.message.failed'))
      }
    }
  }

  return {
    dateRange,
    deliveries,
    handleCancel,
    handlePost,
    handlePrint,
    handleQuery,
    handleReset,
    loadData,
    loadOptions,
    loading,
    locations,
    products,
    queryParams,
    tableData,
    total
  }
}
