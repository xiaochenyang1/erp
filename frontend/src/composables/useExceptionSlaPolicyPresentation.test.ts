import { describe, expect, it } from 'vitest'
import { ref } from 'vue'

import type { ExceptionSlaPolicy } from '@/api/exceptionSlaPolicy'
import { useExceptionSlaPolicyPresentation } from './useExceptionSlaPolicyPresentation'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.count != null ? `${key}:${params.count}` : key

const icons = {
  circleCheck: { name: 'CircleCheck' },
  circleClose: { name: 'CircleClose' },
  trendCharts: { name: 'TrendCharts' },
  alarmClock: { name: 'AlarmClock' },
  warning: { name: 'Warning' }
}

const policy = (overrides: Partial<ExceptionSlaPolicy> = {}) =>
  ({
    id: '1',
    category: 'GENERAL',
    priority: 'HIGH',
    dueHours: 24,
    escalationEnabled: true,
    escalateToPriority: 'URGENT',
    enabled: true,
    ...overrides
  }) as ExceptionSlaPolicy

describe('exception SLA policy presentation', () => {
  it('builds options and maps labels/tags', () => {
    const presentation = useExceptionSlaPolicyPresentation(t, ref([]), icons)

    expect(presentation.categoryOptions.value).toHaveLength(6)
    expect(presentation.priorityOptions.value).toHaveLength(4)
    expect(presentation.categoryLabel('LOW_STOCK')).toBe('exceptionSlaPolicy.categories.lowStock')
    expect(presentation.priorityLabel('URGENT')).toBe('exceptionSlaPolicy.priorities.urgent')
    expect(presentation.priorityType('URGENT')).toBe('danger')
    expect(presentation.priorityType(undefined)).toBe('info')
  })

  it('summarizes the current policy page including average due hours', () => {
    const rows = ref([
      policy({ dueHours: 10, enabled: true, escalationEnabled: true, priority: 'URGENT' }),
      policy({ id: '2', dueHours: 20, enabled: false, escalationEnabled: false, priority: 'LOW' }),
      policy({ id: '3', dueHours: 30, enabled: true, escalationEnabled: true, priority: 'HIGH' })
    ])
    const presentation = useExceptionSlaPolicyPresentation(t, rows, icons)
    const summary = presentation.summaryItems.value

    expect(summary.find((item) => item.tone === 'green')?.value).toBe(2)
    expect(summary.find((item) => item.tone === 'gray')?.value).toBe(1)
    expect(summary.find((item) => item.tone === 'blue')?.value).toBe(2)
    expect(summary.find((item) => item.tone === 'orange')?.value).toBe('exceptionSlaPolicy.hours:20')
    expect(summary.find((item) => item.tone === 'red')?.value).toBe(1)
  })
})
