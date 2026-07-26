import { describe, expect, it, vi } from 'vitest'

import type { BankStatement, FundAccount } from '@/api/fund'
import { useFundList } from './useFundList'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.id != null ? `${key}:${params.id}` : key

const account = (overrides: Partial<FundAccount> = {}) => ({
  id: '1',
  accountCode: 'BANK001',
  accountName: '工行基本户',
  accountType: 'BANK',
  currencyCode: 'CNY',
  openingBalance: 0,
  status: 'ENABLED',
  ...overrides
} as FundAccount)

const statement = (overrides: Partial<BankStatement> = {}) => ({
  id: 's1',
  fundAccountId: '1',
  statementNo: 'BS001',
  transactionDate: '2026-07-20',
  direction: 'IN',
  amount: 100,
  summary: '货款',
  status: 'UNMATCHED',
  ...overrides
} as BankStatement)

const createList = (overrides: Partial<Parameters<typeof useFundList>[1]> = {}) =>
  useFundList(t, {
    getAccounts: vi.fn(async () => ({ records: [account()], total: 1 } as any)),
    getAccount: vi.fn(async () => account({ remark: '详情' })),
    getStatements: vi.fn(async () => ({ records: [statement()], total: 1 } as any)),
    getStatement: vi.fn(async () => statement({ summary: '详情摘要' })),
    onError: vi.fn(),
    ...overrides
  })

describe('fund list', () => {
  it('loads the paged accounts and refreshes the enabled options', async () => {
    const getAccounts = vi.fn(async () => ({ records: [account()], total: 7 } as any))
    const list = createList({ getAccounts })

    await list.loadAccounts()
    expect(list.accountData.value).toHaveLength(1)
    expect(list.accountTotal.value).toBe(7)
    expect(list.accountLoading.value).toBe(false)
    expect(getAccounts).toHaveBeenNthCalledWith(1, list.accountQuery)
    // Options must stay limited to enabled accounts, independent of the filters.
    expect(getAccounts).toHaveBeenNthCalledWith(2, { pageNo: 1, pageSize: 200, status: 'ENABLED' })
    expect(list.allAccounts.value).toHaveLength(1)
  })

  it('keeps the account options fresh even when the paged query fails', async () => {
    const onError = vi.fn()
    const getAccounts = vi.fn(async (params: any) => {
      if (params.pageSize === 200) return { records: [account()], total: 1 } as any
      throw new Error('boom')
    })
    const list = createList({ getAccounts, onError })

    await list.loadAccounts()
    expect(onError).toHaveBeenCalledWith('financeReportPages.funds.message.accountsLoadFailed')
    expect(list.accountLoading.value).toBe(false)
    expect(list.allAccounts.value).toHaveLength(1)
  })

  it('resets to the first page on search but keeps it while paginating', async () => {
    const getAccounts = vi.fn(async () => ({ records: [], total: 0 } as any))
    const getStatements = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getAccounts, getStatements })

    list.accountQuery.pageNo = 4
    list.accountQuery.keyword = 'BANK'
    await list.searchAccounts()
    expect(getAccounts).toHaveBeenNthCalledWith(1, expect.objectContaining({
      keyword: 'BANK',
      pageNo: 1
    }))

    list.accountQuery.pageNo = 3
    await list.loadAccounts()
    expect(getAccounts).toHaveBeenNthCalledWith(3, expect.objectContaining({ pageNo: 3 }))

    list.statementQuery.pageNo = 6
    list.statementQuery.direction = 'OUT'
    await list.searchStatements()
    expect(getStatements).toHaveBeenLastCalledWith(expect.objectContaining({
      direction: 'OUT',
      pageNo: 1
    }))

    list.statementQuery.pageNo = 2
    await list.loadStatements()
    expect(getStatements).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 2 }))
  })

  it('clears every filter on reset and reloads from the first page', async () => {
    const list = createList()

    Object.assign(list.accountQuery, { keyword: 'x', accountType: 'CASH', status: 'DISABLED', pageNo: 5 })
    await list.resetAccountQuery()
    expect(list.accountQuery.keyword).toBe('')
    expect(list.accountQuery.accountType).toBe('')
    expect(list.accountQuery.status).toBe('')
    expect(list.accountQuery.pageNo).toBe(1)

    Object.assign(list.statementQuery, { fundAccountId: '1', direction: 'IN', status: 'MATCHED', pageNo: 3 })
    await list.resetStatementQuery()
    expect(list.statementQuery.fundAccountId).toBeUndefined()
    expect(list.statementQuery.direction).toBe('')
    expect(list.statementQuery.status).toBe('')
    expect(list.statementQuery.pageNo).toBe(1)
  })

  it('reports statement load failures without leaving the table spinning', async () => {
    const onError = vi.fn()
    const list = createList({
      getStatements: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    await list.loadStatements()
    expect(onError).toHaveBeenCalledWith('financeReportPages.funds.message.statementsLoadFailed')
    expect(list.statementLoading.value).toBe(false)
    expect(list.statementData.value).toEqual([])
  })

  it('loads the account options once when the statement tab opens', async () => {
    const getAccounts = vi.fn(async () => ({ records: [account()], total: 1 } as any))
    const getStatements = vi.fn(async () => ({ records: [statement()], total: 1 } as any))
    const list = createList({ getAccounts, getStatements })

    await list.loadStatementTab()
    expect(getAccounts).toHaveBeenCalledTimes(1)
    expect(getStatements).toHaveBeenCalledTimes(1)

    // Options are already cached, so switching back only refreshes the rows.
    await list.loadStatementTab()
    expect(getAccounts).toHaveBeenCalledTimes(1)
    expect(getStatements).toHaveBeenCalledTimes(2)
  })

  it('fetches the full record for both detail dialogs', async () => {
    const getAccount = vi.fn(async () => account({ remark: '基本户' }))
    const getStatement = vi.fn(async () => statement({ summary: '完整摘要' }))
    const list = createList({ getAccount, getStatement })

    expect(await list.handleViewAccount(account())).toBe(true)
    expect(getAccount).toHaveBeenCalledWith('1')
    expect(list.selectedAccount.value?.remark).toBe('基本户')
    expect(list.accountDetailVisible.value).toBe(true)
    expect(list.detailLoading.value).toBe(false)

    expect(await list.handleViewStatement(statement())).toBe(true)
    expect(getStatement).toHaveBeenCalledWith('s1')
    expect(list.selectedStatementDetail.value?.summary).toBe('完整摘要')
    expect(list.statementDetailVisible.value).toBe(true)
  })

  it('closes the detail dialog and reports the failure when loading fails', async () => {
    const onError = vi.fn()
    const list = createList({
      getAccount: vi.fn(async () => { throw new Error('boom') }),
      getStatement: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    expect(await list.handleViewAccount(account())).toBe(false)
    expect(list.accountDetailVisible.value).toBe(false)
    expect(list.selectedAccount.value).toBeUndefined()
    expect(onError).toHaveBeenCalledWith('financeReportPages.funds.message.accountDetailLoadFailed')

    expect(await list.handleViewStatement(statement())).toBe(false)
    expect(list.statementDetailVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.funds.message.statementDetailLoadFailed')
    expect(list.detailLoading.value).toBe(false)
  })

  it('drops the previous record before loading so stale details never show', async () => {
    let resolveNext: ((value: FundAccount) => void) | undefined
    const getAccount = vi.fn(() => new Promise<FundAccount>((resolve) => { resolveNext = resolve }))
    const list = createList({ getAccount })

    list.selectedAccount.value = account({ accountName: '旧账户' })
    const pending = list.handleViewAccount(account({ id: '2' }))
    expect(list.selectedAccount.value).toBeUndefined()
    expect(list.detailLoading.value).toBe(true)

    resolveNext?.(account({ id: '2', accountName: '新账户' }))
    await pending
    expect(list.selectedAccount.value?.accountName).toBe('新账户')
  })
})
