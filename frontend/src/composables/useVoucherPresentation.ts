import type { Voucher } from '@/api/finance'
import { formatLocalizedCurrency, formatLocalizedDate } from '@/utils/locale'

type Translate = (key: string) => string
type SourceTagType = 'success' | 'warning' | 'info'
type StatusTagType = 'info' | 'warning' | 'success' | 'danger'

/** Localized values and tag mappings for read-only finance vouchers. */
export const useVoucherPresentation = (t: Translate) => {
  const formatMoney = (amount?: number) => formatLocalizedCurrency(Number(amount || 0))
  const formatDate = (value?: string) => formatLocalizedDate(value)

  const sourceTypeLabel = (sourceType?: string) => {
    const labels: Record<string, string> = {
      EXPENSE: t('financeReportPages.vouchers.sourceValue.expense'),
      EXPENSE_REVERSAL: t('financeReportPages.vouchers.sourceValue.expenseReversal')
    }
    return sourceType ? labels[sourceType] || sourceType : '-'
  }

  const sourceTypeTag = (sourceType?: string): SourceTagType => {
    const types: Record<string, SourceTagType> = {
      EXPENSE: 'success',
      EXPENSE_REVERSAL: 'warning'
    }
    return sourceType ? types[sourceType] || 'info' : 'info'
  }

  const statusLabel = (status: string) => {
    const labels: Record<string, string> = {
      DRAFT: t('financeReportPages.vouchers.status.draft'),
      APPROVED: t('financeReportPages.vouchers.status.approved'),
      POSTED: t('financeReportPages.vouchers.status.posted'),
      CANCELLED: t('financeReportPages.vouchers.status.cancelled')
    }
    return labels[status] || status
  }

  const statusType = (status: string): StatusTagType => {
    const types: Record<string, StatusTagType> = {
      DRAFT: 'info',
      APPROVED: 'warning',
      POSTED: 'success',
      CANCELLED: 'danger'
    }
    return types[status] || 'info'
  }

  const toVoucherRow = (row: unknown) => row as Voucher

  return {
    formatDate,
    formatMoney,
    sourceTypeLabel,
    sourceTypeTag,
    statusLabel,
    statusType,
    toVoucherRow
  }
}
