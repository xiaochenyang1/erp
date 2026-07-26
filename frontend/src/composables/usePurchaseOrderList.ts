import { reactive, ref } from 'vue'

import type {
  PurchaseOrder,
  PurchaseOrderQuery,
  PurchaseOrderTrace
} from '@/api/purchase'
import type { Product, Supplier } from '@/api/masterdata'
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
    inputPattern?: RegExp
    inputErrorMessage?: string
  }
) => Promise<unknown>
type Prompt = (
  message: string,
  title: string,
  options?: {
    confirmButtonText?: string
    cancelButtonText?: string
    inputPattern?: RegExp
    inputErrorMessage?: string
  }
) => Promise<{ value: string }>
type PageQuery = {
  pageNo?: number
  pageSize?: number
  status?: string
}

export const usePurchaseOrderList = (
  t: Translate,
  options: {
    getOrders: (params: PurchaseOrderQuery) => Promise<PageResponse<PurchaseOrder>>
    getOrder: (id: string | number) => Promise<PurchaseOrder>
    submitOrder: (id: string | number) => Promise<unknown>
    approveOrder: (id: string | number) => Promise<unknown>
    unapproveOrder: (id: string | number) => Promise<unknown>
    rejectOrder: (id: string | number, reason: string) => Promise<unknown>
    cancelOrder: (id: string | number) => Promise<unknown>
    closeOrder: (id: string | number) => Promise<unknown>
    traceOrder: (id: string | number) => Promise<PurchaseOrderTrace>
    exportOrders: (params: PurchaseOrderQuery) => Promise<Blob>
    getSuppliers: (params: PageQuery) => Promise<PageResponse<Supplier>>
    getProducts: (params: PageQuery) => Promise<PageResponse<Product>>
    printOrder: (order: PurchaseOrder) => void
    downloadBlob: (blob: Blob, filename: string) => void
    confirm: Confirm
    prompt: Prompt
    initialOrderNo?: string
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const queryForm = reactive<PurchaseOrderQuery>({
    pageNo: 1,
    pageSize: 20,
    orderNo: options.initialOrderNo || '',
    supplierId: undefined,
    status: '',
    startDate: '',
    endDate: ''
  })
  const dateRange = ref<[string, string]>()
  const tableData = ref<PurchaseOrder[]>([])
  const total = ref(0)
  const loading = ref(false)
  const suppliers = ref<Supplier[]>([])
  const products = ref<Product[]>([])
  const detailVisible = ref(false)
  const currentRow = ref<PurchaseOrder>()
  const traceVisible = ref(false)
  const purchaseTrace = ref<PurchaseOrderTrace>()

  const handleQuery = async () => {
    loading.value = true
    try {
      const res = await options.getOrders(queryForm)
      tableData.value = res.records || []
      total.value = res.total || 0
    } catch {
      options.onError?.(t('purchaseOrder.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const handleReset = () => {
    queryForm.orderNo = ''
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

  const handleView = (row: PurchaseOrder) => {
    currentRow.value = row
    detailVisible.value = true
  }

  const handlePrint = async (row: PurchaseOrder) => {
    try {
      const detail = await options.getOrder(row.id)
      options.printOrder(detail)
    } catch {
      options.onError?.(t('purchaseOrder.message.printLoadFailed'))
    }
  }

  const handleTraceOrder = async (row: PurchaseOrder) => {
    try {
      purchaseTrace.value = await options.traceOrder(row.id)
      traceVisible.value = true
    } catch {
      options.onError?.(t('purchaseOrder.message.traceLoadFailed'))
    }
  }

  const withConfirmAction = async (
    row: PurchaseOrder,
    messageKey: string,
    action: (id: string | number) => Promise<unknown>,
    successKey: string,
    failedKey: string,
    type: string
  ) => {
    try {
      await options.confirm(
        t(messageKey, { orderNo: row.orderNo }),
        t('purchaseOrder.message.prompt'),
        {
          confirmButtonText: t('common.confirm'),
          cancelButtonText: t('common.cancel'),
          type
        }
      )
      await action(row.id)
      options.onSuccess?.(t(successKey))
      await handleQuery()
    } catch (error: any) {
      if (error !== 'cancel') {
        options.onError?.(t(failedKey))
      }
    }
  }

  const handleCancelOrder = (row: PurchaseOrder) =>
    withConfirmAction(
      row,
      'purchaseOrder.message.cancelConfirm',
      options.cancelOrder,
      'purchaseOrder.message.cancelled',
      'purchaseOrder.message.cancelFailed',
      'warning'
    )

  const handleCloseOrder = (row: PurchaseOrder) =>
    withConfirmAction(
      row,
      'purchaseOrder.message.closeConfirm',
      options.closeOrder,
      'purchaseOrder.message.closed',
      'purchaseOrder.message.closeFailed',
      'warning'
    )

  const handleSubmit = (row: PurchaseOrder) =>
    withConfirmAction(
      row,
      'purchaseOrder.message.submitConfirm',
      options.submitOrder,
      'purchaseOrder.message.submitted',
      'purchaseOrder.message.submitFailed',
      'info'
    )

  const handleApprove = (row: PurchaseOrder) =>
    withConfirmAction(
      row,
      'purchaseOrder.message.approveConfirm',
      options.approveOrder,
      'purchaseOrder.message.approved',
      'purchaseOrder.message.approveFailed',
      'success'
    )

  const handleUnapprove = (row: PurchaseOrder) =>
    withConfirmAction(
      row,
      'purchaseOrder.message.unapproveConfirm',
      options.unapproveOrder,
      'purchaseOrder.message.unapproved',
      'purchaseOrder.message.unapproveFailed',
      'warning'
    )

  const handleReject = async (row: PurchaseOrder) => {
    try {
      const { value: reason } = await options.prompt(
        t('purchaseOrder.validation.rejectReason'),
        t('purchaseOrder.message.rejectTitle'),
        {
          confirmButtonText: t('common.confirm'),
          cancelButtonText: t('common.cancel'),
          inputPattern: /.+/,
          inputErrorMessage: t('purchaseOrder.validation.rejectReason')
        }
      )
      await options.rejectOrder(row.id, reason)
      options.onSuccess?.(t('purchaseOrder.message.rejected'))
      await handleQuery()
    } catch (error: any) {
      if (error !== 'cancel') {
        options.onError?.(t('purchaseOrder.message.actionFailed'))
      }
    }
  }

  const handleExport = async () => {
    try {
      const blob = await options.exportOrders(queryForm)
      options.downloadBlob(blob, t('purchaseOrder.message.exportFile', { timestamp: Date.now() }))
      options.onSuccess?.(t('purchaseOrder.message.exported'))
    } catch {
      options.onError?.(t('purchaseOrder.message.exportFailed'))
    }
  }

  const loadOptions = async () => {
    await Promise.all([
      options.getSuppliers({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
        .then((response) => {
          suppliers.value = response.records || []
        })
        .catch(() => {
          options.onError?.(t('purchaseOrder.message.suppliersLoadFailed'))
        }),
      options.getProducts({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
        .then((response) => {
          products.value = response.records || []
        })
        .catch(() => {
          options.onError?.(t('purchaseOrder.message.productsLoadFailed'))
        })
    ])
  }

  return {
    currentRow,
    dateRange,
    detailVisible,
    handleApprove,
    handleCancelOrder,
    handleCloseOrder,
    handleDateChange,
    handleExport,
    handlePageChange,
    handlePrint,
    handleQuery,
    handleReject,
    handleReset,
    handleSubmit,
    handleTraceOrder,
    handleUnapprove,
    handleView,
    loadOptions,
    loading,
    products,
    purchaseTrace,
    queryForm,
    suppliers,
    tableData,
    total,
    traceVisible
  }
}
