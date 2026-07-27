import { describe, expect, it, vi } from 'vitest'

import type { SalesQuote } from '@/api/sales'
import { useSalesQuoteForm } from './useSalesQuoteForm'

const t = (key: string) => key

const quote = (overrides: Partial<SalesQuote> = {}): SalesQuote => ({
  id: '1',
  quoteNo: 'Q1',
  customerId: 'c1',
  customerName: 'Acme',
  quoteDate: '2026-07-01',
  status: 'DRAFT',
  totalAmount: 100,
  lines: [
    { productId: 'p1', qty: 2, price: 10, taxRate: 0.13, amount: 20, taxAmount: 2.6 }
  ],
  ...overrides
})

const createForm = (overrides: Partial<Parameters<typeof useSalesQuoteForm>[1]> = {}) =>
  useSalesQuoteForm(t, {
    getSalesQuote: vi.fn(async () => quote()),
    createSalesQuote: vi.fn(async () => ({})),
    updateSalesQuote: vi.fn(async () => ({})),
    getCustomers: vi.fn(async () => ({
      records: [{ id: 'c1', customerName: 'Acme' } as any],
      total: 1
    })),
    getProducts: vi.fn(async () => ({
      records: [{ id: 'p1', productCode: 'SKU', productName: 'Item' } as any],
      total: 1
    })),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    onSaved: vi.fn(),
    ...overrides
  })

describe('sales quote form', () => {
  it('loads options and opens create with a default line', async () => {
    const form = createForm()
    expect(await form.loadOptions()).toBe(true)
    expect(form.customers.value).toHaveLength(1)
    expect(form.products.value).toHaveLength(1)

    await form.openCreate()
    expect(form.formVisible.value).toBe(true)
    expect(form.editingId.value).toBeNull()
    expect(form.form.quoteDate).toBeTruthy()
    expect(form.form.lines).toHaveLength(1)
    form.addLine()
    expect(form.form.lines).toHaveLength(2)
    form.removeLine(0)
    expect(form.form.lines).toHaveLength(1)
  })

  it('validates and creates or updates quotes', async () => {
    const createSalesQuote = vi.fn(async () => ({}))
    const updateSalesQuote = vi.fn(async () => ({}))
    const onWarning = vi.fn()
    const onSuccess = vi.fn()
    const onSaved = vi.fn()
    const form = createForm({ createSalesQuote, updateSalesQuote, onWarning, onSuccess, onSaved })

    await form.openCreate()
    form.form.customerId = ''
    expect(form.validateForm()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('salesQuote.message.completeForm')

    form.form.customerId = 'c1'
    form.form.quoteDate = '2026-07-01'
    form.form.lines = [{ productId: 'p1', qty: 1, price: 9, taxRate: 0.13 }]
    expect(await form.save()).toBe(true)
    expect(createSalesQuote).toHaveBeenCalledWith({
      customerId: 'c1',
      quoteDate: '2026-07-01',
      validUntil: undefined,
      remark: undefined,
      lines: [{ productId: 'p1', qty: 1, price: 9, taxRate: 0.13 }]
    })
    expect(onSuccess).toHaveBeenCalledWith('salesQuote.message.saved')
    expect(onSaved).toHaveBeenCalled()

    expect(await form.openEdit({ id: '1' })).toBe(true)
    expect(form.editingId.value).toBe('1')
    expect(form.form.customerId).toBe('c1')
    form.form.remark = 'note'
    expect(await form.save()).toBe(true)
    expect(updateSalesQuote).toHaveBeenCalledWith(
      '1',
      expect.objectContaining({ remark: 'note' })
    )
  })

  it('surfaces option and detail load failures', async () => {
    const onError = vi.fn()
    const form = createForm({
      getCustomers: vi.fn(async () => {
        throw new Error('boom')
      }),
      getSalesQuote: vi.fn(async () => {
        throw new Error('missing')
      }),
      onError
    })
    expect(await form.loadOptions()).toBe(false)
    expect(onError).toHaveBeenCalledWith('salesQuote.message.optionsLoadFailed')
    expect(await form.openEdit({ id: '9' })).toBe(false)
    expect(onError).toHaveBeenCalledWith('salesQuote.message.detailLoadFailed')
  })
})
