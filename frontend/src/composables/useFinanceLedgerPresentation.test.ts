import { describe, expect, it } from 'vitest'

import type { AccountSubject } from '@/api/finance'
import {
  findSubjectByCode,
  findSubjectById,
  paginateEntries,
  useFinanceLedgerPresentation
} from './useFinanceLedgerPresentation'

const t = (key: string) => key

const tree: AccountSubject[] = [
  {
    id: '1',
    code: '1001',
    name: 'Cash',
    status: 'ACTIVE',
    children: [
      { id: '2', code: '100101', name: 'Petty', status: 'ACTIVE' }
    ]
  }
]

describe('finance ledger presentation helpers', () => {
  it('finds subjects and paginates entries', () => {
    expect(findSubjectByCode(tree, '100101')?.id).toBe('2')
    expect(findSubjectById(tree, '2')?.code).toBe('100101')
    expect(findSubjectByCode(tree, 'missing')).toBeNull()
    expect(paginateEntries([1, 2, 3, 4, 5], 2, 2)).toEqual([3, 4])
  })

  it('formats amounts/dates and builds general summary', () => {
    const presentation = useFinanceLedgerPresentation(t)
    expect(presentation.formatAmount(12.5)).toBeTruthy()
    expect(presentation.formatDate(undefined)).toBe('-')
    const sums = presentation.getGeneralSummary({
      columns: [
        { property: 'subjectCode' },
        { property: 'subjectName' },
        { property: 'debitAmount' },
        { property: 'creditAmount' }
      ],
      data: [
        { debitAmount: 10, creditAmount: 1 },
        { debitAmount: 5, creditAmount: 2 }
      ]
    })
    expect(sums[0]).toBe('financeReportPages.ledger.total')
    expect(sums[1]).toBe('')
    expect(sums[2]).toBeTruthy()
    expect(sums[3]).toBeTruthy()
  })
})
