import { describe, expect, it, vi } from 'vitest'

import type { ExceptionTicket } from '@/api/exceptionTicket'
import { normalizeOptionalId, useExceptionTicketList } from './useExceptionTicketList'

const t = (key: string) => key

const row = (overrides: Partial<ExceptionTicket> = {}) =>
  ({
    id: '1',
    ticketNo: 'ET1',
    category: 'GENERAL',
    priority: 'HIGH',
    title: 't',
    status: 'OPEN',
    events: [],
    ...overrides
  }) as ExceptionTicket

const createList = (overrides: Partial<Parameters<typeof useExceptionTicketList>[1]> = {}) =>
  useExceptionTicketList(t, {
    getTickets: vi.fn(async () => ({ records: [row()], total: 1 } as any)),
    onError: vi.fn(),
    ...overrides
  })

describe('exception ticket list', () => {
  it('normalizes optional ids by trimming blanks', () => {
    expect(normalizeOptionalId(' 12 ')).toBe('12')
    expect(normalizeOptionalId('   ')).toBeUndefined()
    expect(normalizeOptionalId(undefined)).toBeUndefined()
  })

  it('sends only filled filters and resets paging on query', async () => {
    const getTickets = vi.fn(async () => ({ records: [], total: 5 } as any))
    const list = createList({ getTickets })

    list.pagination.page = 4
    list.queryForm.keyword = 'ET'
    list.queryForm.status = 'OPEN'
    list.queryForm.priority = 'HIGH'
    list.queryForm.category = 'GENERAL'
    list.queryForm.assigneeUserId = ' 9 '
    list.queryForm.sourceNo = 'SO1'
    list.queryForm.overdueOnly = true
    await list.handleQuery()

    expect(getTickets).toHaveBeenCalledWith({
      keyword: 'ET',
      status: 'OPEN',
      priority: 'HIGH',
      category: 'GENERAL',
      assigneeUserId: '9',
      sourceNo: 'SO1',
      overdueOnly: true,
      pageNo: 1,
      pageSize: 20
    })
    expect(list.pagination.total).toBe(5)
  })

  it('keeps the page when paging and returns to page 1 on size change or reset', async () => {
    const getTickets = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getTickets })

    await list.handlePageChange(3)
    expect(list.pagination.page).toBe(3)
    expect(getTickets).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 3 }))

    await list.handleSizeChange(50)
    expect(list.pagination.size).toBe(50)
    expect(list.pagination.page).toBe(1)

    list.queryForm.keyword = 'X'
    list.queryForm.overdueOnly = true
    list.pagination.page = 2
    await list.handleReset()
    expect(list.queryForm.keyword).toBe('')
    expect(list.queryForm.overdueOnly).toBe(false)
    expect(list.pagination.page).toBe(1)
  })

  it('reports load failures and clears loading', async () => {
    const onError = vi.fn()
    const list = createList({
      getTickets: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    await list.loadData()
    expect(onError).toHaveBeenCalledWith('exceptionTicket.message.loadFailed')
    expect(list.loading.value).toBe(false)
  })
})
