import { describe, expect, it, vi } from 'vitest'

import { useAttachmentUploadForm } from './useAttachmentUploadForm'

const t = (key: string) => key

const createForm = (overrides: Partial<Parameters<typeof useAttachmentUploadForm>[1]> = {}) =>
  useAttachmentUploadForm(t, {
    uploadAttachment: vi.fn(async () => ({})),
    validate: vi.fn(async () => true),
    validateFile: vi.fn(),
    resetFields: vi.fn(),
    clearFiles: vi.fn(),
    onUploaded: vi.fn(),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('attachment upload form', () => {
  it('opens with query defaults and keeps file selection in form state', () => {
    const validateFile = vi.fn()
    const form = createForm({ validateFile })

    form.handleOpenUpload({ businessType: '', businessId: '', businessNo: '' })
    expect(form.uploadDialogVisible.value).toBe(true)
    expect(form.uploadForm).toMatchObject({
      businessType: 'SALES_ORDER',
      businessId: '',
      businessNo: '',
      file: null
    })

    form.handleOpenUpload({
      businessType: 'EXPENSE',
      businessId: 42,
      businessNo: 'E-42'
    })
    expect(form.uploadForm).toMatchObject({
      businessType: 'EXPENSE',
      businessId: '42',
      businessNo: 'E-42'
    })

    const file = new File(['receipt'], 'receipt.pdf')
    form.handleFileChange(file)
    expect(form.selectedFile.value).toBe(file)
    expect(form.uploadForm.file).toBe(file)
    form.handleFileRemove()
    expect(form.selectedFile.value).toBeNull()
    expect(form.uploadForm.file).toBeNull()
    expect(validateFile).toHaveBeenCalledTimes(2)
  })

  it('uploads only after validation and returns the normalized scope', async () => {
    const uploadAttachment = vi.fn(async () => ({}))
    const onUploaded = vi.fn()
    const onSuccess = vi.fn()
    const form = createForm({ uploadAttachment, onUploaded, onSuccess })
    const file = new File(['receipt'], 'receipt.pdf')

    form.handleOpenUpload({
      businessType: 'EXPENSE',
      businessId: ' 9007199254740993 ',
      businessNo: ''
    })
    form.handleFileChange(file)

    expect(await form.handleSubmitUpload()).toBe(true)
    expect(uploadAttachment).toHaveBeenCalledWith(
      file,
      'EXPENSE',
      '9007199254740993',
      undefined
    )
    expect(onSuccess).toHaveBeenCalledWith('systemAttachments.message.uploaded')
    expect(onUploaded).toHaveBeenCalledWith({
      businessType: 'EXPENSE',
      businessId: '9007199254740993',
      businessNo: ''
    })
    expect(form.uploadDialogVisible.value).toBe(false)
    expect(form.uploading.value).toBe(false)
  })

  it('does not upload an invalid form, missing file or blank business id', async () => {
    const uploadAttachment = vi.fn(async () => ({}))
    const invalid = createForm({
      uploadAttachment,
      validate: vi.fn(async () => false)
    })
    invalid.handleOpenUpload({ businessId: '1' })
    invalid.handleFileChange(new File(['a'], 'a.txt'))
    expect(await invalid.handleSubmitUpload()).toBe(false)

    const missingFile = createForm({ uploadAttachment })
    missingFile.handleOpenUpload({ businessId: '1' })
    expect(await missingFile.handleSubmitUpload()).toBe(false)

    const blankId = createForm({ uploadAttachment })
    blankId.handleOpenUpload({ businessId: '   ' })
    blankId.handleFileChange(new File(['a'], 'a.txt'))
    expect(await blankId.handleSubmitUpload()).toBe(false)
    expect(uploadAttachment).not.toHaveBeenCalled()
  })

  it('reports upload failures and always releases the loading state', async () => {
    const onError = vi.fn()
    const form = createForm({
      uploadAttachment: vi.fn(async () => { throw new Error('network') }),
      onError
    })
    form.handleOpenUpload({ businessType: 'EXPENSE', businessId: '1' })
    form.handleFileChange(new File(['a'], 'a.txt'))

    expect(await form.handleSubmitUpload()).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemAttachments.message.uploadFailed')
    expect(form.uploadDialogVisible.value).toBe(true)
    expect(form.uploading.value).toBe(false)
  })

  it('clears Element Plus adapters and selected file when the dialog closes', () => {
    const resetFields = vi.fn()
    const clearFiles = vi.fn()
    const form = createForm({ resetFields, clearFiles })
    form.handleFileChange(new File(['a'], 'a.txt'))

    form.handleUploadDialogClose()
    expect(resetFields).toHaveBeenCalledOnce()
    expect(clearFiles).toHaveBeenCalledOnce()
    expect(form.selectedFile.value).toBeNull()
    expect(form.uploadForm.file).toBeNull()
  })
})
