import { describe, expect, it, vi } from 'vitest'

import type { PurchaseReceipt } from '@/api/purchase'
import { usePurchaseReceiptList } from './usePurchaseReceiptList'

const t = (key: string, params?: Record<string, unknown>) => {
  if (params?.receiptNo != null) return `${key}:${params.receiptNo}`
  if (params?.timestamp != null) return `${key}:${params.timestamp}`
  return key
}

describe('purchase receipt list', () => {
  const createList = (overrides: Partial<Parameters<typeof usePurchaseReceiptList>[1]> = {}) =>
    usePurchaseReceiptList(t, {
      getReceipts: vi.fn(async () => ({
        records: [{ id: 'pr-1', receiptNo: 'PR001', status: 'DRAFT' }],
        total: 1
      } as any)),
      getReceipt: vi.fn(async () => ({
        id: 'pr-1',
        receiptNo: 'PR001'
      } as PurchaseReceipt)),
      getOrder: vi.fn(async () => ({ id: 'po-1', orderNo: 'PO001' } as any)),
      completeReceipt: vi.fn(async () => ({})),
      cancelReceipt: vi.fn(async () => ({})),
      exportReceipts: vi.fn(async () => new Blob(['csv'])),
      getWarehouses: vi.fn(async () => ({ records: [{ id: 'w1' }], total: 1 } as any)),
      getSuppliers: vi.fn(async () => ({ records: [{ id: 's1' }], total: 1 } as any)),
      getLocations: vi.fn(async () => ({ records: [{ id: 'loc-1' }], total: 1 } as any)),
      printReceipt: vi.fn(),
      downloadBlob: vi.fn(),
      confirm: vi.fn(async () => true),
      ...overrides
    })

  it('loads receipts and supports reset/pagination/date filters', async () => {
    const getReceipts = vi.fn(async () => ({
      records: [{ id: 'pr-2', receiptNo: 'PR002' }],
      total: 3
    } as any))
    const list = createList({ getReceipts })

    list.queryForm.receiptNo = 'PR002'
    list.handleDateChange(['2026-07-01', '2026-07-26'])
    await list.handleQuery()

    expect(getReceipts).toHaveBeenCalledWith(expect.objectContaining({
      receiptNo: 'PR002',
      startDate: '2026-07-01',
      endDate: '2026-07-26'
    }))
    expect(list.tableData.value).toEqual([{ id: 'pr-2', receiptNo: 'PR002' }])
    expect(list.total.value).toBe(3)

    list.handlePageChange(2, 50)
    await Promise.resolve()
    expect(list.queryForm.pageNo).toBe(2)
    expect(list.queryForm.pageSize).toBe(50)

    list.handleReset()
    await Promise.resolve()
    expect(list.queryForm.receiptNo).toBe('')
    expect(list.dateRange.value).toBeUndefined()
  })

  it('loads master-data options and locations', async () => {
    const list = createList()
    await list.loadOptions()

    expect(list.warehouses.value).toEqual([{ id: 'w1' }])
    expect(list.suppliers.value).toEqual([{ id: 's1' }])
    expect(list.locations.value).toEqual([{ id: 'loc-1' }])
  })

  it('views, prints, and opens linked purchase order details', async () => {
    const printReceipt = vi.fn()
    const getReceipt = vi.fn(async () => ({ id: 'pr-9', receiptNo: 'PR009' } as PurchaseReceipt))
    const getOrder = vi.fn(async () => ({ id: 'po-9', orderNo: 'PO009' } as any))
    const list = createList({ printReceipt, getReceipt, getOrder })

    list.handleView({ id: 'pr-9', receiptNo: 'PR009' } as PurchaseReceipt)
    expect(list.detailVisible.value).toBe(true)
    expect(list.currentRow.value?.id).toBe('pr-9')

    await list.handlePrint({ id: 'pr-9' } as PurchaseReceipt)
    expect(getReceipt).toHaveBeenCalledWith('pr-9')
    expect(printReceipt).toHaveBeenCalledWith(expect.objectContaining({ receiptNo: 'PR009' }))

    await list.viewOrder('po-9')
    expect(list.linkedOrderVisible.value).toBe(true)
    expect(list.linkedOrder.value).toEqual({ id: 'po-9', orderNo: 'PO009' })
  })

  it('posts, cancels, and exports receipts', async () => {
    const completeReceipt = vi.fn(async () => ({}))
    const cancelReceipt = vi.fn(async () => ({}))
    const exportReceipts = vi.fn(async () => new Blob(['csv']))
    const downloadBlob = vi.fn()
    const onSuccess = vi.fn()
    const confirm = vi.fn(async () => true)
    const list = createList({
      completeReceipt,
      cancelReceipt,
      exportReceipts,
      downloadBlob,
      confirm,
      onSuccess
    })

    await list.handleComplete({ id: 'pr-3', receiptNo: 'PR003' } as PurchaseReceipt)
    await list.handleCancel({ id: 'pr-4', receiptNo: 'PR004' } as PurchaseReceipt)
    await list.handleExport()

    expect(completeReceipt).toHaveBeenCalledWith('pr-3')
    expect(cancelReceipt).toHaveBeenCalledWith('pr-4')
    expect(exportReceipts).toHaveBeenCalled()
    expect(downloadBlob).toHaveBeenCalled()
    expect(onSuccess).toHaveBeenCalledWith('purchaseReceipt.message.posted')
    expect(onSuccess).toHaveBeenCalledWith('purchaseReceipt.message.cancelled')
    expect(onSuccess).toHaveBeenCalledWith('purchaseReceipt.message.exported')
  })
})
