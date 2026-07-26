import { reactive, ref } from 'vue'

import type {
  PurchaseOrder,
  PurchaseReceipt,
  PurchaseReceiptQuery
} from '@/api/purchase'
import type { Location, Supplier, Warehouse } from '@/api/masterdata'
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

export const usePurchaseReceiptList = (
  t: Translate,
  options: {
    getReceipts: (params: PurchaseReceiptQuery) => Promise<PageResponse<PurchaseReceipt>>
    getReceipt: (id: string | number) => Promise<PurchaseReceipt>
    getOrder: (id: string | number) => Promise<PurchaseOrder>
    completeReceipt: (id: string | number) => Promise<unknown>
    cancelReceipt: (id: string | number) => Promise<unknown>
    exportReceipts: (params: PurchaseReceiptQuery) => Promise<Blob>
    getWarehouses: (params: PageQuery) => Promise<PageResponse<Warehouse>>
    getSuppliers: (params: PageQuery) => Promise<PageResponse<Supplier>>
    getLocations: (params: PageQuery & { warehouseId?: string | number }) => Promise<PageResponse<Location>>
    printReceipt: (receipt: PurchaseReceipt) => void
    downloadBlob: (blob: Blob, filename: string) => void
    confirm: Confirm
    initialReceiptNo?: string
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
  }
) => {
  const queryForm = reactive<PurchaseReceiptQuery>({
    pageNo: 1,
    pageSize: 20,
    receiptNo: options.initialReceiptNo || '',
    orderId: undefined,
    supplierId: undefined,
    status: '',
    startDate: '',
    endDate: ''
  })
  const dateRange = ref<[string, string]>()
  const tableData = ref<PurchaseReceipt[]>([])
  const total = ref(0)
  const loading = ref(false)
  const detailVisible = ref(false)
  const currentRow = ref<PurchaseReceipt>()
  const linkedOrderVisible = ref(false)
  const linkedOrderLoading = ref(false)
  const linkedOrder = ref<PurchaseOrder>()
  const warehouses = ref<Warehouse[]>([])
  const locations = ref<Location[]>([])
  const suppliers = ref<Supplier[]>([])

  const handleQuery = async () => {
    loading.value = true
    try {
      const res = await options.getReceipts(queryForm)
      tableData.value = res.records || []
      total.value = res.total || 0
    } catch {
      options.onError?.(t('purchaseReceipt.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const handleReset = () => {
    queryForm.receiptNo = ''
    queryForm.orderId = undefined
    queryForm.supplierId = undefined
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

  const handleView = (row: PurchaseReceipt) => {
    currentRow.value = row
    detailVisible.value = true
  }

  const handlePrint = async (row: PurchaseReceipt) => {
    try {
      const detail = await options.getReceipt(row.id)
      options.printReceipt(detail)
    } catch {
      options.onError?.(t('purchaseReceipt.message.printLoadFailed'))
    }
  }

  const viewOrder = async (orderId: string | number) => {
    if (!orderId) {
      options.onWarning?.(t('purchaseReceipt.message.missingOrderId'))
      return
    }

    linkedOrderVisible.value = true
    linkedOrder.value = undefined
    linkedOrderLoading.value = true
    try {
      linkedOrder.value = await options.getOrder(orderId)
    } catch {
      options.onError?.(t('purchaseReceipt.message.orderDetailLoadFailed'))
      linkedOrderVisible.value = false
    } finally {
      linkedOrderLoading.value = false
    }
  }

  const handleComplete = async (row: PurchaseReceipt) => {
    try {
      await options.confirm(
        t('purchaseReceipt.message.postConfirm', { receiptNo: row.receiptNo }),
        t('purchaseReceipt.message.prompt'),
        {
          confirmButtonText: t('common.confirm'),
          cancelButtonText: t('common.cancel'),
          type: 'success'
        }
      )
      await options.completeReceipt(row.id)
      options.onSuccess?.(t('purchaseReceipt.message.posted'))
      await handleQuery()
    } catch (error: any) {
      if (error !== 'cancel') {
        options.onError?.(t('purchaseReceipt.message.postFailed'))
      }
    }
  }

  const handleCancel = async (row: PurchaseReceipt) => {
    try {
      await options.confirm(
        t('purchaseReceipt.message.cancelConfirm', { receiptNo: row.receiptNo }),
        t('purchaseReceipt.message.prompt'),
        {
          confirmButtonText: t('common.confirm'),
          cancelButtonText: t('common.cancel'),
          type: 'warning'
        }
      )
      await options.cancelReceipt(row.id)
      options.onSuccess?.(t('purchaseReceipt.message.cancelled'))
      await handleQuery()
    } catch (error: any) {
      if (error !== 'cancel') {
        options.onError?.(t('purchaseReceipt.message.cancelFailed'))
      }
    }
  }

  const handleExport = async () => {
    try {
      const blob = await options.exportReceipts(queryForm)
      options.downloadBlob(blob, t('purchaseReceipt.message.exportFile', { timestamp: Date.now() }))
      options.onSuccess?.(t('purchaseReceipt.message.exported'))
    } catch {
      options.onError?.(t('purchaseReceipt.message.exportFailed'))
    }
  }

  const loadWarehouses = async () => {
    const response = await options.getWarehouses({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
    warehouses.value = response.records || []
  }

  const loadSuppliers = async () => {
    const response = await options.getSuppliers({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
    suppliers.value = response.records || []
  }

  const loadLocations = async (warehouseId?: string | number) => {
    try {
      const page = await options.getLocations({
        pageNo: 1,
        pageSize: 500,
        status: 'ACTIVE',
        warehouseId: warehouseId || undefined
      })
      locations.value = page.records || []
    } catch {
      locations.value = []
    }
  }

  const loadOptions = async () => {
    await Promise.all([
      loadWarehouses().catch(() => {
        options.onError?.(t('purchaseReceipt.message.warehousesLoadFailed'))
      }),
      loadSuppliers().catch(() => {
        options.onError?.(t('purchaseReceipt.message.suppliersLoadFailed'))
      }),
      loadLocations().catch(() => undefined)
    ])
  }

  return {
    currentRow,
    dateRange,
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
    linkedOrder,
    linkedOrderLoading,
    linkedOrderVisible,
    loadLocations,
    loadOptions,
    loading,
    locations,
    queryForm,
    suppliers,
    tableData,
    total,
    viewOrder,
    warehouses
  }
}
