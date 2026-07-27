import { formatLocalizedDateTime } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'info' | 'warning' | 'success' | 'danger' | 'primary'

/** Labels, tags and time formatting for workflow approval tasks. */
export const useWorkflowTaskPresentation = (t: Translate) => {
  const businessTypeLabel = (type?: string) => {
    const map: Record<string, string> = {
      PURCHASE_ORDER: t('workflow.purchaseOrder'),
      SALES_ORDER: t('workflow.salesOrder'),
      EXPENSE: t('workflow.expense')
    }
    return type ? map[type] || type : '-'
  }

  const taskStatusLabel = (status?: string) => {
    const map: Record<string, string> = {
      PENDING: t('workflow.pending'),
      APPROVED: t('workflow.approved'),
      REJECTED: t('workflow.rejected'),
      CANCELLED: t('workflow.cancelled')
    }
    return status ? map[status] || status : '-'
  }

  const taskStatusType = (status?: string): TagType => {
    const map: Record<string, TagType> = {
      PENDING: 'warning',
      APPROVED: 'success',
      REJECTED: 'danger',
      CANCELLED: 'info'
    }
    return status ? map[status] || 'info' : 'info'
  }

  const formatTime = (value?: string) => formatLocalizedDateTime(value) || '-'

  return {
    businessTypeLabel,
    formatTime,
    taskStatusLabel,
    taskStatusType
  }
}
