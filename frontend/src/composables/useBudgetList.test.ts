import { describe, expect, it, vi } from 'vitest'

import type { Budget } from '@/api/finance'
import { useBudgetList } from './useBudgetList'
import { businessYear } from './useBudgetPresentation'

const t = (key: string) => key

const row = (overrides: Partial<Budget> = {}) =>
  ({ id: 'b1', budgetYear: 2026, budgetName: '2026 年度预算', status: 'DRAFT', lines: [], ...overrides }) as Budget

const createList = (overrides: Partial<Parameters<typeof useBudgetList>[1]> = {}) =>
  useBudgetList(t, {
    getBudgets: vi.fn(async () => ({ records: [row()], total: 1 } as any)),
    getBudget: vi.fn(async () => row({ lines: [{ id: 'l1', periodMonth: 0, subjectId: '1', budgetAmount: 100 }] as any })),
    getAccountSubjectTree: vi.fn(async () => [
      { id: '1', code: '6601', children: [{ id: '11', code: '660101' }] }
    ] as any),
    getDeptTree: vi.fn(async () => [
      { id: '10', code: 'D01', children: [{ id: '11', code: 'D0101' }] }
    ] as any),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('budget list', () => {
  it('sends only filled filters and keeps the total', async () => {
    const getBudgets = vi.fn(async () => ({ records: [row()], total: 7 } as any))
    const list = createList({ getBudgets })

    expect(await list.loadBudgets()).toBe(true)
    expect(getBudgets).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 20,
      budgetYear: businessYear(),
      status: undefined,
      keyword: undefined
    })
    expect(list.records.value).toHaveLength(1)
    expect(list.pagination.total).toBe(7)
    expect(list.loading.value).toBe(false)

    list.query.status = 'APPROVED'
    list.query.keyword = '销售'
    await list.loadBudgets()
    expect(getBudgets).toHaveBeenLastCalledWith({
      pageNo: 1,
      pageSize: 20,
      budgetYear: businessYear(),
      status: 'APPROVED',
      keyword: '销售'
    })
  })

  it('reports a failed page load and clears the loading flag', async () => {
    const onError = vi.fn()
    const list = createList({ getBudgets: vi.fn(async () => { throw new Error('boom') }), onError })

    expect(await list.loadBudgets()).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.budgets.message.loadFailed')
    expect(list.loading.value).toBe(false)
  })

  it('resets the query back to the current business year', async () => {
    const list = createList()

    list.query.budgetYear = 2020
    list.query.status = 'CLOSED'
    list.query.keyword = 'x'
    list.pagination.pageNo = 4
    expect(await list.resetQuery()).toBe(true)

    expect(list.query).toMatchObject({ budgetYear: businessYear(), status: '', keyword: '' })
    expect(list.pagination.pageNo).toBe(1)
  })

  it('pages, and returns to the first page when the page size changes', async () => {
    const getBudgets = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getBudgets })

    await list.handlePageChange(3)
    expect(getBudgets).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 3, pageSize: 20 }))

    await list.handleSizeChange(50)
    expect(list.pagination.pageNo).toBe(1)
    expect(getBudgets).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 1, pageSize: 50 }))
  })

  it('flattens the account and department trees in one round trip', async () => {
    const list = createList()

    expect(await list.loadResources()).toBe(true)
    expect(list.subjects.value.map((subject) => subject.id)).toEqual(['1', '11'])
    expect(list.departments.value.map((dept) => dept.id)).toEqual(['10', '11'])
  })

  it('reports a single error when either tree fails to load', async () => {
    const onError = vi.fn()
    const list = createList({ getDeptTree: vi.fn(async () => { throw new Error('boom') }), onError })

    expect(await list.loadResources()).toBe(false)
    expect(onError).toHaveBeenCalledTimes(1)
    expect(onError).toHaveBeenCalledWith('financeReportPages.budgets.message.resourcesLoadFailed')
  })

  it('refetches the budget for the detail dialog and drops it on close', async () => {
    const getBudget = vi.fn(async () => row({ budgetName: '刷新后的预算' }))
    const list = createList({ getBudget })

    expect(await list.openDetail(row())).toBe(true)
    expect(getBudget).toHaveBeenCalledWith('b1')
    expect(list.selected.value?.budgetName).toBe('刷新后的预算')
    expect(list.detailVisible.value).toBe(true)

    list.resetDetail()
    expect(list.selected.value).toBeUndefined()
  })

  it('keeps the detail dialog closed when the refetch fails', async () => {
    const onError = vi.fn()
    const list = createList({ getBudget: vi.fn(async () => { throw new Error('boom') }), onError })

    expect(await list.openDetail(row())).toBe(false)
    expect(list.detailVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.budgets.message.detailLoadFailed')
  })

  it('confirms a status action, then runs it and reloads the register', async () => {
    const confirm = vi.fn(async () => true)
    const getBudgets = vi.fn(async () => ({ records: [], total: 0 } as any))
    const onSuccess = vi.fn()
    const action = vi.fn(async () => ({}))
    const list = createList({ confirm, getBudgets, onSuccess })

    expect(await list.runAction(action, row(), 'financeReportPages.common.submit')).toBe(true)
    expect(confirm).toHaveBeenCalledWith(
      'financeReportPages.budgets.message.confirmAction',
      'financeReportPages.budgets.message.prompt',
      { type: 'warning' }
    )
    expect(action).toHaveBeenCalledWith('b1')
    expect(onSuccess).toHaveBeenCalledWith('financeReportPages.budgets.message.actionDone')
    expect(getBudgets).toHaveBeenCalledTimes(1)
  })

  it('stays silent when the confirmation is dismissed and reports a failed action', async () => {
    const onError = vi.fn()
    const action = vi.fn(async () => ({}))
    const dismissed = createList({ confirm: vi.fn(async () => { throw new Error('cancel') }), onError })

    expect(await dismissed.runAction(action, row(), 'financeReportPages.common.submit')).toBe(false)
    expect(action).not.toHaveBeenCalled()
    expect(onError).not.toHaveBeenCalled()

    const failing = createList({ onError })
    expect(await failing.runAction(
      vi.fn(async () => { throw new Error('boom') }),
      row(),
      'financeReportPages.common.submit'
    )).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.budgets.message.actionFailed')
  })
})
