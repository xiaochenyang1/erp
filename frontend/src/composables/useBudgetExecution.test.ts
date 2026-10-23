import { describe, expect, it, vi } from 'vitest'

import type { BudgetExecution } from '@/api/finance'
import { useBudgetExecution } from './useBudgetExecution'
import { businessMonth, businessYear } from './useBudgetPresentation'

const t = (key: string) => key

const result = (overrides: Partial<BudgetExecution> = {}) => ({
  budgetYear: 2026,
  periodMonth: 9,
  subjectId: '1',
  budgetAmount: 1000,
  committedAmount: 200,
  actualAmount: 300,
  availableAmount: 500,
  periodSource: 'MONTHLY',
  requestedAmount: 0,
  projectedAvailableAmount: 500,
  overrun: false,
  ...overrides
} as BudgetExecution)

const createExecution = (overrides: Partial<Parameters<typeof useBudgetExecution>[1]> = {}) =>
  useBudgetExecution(t, {
    getBudgetExecution: vi.fn(async () => result()),
    onError: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  })

describe('budget execution enquiry', () => {
  it('opens on the current business period with no account picked yet', () => {
    const enquiry = createExecution()

    expect(enquiry.openExecution()).toBe(true)
    expect(enquiry.executionVisible.value).toBe(true)
    expect(enquiry.executionQuery.budgetYear).toBe(businessYear())
    expect(enquiry.executionQuery.periodMonth).toBe(businessMonth())
    expect(enquiry.executionQuery.subjectId).toBe('')
    expect(enquiry.executionQuery.amount).toBe(0)
    expect(enquiry.execution.value).toBeUndefined()
  })

  it('warns instead of querying when no account is picked', async () => {
    const getBudgetExecution = vi.fn(async () => result())
    const onWarning = vi.fn()
    const enquiry = createExecution({ getBudgetExecution, onWarning })

    expect(await enquiry.loadExecution()).toBe(false)
    expect(getBudgetExecution).not.toHaveBeenCalled()
    expect(onWarning).toHaveBeenCalledWith('financeReportPages.budgets.validation.subject')
  })

  it('asks for the plain balance when no amount is entered', async () => {
    const getBudgetExecution = vi.fn(async () => result())
    const enquiry = createExecution({ getBudgetExecution })

    enquiry.executionQuery.subjectId = '1'
    expect(await enquiry.loadExecution()).toBe(true)

    expect(getBudgetExecution).toHaveBeenCalledWith({
      budgetYear: businessYear(),
      periodMonth: businessMonth(),
      deptId: undefined,
      subjectId: '1',
      amount: undefined
    })
    expect(enquiry.execution.value?.availableAmount).toBe(500)
    expect(enquiry.executionLoading.value).toBe(false)
  })

  it('checks a proposed amount for an overrun when one is entered', async () => {
    const getBudgetExecution = vi.fn(async () => result({
      requestedAmount: 800,
      projectedAvailableAmount: -300,
      overrun: true,
      controlPolicy: 'REJECT'
    }))
    const enquiry = createExecution({ getBudgetExecution })

    enquiry.executionQuery.deptId = '10'
    enquiry.executionQuery.subjectId = '1'
    enquiry.executionQuery.amount = 800
    expect(await enquiry.loadExecution()).toBe(true)

    expect(getBudgetExecution).toHaveBeenCalledWith(expect.objectContaining({ deptId: '10', amount: 800 }))
    expect(enquiry.execution.value?.overrun).toBe(true)
    expect(enquiry.execution.value?.projectedAvailableAmount).toBe(-300)
  })

  it('drops a stale answer when the query fails', async () => {
    const getBudgetExecution = vi.fn(async () => result())
    const onError = vi.fn()
    const enquiry = createExecution({ getBudgetExecution, onError })

    enquiry.executionQuery.subjectId = '1'
    await enquiry.loadExecution()
    expect(enquiry.execution.value).toBeDefined()

    getBudgetExecution.mockRejectedValueOnce(new Error('boom'))
    expect(await enquiry.loadExecution()).toBe(false)

    expect(enquiry.execution.value).toBeUndefined()
    expect(onError).toHaveBeenCalledWith('financeReportPages.budgets.message.executionLoadFailed')
    expect(enquiry.executionLoading.value).toBe(false)
  })

  it('drops the answer on close so a reopen never shows a stale balance', async () => {
    const enquiry = createExecution()

    enquiry.executionQuery.subjectId = '1'
    await enquiry.loadExecution()
    expect(enquiry.execution.value).toBeDefined()

    enquiry.resetExecution()
    expect(enquiry.execution.value).toBeUndefined()
  })
})
