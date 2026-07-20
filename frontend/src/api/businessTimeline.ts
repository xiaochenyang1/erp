import { request } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

export interface BusinessTimelineEvent {
  id: string
  businessType: string
  businessId: string
  businessNo?: string
  eventType: 'COMMENT' | 'ATTACHMENT_UPLOADED' | 'ATTACHMENT_DELETED' | string
  content: string
  attachmentId?: string
  operatorUserId?: string
  createdTime: string
}

export interface BusinessTimelineQuery extends PageQuery {
  businessType: string
  businessId?: string | number
  businessNo?: string
}

export interface BusinessTimelineCommentRequest {
  businessType: string
  businessId: string | number
  businessNo?: string
  content: string
}

export const getBusinessTimeline = (params: BusinessTimelineQuery) => {
  return request.get<PageResponse<BusinessTimelineEvent>>('/business-timeline', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeBusinessTimelineEvent)
  }))
}

export const createBusinessTimelineComment = (data: BusinessTimelineCommentRequest) => {
  return request.post<BusinessTimelineEvent>('/business-timeline/comments', data)
    .then(normalizeBusinessTimelineEvent)
}

const normalizeBusinessTimelineEvent = (event: BusinessTimelineEvent): BusinessTimelineEvent => ({
  ...event,
  id: String(event.id),
  businessId: String(event.businessId),
  attachmentId: event.attachmentId != null ? String(event.attachmentId) : undefined,
  operatorUserId: event.operatorUserId != null ? String(event.operatorUserId) : undefined
})
