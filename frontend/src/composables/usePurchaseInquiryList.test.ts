import { describe, expect, it, vi } from 'vitest'

import type { PurchaseInquiry } from '@/api/purchase'
import { usePurchaseInquiryList } from './usePurchaseInquiryList'

const t = (key: string, params?: Record<string, unknown>) => {
  if (params?.no != null) return `${key}:${params.no}`
  return key
}

describe('purchase inquiry list', () => {
  const createList = (overrides: Partial<Parameters<typeof usePurchaseInquiryList>[1]> = {}) =>
    usePurchaseInquiryList(t, {
      getInquiries: vi.fn(async () => ({
        records: [{ id: 'inq-1', inquiryNo: 'PI001', status: 'DRAFT' }],
        total: 1
      } as any)),
      getInquiry: vi.fn(async () => ({
        id: 'inq-1',
        inquiryNo: 'PI001',
        selectedSupplierId: 's1',
        lines: [{ productId: 'p1', productCode: '', productName: '' }]
      } as any)),
      submitInquiry: vi.fn(async () => ({})),
      cancelInquiry: vi.fn(async () => ({})),
      getProducts: vi.fn(async () => ({
        records: [{ id: 'p1', productCode: 'RM-1', productName: 'Steel' }],
        total: 1
      } as any)),
      getSuppliers: vi.fn(async () => ({
        records: [{ id: 's1', supplierName: 'Acme' }],
        total: 1
      } as any)),
      printInquiry: vi.fn(),
      confirm: vi.fn(async () => true),
      onSuccess: vi.fn(),
      onError: vi.fn(),
      ...overrides
    })

  it('loads inquiries/options and supports search/reset/pagination', async () => {
    const list = createList()
    await list.loadData()
    expect(list.tableData.value).toHaveLength(1)

    await list.loadOptions()
    expect(list.products.value).toHaveLength(1)
    expect(list.suppliers.value).toHaveLength(1)

    list.searchForm.keyword = 'PI'
    list.handleSearch()
    await Promise.resolve()
    expect(list.searchForm.pageNo).toBe(1)

    list.handlePageChange(2)
    await Promise.resolve()
    expect(list.searchForm.pageNo).toBe(2)

    list.handleReset()
    await Promise.resolve()
    expect(list.searchForm.keyword).toBe('')
  })

  it('submits/cancels and prints inquiry details', async () => {
    const submitInquiry = vi.fn(async () => ({}))
    const cancelInquiry = vi.fn(async () => ({}))
    const printInquiry = vi.fn()
    const onSuccess = vi.fn()
    const list = createList({ submitInquiry, cancelInquiry, printInquiry, onSuccess })

    await list.handleSubmit({ id: 'inq-1', inquiryNo: 'PI001' } as PurchaseInquiry)
    await list.handleCancel({ id: 'inq-1', inquiryNo: 'PI001' } as PurchaseInquiry)
    await list.handlePrint({ id: 'inq-1' } as PurchaseInquiry)

    expect(submitInquiry).toHaveBeenCalledWith('inq-1')
    expect(cancelInquiry).toHaveBeenCalledWith('inq-1')
    expect(printInquiry).toHaveBeenCalled()
    expect(onSuccess).toHaveBeenCalledWith('purchaseInquiryOps.message.submitted')
    expect(onSuccess).toHaveBeenCalledWith('purchaseInquiryOps.message.cancelled')
  })
})
