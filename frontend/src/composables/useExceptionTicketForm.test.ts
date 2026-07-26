import { describe, expect, it, vi } from 'vitest'

import type { ExceptionTicket } from '@/api/exceptionTicket'
import { toIsoDateTime, useExceptionTicketForm } from './useExceptionTicketForm'

const t = (key: string) => key

const row = (overrides: Partial<ExceptionTicket> = {}) =>
  ({
    id: '1',
    ticketNo: 'ET1',
    category: 'GENERAL',
    priority: 'HIGH',
    title: 't',
    status: 'OPEN',
    assigneeUserId: '8',
    events: [],
    ...overrides
  }) as ExceptionTicket

const createForm = (overrides: Partial<Parameters<typeof useExceptionTicketForm>[1]> = {}) =>
  useExceptionTicketForm(t, {
    createTicket: vi.fn(async () => ({})),
    assignTicket: vi.fn(async () => ({})),
    startTicket: vi.fn(async () => ({})),
    resolveTicket: vi.fn(async () => ({})),
    closeTicket: vi.fn(async () => ({})),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('exception ticket form', () => {
  it('converts picker datetime strings to ISO-like values', () => {
    expect(toIsoDateTime('2026-07-26 10:00:00')).toBe('2026-07-26T10:00:00')
    expect(toIsoDateTime(undefined)).toBeUndefined()
  })

  it('opens a blank create form with defaults', () => {
    const form = createForm()
    form.openCreateDialog()

    expect(form.createDialogVisible.value).toBe(true)
    expect(form.createForm).toMatchObject({
      category: 'GENERAL',
      priority: 'HIGH',
      title: '',
      description: ''
    })
  })

  it('creates a ticket with trimmed optional fields and ISO due time', async () => {
    const createTicket = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const form = createForm({ createTicket, onSubmitted })
    form.openCreateDialog()
    form.createForm.title = '缺货'
    form.createForm.description = '  说明  '
    form.createForm.sourceType = ' LOW_STOCK '
    form.createForm.sourceId = ' 99 '
    form.createForm.assigneeUserId = ' 7 '
    form.createForm.dueTime = '2026-07-26 12:00:00'

    expect(await form.handleCreate()).toBe(true)
    expect(createTicket).toHaveBeenCalledWith({
      category: 'GENERAL',
      priority: 'HIGH',
      title: '缺货',
      description: '说明',
      sourceType: 'LOW_STOCK',
      sourceId: '99',
      sourceNo: undefined,
      sourceRoute: undefined,
      assigneeUserId: '7',
      dueTime: '2026-07-26T12:00:00'
    })
    expect(form.createDialogVisible.value).toBe(false)
    expect(onSubmitted).toHaveBeenCalled()
  })

  it('opens action dialogs and dispatches assign/start/resolve/close', async () => {
    const assignTicket = vi.fn(async () => ({}))
    const startTicket = vi.fn(async () => ({}))
    const resolveTicket = vi.fn(async () => ({}))
    const closeTicket = vi.fn(async () => ({}))
    const form = createForm({ assignTicket, startTicket, resolveTicket, closeTicket })

    form.openActionDialog('assign', row())
    expect(form.actionDialogTitle.value).toContain('exceptionTicket.dialog.assign')
    expect(form.actionForm.assigneeUserId).toBe('8')
    form.actionForm.assigneeUserId = ' 3 '
    form.actionForm.comment = ' 分派 '
    expect(await form.handleAction()).toBe(true)
    expect(assignTicket).toHaveBeenCalledWith('1', { assigneeUserId: '3', comment: '分派' })

    form.openActionDialog('start', row())
    form.actionForm.comment = '开始'
    expect(await form.handleAction()).toBe(true)
    expect(startTicket).toHaveBeenCalledWith('1', { comment: '开始' })

    form.openActionDialog('resolve', row())
    expect(await form.handleAction()).toBe(true)
    expect(resolveTicket).toHaveBeenCalledWith('1', { comment: undefined })

    form.openActionDialog('close', row())
    expect(await form.handleAction()).toBe(true)
    expect(closeTicket).toHaveBeenCalledWith('1', { comment: undefined })
  })

  it('reports create and action failures without closing dialogs', async () => {
    const onError = vi.fn()
    const form = createForm({
      createTicket: vi.fn(async () => { throw new Error('boom') }),
      assignTicket: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    form.openCreateDialog()
    form.createForm.title = 'x'
    expect(await form.handleCreate()).toBe(false)
    expect(form.createDialogVisible.value).toBe(true)
    expect(onError).toHaveBeenCalledWith('exceptionTicket.message.createFailed')

    form.openActionDialog('assign', row())
    expect(await form.handleAction()).toBe(false)
    expect(form.actionDialogVisible.value).toBe(true)
    expect(onError).toHaveBeenCalledWith('exceptionTicket.message.actionFailed')
    expect(form.submitLoading.value).toBe(false)
  })
})
