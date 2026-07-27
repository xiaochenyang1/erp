import { formatLocalizedDateTime } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'info' | 'warning' | 'success' | 'danger' | 'primary'

/** Labels and tags for workflow approval records. */
export const useWorkflowRecordPresentation = (t: Translate) => {
  const businessTypeLabel = (type?: string) => {
    const map: Record<string, string> = {
      PURCHASE_ORDER: t('workflowRecord.businessTypes.purchaseOrder'),
      SALES_ORDER: t('workflowRecord.businessTypes.salesOrder'),
      EXPENSE: t('workflowRecord.businessTypes.expense')
    }
    return type ? map[type] || type : '-'
  }

  const actionLabel = (action?: string) => {
    const map: Record<string, string> = {
      SUBMIT: t('workflowRecord.actions.submit'),
      APPROVE: t('workflowRecord.actions.approve'),
      REJECT: t('workflowRecord.actions.reject'),
      WITHDRAW: t('workflowRecord.actions.withdraw'),
      CANCEL: t('workflowRecord.actions.cancel')
    }
    return action ? map[action] || action : '-'
  }

  const actionType = (action?: string): TagType => {
    const map: Record<string, TagType> = {
      SUBMIT: 'info',
      APPROVE: 'success',
      REJECT: 'danger',
      WITHDRAW: 'warning',
      CANCEL: 'info'
    }
    return action ? map[action] || 'info' : 'info'
  }

  const formatTime = (value?: string) => formatLocalizedDateTime(value) || '-'

  const recordSummary = (record?: {
    businessType?: string
    businessNo?: string
    businessId?: string | number
  }) => {
    if (!record) return ''
    return t('workflowRecord.recordSummary', {
      type: businessTypeLabel(record.businessType),
      no: record.businessNo || record.businessId
    })
  }

  return {
    actionLabel,
    actionType,
    businessTypeLabel,
    formatTime,
    recordSummary
  }
}
