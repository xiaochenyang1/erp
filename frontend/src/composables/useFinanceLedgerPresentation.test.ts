import { describe, expect, it } from 'vitest'

import type { AccountSubject } from '@/api/finance'
import {
  activeAccountSubjects,
  findSubjectByCode,
  findSubjectById,
  flattenAccountSubjects,
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

  it('keeps inactive subjects available for ledger history but excludes them from mapping choices', () => {
    const subjects: AccountSubject[] = [
      {
        id: '10',
        code: '1000',
        name: 'Assets',
        status: 'ACTIVE',
        children: [
          { id: '11', code: '1001', name: 'Cash', status: 'ACTIVE' },
          { id: '12', code: '1002', name: 'Closed bank', status: 'DISABLED' }
        ]
      }
    ]

    expect(flattenAccountSubjects(subjects).map((subject) => subject.code))
      .toEqual(['1000', '1001', '1002'])
    expect(activeAccountSubjects(subjects).map((subject) => subject.code))
      .toEqual(['1000', '1001'])
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
