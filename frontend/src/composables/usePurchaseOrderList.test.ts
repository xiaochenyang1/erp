import { describe, expect, it, vi } from 'vitest'

import type { PurchaseOrder } from '@/api/purchase'
import { usePurchaseOrderList } from './usePurchaseOrderList'

const t = (key: string, params?: Record<string, unknown>) => {
  if (params?.orderNo != null) return `${key}:${params.orderNo}`
  if (params?.timestamp != null) return `${key}:${params.timestamp}`
  return key
}

describe('purchase order list', () => {
  const createList = (overrides: Partial<Parameters<typeof usePurchaseOrderList>[1]> = {}) =>
    usePurchaseOrderList(t, {
      getOrders: vi.fn(async () => ({
        records: [{ id: 'po-1', orderNo: 'PO001', status: 'DRAFT' }],
        total: 1
      } as any)),
      getOrder: vi.fn(async () => ({
        id: 'po-1',
        orderNo: 'PO001'
      } as PurchaseOrder)),
      submitOrder: vi.fn(async () => ({})),
      approveOrder: vi.fn(async () => ({})),
      unapproveOrder: vi.fn(async () => ({})),
      rejectOrder: vi.fn(async () => ({})),
      cancelOrder: vi.fn(async () => ({})),
      closeOrder: vi.fn(async () => ({})),
      traceOrder: vi.fn(async () => ({
        order: { id: 'po-1', orderNo: 'PO001' },
        executionInfo: { orderedQty: 1, receivedQty: 0, remainingReceiptQty: 1, receiptStatus: 'NOT_RECEIVED' },
        relatedDocs: { receipts: [], returns: [], payables: [], payments: [], vouchers: [] }
      } as any)),
      exportOrders: vi.fn(async () => new Blob(['csv'])),
      getSuppliers: vi.fn(async () => ({ records: [{ id: 's1' }], total: 1 } as any)),
      getProducts: vi.fn(async () => ({ records: [{ id: 'p1' }], total: 1 } as any)),
      printOrder: vi.fn(),
      downloadBlob: vi.fn(),
      confirm: vi.fn(async () => true),
      prompt: vi.fn(async () => ({ value: 'bad price' })),
      ...overrides
    })

  it('loads orders and supports reset/pagination/date filters', async () => {
    const getOrders = vi.fn(async () => ({
      records: [{ id: 'po-2', orderNo: 'PO002' }],
      total: 4
    } as any))
    const list = createList({ getOrders })

    list.queryForm.orderNo = 'PO002'
    list.handleDateChange(['2026-07-01', '2026-07-26'])
    await list.handleQuery()

    expect(getOrders).toHaveBeenCalledWith(expect.objectContaining({
      orderNo: 'PO002',
      startDate: '2026-07-01',
      endDate: '2026-07-26'
    }))
    expect(list.tableData.value).toEqual([{ id: 'po-2', orderNo: 'PO002' }])
    expect(list.total.value).toBe(4)

    list.handlePageChange(2, 50)
    await Promise.resolve()
    expect(list.queryForm.pageNo).toBe(2)
    expect(list.queryForm.pageSize).toBe(50)

    list.handleReset()
    await Promise.resolve()
    expect(list.queryForm.orderNo).toBe('')
    expect(list.dateRange.value).toBeUndefined()
  })

  it('loads suppliers/products and supports view/print/trace', async () => {
    const printOrder = vi.fn()
    const getOrder = vi.fn(async () => ({ id: 'po-9', orderNo: 'PO009' } as PurchaseOrder))
    const list = createList({ printOrder, getOrder })

    await list.loadOptions()
    expect(list.suppliers.value).toEqual([{ id: 's1' }])
    expect(list.products.value).toEqual([{ id: 'p1' }])

    list.handleView({ id: 'po-9', orderNo: 'PO009' } as PurchaseOrder)
    expect(list.detailVisible.value).toBe(true)
    expect(list.currentRow.value?.id).toBe('po-9')

    await list.handlePrint({ id: 'po-9' } as PurchaseOrder)
    expect(getOrder).toHaveBeenCalledWith('po-9')
    expect(printOrder).toHaveBeenCalledWith(expect.objectContaining({ orderNo: 'PO009' }))

    await list.handleTraceOrder({ id: 'po-9' } as PurchaseOrder)
    expect(list.traceVisible.value).toBe(true)
    expect(list.purchaseTrace.value?.order.orderNo).toBe('PO001')
  })

  it('runs approve/cancel/export/reject actions', async () => {
    const approveOrder = vi.fn(async () => ({}))
    const cancelOrder = vi.fn(async () => ({}))
    const rejectOrder = vi.fn(async () => ({}))
    const exportOrders = vi.fn(async () => new Blob(['csv']))
    const downloadBlob = vi.fn()
    const onSuccess = vi.fn()
    const list = createList({
      approveOrder,
      cancelOrder,
      rejectOrder,
      exportOrders,
      downloadBlob,
      onSuccess
    })

    await list.handleApprove({ id: 'po-3', orderNo: 'PO003' } as PurchaseOrder)
    await list.handleCancelOrder({ id: 'po-4', orderNo: 'PO004' } as PurchaseOrder)
    await list.handleReject({ id: 'po-5', orderNo: 'PO005' } as PurchaseOrder)
    await list.handleExport()

    expect(approveOrder).toHaveBeenCalledWith('po-3')
    expect(cancelOrder).toHaveBeenCalledWith('po-4')
    expect(rejectOrder).toHaveBeenCalledWith('po-5', 'bad price')
    expect(exportOrders).toHaveBeenCalled()
    expect(downloadBlob).toHaveBeenCalled()
    expect(onSuccess).toHaveBeenCalledWith('purchaseOrder.message.approved')
    expect(onSuccess).toHaveBeenCalledWith('purchaseOrder.message.cancelled')
    expect(onSuccess).toHaveBeenCalledWith('purchaseOrder.message.rejected')
    expect(onSuccess).toHaveBeenCalledWith('purchaseOrder.message.exported')
  })
})
