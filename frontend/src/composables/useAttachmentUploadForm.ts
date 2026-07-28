import { ref, shallowReactive, shallowRef } from 'vue'

import type { AttachmentQuery } from '@/api/attachment'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export interface AttachmentUploadFormData {
  businessType: string
  businessId: string
  businessNo: string
  file: File | null
}

export interface AttachmentUploadScope {
  businessType: string
  businessId: string
  businessNo: string
}

/** Upload dialog state, file selection and validated submission workflow. */
export const useAttachmentUploadForm = (
  t: Translate,
  options: {
    uploadAttachment: (
      file: File,
      businessType: string,
      businessId: string | number,
      businessNo?: string
    ) => Promise<unknown>
    validate: () => Promise<boolean>
    validateFile?: () => void
    resetFields?: () => void
    clearFiles?: () => void
    onUploaded?: (scope: AttachmentUploadScope) => void
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const uploadDialogVisible = ref(false)
  const uploading = ref(false)
  const selectedFile = shallowRef<File | null>(null)
  const uploadForm = shallowReactive<AttachmentUploadFormData>({
    businessType: '',
    businessId: '',
    businessNo: '',
    file: null
  })

  const handleOpenUpload = (source: AttachmentQuery) => {
    uploadForm.businessType = source.businessType || 'SALES_ORDER'
    uploadForm.businessId = source.businessId != null ? String(source.businessId) : ''
    uploadForm.businessNo = source.businessNo || ''
    selectedFile.value = null
    uploadForm.file = null
    uploadDialogVisible.value = true
  }

  const handleFileChange = (file?: File | null) => {
    selectedFile.value = file || null
    uploadForm.file = selectedFile.value
    options.validateFile?.()
  }

  const handleFileRemove = () => {
    selectedFile.value = null
    uploadForm.file = null
    options.validateFile?.()
  }

  const handleSubmitUpload = async () => {
    let valid = false
    try {
      valid = await options.validate()
    } catch {
      return false
    }

    const businessId = uploadForm.businessId.trim()
    if (!valid || !selectedFile.value || !businessId) return false

    uploading.value = true
    try {
      await options.uploadAttachment(
        selectedFile.value,
        uploadForm.businessType,
        businessId,
        uploadForm.businessNo || undefined
      )
      options.onSuccess?.(t('systemAttachments.message.uploaded'))
      uploadDialogVisible.value = false
      options.onUploaded?.({
        businessType: uploadForm.businessType,
        businessId,
        businessNo: uploadForm.businessNo
      })
      return true
    } catch {
      options.onError?.(t('systemAttachments.message.uploadFailed'))
      return false
    } finally {
      uploading.value = false
    }
  }

  const handleUploadDialogClose = () => {
    options.resetFields?.()
    options.clearFiles?.()
    selectedFile.value = null
    uploadForm.file = null
  }

  return {
    handleFileChange,
    handleFileRemove,
    handleOpenUpload,
    handleSubmitUpload,
    handleUploadDialogClose,
    selectedFile,
    uploadDialogVisible,
    uploadForm,
    uploading
  }
}
