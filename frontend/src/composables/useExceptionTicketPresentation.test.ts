import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

import type { ExceptionTicket } from '@/api/exceptionTicket'
import { useExceptionTicketPresentation } from './useExceptionTicketPresentation'

const t = (key: string) => key
const Warning = { name: 'Warning' }
const Clock = { name: 'Clock' }
const Finished = { name: 'Finished' }

const ticket = (overrides: Partial<ExceptionTicket> = {}) =>
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

describe('exception ticket presentation', () => {
  it('builds filter options and maps labels/tags', () => {
    const presentation = useExceptionTicketPresentation(t, ref([]), {
      warning: Warning,
      clock: Clock,
      finished: Finished
    })

    expect(presentation.statusOptions.value.map((item) => item.value))
      .toEqual(['OPEN', 'PROCESSING', 'RESOLVED', 'CLOSED'])
    expect(presentation.priorityOptions.value).toHaveLength(4)
    expect(presentation.categoryOptions.value).toHaveLength(6)
    expect(presentation.statusLabel('OPEN')).toBe('exceptionTicket.statuses.open')
    expect(presentation.priorityLabel('URGENT')).toBe('exceptionTicket.priorities.urgent')
    expect(presentation.categoryLabel('LOW_STOCK')).toBe('exceptionTicket.categories.lowStock')
    expect(presentation.actionLabel('ASSIGN')).toBe('exceptionTicket.eventActions.assign')
    expect(presentation.statusType('OPEN')).toBe('warning')
    expect(presentation.priorityType('URGENT')).toBe('danger')
    expect(presentation.eventType('RESOLVE')).toBe('success')
    expect(presentation.eventType('ASSIGN')).toBe('warning')
    expect(presentation.eventType('CREATE')).toBe('primary')
  })

  it('detects overdue open tickets and summarizes the current page', () => {
    const now = Date.now()
    vi.setSystemTime(new Date(now))

    const rows = ref([
      ticket({ status: 'OPEN', dueTime: new Date(now - 60_000).toISOString() }),
      ticket({ id: '2', status: 'PROCESSING', priority: 'URGENT', dueTime: new Date(now + 60_000).toISOString() }),
      ticket({ id: '3', status: 'RESOLVED', priority: 'HIGH', dueTime: new Date(now - 60_000).toISOString() }),
      ticket({ id: '4', status: 'OPEN', priority: 'LOW' })
    ])
    const presentation = useExceptionTicketPresentation(t, rows, {
      warning: Warning,
      clock: Clock,
      finished: Finished
    })

    expect(presentation.isOverdue(rows.value[0])).toBe(true)
    expect(presentation.isOverdue(rows.value[1])).toBe(false)
    expect(presentation.isOverdue(rows.value[2])).toBe(false)

    const summary = presentation.summaryItems.value
    expect(summary.find((item) => item.tone === 'orange')?.value).toBe(2)
    expect(summary.find((item) => item.tone === 'blue')?.value).toBe(1)
    expect(summary.find((item) => item.tone === 'green')?.value).toBe(1)
    expect(summary.find((item) => item.tone === 'red')?.value).toBe(1)
    expect(summary.find((item) => item.tone === 'purple')?.value).toBe(3)

    vi.useRealTimers()
  })
})
