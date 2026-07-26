import { describe, expect, it, vi } from 'vitest'

import type { ManualVoucher } from '@/api/finance'
import { useManualVoucherList } from './useManualVoucherList'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.no != null ? `${key}:${params.no}` : key

const row = (overrides: Partial<ManualVoucher> = {}) => ({
  id: 'v1',
  voucherNo: 'MV001',
  bizDate: '2026-07-20',
  amount: 100,
  status: 'DRAFT',
  lines: [],
  ...overrides
} as ManualVoucher)

const createList = (overrides: Partial<Parameters<typeof useManualVoucherList>[1]> = {}) =>
  useManualVoucherList(t, {
    getVouchers: vi.fn(async () => ({ records: [row()], total: 1 } as any)),
    getVoucher: vi.fn(async () => row({ lines: [{ lineNo: 1 }] as any })),
    getSubjects: vi.fn(async () => ({ records: [{ id: 's1' }], total: 1 } as any)),
    submitVoucher: vi.fn(async () => ({})),
    approveVoucher: vi.fn(async () => ({})),
    postVoucher: vi.fn(async () => ({})),
    deleteVoucher: vi.fn(async () => ({})),
    cancelVoucher: vi.fn(async () => ({})),
    rejectVoucher: vi.fn(async () => ({})),
    printVoucher: vi.fn(),
    decoratePrint: (doc) => ({ ...doc, statusLabel: 'draft' }),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  })

describe('manual voucher list', () => {
  it('resets to the first page on query but keeps it while paginating', async () => {
    const getVouchers = vi.fn(async () => ({ records: [], total: 9 } as any))
    const list = createList({ getVouchers })

    list.queryForm.pageNo = 5
    list.queryForm.voucherNo = 'MV009'
    await list.handleQuery()
    expect(getVouchers).toHaveBeenLastCalledWith(expect.objectContaining({
      voucherNo: 'MV009',
      pageNo: 1
    }))
    expect(list.total.value).toBe(9)

    list.queryForm.pageNo = 3
    await list.handlePageChange()
    expect(getVouchers).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 3 }))
  })

  it('clears every filter on reset and reports load failures', async () => {
    const list = createList()
    list.queryForm.voucherNo = 'MV001'
    list.queryForm.status = 'POSTED'
    list.queryForm.dateFrom = '2026-07-01'
    list.queryForm.dateTo = '2026-07-31'
    list.queryForm.pageNo = 4

    await list.handleReset()
    expect(list.queryForm.voucherNo).toBe('')
    expect(list.queryForm.status).toBe('')
    expect(list.queryForm.dateFrom).toBe('')
    expect(list.queryForm.dateTo).toBe('')
    expect(list.queryForm.pageNo).toBe(1)

    const onError = vi.fn()
    const failing = createList({
      getVouchers: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await failing.loadData()
    expect(onError).toHaveBeenCalledWith('financeReportPages.manualVouchers.message.loadFailed')
    expect(failing.loading.value).toBe(false)
  })

  it('loads active accounts and warns without blocking the page', async () => {
    const getSubjects = vi.fn(async () => ({ records: [{ id: 's1' }], total: 1 } as any))
    const list = createList({ getSubjects })

    await list.loadSubjects()
    expect(getSubjects).toHaveBeenCalledWith({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    expect(list.subjects.value).toHaveLength(1)

    const onWarning = vi.fn()
    const failing = createList({
      getSubjects: vi.fn(async () => { throw new Error('boom') }),
      onWarning
    })
    await failing.loadSubjects()
    expect(onWarning)
      .toHaveBeenCalledWith('financeReportPages.manualVouchers.message.subjectsLoadFailed')
  })

  it('runs each state-machine action after confirmation and reloads', async () => {
    const submitVoucher = vi.fn(async () => ({}))
    const approveVoucher = vi.fn(async () => ({}))
    const postVoucher = vi.fn(async () => ({}))
    const getVouchers = vi.fn(async () => ({ records: [], total: 0 } as any))
    const onSuccess = vi.fn()
    const list = createList({ submitVoucher, approveVoucher, postVoucher, getVouchers, onSuccess })

    expect(await list.handleSubmit(row())).toBe(true)
    expect(await list.handleApprove(row({ id: 'v2' }))).toBe(true)
    expect(await list.handlePost(row({ id: 'v3' }))).toBe(true)

    expect(submitVoucher).toHaveBeenCalledWith('v1')
    expect(approveVoucher).toHaveBeenCalledWith('v2')
    expect(postVoucher).toHaveBeenCalledWith('v3')
    expect(getVouchers).toHaveBeenCalledTimes(3)
    expect(onSuccess).toHaveBeenCalledTimes(3)
  })

  it('marks the delete confirmation as destructive and stays silent when dismissed', async () => {
    const confirm = vi.fn(async () => true)
    const deleteVoucher = vi.fn(async () => ({}))
    const list = createList({ confirm, deleteVoucher })

    await list.handleDelete(row())
    expect(deleteVoucher).toHaveBeenCalledWith('v1')
    expect(confirm).toHaveBeenLastCalledWith(
      'financeReportPages.manualVouchers.message.deleteConfirm:MV001',
      'financeReportPages.manualVouchers.message.deleteTitle',
      expect.objectContaining({ confirmButtonClass: 'el-button--danger' })
    )

    const onError = vi.fn()
    const dismissed = createList({
      confirm: vi.fn(async () => { throw 'cancel' }),
      deleteVoucher,
      onError
    })
    expect(await dismissed.handleDelete(row())).toBe(false)
    expect(onError).not.toHaveBeenCalled()
  })

  it('requires a reason before voiding and reports failures', async () => {
    const cancelVoucher = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({ cancelVoucher, onSuccess })

    list.openCancel(row({ status: 'POSTED' }))
    expect(list.cancelVisible.value).toBe(true)
    expect(list.cancelReason.value).toBe('')

    expect(await list.handleCancel()).toBe(false)
    expect(cancelVoucher).not.toHaveBeenCalled()

    list.cancelReason.value = '  录错科目  '
    expect(await list.handleCancel()).toBe(true)
    expect(cancelVoucher).toHaveBeenCalledWith('v1', '录错科目')
    expect(list.cancelVisible.value).toBe(false)
    expect(list.cancelling.value).toBe(false)

    const onError = vi.fn()
    const failing = createList({
      cancelVoucher: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    failing.openCancel(row())
    failing.cancelReason.value = 'x'
    expect(await failing.handleCancel()).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.manualVouchers.message.cancelFailed')
  })

  it('requires a reason before rejecting', async () => {
    const rejectVoucher = vi.fn(async () => ({}))
    const list = createList({ rejectVoucher })

    list.openReject(row({ status: 'PENDING' }))
    expect(list.rejectVisible.value).toBe(true)

    expect(await list.handleReject()).toBe(false)
    expect(rejectVoucher).not.toHaveBeenCalled()

    list.rejectReason.value = ' 摘要不清 '
    expect(await list.handleReject()).toBe(true)
    expect(rejectVoucher).toHaveBeenCalledWith('v1', '摘要不清')
    expect(list.rejectVisible.value).toBe(false)
  })

  it('loads the full detail for view and print rather than the list row', async () => {
    const printVoucher = vi.fn()
    const getVoucher = vi.fn(async () => row({ voucherNo: 'MV777', lines: [{ lineNo: 1 }] as any }))
    const list = createList({ printVoucher, getVoucher })

    expect(await list.openDetail(row())).toBe(true)
    expect(getVoucher).toHaveBeenCalledWith('v1')
    expect(list.currentVoucher.value?.voucherNo).toBe('MV777')
    expect(list.detailVisible.value).toBe(true)

    expect(await list.handlePrint(row())).toBe(true)
    expect(printVoucher).toHaveBeenCalledWith(expect.objectContaining({
      voucherNo: 'MV777',
      statusLabel: 'draft'
    }))

    const onError = vi.fn()
    const failing = createList({
      getVoucher: vi.fn(async () => { throw new Error('boom') }),
      printVoucher,
      onError
    })
    expect(await failing.openDetail(row())).toBe(false)
    expect(failing.detailVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.manualVouchers.message.detailLoadFailed')
    expect(await failing.handlePrint(row())).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.manualVouchers.message.printLoadFailed')
  })
})
