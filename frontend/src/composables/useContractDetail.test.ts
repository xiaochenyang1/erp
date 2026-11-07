import { describe, expect, it, vi } from 'vitest'

import type { Attachment } from '@/api/attachment'
import type { ContractRecord, ContractVersionRecord } from '@/api/contracts'
import { useContractDetail } from './useContractDetail'

const t = (key: string) => key

const contract = () => ({ id: 'c1', contractNo: 'HT001', contractName: '框架协议', lines: [] }) as unknown as ContractRecord

const attachment = (overrides: Partial<Attachment> = {}) =>
  ({ id: 'a1', originalFilename: '合同扫描件.pdf', ...overrides }) as Attachment

const version = (overrides: Partial<ContractVersionRecord> = {}) =>
  ({ id: 'v1', contractId: 'c1', versionNo: 2, eventType: 'EDITED', ...overrides }) as ContractVersionRecord

const createDetail = (overrides: Partial<Parameters<typeof useContractDetail>[1]> = {}) =>
  useContractDetail(t, {
    getContract: vi.fn(async () => contract()),
    getContractAttachments: vi.fn(async () => ({ records: [attachment()], total: 1 } as any)),
    getContractVersions: vi.fn(async () => [version()]),
    getContractVersion: vi.fn(async () => version({ versionNo: 1 })),
    restoreContractVersion: vi.fn(async () => ({})),
    uploadContractAttachment: vi.fn(async () => ({})),
    downloadContractAttachment: vi.fn(async () => new Blob(['x'])),
    deleteContractAttachment: vi.fn(async () => ({})),
    downloadBlob: vi.fn(),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onRestored: vi.fn(),
    ...overrides
  })

const file = () => new File(['x'], 'scan.pdf')

describe('contract detail', () => {
  it('refetches the contract with its attachments and versions', async () => {
    const getContract = vi.fn(async () => contract())
    const detail = createDetail({ getContract })

    expect(await detail.openDetail({ id: 'c1' } as ContractRecord)).toBe(true)
    expect(getContract).toHaveBeenCalledWith('c1')
    expect(detail.detailVisible.value).toBe(true)
    expect(detail.attachments.value).toHaveLength(1)
    expect(detail.versions.value).toHaveLength(1)
  })

  it('reports a failed detail load and keeps the dialog closed', async () => {
    const onError = vi.fn()
    const detail = createDetail({
      getContract: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    expect(await detail.openDetail({ id: 'c1' } as ContractRecord)).toBe(false)
    expect(detail.detailVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('contractPage.message.detailFailed')
  })

  it('clears the detail and the snapshot when the dialogs close', async () => {
    const detail = createDetail()

    await detail.openDetail({ id: 'c1' } as ContractRecord)
    await detail.openVersion(version())
    detail.resetVersion()
    expect(detail.versionSnapshot.value).toBeNull()

    await detail.openVersion(version())
    detail.resetDetail()
    expect(detail.selected.value).toBeNull()
    expect(detail.attachments.value).toEqual([])
    expect(detail.versions.value).toEqual([])
    expect(detail.versionSnapshot.value).toBeNull()
  })

  it('loads one version snapshot, and needs an open contract to do it', async () => {
    const getContractVersion = vi.fn(async () => version({ versionNo: 1 }))
    const detail = createDetail({ getContractVersion })

    expect(await detail.openVersion(version())).toBe(false)
    expect(getContractVersion).not.toHaveBeenCalled()

    await detail.openDetail({ id: 'c1' } as ContractRecord)
    expect(await detail.openVersion(version())).toBe(true)
    expect(getContractVersion).toHaveBeenCalledWith('c1', 'v1')
    expect(detail.versionVisible.value).toBe(true)
    expect(detail.versionSnapshot.value?.versionNo).toBe(1)

    const onError = vi.fn()
    const failing = createDetail({
      getContractVersion: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await failing.openDetail({ id: 'c1' } as ContractRecord)
    expect(await failing.openVersion(version())).toBe(false)
    expect(failing.versionVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('contractPage.message.versionFailed')
  })

  it('restores a version into a new draft after confirmation', async () => {
    const confirm = vi.fn(async () => true)
    const restoreContractVersion = vi.fn(async () => ({}))
    const onRestored = vi.fn()
    const onSuccess = vi.fn()
    const detail = createDetail({ confirm, restoreContractVersion, onRestored, onSuccess })

    await detail.openDetail({ id: 'c1' } as ContractRecord)
    expect(await detail.restoreVersion(version())).toBe(true)

    expect(confirm).toHaveBeenCalledWith(
      'contractPage.message.confirmRestore',
      'contractPage.message.prompt',
      { type: 'warning' }
    )
    expect(restoreContractVersion).toHaveBeenCalledWith('c1', 'v1')
    expect(detail.detailVisible.value).toBe(false)
    expect(onSuccess).toHaveBeenCalledWith('contractPage.message.restored')
    expect(onRestored).toHaveBeenCalled()
  })

  it('leaves the contract untouched when the restore is dismissed or fails', async () => {
    const restoreContractVersion = vi.fn(async () => ({}))
    const onError = vi.fn()
    const dismissed = createDetail({
      confirm: vi.fn(async () => { throw 'cancel' }),
      restoreContractVersion,
      onError
    })

    await dismissed.openDetail({ id: 'c1' } as ContractRecord)
    expect(await dismissed.restoreVersion(version())).toBe(false)
    expect(restoreContractVersion).not.toHaveBeenCalled()
    expect(dismissed.detailVisible.value).toBe(true)
    expect(onError).not.toHaveBeenCalled()

    const failing = createDetail({
      restoreContractVersion: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await failing.openDetail({ id: 'c1' } as ContractRecord)
    expect(await failing.restoreVersion(version())).toBe(false)
    expect(onError).toHaveBeenCalledWith('contractPage.message.restoreFailed')

    const closed = createDetail()
    expect(await closed.restoreVersion(version())).toBe(false)
  })

  it('uploads an attachment, then reloads the list from the backend', async () => {
    const uploadContractAttachment = vi.fn(async () => ({}))
    const getContractAttachments = vi.fn(async () => ({
      records: [attachment(), attachment({ id: 'a2' })],
      total: 2
    } as any))
    const onSuccess = vi.fn()
    const detail = createDetail({ uploadContractAttachment, getContractAttachments, onSuccess })

    await detail.openDetail({ id: 'c1' } as ContractRecord)
    expect(await detail.uploadAttachment(file())).toBe(true)
    expect(uploadContractAttachment).toHaveBeenCalledWith('c1', expect.any(File))
    expect(detail.attachments.value).toHaveLength(2)
    expect(detail.uploading.value).toBe(false)
    expect(onSuccess).toHaveBeenCalledWith('contractPage.message.attachmentUploaded')
  })

  it('ignores an empty pick and reports a failed upload', async () => {
    const uploadContractAttachment = vi.fn(async () => ({}))
    const detail = createDetail({ uploadContractAttachment })

    await detail.openDetail({ id: 'c1' } as ContractRecord)
    expect(await detail.uploadAttachment(undefined)).toBe(false)
    expect(await detail.uploadAttachment(null)).toBe(false)
    expect(uploadContractAttachment).not.toHaveBeenCalled()

    const closed = createDetail({ uploadContractAttachment })
    expect(await closed.uploadAttachment(file())).toBe(false)
    expect(uploadContractAttachment).not.toHaveBeenCalled()

    const onError = vi.fn()
    const failing = createDetail({
      uploadContractAttachment: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await failing.openDetail({ id: 'c1' } as ContractRecord)
    expect(await failing.uploadAttachment(file())).toBe(false)
    expect(failing.uploading.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('contractPage.message.attachmentFailed')
  })

  it('downloads an attachment under its original filename', async () => {
    const downloadContractAttachment = vi.fn(async () => new Blob(['x']))
    const downloadBlob = vi.fn()
    const detail = createDetail({ downloadContractAttachment, downloadBlob })

    await detail.openDetail({ id: 'c1' } as ContractRecord)
    expect(await detail.downloadAttachment(attachment())).toBe(true)
    expect(downloadContractAttachment).toHaveBeenCalledWith('c1', 'a1')
    expect(downloadBlob).toHaveBeenCalledWith(expect.any(Blob), '合同扫描件.pdf')

    expect(await detail.downloadAttachment(attachment({ id: 'a3', originalFilename: '' }))).toBe(true)
    expect(downloadBlob).toHaveBeenLastCalledWith(expect.any(Blob), 'attachment-a3')

    const onError = vi.fn()
    const failing = createDetail({
      downloadContractAttachment: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await failing.openDetail({ id: 'c1' } as ContractRecord)
    expect(await failing.downloadAttachment(attachment())).toBe(false)
    expect(onError).toHaveBeenCalledWith('contractPage.message.attachmentFailed')
  })

  it('confirms before deleting an attachment and drops only that row', async () => {
    const confirm = vi.fn(async () => true)
    const deleteContractAttachment = vi.fn(async () => ({}))
    const getContractAttachments = vi.fn(async () => ({
      records: [attachment(), attachment({ id: 'a2' })],
      total: 2
    } as any))
    const onSuccess = vi.fn()
    const detail = createDetail({
      confirm,
      deleteContractAttachment,
      getContractAttachments,
      onSuccess
    })

    await detail.openDetail({ id: 'c1' } as ContractRecord)
    expect(await detail.deleteAttachment(attachment())).toBe(true)

    expect(confirm).toHaveBeenCalledWith(
      'contractPage.message.confirmDeleteAttachment',
      'contractPage.message.prompt',
      { type: 'warning' }
    )
    expect(deleteContractAttachment).toHaveBeenCalledWith('c1', 'a1')
    expect(detail.attachments.value.map((item) => item.id)).toEqual(['a2'])
    expect(onSuccess).toHaveBeenCalledWith('contractPage.message.attachmentDeleted')
  })

  it('keeps the attachment when the confirmation is dismissed or the call fails', async () => {
    const deleteContractAttachment = vi.fn(async () => ({}))
    const onError = vi.fn()
    const dismissed = createDetail({
      confirm: vi.fn(async () => { throw 'cancel' }),
      deleteContractAttachment,
      onError
    })

    await dismissed.openDetail({ id: 'c1' } as ContractRecord)
    expect(await dismissed.deleteAttachment(attachment())).toBe(false)
    expect(deleteContractAttachment).not.toHaveBeenCalled()
    expect(dismissed.attachments.value).toHaveLength(1)
    expect(onError).not.toHaveBeenCalled()

    const failing = createDetail({
      deleteContractAttachment: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await failing.openDetail({ id: 'c1' } as ContractRecord)
    expect(await failing.deleteAttachment(attachment())).toBe(false)
    expect(failing.attachments.value).toHaveLength(1)
    expect(onError).toHaveBeenCalledWith('contractPage.message.attachmentFailed')

    const closed = createDetail()
    expect(await closed.deleteAttachment(attachment())).toBe(false)
    expect(await closed.downloadAttachment(attachment())).toBe(false)
  })
})
