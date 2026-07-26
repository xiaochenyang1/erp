import { describe, expect, it, vi } from 'vitest'

import type { PurchaseReturn } from '@/api/purchase'
import { usePurchaseReturnList } from './usePurchaseReturnList'

const t = (key: string, params?: Record<string, unknown>) => {
  if (params?.returnNo != null) return `${key}:${params.returnNo}`
  if (params?.timestamp != null) return `${key}:${params.timestamp}`
  return key
}

describe('purchase return list', () => {
  const createList = (overrides: Partial<Parameters<typeof usePurchaseReturnList>[1]> = {}) =>
    usePurchaseReturnList(t, {
      getReturns: vi.fn(async () => ({
        records: [{ id: 'pr-1', returnNo: 'PRT001', status: 'DRAFT' }],
        total: 1
      } as any)),
      getReturn: vi.fn(async () => ({
        id: 'pr-1',
        returnNo: 'PRT001',
        status: 'DRAFT'
      } as PurchaseReturn)),
      getReceipt: vi.fn(async () => ({ id: 'r1', receiptNo: 'PR001' } as any)),
      getReceipts: vi.fn(async () => ({ records: [{ id: 'r1' }], total: 1 } as any)),
      postReturn: vi.fn(async () => ({})),
      cancelReturn: vi.fn(async () => ({})),
      exportReturns: vi.fn(async () => new Blob(['csv'])),
      getProducts: vi.fn(async () => ({ records: [{ id: 'p1' }], total: 1 } as any)),
      getLocations: vi.fn(async () => ({ records: [{ id: 'l1' }], total: 1 } as any)),
      printReturn: vi.fn(),
      downloadBlob: vi.fn(),
      confirm: vi.fn(async () => true),
      onSuccess: vi.fn(),
      onError: vi.fn(),
      onWarning: vi.fn(),
      ...overrides
    })

  it('loads returns and supports reset/pagination/date filters', async () => {
    const getReturns = vi.fn(async () => ({
      records: [{ id: 'pr-2', returnNo: 'PRT002' }],
      total: 3
    } as any))
    const list = createList({ getReturns })

    list.queryForm.returnNo = 'PRT002'
    list.handleDateChange(['2026-07-01', '2026-07-26'])
    await list.handleQuery()
    expect(getReturns).toHaveBeenCalledWith(expect.objectContaining({
      returnNo: 'PRT002',
      startDate: '2026-07-01',
      endDate: '2026-07-26'
    }))
    expect(list.total.value).toBe(3)

    list.handlePageChange(2, 50)
    await Promise.resolve()
    expect(list.queryForm.pageNo).toBe(2)
    expect(list.queryForm.pageSize).toBe(50)

    list.handleReset()
    await Promise.resolve()
    expect(list.queryForm.returnNo).toBe('')
  })

  it('views/prints returns and linked receipts', async () => {
    const printReturn = vi.fn()
    const getReturn = vi.fn(async () => ({ id: 'pr-9', returnNo: 'PRT009' } as PurchaseReturn))
    const list = createList({ printReturn, getReturn })

    await list.handleView({ id: 'pr-9' } as PurchaseReturn)
    expect(list.detailVisible.value).toBe(true)
    expect(list.currentRow.value?.returnNo).toBe('PRT009')

    await list.handlePrint({ id: 'pr-9' } as PurchaseReturn)
    expect(printReturn).toHaveBeenCalledWith(expect.objectContaining({ returnNo: 'PRT009' }))

    await list.viewReceipt('r1')
    expect(list.linkedReceiptVisible.value).toBe(true)
    expect(list.linkedReceipt.value?.id).toBe('r1')
  })

  it('posts, cancels, exports and loads create options', async () => {
    const postReturn = vi.fn(async () => ({}))
    const cancelReturn = vi.fn(async () => ({}))
    const exportReturns = vi.fn(async () => new Blob(['csv']))
    const downloadBlob = vi.fn()
    const onSuccess = vi.fn()
    const list = createList({
      postReturn,
      cancelReturn,
      exportReturns,
      downloadBlob,
      onSuccess
    })

    await list.handleComplete({ id: 'pr-3', returnNo: 'PRT003' } as PurchaseReturn)
    await list.handleCancel({ id: 'pr-4', returnNo: 'PRT004' } as PurchaseReturn)
    await list.handleExport()
    const receipts = await list.loadCreateOptions()

    expect(postReturn).toHaveBeenCalledWith('pr-3')
    expect(cancelReturn).toHaveBeenCalledWith('pr-4')
    expect(exportReturns).toHaveBeenCalled()
    expect(downloadBlob).toHaveBeenCalled()
    expect(receipts).toHaveLength(1)
    expect(onSuccess).toHaveBeenCalledWith('purchaseReturn.message.posted')
    expect(onSuccess).toHaveBeenCalledWith('purchaseReturn.message.cancelled')
    expect(onSuccess).toHaveBeenCalledWith('purchaseReturn.message.exported')
  })
})
