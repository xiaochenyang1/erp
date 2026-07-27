import { describe, expect, it } from 'vitest'

import { useDashboardPresentation } from './useDashboardPresentation'

const t = (key: string) => key

describe('dashboard presentation', () => {
  it('maps todo icons, colors, priorities and quick actions', () => {
    const presentation = useDashboardPresentation(t, () => ({
      locale: 'en-US',
      timeZone: 'UTC'
    }))

    expect(presentation.getTodoIcon('WORKFLOW')).toBe('DocumentChecked')
    expect(presentation.getTodoColor('LOW_STOCK')).toBe('#e6a23c')
    expect(presentation.getTodoTagType('HIGH')).toBe('danger')
    expect(presentation.formatPriority('MEDIUM')).toBe('dashboard.priority.medium')
    expect(presentation.quickActions()).toHaveLength(6)
    expect(presentation.resolveTodoRoute({
      id: '1',
      type: 'WORKFLOW',
      title: 't',
      priority: 'HIGH',
      route: '/workflow/tasks'
    })).toBe('/workflow/tasks')
  })

  it('formats numbers and current date with preferences', () => {
    const presentation = useDashboardPresentation(t, () => ({
      locale: 'en-US',
      timeZone: 'UTC'
    }))
    expect(presentation.formatNumber(12)).toBeTruthy()
    expect(presentation.formatCurrency(12)).toBeTruthy()
    expect(presentation.formatCurrentDate(new Date('2026-07-01T00:00:00Z'))).toMatch(/2026/)
  })
})
