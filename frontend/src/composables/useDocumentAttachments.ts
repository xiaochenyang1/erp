import { computed, ref, shallowRef } from 'vue'

import type { Attachment, AttachmentQuery } from '@/api/attachment'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (message: string, title: string) => Promise<unknown>

/** One document's attachment scope: the gated business type plus its identity. */
export interface DocumentAttachmentScope {
  businessType: string
  businessId: string
  businessNo?: string
}

/** A single document panel is never paginated; this caps one runaway document. */
export const DOCUMENT_ATTACHMENT_PAGE_SIZE = 100

/**
 * Attachment list, upload, download and delete for one business document.
 *
 * The backend gates submit/post on `AttachmentBusinessType.GATED`, so operators
 * need this next to the document instead of only in the global attachment
 * console. Every backend call is injected so the panel stays unit-testable.
 */
export const useDocumentAttachments = (
  t: Translate,
  options: {
    getAttachments: (params: AttachmentQuery) => Promise<PageResponse<Attachment>>
    uploadAttachment: (
      file: File,
      businessType: string,
      businessId: string | number,
      businessNo?: string
    ) => Promise<unknown>
    downloadAttachment: (id: string | number) => Promise<Blob>
    deleteAttachment: (id: string | number) => Promise<unknown>
    downloadBlob: (blob: Blob, filename: string) => void
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const attachments = ref<Attachment[]>([])
  const loading = ref(false)
  const uploading = ref(false)
  const total = ref(0)
  const scope = shallowRef<DocumentAttachmentScope | null>(null)

  /** True once the backend reports more attachments than one page can show. */
  const truncated = computed(() => total.value > attachments.value.length)

  const load = async () => {
    const current = scope.value
    if (!current?.businessId) {
      attachments.value = []
      total.value = 0
      return false
    }
    loading.value = true
    try {
      const page = await options.getAttachments({
        businessType: current.businessType,
        businessId: current.businessId,
        pageNo: 1,
        pageSize: DOCUMENT_ATTACHMENT_PAGE_SIZE
      })
      attachments.value = page.records || []
      total.value = page.total || 0
      return true
    } catch {
      attachments.value = []
      total.value = 0
      options.onError?.(t('documentAttachment.message.loadFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  /** Points the panel at a document and loads its attachments. */
  const open = async (next: DocumentAttachmentScope) => {
    scope.value = {
      businessType: next.businessType,
      businessId: next.businessId != null ? String(next.businessId) : '',
      businessNo: next.businessNo
    }
    attachments.value = []
    total.value = 0
    return load()
  }

  const reset = () => {
    scope.value = null
    attachments.value = []
    total.value = 0
  }

  const upload = async (file: File | null | undefined) => {
    const current = scope.value
    if (!file || !current?.businessId) return false
    uploading.value = true
    try {
      await options.uploadAttachment(file, current.businessType, current.businessId, current.businessNo)
      options.onSuccess?.(t('documentAttachment.message.uploaded'))
      await load()
      return true
    } catch {
      options.onError?.(t('documentAttachment.message.uploadFailed'))
      return false
    } finally {
      uploading.value = false
    }
  }

  const download = async (row: Attachment) => {
    try {
      const blob = await options.downloadAttachment(row.id)
      options.downloadBlob(blob, row.originalFilename || `attachment-${row.id}`)
      return true
    } catch {
      options.onError?.(t('documentAttachment.message.downloadFailed'))
      return false
    }
  }

  const remove = async (row: Attachment) => {
    try {
      await options.confirm(
        t('documentAttachment.message.confirmDelete', { filename: row.originalFilename }),
        t('documentAttachment.message.prompt')
      )
    } catch {
      return false
    }
    try {
      await options.deleteAttachment(row.id)
      options.onSuccess?.(t('documentAttachment.message.deleted'))
      await load()
      return true
    } catch {
      options.onError?.(t('documentAttachment.message.deleteFailed'))
      return false
    }
  }

  return {
    attachments,
    download,
    load,
    loading,
    open,
    remove,
    reset,
    scope,
    total,
    truncated,
    upload,
    uploading
  }
}
