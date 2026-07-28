import { describe, expect, it, vi } from 'vitest'

import type { OrderReportRow } from '@/api/workflow'
import { buildReportParams, useReportList } from './useReportList'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

const orderRow = (id = '1'): OrderReportRow => ({
  id,
  bizNo: `PO-${id}`,
  partnerId: 'partner-1',
  bizDate: '2026-07-01',
  status: 'DRAFT',
  totalQuantity: 2,
  totalAmount: 20,
  totalTaxAmount: 2
})

const page = (records = [orderRow()]) => ({
  records,
  total: records.length,
  pageNo: 1,
  pageSize: 10
})

const createList = (overrides: Partial<Parameters<typeof useReportList>[1]> = {}) =>
  useReportList(t, {
    getPurchaseOrderReport: vi.fn(async () => page()),
    getSalesOrderReport: vi.fn(async () => page([orderRow('2')])),
    getInventoryBalanceReport: vi.fn(async () => ({ records: [], total: 0, pageNo: 1, pageSize: 10 })),
    getInventoryTransactionReport: vi.fn(async () => ({ records: [], total: 0, pageNo: 1, pageSize: 10 })),
    getFinanceSettlementReport: vi.fn(async () => ({ records: [], total: 0, pageNo: 1, pageSize: 10 })),
    exportPurchaseOrderReport: vi.fn(async () => new Blob(['purchase'])),
    exportSalesOrderReport: vi.fn(async () => new Blob(['sales'])),
    exportInventoryBalanceReport: vi.fn(async () => new Blob(['balance'])),
    exportInventoryTransactionReport: vi.fn(async () => new Blob(['transaction'])),
    exportFinanceSettlementReport: vi.fn(async () => new Blob(['settlement'])),
    downloadBlob: vi.fn(),
    now: () => 123,
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('report list', () => {
  it('builds the exact filter contract for each report type', () => {
    const state = { pageNo: 2, pageSize: 50 }
    const range: [string, string] = ['2026-07-01', '2026-07-31']

    expect(buildReportParams('purchase', state, '  PO  ', range)).toEqual({
      pageNo: 2,
      pageSize: 50,
      keyword: 'PO',
      orderDateFrom: '2026-07-01',
      orderDateTo: '2026-07-31'
    })
    expect(buildReportParams('inventoryTransaction', state, 'TX', range)).toEqual({
      pageNo: 2,
      pageSize: 50,
      bizNo: 'TX',
      occurredTimeFrom: '2026-07-01T00:00:00',
      occurredTimeTo: '2026-07-31T23:59:59'
    })
    expect(buildReportParams('financeSettlement', state, '', range)).toEqual({
      pageNo: 2,
      pageSize: 50,
      bizDateFrom: '2026-07-01',
      bizDateTo: '2026-07-31'
    })
    expect(buildReportParams('inventoryBalance', state, 'ignored', range)).toEqual({
      pageNo: 2,
      pageSize: 50
    })
  })

  it('loads isolated tab state and handles page, size and reset actions', async () => {
    const getPurchaseOrderReport = vi.fn(async () => page())
    const getSalesOrderReport = vi.fn(async () => page([orderRow('2')]))
    const list = createList({ getPurchaseOrderReport, getSalesOrderReport })

    list.queryForm.keyword = 'PO'
    list.dateRange.value = ['2026-07-01', '2026-07-31']
    expect(await list.loadActiveReport()).toBe(true)
    expect(list.reportStates.purchase.records[0]).toMatchObject({ id: '1' })

    list.activeKey.value = 'sales'
    expect(await list.handleTabChange()).toBe(true)
    expect(list.reportStates.sales.records[0]).toMatchObject({ id: '2' })
    expect(list.reportStates.purchase.records[0]).toMatchObject({ id: '1' })

    await list.handlePageChange(3)
    expect(getSalesOrderReport).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 3 }))
    await list.handleSizeChange(50)
    expect(getSalesOrderReport).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 1, pageSize: 50 }))

    await list.handleReset()
    expect(list.queryForm.keyword).toBe('')
    expect(list.dateRange.value).toBeNull()
  })

  it('reports load failures and always clears loading state', async () => {
    const onError = vi.fn()
    const list = createList({
      getPurchaseOrderReport: vi.fn(async () => { throw new Error('failed') }),
      onError
    })

    expect(await list.loadActiveReport()).toBe(false)
    expect(list.activeState.value.loading).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.reports.message.loadFailed')
  })

  it('dispatches exports with localized file names and failure feedback', async () => {
    const exportInventoryTransactionReport = vi.fn(async () => new Blob(['transaction']))
    const downloadBlob = vi.fn()
    const onSuccess = vi.fn()
    const list = createList({
      exportInventoryTransactionReport,
      downloadBlob,
      onSuccess
    })
    list.activeKey.value = 'inventoryTransaction'
    list.queryForm.keyword = 'TX-1'

    expect(await list.handleExport()).toBe(true)
    expect(exportInventoryTransactionReport).toHaveBeenCalledWith(expect.objectContaining({ bizNo: 'TX-1' }))
    expect(downloadBlob).toHaveBeenCalledWith(
      expect.any(Blob),
      expect.stringContaining('financeReportPages.reports.tabs.inventoryTransaction')
    )
    expect(onSuccess).toHaveBeenCalledWith('financeReportPages.reports.message.exported')

    const onError = vi.fn()
    const failed = createList({
      exportPurchaseOrderReport: vi.fn(async () => { throw new Error('failed') }),
      onError
    })
    expect(await failed.handleExport()).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeReportPages.reports.message.exportFailed')
  })
})
