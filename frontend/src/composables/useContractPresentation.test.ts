import { ref } from 'vue'
import { describe, expect, it } from 'vitest'

import type { ContractAlertRecord } from '@/api/contracts'
import type { Customer, Product, Supplier } from '@/api/masterdata'
import { useContractPresentation } from './useContractPresentation'

const t = (key: string) => key

const products = () => ref<Product[]>([
  { id: 'p1', productCode: 'P001', productName: '钢板' } as Product,
  { id: 'p2', code: 'P002', name: '螺栓' } as unknown as Product
])

const createPresentation = (alerts: ContractAlertRecord[] = []) =>
  useContractPresentation(t, { alerts: ref(alerts), products: products() })

describe('contract presentation', () => {
  it('formats dates, amounts and the effective period', () => {
    const presentation = createPresentation()

    expect(presentation.formatDate('2026-09-04')).toMatch(/2026/)
    expect(presentation.formatDate()).toBe('-')
    expect(presentation.formatDateTime('2026-09-04 08:30:00')).toMatch(/2026/)
    expect(presentation.formatDateTime()).toBe('-')
    expect(presentation.formatMoney(12.3)).toMatch(/12\.30/)
    expect(presentation.formatMoney()).toMatch(/0\.00/)

    expect(presentation.effectivePeriodText('2026-01-01', '2026-12-31')).toMatch(/ ~ /)
    expect(presentation.effectivePeriodText('2026-01-01')).toMatch(/ ~ -$/)
  })

  it('translates statuses, events, contract types and changed fields', () => {
    const presentation = createPresentation()

    expect(presentation.statusText('ACTIVE')).toBe('contractPage.statusValue.active')
    expect(presentation.statusText()).toBe('-')
    expect(presentation.statusType('ACTIVE')).toBe('success')
    expect(presentation.statusType('CANCELLED')).toBe('info')
    expect(presentation.statusType('REJECTED')).toBe('danger')
    expect(presentation.statusType('SUBMITTED')).toBe('warning')
    expect(presentation.statusType('DRAFT')).toBe('primary')
    expect(presentation.statusType()).toBe('primary')

    expect(presentation.contractTypeText('SALES')).toBe('contractPage.sales')
    expect(presentation.contractTypeText('PURCHASE')).toBe('contractPage.purchase')
    expect(presentation.contractTypeText()).toBe('contractPage.purchase')

    expect(presentation.versionEventText('CREATED')).toBe('contractPage.versionEventValue.created')
    expect(presentation.versionEventText()).toBe('-')

    expect(presentation.changedFieldsText(['STATUS', 'lines']))
      .toBe('contractPage.changedFieldValue.statuscontractPage.listSeparatorcontractPage.changedFieldValue.lines')
    expect(presentation.changedFieldsText([])).toBe('-')
    expect(presentation.changedFieldsText()).toBe('-')
  })

  it('names the partner side that the contract actually carries', () => {
    const presentation = createPresentation()

    expect(presentation.partnerName({ customerName: '甲方' })).toBe('甲方')
    expect(presentation.partnerName({ supplierName: '乙方' })).toBe('乙方')
    expect(presentation.partnerName({})).toBe('-')
  })

  it('labels master-data options and lines from either field spelling', () => {
    const presentation = createPresentation()

    expect(presentation.customerLabel(
      { customerCode: 'C001', customerName: '客户甲' } as Customer
    )).toBe('C001 客户甲')
    expect(presentation.supplierLabel(
      { supplierCode: 'S001', supplierName: '供应商乙' } as Supplier
    )).toBe('S001 供应商乙')
    expect(presentation.productLabel({ productCode: 'P001', productName: '钢板' } as Product))
      .toBe('P001 钢板')
    expect(presentation.productLabel({ code: 'P002', name: '螺栓' } as unknown as Product))
      .toBe('P002 螺栓')

    expect(presentation.productLabelById('p1')).toBe('P001 钢板')
    expect(presentation.productLabelById('p2')).toBe('P002 螺栓')
    expect(presentation.productLabelById('missing')).toBe('missing')

    expect(presentation.lineProductLabel({ productId: 'p1', productCode: 'P001', productName: '钢板' }))
      .toBe('P001 钢板')
    expect(presentation.lineProductLabel({ productId: 'p9' })).toBe('p9')
  })

  it('derives line amount, available quantities and clamped progress', () => {
    const presentation = createPresentation()

    expect(presentation.lineAmount({ quantity: 3, unitPrice: 10 })).toMatch(/30\.00/)
    expect(presentation.lineAmount({})).toMatch(/0\.00/)

    expect(presentation.availableOrderQuantity({ quantity: 10, committedQuantity: 4 })).toBe(6)
    expect(presentation.availableOrderQuantity({ quantity: 10, committedQuantity: 14 })).toBe(0)
    expect(presentation.availableFulfillmentQuantity({ quantity: 10, fulfilledQuantity: 2 })).toBe(8)
    expect(presentation.availableFulfillmentQuantity({ quantity: 1, fulfilledQuantity: 5 })).toBe(0)

    expect(presentation.fulfillmentPercentage({ quantity: 4, fulfilledQuantity: 1 })).toBe(25)
    expect(presentation.fulfillmentPercentage({ quantity: 4, fulfilledQuantity: 9 })).toBe(100)
    expect(presentation.fulfillmentPercentage({ quantity: 0, fulfilledQuantity: 3 })).toBe(0)
  })

  it('indexes alerts by contract and names each alert type', () => {
    const presentation = createPresentation([
      { contractId: 1, alertTypes: ['CONTRACT_EXPIRING'] } as unknown as ContractAlertRecord,
      { contractId: '2' } as ContractAlertRecord
    ])

    expect(presentation.alertsFor('1')).toEqual(['CONTRACT_EXPIRING'])
    expect(presentation.alertsFor('2')).toEqual([])
    expect(presentation.alertsFor('3')).toEqual([])

    expect(presentation.alertText('CONTRACT_EXPIRING')).toBe('contractPage.expiringAlert')
    expect(presentation.alertText('CONTRACT_LOW_EXECUTION')).toBe('contractPage.lowExecutionAlert')
  })
})
