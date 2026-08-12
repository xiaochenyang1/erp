import { ref } from 'vue'
import { describe, expect, it } from 'vitest'

import type { Supplier } from '@/api/masterdata'
import type { DisplayPreferences } from '@/utils/locale'
import { useSupplierPresentation } from './useSupplierPresentation'

describe('supplier presentation', () => {
  const texts = ref({
    creditPeriodValue: '{days} days',
    cashSettlement: 'Cash settlement',
    bankTransfer: 'Bank transfer',
    cash: 'Cash',
    monthly: 'Monthly',
    cod: 'COD'
  })
  const displayPreferences = ref<DisplayPreferences>({
    locale: 'en-US',
    timeZone: 'UTC'
  })

  it('formats credit/settlement values and counts active suppliers', () => {
    const presentation = useSupplierPresentation(texts, displayPreferences)

    expect(presentation.hasCreditPeriod(15)).toBe(true)
    expect(presentation.formatCreditPeriod(15)).toBe('15 days')
    expect(presentation.formatCreditPeriod(0)).toBe('Cash settlement')
    expect(presentation.settlementMethodLabel('BANK_TRANSFER')).toBe('Bank transfer')
    expect(presentation.activeCount([
      { id: '1', status: 'ACTIVE' },
      { id: '2', status: 'INACTIVE' },
      { id: '3', status: 'ACTIVE' }
    ] as Supplier[])).toBe(2)
    expect(presentation.supplierLabel({ id: '9', supplierName: 'Vendor' } as Supplier)).toBe('Vendor')
  })
})
