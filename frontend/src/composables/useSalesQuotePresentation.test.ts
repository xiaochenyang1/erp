import { describe, expect, it } from 'vitest'

import { useSalesQuotePresentation } from './useSalesQuotePresentation'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

describe('sales quote presentation', () => {
  it('maps status labels and tag types', () => {
    const presentation = useSalesQuotePresentation(t)
    expect(presentation.statusLabel('DRAFT')).toBe('salesQuote.statusValue.draft')
    expect(presentation.statusLabel('CONFIRMED')).toBe('salesQuote.statusValue.confirmed')
    expect(presentation.statusLabel('CONVERTED')).toBe('salesQuote.statusValue.converted')
    expect(presentation.statusLabel('CANCELLED')).toBe('salesQuote.statusValue.cancelled')
    expect(presentation.statusLabel('OTHER')).toBe('OTHER')
    expect(presentation.statusTagType('DRAFT')).toBe('info')
    expect(presentation.statusTagType('CONFIRMED')).toBe('success')
    expect(presentation.statusTagType('CONVERTED')).toBe('warning')
    expect(presentation.statusTagType('CANCELLED')).toBe('danger')
  })

  it('formats money/date and builds detail content', () => {
    const presentation = useSalesQuotePresentation(t)
    expect(presentation.formatMoney(12.5)).toBeTruthy()
    expect(presentation.formatDate(undefined)).toBe('-')
    const content = presentation.detailContent({
      quoteNo: 'Q1',
      customerName: 'Acme',
      totalAmount: 100,
      status: 'DRAFT',
      lines: [{}, {}]
    })
    expect(content).toContain('salesQuote.detailContent')
    expect(content).toContain('Q1')
    expect(content).toContain('"count":2')
  })
})
