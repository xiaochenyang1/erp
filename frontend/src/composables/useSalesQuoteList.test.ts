import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type { SalesQuote } from '@/api/sales'
import { useSalesQuoteList } from './useSalesQuoteList'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

const quote = (overrides: Partial<SalesQuote> = {}): SalesQuote => ({
  id: '1',
  quoteNo: 'Q1',
  customerId: 'c1',
  customerName: 'Acme',
  quoteDate: '2026-07-01',
  status: 'DRAFT',
  totalAmount: 100,
  lines: [{ productId: 'p1', qty: 1, price: 10, taxRate: 0.13 }],
  ...overrides
})

const createList = (overrides: Partial<Parameters<typeof useSalesQuoteList>[1]> = {}) =>
  useSalesQuoteList(t, {
    getSalesQuotes: vi.fn(async () => ({
      records: [quote()],
      total: 1,
      pageNo: 1,
      pageSize: 20
    })),
    getSalesQuote: vi.fn(async () => quote({ totalAmount: 120 })),
    confirmSalesQuote: vi.fn(async () => ({})),
    cancelSalesQuote: vi.fn(async () => ({})),
    convertSalesQuoteToOrder: vi.fn(async () => ({ orderNo: 'SO9' })),
    getWarehouses: vi.fn(async () => ({
      records: [{ id: 'w1', warehouseName: 'Main' } as any],
      total: 1,
      pageNo: 1,
      pageSize: 200
    })),
    getProducts: vi.fn(async () => ({
      records: [{ id: 'p1', productCode: 'SKU', productName: 'Item' } as any],
      total: 1,
      pageNo: 1,
      pageSize: 200
    })),
    printSalesQuote: vi.fn(),
    detailContent: (detail) => `detail:${detail.quoteNo}`,
    confirm: vi.fn(async () => true),
    alert: vi.fn(async () => true),
    products: ref([]),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  })

describe('sales quote list', () => {
  it('loads, pages and resets filters', async () => {
    const list = createList()
    expect(await list.loadData()).toBe(true)
    expect(list.rows.value).toHaveLength(1)

    await list.handleSizeChange(50)
    expect(list.query.pageSize).toBe(50)
    expect(list.query.pageNo).toBe(1)
    await list.handlePageChange(2)
    expect(list.query.pageNo).toBe(2)

    list.query.keyword = 'Q'
    list.query.status = 'DRAFT'
    await list.handleReset()
    expect(list.query.keyword).toBe('')
    expect(list.query.status).toBe('')
    expect(list.query.pageNo).toBe(1)
  })

  it('views, prints, confirms and cancels quotes', async () => {
    const alert = vi.fn(async () => true)
    const printSalesQuote = vi.fn()
    const confirmSalesQuote = vi.fn(async () => ({}))
    const cancelSalesQuote = vi.fn(async () => ({}))
    const confirm = vi.fn(async () => true)
    const onSuccess = vi.fn()
    const list = createList({
      alert,
      printSalesQuote,
      confirmSalesQuote,
      cancelSalesQuote,
      confirm,
      onSuccess
    })

    expect(await list.openView(quote())).toBe(true)
    expect(alert).toHaveBeenCalledWith('detail:Q1', 'salesQuote.detailTitle')

    expect(await list.handlePrint(quote())).toBe(true)
    expect(printSalesQuote).toHaveBeenCalledWith(
      expect.objectContaining({
        totalAmount: 120,
        lines: [expect.objectContaining({ productCode: 'SKU', productName: 'Item' })]
      })
    )

    expect(await list.confirmQuote(quote())).toBe(true)
    expect(confirmSalesQuote).toHaveBeenCalledWith('1')
    expect(onSuccess).toHaveBeenCalledWith('salesQuote.message.confirmed')

    expect(await list.cancelQuote(quote({ quoteNo: 'Q9' }))).toBe(true)
    expect(confirm).toHaveBeenCalled()
    expect(cancelSalesQuote).toHaveBeenCalledWith('1')
    expect(onSuccess).toHaveBeenCalledWith('salesQuote.message.cancelled')
  })

  it('converts confirmed quotes after warehouse selection', async () => {
    const convertSalesQuoteToOrder = vi.fn(async () => ({ orderNo: 'SO42' }))
    const onWarning = vi.fn()
    const onSuccess = vi.fn()
    const list = createList({ convertSalesQuoteToOrder, onWarning, onSuccess })

    await list.openConvert(quote({ id: '7', status: 'CONFIRMED' }))
    expect(list.convertVisible.value).toBe(true)
    expect(list.warehouses.value).toHaveLength(1)

    expect(await list.doConvert()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('salesQuote.message.selectWarehouse')

    list.convertWarehouseId.value = 'w1'
    expect(await list.doConvert()).toBe(true)
    expect(convertSalesQuoteToOrder).toHaveBeenCalledWith('7', 'w1')
    expect(onSuccess).toHaveBeenCalledWith(
      'salesQuote.message.converted:{"orderNo":"SO42"}'
    )
    expect(list.convertVisible.value).toBe(false)
  })

  it('aborts cancel when the user dismisses the confirm dialog', async () => {
    const cancelSalesQuote = vi.fn(async () => ({}))
    const list = createList({
      cancelSalesQuote,
      confirm: vi.fn(async () => {
        throw new Error('cancel')
      })
    })
    expect(await list.cancelQuote(quote())).toBe(false)
    expect(cancelSalesQuote).not.toHaveBeenCalled()
  })
})
