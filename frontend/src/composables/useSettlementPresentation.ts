import { computed, type Ref } from 'vue'

import type { Customer, Supplier } from '@/api/masterdata'
import { formatLocalizedCurrency, formatLocalizedNumber } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info' | 'danger'

/**
 * Shared display helpers for the receipt/payment page. Receipts and payments
 * carry the same status set, so both tabs use one status mapping.
 */
export const useSettlementPresentation = (
  t: Translate,
  resources: {
    customers: Ref<Customer[]>
    suppliers: Ref<Supplier[]>
  }
) => {
  const customerMap = computed(
    () => new Map(resources.customers.value.map((item) => [String(item.id), item.name]))
  )
  const supplierMap = computed(
    () => new Map(resources.suppliers.value.map((item) => [String(item.id), item.name]))
  )

  const customerName = (id?: string | number | null) =>
    customerMap.value.get(String(id))
    || t('financeReportPages.payments.customerFallback', { id })

  const supplierName = (id?: string | number | null) =>
    supplierMap.value.get(String(id))
    || t('financeReportPages.payments.supplierFallback', { id })

  /** Allocation grids show bare numbers; totals and detail rows show currency. */
  const formatMoney = (value?: number) => formatLocalizedNumber(Number(value ?? 0), {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })

  const formatCurrency = (value?: number) => formatLocalizedCurrency(Number(value ?? 0))

  const paymentStatusLabel = (status?: string) => {
    if (status === 'DRAFT') return t('financeReportPages.payments.status.draft')
    if (status === 'POSTED' || status === 'COMPLETED') {
      return t('financeReportPages.payments.status.posted')
    }
    if (status === 'CANCELLED') return t('financeReportPages.payments.status.cancelled')
    return status || '-'
  }

  const paymentStatusTagType = (status?: string): TagType => {
    if (status === 'DRAFT') return 'info'
    if (status === 'POSTED' || status === 'COMPLETED') return 'success'
    return 'danger'
  }

  return {
    customerName,
    formatCurrency,
    formatMoney,
    paymentStatusLabel,
    paymentStatusTagType,
    supplierName
  }
}
