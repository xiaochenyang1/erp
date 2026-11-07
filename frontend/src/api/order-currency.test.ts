import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createSalesOrder, updateSalesOrder, previewSalesOrderCredit } from './sales'
import { createPurchaseOrder, updatePurchaseOrder } from './purchase'

const { post, put } = vi.hoisted(() => ({ post: vi.fn(), put: vi.fn() }))
vi.mock('@/utils/request', () => ({ request: { post, put } }))
const sales = { customerId: '1', orderDate: '2026-09-23', items: [] }
const purchase = { supplierId: '2', orderDate: '2026-09-23', items: [] }
beforeEach(() => {
  vi.resetAllMocks()
  post.mockResolvedValue({ id: '3', customerId: '1', supplierId: '2', items: [] })
  put.mockResolvedValue({ id: '3', customerId: '1', supplierId: '2', items: [] })
})

describe('order currency API contract', () => {
  it('sends the saved USD currency and rate when updating a sales order', async () => {
    await updateSalesOrder('3', { ...sales, currencyCode: 'USD', exchangeRate: 7 })
    expect(put).toHaveBeenCalledWith('/sales/orders/3', expect.objectContaining({ currencyCode: 'USD', exchangeRate: 7 }))
  })

  it('sends the saved EUR currency and rate when updating a purchase order', async () => {
    await updatePurchaseOrder('3', { ...purchase, currencyCode: 'EUR', exchangeRate: 8.25 })
    expect(put).toHaveBeenCalledWith('/purchase/orders/3', expect.objectContaining({ currencyCode: 'EUR', exchangeRate: 8.25 }))
  })

  it('leaves omitted currency metadata to the account-book resolver for both order types', async () => {
    await createSalesOrder(sales)
    await createPurchaseOrder(purchase)
    for (const [, payload] of post.mock.calls) {
      expect(payload.currencyCode).toBeUndefined()
      expect(payload.exchangeRate).toBeUndefined()
    }
  })

  it('does not invent rate one when a foreign currency is supplied without a rate', async () => {
    await createSalesOrder({ ...sales, currencyCode: 'USD' })
    await createPurchaseOrder({ ...purchase, currencyCode: 'EUR' })
    expect(post.mock.calls.map(([, body]) => body.exchangeRate)).toEqual([undefined, undefined])
  })

  it('sends the order date and full currency context to credit preview', async () => {
    await previewSalesOrderCredit('1', [], { orderDate: '2026-09-23', currencyCode: 'USD', exchangeRate: 7 })
    expect(post).toHaveBeenCalledWith('/sales/orders/credit-preview', {
      customerId: '1', lines: [], orderDate: '2026-09-23', currencyCode: 'USD', exchangeRate: 7
    })
  })
})
