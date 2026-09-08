import { describe, expect, it, vi } from 'vitest'

import type { Attachment } from '@/api/attachment'
import { DOCUMENT_ATTACHMENT_PAGE_SIZE, useDocumentAttachments } from './useDocumentAttachments'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

const attachment = (overrides: Partial<Attachment> = {}): Attachment => ({
  id: 'a1',
  businessType: 'SALES_ORDER',
  businessId: '9007199254740993',
  originalFilename: 'order.pdf',
  fileSize: 2048,
  contentType: 'application/pdf',
  checksumSha256: 'sha',
  createdTime: '2026-09-01T10:00:00',
  createdBy: 'u1',
  ...overrides
})

const page = (records: Attachment[] = [], total = records.length) => ({
  records,
  total,
  pageNo: 1,
  pageSize: DOCUMENT_ATTACHMENT_PAGE_SIZE
})

const createPanel = (overrides: Partial<Parameters<typeof useDocumentAttachments>[1]> = {}) =>
  useDocumentAttachments(t, {
    getAttachments: vi.fn(async () => page([attachment()])),
    uploadAttachment: vi.fn(async () => attachment()),
    downloadAttachment: vi.fn(async () => new Blob(['file'])),
    deleteAttachment: vi.fn(async () => ({})),
    downloadBlob: vi.fn(),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

const scope = { businessType: 'SALES_ORDER', businessId: '9007199254740993', businessNo: 'SO-001' }

describe('document attachments', () => {
  it('loads one document scope without pagination filters from other documents', async () => {
    const getAttachments = vi.fn(async () => page([attachment()], 1))
    const panel = createPanel({ getAttachments })

    expect(await panel.open(scope)).toBe(true)
    expect(getAttachments).toHaveBeenCalledWith({
      businessType: 'SALES_ORDER',
      businessId: '9007199254740993',
      pageNo: 1,
      pageSize: DOCUMENT_ATTACHMENT_PAGE_SIZE
    })
    expect(panel.attachments.value).toEqual([attachment()])
    expect(panel.truncated.value).toBe(false)
    expect(panel.loading.value).toBe(false)
  })

  it('stringifies the document id so snowflake ids reach the backend intact', async () => {
    const getAttachments = vi.fn(async () => page())
    const panel = createPanel({ getAttachments })

    await panel.open({ businessType: 'EXPENSE', businessId: 4096 as unknown as string })
    expect(getAttachments).toHaveBeenCalledWith(expect.objectContaining({ businessId: '4096' }))

    await panel.open({ businessType: 'EXPENSE', businessId: '9007199254740993' })
    expect(getAttachments).toHaveBeenLastCalledWith(expect.objectContaining({ businessId: '9007199254740993' }))
    expect(panel.scope.value?.businessId).toBe('9007199254740993')
  })

  it('flags a document whose attachment count exceeds one page', async () => {
    const panel = createPanel({ getAttachments: vi.fn(async () => page([attachment()], 120)) })

    await panel.open(scope)

    expect(panel.truncated.value).toBe(true)
  })

  it('skips the backend and clears the table when no document is selected', async () => {
    const getAttachments = vi.fn(async () => page([attachment()]))
    const panel = createPanel({ getAttachments })

    expect(await panel.open({ businessType: 'SALES_ORDER', businessId: '' })).toBe(false)
    expect(getAttachments).not.toHaveBeenCalled()
    expect(panel.attachments.value).toEqual([])
  })

  it('reports a failed load instead of leaving stale rows on screen', async () => {
    const onError = vi.fn()
    const panel = createPanel({ onError })
    await panel.open(scope)
    expect(panel.attachments.value).toHaveLength(1)

    const failing = createPanel({
      getAttachments: vi.fn(async () => {
        throw new Error('network')
      }),
      onError
    })

    expect(await failing.open(scope)).toBe(false)
    expect(failing.attachments.value).toEqual([])
    expect(failing.total.value).toBe(0)
    expect(onError).toHaveBeenCalledWith('documentAttachment.message.loadFailed')
  })

  it('uploads against the open document scope and reloads the list', async () => {
    const uploadAttachment = vi.fn(async () => attachment())
    const getAttachments = vi.fn(async () => page([attachment()]))
    const onSuccess = vi.fn()
    const panel = createPanel({ uploadAttachment, getAttachments, onSuccess })
    await panel.open(scope)

    const file = new File(['data'], 'order.pdf')
    expect(await panel.upload(file)).toBe(true)
    expect(uploadAttachment).toHaveBeenCalledWith(file, 'SALES_ORDER', '9007199254740993', 'SO-001')
    expect(onSuccess).toHaveBeenCalledWith('documentAttachment.message.uploaded')
    expect(getAttachments).toHaveBeenCalledTimes(2)
    expect(panel.uploading.value).toBe(false)
  })

  it('refuses to upload without a file or without an open document', async () => {
    const uploadAttachment = vi.fn(async () => attachment())
    const panel = createPanel({ uploadAttachment })

    expect(await panel.upload(null)).toBe(false)
    await panel.open(scope)
    expect(await panel.upload(undefined)).toBe(false)
    expect(uploadAttachment).not.toHaveBeenCalled()
  })

  it('surfaces an upload rejection and leaves the list untouched', async () => {
    const onError = vi.fn()
    const getAttachments = vi.fn(async () => page([attachment()]))
    const panel = createPanel({
      getAttachments,
      uploadAttachment: vi.fn(async () => {
        throw new Error('too large')
      }),
      onError
    })
    await panel.open(scope)

    expect(await panel.upload(new File(['data'], 'order.pdf'))).toBe(false)
    expect(onError).toHaveBeenCalledWith('documentAttachment.message.uploadFailed')
    expect(getAttachments).toHaveBeenCalledTimes(1)
    expect(panel.uploading.value).toBe(false)
  })

  it('downloads with the stored filename and falls back when it is missing', async () => {
    const downloadBlob = vi.fn()
    const blob = new Blob(['file'])
    const panel = createPanel({ downloadAttachment: vi.fn(async () => blob), downloadBlob })

    expect(await panel.download(attachment())).toBe(true)
    expect(downloadBlob).toHaveBeenCalledWith(blob, 'order.pdf')

    await panel.download(attachment({ id: 'a2', originalFilename: '' }))
    expect(downloadBlob).toHaveBeenLastCalledWith(blob, 'attachment-a2')
  })

  it('reports a failed download', async () => {
    const onError = vi.fn()
    const panel = createPanel({
      downloadAttachment: vi.fn(async () => {
        throw new Error('gone')
      }),
      onError
    })

    expect(await panel.download(attachment())).toBe(false)
    expect(onError).toHaveBeenCalledWith('documentAttachment.message.downloadFailed')
  })

  it('confirms before deleting and reloads the remaining attachments', async () => {
    const confirm = vi.fn(async () => true)
    const deleteAttachment = vi.fn(async () => ({}))
    const getAttachments = vi.fn(async () => page([attachment()]))
    const onSuccess = vi.fn()
    const panel = createPanel({ confirm, deleteAttachment, getAttachments, onSuccess })
    await panel.open(scope)

    expect(await panel.remove(attachment())).toBe(true)
    expect(confirm).toHaveBeenCalledWith(
      'documentAttachment.message.confirmDelete:{"filename":"order.pdf"}',
      'documentAttachment.message.prompt'
    )
    expect(deleteAttachment).toHaveBeenCalledWith('a1')
    expect(onSuccess).toHaveBeenCalledWith('documentAttachment.message.deleted')
    expect(getAttachments).toHaveBeenCalledTimes(2)
  })

  it('keeps the attachment when the confirmation is dismissed', async () => {
    const deleteAttachment = vi.fn(async () => ({}))
    const panel = createPanel({
      confirm: vi.fn(async () => {
        throw new Error('cancel')
      }),
      deleteAttachment
    })
    await panel.open(scope)

    expect(await panel.remove(attachment())).toBe(false)
    expect(deleteAttachment).not.toHaveBeenCalled()
  })

  it('reports a failed delete', async () => {
    const onError = vi.fn()
    const panel = createPanel({
      deleteAttachment: vi.fn(async () => {
        throw new Error('locked')
      }),
      onError
    })
    await panel.open(scope)

    expect(await panel.remove(attachment())).toBe(false)
    expect(onError).toHaveBeenCalledWith('documentAttachment.message.deleteFailed')
  })

  it('drops the document scope on reset so a reopened dialog cannot leak rows', async () => {
    const panel = createPanel()
    await panel.open(scope)

    panel.reset()

    expect(panel.scope.value).toBeNull()
    expect(panel.attachments.value).toEqual([])
    expect(panel.total.value).toBe(0)
    expect(await panel.upload(new File(['data'], 'order.pdf'))).toBe(false)
  })
})
