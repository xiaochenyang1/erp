import { computed, type Component, type Ref } from 'vue'

import type {
  ExceptionTicket,
  ExceptionTicketPriority,
  ExceptionTicketStatus
} from '@/api/exceptionTicket'
import { formatLocalizedDateTime } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

export type ExceptionTicketOption = { label: string; value: string }

const STATUS_TAG_TYPES: Record<string, TagType> = {
  OPEN: 'warning',
  PROCESSING: 'primary',
  RESOLVED: 'success',
  CLOSED: 'info'
}

const PRIORITY_TAG_TYPES: Record<string, TagType> = {
  LOW: 'info',
  MEDIUM: 'primary',
  HIGH: 'warning',
  URGENT: 'danger'
}

const EVENT_ACTION_KEYS: Record<string, string> = {
  CREATE: 'exceptionTicket.eventActions.create',
  ASSIGN: 'exceptionTicket.eventActions.assign',
  START: 'exceptionTicket.eventActions.start',
  RESOLVE: 'exceptionTicket.eventActions.resolve',
  CLOSE: 'exceptionTicket.eventActions.close'
}

/** Labels, tags and summary counters for the exception ticket page. */
export const useExceptionTicketPresentation = (
  t: Translate,
  tableData: Ref<ExceptionTicket[]>,
  icons: {
    warning: Component
    clock: Component
    finished: Component
  }
) => {
  const statusOptions = computed<ExceptionTicketOption[]>(() => [
    { label: t('exceptionTicket.statuses.open'), value: 'OPEN' },
    { label: t('exceptionTicket.statuses.processing'), value: 'PROCESSING' },
    { label: t('exceptionTicket.statuses.resolved'), value: 'RESOLVED' },
    { label: t('exceptionTicket.statuses.closed'), value: 'CLOSED' }
  ])

  const priorityOptions = computed<ExceptionTicketOption[]>(() => [
    { label: t('exceptionTicket.priorities.low'), value: 'LOW' },
    { label: t('exceptionTicket.priorities.medium'), value: 'MEDIUM' },
    { label: t('exceptionTicket.priorities.high'), value: 'HIGH' },
    { label: t('exceptionTicket.priorities.urgent'), value: 'URGENT' }
  ])

  const categoryOptions = computed<ExceptionTicketOption[]>(() => [
    { label: t('exceptionTicket.categories.general'), value: 'GENERAL' },
    { label: t('exceptionTicket.categories.lowStock'), value: 'LOW_STOCK' },
    { label: t('exceptionTicket.categories.paymentOverdue'), value: 'PAYMENT_OVERDUE' },
    { label: t('exceptionTicket.categories.deliveryDelay'), value: 'DELIVERY_DELAY' },
    { label: t('exceptionTicket.categories.qualityIssue'), value: 'QUALITY_ISSUE' },
    { label: t('exceptionTicket.categories.systemError'), value: 'SYSTEM_ERROR' }
  ])

  const isOverdue = (ticket: ExceptionTicket) => {
    if (!ticket.dueTime || ticket.status === 'RESOLVED' || ticket.status === 'CLOSED') return false
    return Date.parse(ticket.dueTime) < Date.now()
  }

  const countByStatus = (status: ExceptionTicketStatus | string) =>
    tableData.value.filter((item) => item.status === status).length

  const summaryItems = computed(() => [
    {
      label: t('exceptionTicket.summary.open'),
      value: countByStatus('OPEN'),
      icon: icons.warning,
      tone: 'orange'
    },
    {
      label: t('exceptionTicket.summary.processing'),
      value: countByStatus('PROCESSING'),
      icon: icons.clock,
      tone: 'blue'
    },
    {
      label: t('exceptionTicket.summary.resolved'),
      value: countByStatus('RESOLVED'),
      icon: icons.finished,
      tone: 'green'
    },
    {
      label: t('exceptionTicket.summary.overdue'),
      value: tableData.value.filter(isOverdue).length,
      icon: icons.warning,
      tone: 'red'
    },
    {
      label: t('exceptionTicket.summary.highPriority'),
      value: tableData.value.filter(
        (item) => item.priority === 'HIGH' || item.priority === 'URGENT'
      ).length,
      icon: icons.warning,
      tone: 'purple'
    }
  ])

  const statusLabel = (status?: string) =>
    statusOptions.value.find((item) => item.value === status)?.label || status || '-'

  const priorityLabel = (priority?: string) =>
    priorityOptions.value.find((item) => item.value === priority)?.label || priority || '-'

  const categoryLabel = (category?: string) =>
    categoryOptions.value.find((item) => item.value === category)?.label || category || '-'

  const actionLabel = (action?: string) => {
    if (!action) return '-'
    const key = EVENT_ACTION_KEYS[action]
    return key ? t(key) : action
  }

  const statusType = (status?: string): TagType =>
    (status && STATUS_TAG_TYPES[status]) || 'info'

  const priorityType = (priority?: ExceptionTicketPriority | string): TagType =>
    (priority && PRIORITY_TAG_TYPES[priority]) || 'info'

  const eventType = (action?: string): TagType => {
    if (action === 'CLOSE') return 'info'
    if (action === 'RESOLVE') return 'success'
    if (action === 'ASSIGN') return 'warning'
    return 'primary'
  }

  const formatDateTime = (value?: string) => formatLocalizedDateTime(value)

  return {
    actionLabel,
    categoryLabel,
    categoryOptions,
    eventType,
    formatDateTime,
    isOverdue,
    priorityLabel,
    priorityOptions,
    priorityType,
    statusLabel,
    statusOptions,
    statusType,
    summaryItems
  }
}
