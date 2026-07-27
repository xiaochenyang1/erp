import { describe, expect, it } from 'vitest'

import { useSalesPricePresentation } from './useSalesPricePresentation'

const t = (key: string) => key

describe('sales price presentation', () => {
  it('maps scope and status labels', () => {
    const presentation = useSalesPricePresentation(t)
    expect(presentation.scopeLabel('c1')).toBe('salesPrice.customerSpecific')
    expect(presentation.scopeLabel(null)).toBe('salesPrice.productGeneral')
    expect(presentation.scopeTagType('c1')).toBe('warning')
    expect(presentation.scopeTagType(undefined)).toBe('info')
    expect(presentation.statusLabel('ACTIVE')).toBe('salesPrice.active')
    expect(presentation.statusTagType('INACTIVE')).toBe('info')
    expect(presentation.scopeDetail({ customerId: '9', customerName: 'Acme' })).toBe('Acme')
    expect(presentation.scopeDetail({})).toBe('salesPrice.allCustomers')
  })

  it('formats money and effective periods', () => {
    const presentation = useSalesPricePresentation(t)
    expect(presentation.formatMoney(12.5)).toBeTruthy()
    expect(presentation.formatEffectivePeriod('2026-01-01', undefined)).toContain('salesPrice.longTerm')
  })
})
