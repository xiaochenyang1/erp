import { computed } from 'vue'

import type { Notification } from '@/api/notification'

type Translate = (key: string) => string
type NotificationTagType = 'info' | 'warning'
export type NotificationReadStatus = 'ALL' | 'UNREAD'

/** Labels and row-state helpers for the notification center. */
export const useSystemNotificationPresentation = (t: Translate) => {
  const readStatusOptions = computed<Array<{
    label: string
    value: NotificationReadStatus
  }>>(() => [
    { label: t('systemNotifications.unread'), value: 'UNREAD' },
    { label: t('systemNotifications.all'), value: 'ALL' }
  ])

  const notificationTypeLabel = (row: Notification) =>
    row.notificationType || row.type || '-'

  const notificationStatusLabel = (row: Notification) =>
    t(row.readFlag ? 'systemNotifications.read' : 'systemNotifications.unread')

  const notificationStatusTagType = (row: Notification): NotificationTagType =>
    row.readFlag ? 'info' : 'warning'

  const canSelectNotification = (row: Notification) => !row.readFlag

  return {
    canSelectNotification,
    notificationStatusLabel,
    notificationStatusTagType,
    notificationTypeLabel,
    readStatusOptions
  }
}
