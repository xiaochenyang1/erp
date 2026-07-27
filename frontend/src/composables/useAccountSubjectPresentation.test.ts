import { describe, expect, it } from 'vitest'

import {
  defaultBalanceDirection,
  useAccountSubjectPresentation
} from './useAccountSubjectPresentation'

const t = (key: string) => key

describe('account subject presentation', () => {
  it('defaults balance direction by category', () => {
    expect(defaultBalanceDirection('ASSET')).toBe('DEBIT')
    expect(defaultBalanceDirection('EXPENSE')).toBe('DEBIT')
    expect(defaultBalanceDirection('LIABILITY')).toBe('CREDIT')
    expect(defaultBalanceDirection('EQUITY')).toBe('CREDIT')
    expect(defaultBalanceDirection('REVENUE')).toBe('CREDIT')
  })

  it('maps category/status/leaf labels', () => {
    const presentation = useAccountSubjectPresentation(t)
    expect(presentation.getCategoryLabel('ASSET')).toBe(
      'financeReportPages.subjects.categoryValue.asset'
    )
    expect(presentation.getCategoryType('EXPENSE')).toBe('danger')
    expect(presentation.statusLabel('ACTIVE')).toBe(
      'financeReportPages.subjects.status.active'
    )
    expect(presentation.statusType('DISABLED')).toBe('danger')
    expect(presentation.leafLabel(true)).toBe('financeReportPages.subjects.yes')
    expect(presentation.leafType(false)).toBe('info')
    expect(presentation.subjectDisplayName({ name: 'Cash' })).toBe('Cash')
    expect(presentation.subjectDisplayName({ id: '9' })).toBe('9')
  })
})
