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

import { getFinanceAgingSummary, getGrossMarginSummary } from '@/api/finance'
import { getSalesQuotes } from '@/api/sales'
import { runMrpPlan } from '@/api/inventory'
import { markNotificationsReadBatch } from '@/api/notification'
import { getProductByBarcode } from '@/api/masterdata'

beforeEach(() => {
  get.mockReset()
  post.mockReset()
})

describe('P2 扩展 API 归一化', () => {
  it('getFinanceAgingSummary 归一化金额与逾期 id', async () => {
    get.mockResolvedValue({
      asOfDate: '2026-07-17',
      receivableTotal: '100.50',
      payableTotal: 20,
      receivableBuckets: [{ code: 'D0_30', label: '0-30', minDaysInclusive: 0, maxDaysInclusive: 30, count: 1, amount: '100.50' }],
      payableBuckets: [],
      overdueReceivables: [{ side: 'RECEIVABLE', id: '9007199254740993', docNo: 'AR1', agingDays: 10, bucketCode: 'D0_30', remainingAmount: '100.50' }],
      overduePayables: []
    })
    const summary = await getFinanceAgingSummary()
    expect(summary.receivableTotal).toBe(100.5)
    expect(summary.receivableBuckets[0].amount).toBe(100.5)
    expect(summary.overdueReceivables[0].id).toBe('9007199254740993')
    expect(typeof summary.overdueReceivables[0].id).toBe('string')
  })

  it('getSalesQuotes 归一化 quote id', async () => {
    get.mockResolvedValue({
      pageNo: 1,
      pageSize: 20,
      total: 1,
      records: [{ id: '9007199254740993', quoteNo: 'SQ1', customerId: '9', quoteDate: '2026-07-17', status: 'DRAFT', totalAmount: 1, lines: [] }]
    })
    const page = await getSalesQuotes({ pageNo: 1, pageSize: 20 })
    expect(page.records[0].id).toBe('9007199254740993')
    expect(typeof page.records[0].customerId).toBe('string')
  })

  it('runMrpPlan 归一化 productId 与数量', async () => {
    post.mockResolvedValue({
      asOfDate: '2026-07-17',
      purchaseCount: 1,
      productionCount: 0,
      purchaseLines: [{ productId: '9007199254740993', suggestionType: 'PURCHASE', demandQty: '2', onHandQty: 0, openSupplyQty: 0, netQty: '2' }],
      productionLines: []
    })
    const result = await runMrpPlan()
    expect(result.purchaseLines[0].productId).toBe('9007199254740993')
    expect(result.purchaseLines[0].netQty).toBe(2)
  })

  it('getGrossMarginSummary 归一化金额', async () => {
    get.mockResolvedValue({
      dateFrom: '2026-01-01',
      dateTo: '2026-12-31',
      salesAmount: '10.00',
      costAmount: '4.00',
      grossMargin: '6.00',
      marginRate: 60,
      lines: []
    })
    const summary = await getGrossMarginSummary('2026-01-01', '2026-12-31')
    expect(summary.salesAmount).toBe(10)
    expect(summary.grossMargin).toBe(6)
  })

  it('markNotificationsReadBatch 提交 recipientIds', async () => {
    post.mockResolvedValue({ updated: 2 })
    const res = await markNotificationsReadBatch(['1', '2'])
    expect(res.updated).toBe(2)
    expect(post).toHaveBeenCalledWith('/system/notifications/read-batch', { recipientIds: ['1', '2'] })
  })

  it('getProductByBarcode 修剪条码并归一化商品 id', async () => {
    get.mockResolvedValue({
      id: '9007199254740993',
      productCode: 'P-BARCODE',
      productName: '扫码商品',
      barcode: '6901234567890',
      status: 'ACTIVE'
    })

    const product = await getProductByBarcode(' 6901234567890 ')

    expect(get).toHaveBeenCalledWith('/masterdata/products/by-barcode', {
      params: { barcode: '6901234567890' }
    })
    expect(product.id).toBe('9007199254740993')
    expect(product.barcode).toBe('6901234567890')
  })
})
