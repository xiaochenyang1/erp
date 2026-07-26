import { describe, expect, it, vi } from 'vitest'

import type { Expense } from '@/api/finance'
import { useExpenseList } from './useExpenseList'

const t = (key: string) => key

const row = (overrides: Partial<Expense> = {}) =>
  ({ id: 'e1', expenseNo: 'EXP001', status: 'DRAFT', ...overrides }) as Expense

const createList = (overrides: Partial<Parameters<typeof useExpenseList>[1]> = {}) =>
  useExpenseList(t, {
    getExpenses: vi.fn(async () => ({ records: [row()], total: 1 } as any)),
    getExpense: vi.fn(async () => row({ amount: 120 })),
    getSubjectTree: vi.fn(async () => [
      { id: '1', code: '6601', children: [{ id: '11', code: '660101' }] }
    ] as any),
    getReconciliation: vi.fn(async () => ({ voucherBalanced: true } as any)),
    submitExpense: vi.fn(async () => ({})),
    approveExpense: vi.fn(async () => ({})),
    postExpense: vi.fn(async () => ({})),
    reverseExpense: vi.fn(async () => ({})),
    cancelExpense: vi.fn(async () => ({})),
    printExpense: vi.fn(),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('expense list', () => {
  it('sends only filled filters and resets paging on query', async () => {
    const getExpenses = vi.fn(async () => ({ records: [], total: 3 } as any))
    const list = createList({ getExpenses })

    list.pagination.pageNo = 5
    list.queryForm.status = 'PENDING'
    list.dateRange.value = ['2026-07-01', '2026-07-26']
    await list.handleQuery()

    expect(getExpenses).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 20,
      status: 'PENDING',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-26'
    })
    expect(list.pagination.total).toBe(3)

    list.queryForm.status = ''
    list.dateRange.value = []
    await list.handleQuery()
    expect(getExpenses).toHaveBeenLastCalledWith({
      pageNo: 1,
      pageSize: 20,
      status: undefined,
      dateFrom: undefined,
      dateTo: undefined
    })
  })

  it('keeps the page when paging and returns to page 1 on size change or reset', async () => {
    const getExpenses = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getExpenses })

    await list.handlePageChange(4)
    expect(list.pagination.pageNo).toBe(4)
    expect(getExpenses).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 4 }))

    await list.handleSizeChange(50)
    expect(list.pagination.pageSize).toBe(50)
    expect(list.pagination.pageNo).toBe(1)

    list.queryForm.status = 'POSTED'
    list.dateRange.value = ['2026-07-01', '2026-07-02']
    list.pagination.pageNo = 3
    await list.handleReset()

    expect(list.queryForm.status).toBe('')
    expect(list.queryForm.dateFrom).toBe('')
    expect(list.dateRange.value).toEqual([])
    expect(list.pagination.pageNo).toBe(1)
  })

  it('reports load failures and clears the loading flag', async () => {
    const onError = vi.fn()
    const list = createList({
      getExpenses: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    await list.loadData()
    expect(onError).toHaveBeenCalledWith('financeReportPages.expenses.message.loadFailed')
    expect(list.loading.value).toBe(false)
  })

  it('flattens the loaded account subject tree into selectable options', async () => {
    const list = createList()
    await list.loadSubjects()
    expect(list.subjectOptions.value.map((item) => item.id)).toEqual(['1', '11'])

    const onError = vi.fn()
    const failing = createList({
      getSubjectTree: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await failing.loadSubjects()
    expect(onError).toHaveBeenCalledWith('financeReportPages.expenses.message.subjectsLoadFailed')
  })

  it('loads the detail for view and prints the decorated detail', async () => {
    const getExpense = vi.fn(async () => row({ amount: 88 }))
    const printExpense = vi.fn()
    const decoratePrint = vi.fn((doc: Expense) => ({ ...doc, subjectName: '销售费用' }))
    const list = createList({ getExpense, printExpense, decoratePrint })

    await list.handleView(row())
    expect(list.viewDialogVisible.value).toBe(true)
    expect(list.viewData.value.amount).toBe(88)

    await list.handlePrint(row())
    expect(decoratePrint).toHaveBeenCalled()
    expect(printExpense).toHaveBeenCalledWith(expect.objectContaining({ subjectName: '销售费用' }))

    const onError = vi.fn()
    const failing = createList({
      getExpense: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await failing.handleView(row())
    expect(failing.viewDialogVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.expenses.message.detailLoadFailed')

    await failing.handlePrint(row())
    expect(onError).toHaveBeenCalledWith('financeReportPages.expenses.message.printLoadFailed')
  })

  it('opens reconciliation, clearing stale data while loading', async () => {
    const getReconciliation = vi.fn(async () => ({ voucherBalanced: true } as any))
    const list = createList({ getReconciliation })

    await list.handleReconciliation(row())
    expect(list.reconciliationDialogVisible.value).toBe(true)
    expect(list.reconciliationLoading.value).toBe(false)
    expect(list.reconciliationData.value).toEqual({ voucherBalanced: true })

    const onError = vi.fn()
    const failing = createList({
      getReconciliation: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await failing.handleReconciliation(row())
    expect(failing.reconciliationData.value).toBeUndefined()
    expect(failing.reconciliationLoading.value).toBe(false)
    expect(onError).toHaveBeenCalledWith(
      'financeReportPages.expenses.message.reconciliationLoadFailed'
    )
  })

  it('runs each lifecycle action after confirmation and reloads', async () => {
    const submitExpense = vi.fn(async () => ({}))
    const approveExpense = vi.fn(async () => ({}))
    const postExpense = vi.fn(async () => ({}))
    const reverseExpense = vi.fn(async () => ({}))
    const cancelExpense = vi.fn(async () => ({}))
    const getExpenses = vi.fn(async () => ({ records: [], total: 0 } as any))
    const onSuccess = vi.fn()
    const list = createList({
      submitExpense,
      approveExpense,
      postExpense,
      reverseExpense,
      cancelExpense,
      getExpenses,
      onSuccess
    })

    await list.handleSubmit(row())
    await list.handleApprove(row())
    await list.handlePost(row())
    await list.handleReverse(row())
    await list.handleCancel(row())

    expect(submitExpense).toHaveBeenCalledWith('e1')
    expect(approveExpense).toHaveBeenCalledWith('e1')
    expect(postExpense).toHaveBeenCalledWith('e1')
    expect(reverseExpense).toHaveBeenCalledWith('e1')
    expect(cancelExpense).toHaveBeenCalledWith('e1')
    expect(getExpenses).toHaveBeenCalledTimes(5)
    expect(onSuccess).toHaveBeenCalledTimes(5)
  })

  it('stays silent when a confirmation is dismissed but reports action failures', async () => {
    const onError = vi.fn()
    const submitExpense = vi.fn(async () => ({}))
    const dismissed = createList({
      confirm: vi.fn(async () => { throw 'cancel' }),
      submitExpense,
      onError
    })

    await dismissed.handleSubmit(row())
    expect(submitExpense).not.toHaveBeenCalled()
    expect(onError).not.toHaveBeenCalled()

    const failing = createList({
      submitExpense: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await failing.handleSubmit(row())
    expect(onError).toHaveBeenCalledWith('financeReportPages.expenses.message.submitFailed')

    const postFailing = createList({
      postExpense: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await postFailing.handlePost(row())
    expect(onError).toHaveBeenCalledWith('financeReportPages.expenses.message.postFailed')
  })
})
