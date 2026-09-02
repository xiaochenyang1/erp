import { describe, expect, it, vi } from 'vitest'

import type { FinanceInvoice } from '@/api/finance'
import { useFinanceInvoiceList } from './useFinanceInvoiceList'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

const invoice = (overrides: Partial<FinanceInvoice> = {}): FinanceInvoice => ({
  id: '1',
  invoiceNo: 'INV1',
  invoiceType: 'INPUT',
  partnerName: 'Acme',
  amount: 100,
  taxAmount: 13,
  invoiceDate: '2026-07-01',
  status: 'DRAFT',
  ...overrides
})

const createList = (overrides: Partial<Parameters<typeof useFinanceInvoiceList>[1]> = {}) =>
  useFinanceInvoiceList(t, {
    getFinanceInvoices: vi.fn(async () => ({
      records: [invoice()],
      total: 1,
      pageNo: 1,
      pageSize: 20
    })),
    getFinanceInvoice: vi.fn(async () => invoice({ amount: 200 })),
    postFinanceInvoice: vi.fn(async () => ({})),
    cancelFinanceInvoice: vi.fn(async () => ({})),
    printFinanceInvoice: vi.fn(),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('finance invoice list', () => {
  it('loads, pages, queries date range and resets filters', async () => {
    const getFinanceInvoices = vi.fn(async () => ({
      records: [invoice()],
      total: 3,
      pageNo: 1,
      pageSize: 20
    }))
    const list = createList({ getFinanceInvoices })

    expect(await list.loadData()).toBe(true)
    expect(list.tableData.value).toHaveLength(1)

    list.dateRange.value = ['2026-07-01', '2026-07-31']
    list.queryForm.partnerName = 'Acme'
    await list.handleQuery()
    expect(list.queryForm.dateFrom).toBe('2026-07-01')
    expect(list.queryForm.dateTo).toBe('2026-07-31')
    expect(list.pagination.pageNo).toBe(1)
    expect(getFinanceInvoices).toHaveBeenCalledWith(
      expect.objectContaining({
        partnerName: 'Acme',
        dateFrom: '2026-07-01',
        dateTo: '2026-07-31'
      })
    )

    await list.handleSizeChange(50)
    expect(list.pagination.pageSize).toBe(50)
    expect(list.pagination.pageNo).toBe(1)
    await list.handlePageChange(2)
    expect(list.pagination.pageNo).toBe(2)

    await list.handleReset()
    expect(list.queryForm.partnerName).toBe('')
    expect(list.dateRange.value).toEqual([])
    expect(list.queryForm.dateFrom).toBe('')
  })

  it('prints, posts and cancels invoices', async () => {
    const printFinanceInvoice = vi.fn()
    const postFinanceInvoice = vi.fn(async () => ({}))
    const cancelFinanceInvoice = vi.fn(async () => ({}))
    const confirm = vi.fn(async () => true)
    const onSuccess = vi.fn()
    const list = createList({
      printFinanceInvoice,
      postFinanceInvoice,
      cancelFinanceInvoice,
      confirm,
      onSuccess
    })

    expect(await list.handlePrint(invoice())).toBe(true)
    expect(printFinanceInvoice).toHaveBeenCalledWith(expect.objectContaining({ amount: 200 }))

    expect(await list.handlePost(invoice({ invoiceNo: 'INV9' }))).toBe(true)
    expect(confirm).toHaveBeenCalled()
    expect(postFinanceInvoice).toHaveBeenCalledWith('1')
    expect(onSuccess).toHaveBeenCalledWith('financeReportPages.invoices.message.posted')

    expect(await list.handleCancel(invoice())).toBe(true)
    expect(cancelFinanceInvoice).toHaveBeenCalledWith('1')
    expect(onSuccess).toHaveBeenCalledWith('financeReportPages.invoices.message.cancelled')
  })

  it('aborts post when confirm is dismissed', async () => {
    const postFinanceInvoice = vi.fn(async () => ({}))
    const list = createList({
      postFinanceInvoice,
      confirm: vi.fn(async () => {
        throw new Error('cancel')
      })
    })
    expect(await list.handlePost(invoice())).toBe(false)
    expect(postFinanceInvoice).not.toHaveBeenCalled()
  })
})
