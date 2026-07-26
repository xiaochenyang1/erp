import { describe, expect, it, vi } from 'vitest'

import type { PurchaseInquiry, PurchaseInquiryQuote } from '@/api/purchase'
import { usePurchaseInquiryQuotes } from './usePurchaseInquiryQuotes'

const t = (key: string) => key

describe('purchase inquiry quotes', () => {
  it('adds quote lines and selects a winner', async () => {
    const loadOptions = vi.fn(async () => {})
    const addQuote = vi.fn(async () => ({}))
    const selectQuote = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const onCompleted = vi.fn()
    const getInquiry = vi.fn(async () => ({
      id: 'inq-1',
      lines: [{ id: 'l1', productId: 'p1', qty: 3 }],
      quotes: [{ id: 'q1', status: 'PENDING' }]
    } as PurchaseInquiry))

    const quotes = usePurchaseInquiryQuotes(t, {
      loadOptions,
      getInquiry,
      addQuote,
      selectQuote,
      onSuccess,
      onCompleted
    })

    await quotes.handleAddQuote({ id: 'inq-1' } as PurchaseInquiry)
    expect(quotes.quoteVisible.value).toBe(true)
    expect(quotes.quoteForm.lines).toHaveLength(1)
    quotes.quoteForm.supplierId = 's1'
    quotes.quoteForm.lines[0].unitPrice = 12.5
    await quotes.confirmQuote()
    expect(addQuote).toHaveBeenCalledWith('inq-1', expect.objectContaining({
      supplierId: 's1',
      lines: [expect.objectContaining({ inquiryLineId: 'l1', unitPrice: 12.5, taxRate: 13 })]
    }))
    expect(onSuccess).toHaveBeenCalledWith('purchaseInquiryOps.message.quoteAdded')

    await quotes.handleSelectQuote({ id: 'inq-1' } as PurchaseInquiry)
    expect(quotes.selectVisible.value).toBe(true)
    quotes.onSelectQuoteRow({ id: 'q1' } as PurchaseInquiryQuote)
    await quotes.confirmSelectQuote()
    expect(selectQuote).toHaveBeenCalledWith('inq-1', 'q1')
    expect(onSuccess).toHaveBeenCalledWith('purchaseInquiryOps.message.winnerSelected')
    expect(onCompleted).toHaveBeenCalledTimes(2)
  })

  it('blocks quote when inquiry line ids are missing', async () => {
    const onError = vi.fn()
    const quotes = usePurchaseInquiryQuotes(t, {
      loadOptions: vi.fn(async () => {}),
      getInquiry: vi.fn(async () => ({
        id: 'inq-2',
        lines: [{ productId: 'p1', qty: 1 }]
      } as PurchaseInquiry)),
      addQuote: vi.fn(async () => ({})),
      selectQuote: vi.fn(async () => ({})),
      onError
    })

    await quotes.handleAddQuote({ id: 'inq-2' } as PurchaseInquiry)
    expect(onError).toHaveBeenCalledWith('purchaseInquiryOps.validation.lineIdMissing')
    expect(quotes.quoteVisible.value).toBe(false)
  })
})
