import { describe, expect, it, vi } from 'vitest'

import type { Notification } from '@/api/notification'
import { useSystemNotificationList } from './useSystemNotificationList'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

const notification = (overrides: Partial<Notification> = {}): Notification => ({
  id: 'r1',
  recipientId: 'r1',
  notificationId: 'n1',
  title: 'Approval',
  content: 'Pending approval',
  readFlag: false,
  createdTime: '2026-07-28T10:00:00',
  ...overrides
})

const page = (records: Notification[] = [notification()], total = records.length) => ({
  records,
  total,
  pageNo: 1,
  pageSize: 20
})

const createList = (
  overrides: Partial<Parameters<typeof useSystemNotificationList>[1]> = {}
) => useSystemNotificationList(t, {
  getNotifications: vi.fn(async () => page()),
  getUnreadCount: vi.fn(async () => ({ unreadCount: 3 })),
  markNotificationRead: vi.fn(async () => ({})),
  markAllNotificationsRead: vi.fn(async () => ({})),
  markNotificationsReadBatch: vi.fn(async (ids) => ({ updated: ids.length })),
  formatNumber: (value) => `#${value}`,
  onError: vi.fn(),
  onSuccess: vi.fn(),
  onWarning: vi.fn(),
  reportLoadError: vi.fn(),
  ...overrides
})

describe('system notification list', () => {
  it('normalizes filters and loads the page with the unread summary', async () => {
    const getNotifications = vi.fn(async () => page([notification()], 8))
    const getUnreadCount = vi.fn(async () => ({ unreadCount: 5 }))
    const list = createList({ getNotifications, getUnreadCount })
    list.queryForm.category = ' TODO '
    list.queryForm.notificationType = ' WORKFLOW '
    list.readStatus.value = 'UNREAD'
    list.pagination.page = 2

    expect(await list.loadData()).toBe(true)
    expect(getNotifications).toHaveBeenCalledWith({
      category: 'TODO',
      notificationType: 'WORKFLOW',
      unreadOnly: true,
      pageNo: 2,
      pageSize: 20
    })
    expect(list.tableData.value).toEqual([notification()])
    expect(list.pagination.total).toBe(8)
    expect(list.unreadCount.value).toBe(5)
    expect(list.loading.value).toBe(false)
  })

  it('queries, resets and reloads the current pagination state', async () => {
    const getNotifications = vi.fn(async () => page([]))
    const list = createList({ getNotifications })
    list.pagination.page = 4
    await list.handleQuery()
    expect(list.pagination.page).toBe(1)

    list.queryForm.category = 'NOTICE'
    list.queryForm.notificationType = 'SYSTEM'
    list.readStatus.value = 'UNREAD'
    list.pagination.page = 3
    await list.handleReset()
    expect(list.queryForm).toMatchObject({ category: '', notificationType: '' })
    expect(list.readStatus.value).toBe('ALL')
    expect(list.pagination.page).toBe(1)

    list.pagination.page = 2
    await list.handlePageChange()
    expect(getNotifications).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 2 }))
  })

  it('opens details and marks only unread rows as read', async () => {
    const markNotificationRead = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({ markNotificationRead, onSuccess })
    const unread = notification()

    expect(await list.handleView(unread)).toBe(true)
    expect(list.detailVisible.value).toBe(true)
    expect(list.detailData.value).toEqual(unread)
    expect(markNotificationRead).toHaveBeenCalledWith('r1')
    expect(onSuccess).not.toHaveBeenCalledWith('systemNotifications.message.markedRead')

    markNotificationRead.mockClear()
    expect(await list.handleView(notification({ readFlag: true }))).toBe(true)
    expect(markNotificationRead).not.toHaveBeenCalled()

    expect(await list.handleMarkRead(unread)).toBe(true)
    expect(onSuccess).toHaveBeenCalledWith('systemNotifications.message.markedRead')
  })

  it('marks all notifications and only selected unread recipients', async () => {
    const markAllNotificationsRead = vi.fn(async () => ({}))
    const markNotificationsReadBatch = vi.fn(async () => ({ updated: 1 }))
    const onSuccess = vi.fn()
    const list = createList({
      markAllNotificationsRead,
      markNotificationsReadBatch,
      onSuccess
    })
    list.onSelectionChange([
      notification({ recipientId: 'r1' }),
      notification({ recipientId: 'r2', readFlag: true })
    ])

    expect(await list.handleMarkSelectedRead()).toBe(true)
    expect(markNotificationsReadBatch).toHaveBeenCalledWith(['r1'])
    expect(onSuccess).toHaveBeenCalledWith(
      'systemNotifications.message.batchMarkedRead:{"count":"#1"}'
    )
    expect(list.selectedRows.value).toEqual([])

    list.onSelectionChange([notification()])
    expect(await list.handleMarkAllRead()).toBe(true)
    expect(markAllNotificationsRead).toHaveBeenCalledOnce()
    expect(onSuccess).toHaveBeenCalledWith('systemNotifications.message.allMarkedRead')
    expect(list.selectedRows.value).toEqual([])
  })

  it('warns instead of calling the batch API when no unread row is selected', async () => {
    const markNotificationsReadBatch = vi.fn(async () => ({ updated: 0 }))
    const onWarning = vi.fn()
    const list = createList({ markNotificationsReadBatch, onWarning })
    list.onSelectionChange([notification({ readFlag: true })])

    expect(await list.handleMarkSelectedRead()).toBe(false)
    expect(markNotificationsReadBatch).not.toHaveBeenCalled()
    expect(onWarning).toHaveBeenCalledWith('systemNotifications.message.selectUnread')
  })

  it('reports load and read-action failures with side-specific feedback', async () => {
    const loadFailure = new Error('load')
    const onError = vi.fn()
    const reportLoadError = vi.fn()
    const list = createList({
      getNotifications: vi.fn(async () => { throw loadFailure }),
      markNotificationRead: vi.fn(async () => { throw new Error('read') }),
      markAllNotificationsRead: vi.fn(async () => { throw new Error('all') }),
      markNotificationsReadBatch: vi.fn(async () => { throw new Error('batch') }),
      onError,
      reportLoadError
    })

    expect(await list.loadData()).toBe(false)
    expect(reportLoadError).toHaveBeenCalledWith(loadFailure)
    expect(await list.handleMarkRead(notification())).toBe(false)
    expect(await list.handleMarkAllRead()).toBe(false)
    list.onSelectionChange([notification()])
    expect(await list.handleMarkSelectedRead()).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemNotifications.message.loadFailed')
    expect(onError).toHaveBeenCalledWith('systemNotifications.message.markReadFailed')
    expect(onError).toHaveBeenCalledWith('systemNotifications.message.markAllReadFailed')
    expect(onError).toHaveBeenCalledWith('systemNotifications.message.batchMarkReadFailed')
    expect(list.loading.value).toBe(false)
  })
})
