import { reactive, ref } from 'vue'

import type {
  PurchaseReceipt,
  PurchaseReturn,
  PurchaseReturnQuery
} from '@/api/purchase'
import type { Location, Product } from '@/api/masterdata'
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
type PageQuery = {
  pageNo?: number
  pageSize?: number
  status?: string
}

export const usePurchaseReturnList = (
  t: Translate,
  options: {
    getReturns: (params: PurchaseReturnQuery) => Promise<PageResponse<PurchaseReturn>>
    getReturn: (id: string | number) => Promise<PurchaseReturn>
    getReceipt: (id: string | number) => Promise<PurchaseReceipt>
    getReceipts: (params: PageQuery) => Promise<PageResponse<PurchaseReceipt>>
    postReturn: (id: string | number) => Promise<unknown>
    cancelReturn: (id: string | number) => Promise<unknown>
    exportReturns: (params: PurchaseReturnQuery) => Promise<Blob>
    getProducts: (params: PageQuery) => Promise<PageResponse<Product>>
    getLocations: (params: PageQuery & { warehouseId?: string | number }) => Promise<PageResponse<Location>>
    printReturn: (doc: PurchaseReturn) => void
    downloadBlob: (blob: Blob, filename: string) => void
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
  }
) => {
  const queryForm = reactive<PurchaseReturnQuery>({
    pageNo: 1,
    pageSize: 20,
    returnNo: '',
    receiptId: undefined,
    status: '',
    startDate: '',
    endDate: ''
  })
  const dateRange = ref<[string, string]>()
  const tableData = ref<PurchaseReturn[]>([])
  const total = ref(0)
  const loading = ref(false)
  const detailVisible = ref(false)
  const detailLoading = ref(false)
  const currentRow = ref<PurchaseReturn>()
  const linkedReceiptVisible = ref(false)
  const linkedReceiptLoading = ref(false)
  const linkedReceipt = ref<PurchaseReceipt>()
  const availableReceipts = ref<PurchaseReceipt[]>([])
  const products = ref<Product[]>([])
  const locations = ref<Location[]>([])

  const handleQuery = async () => {
    loading.value = true
    try {
      const res = await options.getReturns(queryForm)
      tableData.value = res.records || []
      total.value = res.total || 0
    } catch {
      options.onError?.(t('purchaseReturn.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const handleReset = () => {
    queryForm.returnNo = ''
    queryForm.receiptId = undefined
    queryForm.status = ''
    queryForm.startDate = ''
    queryForm.endDate = ''
    queryForm.pageNo = 1
    dateRange.value = undefined
    void handleQuery()
  }

  const handleDateChange = (dates: [string, string] | null) => {
    if (dates) {
      queryForm.startDate = dates[0]
      queryForm.endDate = dates[1]
    } else {
      queryForm.startDate = ''
      queryForm.endDate = ''
    }
  }

  const handlePageChange = (page: number, size: number) => {
    queryForm.pageNo = page
    queryForm.pageSize = size
    void handleQuery()
  }

  const handleView = async (row: PurchaseReturn) => {
    detailVisible.value = true
    currentRow.value = undefined
    detailLoading.value = true
    try {
      currentRow.value = await options.getReturn(row.id)
    } catch {
      options.onError?.(t('purchaseReturn.message.detailLoadFailed'))
      detailVisible.value = false
    } finally {
      detailLoading.value = false
    }
  }

  const handlePrint = async (row: PurchaseReturn) => {
    try {
      const detail = await options.getReturn(row.id)
      options.printReturn(detail)
    } catch {
      options.onError?.(t('purchaseReturn.message.printLoadFailed'))
    }
  }

  const viewReceipt = async (receiptId: string | number) => {
    if (!receiptId) {
      options.onWarning?.(t('purchaseReturn.message.missingReceiptId'))
      return
    }
    linkedReceiptVisible.value = true
    linkedReceipt.value = undefined
    linkedReceiptLoading.value = true
    try {
      linkedReceipt.value = await options.getReceipt(receiptId)
    } catch {
      options.onError?.(t('purchaseReturn.message.receiptDetailLoadFailed'))
      linkedReceiptVisible.value = false
    } finally {
      linkedReceiptLoading.value = false
    }
  }

  const handleComplete = async (row: PurchaseReturn) => {
    try {
      await options.confirm(
        t('purchaseReturn.message.postConfirm'),
        t('purchaseReturn.message.prompt'),
        {
          confirmButtonText: t('common.confirm'),
          cancelButtonText: t('common.cancel'),
          type: 'success'
        }
      )
      await options.postReturn(row.id)
      options.onSuccess?.(t('purchaseReturn.message.posted'))
      await handleQuery()
    } catch (error: any) {
      if (error !== 'cancel') {
        options.onError?.(t('purchaseReturn.message.postFailed'))
      }
    }
  }

  const handleCancel = async (row: PurchaseReturn) => {
    try {
      await options.confirm(
        t('purchaseReturn.message.cancelConfirm', { returnNo: row.returnNo }),
        t('purchaseReturn.message.prompt'),
        {
          confirmButtonText: t('common.confirm'),
          cancelButtonText: t('common.cancel'),
          type: 'warning'
        }
      )
      await options.cancelReturn(row.id)
      options.onSuccess?.(t('purchaseReturn.message.cancelled'))
      await handleQuery()
    } catch (error: any) {
      if (error !== 'cancel') {
        options.onError?.(t('purchaseReturn.message.cancelFailed'))
      }
    }
  }

  const handleExport = async () => {
    try {
      const blob = await options.exportReturns(queryForm)
      options.downloadBlob(blob, t('purchaseReturn.message.exportFile', { timestamp: Date.now() }))
      options.onSuccess?.(t('purchaseReturn.message.exported'))
    } catch {
      options.onError?.(t('purchaseReturn.message.exportFailed'))
    }
  }

  const loadOptions = async () => {
    try {
      const page = await options.getLocations({ pageNo: 1, pageSize: 500, status: 'ACTIVE' })
      locations.value = page.records || []
    } catch {
      locations.value = []
    }
  }

  const loadCreateOptions = async () => {
    const receiptPageQuery = { pageNo: 1, pageSize: 200, status: 'POSTED' }
    const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }
    const [res, productResponse, locationResponse] = await Promise.all([
      options.getReceipts(receiptPageQuery),
      options.getProducts(optionPageQuery),
      options.getLocations({ pageNo: 1, pageSize: 500, status: 'ACTIVE' })
    ])
    availableReceipts.value = res.records || []
    products.value = productResponse.records || []
    locations.value = locationResponse.records || []
    return availableReceipts.value
  }

  return {
    availableReceipts,
    currentRow,
    dateRange,
    detailLoading,
    detailVisible,
    handleCancel,
    handleComplete,
    handleDateChange,
    handleExport,
    handlePageChange,
    handlePrint,
    handleQuery,
    handleReset,
    handleView,
    linkedReceipt,
    linkedReceiptLoading,
    linkedReceiptVisible,
    loadCreateOptions,
    loadOptions,
    loading,
    locations,
    products,
    queryForm,
    tableData,
    total,
    viewReceipt
  }
}
