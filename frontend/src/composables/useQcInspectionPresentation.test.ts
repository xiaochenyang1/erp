import { describe, expect, it } from 'vitest'

import type { QcInspection } from '@/api/qc'
import { useQcInspectionPresentation } from './useQcInspectionPresentation'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.id != null ? `${key}:${params.id}` : key

describe('qc inspection presentation', () => {
  it('maps status labels and tag types, unknown status falls back to raw value', () => {
    const presentation = useQcInspectionPresentation(t)

    expect(presentation.statusText('DRAFT')).toBe('qcInspection.status.draft')
    expect(presentation.statusText('SUBMITTED')).toBe('qcInspection.status.submitted')
    expect(presentation.statusText('JUDGED')).toBe('qcInspection.status.judged')
    expect(presentation.statusText('CANCELLED')).toBe('qcInspection.status.cancelled')
    expect(presentation.statusText('WEIRD')).toBe('WEIRD')
    expect(presentation.statusText(undefined)).toBe('')

    expect(presentation.statusType('DRAFT')).toBe('info')
    expect(presentation.statusType('SUBMITTED')).toBe('warning')
    expect(presentation.statusType('JUDGED')).toBe('success')
    expect(presentation.statusType('CANCELLED')).toBe('danger')
    expect(presentation.statusType('WEIRD')).toBe('info')
  })

  it('labels the three inspection types and falls back for unknown ones', () => {
    const presentation = useQcInspectionPresentation(t)

    expect(presentation.inspectionTypeText('IQC')).toBe('qcInspection.type.iqc')
    expect(presentation.inspectionTypeText('OQC')).toBe('qcInspection.type.oqc')
    expect(presentation.inspectionTypeText('IPQC')).toBe('qcInspection.type.ipqc')
    expect(presentation.inspectionTypeText('XQC')).toBe('XQC')
    expect(presentation.inspectionTypeText(undefined)).toBe('-')
  })

  it('names the source document field per inspection type', () => {
    const presentation = useQcInspectionPresentation(t)

    expect(presentation.sourceDocumentLabel('IQC')).toBe('qcInspection.purchaseReceipt')
    expect(presentation.sourceDocumentLabel('OQC')).toBe('qcInspection.salesDelivery')
    expect(presentation.sourceDocumentLabel('IPQC')).toBe('qcInspection.productionOrderId')
    expect(presentation.sourceDocumentLabel(undefined)).toBe('qcInspection.purchaseReceipt')
  })

  it('reads the source id from the field matching the type', () => {
    const presentation = useQcInspectionPresentation(t)

    expect(presentation.sourceDocumentId({
      inspectionType: 'IQC',
      receiptId: 'r1',
      deliveryId: 'd1'
    } as QcInspection)).toBe('r1')

    expect(presentation.sourceDocumentId({
      inspectionType: 'OQC',
      receiptId: 'r1',
      deliveryId: 'd1'
    } as QcInspection)).toBe('d1')

    expect(presentation.sourceDocumentId({
      inspectionType: 'IPQC',
      productionOrderId: 'po1'
    } as QcInspection)).toBe('po1')

    // legacy payloads carry the production order in orderId
    expect(presentation.sourceDocumentId({
      inspectionType: 'IPQC',
      orderId: 'legacy1'
    } as QcInspection)).toBe('legacy1')

    expect(presentation.sourceDocumentId(undefined)).toBe('')
  })

  it('renders the source document text with a dash when the id is missing', () => {
    const presentation = useQcInspectionPresentation(t)

    expect(presentation.sourceDocumentText({ inspectionType: 'IQC', receiptId: 'r9' } as QcInspection))
      .toBe('qcInspection.sourceInbound:r9')
    expect(presentation.sourceDocumentText({ inspectionType: 'OQC', deliveryId: 'd9' } as QcInspection))
      .toBe('qcInspection.sourceOutbound:d9')
    expect(presentation.sourceDocumentText({ inspectionType: 'IPQC', productionOrderId: 'p9' } as QcInspection))
      .toBe('qcInspection.sourceProduction:p9')
    expect(presentation.sourceDocumentText({ inspectionType: 'IQC' } as QcInspection))
      .toBe('qcInspection.sourceInbound:-')
  })
})
