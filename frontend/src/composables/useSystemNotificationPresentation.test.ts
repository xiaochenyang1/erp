import { describe, expect, it } from 'vitest'

import type { Notification } from '@/api/notification'
import { useSystemNotificationPresentation } from './useSystemNotificationPresentation'

const t = (key: string) => key
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

describe('system notification presentation', () => {
  it('provides localized read filters and row status rendering', () => {
    const presentation = useSystemNotificationPresentation(t)

    expect(presentation.readStatusOptions.value).toEqual([
      { label: 'systemNotifications.unread', value: 'UNREAD' },
      { label: 'systemNotifications.all', value: 'ALL' }
    ])
    expect(presentation.notificationStatusLabel(notification())).toBe('systemNotifications.unread')
    expect(presentation.notificationStatusTagType(notification())).toBe('warning')
    expect(presentation.canSelectNotification(notification())).toBe(true)

    const read = notification({ readFlag: true })
    expect(presentation.notificationStatusLabel(read)).toBe('systemNotifications.read')
    expect(presentation.notificationStatusTagType(read)).toBe('info')
    expect(presentation.canSelectNotification(read)).toBe(false)
  })

  it('prefers the normalized notification type with a visible fallback', () => {
    const { notificationTypeLabel } = useSystemNotificationPresentation(t)

    expect(notificationTypeLabel(notification({ notificationType: 'WORKFLOW', type: 'LEGACY' }))).toBe('WORKFLOW')
    expect(notificationTypeLabel(notification({ type: 'LEGACY' }))).toBe('LEGACY')
    expect(notificationTypeLabel(notification())).toBe('-')
  })
})
