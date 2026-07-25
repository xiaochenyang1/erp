import { describe, it, expect, vi, beforeEach } from 'vitest'

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock('@/utils/request', () => ({
  request: {
    get: (...args: unknown[]) => get(...args),
    post: (...args: unknown[]) => post(...args),
    put: (...args: unknown[]) => put(...args),
    delete: vi.fn()
  }
}))

import {
  addPurchaseInquiryQuote,
  convertPurchaseInquiryToPurchaseOrder,
  getPurchaseInquiry,
  getPurchaseOrder,
  getPurchaseOrders,
  updatePurchaseReceipt
} from '@/api/purchase'

beforeEach(() => {
  get.mockReset()
  post.mockReset()
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

  it('询价转换只调用原子接口并归一化双向来源 id', async () => {
    post.mockResolvedValue({
      id: '9007199254740993',
      orderNo: 'PO202607230001',
      supplierId: '9007199254740994',
      supplierName: '供应商A',
      orderDate: '2026-07-23',
      status: 'DRAFT',
      approvalStatus: 'NOT_SUBMITTED',
      receiptStatus: 'NOT_RECEIVED',
      sourceInquiryId: '9007199254740995',
      sourceInquiryNo: 'RFQ202607230001',
      sourceQuoteId: '9007199254740996',
      totalQuantity: 2,
      totalAmount: 20,
      totalTaxAmount: 2.6,
      lines: [{
        id: '9007199254740997',
        productId: '9007199254740998',
        qty: 2,
        price: 10,
        taxRate: 13,
        amount: 20,
        taxAmount: 2.6,
        sourceInquiryId: '9007199254740995',
        sourceInquiryLineId: '9007199254740999'
      }]
    })

    const order = await convertPurchaseInquiryToPurchaseOrder('9007199254740995')

    expect(post).toHaveBeenCalledTimes(1)
    expect(post).toHaveBeenCalledWith(
      '/purchase/inquiries/9007199254740995/convert-to-purchase-order'
    )
    expect(get).not.toHaveBeenCalled()
    expect(order.sourceInquiryId).toBe('9007199254740995')
    expect(order.sourceQuoteId).toBe('9007199254740996')
    expect(order.lines?.[0].sourceInquiryLineId).toBe('9007199254740999')
  })

  it('询价详情归一化已转换采购订单与操作人 id', async () => {
    get.mockResolvedValue({
      id: '9007199254740993',
      inquiryNo: 'RFQ202607230001',
      inquiryDate: '2026-07-23',
      status: 'CONVERTED',
      selectedSupplierId: '9007199254740994',
      selectedQuoteId: '9007199254740995',
      convertedOrderId: '9007199254740996',
      convertedOrderNo: 'PO202607230001',
      convertedBy: '9007199254740997',
      convertedTime: '2026-07-23T10:00:00',
      lines: [],
      quotes: []
    })

    const inquiry = await getPurchaseInquiry('9007199254740993')

    expect(inquiry.status).toBe('CONVERTED')
    expect(inquiry.convertedOrderId).toBe('9007199254740996')
    expect(inquiry.convertedBy).toBe('9007199254740997')
    expect(inquiry.convertedOrderNo).toBe('PO202607230001')
  })

  it('逐行报价提交 lines 并归一化询价、报价与报价行 Long ID', async () => {
    post.mockResolvedValue({
      id: '9007199254740993',
      inquiryNo: 'RFQ202607230002',
      inquiryDate: '2026-07-23',
      status: 'SUBMITTED',
      lines: [{
        id: '9007199254740994',
        productId: '9007199254740995',
        qty: '2'
      }],
      quotes: [{
        id: '9007199254740996',
        supplierId: '9007199254740997',
        unitPrice: null,
        taxRate: null,
        status: 'PENDING',
        lines: [{
          id: '9007199254740998',
          inquiryLineId: '9007199254740994',
          unitPrice: '12.5',
          taxRate: '13'
        }]
      }]
    })

    const inquiry = await addPurchaseInquiryQuote('9007199254740993', {
      supplierId: '9007199254740997',
      lines: [{
        inquiryLineId: '9007199254740994',
        unitPrice: 12.5,
        taxRate: 13
      }],
      remark: '逐行报价'
    })

    expect(post).toHaveBeenCalledWith('/purchase/inquiries/9007199254740993/quotes', {
      supplierId: '9007199254740997',
      lines: [{
        inquiryLineId: '9007199254740994',
        unitPrice: 12.5,
        taxRate: 13
      }],
      remark: '逐行报价'
    })
    expect(inquiry.id).toBe('9007199254740993')
    expect(inquiry.lines[0].id).toBe('9007199254740994')
    expect(inquiry.lines[0].productId).toBe('9007199254740995')
    expect(inquiry.quotes[0].id).toBe('9007199254740996')
    expect(inquiry.quotes[0].supplierId).toBe('9007199254740997')
    expect(inquiry.quotes[0].lines?.[0].id).toBe('9007199254740998')
    expect(inquiry.quotes[0].lines?.[0].inquiryLineId).toBe('9007199254740994')
    expect(inquiry.quotes[0].lines?.[0].unitPrice).toBe(12.5)
    expect(inquiry.quotes[0].lines?.[0].taxRate).toBe(13)
  })
})
