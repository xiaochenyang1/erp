import { afterEach, describe, expect, it } from 'vitest'

import { i18n, setI18nLocale } from './index'

describe('workflow localization', () => {
  afterEach(() => setI18nLocale('zh-CN'))

  it('provides English task and escalation labels', () => {
    setI18nLocale('en-US')

    expect(i18n.global.t('workflow.tasks')).toBe('Approval tasks')
    expect(i18n.global.t('workflow.escalationTitle')).toBe('Escalate overdue task')
    expect(i18n.global.t('workflow.escalationSuccess')).toBe('Overdue task escalated')
  })
})

describe('sales order localization', () => {
  afterEach(() => setI18nLocale('zh-CN'))

  it('provides English order, credit, status, and validation labels', () => {
    setI18nLocale('en-US')

    expect(i18n.global.t('salesOrder.title')).toBe('Sales orders')
    expect(i18n.global.t('salesOrder.creditPreview')).toBe('Customer credit preview')
    expect(i18n.global.t('salesOrder.status.notDelivered')).toBe('Not delivered')
    expect(i18n.global.t('salesOrder.validation.lineRequired')).toBe('Add at least one valid order line')
  })
})

describe('sales delivery localization', () => {
  afterEach(() => setI18nLocale('zh-CN'))

  it('provides English delivery, scan, status, and validation labels', () => {
    setI18nLocale('en-US')

    expect(i18n.global.t('salesDelivery.title')).toBe('Sales deliveries')
    expect(i18n.global.t('salesDelivery.status.posted')).toBe('Posted')
    expect(i18n.global.t('salesDelivery.scan.notInOrder', { code: 'SKU-1' })).toBe('Product SKU-1 is not in this sales order')
    expect(i18n.global.t('salesDelivery.validation.quantityRequired')).toBe('Enter a delivery quantity')
  })
})

describe('purchase order localization', () => {
  afterEach(() => setI18nLocale('zh-CN'))

  it('provides English order, trace, action, and export labels', () => {
    setI18nLocale('en-US')

    expect(i18n.global.t('purchaseOrder.title')).toBe('Purchase order management')
    expect(i18n.global.t('purchaseOrder.traceSections.receipts')).toBe('Purchase receipts')
    expect(i18n.global.t('purchaseOrder.message.closeConfirm', { orderNo: 'PO-1' })).toBe('Close order “PO-1”? No further receipts can be created.')
    expect(i18n.global.t('purchaseOrder.message.exportFile', { timestamp: 123 })).toBe('purchase_orders_123.csv')
  })
})

describe('purchase receipt localization', () => {
  afterEach(() => setI18nLocale('zh-CN'))

  it('provides English receipt, scan, posting, and export labels', () => {
    setI18nLocale('en-US')

    expect(i18n.global.t('purchaseReceipt.title')).toBe('Purchase receipt management')
    expect(i18n.global.t('purchaseReceipt.status.posted')).toBe('Posted')
    expect(i18n.global.t('purchaseReceipt.scan.atMaximum', { code: 'SKU-1' })).toBe('Product SKU-1 has reached its receivable quantity')
    expect(i18n.global.t('purchaseReceipt.message.postConfirm', { receiptNo: 'PR-1' })).toBe('Post receipt “PR-1”? Inventory and a payable will be created.')
    expect(i18n.global.t('purchaseReceipt.message.exportFile', { timestamp: 123 })).toBe('purchase_receipts_123.csv')
  })
})

describe('purchase return localization', () => {
  afterEach(() => setI18nLocale('zh-CN'))

  it('provides English return, posting, linked receipt, and export labels', () => {
    setI18nLocale('en-US')

    expect(i18n.global.t('purchaseReturn.title')).toBe('Purchase return management')
    expect(i18n.global.t('purchaseReturn.linkedReceiptTitle')).toBe('Purchase receipt details')
    expect(i18n.global.t('purchaseReturn.message.postConfirm')).toBe('Post this purchase return? Inventory will be reduced and the payable will be reversed.')
    expect(i18n.global.t('purchaseReturn.message.cancelConfirm', { returnNo: 'RT-1' })).toBe('Cancel return “RT-1”?')
    expect(i18n.global.t('purchaseReturn.message.exportFile', { timestamp: 123 })).toBe('purchase_returns_123.csv')
  })
})

describe('production order localization', () => {
  afterEach(() => setI18nLocale('zh-CN'))

  it('provides English status, operation, validation, and confirmation labels', () => {
    setI18nLocale('en-US')

    expect(i18n.global.t('productionOrder.title')).toBe('Production order management')
    expect(i18n.global.t('productionOrder.status.materialIssued')).toBe('Materials issued')
    expect(i18n.global.t('productionOrder.operationsTitle', { orderNo: 'MO-1' })).toBe('Operation reporting · MO-1')
    expect(i18n.global.t('productionOrder.validation.qualifiedExceedsReported')).toBe('Qualified quantity cannot exceed reported quantity')
    expect(i18n.global.t('productionOrder.message.releaseConfirm', { orderNo: 'MO-1' })).toBe('Release production order “MO-1”?')
  })
})

describe('production BOM localization', () => {
  afterEach(() => setI18nLocale('zh-CN'))

  it('provides English list, status, validation, and feedback labels', () => {
    setI18nLocale('en-US')

    expect(i18n.global.t('productionBom.title')).toBe('BOM management')
    expect(i18n.global.t('productionBom.status.disabled')).toBe('Disabled')
    expect(i18n.global.t('productionBom.validation.materials')).toBe('Add at least one material line')
    expect(i18n.global.t('productionBom.productFallback', { id: 7 })).toBe('Product 7')
    expect(i18n.global.t('productionBom.message.created')).toBe('BOM created')
  })
})

describe('production work center localization', () => {
  afterEach(() => setI18nLocale('zh-CN'))

  it('provides English status, validation, confirmation, and feedback labels', () => {
    setI18nLocale('en-US')

    expect(i18n.global.t('productionWorkCenter.title')).toBe('Work centers')
    expect(i18n.global.t('productionWorkCenter.status.active')).toBe('Active')
    expect(i18n.global.t('productionWorkCenter.validation.code')).toBe('Enter a work center code')
    expect(i18n.global.t('productionWorkCenter.message.disableConfirm', { name: 'Assembly' })).toBe('Disable work center “Assembly”?')
    expect(i18n.global.t('productionWorkCenter.message.loadFailed')).toBe('Failed to load work centers')
  })
})

describe('production routing localization', () => {
  afterEach(() => setI18nLocale('zh-CN'))

  it('provides English operation, validation, confirmation, and failure labels', () => {
    setI18nLocale('en-US')

    expect(i18n.global.t('productionRouting.title')).toBe('Routings')
    expect(i18n.global.t('productionRouting.standardMinutes')).toBe('Standard time (min)')
    expect(i18n.global.t('productionRouting.validation.operationRequired', { line: 2 })).toBe('Operation 2: code, name, work center, and standard time are required')
    expect(i18n.global.t('productionRouting.message.enableConfirm', { name: 'Assembly' })).toBe('Enable routing “Assembly”?')
    expect(i18n.global.t('productionRouting.message.optionsLoadFailed')).toBe('Failed to load work center and BOM options')
  })
})
