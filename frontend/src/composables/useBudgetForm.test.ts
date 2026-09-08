import { describe, expect, it, vi } from 'vitest'

import type { Budget } from '@/api/finance'
import { useBudgetForm } from './useBudgetForm'
import { ANNUAL_PERIOD, businessYear } from './useBudgetPresentation'

const t = (key: string) => key

const detail = (overrides: Partial<Budget> = {}) => ({
  id: 'b1',
  budgetYear: 2025,
  budgetName: '2025 年度预算',
  controlPolicy: 'APPROVAL',
  remark: '上年结转',
  status: 'DRAFT',
  lines: [
    { id: 'l1', periodMonth: 3, deptId: '10', subjectId: '1', budgetAmount: 100, remark: '一季度' },
    { id: 'l2', periodMonth: ANNUAL_PERIOD, deptId: undefined, subjectId: '2', budgetAmount: 900, remark: '' }
  ],
  ...overrides
} as unknown as Budget)

const createForm = (overrides: Partial<Parameters<typeof useBudgetForm>[1]> = {}) =>
  useBudgetForm(t, {
    getBudget: vi.fn(async () => detail()),
    createBudget: vi.fn(async () => ({})),
    updateBudget: vi.fn(async () => ({})),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    onSubmitted: vi.fn(),
    ...overrides
  })

describe('budget form', () => {
  it('opens a blank draft for the current business year with one annual line', () => {
    const form = createForm()

    expect(form.openCreate()).toBe(true)
    expect(form.editorVisible.value).toBe(true)
    expect(form.editorId.value).toBeUndefined()
    expect(form.dialogTitle.value).toBe('financeReportPages.budgets.create')
    expect(form.editor.budgetYear).toBe(businessYear())
    expect(form.editor.controlPolicy).toBe('REJECT')
    expect(form.editor.lines).toHaveLength(1)
    expect(form.editor.lines[0]).toMatchObject({ periodMonth: ANNUAL_PERIOD, subjectId: '', budgetAmount: 0 })
  })

  it('refetches the budget when editing so the form never edits a stale row', async () => {
    const getBudget = vi.fn(async () => detail())
    const form = createForm({ getBudget })

    expect(await form.openEdit({ id: 'b1' } as Budget)).toBe(true)
    expect(getBudget).toHaveBeenCalledWith('b1')
    expect(form.editorId.value).toBe('b1')
    expect(form.dialogTitle.value).toBe('financeReportPages.budgets.edit')
    expect(form.editor).toMatchObject({
      budgetYear: 2025,
      budgetName: '2025 年度预算',
      controlPolicy: 'APPROVAL',
      remark: '上年结转'
    })
    expect(form.editor.lines).toHaveLength(2)
    expect(form.editor.lines[0]).toEqual({
      periodMonth: 3,
      deptId: '10',
      subjectId: '1',
      budgetAmount: 100,
      remark: '一季度'
    })
  })

  it('reports a failed refetch and leaves the editor closed', async () => {
    const onError = vi.fn()
    const form = createForm({ getBudget: vi.fn(async () => { throw new Error('boom') }), onError })

    expect(await form.openEdit({ id: 'b1' } as Budget)).toBe(false)
    expect(form.editorVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.budgets.message.detailLoadFailed')
  })

  it('adds and removes lines, including the last one', () => {
    const form = createForm()

    form.openCreate()
    form.addLine()
    expect(form.editor.lines).toHaveLength(2)

    form.removeLine(0)
    expect(form.editor.lines).toHaveLength(1)
    form.removeLine(0)
    expect(form.editor.lines).toHaveLength(0)
  })

  it('warns instead of saving a budget without a name, lines or an account', async () => {
    const onWarning = vi.fn()
    const createBudget = vi.fn(async () => ({}))
    const form = createForm({ createBudget, onWarning })

    form.openCreate()
    expect(await form.save()).toBe(false)

    form.editor.budgetName = '  '
    form.editor.lines[0].subjectId = '1'
    expect(await form.save()).toBe(false)

    form.editor.budgetName = '2026 年度预算'
    form.editor.lines = []
    expect(await form.save()).toBe(false)

    expect(createBudget).not.toHaveBeenCalled()
    expect(onWarning).toHaveBeenCalledTimes(3)
    expect(onWarning).toHaveBeenLastCalledWith('financeReportPages.budgets.validation.completeForm')
  })

  it('creates a new budget, then reloads the register and closes', async () => {
    const createBudget = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const onSuccess = vi.fn()
    const form = createForm({ createBudget, onSubmitted, onSuccess })

    form.openCreate()
    form.editor.budgetName = '2026 年度预算'
    form.editor.lines[0].subjectId = '1'
    form.editor.lines[0].budgetAmount = 5000
    expect(await form.save()).toBe(true)

    expect(createBudget).toHaveBeenCalledWith({
      budgetYear: businessYear(),
      budgetName: '2026 年度预算',
      controlPolicy: 'REJECT',
      remark: '',
      lines: form.editor.lines
    })
    expect(onSuccess).toHaveBeenCalledWith('financeReportPages.budgets.message.saved')
    expect(onSubmitted).toHaveBeenCalledTimes(1)
    expect(form.editorVisible.value).toBe(false)
  })

  it('updates the fetched budget by id instead of creating a second one', async () => {
    const createBudget = vi.fn(async () => ({}))
    const updateBudget = vi.fn(async () => ({}))
    const form = createForm({ createBudget, updateBudget })

    await form.openEdit({ id: 'b1' } as Budget)
    expect(await form.save()).toBe(true)

    expect(createBudget).not.toHaveBeenCalled()
    expect(updateBudget).toHaveBeenCalledWith('b1', expect.objectContaining({
      budgetYear: 2025,
      budgetName: '2025 年度预算',
      controlPolicy: 'APPROVAL'
    }))
  })

  it('keeps the dialog open and clears the saving flag when the save fails', async () => {
    const onError = vi.fn()
    const form = createForm({ createBudget: vi.fn(async () => { throw new Error('boom') }), onError })

    form.openCreate()
    form.editor.budgetName = '2026 年度预算'
    form.editor.lines[0].subjectId = '1'
    expect(await form.save()).toBe(false)

    expect(onError).toHaveBeenCalledWith('financeReportPages.budgets.message.saveFailed')
    expect(form.editorVisible.value).toBe(true)
    expect(form.saving.value).toBe(false)
  })

  it('resets the editor back to a blank draft', async () => {
    const form = createForm()

    await form.openEdit({ id: 'b1' } as Budget)
    form.resetEditor()

    expect(form.editorId.value).toBeUndefined()
    expect(form.editor.budgetName).toBe('')
    expect(form.editor.budgetYear).toBe(businessYear())
    expect(form.editor.lines).toHaveLength(0)
  })
})
