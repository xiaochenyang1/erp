import { reactive, ref } from 'vue'

import type {
  AccountSubject,
  Expense,
  ExpenseReconciliation
} from '@/api/finance'
import type { PageResponse } from '@/types/common'
import { flattenSubjects } from './useExpensePresentation'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title: string,
  options?: { type?: string }
) => Promise<unknown>

export interface ExpenseQueryParams {
  pageNo: number
  pageSize: number
  status?: string
  dateFrom?: string
  dateTo?: string
}

export const useExpenseList = (
  t: Translate,
  options: {
    getExpenses: (params: ExpenseQueryParams) => Promise<PageResponse<Expense>>
    getExpense: (id: string | number) => Promise<Expense>
    getSubjectTree: () => Promise<AccountSubject[]>
    getReconciliation: (id: string | number) => Promise<ExpenseReconciliation>
    submitExpense: (id: string | number) => Promise<unknown>
    approveExpense: (id: string | number) => Promise<unknown>
    postExpense: (id: string | number) => Promise<unknown>
    reverseExpense: (id: string | number) => Promise<unknown>
    cancelExpense: (id: string | number) => Promise<unknown>
    printExpense: (doc: Expense) => void
    /** Print needs resolved account names, which only the page's options know. */
    decoratePrint?: (doc: Expense) => Expense
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const queryForm = reactive({
    status: '',
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
  const tableData = ref<Expense[]>([])
  const subjectOptions = ref<AccountSubject[]>([])

  const viewDialogVisible = ref(false)
  const viewData = ref<Expense>({} as Expense)

  const reconciliationDialogVisible = ref(false)
  const reconciliationLoading = ref(false)
  const reconciliationData = ref<ExpenseReconciliation>()

  const loadData = async () => {
    loading.value = true
    try {
      const response = await options.getExpenses({
        pageNo: pagination.pageNo,
        pageSize: pagination.pageSize,
        status: queryForm.status || undefined,
        dateFrom: queryForm.dateFrom || undefined,
        dateTo: queryForm.dateTo || undefined
      })
      tableData.value = response.records || []
      pagination.total = response.total || 0
    } catch {
      options.onError?.(t('financeReportPages.expenses.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const loadSubjects = async () => {
    try {
      const subjects = await options.getSubjectTree()
      subjectOptions.value = flattenSubjects(subjects || [])
    } catch {
      options.onError?.(t('financeReportPages.expenses.message.subjectsLoadFailed'))
    }
  }

  const handleQuery = async () => {
    if (dateRange.value?.length === 2) {
      queryForm.dateFrom = dateRange.value[0]
      queryForm.dateTo = dateRange.value[1]
    } else {
      queryForm.dateFrom = ''
      queryForm.dateTo = ''
    }
    pagination.pageNo = 1
    await loadData()
  }

  const handlePageChange = async (page: number) => {
    pagination.pageNo = page
    await loadData()
  }

  const handleSizeChange = async (size: number) => {
    pagination.pageSize = size
    pagination.pageNo = 1
    await loadData()
  }

  const handleReset = async () => {
    Object.assign(queryForm, { status: '', dateFrom: '', dateTo: '' })
    dateRange.value = []
    pagination.pageNo = 1
    await loadData()
  }

  const handleView = async (row: Expense) => {
    try {
      viewData.value = await options.getExpense(row.id)
      viewDialogVisible.value = true
      return true
    } catch {
      options.onError?.(t('financeReportPages.expenses.message.detailLoadFailed'))
      return false
    }
  }

  const handlePrint = async (row: Expense) => {
    try {
      const detail = await options.getExpense(row.id)
      options.printExpense(options.decoratePrint ? options.decoratePrint(detail) : detail)
      return true
    } catch {
      options.onError?.(t('financeReportPages.expenses.message.printLoadFailed'))
      return false
    }
  }

  const handleReconciliation = async (row: Expense) => {
    reconciliationDialogVisible.value = true
    reconciliationLoading.value = true
    reconciliationData.value = undefined
    try {
      reconciliationData.value = await options.getReconciliation(row.id)
      return true
    } catch {
      options.onError?.(t('financeReportPages.expenses.message.reconciliationLoadFailed'))
      return false
    } finally {
      reconciliationLoading.value = false
    }
  }

  /** Dismissing the confirmation is silent; a failed call reports its own key. */
  const confirmThen = async (
    action: (id: string | number) => Promise<unknown>,
    row: Expense,
    keys: { confirm: string; success: string; failed: string }
  ) => {
    try {
      await options.confirm(
        t(keys.confirm, { no: row.expenseNo }),
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

  const handleSubmit = (row: Expense) =>
    confirmThen(options.submitExpense, row, {
      confirm: 'financeReportPages.expenses.message.submitConfirm',
      success: 'financeReportPages.expenses.message.submitted',
      failed: 'financeReportPages.expenses.message.submitFailed'
    })

  const handleApprove = (row: Expense) =>
    confirmThen(options.approveExpense, row, {
      confirm: 'financeReportPages.expenses.message.approveConfirm',
      success: 'financeReportPages.expenses.message.approved',
      failed: 'financeReportPages.expenses.message.approveFailed'
    })

  const handlePost = (row: Expense) =>
    confirmThen(options.postExpense, row, {
      confirm: 'financeReportPages.expenses.message.postConfirm',
      success: 'financeReportPages.expenses.message.posted',
      failed: 'financeReportPages.expenses.message.postFailed'
    })

  const handleReverse = (row: Expense) =>
    confirmThen(options.reverseExpense, row, {
      confirm: 'financeReportPages.expenses.message.reverseConfirm',
      success: 'financeReportPages.expenses.message.reversed',
      failed: 'financeReportPages.expenses.message.reverseFailed'
    })

  const handleCancel = (row: Expense) =>
    confirmThen(options.cancelExpense, row, {
      confirm: 'financeReportPages.expenses.message.cancelConfirm',
      success: 'financeReportPages.expenses.message.cancelled',
      failed: 'financeReportPages.expenses.message.cancelFailed'
    })

  return {
    dateRange,
    handleApprove,
    handleCancel,
    handlePageChange,
    handlePost,
    handlePrint,
    handleQuery,
    handleReconciliation,
    handleReset,
    handleReverse,
    handleSizeChange,
    handleSubmit,
    handleView,
    loadData,
    loadSubjects,
    loading,
    pagination,
    queryForm,
    reconciliationData,
    reconciliationDialogVisible,
    reconciliationLoading,
    subjectOptions,
    tableData,
    viewData,
    viewDialogVisible
  }
}
