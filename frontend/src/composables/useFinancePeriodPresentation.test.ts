import { describe, expect, it } from 'vitest'

import type { AccountPeriod } from '@/api/finance'
import { useFinancePeriodPresentation } from './useFinancePeriodPresentation'

const t = (key: string) => key

describe('finance period presentation', () => {
  it('maps status/issue/difference labels and builds summary counts', () => {
    const presentation = useFinancePeriodPresentation(t)

    expect(presentation.getStatusLabel('LOCKED')).toBe('financeReportPages.periods.status.locked')
    expect(presentation.getStatusType('CLOSED')).toBe('success')
    expect(presentation.getIssueSeverity('INVENTORY_FINANCE_RECONCILIATION')).toBe('danger')
    expect(presentation.getDifferenceTypeTag('AMOUNT_MISMATCH')).toBe('danger')
    expect(presentation.getSourceTypeLabel('SALES_DELIVERY')).toBe(
      'financeReportPages.periods.source.salesDelivery'
    )
    expect(presentation.formatAmount(12.3)).toMatch(/12\.30/)
    expect(presentation.formatQty(1.2)).toMatch(/1\.2000/)

    const summary = presentation.buildStatusSummary([
      { status: 'OPEN' },
      { status: 'LOCKED' },
      { status: 'CLOSED' },
      { status: 'OPEN' }
    ] as AccountPeriod[])
    expect(summary.find((item) => item.key === 'open')?.value).toBe(2)
    expect(summary.find((item) => item.key === 'total')?.value).toBe(4)
  })
})
