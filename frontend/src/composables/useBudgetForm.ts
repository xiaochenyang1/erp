import { computed, reactive, ref } from 'vue'

import type { Budget, BudgetLineSaveRequest, BudgetSaveRequest } from '@/api/finance'
import { ANNUAL_PERIOD, businessYear } from './useBudgetPresentation'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export type BudgetFormState = Required<Pick<BudgetSaveRequest, 'budgetYear' | 'budgetName' | 'controlPolicy' | 'remark'>>
  & { lines: BudgetLineSaveRequest[] }

/** A new line defaults to the annual allocation, which most budgets use. */
const blankLine = (): BudgetLineSaveRequest => ({
  periodMonth: ANNUAL_PERIOD,
  deptId: undefined,
  subjectId: '',
  budgetAmount: 0,
  remark: ''
})

const blankForm = (): BudgetFormState => ({
  budgetYear: businessYear(),
  budgetName: '',
  controlPolicy: 'REJECT',
  remark: '',
  lines: []
})

/** Create and edit form for one budget, including its monthly line editor. */
export const useBudgetForm = (
  t: Translate,
  options: {
    getBudget: (id: string | number) => Promise<Budget>
    createBudget: (data: BudgetSaveRequest) => Promise<unknown>
    updateBudget: (id: string, data: BudgetSaveRequest) => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const editorVisible = ref(false)
  const saving = ref(false)
  const editorId = ref<string>()
  const editor = reactive<BudgetFormState>(blankForm())

  const dialogTitle = computed(() => (editorId.value
    ? t('financeReportPages.budgets.edit')
    : t('financeReportPages.budgets.create')))

  const resetEditor = () => {
    editorId.value = undefined
    Object.assign(editor, blankForm())
  }

  const addLine = () => editor.lines.push(blankLine())

  /** Every line may go; saving an empty budget is what the validation catches. */
  const removeLine = (index: number) => editor.lines.splice(index, 1)

  const openCreate = () => {
    resetEditor()
    addLine()
    editorVisible.value = true
    return true
  }

  /** Editing refetches the budget so the form never edits a stale register row. */
  const openEdit = async (row: Budget) => {
    try {
      const detail = await options.getBudget(row.id)
      editorId.value = detail.id
      Object.assign(editor, {
        budgetYear: detail.budgetYear,
        budgetName: detail.budgetName,
        controlPolicy: detail.controlPolicy,
        remark: detail.remark || '',
        lines: detail.lines.map((line) => ({
          periodMonth: line.periodMonth,
          deptId: line.deptId,
          subjectId: line.subjectId,
          budgetAmount: line.budgetAmount,
          remark: line.remark
        }))
      })
      editorVisible.value = true
      return true
    } catch {
      options.onError?.(t('financeReportPages.budgets.message.detailLoadFailed'))
      return false
    }
  }

  /** A budget needs a name and at least one line, and every line needs an account. */
  const isComplete = () => Boolean(editor.budgetName.trim())
    && editor.lines.length > 0
    && editor.lines.every((line) => Boolean(line.subjectId))

  const save = async () => {
    if (!isComplete()) {
      options.onWarning?.(t('financeReportPages.budgets.validation.completeForm'))
      return false
    }
    saving.value = true
    try {
      const payload: BudgetSaveRequest = {
        budgetYear: editor.budgetYear,
        budgetName: editor.budgetName,
        controlPolicy: editor.controlPolicy,
        remark: editor.remark,
        lines: editor.lines
      }
      if (editorId.value) {
        await options.updateBudget(editorId.value, payload)
      } else {
        await options.createBudget(payload)
      }
      options.onSuccess?.(t('financeReportPages.budgets.message.saved'))
      editorVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('financeReportPages.budgets.message.saveFailed'))
      return false
    } finally {
      saving.value = false
    }
  }

  return {
    addLine,
    dialogTitle,
    editor,
    editorId,
    editorVisible,
    openCreate,
    openEdit,
    removeLine,
    resetEditor,
    save,
    saving
  }
}
