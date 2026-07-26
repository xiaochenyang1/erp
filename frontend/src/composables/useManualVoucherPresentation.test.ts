import { describe, expect, it } from 'vitest'

import type { AccountSubject } from '@/api/finance'
import { useManualVoucherPresentation } from './useManualVoucherPresentation'

const t = (key: string) => key

describe('manual voucher presentation', () => {
  it('exposes every status filter option in state-machine order', () => {
    const { statusOptions } = useManualVoucherPresentation(t)

    expect(statusOptions.value.map((option) => option.value))
      .toEqual(['DRAFT', 'PENDING', 'APPROVED', 'POSTED', 'CANCELLED'])
    expect(statusOptions.value[0].label)
      .toBe('financeReportPages.manualVouchers.status.draft')
  })

  it('maps status labels and falls back to the raw value for unknown status', () => {
    const { statusLabel } = useManualVoucherPresentation(t)

    expect(statusLabel('DRAFT')).toBe('financeReportPages.manualVouchers.status.draft')
    expect(statusLabel('PENDING')).toBe('financeReportPages.manualVouchers.status.pending')
    expect(statusLabel('APPROVED')).toBe('financeReportPages.manualVouchers.status.approved')
    expect(statusLabel('POSTED')).toBe('financeReportPages.manualVouchers.status.posted')
    expect(statusLabel('CANCELLED')).toBe('financeReportPages.manualVouchers.status.cancelled')
    expect(statusLabel('WEIRD')).toBe('WEIRD')
    expect(statusLabel(undefined)).toBe('')
  })

  it('maps status tag types, unknown status stays neutral', () => {
    const { statusTagType } = useManualVoucherPresentation(t)

    expect(statusTagType('DRAFT')).toBe('info')
    expect(statusTagType('PENDING')).toBe('warning')
    expect(statusTagType('APPROVED')).toBe('primary')
    expect(statusTagType('POSTED')).toBe('success')
    expect(statusTagType('CANCELLED')).toBe('danger')
    expect(statusTagType('WEIRD')).toBe('info')
    expect(statusTagType(undefined)).toBe('info')
  })

  it('formats amounts to two decimals and treats non-numeric input as zero', () => {
    const { formatAmount } = useManualVoucherPresentation(t)

    expect(formatAmount(1234.5)).toBe('1,234.50')
    expect(formatAmount('980')).toBe('980.00')
    expect(formatAmount(0)).toBe('0.00')
    expect(formatAmount(undefined)).toBe('0.00')
    expect(formatAmount('abc')).toBe('0.00')
  })

  it('labels accounts by code and name across both field spellings', () => {
    const { subjectLabel } = useManualVoucherPresentation(t)

    expect(subjectLabel({ id: '1', subjectCode: '6601', subjectName: '销售费用' } as AccountSubject))
      .toBe('6601 销售费用')
    expect(subjectLabel({ id: '2', code: '1001', name: 'Cash' } as unknown as AccountSubject))
      .toBe('1001 Cash')
    expect(subjectLabel({ id: '3' } as AccountSubject)).toBe('3')
  })
})
