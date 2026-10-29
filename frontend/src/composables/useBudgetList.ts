import { reactive, ref } from 'vue'

import type { AccountSubject, Budget, BudgetQuery } from '@/api/finance'
import type { Dept } from '@/api/system'
import type { PageResponse } from '@/types/common'
import { businessYear } from './useBudgetPresentation'
import { flattenSubjects } from './useExpensePresentation'
import { flattenDepts } from './useSystemUserPresentation'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title: string,
  options?: { type?: string }
) => Promise<unknown>

/**
 * Budget register query, the read-only detail dialog, the account and
 * department pickers shared with the editor, and the status actions.
 */
export const useBudgetList = (
  t: Translate,
  options: {
    getBudgets: (params: BudgetQuery) => Promise<PageResponse<Budget>>
    getBudget: (id: string | number) => Promise<Budget>
    getAccountSubjectTree: () => Promise<AccountSubject[]>
    getDeptTree: () => Promise<Dept[]>
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const query = reactive({ budgetYear: businessYear(), status: '', keyword: '' })
  const pagination = reactive({ pageNo: 1, pageSize: 20, total: 0 })
  const records = ref<Budget[]>([])
  const loading = ref(false)
  const detailVisible = ref(false)
  const selected = ref<Budget>()
  const subjects = ref<AccountSubject[]>([])
  const departments = ref<Dept[]>([])

  /** Both trees are flattened once so every level stays selectable in the pickers. */
  const loadResources = async () => {
    try {
      const [subjectTree, deptTree] = await Promise.all([
        options.getAccountSubjectTree(),
        options.getDeptTree()
      ])
      subjects.value = flattenSubjects(subjectTree || [])
      departments.value = flattenDepts(deptTree || [])
      return true
    } catch {
      options.onError?.(t('financeReportPages.budgets.message.resourcesLoadFailed'))
      return false
    }
  }

  const loadBudgets = async () => {
    loading.value = true
    try {
      const page = await options.getBudgets({
        pageNo: pagination.pageNo,
        pageSize: pagination.pageSize,
        budgetYear: query.budgetYear || undefined,
        status: query.status || undefined,
        keyword: query.keyword || undefined
      })
      records.value = page.records || []
      pagination.total = page.total || 0
      return true
    } catch {
      options.onError?.(t('financeReportPages.budgets.message.loadFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  const resetQuery = async () => {
    Object.assign(query, { budgetYear: businessYear(), status: '', keyword: '' })
    pagination.pageNo = 1
    return loadBudgets()
  }

  const handlePageChange = async (page: number) => {
    pagination.pageNo = page
    return loadBudgets()
  }

  const handleSizeChange = async (size: number) => {
    pagination.pageSize = size
    pagination.pageNo = 1
    return loadBudgets()
  }

  /** The detail refetches the budget so line consumption is never stale. */
  const openDetail = async (row: Budget) => {
    try {
      selected.value = await options.getBudget(row.id)
      detailVisible.value = true
      return true
    } catch {
      options.onError?.(t('financeReportPages.budgets.message.detailLoadFailed'))
      return false
    }
  }

  const resetDetail = () => {
    selected.value = undefined
  }

  /** Dismissing the confirmation is silent; only a failed call reports an error. */
  const runAction = async (
    action: (id: string) => Promise<unknown>,
    row: Budget,
    actionLabelKey: string
  ) => {
    try {
      await options.confirm(
        t('financeReportPages.budgets.message.confirmAction', {
          action: t(actionLabelKey),
          name: row.budgetName
        }),
        t('financeReportPages.budgets.message.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    try {
      await action(row.id)
      options.onSuccess?.(t('financeReportPages.budgets.message.actionDone'))
      await loadBudgets()
      return true
    } catch {
      options.onError?.(t('financeReportPages.budgets.message.actionFailed'))
      return false
    }
  }

  return {
    departments,
    detailVisible,
    handlePageChange,
    handleSizeChange,
    loadBudgets,
    loadResources,
    loading,
    openDetail,
    pagination,
    query,
    records,
    resetDetail,
    resetQuery,
    runAction,
    selected,
    subjects
  }
}
