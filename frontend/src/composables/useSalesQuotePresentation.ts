import { formatLocalizedCurrency, formatLocalizedDate } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary'

/** Display helpers for sales quote lists and detail dialogs. */
export const useSalesQuotePresentation = (t: Translate) => {
  const formatMoney = (value?: number | string) => formatLocalizedCurrency(Number(value || 0))

  const formatDate = (value?: string) => formatLocalizedDate(value) || '-'

  const statusLabel = (status?: string) => {
    const map: Record<string, string> = {
      DRAFT: t('salesQuote.statusValue.draft'),
      CONFIRMED: t('salesQuote.statusValue.confirmed'),
      CONVERTED: t('salesQuote.statusValue.converted'),
      CANCELLED: t('salesQuote.statusValue.cancelled')
    }
    return status ? map[status] || status : '-'
  }

  const statusTagType = (status?: string): TagType => {
    const map: Record<string, TagType> = {
      DRAFT: 'info',
      CONFIRMED: 'success',
      CONVERTED: 'warning',
      CANCELLED: 'danger'
    }
    return status ? map[status] || 'info' : 'info'
  }

  const detailContent = (detail: {
    quoteNo?: string
    customerName?: string
    totalAmount?: number | string
    status?: string
    lines?: unknown[]
  }) =>
    t('salesQuote.detailContent', {
      quoteNo: detail.quoteNo || '-',
      customer: detail.customerName || '-',
      amount: formatMoney(detail.totalAmount),
      status: statusLabel(detail.status),
      count: detail.lines?.length || 0
    })

  return {
    detailContent,
    formatDate,
    formatMoney,
    statusLabel,
    statusTagType
  }
}
