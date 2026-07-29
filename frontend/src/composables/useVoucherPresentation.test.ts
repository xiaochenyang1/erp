import { beforeEach, describe, expect, it } from 'vitest'

import type { Voucher } from '@/api/finance'
import { useVoucherPresentation } from './useVoucherPresentation'

const t = (key: string) => key

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
    expect(sourceTypeLabel('MANUAL')).toBe('MANUAL')
    expect(sourceTypeLabel()).toBe('-')

    expect(sourceTypeTag('EXPENSE')).toBe('success')
    expect(sourceTypeTag('EXPENSE_REVERSAL')).toBe('warning')
    expect(sourceTypeTag('MANUAL')).toBe('info')
    expect(sourceTypeTag()).toBe('info')
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
