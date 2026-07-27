import { formatLocalizedCurrency, formatLocalizedDate } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary'

/** Display helpers for finance invoice registration lists. */
export const useFinanceInvoicePresentation = (t: Translate) => {
  const formatAmount = (amount?: number | string) =>
    formatLocalizedCurrency(Number(amount || 0))

  const formatDate = (value?: string) => formatLocalizedDate(value) || '-'

  const typeLabel = (type?: string) => {
    const map: Record<string, string> = {
      INPUT: t('financeReportPages.invoices.typeValue.input'),
      OUTPUT: t('financeReportPages.invoices.typeValue.output')
    }
    return type ? map[type] || type : '-'
  }

  const statusLabel = (status?: string) => {
    const map: Record<string, string> = {
      DRAFT: t('financeReportPages.invoices.status.draft'),
      POSTED: t('financeReportPages.invoices.status.posted'),
      CANCELLED: t('financeReportPages.invoices.status.cancelled')
    }
    return status ? map[status] || status : '-'
  }

  const statusType = (status?: string): TagType => {
    const map: Record<string, TagType> = {
      DRAFT: 'info',
      POSTED: 'success',
      CANCELLED: 'danger'
    }
    return status ? map[status] || 'info' : 'info'
  }

  return {
    formatAmount,
    formatDate,
    statusLabel,
    statusType,
    typeLabel
  }
}
