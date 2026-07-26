import { describe, expect, it, vi } from 'vitest'

import type { SalesOrder } from '@/api/sales'
import { useSalesOrderList } from './useSalesOrderList'

const t = (key: string) => key

describe('sales order list', () => {
  const createList = (overrides: Partial<Parameters<typeof useSalesOrderList>[1]> = {}) =>
    useSalesOrderList(t, {
      getOrders: vi.fn(async () => ({
        records: [{ id: 'so-1', orderNo: 'SO001', status: 'DRAFT' }],
        total: 1
      } as any)),
      getOrder: vi.fn(async () => ({ id: 'so-1', orderNo: 'SO001' } as SalesOrder)),
      submitOrder: vi.fn(async () => ({})),
      approveOrder: vi.fn(async () => ({})),
      unapproveOrder: vi.fn(async () => ({})),
      rejectOrder: vi.fn(async () => ({})),
      cancelOrder: vi.fn(async () => ({})),
      getCustomers: vi.fn(async () => ({ records: [{ id: 'c1' }], total: 1 } as any)),
      getWarehouses: vi.fn(async () => ({ records: [{ id: 'w1' }], total: 1 } as any)),
      getProducts: vi.fn(async () => ({ records: [{ id: 'p1' }], total: 1 } as any)),
      printOrder: vi.fn(),
      confirm: vi.fn(async () => true),
      prompt: vi.fn(async () => ({ value: 'price issue' })),
      onSuccess: vi.fn(),
      onError: vi.fn(),
      ...overrides
    })

  it('loads orders/options and supports query reset', async () => {
    const list = createList()
    await list.loadData()
    expect(list.tableData.value).toHaveLength(1)

    await list.loadOptions()
    expect(list.customers.value).toHaveLength(1)
    expect(list.products.value).toHaveLength(1)

    list.queryParams.keyword = 'SO'
    list.handleQuery()
    await Promise.resolve()
    expect(list.queryParams.pageNo).toBe(1)

    list.handleReset()
    await Promise.resolve()
    expect(list.queryParams.keyword).toBe('')
  })

  it('prints and runs submit/approve/cancel/reject actions', async () => {
    const printOrder = vi.fn()
    const submitOrder = vi.fn(async () => ({}))
    const approveOrder = vi.fn(async () => ({}))
    const cancelOrder = vi.fn(async () => ({}))
    const rejectOrder = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({
      printOrder,
      submitOrder,
      approveOrder,
      cancelOrder,
      rejectOrder,
      onSuccess
    })

    await list.handlePrint({ id: 'so-1' } as SalesOrder)
    await list.handleSubmitOrder({ id: 'so-1', orderNo: 'SO001' } as SalesOrder)
    await list.handleApprove({ id: 'so-1', orderNo: 'SO001' } as SalesOrder)
    await list.handleCancel({ id: 'so-1', orderNo: 'SO001' } as SalesOrder)
    await list.handleReject({ id: 'so-1', orderNo: 'SO001' } as SalesOrder)

    expect(printOrder).toHaveBeenCalled()
    expect(submitOrder).toHaveBeenCalledWith('so-1')
    expect(approveOrder).toHaveBeenCalledWith('so-1')
    expect(cancelOrder).toHaveBeenCalledWith('so-1')
    expect(rejectOrder).toHaveBeenCalledWith('so-1', 'price issue')
    expect(onSuccess).toHaveBeenCalledWith('salesOrder.message.submitted')
    expect(onSuccess).toHaveBeenCalledWith('salesOrder.message.approved')
    expect(onSuccess).toHaveBeenCalledWith('salesOrder.message.cancelled')
    expect(onSuccess).toHaveBeenCalledWith('salesOrder.message.rejected')
  })
})
