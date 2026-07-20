import { describe, it, expect, vi, beforeEach } from 'vitest'

const get = vi.fn()
const post = vi.fn()
vi.mock('@/utils/request', () => ({
  request: {
    get: (...args: unknown[]) => get(...args),
    post: (...args: unknown[]) => post(...args),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

import { getSalesOrder, getSalesOrders, getSalesDelivery } from '@/api/sales'

beforeEach(() => {
  get.mockReset()
  post.mockReset()
})

describe('sales API 归一化', () => {
  it('getSalesOrder 把雪花 id/customerId 归一化为字符串', async () => {
    get.mockResolvedValue({
      id: '9007199254740993',
      orderNo: 'SO1',
      customerId: '9007199254740995',
      customerName: 'C',
      orderDate: '2026-07-17',
      totalAmount: 10,
      status: 'DRAFT',
      items: [{ productId: '9007199254740997', quantity: 1, price: 10, amount: 10 }]
    })
    const order = await getSalesOrder('9007199254740993')
    expect(order.id).toBe('9007199254740993')
    expect(typeof order.id).toBe('string')
    expect(order.customerId).toBe('9007199254740995')
    expect(order.items[0].productId).toBe('9007199254740997')
  })

  it('getSalesOrders 分页 records 归一化 id', async () => {
    get.mockResolvedValue({
      records: [{
        id: '9007199254740993',
        orderNo: 'SO1',
        customerId: '1',
        customerName: 'C',
        orderDate: '2026-07-17',
        totalAmount: 1,
        status: 'DRAFT',
        items: []
      }],
      total: 1,
      pageNo: 1,
      pageSize: 20
    })
    const page = await getSalesOrders({ pageNo: 1, pageSize: 20 })
    expect(page.records[0].id).toBe('9007199254740993')
  })

  it('getSalesDelivery 归一化 orderId/warehouseId 与明细 productId', async () => {
    get.mockResolvedValue({
      id: '9007199254740993',
      deliveryNo: 'SD1',
      orderId: '9007199254740995',
      warehouseId: '9007199254740997',
      deliveryDate: '2026-07-17',
      status: 'DRAFT',
      lines: [{ id: '9', productId: '9007199254741001', qty: 2 }]
    })
    const d = await getSalesDelivery('9007199254740993')
    expect(d.id).toBe('9007199254740993')
    expect(d.orderId).toBe('9007199254740995')
    expect(d.warehouseId).toBe('9007199254740997')
    expect(d.items[0].productId).toBe('9007199254741001')
  })
})
