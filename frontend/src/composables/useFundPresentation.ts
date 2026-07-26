import { computed, type Ref } from 'vue'

import type { FundAccount } from '@/api/fund'
import { formatLocalizedNumber } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string

const ACCOUNT_TYPE_KEYS: Record<string, string> = {
  BANK: 'financeReportPages.funds.accountType.bank',
  CASH: 'financeReportPages.funds.accountType.cash'
}

const ACCOUNT_STATUS_KEYS: Record<string, string> = {
  ENABLED: 'financeReportPages.funds.enabled',
  DISABLED: 'financeReportPages.funds.disabled'
}

const DIRECTION_KEYS: Record<string, string> = {
  IN: 'financeReportPages.funds.income',
  OUT: 'financeReportPages.funds.expense'
}

const STATEMENT_STATUS_KEYS: Record<string, string> = {
  MATCHED: 'financeReportPages.funds.matched',
  UNMATCHED: 'financeReportPages.funds.unmatched'
}

const BUSINESS_TYPE_KEYS: Record<string, string> = {
  RECEIPT: 'financeReportPages.funds.receipt',
  PAYMENT: 'financeReportPages.funds.payment'
}

/** Display helpers for fund accounts and bank statements. */
export const useFundPresentation = (
  t: Translate,
  accounts: Ref<FundAccount[]>
) => {
  const accountMap = computed(
    () => new Map(accounts.value.map((item) => [String(item.id), item]))
  )

  /** Statements reference accounts by id, so the label is resolved from options. */
  const accountName = (id?: string | number | null) => {
    const account = accountMap.value.get(String(id))
    return account
      ? `${account.accountCode} - ${account.accountName}`
      : t('financeReportPages.funds.accountFallback', { id })
  }

  const translateOrRaw = (map: Record<string, string>, value?: string) => {
    if (!value) return ''
    const key = map[value]
    return key ? t(key) : value
  }

  const accountTypeLabel = (type?: string) => translateOrRaw(ACCOUNT_TYPE_KEYS, type)
  const accountStatusLabel = (status?: string) => translateOrRaw(ACCOUNT_STATUS_KEYS, status)
  const statementDirectionLabel = (direction?: string) => translateOrRaw(DIRECTION_KEYS, direction)
  const statementStatusLabel = (status?: string) => translateOrRaw(STATEMENT_STATUS_KEYS, status)

  const businessTypeLabel = (type?: string) => {
    const key = BUSINESS_TYPE_KEYS[type || '']
    return key ? t(key) : type || '-'
  }

  const formatMoney = (value?: number) => formatLocalizedNumber(Number(value ?? 0), {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })

  return {
    accountName,
    accountStatusLabel,
    accountTypeLabel,
    businessTypeLabel,
    formatMoney,
    statementDirectionLabel,
    statementStatusLabel
  }
}
