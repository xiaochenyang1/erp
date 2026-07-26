import { computed, type Component, type Ref } from 'vue'

import type { ExceptionRule } from '@/api/exceptionRule'
import { formatLocalizedDateTime } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

export type ExceptionRuleOption = { label: string; value: string }

const PRIORITY_TAG_TYPES: Record<string, TagType> = {
  LOW: 'info',
  MEDIUM: 'primary',
  HIGH: 'warning',
  URGENT: 'danger'
}

const SCAN_STATUS_TAG_TYPES: Record<string, TagType> = {
  SUCCESS: 'success',
  FAILED: 'danger',
  SKIPPED: 'info'
}

const SCAN_STATUS_KEYS: Record<string, string> = {
  SUCCESS: 'exceptionRule.scanStatuses.success',
  FAILED: 'exceptionRule.scanStatuses.failed',
  SKIPPED: 'exceptionRule.scanStatuses.skipped'
}

/** Labels, tags and summary counters for the exception rule page. */
export const useExceptionRulePresentation = (
  t: Translate,
  ruleData: Ref<ExceptionRule[]>,
  icons: {
    circleCheck: Component
    circleClose: Component
    warning: Component
    tickets: Component
    finished: Component
  }
) => {
  const ruleTypeOptions = computed<ExceptionRuleOption[]>(() => [
    { label: t('exceptionRule.ruleTypes.lowStock'), value: 'LOW_STOCK' },
    { label: t('exceptionRule.ruleTypes.receivableOverdue'), value: 'RECEIVABLE_OVERDUE' },
    { label: t('exceptionRule.ruleTypes.payableOverdue'), value: 'PAYABLE_OVERDUE' },
    { label: t('exceptionRule.ruleTypes.operationFailure'), value: 'OPERATION_FAILURE' }
  ])

  const thresholdUnitOptions = computed<ExceptionRuleOption[]>(() => [
    { label: t('exceptionRule.units.quantity'), value: 'QTY' },
    { label: t('exceptionRule.units.days'), value: 'DAYS' },
    { label: t('exceptionRule.units.minutes'), value: 'MINUTES' },
    { label: t('exceptionRule.units.count'), value: 'COUNT' }
  ])

  const priorityOptions = computed<ExceptionRuleOption[]>(() => [
    { label: t('exceptionRule.priorities.low'), value: 'LOW' },
    { label: t('exceptionRule.priorities.medium'), value: 'MEDIUM' },
    { label: t('exceptionRule.priorities.high'), value: 'HIGH' },
    { label: t('exceptionRule.priorities.urgent'), value: 'URGENT' }
  ])

  const summaryItems = computed(() => [
    {
      label: t('exceptionRule.summary.enabledRules'),
      value: ruleData.value.filter((item) => item.enabled).length,
      icon: icons.circleCheck,
      tone: 'green'
    },
    {
      label: t('exceptionRule.summary.disabledRules'),
      value: ruleData.value.filter((item) => !item.enabled).length,
      icon: icons.circleClose,
      tone: 'gray'
    },
    {
      label: t('exceptionRule.summary.recentHits'),
      value: ruleData.value.reduce((sum, item) => sum + (item.lastHitCount || 0), 0),
      icon: icons.warning,
      tone: 'orange'
    },
    {
      label: t('exceptionRule.summary.newTickets'),
      value: ruleData.value.reduce((sum, item) => sum + (item.lastTicketCreatedCount || 0), 0),
      icon: icons.tickets,
      tone: 'blue'
    },
    {
      label: t('exceptionRule.summary.scanFailures'),
      value: ruleData.value.filter((item) => item.lastScanStatus === 'FAILED').length,
      icon: icons.finished,
      tone: 'red'
    }
  ])

  const ruleTypeLabel = (value?: string) =>
    ruleTypeOptions.value.find((item) => item.value === value)?.label || value || '-'

  const priorityLabel = (value?: string) =>
    priorityOptions.value.find((item) => item.value === value)?.label || value || '-'

  const thresholdUnitLabel = (value?: string) =>
    thresholdUnitOptions.value.find((item) => item.value === value)?.label || value || '-'

  const thresholdLabel = (row: ExceptionRule) =>
    `${row.thresholdValue ?? 0} ${thresholdUnitLabel(row.thresholdUnit)}`

  const priorityType = (value?: string): TagType =>
    (value && PRIORITY_TAG_TYPES[value]) || 'info'

  const scanStatusLabel = (value?: string) => {
    if (!value) return '-'
    const key = SCAN_STATUS_KEYS[value]
    return key ? t(key) : value
  }

  const scanStatusType = (value?: string): TagType =>
    (value && SCAN_STATUS_TAG_TYPES[value]) || 'info'

  const formatDateTime = (value?: string) => formatLocalizedDateTime(value) || '-'

  return {
    formatDateTime,
    priorityLabel,
    priorityOptions,
    priorityType,
    ruleTypeLabel,
    ruleTypeOptions,
    scanStatusLabel,
    scanStatusType,
    summaryItems,
    thresholdLabel,
    thresholdUnitLabel,
    thresholdUnitOptions
  }
}
