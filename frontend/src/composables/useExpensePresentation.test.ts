import { describe, expect, it } from 'vitest'
import { ref } from 'vue'

import type { AccountSubject, ExpenseReconciliation } from '@/api/finance'
import { flattenSubjects, useExpensePresentation } from './useExpensePresentation'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.id != null ? `${key}:${params.id}` : key

const createSubjects = (): AccountSubject[] => [
  {
    id: '1',
    code: '6601',
    name: '销售费用',
    category: 'EXPENSE',
    status: 'ACTIVE'
  } as unknown as AccountSubject,
  {
    id: '2',
    subjectCode: '1001',
    subjectName: '库存现金',
    category: 'ASSET',
    status: 'ACTIVE'
  } as unknown as AccountSubject,
  {
    id: '3',
    code: '2202',
    name: '应付账款',
    category: 'LIABILITY',
    status: 'ACTIVE'
  } as unknown as AccountSubject,
  {
    id: '4',
    code: '6602',
    name: '停用费用',
    category: 'EXPENSE',
    status: 'INACTIVE'
  } as unknown as AccountSubject
]

const createPresentation = (
  subjects: AccountSubject[] = createSubjects(),
  reconciliation?: ExpenseReconciliation
) =>
  useExpensePresentation(t, {
    subjects: ref(subjects),
    reconciliation: ref(reconciliation)
  })

const passingReconciliation = (
  overrides: Partial<ExpenseReconciliation> = {}
): ExpenseReconciliation => ({
  voucherMissing: false,
  entriesMissing: false,
  voucherBalanced: true,
  amountMatched: true,
  voucherLinkedToExpense: true,
  reversed: false,
  ...overrides
} as ExpenseReconciliation)

describe('expense presentation', () => {
  it('flattens nested account subject trees depth-first', () => {
    const tree = [
      {
        id: '1',
        code: '6601',
        children: [
          { id: '11', code: '660101', children: [{ id: '111', code: '66010101' }] },
          { id: '12', code: '660102' }
        ]
      },
      { id: '2', code: '1001' }
    ] as unknown as AccountSubject[]

    expect(flattenSubjects(tree).map((item) => item.id))
      .toEqual(['1', '11', '111', '12', '2'])
    expect(flattenSubjects([])).toEqual([])
  })

  it('splits active expense accounts from cash and payable accounts', () => {
    const presentation = createPresentation()

    expect(presentation.expenseSubjects.value.map((item) => item.id)).toEqual(['1'])
    expect(presentation.paymentSubjects.value.map((item) => item.id)).toEqual(['2', '3'])
  })

  it('labels subjects with code and name, tolerating either field spelling', () => {
    const subjects = createSubjects()
    const presentation = createPresentation(subjects)

    expect(presentation.subjectLabel(subjects[0])).toBe('6601 - 销售费用')
    expect(presentation.subjectLabel(subjects[1])).toBe('1001 - 库存现金')

    expect(presentation.subjectName('1')).toBe('6601 - 销售费用')
    expect(presentation.subjectName('99')).toBe('financeReportPages.expenses.subjectFallback:99')
    expect(presentation.subjectName(undefined)).toBe('-')
    expect(presentation.subjectName(null)).toBe('-')
  })

  it('exposes the bare account name for printed documents', () => {
    const presentation = createPresentation()

    expect(presentation.rawSubjectName('1')).toBe('销售费用')
    expect(presentation.rawSubjectName('2')).toBe('库存现金')
    expect(presentation.rawSubjectName('99')).toBe('')
    expect(presentation.rawSubjectName(undefined)).toBe('')
  })

  it('maps every status to a label and tag type, unknown falls back to raw value', () => {
    const presentation = createPresentation()

    expect(presentation.getStatusLabel('DRAFT')).toBe('financeReportPages.expenses.status.draft')
    expect(presentation.getStatusLabel('PENDING')).toBe('financeReportPages.expenses.status.pending')
    expect(presentation.getStatusLabel('APPROVED')).toBe('financeReportPages.expenses.status.approved')
    expect(presentation.getStatusLabel('REJECTED')).toBe('financeReportPages.expenses.status.rejected')
    expect(presentation.getStatusLabel('POSTED')).toBe('financeReportPages.expenses.status.posted')
    expect(presentation.getStatusLabel('CANCELLED')).toBe('financeReportPages.expenses.status.cancelled')
    expect(presentation.getStatusLabel('WEIRD')).toBe('WEIRD')
    expect(presentation.getStatusLabel(undefined)).toBe('')

    expect(presentation.getStatusType('DRAFT')).toBe('info')
    expect(presentation.getStatusType('PENDING')).toBe('warning')
    expect(presentation.getStatusType('APPROVED')).toBe('success')
    expect(presentation.getStatusType('REJECTED')).toBe('danger')
    expect(presentation.getStatusType('POSTED')).toBe('primary')
    expect(presentation.getStatusType('WEIRD')).toBe('info')
  })

  it('formats amounts with two fraction digits and treats blanks as zero', () => {
    const presentation = createPresentation()

    expect(presentation.formatAmount(1234.5)).toBe('1,234.50')
    expect(presentation.formatAmount(0)).toBe('0.00')
    expect(presentation.formatAmount(undefined)).toBe('0.00')
  })

  it('labels reconciliation checks as normal or abnormal', () => {
    const presentation = createPresentation()

    expect(presentation.checkLabel(true)).toBe('financeReportPages.common.normal')
    expect(presentation.checkLabel(false)).toBe('financeReportPages.common.abnormal')
    expect(presentation.checkTagType(true)).toBe('success')
    expect(presentation.checkTagType(false)).toBe('danger')
  })

  it('passes reconciliation only when every voucher check holds', () => {
    expect(createPresentation(createSubjects(), passingReconciliation())
      .reconciliationPassed.value).toBe(true)

    expect(createPresentation().reconciliationPassed.value).toBe(false)

    for (const broken of [
      { voucherMissing: true },
      { entriesMissing: true },
      { voucherBalanced: false },
      { amountMatched: false },
      { voucherLinkedToExpense: false }
    ]) {
      expect(
        createPresentation(createSubjects(), passingReconciliation(broken))
          .reconciliationPassed.value
      ).toBe(false)
    }
  })

  it('requires a balanced, amount-matched reversal once the expense is reversed', () => {
    const reversedOk = passingReconciliation({
      reversed: true,
      reversalVoucherBalanced: true,
      reversalAmountMatched: true
    })
    expect(createPresentation(createSubjects(), reversedOk).reconciliationPassed.value).toBe(true)

    const reversalUnbalanced = passingReconciliation({
      reversed: true,
      reversalVoucherBalanced: false,
      reversalAmountMatched: true
    })
    expect(
      createPresentation(createSubjects(), reversalUnbalanced).reconciliationPassed.value
    ).toBe(false)

    const reversalAmountOff = passingReconciliation({
      reversed: true,
      reversalVoucherBalanced: true,
      reversalAmountMatched: false
    })
    expect(
      createPresentation(createSubjects(), reversalAmountOff).reconciliationPassed.value
    ).toBe(false)
  })
})
