import { describe, expect, it, vi } from 'vitest'

import type { ProductionOrder } from '@/api/production'
import { useProductionOrderList } from './useProductionOrderList'

const t = (key: string) => key

describe('production order list', () => {
  it('loads option catalogs for products, warehouses, and boms', async () => {
    const list = useProductionOrderList(t, {
      getOrders: vi.fn(),
      getOrder: vi.fn(),
      getProducts: vi.fn(async () => ({ records: [{ id: 'p1' }], total: 1 } as any)),
      getWarehouses: vi.fn(async () => ({ records: [{ id: 'w1' }], total: 1 } as any)),
      getBoms: vi.fn(async () => ({ records: [{ id: 'b1' }], total: 1 } as any)),
      getLocations: vi.fn(),
      printOrder: vi.fn()
    })

    await list.loadOptions()

    expect(list.productOptions.value).toEqual([{ id: 'p1' }])
    expect(list.warehouseOptions.value).toEqual([{ id: 'w1' }])
    expect(list.allBomOptions.value).toEqual([{ id: 'b1' }])
  })

  it('loads paginated orders and supports query reset', async () => {
    const getOrders = vi.fn(async () => ({
      records: [{ id: 'mo-1', orderNo: 'MO001' }],
      total: 1
    } as any))
    const list = useProductionOrderList(t, {
      getOrders,
      getOrder: vi.fn(),
      getProducts: vi.fn(),
      getWarehouses: vi.fn(),
      getBoms: vi.fn(),
      getLocations: vi.fn(),
      printOrder: vi.fn()
    })
    list.queryForm.orderNo = 'MO001'
    list.pagination.page = 3

    list.handleQuery()
    await Promise.resolve()
    expect(list.pagination.page).toBe(1)
    expect(getOrders).toHaveBeenCalledWith(expect.objectContaining({
      orderNo: 'MO001',
      page: 1,
      size: 20
    }))
    expect(list.tableData.value).toEqual([{ id: 'mo-1', orderNo: 'MO001' }])
    expect(list.pagination.total).toBe(1)

    list.handleReset()
    await Promise.resolve()
    expect(list.queryForm.orderNo).toBe('')
    expect(getOrders).toHaveBeenCalledTimes(2)
  })

  it('loads finished and material locations by warehouse', async () => {
    const getLocations = vi.fn(async ({ warehouseId }) => ({
      records: [{ id: `loc-${warehouseId}` }],
      total: 1
    } as any))
    const list = useProductionOrderList(t, {
      getOrders: vi.fn(),
      getOrder: vi.fn(),
      getProducts: vi.fn(),
      getWarehouses: vi.fn(),
      getBoms: vi.fn(),
      getLocations,
      printOrder: vi.fn()
    })

    await list.loadFinishedLocations('fw-1')
    await list.loadMaterialLocations('mw-1')

    expect(list.finishedLocations.value).toEqual([{ id: 'loc-fw-1' }])
    expect(list.materialLocations.value).toEqual([{ id: 'loc-mw-1' }])
  })

  it('opens detail dialog and prints order details', async () => {
    const order = {
      id: 'mo-9',
      orderNo: 'MO009',
      planQuantity: 5
    } as ProductionOrder
    const getOrder = vi.fn(async () => order)
    const printOrder = vi.fn()
    const list = useProductionOrderList(t, {
      getOrders: vi.fn(),
      getOrder,
      getProducts: vi.fn(),
      getWarehouses: vi.fn(),
      getBoms: vi.fn(),
      getLocations: vi.fn(),
      printOrder
    })

    await list.handleView({ id: 'mo-9' } as ProductionOrder)
    expect(list.viewDialogVisible.value).toBe(true)
    expect(list.viewData.value).toEqual(order)

    await list.handlePrint({ id: 'mo-9' } as ProductionOrder)
    expect(printOrder).toHaveBeenCalledWith(order)
  })
})
