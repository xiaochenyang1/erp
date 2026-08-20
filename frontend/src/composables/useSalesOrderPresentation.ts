import type { SalesOrder, SalesOrderItem } from '@/api/sales'
import { formatLocalizedNumber } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info' | 'warning' | 'danger'

export const useSalesOrderPresentation = (t: Translate) => {
  // Keep action visibility aligned with the lifecycle guards in SalesOrderService.
  // Approval actions require both the document and approval state to match.
  const canEdit = (row: SalesOrder) => ['DRAFT', 'REJECTED'].includes(row.status)
  const canSubmit = (row: SalesOrder) => ['DRAFT', 'REJECTED'].includes(row.status)
  const canApprove = (row: SalesOrder) =>
    row.status === 'SUBMITTED'
    && row.approvalStatus === 'IN_APPROVAL'
  const canReject = (row: SalesOrder) =>
    row.status === 'SUBMITTED'
    && row.approvalStatus === 'IN_APPROVAL'
  const canUnapprove = (row: SalesOrder) =>
    row.status === 'APPROVED'
    && row.approvalStatus === 'APPROVED'
    && row.deliveryStatus === 'NOT_DELIVERED'
  const canCancel = (row: SalesOrder) =>
    ['DRAFT', 'REJECTED', 'SUBMITTED'].includes(row.status)
    || (row.status === 'APPROVED' && row.deliveryStatus === 'NOT_DELIVERED')

  const lineAmount = (row: SalesOrderItem) => Number(row.quantity ?? 0) * Number(row.price ?? 0)

  const formatNumber = (value?: number) =>
    formatLocalizedNumber(Number(value ?? 0), { maximumFractionDigits: 4 })

  const formatMoney = (value?: number) => formatLocalizedNumber(Number(value ?? 0), {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })

  const statusText = (status?: string) => ({
    DRAFT: t('salesOrder.status.draft'),
    SUBMITTED: t('salesOrder.status.submitted'),
    APPROVED: t('salesOrder.status.approved'),
    REJECTED: t('salesOrder.status.rejected'),
    CONFIRMED: t('salesOrder.status.confirmed'),
    CANCELLED: t('salesOrder.status.cancelled'),
    CLOSED: t('salesOrder.status.closed')
  }[status || ''] || status || '-')

  const approvalText = (status?: string) => ({
    DRAFT: t('salesOrder.status.draft'),
    NOT_SUBMITTED: t('salesOrder.status.notSubmitted'),
    IN_APPROVAL: t('salesOrder.status.submitted'),
    PENDING: t('salesOrder.status.submitted'),
    APPROVED: t('salesOrder.status.approved'),
    REJECTED: t('salesOrder.status.rejected')
  }[status || ''] || status || '-')

  const deliveryText = (status?: string) => ({
    NOT_DELIVERED: t('salesOrder.status.notDelivered'),
    PARTIAL_DELIVERED: t('salesOrder.status.partial'),
    FULL_DELIVERED: t('salesOrder.status.delivered'),
    PARTIAL: t('salesOrder.status.partial'),
    COMPLETED: t('salesOrder.status.delivered')
  }[status || ''] || status || '-')

  const approvalTagType = (status?: string): TagType => {
    if (status === 'APPROVED') return 'success'
    if (status === 'REJECTED') return 'danger'
    if (status === 'IN_APPROVAL' || status === 'PENDING') return 'warning'
    return 'info'
  }

  return {
    approvalTagType,
    approvalText,
    canApprove,
    canCancel,
    canEdit,
    canReject,
    canSubmit,
    canUnapprove,
    deliveryText,
    formatMoney,
    formatNumber,
    lineAmount,
    statusText
  }
}
