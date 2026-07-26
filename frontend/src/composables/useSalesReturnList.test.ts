import { describe, expect, it, vi } from 'vitest'

import type { SalesReturn } from '@/api/sales'
import { useSalesReturnList } from './useSalesReturnList'

const t = (key: string) => key

describe('sales return list', () => {
  const createList = (overrides: Partial<Parameters<typeof useSalesReturnList>[1]> = {}) =>
    useSalesReturnList(t, {
      getReturns: vi.fn(async () => ({
        records: [{ id: 'sr-1', returnNo: 'SRT001', status: 'DRAFT' }],
        total: 1
      } as any)),
      getReturn: vi.fn(async () => ({ id: 'sr-1', returnNo: 'SRT001' } as SalesReturn)),
      postReturn: vi.fn(async () => ({})),
      cancelReturn: vi.fn(async () => ({})),
      getDeliveries: vi.fn(async () => ({ records: [{ id: 'd1' }], total: 1 } as any)),
      getProducts: vi.fn(async () => ({ records: [{ id: 'p1' }], total: 1 } as any)),
      getLocations: vi.fn(async () => ({ records: [{ id: 'l1' }], total: 1 } as any)),
      printReturn: vi.fn(),
      confirm: vi.fn(async () => true),
      onSuccess: vi.fn(),
      onError: vi.fn(),
      ...overrides
    })

  it('loads returns/options and supports query reset', async () => {
    const list = createList()
    await list.loadData()
    expect(list.tableData.value).toHaveLength(1)

    await list.loadOptions()
    expect(list.deliveries.value).toHaveLength(1)
    expect(list.products.value).toHaveLength(1)

    list.dateRange.value = ['2026-07-01', '2026-07-26']
    list.handleQuery()
    await Promise.resolve()
    expect(list.queryParams.startDate).toBe('2026-07-01')

    list.handleReset()
    await Promise.resolve()
    expect(list.queryParams.returnNo).toBe('')
    expect(list.dateRange.value).toBeNull()
  })

  it('prints and posts/cancels returns', async () => {
    const printReturn = vi.fn()
    const postReturn = vi.fn(async () => ({}))
    const cancelReturn = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({ printReturn, postReturn, cancelReturn, onSuccess })

    await list.handlePrint({ id: 'sr-1' } as SalesReturn)
    await list.handlePost({ id: 'sr-1' } as SalesReturn)
    await list.handleCancel({ id: 'sr-1' } as SalesReturn)

    expect(printReturn).toHaveBeenCalled()
    expect(postReturn).toHaveBeenCalledWith('sr-1')
    expect(cancelReturn).toHaveBeenCalledWith('sr-1')
    expect(onSuccess).toHaveBeenCalledWith('salesReturnOps.message.success')
  })
})
