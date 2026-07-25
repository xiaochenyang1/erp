import { afterEach, describe, expect, it } from 'vitest'
import { i18n, setI18nLocale } from './index'

describe('inventory planning localization', () => {
  afterEach(() => setI18nLocale('zh-CN'))

  it('provides English MRP labels and feedback', () => {
    setI18nLocale('en-US')

    expect(i18n.global.t('inventoryMrp.title')).toBe('MRP Lite')
    expect(i18n.global.t('inventoryMrp.summary', {
      date: '2026-07-23',
      purchaseCount: 2,
      productionCount: 1
    })).toBe('As of 2026-07-23 · 2 purchase suggestions · 1 production suggestions')
    expect(i18n.global.t('inventoryMrp.message.failed')).toBe('MRP run failed')
  })

  it('provides English replenishment states, validation, and confirmations', () => {
    setI18nLocale('en-US')

    expect(i18n.global.t('inventoryReplenishment.fulfillment.partialReceived')).toBe('Partially received')
    expect(i18n.global.t('inventoryReplenishment.validation.quantityPositive')).toBe('Suggested quantity must be greater than 0')
    expect(i18n.global.t('inventoryReplenishment.message.convertConfirm', { no: 'REP-1' }))
      .toBe('Convert replenishment suggestion REP-1 to a purchase order?')
    expect(i18n.global.t('inventoryReplenishment.message.converted', { no: 'PO-1' }))
      .toBe('Purchase order PO-1 created')
  })
})
