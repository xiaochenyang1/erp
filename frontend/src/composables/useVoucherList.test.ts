import { describe, expect, it, vi } from 'vitest'

import type { Voucher, VoucherEntry } from '@/api/finance'
import { useVoucherList } from './useVoucherList'

const t = (key: string) => key

const voucher = (overrides: Partial<Voucher> = {}): Voucher => ({
  id: 'v1',
  voucherNo: 'JV-001',
  sourceType: 'EXPENSE',
  sourceId: 'e1',
  sourceNo: 'EXP-001',
  bizDate: '2026-07-28',
  amount: 128.5,
  status: 'POSTED',
  remark: 'expense posting',
  ...overrides
})

const entry = (overrides: Partial<VoucherEntry> = {}): VoucherEntry => ({
  id: 've1',
  voucherId: 'v1',
  lineNo: 1,
  subjectId: 's1',
  subjectCode: '6601',
  subjectName: 'Expense',
  debitAmount: 128.5,
  creditAmount: 0,
  summary: 'expense posting',
  ...overrides
})

const page = (records: Voucher[] = [], total = records.length) => ({
  records,
  total,
  pageNo: 1,
  pageSize: 10
})

const createList = (overrides: Partial<Parameters<typeof useVoucherList>[1]> = {}) =>
  useVoucherList(t, {
    getVouchers: vi.fn(async () => page([voucher()])),
    getVoucher: vi.fn(async () => voucher()),
    getVoucherEntries: vi.fn(async () => [entry()]),
    printVoucher: vi.fn(),
    sourceTypeLabel: vi.fn((sourceType) => `source:${sourceType}`),
    statusLabel: vi.fn((status) => `status:${status}`),
    onError: vi.fn(),
    ...overrides
  })

describe('voucher list', () => {
  it('builds query parameters with the selected date range and stores the page', async () => {
    const getVouchers = vi.fn(async () => page([voucher()], 8))
    const list = createList({ getVouchers })
    list.queryParams.pageNo = 3
    list.queryParams.pageSize = 20
    list.queryParams.sourceType = 'EXPENSE'
    list.queryParams.status = 'POSTED'
    list.dateRange.value = ['2026-07-01', '2026-07-31']

    expect(list.buildQueryParams()).toEqual({
      pageNo: 3,
      pageSize: 20,
      sourceType: 'EXPENSE',
      status: 'POSTED',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-31'
    })
    expect(await list.loadData()).toBe(true)
    expect(getVouchers).toHaveBeenCalledWith(list.buildQueryParams())
    expect(list.tableData.value).toEqual([voucher()])
    expect(list.total.value).toBe(8)
    expect(list.loading.value).toBe(false)
  })

  it('resets paging on query and clears every filter on reset', async () => {
    const getVouchers = vi.fn(async () => page())
    const list = createList({ getVouchers })
    list.queryParams.pageNo = 5
    list.queryParams.sourceType = 'EXPENSE_REVERSAL'
    list.queryParams.status = 'CANCELLED'
    list.dateRange.value = ['2026-06-01', '2026-06-30']

    await list.handleQuery()
    expect(getVouchers).toHaveBeenLastCalledWith(expect.objectContaining({
      pageNo: 1,
      sourceType: 'EXPENSE_REVERSAL',
      status: 'CANCELLED',
      dateFrom: '2026-06-01',
      dateTo: '2026-06-30'
    }))

    list.queryParams.pageNo = 4
    await list.handleReset()
    expect(list.queryParams).toMatchObject({
      pageNo: 1,
      sourceType: '',
      status: ''
    })
    expect(list.dateRange.value).toBeNull()
    expect(getVouchers).toHaveBeenLastCalledWith(expect.objectContaining({
      pageNo: 1,
      sourceType: '',
      status: '',
      dateFrom: undefined,
      dateTo: undefined
    }))
  })

  it('keeps page changes and resets to the first page when page size changes', async () => {
    const getVouchers = vi.fn(async () => page())
    const list = createList({ getVouchers })

    await list.handlePageChange(4)
    expect(getVouchers).toHaveBeenLastCalledWith(expect.objectContaining({
      pageNo: 4,
      pageSize: 10
    }))

    await list.handleSizeChange(50)
    expect(getVouchers).toHaveBeenLastCalledWith(expect.objectContaining({
      pageNo: 1,
      pageSize: 50
    }))
  })

  it('reports list failures and always releases the loading state', async () => {
    const onError = vi.fn()
    const getVouchers = vi.fn(async () => { throw new Error('network') })
    const list = createList({ getVouchers, onError })
    list.tableData.value = [voucher({ id: 'existing' })]
    list.total.value = 1

    expect(await list.loadData()).toBe(false)
    expect(list.loading.value).toBe(false)
    expect(list.tableData.value).toEqual([voucher({ id: 'existing' })])
    expect(list.total.value).toBe(1)
    expect(onError).toHaveBeenCalledWith(
      'financeReportPages.vouchers.message.loadFailed'
    )
  })

  it('loads voucher and entries in parallel and replaces stale detail state', async () => {
    let resolveVoucher!: (value: Voucher) => void
    let resolveEntries!: (value: VoucherEntry[]) => void
    const fullVoucher = voucher({ voucherNo: 'JV-DETAIL' })
    const entries = [entry(), entry({ id: 've2', lineNo: 2 })]
    const getVoucher = vi.fn(() => new Promise<Voucher>((resolve) => {
      resolveVoucher = resolve
    }))
    const getVoucherEntries = vi.fn(() => new Promise<VoucherEntry[]>((resolve) => {
      resolveEntries = resolve
    }))
    const list = createList({ getVoucher, getVoucherEntries })
    list.currentVoucher.value = voucher({ id: 'stale' })
    list.detailEntries.value = [entry({ id: 'stale' })]

    const pending = list.handleView(voucher())
    expect(getVoucher).toHaveBeenCalledWith('v1')
    expect(getVoucherEntries).toHaveBeenCalledWith('v1')
    expect(list.detailVisible.value).toBe(true)
    expect(list.detailLoading.value).toBe(true)
    expect(list.currentVoucher.value).toBeNull()
    expect(list.detailEntries.value).toEqual([])

    resolveVoucher(fullVoucher)
    resolveEntries(entries)
    expect(await pending).toBe(true)
    expect(list.currentVoucher.value).toEqual(fullVoucher)
    expect(list.detailEntries.value).toEqual(entries)
    expect(list.detailLoading.value).toBe(false)
  })

  it('clears stale detail and loading state when either detail request fails', async () => {
    const onError = vi.fn()
    const getVoucher = vi.fn(async () => voucher({ voucherNo: 'JV-DETAIL' }))
    const getVoucherEntries = vi.fn(async () => { throw new Error('network') })
    const list = createList({ getVoucher, getVoucherEntries, onError })
    list.currentVoucher.value = voucher({ id: 'stale' })
    list.detailEntries.value = [entry({ id: 'stale' })]

    expect(await list.handleView(voucher())).toBe(false)
    expect(getVoucher).toHaveBeenCalledWith('v1')
    expect(getVoucherEntries).toHaveBeenCalledWith('v1')
    expect(list.currentVoucher.value).toBeNull()
    expect(list.detailEntries.value).toEqual([])
    expect(list.detailVisible.value).toBe(true)
    expect(list.detailLoading.value).toBe(false)
    expect(onError).toHaveBeenCalledWith(
      'financeReportPages.vouchers.message.detailLoadFailed'
    )
  })

  it('assembles complete voucher details and localized labels for printing', async () => {
    const fullVoucher = voucher({
      voucherNo: 'JV-PRINT',
      sourceType: 'EXPENSE_REVERSAL',
      status: 'CANCELLED'
    })
    const entries = [entry()]
    const printVoucher = vi.fn()
    const sourceTypeLabel = vi.fn((sourceType?: string) => `source:${sourceType}`)
    const statusLabel = vi.fn((status: string) => `status:${status}`)
    const list = createList({
      getVoucher: vi.fn(async () => fullVoucher),
      getVoucherEntries: vi.fn(async () => entries),
      printVoucher,
      sourceTypeLabel,
      statusLabel
    })

    expect(await list.handlePrint(voucher())).toBe(true)
    expect(sourceTypeLabel).toHaveBeenCalledWith('EXPENSE_REVERSAL')
    expect(statusLabel).toHaveBeenCalledWith('CANCELLED')
    expect(printVoucher).toHaveBeenCalledWith({
      ...fullVoucher,
      sourceTypeLabel: 'source:EXPENSE_REVERSAL',
      statusLabel: 'status:CANCELLED',
      entries
    })
  })

  it('reports print detail failures without invoking the printer', async () => {
    const getVoucher = vi.fn(async () => { throw new Error('network') })
    const getVoucherEntries = vi.fn(async () => [entry()])
    const printVoucher = vi.fn()
    const onError = vi.fn()
    const list = createList({ getVoucher, getVoucherEntries, printVoucher, onError })

    expect(await list.handlePrint(voucher())).toBe(false)
    expect(getVoucher).toHaveBeenCalledWith('v1')
    expect(getVoucherEntries).toHaveBeenCalledWith('v1')
    expect(printVoucher).not.toHaveBeenCalled()
    expect(onError).toHaveBeenCalledWith(
      'financeReportPages.vouchers.message.printLoadFailed'
    )
  })
})
