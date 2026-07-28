import { beforeEach, describe, expect, it } from 'vitest'

import { useFinanceAccountPresentation } from './useFinanceAccountPresentation'

const t = (key: string) => key

describe('finance account presentation', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('locale', 'en-US')
    localStorage.setItem('timeZone', 'UTC')
  })

  it('formats amounts and dates with shared locale helpers', () => {
    const presentation = useFinanceAccountPresentation(t)

    expect(presentation.formatCurrency(1234.5)).toContain('1,234.50')
    expect(presentation.formatCurrency()).toContain('0.00')
    expect(presentation.formatDate('2026-07-28')).toBe('07/28/2026')
    expect(presentation.formatDate()).toBe('-')
    expect(presentation.formatDateTime('2026-07-28T16:00:00')).toContain('08:00:00')
    expect(presentation.formatDateTime()).toBe('-')
  })

  it('maps every account status to its localized label and tag type', () => {
    const presentation = useFinanceAccountPresentation(t)

    expect(presentation.accountStatusLabel('UNSETTLED')).toBe('financeAccount.status.unsettled')
    expect(presentation.accountStatusLabel('PARTIALLY_SETTLED')).toBe('financeAccount.status.partiallySettled')
    expect(presentation.accountStatusLabel('SETTLED')).toBe('financeAccount.status.settled')
    expect(presentation.accountStatusLabel('OFFSET')).toBe('financeAccount.status.offset')
    expect(presentation.accountStatusType('UNSETTLED')).toBe('danger')
    expect(presentation.accountStatusType('PARTIALLY_SETTLED')).toBe('warning')
    expect(presentation.accountStatusType('SETTLED')).toBe('success')
    expect(presentation.accountStatusType('OFFSET')).toBe('info')
  })
})
