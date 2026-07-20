import { request } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

export type ExceptionTicketStatus = 'OPEN' | 'PROCESSING' | 'RESOLVED' | 'CLOSED'
export type ExceptionTicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

export interface ExceptionTicketQuery extends PageQuery {
  keyword?: string
  status?: string
  priority?: string
  category?: string
  assigneeUserId?: string | number
  sourceNo?: string
  overdueOnly?: boolean
}

export interface ExceptionTicketCreateRequest {
  category?: string
  priority?: string
  title: string
  description?: string
  sourceType?: string
  sourceId?: string
  sourceNo?: string
  sourceRoute?: string
  assigneeUserId?: string | number
  dueTime?: string
}

export interface ExceptionTicketAssignRequest {
  assigneeUserId?: string | number
  comment?: string
}

export interface ExceptionTicketActionRequest {
  comment?: string
}

export interface ExceptionTicketEvent {
  id?: string
  ticketId?: string
  action: string
  fromStatus?: string
  toStatus?: string
  comment?: string
  operatorUserId?: string
  createdTime?: string
}

export interface ExceptionTicket {
  id: string
  ticketNo: string
  category: string
  priority: ExceptionTicketPriority | string
  title: string
  description?: string
  sourceType?: string
  sourceId?: string
  sourceNo?: string
  sourceRoute?: string
  traceable?: boolean
  traceKeyword?: string
  traceRoute?: string
  status: ExceptionTicketStatus | string
  assigneeUserId?: string
  dueTime?: string
  resolvedBy?: string
  resolvedTime?: string
  resolution?: string
  createdBy?: string
  createdTime?: string
  updatedTime?: string
  events: ExceptionTicketEvent[]
}

export const getExceptionTickets = (params: ExceptionTicketQuery) => {
  return request.get<PageResponse<ExceptionTicket>>('/exception-tickets', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeExceptionTicket)
  }))
}

export const createExceptionTicket = (data: ExceptionTicketCreateRequest) => {
  return request.post<ExceptionTicket>('/exception-tickets', data).then(normalizeExceptionTicket)
}

export const assignExceptionTicket = (id: string | number, data: ExceptionTicketAssignRequest) => {
  return request.post<ExceptionTicket>(`/exception-tickets/${id}/assign`, data).then(normalizeExceptionTicket)
}

export const startExceptionTicket = (id: string | number, data: ExceptionTicketActionRequest) => {
  return request.post<ExceptionTicket>(`/exception-tickets/${id}/start`, data).then(normalizeExceptionTicket)
}

export const resolveExceptionTicket = (id: string | number, data: ExceptionTicketActionRequest) => {
  return request.post<ExceptionTicket>(`/exception-tickets/${id}/resolve`, data).then(normalizeExceptionTicket)
}

export const closeExceptionTicket = (id: string | number, data: ExceptionTicketActionRequest) => {
  return request.post<ExceptionTicket>(`/exception-tickets/${id}/close`, data).then(normalizeExceptionTicket)
}

const normalizeExceptionTicket = (ticket: ExceptionTicket): ExceptionTicket => ({
  ...ticket,
  id: String(ticket.id),
  sourceId: ticket.sourceId != null ? String(ticket.sourceId) : undefined,
  traceable: Boolean(ticket.traceable),
  assigneeUserId: ticket.assigneeUserId != null ? String(ticket.assigneeUserId) : undefined,
  resolvedBy: ticket.resolvedBy != null ? String(ticket.resolvedBy) : undefined,
  createdBy: ticket.createdBy != null ? String(ticket.createdBy) : undefined,
  events: (ticket.events || []).map((event) => ({
    ...event,
    id: event.id != null ? String(event.id) : undefined,
    ticketId: event.ticketId != null ? String(event.ticketId) : undefined,
    operatorUserId: event.operatorUserId != null ? String(event.operatorUserId) : undefined
  }))
})
