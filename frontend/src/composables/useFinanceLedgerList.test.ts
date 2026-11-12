import { describe, expect, it, vi } from 'vitest'

import type {
  AccountSubject,
  BalanceSheet,
  CashFlow,
  IncomeStatement,
  LedgerEntry,
  LedgerSummary,
  TrialBalance
} from '@/api/finance'
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
  id: 'entry-1',
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

const trialBalance = (): TrialBalance => ({
  dateFrom: '2026-07-01',
  dateTo: '2026-07-31',
  rows: [],
  openingDebit: 100,
  openingCredit: 100,
  periodDebit: 50,
  periodCredit: 50,
  closingDebit: 150,
  closingCredit: 150,
  openingBalanced: true,
  periodBalanced: true,
  closingBalanced: true
})

const balanceSheet = (): BalanceSheet => ({
  asOfDate: '2026-07-31',
  formulaVersion: 'TYPE_MAPPING_V1',
  assetLines: [],
  liabilityLines: [],
  equityLines: [],
  totalAssets: 100,
  totalLiabilities: 60,
  totalEquity: 40,
  liabilitiesAndEquity: 100,
  difference: 0,
  balanced: true,
  unclassifiedSubjectCodes: [],
  mappingErrors: []
})

const incomeStatement = (): IncomeStatement => ({
  dateFrom: '2026-07-01',
  dateTo: '2026-07-31',
  formulaVersion: 'TYPE_MAPPING_V1',
  revenueLines: [],
  expenseLines: [],
  totalRevenue: 100,
  totalExpense: 30,
  netProfit: 70,
  unclassifiedSubjectCodes: [],
  mappingErrors: []
})

const cashFlow = (): CashFlow => ({
  dateFrom: '2026-07-01',
  dateTo: '2026-07-31',
  formulaVersion: 'CASH_FLOW_DIRECT_V1',
  operatingLines: [],
  investingLines: [],
  financingLines: [],
  netOperatingCashFlow: 100,
  netInvestingCashFlow: -20,
  netFinancingCashFlow: 0,
  netIncreaseInCash: 80,
  openingCash: 20,
  closingCash: 100,
  reconciliationDifference: 0,
  reconciled: true,
  unmappedSubjectCodes: [],
  mappingErrors: []
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

  it('requires a date range and loads the trial balance', async () => {
    const getTrialBalance = vi.fn(async () => trialBalance())
    const onError = vi.fn()
    const list = createList({ getTrialBalance, onError })

    expect(await list.handleTabChange('trial')).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.ledger.message.trialDateRequired')

    list.dateRange.value = ['2026-07-01', '2026-07-31']
    expect(await list.handleQuery()).toBe(true)
    expect(getTrialBalance).toHaveBeenCalledWith(expect.objectContaining({
      startDate: '2026-07-01',
      endDate: '2026-07-31'
    }))
    expect(list.trialBalance.value?.closingBalanced).toBe(true)
  })

  it('loads balance sheet and income statement in sequence', async () => {
    const getBalanceSheet = vi.fn(async () => balanceSheet())
    const getIncomeStatement = vi.fn(async () => incomeStatement())
    const list = createList({ getBalanceSheet, getIncomeStatement })
    list.dateRange.value = ['2026-07-01', '2026-07-31']

    expect(await list.handleTabChange('balance')).toBe(true)
    expect(getBalanceSheet).toHaveBeenCalledWith('2026-07-31')
    expect(list.balanceSheet.value?.balanced).toBe(true)

    expect(await list.handleTabChange('income')).toBe(true)
    expect(getIncomeStatement).toHaveBeenCalledWith(expect.objectContaining({
      startDate: '2026-07-01',
      endDate: '2026-07-31'
    }))
    expect(list.incomeStatement.value?.netProfit).toBe(70)
  })

  it('loads and exports the direct cash flow statement', async () => {
    const getCashFlow = vi.fn(async () => cashFlow())
    const exportCashFlow = vi.fn(async () => new Blob(['cash-flow']))
    const downloadBlob = vi.fn()
    const list = createList({ getCashFlow, exportCashFlow, downloadBlob })
    list.dateRange.value = ['2026-07-01', '2026-07-31']

    expect(await list.handleTabChange('cash')).toBe(true)
    expect(getCashFlow).toHaveBeenCalledWith(expect.objectContaining({
      startDate: '2026-07-01',
      endDate: '2026-07-31'
    }))
    expect(list.cashFlow.value?.reconciled).toBe(true)

    expect(await list.handleExport()).toBe(true)
    expect(exportCashFlow).toHaveBeenCalled()
    expect(downloadBlob).toHaveBeenCalledWith(
      expect.any(Blob),
      expect.stringContaining('financeReportPages.ledger.cashFlowFileName')
    )
  })

  it('exports the active financial statement with its own file name', async () => {
    const exportBalanceSheet = vi.fn(async () => new Blob(['balance']))
    const downloadBlob = vi.fn()
    const list = createList({ exportBalanceSheet, downloadBlob })
    list.dateRange.value = ['2026-07-01', '2026-07-31']
    list.activeTab.value = 'balance'

    expect(await list.handleExport()).toBe(true)
    expect(exportBalanceSheet).toHaveBeenCalledWith('2026-07-31')
    expect(downloadBlob).toHaveBeenCalledWith(
      expect.any(Blob),
      expect.stringContaining('financeReportPages.ledger.balanceFileName')
    )
  })
})
