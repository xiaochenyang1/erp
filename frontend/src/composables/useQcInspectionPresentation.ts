import type { QcInspection } from '@/api/qc'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info' | 'warning' | 'danger'

const STATUS_KEYS: Record<string, string> = {
  DRAFT: 'qcInspection.status.draft',
  SUBMITTED: 'qcInspection.status.submitted',
  JUDGED: 'qcInspection.status.judged',
  CANCELLED: 'qcInspection.status.cancelled'
}

const STATUS_TAG_TYPES: Record<string, TagType> = {
  DRAFT: 'info',
  SUBMITTED: 'warning',
  JUDGED: 'success',
  CANCELLED: 'danger'
}

const TYPE_KEYS: Record<string, string> = {
  IQC: 'qcInspection.type.iqc',
  OQC: 'qcInspection.type.oqc',
  IPQC: 'qcInspection.type.ipqc'
}

export const useQcInspectionPresentation = (t: Translate) => {
  const statusText = (status?: string) => {
    if (!status) return ''
    const key = STATUS_KEYS[status]
    return key ? t(key) : status
  }

  const statusType = (status?: string): TagType =>
    (status && STATUS_TAG_TYPES[status]) || 'info'

  const inspectionTypeText = (type?: string) => {
    const key = TYPE_KEYS[type || '']
    return key ? t(key) : type || '-'
  }

  /** IQC references a receipt, OQC a delivery, IPQC a production order. */
  const sourceDocumentLabel = (type?: string) => {
    if (type === 'OQC') return t('qcInspection.salesDelivery')
    if (type === 'IPQC') return t('qcInspection.productionOrderId')
    return t('qcInspection.purchaseReceipt')
  }

  const sourceDocumentId = (inspection?: Partial<QcInspection>) => {
    if (!inspection) return ''
    if (inspection.inspectionType === 'OQC') return inspection.deliveryId
    if (inspection.inspectionType === 'IPQC') {
      return inspection.productionOrderId || inspection.orderId
    }
    return inspection.receiptId
  }

  const sourceDocumentText = (inspection: Partial<QcInspection>) => {
    const id = sourceDocumentId(inspection) || '-'
    if (inspection.inspectionType === 'OQC') return t('qcInspection.sourceOutbound', { id })
    if (inspection.inspectionType === 'IPQC') return t('qcInspection.sourceProduction', { id })
    return t('qcInspection.sourceInbound', { id })
  }

  return {
    inspectionTypeText,
    sourceDocumentId,
    sourceDocumentLabel,
    sourceDocumentText,
    statusText,
    statusType
  }
}
