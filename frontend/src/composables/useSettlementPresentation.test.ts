import { describe, expect, it } from 'vitest'
import { ref } from 'vue'

import type { Customer, Supplier } from '@/api/masterdata'
import { useSettlementPresentation } from './useSettlementPresentation'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.id != null ? `${key}:${params.id}` : key

const createResources = () => ({
  customers: ref<Customer[]>([
    { id: '1', name: '甲客户' } as Customer,
    { id: 2, name: 'Beta Ltd' } as unknown as Customer
  ]),
  suppliers: ref<Supplier[]>([
    { id: '10', name: '乙供应商' } as Supplier
  ])
})

describe('settlement presentation', () => {
  it('resolves party names by id with fallbacks', () => {
    const resources = createResources()
    const presentation = useSettlementPresentation(t, resources)

    expect(presentation.customerName('1')).toBe('甲客户')
    expect(presentation.customerName(2)).toBe('Beta Ltd')
    expect(presentation.customerName('999'))
      .toBe('financeReportPages.payments.customerFallback:999')
    expect(presentation.supplierName(10)).toBe('乙供应商')
    expect(presentation.supplierName('404'))
      .toBe('financeReportPages.payments.supplierFallback:404')
  })

  it('tracks newly loaded parties without rebuilding the composable', () => {
    const resources = createResources()
    const presentation = useSettlementPresentation(t, resources)

    expect(presentation.customerName('7'))
      .toBe('financeReportPages.payments.customerFallback:7')

    resources.customers.value = [
      ...resources.customers.value,
      { id: '7', name: '新客户' } as Customer
    ]
    expect(presentation.customerName('7')).toBe('新客户')
  })

  it('formats money with two fractional digits and treats blanks as zero', () => {
    const presentation = useSettlementPresentation(t, createResources())

    expect(presentation.formatMoney(1234.5)).toContain('1,234.50')
    expect(presentation.formatMoney(undefined)).toContain('0.00')
    expect(presentation.formatCurrency(0)).toMatch(/0[.,]00/)
  })

  it('maps DRAFT/POSTED/COMPLETED/CANCELLED statuses to labels and tag types', () => {
    const presentation = useSettlementPresentation(t, createResources())

    expect(presentation.paymentStatusLabel('DRAFT'))
      .toBe('financeReportPages.payments.status.draft')
    expect(presentation.paymentStatusLabel('POSTED'))
      .toBe('financeReportPages.payments.status.posted')
    expect(presentation.paymentStatusLabel('COMPLETED'))
      .toBe('financeReportPages.payments.status.posted')
    expect(presentation.paymentStatusLabel('CANCELLED'))
      .toBe('financeReportPages.payments.status.cancelled')
    expect(presentation.paymentStatusLabel('WEIRD')).toBe('WEIRD')
    expect(presentation.paymentStatusLabel(undefined)).toBe('-')

    expect(presentation.paymentStatusTagType('DRAFT')).toBe('info')
    expect(presentation.paymentStatusTagType('POSTED')).toBe('success')
    expect(presentation.paymentStatusTagType('COMPLETED')).toBe('success')
    expect(presentation.paymentStatusTagType('CANCELLED')).toBe('danger')
  })
})
