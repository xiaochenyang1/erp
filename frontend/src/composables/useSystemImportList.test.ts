import { describe, expect, it, vi } from 'vitest'

import type { ImportJob } from '@/api/imports'
import { useSystemImportList } from './useSystemImportList'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

const job = (overrides: Partial<ImportJob> = {}) =>
  ({
    jobId: 'j1',
    importType: 'PRODUCT',
    fileName: 'a.csv',
    status: 'VALIDATED',
    totalRows: 2,
    validRows: 2,
    errorRows: 0,
    committedRows: 0,
    rows: [],
    ...overrides
  }) as ImportJob

const createList = (overrides: Partial<Parameters<typeof useSystemImportList>[1]> = {}) =>
  useSystemImportList(t, {
    listJobs: vi.fn(async () => ({ records: [job()], total: 1 } as any)),
    getJob: vi.fn(async () => job({ status: 'COMMITTED' })),
    previewJob: vi.fn(async () => job({ status: 'INVALID', errorRows: 1 })),
    commitJob: vi.fn(async () => job({ status: 'COMMITTED', committedRows: 2 })),
    downloadTemplate: vi.fn(async () => new Blob(['t'])),
    exportErrorRows: vi.fn(async () => new Blob(['e'])),
    downloadBlob: vi.fn(),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  })

describe('system import list', () => {
  it('queries and pages import jobs', async () => {
    const listJobs = vi.fn(async () => ({ records: [], total: 4 } as any))
    const list = createList({ listJobs })
    list.pagination.page = 3
    list.queryForm.importType = 'PRODUCT'
    await list.handleQuery()
    expect(listJobs).toHaveBeenCalledWith(expect.objectContaining({
      importType: 'PRODUCT',
      pageNo: 1,
      pageSize: 20
    }))

    await list.handlePageChange(2)
    expect(list.pagination.page).toBe(2)
    await list.handleSizeChange(50)
    expect(list.pagination.size).toBe(50)
    expect(list.pagination.page).toBe(1)
  })

  it('downloads templates and previews selected files', async () => {
    const downloadBlob = vi.fn()
    const previewJob = vi.fn(async () => job({ status: 'VALIDATED' }))
    const onSuccess = vi.fn()
    const onWarning = vi.fn()
    const list = createList({ downloadBlob, previewJob, onSuccess, onWarning })

    expect(await list.handleDownloadTemplate()).toBe(true)
    expect(downloadBlob).toHaveBeenCalled()

    expect(await list.handlePreview()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('systemImports.message.selectCsv')

    list.handleFileChange(new File(['a'], 'a.csv'))
    expect(await list.handlePreview()).toBe(true)
    expect(previewJob).toHaveBeenCalled()
    expect(list.detailVisible.value).toBe(true)
    expect(onSuccess).toHaveBeenCalledWith('systemImports.message.previewValidated')
  })

  it('views details, exports errors and commits jobs', async () => {
    const commitJob = vi.fn(async () => job({ status: 'COMMITTED', committedRows: 2 }))
    const onSuccess = vi.fn()
    const list = createList({ commitJob, onSuccess })

    expect(await list.handleViewDetail(job())).toBe(true)
    expect(list.detailJob.value?.status).toBe('COMMITTED')

    expect(await list.handleExportErrors(job({ errorRows: 1 }))).toBe(true)
    expect(await list.handleCommit(job())).toBe(true)
    expect(commitJob).toHaveBeenCalledWith('j1')
    expect(onSuccess).toHaveBeenCalledWith('systemImports.message.commitSuccess')
  })

  it('aborts commit when confirmation is cancelled', async () => {
    const commitJob = vi.fn(async () => job())
    const list = createList({
      confirm: vi.fn(async () => { throw new Error('cancel') }),
      commitJob
    })
    expect(await list.handleCommit(job())).toBe(false)
    expect(commitJob).not.toHaveBeenCalled()
  })
})
