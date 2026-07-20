import { describe, it, expect, vi, beforeEach } from 'vitest'

const get = vi.fn()
vi.mock('@/utils/request', () => ({
  request: {
    get: (...args: unknown[]) => get(...args),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

import { getPurchaseOrder, getPurchaseOrders } from '@/api/purchase'

beforeEach(() => {
  get.mockReset()
})

describe('purchase API 归一化', () => {
  it('getPurchaseOrder 归一化雪花 id', async () => {
    get.mockResolvedValue({
      id: '9007199254740993',
      orderNo: 'PO1',
      supplierId: '9007199254740995',
      warehouseId: '9007199254740997',
      orderDate: '2026-07-17',
      totalAmount: 1,
      status: 'DRAFT',
      items: [{ productId: '9007199254741001', quantity: 1, price: 1, amount: 1 }]
    })
    const order = await getPurchaseOrder('9007199254740993')
    expect(String(order.id)).toBe('9007199254740993')
    expect(String(order.supplierId)).toBe('9007199254740995')
  })

  it('getPurchaseOrders 分页 id 为字符串', async () => {
    get.mockResolvedValue({
      records: [{
        id: '9007199254740993',
        orderNo: 'PO1',
        supplierId: '1',
        orderDate: '2026-07-17',
        totalAmount: 1,
        status: 'DRAFT',
        items: []
      }],
      total: 1,
      pageNo: 1,
      pageSize: 20
    })
    const page = await getPurchaseOrders({ pageNo: 1, pageSize: 20 })
    expect(String(page.records[0].id)).toBe('9007199254740993')
  })
})
