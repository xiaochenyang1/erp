import { computed, reactive, ref } from 'vue'

import type { Expense } from '@/api/finance'
import { formatBusinessDate } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export interface ExpenseFormState {
  id: string | number
  subjectId: string
  paymentSubjectId: string
  expenseDate: string
  amount: number
  remark: string
}

export interface ExpenseSavePayload {
  expenseDate: string
  subjectId: string
  paymentSubjectId: string
  amount: number
  remark: string
}

export const useExpenseForm = (
  t: Translate,
  options: {
    getExpense: (id: string | number) => Promise<Expense>
    createExpense: (data: ExpenseSavePayload) => Promise<unknown>
    updateExpense: (id: string | number, data: ExpenseSavePayload) => Promise<unknown>
    rejectExpense: (id: string | number, reason: string) => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const dialogVisible = ref(false)
  const submitLoading = ref(false)
  const rejectDialogVisible = ref(false)

  const formData = reactive<ExpenseFormState>({
    id: '',
    subjectId: '',
    paymentSubjectId: '',
    expenseDate: '',
    amount: 0,
    remark: ''
  })

  const rejectForm = reactive({
    id: '' as string | number,
    reason: ''
  })

  const dialogTitle = computed(() => formData.id
    ? t('financeReportPages.expenses.editTitle')
    : t('financeReportPages.expenses.createTitle'))

  const resetForm = () => {
    Object.assign(formData, {
      id: '',
      subjectId: '',
      paymentSubjectId: '',
      expenseDate: '',
      amount: 0,
      remark: ''
    })
  }

  const handleAdd = () => {
    resetForm()
    formData.expenseDate = formatBusinessDate()
    dialogVisible.value = true
  }

  /** Editing refetches the detail so the form never edits a stale list row. */
  const handleEdit = async (row: Expense) => {
    try {
      const expense = await options.getExpense(row.id)
      Object.assign(formData, {
        id: expense.id,
        subjectId: String(expense.subjectId),
        paymentSubjectId: String(expense.paymentSubjectId),
        expenseDate: expense.expenseDate,
        amount: Number(expense.amount || 0),
        remark: expense.remark || ''
      })
      dialogVisible.value = true
      return true
    } catch {
      options.onError?.(t('financeReportPages.expenses.message.detailLoadFailed'))
      return false
    }
  }

  const handleSave = async () => {
    submitLoading.value = true
    try {
      const payload: ExpenseSavePayload = {
        expenseDate: formData.expenseDate,
        subjectId: formData.subjectId,
        paymentSubjectId: formData.paymentSubjectId,
        amount: formData.amount,
        remark: formData.remark
      }
      if (formData.id) {
        await options.updateExpense(formData.id, payload)
      } else {
        await options.createExpense(payload)
      }
      options.onSuccess?.(t('financeReportPages.expenses.message.saved'))
      dialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('financeReportPages.expenses.message.saveFailed'))
      return false
    } finally {
      submitLoading.value = false
    }
  }

  const handleReject = (row: Expense) => {
    rejectForm.id = row.id
    rejectForm.reason = ''
    rejectDialogVisible.value = true
  }

  /** A rejection must carry a reason, so the approver's decision stays auditable. */
  const handleConfirmReject = async () => {
    if (!rejectForm.reason.trim()) {
      options.onWarning?.(t('financeReportPages.expenses.message.rejectReasonRequired'))
      return false
    }
    submitLoading.value = true
    try {
      await options.rejectExpense(rejectForm.id, rejectForm.reason)
      options.onSuccess?.(t('financeReportPages.expenses.message.rejected'))
      rejectDialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('financeReportPages.expenses.message.rejectFailed'))
      return false
    } finally {
      submitLoading.value = false
    }
  }

  return {
    dialogTitle,
    dialogVisible,
    formData,
    handleAdd,
    handleConfirmReject,
    handleEdit,
    handleReject,
    handleSave,
    rejectDialogVisible,
    rejectForm,
    resetForm,
    submitLoading
  }
}
