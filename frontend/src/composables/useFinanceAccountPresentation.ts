import type { FinanceAccountStatus } from '@/api/finance'
import {
  formatLocalizedCurrency,
  formatLocalizedDate,
  formatLocalizedDateTime
} from '@/utils/locale'

type Translate = (key: string) => string
type AccountTagType = 'danger' | 'warning' | 'success' | 'info'

const statusKeyMap: Record<FinanceAccountStatus, string> = {
  UNSETTLED: 'financeAccount.status.unsettled',
  PARTIALLY_SETTLED: 'financeAccount.status.partiallySettled',
  SETTLED: 'financeAccount.status.settled',
  OFFSET: 'financeAccount.status.offset'
}

const statusTypeMap: Record<FinanceAccountStatus, AccountTagType> = {
  UNSETTLED: 'danger',
  PARTIALLY_SETTLED: 'warning',
  SETTLED: 'success',
  OFFSET: 'info'
}

/** Localized amount, date and status rendering shared by receivables and payables. */
export const useFinanceAccountPresentation = (t: Translate) => {
  const formatCurrency = (value?: number) => formatLocalizedCurrency(Number(value ?? 0))
  const formatDate = (value?: string) => formatLocalizedDate(value) || '-'
  const formatDateTime = (value?: string) => formatLocalizedDateTime(value) || '-'
  const accountStatusLabel = (status: FinanceAccountStatus) => t(statusKeyMap[status])
  const accountStatusType = (status: FinanceAccountStatus) => statusTypeMap[status]

  return {
    accountStatusLabel,
    accountStatusType,
    formatCurrency,
    formatDate,
    formatDateTime
  }
}
