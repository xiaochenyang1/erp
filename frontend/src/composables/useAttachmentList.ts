import { reactive, ref } from 'vue'

import type { Attachment, AttachmentQuery } from '@/api/attachment'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title: string,
  options?: {
    confirmButtonText?: string
    cancelButtonText?: string
    type?: string
  }
) => Promise<unknown>

/** Attachment query, pagination, download and delete workflows. */
export const useAttachmentList = (
  t: Translate,
  options: {
    getAttachments: (params: AttachmentQuery) => Promise<PageResponse<Attachment>>
    downloadAttachment: (id: string | number) => Promise<Blob>
    deleteAttachment: (id: string | number) => Promise<unknown>
    downloadBlob: (blob: Blob, filename: string) => void
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
    reportLoadError?: (error: unknown) => void
  }
) => {
  const queryForm = reactive<AttachmentQuery>({
    businessType: '',
    businessId: '',
    businessNo: ''
  })
  const pagination = reactive({
    page: 1,
    size: 20,
    total: 0
  })
  const loading = ref(false)
  const tableData = ref<Attachment[]>([])

  const buildQueryParams = (): AttachmentQuery => ({
    businessType: queryForm.businessType?.trim() || undefined,
    businessId: queryForm.businessId != null
      ? String(queryForm.businessId).trim() || undefined
      : undefined,
    businessNo: queryForm.businessNo?.trim() || undefined,
    pageNo: pagination.page,
    pageSize: pagination.size
  })

  const loadData = async () => {
    loading.value = true
    try {
      const res = await options.getAttachments(buildQueryParams())
      tableData.value = res.records || []
      pagination.total = res.total || 0
      return true
    } catch (error) {
      options.reportLoadError?.(error)
      options.onError?.(t('systemAttachments.message.loadFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  const handleQuery = async () => {
    pagination.page = 1
    return loadData()
  }

  const handlePageChange = async () => loadData()

  const handleReset = async () => {
    queryForm.businessType = ''
    queryForm.businessId = ''
    queryForm.businessNo = ''
    pagination.page = 1
    return loadData()
  }

  const handleDownload = async (row: Attachment) => {
    try {
      const blob = await options.downloadAttachment(row.id)
      options.downloadBlob(blob, row.originalFilename || `attachment-${row.id}`)
      options.onSuccess?.(t('systemAttachments.message.downloaded'))
      return true
    } catch {
      options.onError?.(t('systemAttachments.message.downloadFailed'))
      return false
    }
  }

  const handleDelete = async (row: Attachment) => {
    try {
      await options.confirm(
        t('systemAttachments.message.deleteConfirm', { filename: row.originalFilename }),
        t('systemAttachments.message.prompt'),
        {
          confirmButtonText: t('systemAttachments.message.confirm'),
          cancelButtonText: t('systemAttachments.message.cancelled'),
          type: 'warning'
        }
      )
      await options.deleteAttachment(row.id)
      options.onSuccess?.(t('systemAttachments.message.deleted'))
      await loadData()
      return true
    } catch (error) {
      if (error !== 'cancel') {
        options.onError?.(t('systemAttachments.message.deleteFailed'))
      }
      return false
    }
  }

  return {
    buildQueryParams,
    handleDelete,
    handleDownload,
    handlePageChange,
    handleQuery,
    handleReset,
    loadData,
    loading,
    pagination,
    queryForm,
    tableData
  }
}
