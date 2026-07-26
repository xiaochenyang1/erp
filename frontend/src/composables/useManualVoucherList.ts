import { reactive, ref } from 'vue'

import type {
  AccountSubject,
  ManualVoucher,
  ManualVoucherQuery
} from '@/api/finance'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title: string,
  options?: { type?: string; confirmButtonClass?: string }
) => Promise<unknown>
type OptionPageQuery = { pageNo: number; pageSize: number; status: string }

export const useManualVoucherList = (
  t: Translate,
  options: {
    getVouchers: (params: ManualVoucherQuery) => Promise<PageResponse<ManualVoucher>>
    getVoucher: (id: string | number) => Promise<ManualVoucher>
    getSubjects: (params: OptionPageQuery) => Promise<PageResponse<AccountSubject>>
    submitVoucher: (id: string | number) => Promise<unknown>
    approveVoucher: (id: string | number) => Promise<unknown>
    postVoucher: (id: string | number) => Promise<unknown>
    deleteVoucher: (id: string | number) => Promise<unknown>
    cancelVoucher: (id: string | number, reason: string) => Promise<unknown>
    rejectVoucher: (id: string | number, reason: string) => Promise<unknown>
    printVoucher: (doc: Record<string, unknown>) => void
    /** Print needs the localized status/source labels the page owns. */
    decoratePrint: (doc: ManualVoucher) => Record<string, unknown>
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
  }
) => {
  const queryForm = reactive<ManualVoucherQuery>({
    pageNo: 1,
    pageSize: 20,
    voucherNo: '',
    status: '',
    dateFrom: '',
    dateTo: ''
  })

  const loading = ref(false)
  const tableData = ref<ManualVoucher[]>([])
  const total = ref(0)
  const subjects = ref<AccountSubject[]>([])

  const detailVisible = ref(false)
  const currentVoucher = ref<ManualVoucher | null>(null)

  const cancelVisible = ref(false)
  const cancelling = ref(false)
  const cancelReason = ref('')
  const cancellingRow = ref<ManualVoucher | null>(null)

  const rejectVisible = ref(false)
  const rejecting = ref(false)
  const rejectReason = ref('')
  const rejectingRow = ref<ManualVoucher | null>(null)

  const loadData = async () => {
    loading.value = true
    try {
      const response = await options.getVouchers(queryForm)
      tableData.value = response.records || []
      total.value = response.total || 0
    } catch {
      options.onError?.(t('financeReportPages.manualVouchers.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const loadSubjects = async () => {
    try {
      const response = await options.getSubjects({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
      subjects.value = response.records || []
    } catch {
      options.onWarning?.(t('financeReportPages.manualVouchers.message.subjectsLoadFailed'))
    }
  }

  /** A new filter set always returns to the first page. */
  const handleQuery = async () => {
    queryForm.pageNo = 1
    await loadData()
  }

  const handlePageChange = async () => {
    await loadData()
  }

  const handleReset = async () => {
    queryForm.voucherNo = ''
    queryForm.status = ''
    queryForm.dateFrom = ''
    queryForm.dateTo = ''
    await handleQuery()
  }

  const confirmThen = async (
    keys: { confirm: string; title: string; success: string; failed: string },
    action: (id: string | number) => Promise<unknown>,
    row: ManualVoucher,
    confirmOptions?: { confirmButtonClass?: string }
  ) => {
    try {
      await options.confirm(
        t(keys.confirm, { no: row.voucherNo }),
        t(keys.title),
        { type: 'warning', ...confirmOptions }
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

  const handleSubmit = (row: ManualVoucher) =>
    confirmThen({
      confirm: 'financeReportPages.manualVouchers.message.submitConfirm',
      title: 'financeReportPages.manualVouchers.message.submitTitle',
      success: 'financeReportPages.manualVouchers.message.submitted',
      failed: 'financeReportPages.manualVouchers.message.submitFailed'
    }, options.submitVoucher, row)

  const handleApprove = (row: ManualVoucher) =>
    confirmThen({
      confirm: 'financeReportPages.manualVouchers.message.approveConfirm',
      title: 'financeReportPages.manualVouchers.message.approveTitle',
      success: 'financeReportPages.manualVouchers.message.approved',
      failed: 'financeReportPages.manualVouchers.message.approveFailed'
    }, options.approveVoucher, row)

  const handlePost = (row: ManualVoucher) =>
    confirmThen({
      confirm: 'financeReportPages.manualVouchers.message.postConfirm',
      title: 'financeReportPages.manualVouchers.message.postTitle',
      success: 'financeReportPages.manualVouchers.message.posted',
      failed: 'financeReportPages.manualVouchers.message.postFailed'
    }, options.postVoucher, row)

  const handleDelete = (row: ManualVoucher) =>
    confirmThen({
      confirm: 'financeReportPages.manualVouchers.message.deleteConfirm',
      title: 'financeReportPages.manualVouchers.message.deleteTitle',
      success: 'financeReportPages.manualVouchers.message.deleted',
      failed: 'financeReportPages.manualVouchers.message.deleteFailed'
    }, options.deleteVoucher, row, { confirmButtonClass: 'el-button--danger' })

  const openCancel = (row: ManualVoucher) => {
    cancellingRow.value = row
    cancelReason.value = ''
    cancelVisible.value = true
  }

  /** Voiding a posted voucher generates a reversal, so a reason is mandatory. */
  const handleCancel = async () => {
    if (cancelling.value) return false
    const reason = cancelReason.value.trim()
    if (!cancellingRow.value || !reason) return false
    cancelling.value = true
    try {
      await options.cancelVoucher(cancellingRow.value.id, reason)
      options.onSuccess?.(t('financeReportPages.manualVouchers.message.cancelled'))
      cancelVisible.value = false
      await loadData()
      return true
    } catch {
      options.onError?.(t('financeReportPages.manualVouchers.message.cancelFailed'))
      return false
    } finally {
      cancelling.value = false
    }
  }

  const openReject = (row: ManualVoucher) => {
    rejectingRow.value = row
    rejectReason.value = ''
    rejectVisible.value = true
  }

  const handleReject = async () => {
    if (rejecting.value) return false
    const reason = rejectReason.value.trim()
    if (!rejectingRow.value || !reason) return false
    rejecting.value = true
    try {
      await options.rejectVoucher(rejectingRow.value.id, reason)
      options.onSuccess?.(t('financeReportPages.manualVouchers.message.rejected'))
      rejectVisible.value = false
      await loadData()
      return true
    } catch {
      options.onError?.(t('financeReportPages.manualVouchers.message.rejectFailed'))
      return false
    } finally {
      rejecting.value = false
    }
  }

  const openDetail = async (row: ManualVoucher) => {
    try {
      currentVoucher.value = await options.getVoucher(row.id)
      detailVisible.value = true
      return true
    } catch {
      options.onError?.(t('financeReportPages.manualVouchers.message.detailLoadFailed'))
      return false
    }
  }

  const handlePrint = async (row: ManualVoucher) => {
    try {
      const detail = await options.getVoucher(row.id)
      options.printVoucher(options.decoratePrint(detail))
      return true
    } catch {
      options.onError?.(t('financeReportPages.manualVouchers.message.printLoadFailed'))
      return false
    }
  }

  return {
    cancelReason,
    cancelVisible,
    cancelling,
    cancellingRow,
    currentVoucher,
    detailVisible,
    handleApprove,
    handleCancel,
    handleDelete,
    handlePageChange,
    handlePost,
    handlePrint,
    handleQuery,
    handleReject,
    handleReset,
    handleSubmit,
    loadData,
    loadSubjects,
    loading,
    openCancel,
    openDetail,
    openReject,
    queryForm,
    rejectReason,
    rejectVisible,
    rejecting,
    rejectingRow,
    subjects,
    tableData,
    total
  }
}
