import { request } from '@/utils/request'

export interface BusinessTraceQuery {
  keyword?: string
}

export interface BusinessTraceDocument {
  id: string
  documentType: string
  documentLabel: string
  documentId: string
  documentNo?: string
  bizNo: string
  title?: string
  status?: string
  secondaryStatus?: string
  bizDate?: string
  partnerType?: string
  partnerId?: string
  totalQuantity?: number
  totalAmount?: number
  route?: string
}

export interface BusinessTraceTimeline {
  id: string
  eventType: string
  title: string
  bizNo?: string
  description?: string
  occurredAt?: string
  status?: string
  severity?: 'NORMAL' | 'WARNING' | 'ERROR' | string
  route?: string
}

export interface BusinessTraceExceptionTicket {
  id: string
  ticketNo: string
  category?: string
  priority?: string
  title: string
  sourceType?: string
  sourceId?: string
  sourceNo?: string
  status?: string
  assigneeUserId?: string
  dueTime?: string
  updatedTime?: string
  route?: string
}

export interface BusinessTraceSummary {
  documentCount: number
  timelineCount: number
  openReceivableAmount: number
  openPayableAmount: number
  inventoryMovementQuantity: number
  failedOperationCount: number
  openExceptionTicketCount: number
}

export interface BusinessTraceResponse {
  keyword: string
  documents: BusinessTraceDocument[]
  timeline: BusinessTraceTimeline[]
  exceptionTickets: BusinessTraceExceptionTicket[]
  summary: BusinessTraceSummary
  generatedAt: string
}

export const getBusinessTrace = (params: BusinessTraceQuery) => {
  return request.get<BusinessTraceResponse>('/reports/business-traces', { params }).then(normalizeBusinessTrace)
}

const normalizeBusinessTraceDocument = (document: BusinessTraceDocument): BusinessTraceDocument => ({
  ...document,
  documentId: String(document.documentId),
  partnerId: document.partnerId != null ? String(document.partnerId) : undefined,
  totalQuantity: Number(document.totalQuantity ?? 0),
  totalAmount: Number(document.totalAmount ?? 0)
})

const normalizeBusinessTraceExceptionTicket = (
  ticket: BusinessTraceExceptionTicket
): BusinessTraceExceptionTicket => ({
  ...ticket,
  id: String(ticket.id),
  sourceId: ticket.sourceId != null ? String(ticket.sourceId) : undefined,
  assigneeUserId: ticket.assigneeUserId != null ? String(ticket.assigneeUserId) : undefined
})

const normalizeBusinessTrace = (trace: BusinessTraceResponse): BusinessTraceResponse => ({
  ...trace,
  documents: (trace.documents || []).map(normalizeBusinessTraceDocument),
  timeline: trace.timeline || [],
  exceptionTickets: (trace.exceptionTickets || []).map(normalizeBusinessTraceExceptionTicket),
  summary: {
    ...trace.summary,
    openReceivableAmount: Number(trace.summary.openReceivableAmount ?? 0),
    openPayableAmount: Number(trace.summary.openPayableAmount ?? 0),
    inventoryMovementQuantity: Number(trace.summary.inventoryMovementQuantity ?? 0),
    failedOperationCount: Number(trace.summary.failedOperationCount ?? 0),
    openExceptionTicketCount: Number(trace.summary.openExceptionTicketCount ?? 0)
  }
})
