import { computed, type Ref } from 'vue'

import type { Supplier } from '@/api/masterdata'

type Translate = (key: string, params?: Record<string, unknown>) => string

const STATUS_VALUES = [
  'DRAFT',
  'SUBMITTED',
  'APPROVED',
  'REJECTED',
  'CONVERTED',
  'CANCELLED'
] as const

/** Display helpers for purchase requisition status, approval and supplier labels. */
export const usePurchaseRequisitionPresentation = (
  t: Translate,
  suppliers: Ref<Supplier[]>
) => {
  const statuses = [...STATUS_VALUES]

  const supplierMap = computed(
    () => new Map(suppliers.value.map((item) => [String(item.id), item]))
  )

  const statusLabel = (status?: string) => {
    if (!status) return '-'
    const key = `purchaseRequisition.statusValue.${status.toLowerCase()}`
    const translated = t(key)
    return translated === key ? status : translated
  }

  const approvalLabel = (status?: string | null) => {
    if (!status) return '-'
    const key = `purchaseRequisition.approvalValue.${status.toLowerCase()}`
    const translated = t(key)
    return translated === key ? status : translated
  }

  const supplierLabel = (supplierId?: string | number | null) => {
    if (supplierId == null || supplierId === '') return '-'
    const supplier = supplierMap.value.get(String(supplierId))
    return supplier
      ? (supplier.supplierName || supplier.name || String(supplierId))
      : String(supplierId)
  }

  return {
    approvalLabel,
    statusLabel,
    statuses,
    supplierLabel
  }
}
