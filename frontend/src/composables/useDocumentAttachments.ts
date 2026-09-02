import { computed, ref } from 'vue'

import {
  deleteAttachment,
  downloadAttachment,
  getAttachments,
  uploadAttachment,
  type Attachment
} from '@/api/attachment'

/** 后端 `erp.attachment.max-file-size-bytes` 的默认值，前端先拦一层避免大文件白跑一趟。 */
export const DEFAULT_MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024

export interface DocumentAttachmentTarget {
  businessType: string
  businessId: string | number
  businessNo?: string
}

export interface DocumentAttachmentDependencies {
  deleteAttachment: typeof deleteAttachment
  downloadAttachment: typeof downloadAttachment
  getAttachments: typeof getAttachments
  uploadAttachment: typeof uploadAttachment
}

export interface DocumentAttachmentCallbacks {
  confirm: (message: string, title: string, options?: Record<string, unknown>) => Promise<unknown>
  downloadBlob: (blob: Blob, filename: string) => void
  onError: (messageKey: string, params?: Record<string, string | number>) => void
  onSuccess: (messageKey: string) => void
  t: (key: string, params?: Record<string, string | number>) => string
  target: () => DocumentAttachmentTarget | null
  maxFileSizeBytes?: () => number
}

const defaultDependencies: DocumentAttachmentDependencies = {
  deleteAttachment,
  downloadAttachment,
  getAttachments,
  uploadAttachment
}

/**
 * 单据页内附件留痕：按 businessType + businessId 复用附件中心接口，供 15 类闸门单据的详情页直接上传。
 * 列表带请求序号守卫，快速切换单据时旧响应不会覆盖新单据的附件；上传/删除后只刷新当前单据。
 */
export const useDocumentAttachments = (
  callbacks: DocumentAttachmentCallbacks,
  dependencies: DocumentAttachmentDependencies = defaultDependencies
) => {
  const rows = ref<Attachment[]>([])
  const loading = ref(false)
  const uploading = ref(false)
  const removingId = ref<string | null>(null)
  let loadToken = 0

  const count = computed(() => rows.value.length)
  const empty = computed(() => !loading.value && rows.value.length === 0)

  const load = async () => {
    const target = callbacks.target()
    if (!target?.businessType || target.businessId == null || target.businessId === '') {
      loadToken += 1
      rows.value = []
      loading.value = false
      return false
    }
    const token = ++loadToken
    loading.value = true
    try {
      const page = await dependencies.getAttachments({
        businessType: target.businessType,
        businessId: target.businessId,
        pageNo: 1,
        pageSize: 100
      })
      if (token !== loadToken) {
        return false
      }
      rows.value = page.records || []
      return true
    } catch {
      if (token === loadToken) {
        rows.value = []
        callbacks.onError('documentAttachment.message.loadFailed')
      }
      return false
    } finally {
      if (token === loadToken) {
        loading.value = false
      }
    }
  }

  const clear = () => {
    loadToken += 1
    rows.value = []
    loading.value = false
    uploading.value = false
    removingId.value = null
  }

  const upload = async (file: File) => {
    const target = callbacks.target()
    if (!target?.businessType || target.businessId == null || target.businessId === '') {
      return false
    }
    const limit = callbacks.maxFileSizeBytes?.() ?? DEFAULT_MAX_ATTACHMENT_BYTES
    if (file.size > limit) {
      callbacks.onError('documentAttachment.message.tooLarge', { limit: Math.floor(limit / 1024 / 1024) })
      return false
    }
    uploading.value = true
    try {
      await dependencies.uploadAttachment(file, target.businessType, target.businessId, target.businessNo)
      callbacks.onSuccess('documentAttachment.message.uploaded')
      await load()
      return true
    } catch {
      callbacks.onError('documentAttachment.message.uploadFailed')
      return false
    } finally {
      uploading.value = false
    }
  }

  const download = async (row: Attachment) => {
    try {
      const blob = await dependencies.downloadAttachment(row.id)
      callbacks.downloadBlob(blob as Blob, row.originalFilename || `attachment-${row.id}`)
      return true
    } catch {
      callbacks.onError('documentAttachment.message.downloadFailed')
      return false
    }
  }

  const remove = async (row: Attachment) => {
    try {
      await callbacks.confirm(
        callbacks.t('documentAttachment.message.confirmDelete', { filename: row.originalFilename || row.id }),
        callbacks.t('documentAttachment.message.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    removingId.value = row.id
    try {
      await dependencies.deleteAttachment(row.id)
      callbacks.onSuccess('documentAttachment.message.deleted')
      await load()
      return true
    } catch {
      callbacks.onError('documentAttachment.message.deleteFailed')
      return false
    } finally {
      removingId.value = null
    }
  }

  return { clear, count, download, empty, load, loading, remove, removingId, rows, upload, uploading }
}
