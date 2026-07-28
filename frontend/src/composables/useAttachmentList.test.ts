import { describe, expect, it, vi } from 'vitest'

import type { Attachment } from '@/api/attachment'
import { useAttachmentList } from './useAttachmentList'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

const attachment = (overrides: Partial<Attachment> = {}): Attachment => ({
  id: 'a1',
  businessType: 'SALES_ORDER',
  businessId: '9007199254740993',
  originalFilename: 'contract.pdf',
  fileSize: 128,
  contentType: 'application/pdf',
  checksumSha256: 'sha',
  createdTime: '2026-07-28T10:00:00',
  createdBy: 'u1',
  ...overrides
})

const page = (records: Attachment[] = [], total = records.length) => ({
  records,
  total,
  pageNo: 1,
  pageSize: 20
})

const createList = (overrides: Partial<Parameters<typeof useAttachmentList>[1]> = {}) =>
  useAttachmentList(t, {
    getAttachments: vi.fn(async () => page([attachment()])),
    downloadAttachment: vi.fn(async () => new Blob(['attachment'])),
    deleteAttachment: vi.fn(async () => ({})),
    downloadBlob: vi.fn(),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    reportLoadError: vi.fn(),
    ...overrides
  })

describe('attachment list', () => {
  it('normalizes filters, resets query paging and stores the returned page', async () => {
    const getAttachments = vi.fn(async () => page([attachment()], 7))
    const list = createList({ getAttachments })
    list.pagination.page = 4
    list.queryForm.businessType = ' SALES_ORDER '
    list.queryForm.businessId = ' 9007199254740993 '
    list.queryForm.businessNo = ' SO-001 '

    expect(await list.handleQuery()).toBe(true)
    expect(getAttachments).toHaveBeenCalledWith({
      businessType: 'SALES_ORDER',
      businessId: '9007199254740993',
      businessNo: 'SO-001',
      pageNo: 1,
      pageSize: 20
    })
    expect(list.tableData.value).toEqual([attachment()])
    expect(list.pagination.total).toBe(7)
    expect(list.loading.value).toBe(false)
  })

  it('reloads the current page and clears all filters on reset', async () => {
    const getAttachments = vi.fn(async () => page())
    const list = createList({ getAttachments })
    list.pagination.page = 3
    list.pagination.size = 50

    await list.handlePageChange()
    expect(getAttachments).toHaveBeenLastCalledWith(expect.objectContaining({
      pageNo: 3,
      pageSize: 50
    }))

    list.queryForm.businessType = 'EXPENSE'
    list.queryForm.businessId = '42'
    list.queryForm.businessNo = 'E-42'
    await list.handleReset()
    expect(list.queryForm).toMatchObject({
      businessType: '',
      businessId: '',
      businessNo: ''
    })
    expect(list.pagination.page).toBe(1)
  })

  it('reports list failures and restores loading state', async () => {
    const failure = new Error('network')
    const onError = vi.fn()
    const reportLoadError = vi.fn()
    const list = createList({
      getAttachments: vi.fn(async () => { throw failure }),
      onError,
      reportLoadError
    })

    expect(await list.loadData()).toBe(false)
    expect(reportLoadError).toHaveBeenCalledWith(failure)
    expect(onError).toHaveBeenCalledWith('systemAttachments.message.loadFailed')
    expect(list.loading.value).toBe(false)
  })

  it('downloads with the original filename and the id fallback', async () => {
    const blob = new Blob(['attachment'])
    const downloadAttachment = vi.fn(async () => blob)
    const downloadBlob = vi.fn()
    const onSuccess = vi.fn()
    const list = createList({ downloadAttachment, downloadBlob, onSuccess })

    expect(await list.handleDownload(attachment())).toBe(true)
    expect(downloadBlob).toHaveBeenLastCalledWith(blob, 'contract.pdf')
    expect(await list.handleDownload(attachment({ originalFilename: '' }))).toBe(true)
    expect(downloadBlob).toHaveBeenLastCalledWith(blob, 'attachment-a1')
    expect(onSuccess).toHaveBeenCalledWith('systemAttachments.message.downloaded')
  })

  it('reports download failures', async () => {
    const onError = vi.fn()
    const list = createList({
      downloadAttachment: vi.fn(async () => { throw new Error('network') }),
      onError
    })

    expect(await list.handleDownload(attachment())).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemAttachments.message.downloadFailed')
  })

  it('confirms deletion, deletes the attachment and refreshes the list', async () => {
    const confirm = vi.fn(async () => true)
    const deleteAttachment = vi.fn(async () => ({}))
    const getAttachments = vi.fn(async () => page())
    const onSuccess = vi.fn()
    const list = createList({ confirm, deleteAttachment, getAttachments, onSuccess })

    expect(await list.handleDelete(attachment())).toBe(true)
    expect(confirm).toHaveBeenCalledWith(
      'systemAttachments.message.deleteConfirm:{"filename":"contract.pdf"}',
      'systemAttachments.message.prompt',
      {
        confirmButtonText: 'systemAttachments.message.confirm',
        cancelButtonText: 'systemAttachments.message.cancelled',
        type: 'warning'
      }
    )
    expect(deleteAttachment).toHaveBeenCalledWith('a1')
    expect(onSuccess).toHaveBeenCalledWith('systemAttachments.message.deleted')
    expect(getAttachments).toHaveBeenCalledOnce()
  })

  it('silently stops a cancelled deletion and reports other failures', async () => {
    const deleteAttachment = vi.fn(async () => ({}))
    const onError = vi.fn()
    const cancelled = createList({
      confirm: vi.fn(async () => { throw 'cancel' }),
      deleteAttachment,
      onError
    })

    expect(await cancelled.handleDelete(attachment())).toBe(false)
    expect(deleteAttachment).not.toHaveBeenCalled()
    expect(onError).not.toHaveBeenCalled()

    const failed = createList({
      deleteAttachment: vi.fn(async () => { throw new Error('network') }),
      onError
    })
    expect(await failed.handleDelete(attachment())).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemAttachments.message.deleteFailed')
  })
})
