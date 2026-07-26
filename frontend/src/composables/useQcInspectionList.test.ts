import { describe, expect, it, vi } from 'vitest'

import type { QcInspection } from '@/api/qc'
import { useQcInspectionList } from './useQcInspectionList'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.no != null ? `${key}:${params.no}` : key

const row = (overrides: Partial<QcInspection> = {}) => ({
  id: 'q1',
  inspectionNo: 'QC001',
  status: 'DRAFT',
  ...overrides
} as QcInspection)

const createList = (overrides: Partial<Parameters<typeof useQcInspectionList>[1]> = {}) =>
  useQcInspectionList(t, {
    getInspections: vi.fn(async () => ({ records: [row()], total: 1 } as any)),
    getInspection: vi.fn(async () => row({ inspectionNo: 'QC009' })),
    submitInspection: vi.fn(async () => ({})),
    cancelInspection: vi.fn(async () => ({})),
    exportInspections: vi.fn(async () => new Blob(['csv'])),
    printInspection: vi.fn(),
    downloadBlob: vi.fn(),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('qc inspection list', () => {
  it('resets paging on search and clears filters on reset', async () => {
    const getInspections = vi.fn(async () => ({ records: [], total: 4 } as any))
    const list = createList({ getInspections })

    list.searchForm.pageNo = 6
    list.searchForm.keyword = 'QC'
    await list.handleSearch()
    expect(getInspections).toHaveBeenCalledWith(expect.objectContaining({
      keyword: 'QC',
      pageNo: 1
    }))
    expect(list.total.value).toBe(4)

    list.searchForm.status = 'JUDGED'
    list.searchForm.inspectionType = 'OQC'
    await list.handleReset()
    expect(list.searchForm.keyword).toBe('')
    expect(list.searchForm.status).toBe('')
    expect(list.searchForm.inspectionType).toBe('')
    expect(list.searchForm.pageNo).toBe(1)
  })

  it('keeps the requested page and resets to page one on size change', async () => {
    const getInspections = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getInspections })

    await list.handlePageChange(3)
    expect(list.searchForm.pageNo).toBe(3)
    expect(getInspections).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 3 }))

    await list.handleSizeChange(50)
    expect(list.searchForm.pageSize).toBe(50)
    expect(list.searchForm.pageNo).toBe(1)
  })

  it('reports load failures and clears loading', async () => {
    const onError = vi.fn()
    const list = createList({
      getInspections: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    await list.loadData()
    expect(onError).toHaveBeenCalledWith('qcInspection.message.loadFailed')
    expect(list.loading.value).toBe(false)
  })

  it('opens the detail dialog with the fetched detail, not the list row', async () => {
    const getInspection = vi.fn(async () => row({ inspectionNo: 'QC777', totalQty: 5 }))
    const list = createList({ getInspection })

    await list.handleView(row())
    expect(getInspection).toHaveBeenCalledWith('q1')
    expect(list.current.value?.inspectionNo).toBe('QC777')
    expect(list.detailVisible.value).toBe(true)

    const onError = vi.fn()
    const failing = createList({
      getInspection: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await failing.handleView(row())
    expect(failing.detailVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('qcInspection.message.detailLoadFailed')
  })

  it('prints the fetched detail and reports print load failures', async () => {
    const printInspection = vi.fn()
    const list = createList({ printInspection })

    await list.handlePrint(row())
    expect(printInspection).toHaveBeenCalledWith(expect.objectContaining({ inspectionNo: 'QC009' }))

    const onError = vi.fn()
    const failing = createList({
      getInspection: vi.fn(async () => { throw new Error('boom') }),
      printInspection,
      onError
    })
    await failing.handlePrint(row())
    expect(onError).toHaveBeenCalledWith('qcInspection.message.printLoadFailed')
  })

  it('submits and cancels after confirmation, naming the document in the prompt', async () => {
    const submitInspection = vi.fn(async () => ({}))
    const cancelInspection = vi.fn(async () => ({}))
    const confirm = vi.fn(async () => true)
    const onSuccess = vi.fn()
    const getInspections = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ submitInspection, cancelInspection, confirm, onSuccess, getInspections })

    expect(await list.handleSubmit(row())).toBe(true)
    expect(confirm).toHaveBeenCalledWith(
      'qcInspection.message.submitConfirm:QC001',
      'qcInspection.message.prompt',
      { type: 'warning' }
    )
    expect(submitInspection).toHaveBeenCalledWith('q1')

    expect(await list.handleCancel(row({ id: 'q2', inspectionNo: 'QC002' }))).toBe(true)
    expect(cancelInspection).toHaveBeenCalledWith('q2')
    expect(getInspections).toHaveBeenCalledTimes(2)
    expect(onSuccess).toHaveBeenCalledTimes(2)
  })

  it('stays silent when a confirmation is dismissed and defers failures to the interceptor', async () => {
    const submitInspection = vi.fn(async () => ({}))
    const onError = vi.fn()
    const onSuccess = vi.fn()
    const dismissed = createList({
      confirm: vi.fn(async () => { throw 'cancel' }),
      submitInspection,
      onError,
      onSuccess
    })

    expect(await dismissed.handleSubmit(row())).toBe(false)
    expect(submitInspection).not.toHaveBeenCalled()
    expect(onError).not.toHaveBeenCalled()

    const failing = createList({
      submitInspection: vi.fn(async () => { throw new Error('boom') }),
      onError,
      onSuccess
    })
    expect(await failing.handleSubmit(row())).toBe(false)
    expect(onSuccess).not.toHaveBeenCalled()
    expect(onError).not.toHaveBeenCalled()
  })

  it('exports using the current filters and a business-dated file name', async () => {
    const exportInspections = vi.fn(async () => new Blob(['csv']))
    const downloadBlob = vi.fn()
    const onSuccess = vi.fn()
    const list = createList({ exportInspections, downloadBlob, onSuccess })

    list.searchForm.status = 'JUDGED'
    expect(await list.handleExport()).toBe(true)
    expect(exportInspections).toHaveBeenCalledWith(expect.objectContaining({ status: 'JUDGED' }))
    expect(downloadBlob).toHaveBeenCalledWith(
      expect.any(Blob),
      expect.stringContaining('qcInspection.message.exportFile')
    )
    expect(onSuccess).toHaveBeenCalledWith('qcInspection.message.exported')

    const onError = vi.fn()
    const failing = createList({
      exportInspections: vi.fn(async () => { throw new Error('boom') }),
      downloadBlob,
      onError
    })
    expect(await failing.handleExport()).toBe(false)
    expect(onError).toHaveBeenCalledWith('qcInspection.message.exportFailed')
  })
})
