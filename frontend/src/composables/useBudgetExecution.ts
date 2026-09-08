import { reactive, ref } from 'vue'

import type { BudgetExecution, BudgetExecutionQuery } from '@/api/finance'
import { businessMonth, businessYear } from './useBudgetPresentation'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

/**
 * Budget execution enquiry: how much of one account's allocation is left, and
 * whether a proposed amount would overrun it.
 */
export const useBudgetExecution = (
  t: Translate,
  options: {
    getBudgetExecution: (params: BudgetExecutionQuery) => Promise<BudgetExecution>
    onError?: Notify
    onWarning?: Notify
  }
) => {
  const executionVisible = ref(false)
  const executionLoading = ref(false)
  const execution = ref<BudgetExecution>()
  const executionQuery = reactive({
    budgetYear: businessYear(),
    periodMonth: businessMonth(),
    deptId: undefined as string | undefined,
    subjectId: '',
    amount: 0
  })

  const openExecution = () => {
    executionVisible.value = true
    return true
  }

  /** Closing the dialog drops the result so a reopen never shows a stale answer. */
  const resetExecution = () => {
    execution.value = undefined
  }

  /** A zero amount asks for the plain balance rather than an overrun check. */
  const loadExecution = async () => {
    if (!executionQuery.subjectId) {
      options.onWarning?.(t('financeReportPages.budgets.validation.subject'))
      return false
    }
    executionLoading.value = true
    try {
      execution.value = await options.getBudgetExecution({
        ...executionQuery,
        amount: executionQuery.amount || undefined
      })
      return true
    } catch {
      execution.value = undefined
      options.onError?.(t('financeReportPages.budgets.message.executionLoadFailed'))
      return false
    } finally {
      executionLoading.value = false
    }
  }

  return {
    execution,
    executionLoading,
    executionQuery,
    executionVisible,
    loadExecution,
    openExecution,
    resetExecution
  }
}
