import { ref } from 'vue'
import { describe, expect, it } from 'vitest'

import type { Customer } from '@/api/masterdata'
import { useCustomerPresentation } from './useCustomerPresentation'

describe('customer presentation', () => {
  const texts = ref({
    creditPeriodValue: '{days} days',
    cashSettlement: 'Cash',
    noLimit: 'Unlimited'
  })
  const displayPreferences = ref({
    locale: 'en-US',
    timeZone: 'UTC'
  })

  it('formats credit fields and counts customer types', () => {
    const presentation = useCustomerPresentation(texts, displayPreferences)

    expect(presentation.interpolate('Hello {name}', { name: 'A' })).toBe('Hello A')
    expect(presentation.hasCreditPeriod(30)).toBe(true)
    expect(presentation.hasCreditPeriod(0)).toBe(false)
    expect(presentation.formatCreditPeriod(30)).toBe('30 days')
    expect(presentation.formatCreditPeriod(0)).toBe('Cash')
    expect(presentation.formatCreditLimit(null)).toBe('Unlimited')

    const rows = [
      { id: '1', type: 'COMPANY' },
      { id: '2', type: 'INDIVIDUAL' },
      { id: '3', type: 'COMPANY' }
    ] as Customer[]
    expect(presentation.companyCount(rows)).toBe(2)
    expect(presentation.individualCount(rows)).toBe(1)
    expect(presentation.customerLabel({ id: '9', customerName: 'Acme' } as Customer)).toBe('Acme')
  })
})
