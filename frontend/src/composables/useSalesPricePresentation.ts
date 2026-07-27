import { formatLocalizedCurrency, formatLocalizedDate } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary'

/** Display helpers for sales price lists. */
export const useSalesPricePresentation = (t: Translate) => {
  const formatMoney = (value?: number | string) => formatLocalizedCurrency(Number(value || 0))

  const formatDate = (value?: string) => formatLocalizedDate(value) || '-'

  const formatEffectivePeriod = (from?: string, to?: string) =>
    `${formatDate(from)} ~ ${to ? formatDate(to) : t('salesPrice.longTerm')}`

  const statusLabel = (status?: string) =>
    status === 'ACTIVE' ? t('salesPrice.active') : t('salesPrice.inactive')

  const statusTagType = (status?: string): TagType =>
    status === 'ACTIVE' ? 'success' : 'info'

  const scopeTagType = (customerId?: string | number | null): TagType =>
    customerId ? 'warning' : 'info'

  const scopeLabel = (customerId?: string | number | null) =>
    customerId ? t('salesPrice.customerSpecific') : t('salesPrice.productGeneral')

  const scopeDetail = (row: {
    customerId?: string | number | null
    customerName?: string
  }) => (row.customerId ? row.customerName || String(row.customerId) : t('salesPrice.allCustomers'))

  return {
    formatDate,
    formatEffectivePeriod,
    formatMoney,
    scopeDetail,
    scopeLabel,
    scopeTagType,
    statusLabel,
    statusTagType
  }
}
