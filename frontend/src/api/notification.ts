import { request } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

// ==================== 通知消息 ====================

export interface Notification {
  id: string
  recipientId: string
  notificationId: string
  type?: string
  category?: string
  notificationType?: string
  title: string
  content: string
  bizType?: string
  bizId?: string
  bizNo?: string
  businessType?: string
  businessId?: string
  businessNo?: string
  targetUrl?: string
  readFlag: boolean
  readTime?: string
  createdTime: string
}

export interface NotificationQuery extends PageQuery {
  type?: string
  readFlag?: boolean
  unreadOnly?: boolean
  category?: string
  notificationType?: string
}

export interface UnreadCount {
  count?: number
  unreadCount: number
}

// 通知API
export const getNotifications = (params: NotificationQuery) => {
  return request.get<PageResponse<Notification>>('/system/notifications', {
    params: {
      ...params,
      unreadOnly: params.unreadOnly ?? (params.readFlag === false ? true : undefined),
      notificationType: params.notificationType || params.type || undefined
    }
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizeNotification)
  }))
}

export const getUnreadCount = () => {
  return request.get<UnreadCount>('/system/notifications/unread-count').then((count) => ({
    ...count,
    unreadCount: count.unreadCount ?? count.count ?? 0
  }))
}

export const markNotificationRead = (recipientId: string | number) => {
  return request.post<Notification>(`/system/notifications/${recipientId}/read`).then(normalizeNotification)
}

export const markAllNotificationsRead = () => {
  return request.post('/system/notifications/read-all')
}

export const markNotificationsReadBatch = (recipientIds: Array<string | number>) => {
  return request.post<{ updated: number }>('/system/notifications/read-batch', {
    recipientIds: recipientIds.map((id) => String(id))
  })
}

const normalizeNotification = (notification: Notification): Notification => ({
  ...notification,
  id: String(notification.recipientId ?? notification.id),
  recipientId: String(notification.recipientId ?? notification.id),
  notificationId: String(notification.notificationId ?? notification.id),
  type: notification.notificationType ?? notification.type ?? '',
  bizType: notification.businessType ?? notification.bizType,
  bizId: notification.businessId != null ? String(notification.businessId) : notification.bizId,
  bizNo: notification.businessNo ?? notification.bizNo
})
