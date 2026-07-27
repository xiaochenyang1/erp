import { describe, expect, it } from 'vitest'

import { useFinanceInvoicePresentation } from './useFinanceInvoicePresentation'

const t = (key: string) => key

describe('finance invoice presentation', () => {
  it('maps type and status labels/tags', () => {
    const presentation = useFinanceInvoicePresentation(t)
    expect(presentation.typeLabel('INPUT')).toBe('financeReportPages.invoices.typeValue.input')
    expect(presentation.typeLabel('OUTPUT')).toBe('financeReportPages.invoices.typeValue.output')
    expect(presentation.typeLabel('OTHER')).toBe('OTHER')
    expect(presentation.statusLabel('DRAFT')).toBe('financeReportPages.invoices.status.draft')
    expect(presentation.statusLabel('POSTED')).toBe('financeReportPages.invoices.status.posted')
    expect(presentation.statusLabel('CANCELLED')).toBe('financeReportPages.invoices.status.cancelled')
    expect(presentation.statusType('DRAFT')).toBe('info')
    expect(presentation.statusType('POSTED')).toBe('success')
    expect(presentation.statusType('CANCELLED')).toBe('danger')
  })

  it('formats amount and date', () => {
    const presentation = useFinanceInvoicePresentation(t)
    expect(presentation.formatAmount(12.5)).toBeTruthy()
    expect(presentation.formatDate(undefined)).toBe('-')
  })
})
