import { describe, expect, it } from 'vitest'

import { useInventoryLotGenealogyPresentation } from './useInventoryLotGenealogyPresentation'

const t = (key: string, params?: Record<string, unknown>) => params ? `${key}:${JSON.stringify(params)}` : key

describe('useInventoryLotGenealogyPresentation', () => {
  const presentation = () => useInventoryLotGenealogyPresentation(t, {
    formatNumber: (value) => `#${value}`,
    formatDateTime: (value) => `@${value}`
  })

  it('localizes known document types and terminal reasons', () => {
    const { bizTypeLabel, terminalReasonLabel, terminalReasonType } = presentation()

    expect(bizTypeLabel({ bizType: 'PURCHASE_RECEIPT', bizLabel: '采购收货' }))
      .toBe('inventoryLotGenealogy.bizType.purchaseReceipt')
    expect(terminalReasonLabel('SOLD')).toBe('inventoryLotGenealogy.reason.sold')
    expect(terminalReasonType('MAX_DEPTH')).toBe('warning')
  })

  it('formats details and only builds active banners', () => {
    const { counterpartyLabel, formatQty, formatDateTime, truncationBanner, scopeBanner } = presentation()

    expect(counterpartyLabel({ type: 'CUSTOMER', id: '1', code: 'C-1', name: 'Customer', documentNo: 'SO-1' }))
      .toBe('C-1 Customer')
    expect(formatQty('10')).toBe('#10')
    expect(formatDateTime('2026-08-01T10:00:00')).toBe('@2026-08-01T10:00:00')
    expect(truncationBanner({ truncated: false, truncationReasons: [] })).toBeNull()
    expect(scopeBanner({ scopeLimited: true })).toBe('inventoryLotGenealogy.banner.scopeLimited')
  })
})
