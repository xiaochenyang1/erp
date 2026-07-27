import { reactive, ref } from 'vue'

import type {
  FinanceInvoice,
  FinanceInvoiceQuery
} from '@/api/finance'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (message: string, title: string, options?: { type?: string }) => Promise<unknown>

/**
 * Finance invoice list: filters, paging, print, confirm (post) and void.
 */
export const useFinanceInvoiceList = (
  t: Translate,
  options: {
    getFinanceInvoices: (params: FinanceInvoiceQuery) => Promise<PageResponse<FinanceInvoice>>
    getFinanceInvoice: (id: string | number) => Promise<FinanceInvoice>
    postFinanceInvoice: (id: string | number) => Promise<unknown>
    cancelFinanceInvoice: (id: string | number) => Promise<unknown>
    printFinanceInvoice: (invoice: FinanceInvoice) => void
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const queryForm = reactive({
    status: '',
    invoiceType: '',
    partnerName: '',
    dateFrom: '',
    dateTo: ''
  })
  const dateRange = ref<string[]>([])
  const pagination = reactive({
    pageNo: 1,
    pageSize: 20,
    total: 0
  })
  const loading = ref(false)
  const tableData = ref<FinanceInvoice[]>([])

  const loadData = async () => {
    loading.value = true
    try {
      const res = await options.getFinanceInvoices({
        pageNo: pagination.pageNo,
        pageSize: pagination.pageSize,
        status: queryForm.status || undefined,
        invoiceType: queryForm.invoiceType || undefined,
        partnerName: queryForm.partnerName || undefined,
        dateFrom: queryForm.dateFrom || undefined,
        dateTo: queryForm.dateTo || undefined
      })
      tableData.value = res.records || []
      pagination.total = res.total || 0
      return true
    } catch {
      options.onError?.(t('financeReportPages.invoices.message.loadFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  const applyDateRange = () => {
    if (dateRange.value?.length === 2) {
      queryForm.dateFrom = dateRange.value[0]
      queryForm.dateTo = dateRange.value[1]
    } else {
      queryForm.dateFrom = ''
      queryForm.dateTo = ''
    }
  }

  const handleQuery = () => {
    applyDateRange()
    pagination.pageNo = 1
    return loadData()
  }

  const handleReset = () => {
    Object.assign(queryForm, {
      status: '',
      invoiceType: '',
      partnerName: '',
      dateFrom: '',
      dateTo: ''
    })
    dateRange.value = []
    pagination.pageNo = 1
    return loadData()
  }

  const handlePageChange = (page: number) => {
    pagination.pageNo = page
    return loadData()
  }

  const handleSizeChange = (size: number) => {
    pagination.pageSize = size
    pagination.pageNo = 1
    return loadData()
  }

  const handlePrint = async (row: FinanceInvoice) => {
    try {
      const detail = await options.getFinanceInvoice(row.id)
      options.printFinanceInvoice(detail)
      return true
    } catch {
      options.onError?.(t('financeReportPages.invoices.message.printLoadFailed'))
      return false
    }
  }

  const confirmThen = async (
    action: (id: string | number) => Promise<unknown>,
    row: FinanceInvoice,
    keys: { confirm: string; success: string; failed: string }
  ) => {
    try {
      await options.confirm(
        t(keys.confirm, { no: row.invoiceNo }),
        t('financeReportPages.common.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    try {
      await action(row.id)
      options.onSuccess?.(t(keys.success))
      await loadData()
      return true
    } catch {
      options.onError?.(t(keys.failed))
      return false
    }
  }

  const handlePost = (row: FinanceInvoice) =>
    confirmThen(options.postFinanceInvoice, row, {
      confirm: 'financeReportPages.invoices.message.postConfirm',
      success: 'financeReportPages.invoices.message.posted',
      failed: 'financeReportPages.invoices.message.postFailed'
    })

  const handleCancel = (row: FinanceInvoice) =>
    confirmThen(options.cancelFinanceInvoice, row, {
      confirm: 'financeReportPages.invoices.message.cancelConfirm',
      success: 'financeReportPages.invoices.message.cancelled',
      failed: 'financeReportPages.invoices.message.cancelFailed'
    })

  return {
    dateRange,
    handleCancel,
    handlePageChange,
    handlePost,
    handlePrint,
    handleQuery,
    handleReset,
    handleSizeChange,
    loadData,
    loading,
    pagination,
    queryForm,
    tableData
  }
}
