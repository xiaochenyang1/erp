import { describe, expect, it, vi } from 'vitest'

import type { AccountSubject, LedgerEntry, LedgerSummary } from '@/api/finance'
import { useFinanceLedgerList } from './useFinanceLedgerList'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

const subjects: AccountSubject[] = [
  {
    id: '1',
    code: '1001',
    name: 'Cash',
    status: 'ACTIVE',
    children: [{ id: '2', code: '100101', name: 'Petty', status: 'ACTIVE' }]
  }
]

const summary = (overrides: Partial<LedgerSummary> = {}): LedgerSummary => ({
  subjectCode: '1001',
  subjectName: 'Cash',
  debitAmount: 10,
  creditAmount: 0,
  ...overrides
})

const entry = (overrides: Partial<LedgerEntry> = {}): LedgerEntry => ({
  bizDate: '2026-07-01',
  voucherId: 'v1',
  lineNo: 1,
  subjectCode: '1001',
  subjectName: 'Cash',
  summary: 'open',
  debitAmount: 10,
  creditAmount: 0,
  ...overrides
})

const createList = (overrides: Partial<Parameters<typeof useFinanceLedgerList>[1]> = {}) =>
  useFinanceLedgerList(t, {
    getAccountSubjectTree: vi.fn(async () => subjects),
    getLedgerSummary: vi.fn(async () => [summary()]),
    getLedgerEntries: vi.fn(async () => [
      entry({ voucherId: 'v1' }),
      entry({ voucherId: 'v2' }),
      entry({ voucherId: 'v3' })
    ]),
    exportLedger: vi.fn(async () => new Blob(['csv'])),
    downloadBlob: vi.fn(),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('finance ledger list', () => {
  it('loads subjects, general ledger and detail paging', async () => {
    const getLedgerEntries = vi.fn(async () => [
      entry({ voucherId: 'v1' }),
      entry({ voucherId: 'v2' }),
      entry({ voucherId: 'v3' })
    ])
    const list = createList({ getLedgerEntries })

    expect(await list.loadSubjects()).toBe(true)
    expect(list.subjectOptions.value).toHaveLength(1)
    expect(await list.loadGeneralLedger()).toBe(true)
    expect(list.generalLedger.value).toHaveLength(1)

    list.pagination.size = 2
    expect(await list.loadDetailLedger()).toBe(true)
    expect(list.pagination.total).toBe(3)
    expect(list.detailLedger.value).toHaveLength(2)

    await list.handlePageChange(2)
    expect(list.pagination.page).toBe(2)
    expect(list.detailLedger.value).toHaveLength(1)

    await list.handleSizeChange(10)
    expect(list.pagination.size).toBe(10)
    expect(list.pagination.page).toBe(1)
  })

  it('applies date range, switches tabs and drills into detail', async () => {
    const getLedgerSummary = vi.fn(async () => [summary()])
    const getLedgerEntries = vi.fn(async () => [entry()])
    const list = createList({ getLedgerSummary, getLedgerEntries })
    await list.loadSubjects()

    list.dateRange.value = ['2026-07-01', '2026-07-31']
    list.queryForm.subjectId = '2'
    await list.handleQuery()
    expect(list.queryForm.startDate).toBe('2026-07-01')
    expect(getLedgerSummary).toHaveBeenCalledWith(
      expect.objectContaining({ subjectCode: '100101', startDate: '2026-07-01' })
    )

    await list.handleTabChange('detail')
    expect(list.activeTab.value).toBe('detail')
    expect(getLedgerEntries).toHaveBeenCalled()

    await list.handleViewDetail(summary({ subjectCode: '100101' }))
    expect(list.queryForm.subjectId).toBe('2')
    expect(list.activeTab.value).toBe('detail')
  })

  it('exports and resets filters', async () => {
    const downloadBlob = vi.fn()
    const exportLedger = vi.fn(async () => new Blob(['x']))
    const onSuccess = vi.fn()
    const list = createList({ downloadBlob, exportLedger, onSuccess })
    await list.loadSubjects()
    list.queryForm.subjectId = '1'
    list.dateRange.value = ['2026-01-01', '2026-01-31']
    expect(await list.handleExport()).toBe(true)
    expect(exportLedger).toHaveBeenCalled()
    expect(downloadBlob).toHaveBeenCalled()
    expect(onSuccess).toHaveBeenCalledWith('financeReportPages.ledger.message.exported')

    await list.handleReset()
    expect(list.queryForm.subjectId).toBeUndefined()
    expect(list.dateRange.value).toEqual([])
  })
})
