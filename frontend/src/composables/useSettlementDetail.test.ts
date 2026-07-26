import { describe, expect, it, vi } from 'vitest'

import type { Payment, Receipt } from '@/api/finance'
import { useSettlementDetail } from './useSettlementDetail'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.no != null ? `${key}:${params.no}` : key

const receipt = {
  id: 'rc1',
  receiptNo: 'RC001',
  customerId: 'c1',
  receiptDate: '2026-07-20',
  receiptAmount: 500,
  allocations: [{ receivableId: 'r1', receivableNo: 'AR001', allocatedAmount: 500 }]
} as unknown as Receipt

const payment = {
  id: 'pm1',
  paymentNo: 'PM001',
  supplierId: 's1',
  paymentDate: '2026-07-21',
  paymentAmount: 300,
  allocations: [{ payableId: 'p1', payableNo: 'AP001', allocatedAmount: 300 }]
} as unknown as Payment

const createDetail = (overrides: Partial<Parameters<typeof useSettlementDetail>[1]> = {}) =>
  useSettlementDetail(t, {
    getReceipt: vi.fn(async () => receipt),
    getPayment: vi.fn(async () => payment),
    buildReceiptItems: (doc) => [{ label: 'receipt', value: doc.receiptNo }],
    buildPaymentItems: (doc) => [{ label: 'payment', value: doc.paymentNo }],
    onError: vi.fn(),
    ...overrides
  })

describe('settlement detail', () => {
  it('loads a receipt with its allocations and side-specific rows', async () => {
    const detail = createDetail()

    await detail.viewReceipt({ id: 'rc1', receiptNo: 'RC001' } as Receipt)

    expect(detail.detailVisible.value).toBe(true)
    expect(detail.detailTitle.value).toBe('financeReportPages.payments.receiptTitle:RC001')
    expect(detail.selectedReceipt.value?.receiptNo).toBe('RC001')
    expect(detail.receiptAllocations.value).toHaveLength(1)
    expect(detail.detailItems.value).toEqual([{ label: 'receipt', value: 'RC001' }])
    expect(detail.detailLoading.value).toBe(false)
  })

  it('loads a payment through the same dialog state', async () => {
    const detail = createDetail()

    await detail.viewPayment({ id: 'pm1', paymentNo: 'PM001' } as Payment)

    expect(detail.detailTitle.value).toBe('financeReportPages.payments.paymentTitle:PM001')
    expect(detail.selectedPayment.value?.paymentNo).toBe('PM001')
    expect(detail.paymentAllocations.value).toHaveLength(1)
    expect(detail.detailItems.value).toEqual([{ label: 'payment', value: 'PM001' }])
  })

  it('clears the other side so a stale allocation grid never renders', async () => {
    const detail = createDetail()

    await detail.viewReceipt({ id: 'rc1', receiptNo: 'RC001' } as Receipt)
    expect(detail.selectedReceipt.value).toBeDefined()

    await detail.viewPayment({ id: 'pm1', paymentNo: 'PM001' } as Payment)

    expect(detail.selectedReceipt.value).toBeUndefined()
    expect(detail.receiptAllocations.value).toEqual([])
    expect(detail.selectedPayment.value).toBeDefined()
  })

  it('closes the dialog and reports failures per side', async () => {
    const onError = vi.fn()
    const failingReceipt = createDetail({
      getReceipt: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    await failingReceipt.viewReceipt({ id: 'rc1', receiptNo: 'RC001' } as Receipt)
    expect(failingReceipt.detailVisible.value).toBe(false)
    expect(failingReceipt.detailLoading.value).toBe(false)
    expect(onError).toHaveBeenCalledWith(
      'financeReportPages.payments.message.receiptDetailLoadFailed'
    )

    const failingPayment = createDetail({
      getPayment: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await failingPayment.viewPayment({ id: 'pm1', paymentNo: 'PM001' } as Payment)
    expect(failingPayment.detailVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith(
      'financeReportPages.payments.message.paymentDetailLoadFailed'
    )
  })

  it('tolerates a document with no allocations', async () => {
    const detail = createDetail({
      getReceipt: vi.fn(async () => ({ ...receipt, allocations: undefined } as any))
    })

    await detail.viewReceipt({ id: 'rc1', receiptNo: 'RC001' } as Receipt)

    expect(detail.receiptAllocations.value).toEqual([])
  })
})
