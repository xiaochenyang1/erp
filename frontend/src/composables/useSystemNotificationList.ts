import { reactive, ref } from 'vue'

import type {
  Notification,
  NotificationQuery,
  UnreadCount
} from '@/api/notification'
import type { PageResponse } from '@/types/common'
import type { NotificationReadStatus } from './useSystemNotificationPresentation'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

/** Query, detail and all read-state mutations for the notification center. */
export const useSystemNotificationList = (
  t: Translate,
  options: {
    getNotifications: (params: NotificationQuery) => Promise<PageResponse<Notification>>
    getUnreadCount: () => Promise<UnreadCount>
    markNotificationRead: (recipientId: string | number) => Promise<unknown>
    markAllNotificationsRead: () => Promise<unknown>
    markNotificationsReadBatch: (
      recipientIds: Array<string | number>
    ) => Promise<{ updated: number }>
    formatNumber: (value: number) => string
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
    reportLoadError?: (error: unknown) => void
  }
) => {
  const queryForm = reactive<NotificationQuery>({
    category: '',
    notificationType: ''
  })
  const readStatus = ref<NotificationReadStatus>('ALL')
  const unreadCount = ref(0)
  const selectedRows = ref<Notification[]>([])
  const pagination = reactive({
    page: 1,
    size: 20,
    total: 0
  })
  const loading = ref(false)
  const tableData = ref<Notification[]>([])
  const detailVisible = ref(false)
  const detailData = ref<Notification>({} as Notification)

  const buildQueryParams = (): NotificationQuery => ({
    category: queryForm.category?.trim() || undefined,
    notificationType: queryForm.notificationType?.trim() || undefined,
    unreadOnly: readStatus.value === 'UNREAD' ? true : undefined,
    pageNo: pagination.page,
    pageSize: pagination.size
  })

  const loadUnreadCount = async () => {
    const result = await options.getUnreadCount()
    unreadCount.value = result.unreadCount
  }

  const loadData = async () => {
    loading.value = true
    try {
      const result = await options.getNotifications(buildQueryParams())
      tableData.value = result.records || []
      pagination.total = result.total || 0
      await loadUnreadCount()
      return true
    } catch (error) {
      options.reportLoadError?.(error)
      options.onError?.(t('systemNotifications.message.loadFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  const handleQuery = async () => {
    pagination.page = 1
    return loadData()
  }

  const handleReset = async () => {
    queryForm.category = ''
    queryForm.notificationType = ''
    readStatus.value = 'ALL'
    pagination.page = 1
    return loadData()
  }

  const handlePageChange = async () => loadData()

  const onSelectionChange = (rows: Notification[]) => {
    selectedRows.value = rows
  }

  const handleMarkRead = async (row: Notification, showMessage = true) => {
    try {
      await options.markNotificationRead(row.recipientId)
      if (showMessage) {
        options.onSuccess?.(t('systemNotifications.message.markedRead'))
      }
      await loadData()
      return true
    } catch {
      options.onError?.(t('systemNotifications.message.markReadFailed'))
      return false
    }
  }

  const handleView = async (row: Notification) => {
    detailData.value = row
    detailVisible.value = true
    return row.readFlag ? true : handleMarkRead(row, false)
  }

  const handleMarkAllRead = async () => {
    try {
      await options.markAllNotificationsRead()
      options.onSuccess?.(t('systemNotifications.message.allMarkedRead'))
      selectedRows.value = []
      await loadData()
      return true
    } catch {
      options.onError?.(t('systemNotifications.message.markAllReadFailed'))
      return false
    }
  }

  const handleMarkSelectedRead = async () => {
    const ids = selectedRows.value
      .filter((row) => !row.readFlag)
      .map((row) => row.recipientId)
    if (!ids.length) {
      options.onWarning?.(t('systemNotifications.message.selectUnread'))
      return false
    }
    try {
      const result = await options.markNotificationsReadBatch(ids)
      options.onSuccess?.(t('systemNotifications.message.batchMarkedRead', {
        count: options.formatNumber(result?.updated ?? ids.length)
      }))
      selectedRows.value = []
      await loadData()
      return true
    } catch {
      options.onError?.(t('systemNotifications.message.batchMarkReadFailed'))
      return false
    }
  }

  return {
    buildQueryParams,
    detailData,
    detailVisible,
    handleMarkAllRead,
    handleMarkRead,
    handleMarkSelectedRead,
    handlePageChange,
    handleQuery,
    handleReset,
    handleView,
    loadData,
    loadUnreadCount,
    loading,
    onSelectionChange,
    pagination,
    queryForm,
    readStatus,
    selectedRows,
    tableData,
    unreadCount
  }
}
