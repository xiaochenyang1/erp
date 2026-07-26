import { describe, expect, it, vi } from 'vitest'

import type { Expense } from '@/api/finance'
import { useExpenseForm } from './useExpenseForm'

const t = (key: string) => key

const detail = {
  id: 'e1',
  expenseNo: 'EXP001',
  subjectId: 660101,
  paymentSubjectId: 100201,
  expenseDate: '2026-07-20',
  amount: 250.5,
  remark: '差旅'
} as unknown as Expense

const createForm = (overrides: Partial<Parameters<typeof useExpenseForm>[1]> = {}) =>
  useExpenseForm(t, {
    getExpense: vi.fn(async () => detail),
    createExpense: vi.fn(async () => ({})),
    updateExpense: vi.fn(async () => ({})),
    rejectExpense: vi.fn(async () => ({})),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  })

describe('expense form', () => {
  it('opens a blank create form dated in the business timezone', () => {
    const form = createForm()
    form.handleAdd()

    expect(form.dialogVisible.value).toBe(true)
    expect(form.formData.id).toBe('')
    expect(form.formData.amount).toBe(0)
    expect(form.formData.expenseDate).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(form.dialogTitle.value).toBe('financeReportPages.expenses.createTitle')
  })

  it('loads the detail for edit and stringifies account ids for the selects', async () => {
    const getExpense = vi.fn(async () => detail)
    const form = createForm({ getExpense })

    expect(await form.handleEdit({ id: 'e1' } as Expense)).toBe(true)
    expect(getExpense).toHaveBeenCalledWith('e1')
    expect(form.formData).toMatchObject({
      id: 'e1',
      subjectId: '660101',
      paymentSubjectId: '100201',
      expenseDate: '2026-07-20',
      amount: 250.5,
      remark: '差旅'
    })
    expect(form.dialogTitle.value).toBe('financeReportPages.expenses.editTitle')
  })

  it('reports detail failures without opening the dialog', async () => {
    const onError = vi.fn()
    const form = createForm({
      getExpense: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    expect(await form.handleEdit({ id: 'e1' } as Expense)).toBe(false)
    expect(form.dialogVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.expenses.message.detailLoadFailed')
  })

  it('creates when no id is present and updates when editing', async () => {
    const createExpense = vi.fn(async () => ({}))
    const updateExpense = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const form = createForm({ createExpense, updateExpense, onSubmitted })

    form.handleAdd()
    form.formData.subjectId = '660101'
    form.formData.paymentSubjectId = '100201'
    form.formData.amount = 99
    expect(await form.handleSave()).toBe(true)

    expect(createExpense).toHaveBeenCalledWith(expect.objectContaining({
      subjectId: '660101',
      paymentSubjectId: '100201',
      amount: 99
    }))
    expect(updateExpense).not.toHaveBeenCalled()
    expect(form.dialogVisible.value).toBe(false)
    expect(onSubmitted).toHaveBeenCalled()

    await form.handleEdit({ id: 'e1' } as Expense)
    expect(await form.handleSave()).toBe(true)
    expect(updateExpense).toHaveBeenCalledWith('e1', expect.objectContaining({ amount: 250.5 }))
  })

  it('keeps the dialog open and clears loading when saving fails', async () => {
    const onError = vi.fn()
    const form = createForm({
      createExpense: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    form.handleAdd()
    expect(await form.handleSave()).toBe(false)
    expect(form.dialogVisible.value).toBe(true)
    expect(form.submitLoading.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.expenses.message.saveFailed')
  })

  it('clears the form state on reset', () => {
    const form = createForm()
    form.handleAdd()
    form.formData.subjectId = '660101'
    form.formData.amount = 42
    form.formData.remark = '备注'

    form.resetForm()

    expect(form.formData).toMatchObject({
      id: '',
      subjectId: '',
      paymentSubjectId: '',
      expenseDate: '',
      amount: 0,
      remark: ''
    })
  })

  it('opens the reject dialog with a cleared reason for the chosen row', () => {
    const form = createForm()
    form.rejectForm.reason = '旧原因'

    form.handleReject({ id: 'e9' } as Expense)

    expect(form.rejectDialogVisible.value).toBe(true)
    expect(form.rejectForm.id).toBe('e9')
    expect(form.rejectForm.reason).toBe('')
  })

  it('requires a non-blank reason before rejecting', async () => {
    const rejectExpense = vi.fn(async () => ({}))
    const onWarning = vi.fn()
    const form = createForm({ rejectExpense, onWarning })

    form.handleReject({ id: 'e9' } as Expense)
    form.rejectForm.reason = '   '
    expect(await form.handleConfirmReject()).toBe(false)

    expect(rejectExpense).not.toHaveBeenCalled()
    expect(onWarning).toHaveBeenCalledWith(
      'financeReportPages.expenses.message.rejectReasonRequired'
    )
    expect(form.rejectDialogVisible.value).toBe(true)
  })

  it('rejects with the reason, then closes and reloads', async () => {
    const rejectExpense = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const onSuccess = vi.fn()
    const form = createForm({ rejectExpense, onSubmitted, onSuccess })

    form.handleReject({ id: 'e9' } as Expense)
    form.rejectForm.reason = '票据不全'
    expect(await form.handleConfirmReject()).toBe(true)

    expect(rejectExpense).toHaveBeenCalledWith('e9', '票据不全')
    expect(form.rejectDialogVisible.value).toBe(false)
    expect(onSuccess).toHaveBeenCalledWith('financeReportPages.expenses.message.rejected')
    expect(onSubmitted).toHaveBeenCalled()
  })

  it('keeps the reject dialog open and clears loading when rejection fails', async () => {
    const onError = vi.fn()
    const form = createForm({
      rejectExpense: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    form.handleReject({ id: 'e9' } as Expense)
    form.rejectForm.reason = '不通过'
    expect(await form.handleConfirmReject()).toBe(false)

    expect(form.rejectDialogVisible.value).toBe(true)
    expect(form.submitLoading.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.expenses.message.rejectFailed')
  })
})
