import { describe, expect, it, vi } from 'vitest'

import type { ExceptionSlaPolicy } from '@/api/exceptionSlaPolicy'
import { useExceptionSlaPolicyList } from './useExceptionSlaPolicyList'

const t = (key: string) => key

const policy = (overrides: Partial<ExceptionSlaPolicy> = {}) =>
  ({
    id: '1',
    category: 'GENERAL',
    priority: 'HIGH',
    dueHours: 24,
    escalationEnabled: true,
    escalateToPriority: 'URGENT',
    enabled: true,
    ...overrides
  }) as ExceptionSlaPolicy

const createList = (overrides: Partial<Parameters<typeof useExceptionSlaPolicyList>[1]> = {}) =>
  useExceptionSlaPolicyList(t, {
    getPolicies: vi.fn(async () => ({ records: [policy()], total: 1 } as any)),
    onError: vi.fn(),
    ...overrides
  })

describe('exception SLA policy list', () => {
  it('sends filled filters and maps enabled string to boolean', async () => {
    const getPolicies = vi.fn(async () => ({ records: [], total: 3 } as any))
    const list = createList({ getPolicies })

    list.pagination.page = 4
    list.queryForm.category = 'GENERAL'
    list.queryForm.priority = 'HIGH'
    list.queryForm.enabled = 'true'
    await list.handleQuery()

    expect(getPolicies).toHaveBeenCalledWith({
      category: 'GENERAL',
      priority: 'HIGH',
      enabled: true,
      pageNo: 1,
      pageSize: 20
    })
    expect(list.pagination.total).toBe(3)

    list.queryForm.enabled = 'false'
    await list.handleQuery()
    expect(getPolicies).toHaveBeenLastCalledWith(expect.objectContaining({ enabled: false }))
  })

  it('keeps the page when paging and returns to page 1 on size change or reset', async () => {
    const getPolicies = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getPolicies })

    await list.handlePageChange(3)
    expect(list.pagination.page).toBe(3)

    await list.handleSizeChange(50)
    expect(list.pagination.size).toBe(50)
    expect(list.pagination.page).toBe(1)

    list.queryForm.category = 'X'
    list.pagination.page = 2
    await list.handleReset()
    expect(list.queryForm.category).toBe('')
    expect(list.pagination.page).toBe(1)
  })

  it('reports load failures and clears loading', async () => {
    const onError = vi.fn()
    const list = createList({
      getPolicies: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    await list.loadData()
    expect(onError).toHaveBeenCalledWith('exceptionSlaPolicy.message.loadFailed')
    expect(list.loading.value).toBe(false)
  })
})
