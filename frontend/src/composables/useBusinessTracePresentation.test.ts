import { describe, expect, it } from 'vitest'

import { useBusinessTracePresentation } from './useBusinessTracePresentation'

const t = (key: string) => key
const icons = {
  document: { name: 'Document' },
  clock: { name: 'Clock' },
  money: { name: 'Money' },
  box: { name: 'Box' },
  warning: { name: 'Warning' },
  connection: { name: 'Connection' }
}

describe('business trace presentation', () => {
  it('maps document/timeline/ticket labels and tags', () => {
    const presentation = useBusinessTracePresentation(t, icons as any)
    expect(presentation.documentTagType('SALES_ORDER')).toBe('success')
    expect(presentation.documentTagType('PURCHASE_ORDER')).toBe('primary')
    expect(presentation.timelineItemType('ERROR')).toBe('danger')
    expect(presentation.priorityLabel('URGENT')).toBe('financeReportPages.traces.priorityLabel.urgent')
    expect(presentation.ticketStatusLabel('OPEN')).toBe('financeReportPages.traces.ticketStatus.open')
    expect(presentation.traceStatusLabel('POSTED')).toBe('financeReportPages.traces.status.posted')
    expect(presentation.businessTimelineEventLabel('COMMENT')).toBe('financeReportPages.traces.event.comment')
    expect(presentation.businessTimelineItemType('ATTACHMENT_DELETED')).toBe('warning')
    expect(presentation.eventIcon('FINANCE')).toEqual(icons.money)
  })

  it('builds summary cards from a trace payload', () => {
    const presentation = useBusinessTracePresentation(t, icons as any)
    const items = presentation.buildSummaryItems({
      keyword: 'SO1',
      documents: [],
      timeline: [],
      summary: {
        documentCount: 2,
        timelineCount: 3,
        openReceivableAmount: 10,
        openPayableAmount: 20,
        inventoryMovementQuantity: 1.5,
        failedOperationCount: 1,
        openExceptionTicketCount: 4
      },
      exceptionTickets: [],
      generatedAt: ''
    })
    expect(items).toHaveLength(7)
    expect(items[0].value).toBe(2)
    expect(items[5].value).toBe(1)
  })
})
