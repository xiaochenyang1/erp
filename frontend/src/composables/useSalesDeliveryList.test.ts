import { describe, expect, it, vi } from 'vitest'

import type { SalesDelivery } from '@/api/sales'
import { useSalesDeliveryList } from './useSalesDeliveryList'

const t = (key: string) => key

describe('sales delivery list', () => {
  const createList = (overrides: Partial<Parameters<typeof useSalesDeliveryList>[1]> = {}) =>
    useSalesDeliveryList(t, {
      getDeliveries: vi.fn(async () => ({
        records: [{ id: 'sd-1', deliveryNo: 'SD001', status: 'DRAFT' }],
        total: 1
      } as any)),
      getDelivery: vi.fn(async () => ({
        id: 'sd-1',
        deliveryNo: 'SD001'
      } as SalesDelivery)),
      cancelDelivery: vi.fn(async () => ({})),
      postDelivery: vi.fn(async () => ({})),
      updateLogistics: vi.fn(async () => ({})),
      getCustomers: vi.fn(async () => ({ records: [{ id: 'c1' }], total: 1 } as any)),
      getWarehouses: vi.fn(async () => ({ records: [{ id: 'w1' }], total: 1 } as any)),
      getOrders: vi.fn(async () => ({ records: [{ id: 'so1' }], total: 1 } as any)),
      getLocations: vi.fn(async () => ({ records: [{ id: 'l1' }], total: 1 } as any)),
      printDelivery: vi.fn(),
      confirm: vi.fn(async () => true),
      onSuccess: vi.fn(),
      onError: vi.fn(),
      onInfo: vi.fn(),
      ...overrides
    })

  it('loads deliveries and supports query reset', async () => {
    const list = createList()
    await list.loadData()
    expect(list.tableData.value).toHaveLength(1)

    list.dateRange.value = ['2026-07-01', '2026-07-26']
    list.handleQuery()
    await Promise.resolve()
    expect(list.queryParams.startDate).toBe('2026-07-01')

    list.handleReset()
    await Promise.resolve()
    expect(list.queryParams.deliveryNo).toBe('')
    expect(list.dateRange.value).toBeNull()
  })

  it('loads options and prints/cancels/posts deliveries', async () => {
    const printDelivery = vi.fn()
    const cancelDelivery = vi.fn(async () => ({}))
    const postDelivery = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({ printDelivery, cancelDelivery, postDelivery, onSuccess })

    await list.loadOptions()
    expect(list.customers.value).toHaveLength(1)
    expect(list.warehouses.value).toHaveLength(1)
    expect(list.orders.value).toHaveLength(1)

    await list.handlePrint({ id: 'sd-1' } as SalesDelivery)
    await list.handleCancel({ id: 'sd-1' } as SalesDelivery)
    await list.handlePost({ id: 'sd-1' } as SalesDelivery)

    expect(printDelivery).toHaveBeenCalled()
    expect(cancelDelivery).toHaveBeenCalledWith('sd-1')
    expect(postDelivery).toHaveBeenCalledWith('sd-1')
    expect(onSuccess).toHaveBeenCalledWith('salesDelivery.message.cancelled')
    expect(onSuccess).toHaveBeenCalledWith('salesDelivery.message.posted')
  })

  it('advances logistics status until delivered', async () => {
    const updateLogistics = vi.fn(async () => ({}))
    const onInfo = vi.fn()
    const onSuccess = vi.fn()
    const list = createList({ updateLogistics, onInfo, onSuccess })

    await list.advanceLogistics({
      id: 'sd-2',
      logisticsStatus: 'PENDING_SHIP',
      carrierName: 'SF',
      trackingNo: 'T1'
    } as SalesDelivery)
    expect(updateLogistics).toHaveBeenCalledWith('sd-2', expect.objectContaining({
      logisticsStatus: 'PICKED_UP'
    }))
    expect(onSuccess).toHaveBeenCalledWith('salesDelivery.message.logisticsUpdated')

    await list.advanceLogistics({
      id: 'sd-2',
      logisticsStatus: 'DELIVERED'
    } as SalesDelivery)
    expect(onInfo).toHaveBeenCalledWith('salesDelivery.message.logisticsDone')
  })
})
