import { formatLocalizedCurrency, formatLocalizedDate } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary'

/** Display helpers for purchase price lists. */
export const usePurchasePricePresentation = (t: Translate) => {
  const formatMoney = (value?: number | string) => formatLocalizedCurrency(Number(value || 0))

  const formatDate = (value?: string) => formatLocalizedDate(value) || '-'

  const formatEffectivePeriod = (from?: string, to?: string) =>
    `${formatDate(from)} ~ ${to ? formatDate(to) : t('purchasePrice.longTerm')}`

  const statusLabel = (status?: string) =>
    status === 'ACTIVE' ? t('purchasePrice.active') : t('purchasePrice.inactive')

  const statusTagType = (status?: string): TagType =>
    status === 'ACTIVE' ? 'success' : 'info'

  const scopeTagType = (supplierId?: string | number | null): TagType =>
    supplierId ? 'warning' : 'info'

  const scopeLabel = (supplierId?: string | number | null) =>
    supplierId ? t('purchasePrice.supplierSpecific') : t('purchasePrice.productGeneral')

  const scopeDetail = (row: {
    supplierId?: string | number | null
    supplierName?: string
  }) => (row.supplierId ? row.supplierName || String(row.supplierId) : t('purchasePrice.allSuppliers'))

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
