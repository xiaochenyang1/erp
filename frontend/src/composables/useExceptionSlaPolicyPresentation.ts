import { computed, type Component, type Ref } from 'vue'

import type { ExceptionSlaPolicy } from '@/api/exceptionSlaPolicy'
import { formatLocalizedDateTime } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

export type ExceptionSlaOption = { label: string; value: string }

const PRIORITY_TAG_TYPES: Record<string, TagType> = {
  LOW: 'info',
  MEDIUM: 'primary',
  HIGH: 'warning',
  URGENT: 'danger'
}

/** Labels, tags and summary counters for the exception SLA policy page. */
export const useExceptionSlaPolicyPresentation = (
  t: Translate,
  tableData: Ref<ExceptionSlaPolicy[]>,
  icons: {
    circleCheck: Component
    circleClose: Component
    trendCharts: Component
    alarmClock: Component
    warning: Component
  }
) => {
  const categoryOptions = computed<ExceptionSlaOption[]>(() => [
    { label: t('exceptionSlaPolicy.categories.general'), value: 'GENERAL' },
    { label: t('exceptionSlaPolicy.categories.lowStock'), value: 'LOW_STOCK' },
    { label: t('exceptionSlaPolicy.categories.paymentOverdue'), value: 'PAYMENT_OVERDUE' },
    { label: t('exceptionSlaPolicy.categories.deliveryDelay'), value: 'DELIVERY_DELAY' },
    { label: t('exceptionSlaPolicy.categories.qualityIssue'), value: 'QUALITY_ISSUE' },
    { label: t('exceptionSlaPolicy.categories.systemError'), value: 'SYSTEM_ERROR' }
  ])

  const priorityOptions = computed<ExceptionSlaOption[]>(() => [
    { label: t('exceptionSlaPolicy.priorities.low'), value: 'LOW' },
    { label: t('exceptionSlaPolicy.priorities.medium'), value: 'MEDIUM' },
    { label: t('exceptionSlaPolicy.priorities.high'), value: 'HIGH' },
    { label: t('exceptionSlaPolicy.priorities.urgent'), value: 'URGENT' }
  ])

  const averageDueHours = computed(() => {
    if (!tableData.value.length) return '-'
    const total = tableData.value.reduce((sum, item) => sum + (item.dueHours || 0), 0)
    return t('exceptionSlaPolicy.hours', { count: Math.round(total / tableData.value.length) })
  })

  const summaryItems = computed(() => [
    {
      label: t('exceptionSlaPolicy.summary.enabledPolicies'),
      value: tableData.value.filter((item) => item.enabled).length,
      icon: icons.circleCheck,
      tone: 'green'
    },
    {
      label: t('exceptionSlaPolicy.summary.disabledPolicies'),
      value: tableData.value.filter((item) => !item.enabled).length,
      icon: icons.circleClose,
      tone: 'gray'
    },
    {
      label: t('exceptionSlaPolicy.summary.escalationEnabled'),
      value: tableData.value.filter((item) => item.escalationEnabled).length,
      icon: icons.trendCharts,
      tone: 'blue'
    },
    {
      label: t('exceptionSlaPolicy.summary.averageLimit'),
      value: averageDueHours.value,
      icon: icons.alarmClock,
      tone: 'orange'
    },
    {
      label: t('exceptionSlaPolicy.summary.urgentPolicies'),
      value: tableData.value.filter((item) => item.priority === 'URGENT').length,
      icon: icons.warning,
      tone: 'red'
    }
  ])

  const categoryLabel = (value?: string) =>
    categoryOptions.value.find((item) => item.value === value)?.label || value || '-'

  const priorityLabel = (value?: string) =>
    priorityOptions.value.find((item) => item.value === value)?.label || value || '-'

  const priorityType = (value?: string): TagType =>
    (value && PRIORITY_TAG_TYPES[value]) || 'info'

  const formatDateTime = (value?: string) => formatLocalizedDateTime(value) || '-'

  return {
    categoryLabel,
    categoryOptions,
    formatDateTime,
    priorityLabel,
    priorityOptions,
    priorityType,
    summaryItems
  }
}
