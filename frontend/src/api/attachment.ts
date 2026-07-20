import { request } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

// ==================== 附件管理 ====================

export interface Attachment {
  id: string
  businessType: string
  businessId: string
  businessNo?: string
  originalFilename: string
  fileSize: number
  contentType: string
  checksumSha256: string
  createdTime: string
  createdBy: string
}

export interface AttachmentQuery extends PageQuery {
  businessType?: string
  businessId?: string | number
  businessNo?: string
}

// 附件API
export const getAttachments = (params: AttachmentQuery) => {
  return request.get<PageResponse<Attachment>>('/system/attachments', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeAttachment)
  }))
}

export const uploadAttachment = (file: File, businessType: string, businessId: string | number, businessNo?: string) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('businessType', businessType)
  formData.append('businessId', businessId.toString())
  if (businessNo) {
    formData.append('businessNo', businessNo)
  }

  return request.post<Attachment>('/system/attachments', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  }).then(normalizeAttachment)
}

export const downloadAttachment = (id: string | number) => {
  return request.get(`/system/attachments/${id}/download`, {
    responseType: 'blob'
  })
}

export const deleteAttachment = (id: string | number) => {
  return request.delete(`/system/attachments/${id}`)
}

const normalizeAttachment = (attachment: Attachment): Attachment => ({
  ...attachment,
  id: String(attachment.id),
  businessId: String(attachment.businessId),
  createdBy: attachment.createdBy != null ? String(attachment.createdBy) : ''
})
