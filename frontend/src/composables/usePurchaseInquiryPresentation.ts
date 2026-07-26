import type { Product, Supplier } from '@/api/masterdata'
import type { PurchaseInquiryLine } from '@/api/purchase'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info' | 'warning' | 'danger'

export const usePurchaseInquiryPresentation = (
  t: Translate,
  products: { value: Product[] },
  suppliers: { value: Supplier[] }
) => {
  const statusText = (status: string) =>
    ({
      DRAFT: t('purchaseInquiryOps.status.draft'),
      SUBMITTED: t('purchaseInquiryOps.status.submitted'),
      CLOSED: t('purchaseInquiryOps.status.closed'),
      CONVERTED: t('purchaseInquiryOps.status.converted'),
      CANCELLED: t('purchaseInquiryOps.status.cancelled')
    }[status] || status)

  const quoteStatusText = (status: string) =>
    ({
      PENDING: t('purchaseInquiryOps.quoteStatus.pending'),
      SELECTED: t('purchaseInquiryOps.quoteStatus.selected'),
      REJECTED: t('purchaseInquiryOps.quoteStatus.rejected')
    }[status] || status)

  const statusType = (status: string): TagType =>
    ({ DRAFT: 'info', SUBMITTED: 'warning', CLOSED: 'success', CONVERTED: 'success', CANCELLED: 'danger' }[status] || 'info') as TagType

  const productLabel = (product: Product) =>
    `${product.productCode || ''} ${product.productName || ''}`.trim() || String(product.id)

  const productLabelById = (productId?: string | number | null) => {
    if (productId == null || productId === '') return '-'
    const found = products.value.find((item) => String(item.id) === String(productId))
    return found ? productLabel(found) : String(productId)
  }

  const supplierLabelByEntity = (supplier: Supplier) =>
    supplier.supplierName || supplier.name || String(supplier.id)

  const supplierLabel = (supplierId?: string | number | null) => {
    if (supplierId == null || supplierId === '') return '-'
    const found = suppliers.value.find((item) => String(item.id) === String(supplierId))
    return found ? supplierLabelByEntity(found) : String(supplierId)
  }

  const inquiryLineProductLabel = (
    inquiryLineId: string | number,
    inquiryLines: PurchaseInquiryLine[]
  ) => {
    const line = inquiryLines.find((item) => String(item.id) === String(inquiryLineId))
    return line
      ? productLabelById(line.productId)
      : t('purchaseInquiryOps.inquiryLineFallback', { id: String(inquiryLineId) })
  }

  return {
    inquiryLineProductLabel,
    productLabel,
    productLabelById,
    quoteStatusText,
    statusText,
    statusType,
    supplierLabel,
    supplierLabelByEntity
  }
}
