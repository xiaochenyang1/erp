import { describe, expect, it, vi } from 'vitest'

import type { Attachment } from '@/api/attachment'
import { DEFAULT_MAX_ATTACHMENT_BYTES, useDocumentAttachments } from './useDocumentAttachments'

const attachment = (overrides: Partial<Attachment> = {}): Attachment => ({
  id: '5',
  businessType: 'EXPENSE',
  businessId: '77',
  businessNo: 'EXP-0001',
  originalFilename: 'invoice.pdf',
  fileSize: 2048,
  contentType: 'application/pdf',
  checksumSha256: 'abc',
  createdTime: '2026-09-01 09:00:00',
  createdBy: '9501',
  ...overrides
})

const page = (records: Attachment[] = [attachment()]) => ({
  records,
  total: records.length,
  current: 1,
  size: 100
})

const file = (size = 1024, name = 'invoice.pdf') => ({ size, name } as File)

const setup = (overrides: Record<string, unknown> = {}) => {
  const getAttachments = vi.fn(async () => page())
  const uploadAttachment = vi.fn(async () => attachment())
  const downloadAttachment = vi.fn(async () => new Blob(['x']))
  const deleteAttachment = vi.fn(async () => ({}))
  const confirm = vi.fn(async () => true)
  const downloadBlob = vi.fn()
  const onError = vi.fn()
  const onSuccess = vi.fn()
  const t = vi.fn((key: string, params?: Record<string, string | number>) => (
    params ? `${key}:${Object.values(params).join(',')}` : key
  ))
  const target = vi.fn(() => ({ businessType: 'EXPENSE', businessId: '77', businessNo: 'EXP-0001' }))
  const dependencies = { deleteAttachment, downloadAttachment, getAttachments, uploadAttachment, ...overrides } as never
  const callbacks = { confirm, downloadBlob, onError, onSuccess, t, target, ...overrides } as never
  return {
    attachments: useDocumentAttachments(callbacks, dependencies),
    confirm,
    deleteAttachment,
    downloadAttachment,
    downloadBlob,
    getAttachments,
    onError,
    onSuccess,
    uploadAttachment
  }
}

describe('document attachments', () => {
  it('loads attachments scoped to the business type and id', async () => {
    const { attachments, getAttachments } = setup()

    const loaded = await attachments.load()

    expect(loaded).toBe(true)
    expect(getAttachments).toHaveBeenCalledWith({ businessType: 'EXPENSE', businessId: '77', pageNo: 1, pageSize: 100 })
    expect(attachments.rows.value).toHaveLength(1)
    expect(attachments.count.value).toBe(1)
    expect(attachments.loading.value).toBe(false)
  })

  it('clears rows without a request when the document has no id yet', async () => {
    const { attachments, getAttachments } = setup({ target: () => ({ businessType: 'EXPENSE', businessId: '' }) })

    const loaded = await attachments.load()

    expect(loaded).toBe(false)
    expect(getAttachments).not.toHaveBeenCalled()
    expect(attachments.rows.value).toEqual([])
  })

  it('reports a load failure and clears rows', async () => {
    const { attachments, onError } = setup({
      getAttachments: vi.fn(async () => {
        throw new Error('boom')
      })
    })

    await attachments.load()

    expect(attachments.rows.value).toEqual([])
    expect(onError).toHaveBeenCalledWith('documentAttachment.message.loadFailed')
  })

  it('ignores a stale list response after switching documents', async () => {
    let resolveFirst: ((value: unknown) => void) | undefined
    const getAttachments = vi
      .fn()
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveFirst = resolve
      }))
      .mockImplementationOnce(async () => page([attachment({ id: '9', originalFilename: 'second.pdf' })]))
    const { attachments } = setup({ getAttachments })

    const first = attachments.load()
    const second = attachments.load()
    resolveFirst?.(page([attachment({ id: '1', originalFilename: 'stale.pdf' })]))
    await Promise.all([first, second])

    expect(attachments.rows.value.map((row) => row.originalFilename)).toEqual(['second.pdf'])
    expect(attachments.loading.value).toBe(false)
  })

  it('uploads with the document business key and reloads', async () => {
    const { attachments, getAttachments, onSuccess, uploadAttachment } = setup()

    const uploaded = await attachments.upload(file())

    expect(uploaded).toBe(true)
    expect(uploadAttachment).toHaveBeenCalledWith(expect.anything(), 'EXPENSE', '77', 'EXP-0001')
    expect(onSuccess).toHaveBeenCalledWith('documentAttachment.message.uploaded')
    expect(getAttachments).toHaveBeenCalledTimes(1)
    expect(attachments.uploading.value).toBe(false)
  })

  it('rejects a file above the size limit before calling the server', async () => {
    const { attachments, onError, uploadAttachment } = setup()

    const uploaded = await attachments.upload(file(DEFAULT_MAX_ATTACHMENT_BYTES + 1))

    expect(uploaded).toBe(false)
    expect(uploadAttachment).not.toHaveBeenCalled()
    expect(onError).toHaveBeenCalledWith('documentAttachment.message.tooLarge', { limit: 20 })
  })

  it('honours an overridden size limit', async () => {
    const { attachments, onError } = setup({ maxFileSizeBytes: () => 1024 * 1024 })

    await attachments.upload(file(2 * 1024 * 1024))

    expect(onError).toHaveBeenCalledWith('documentAttachment.message.tooLarge', { limit: 1 })
  })

  it('surfaces an upload failure', async () => {
    const { attachments, onError } = setup({
      uploadAttachment: vi.fn(async () => {
        throw new Error('boom')
      })
    })

    const uploaded = await attachments.upload(file())

    expect(uploaded).toBe(false)
    expect(onError).toHaveBeenCalledWith('documentAttachment.message.uploadFailed')
    expect(attachments.uploading.value).toBe(false)
  })

  it('downloads using the original filename', async () => {
    const { attachments, downloadAttachment, downloadBlob } = setup()

    const done = await attachments.download(attachment())

    expect(done).toBe(true)
    expect(downloadAttachment).toHaveBeenCalledWith('5')
    expect(downloadBlob).toHaveBeenCalledWith(expect.any(Blob), 'invoice.pdf')
  })

  it('reports a download failure', async () => {
    const { attachments, onError } = setup({
      downloadAttachment: vi.fn(async () => {
        throw new Error('boom')
      })
    })

    const done = await attachments.download(attachment())

    expect(done).toBe(false)
    expect(onError).toHaveBeenCalledWith('documentAttachment.message.downloadFailed')
  })

  it('confirms with the filename before deleting and reloads', async () => {
    const { attachments, confirm, deleteAttachment, getAttachments, onSuccess } = setup()

    const removed = await attachments.remove(attachment())

    expect(removed).toBe(true)
    expect(confirm).toHaveBeenCalledWith(
      'documentAttachment.message.confirmDelete:invoice.pdf',
      'documentAttachment.message.prompt',
      expect.objectContaining({ type: 'warning' })
    )
    expect(deleteAttachment).toHaveBeenCalledWith('5')
    expect(onSuccess).toHaveBeenCalledWith('documentAttachment.message.deleted')
    expect(getAttachments).toHaveBeenCalledTimes(1)
    expect(attachments.removingId.value).toBeNull()
  })

  it('keeps the attachment when the confirm dialog is cancelled', async () => {
    const { attachments, deleteAttachment } = setup({
      confirm: vi.fn(async () => {
        throw new Error('cancel')
      })
    })

    const removed = await attachments.remove(attachment())

    expect(removed).toBe(false)
    expect(deleteAttachment).not.toHaveBeenCalled()
  })

  it('surfaces a delete failure and releases the row spinner', async () => {
    const { attachments, onError } = setup({
      deleteAttachment: vi.fn(async () => {
        throw new Error('boom')
      })
    })

    const removed = await attachments.remove(attachment())

    expect(removed).toBe(false)
    expect(onError).toHaveBeenCalledWith('documentAttachment.message.deleteFailed')
    expect(attachments.removingId.value).toBeNull()
  })

  it('drops rows and in-flight responses on clear', async () => {
    let resolveLoad: ((value: unknown) => void) | undefined
    const getAttachments = vi.fn(() => new Promise((resolve) => {
      resolveLoad = resolve
    }))
    const { attachments } = setup({ getAttachments })

    const pending = attachments.load()
    attachments.clear()
    resolveLoad?.(page())
    await pending

    expect(attachments.rows.value).toEqual([])
    expect(attachments.empty.value).toBe(true)
  })
})
