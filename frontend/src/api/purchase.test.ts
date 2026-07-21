import { describe, it, expect, vi, beforeEach } from 'vitest'

const get = vi.fn()
const put = vi.fn()
vi.mock('@/utils/request', () => ({
  request: {
    get: (...args: unknown[]) => get(...args),
    post: vi.fn(),
    put: (...args: unknown[]) => put(...args),
    delete: vi.fn()
  }
}))

import { getPurchaseOrder, getPurchaseOrders, updatePurchaseReceipt } from '@/api/purchase'

beforeEach(() => {
  get.mockReset()
  put.mockReset()
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

  it('updatePurchaseReceipt 提交界面当前 quantity 而不是旧 qty', async () => {
    put.mockResolvedValue({
      id: '1',
      receiptNo: 'PR1',
      orderId: '2',
      supplierName: '供应商',
      warehouseId: '3',
      warehouseName: '入库仓',
      receiptDate: '2026-07-20',
      status: 'DRAFT',
      items: []
    })

    await updatePurchaseReceipt('1', {
      orderId: '2',
      warehouseId: '3',
      receiptDate: '2026-07-20',
      items: [{
        orderLineId: '4',
        productId: '5',
        quantity: 2,
        qty: 9
      }]
    })

    expect(put).toHaveBeenCalledWith('/purchase/receipts/1', {
      orderId: '2',
      warehouseId: '3',
      receiptDate: '2026-07-20',
      remark: undefined,
      lines: [{ orderLineId: '4', qty: 2, remark: undefined }]
    })
  })
})
