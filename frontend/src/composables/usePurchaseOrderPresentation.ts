import { computed, type Ref } from 'vue'

import type { PurchaseOrder } from '@/api/purchase'
import { formatLocalizedNumber } from '@/utils/locale'

export type PurchaseOrderLike = Pick<PurchaseOrder, 'approvalStatus' | 'receiptStatus'> & {
  status: string
}

export const usePurchaseOrderPresentation = () => {
  const formatMoney = (value?: number) => formatLocalizedNumber(Number(value ?? 0), {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })

  const isPendingOrder = (row: PurchaseOrderLike) =>
    row.status === 'SUBMITTED'
    || row.status === 'PENDING'
    || row.approvalStatus === 'IN_APPROVAL'

  const isApprovedOrder = (row: PurchaseOrderLike) => row.status === 'APPROVED'

  const canCancelOrder = (row: PurchaseOrderLike) =>
    ['DRAFT', 'REJECTED', 'SUBMITTED'].includes(row.status)

  const canCloseOrder = (row: PurchaseOrderLike) =>
    row.status === 'APPROVED' && row.receiptStatus !== 'RECEIVED'

  // Align with backend PurchaseOrderService.unapprove:
  // approved and not yet received into inventory.
  const canUnapproveOrder = (row: PurchaseOrderLike) =>
    row.status === 'APPROVED'
    && (row.approvalStatus === 'APPROVED' || !row.approvalStatus)
    && (row.receiptStatus === 'NOT_RECEIVED' || !row.receiptStatus)

  const pendingCount = (rows: PurchaseOrderLike[]) =>
    rows.filter((item) => isPendingOrder(item)).length

  const approvedCount = (rows: PurchaseOrderLike[]) =>
    rows.filter((item) => isApprovedOrder(item)).length

  return {
    approvedCount,
    canCancelOrder,
    canCloseOrder,
    canUnapproveOrder,
    formatMoney,
    isApprovedOrder,
    isPendingOrder,
    pendingCount
  }
}

export const usePurchaseOrderSummary = (
  tableData: Ref<PurchaseOrderLike[]>
) => {
  const presentation = usePurchaseOrderPresentation()
  const pendingCount = computed(() => presentation.pendingCount(tableData.value))
  const approvedCount = computed(() => presentation.approvedCount(tableData.value))

  return {
    ...presentation,
    approvedCount,
    pendingCount
  }
}
