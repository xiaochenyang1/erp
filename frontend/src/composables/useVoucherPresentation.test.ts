import { beforeEach, describe, expect, it } from 'vitest'

import type { Voucher } from '@/api/finance'
import { financeReportPageMessages } from '@/i18n/finance-report-pages'
import { useVoucherPresentation } from './useVoucherPresentation'

const t = (key: string) => key

const readNestedValue = (source: unknown, path: string): unknown => (
  path.split('.').reduce<unknown>((current, segment) => {
    if (!current || typeof current !== 'object') return undefined
    return (current as Record<string, unknown>)[segment]
  }, source)
)

describe('voucher presentation', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('locale', 'en-US')
    localStorage.setItem('timeZone', 'UTC')
  })

  it('maps source labels and tag types with neutral fallbacks', () => {
    const { sourceTypeLabel, sourceTypeTag } = useVoucherPresentation(t)

    expect(sourceTypeLabel('EXPENSE'))
      .toBe('financeReportPages.vouchers.sourceValue.expense')
    expect(sourceTypeLabel('EXPENSE_REVERSAL'))
      .toBe('financeReportPages.vouchers.sourceValue.expenseReversal')
    expect(sourceTypeLabel('RECEIPT'))
      .toBe('financeReportPages.vouchers.sourceValue.receipt')
    expect(sourceTypeLabel('PAYMENT_REVERSAL'))
      .toBe('financeReportPages.vouchers.sourceValue.paymentReversal')
    expect(sourceTypeLabel('UNKNOWN_SOURCE')).toBe('UNKNOWN_SOURCE')
    expect(sourceTypeLabel()).toBe('-')

    expect(sourceTypeTag('EXPENSE')).toBe('success')
    expect(sourceTypeTag('EXPENSE_REVERSAL')).toBe('warning')
    expect(sourceTypeTag('RECEIPT')).toBe('success')
    expect(sourceTypeTag('PAYMENT_REVERSAL')).toBe('warning')
    expect(sourceTypeTag('SALES_DELIVERY')).toBe('info')
    expect(sourceTypeTag('UNKNOWN_SOURCE')).toBe('info')
    expect(sourceTypeTag()).toBe('info')
  })

  it('offers every posted source type as a filter option, settlement sources first', () => {
    const { sourceTypeLabel, sourceTypeOptions } = useVoucherPresentation(t)
    const options = sourceTypeOptions()

    expect(options.map((option) => option.value).slice(0, 6)).toEqual([
      'RECEIPT',
      'RECEIPT_REVERSAL',
      'PAYMENT',
      'PAYMENT_REVERSAL',
      'EXPENSE',
      'EXPENSE_REVERSAL'
    ])
    expect(options.map((option) => option.value)).toContain('MANUAL')
    expect(options.map((option) => option.value)).toContain('SALES_DELIVERY')
    expect(new Set(options.map((option) => option.value)).size).toBe(options.length)
    for (const option of options) {
      expect(option.label, option.value).toBe(sourceTypeLabel(option.value))
    }
  })

  it('resolves every filter option label in both locales', () => {
    const { sourceTypeOptions } = useVoucherPresentation(t)

    for (const locale of ['zh-CN', 'en-US'] as const) {
      for (const option of sourceTypeOptions()) {
        expect(typeof readNestedValue(financeReportPageMessages[locale], option.label), `${locale}:${option.value}`)
          .toBe('string')
      }
    }
  })

  it('maps every voucher status and preserves unknown values', () => {
    const { statusLabel, statusType } = useVoucherPresentation(t)

    expect(statusLabel('DRAFT')).toBe('financeReportPages.vouchers.status.draft')
    expect(statusLabel('APPROVED')).toBe('financeReportPages.vouchers.status.approved')
    expect(statusLabel('POSTED')).toBe('financeReportPages.vouchers.status.posted')
    expect(statusLabel('CANCELLED')).toBe('financeReportPages.vouchers.status.cancelled')
    expect(statusLabel('UNKNOWN')).toBe('UNKNOWN')

    expect(statusType('DRAFT')).toBe('info')
    expect(statusType('APPROVED')).toBe('warning')
    expect(statusType('POSTED')).toBe('success')
    expect(statusType('CANCELLED')).toBe('danger')
    expect(statusType('UNKNOWN')).toBe('info')
  })

  it('formats money and dates through localized helpers', () => {
    const { formatDate, formatMoney, toVoucherRow } = useVoucherPresentation(t)
    const row = { id: 'v1' } as Voucher

    expect(formatMoney(1234.5)).toContain('1,234.50')
    expect(formatMoney(1234.5)).toMatch(/¥/)
    expect(formatMoney()).toContain('0.00')
    expect(formatDate('2026-07-28')).toBe('07/28/2026')
    expect(formatDate()).toBe('')
    expect(toVoucherRow(row)).toBe(row)
  })
})
