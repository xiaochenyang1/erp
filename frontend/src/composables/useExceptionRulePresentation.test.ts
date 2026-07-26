import { describe, expect, it } from 'vitest'
import { ref } from 'vue'

import type { ExceptionRule } from '@/api/exceptionRule'
import { useExceptionRulePresentation } from './useExceptionRulePresentation'

const t = (key: string) => key
const icons = {
  circleCheck: { name: 'CircleCheck' },
  circleClose: { name: 'CircleClose' },
  warning: { name: 'Warning' },
  tickets: { name: 'Tickets' },
  finished: { name: 'Finished' }
}

const rule = (overrides: Partial<ExceptionRule> = {}) =>
  ({
    id: '1',
    ruleCode: 'R1',
    ruleName: '低库存',
    ruleType: 'LOW_STOCK',
    category: 'STOCK',
    priority: 'HIGH',
    thresholdValue: 10,
    thresholdUnit: 'QTY',
    enabled: true,
    lastHitCount: 2,
    lastTicketCreatedCount: 1,
    lastScanStatus: 'SUCCESS',
    ...overrides
  }) as ExceptionRule

describe('exception rule presentation', () => {
  it('builds filter options and maps labels/tags', () => {
    const presentation = useExceptionRulePresentation(t, ref([]), icons)

    expect(presentation.ruleTypeOptions.value.map((item) => item.value)).toEqual([
      'LOW_STOCK',
      'RECEIVABLE_OVERDUE',
      'PAYABLE_OVERDUE',
      'OPERATION_FAILURE'
    ])
    expect(presentation.priorityOptions.value).toHaveLength(4)
    expect(presentation.thresholdUnitOptions.value).toHaveLength(4)
    expect(presentation.ruleTypeLabel('LOW_STOCK')).toBe('exceptionRule.ruleTypes.lowStock')
    expect(presentation.priorityLabel('URGENT')).toBe('exceptionRule.priorities.urgent')
    expect(presentation.thresholdUnitLabel('DAYS')).toBe('exceptionRule.units.days')
    expect(presentation.thresholdLabel(rule())).toBe('10 exceptionRule.units.quantity')
    expect(presentation.priorityType('URGENT')).toBe('danger')
    expect(presentation.scanStatusLabel('FAILED')).toBe('exceptionRule.scanStatuses.failed')
    expect(presentation.scanStatusType('SUCCESS')).toBe('success')
    expect(presentation.scanStatusLabel(undefined)).toBe('-')
  })

  it('summarizes the current rule page', () => {
    const rows = ref([
      rule(),
      rule({
        id: '2',
        enabled: false,
        lastHitCount: 3,
        lastTicketCreatedCount: 0,
        lastScanStatus: 'FAILED'
      }),
      rule({
        id: '3',
        enabled: true,
        lastHitCount: 1,
        lastTicketCreatedCount: 4,
        lastScanStatus: 'SKIPPED'
      })
    ])
    const presentation = useExceptionRulePresentation(t, rows, icons)
    const summary = presentation.summaryItems.value

    expect(summary.find((item) => item.tone === 'green')?.value).toBe(2)
    expect(summary.find((item) => item.tone === 'gray')?.value).toBe(1)
    expect(summary.find((item) => item.tone === 'orange')?.value).toBe(6)
    expect(summary.find((item) => item.tone === 'blue')?.value).toBe(5)
    expect(summary.find((item) => item.tone === 'red')?.value).toBe(1)
  })
})
