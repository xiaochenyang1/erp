import { request } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

export interface FundAccount {
  id: string
  accountCode: string
  accountName: string
  accountType: 'BANK' | 'CASH' | string
  bankName?: string
  bankAccountNo?: string
  currencyCode: string
  openingBalance: number
  status: 'ENABLED' | 'DISABLED' | string
  remark?: string
  createdTime?: string
}

export interface FundAccountQuery extends PageQuery {
  accountType?: string
  status?: string
  keyword?: string
}

export interface FundAccountCreateRequest {
  accountCode: string
  accountName: string
  accountType: string
  bankName?: string
  bankAccountNo?: string
  currencyCode: string
  openingBalance: number
  remark?: string
}

export interface BankStatement {
  id: string
  fundAccountId: string
  statementNo: string
  externalTxnNo?: string
  transactionDate: string
  direction: 'IN' | 'OUT' | string
  amount: number
  counterpartyName?: string
  summary: string
  status: 'UNMATCHED' | 'MATCHED' | string
  matchedBizType?: string
  matchedBizId?: string
  matchedBizNo?: string
  matchedTime?: string
  matchedBy?: string
  unmatchReason?: string
  remark?: string
  createdTime?: string
}

export interface BankStatementQuery extends PageQuery {
  fundAccountId?: string | number
  direction?: string
  status?: string
  transactionDateFrom?: string
  transactionDateTo?: string
  matchedBizType?: string
  matchedBizNo?: string
}

export interface BankStatementCreateRequest {
  fundAccountId: string | number
  externalTxnNo?: string
  transactionDate: string
  direction: string
  amount: number
  counterpartyName?: string
  summary: string
  remark?: string
}

export interface BankStatementMatchRequest {
  bizType: string
  bizId: string | number
  remark?: string
}

export const getFundAccounts = (params: FundAccountQuery) => {
  return request.get<PageResponse<FundAccount>>('/finance/fund/accounts', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeFundAccount)
  }))
}

export const getFundAccount = (id: string | number) => {
  return request.get<FundAccount>(`/finance/fund/accounts/${id}`).then(normalizeFundAccount)
}

export const createFundAccount = (data: FundAccountCreateRequest) => {
  return request.post<FundAccount>('/finance/fund/accounts', data).then(normalizeFundAccount)
}

export const getBankStatements = (params: BankStatementQuery) => {
  return request.get<PageResponse<BankStatement>>('/finance/fund/statements', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeBankStatement)
  }))
}

export const getBankStatement = (id: string | number) => {
  return request.get<BankStatement>(`/finance/fund/statements/${id}`).then(normalizeBankStatement)
}

export const createBankStatement = (data: BankStatementCreateRequest) => {
  return request.post<BankStatement>('/finance/fund/statements', data).then(normalizeBankStatement)
}

export const matchBankStatement = (id: string | number, data: BankStatementMatchRequest) => {
  return request.post<BankStatement>(`/finance/fund/statements/${id}/match`, data).then(normalizeBankStatement)
}

export const unmatchBankStatement = (id: string | number, reason: string) => {
  return request.post<BankStatement>(`/finance/fund/statements/${id}/unmatch`, { reason }).then(normalizeBankStatement)
}

const normalizeFundAccount = (account: FundAccount): FundAccount => ({
  ...account,
  id: String(account.id),
  openingBalance: account.openingBalance ?? 0,
  currencyCode: account.currencyCode || 'CNY'
})

const normalizeBankStatement = (statement: BankStatement): BankStatement => ({
  ...statement,
  id: String(statement.id),
  fundAccountId: String(statement.fundAccountId),
  matchedBizId: statement.matchedBizId != null ? String(statement.matchedBizId) : undefined,
  matchedBy: statement.matchedBy != null ? String(statement.matchedBy) : undefined,
  amount: statement.amount ?? 0
})
