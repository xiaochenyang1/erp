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

  // Keep these predicates in lockstep with PurchaseOrderWorkflowService and
  // PurchaseOrderService so the list never advertises an action the API will
  // reject for the current lifecycle state.
  const canEdit = (row: PurchaseOrderLike) =>
    row.status === 'DRAFT' || row.status === 'REJECTED'

  const canSubmit = (row: PurchaseOrderLike) =>
    row.status === 'DRAFT' || row.status === 'REJECTED'

  const canApprove = (row: PurchaseOrderLike) =>
    row.status === 'SUBMITTED' && row.approvalStatus === 'IN_APPROVAL'

  const canReject = (row: PurchaseOrderLike) =>
    row.status === 'SUBMITTED' && row.approvalStatus === 'IN_APPROVAL'

  const isPendingOrder = (row: PurchaseOrderLike) =>
    row.status === 'SUBMITTED' && row.approvalStatus === 'IN_APPROVAL'

  const isApprovedOrder = (row: PurchaseOrderLike) => row.status === 'APPROVED'

  const canCancel = (row: PurchaseOrderLike) =>
    ['DRAFT', 'REJECTED', 'SUBMITTED'].includes(row.status)

  const canClose = (row: PurchaseOrderLike) =>
    row.status === 'APPROVED' && row.receiptStatus !== 'RECEIVED'

  const canUnapprove = (row: PurchaseOrderLike) =>
    row.status === 'APPROVED'
    && row.approvalStatus === 'APPROVED'
    && row.receiptStatus === 'NOT_RECEIVED'

  // Keep the old names as aliases for callers that have not migrated yet.
  const canCancelOrder = canCancel
  const canCloseOrder = canClose
  const canUnapproveOrder = canUnapprove

  const pendingCount = (rows: PurchaseOrderLike[]) =>
    rows.filter((item) => isPendingOrder(item)).length

  const approvedCount = (rows: PurchaseOrderLike[]) =>
    rows.filter((item) => isApprovedOrder(item)).length

  return {
    approvedCount,
    canApprove,
    canCancel,
    canClose,
    canEdit,
    canReject,
    canSubmit,
    canUnapprove,
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
