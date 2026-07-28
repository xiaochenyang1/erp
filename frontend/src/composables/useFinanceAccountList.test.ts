import { describe, expect, it, vi } from 'vitest'

import type { Receivable, ReceivableQuery } from '@/api/finance'
import { useFinanceAccountList } from './useFinanceAccountList'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

const receivable = (overrides: Partial<Receivable> = {}): Receivable => ({
  id: 'r1',
  receivableNo: 'AR-001',
  customerId: 'c1',
  receivableAmount: 100,
  receivedAmount: 25,
  remainingAmount: 75,
  status: 'PARTIALLY_SETTLED',
  ...overrides
})

const page = (records: Receivable[] = [receivable()], total = records.length) => ({
  records,
  total,
  pageNo: 1,
  pageSize: 20
})

const createList = (
  overrides: Partial<Parameters<typeof useFinanceAccountList<Receivable, ReceivableQuery>>[1]> = {}
) => useFinanceAccountList<Receivable, ReceivableQuery>(t, {
  canView: () => true,
  initialQuery: {
    pageNo: 1,
    pageSize: 20,
    receivableNo: '',
    status: ''
  },
  getList: vi.fn(async () => page()),
  getDetail: vi.fn(async () => receivable({ customerName: 'Acme' })),
  exportList: vi.fn(async () => new Blob(['receivables'])),
  listFailedKey: 'financeAccount.message.receivablesLoadFailed',
  detailFailedKey: 'financeAccount.message.receivableDetailLoadFailed',
  fileNameKey: 'financeAccount.file.receivables',
  downloadBlob: vi.fn(),
  now: () => 123,
  onError: vi.fn(),
  onSuccess: vi.fn(),
  ...overrides
})

describe('finance account list', () => {
  it('loads the configured account side and stores isolated paging state', async () => {
    const getList = vi.fn(async () => page([receivable()], 7))
    const list = createList({ getList })
    list.query.receivableNo = 'AR-001'
    list.query.pageNo = 3

    expect(await list.loadData()).toBe(true)
    expect(getList).toHaveBeenCalledWith(expect.objectContaining({
      receivableNo: 'AR-001',
      pageNo: 3,
      pageSize: 20
    }))
    expect(list.tableData.value).toEqual([receivable()])
    expect(list.total.value).toBe(7)
    expect(list.loading.value).toBe(false)
  })

  it('keeps independently configured account sides from sharing state', () => {
    const receivables = createList()
    const payables = createList()

    receivables.query.pageNo = 4
    receivables.tableData.value = [receivable()]
    receivables.detailVisible.value = true

    expect(payables.query.pageNo).toBe(1)
    expect(payables.tableData.value).toEqual([])
    expect(payables.detailVisible.value).toBe(false)
  })

  it('exports the current query with the localized side-specific filename', async () => {
    const blob = new Blob(['receivables'])
    const exportList = vi.fn(async () => blob)
    const downloadBlob = vi.fn()
    const onSuccess = vi.fn()
    const list = createList({ exportList, downloadBlob, onSuccess })
    list.query.receivableNo = 'AR-009'

    expect(await list.handleExport()).toBe(true)
    expect(exportList).toHaveBeenCalledWith(expect.objectContaining({ receivableNo: 'AR-009' }))
    expect(downloadBlob).toHaveBeenCalledWith(
      blob,
      'financeAccount.file.receivables:{"timestamp":123}'
    )
    expect(onSuccess).toHaveBeenCalledWith('financeAccount.message.exported')
  })

  it('opens a detail only after loading the configured document API', async () => {
    const detail = receivable({ customerName: 'Acme' })
    const getDetail = vi.fn(async () => detail)
    const list = createList({ getDetail })

    expect(await list.handleView(receivable())).toBe(true)
    expect(getDetail).toHaveBeenCalledWith('r1')
    expect(list.detailVisible.value).toBe(true)
    expect(list.selectedDocument.value).toEqual(detail)
    expect(list.detailLoading.value).toBe(false)
  })

  it('reports list, export and detail failures while clearing transient state', async () => {
    const onError = vi.fn()
    const list = createList({
      getList: vi.fn(async () => { throw new Error('list') }),
      exportList: vi.fn(async () => { throw new Error('export') }),
      getDetail: vi.fn(async () => { throw new Error('detail') }),
      onError
    })

    expect(await list.loadData()).toBe(false)
    expect(await list.handleExport()).toBe(false)
    expect(await list.handleView(receivable())).toBe(false)
    expect(onError).toHaveBeenCalledWith('financeAccount.message.receivablesLoadFailed')
    expect(onError).toHaveBeenCalledWith('financeAccount.message.exportFailed')
    expect(onError).toHaveBeenCalledWith('financeAccount.message.receivableDetailLoadFailed')
    expect(list.loading.value).toBe(false)
    expect(list.detailLoading.value).toBe(false)
    expect(list.detailVisible.value).toBe(false)
  })

  it('short-circuits every API when the side is not authorized', async () => {
    const getList = vi.fn(async () => page())
    const exportList = vi.fn(async () => new Blob())
    const getDetail = vi.fn(async () => receivable())
    const list = createList({
      canView: () => false,
      getList,
      exportList,
      getDetail
    })

    expect(await list.loadData()).toBe(false)
    expect(await list.handleExport()).toBe(false)
    expect(await list.handleView(receivable())).toBe(false)
    expect(getList).not.toHaveBeenCalled()
    expect(exportList).not.toHaveBeenCalled()
    expect(getDetail).not.toHaveBeenCalled()
  })
})
