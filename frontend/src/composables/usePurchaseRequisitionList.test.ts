import { describe, expect, it, vi } from 'vitest'

import type { PurchaseRequisition } from '@/api/purchase'
import { usePurchaseRequisitionList } from './usePurchaseRequisitionList'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.no != null ? `${key}:${params.no}` : key

const row = (overrides: Partial<PurchaseRequisition> = {}) =>
  ({
    id: 'rq1',
    requisitionNo: 'PRQ001',
    status: 'DRAFT',
    lines: [],
    ...overrides
  }) as PurchaseRequisition

const createList = (overrides: Partial<Parameters<typeof usePurchaseRequisitionList>[1]> = {}) =>
  usePurchaseRequisitionList(t, {
    getRequisitions: vi.fn(async () => ({ records: [row()], total: 1 } as any)),
    getRequisition: vi.fn(async () => row({ remark: 'detail' })),
    getProducts: vi.fn(async () => ({ records: [{ id: 'p1' }], total: 1 } as any)),
    getSuppliers: vi.fn(async () => ({ records: [{ id: 's1' }], total: 1 } as any)),
    submit: vi.fn(async () => ({})),
    approve: vi.fn(async () => ({})),
    reject: vi.fn(async () => ({})),
    cancel: vi.fn(async () => ({})),
    convert: vi.fn(async () => ({})),
    printRequisition: vi.fn(),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('purchase requisition list', () => {
  it('sends filled filters and resets paging on search', async () => {
    const getRequisitions = vi.fn(async () => ({ records: [], total: 4 } as any))
    const list = createList({ getRequisitions })

    list.query.pageNo = 5
    list.query.keyword = 'PRQ'
    list.query.status = 'DRAFT'
    await list.handleSearch()

    expect(getRequisitions).toHaveBeenCalledWith({
      keyword: 'PRQ',
      status: 'DRAFT',
      pageNo: 1,
      pageSize: 20
    })
    expect(list.total.value).toBe(4)
  })

  it('keeps the page when paging and returns to page 1 on size change or reset', async () => {
    const getRequisitions = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getRequisitions })

    await list.handlePageChange(3)
    expect(list.query.pageNo).toBe(3)
    expect(getRequisitions).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 3 }))

    await list.handleSizeChange(50)
    expect(list.query.pageSize).toBe(50)
    expect(list.query.pageNo).toBe(1)

    list.query.keyword = 'X'
    list.query.pageNo = 4
    await list.handleReset()
    expect(list.query.keyword).toBe('')
    expect(list.query.pageNo).toBe(1)
  })

  it('loads product and supplier options with ACTIVE page contracts', async () => {
    const getProducts = vi.fn(async () => ({ records: [{ id: 'p1' }], total: 1 } as any))
    const getSuppliers = vi.fn(async () => ({ records: [{ id: 's1' }], total: 1 } as any))
    const list = createList({ getProducts, getSuppliers })

    await list.loadOptions()
    expect(getProducts).toHaveBeenCalledWith({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    expect(getSuppliers).toHaveBeenCalledWith({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    expect(list.products.value).toEqual([{ id: 'p1' }])
    expect(list.suppliers.value).toEqual([{ id: 's1' }])
  })

  it('opens detail and prints decorated requisitions', async () => {
    const printRequisition = vi.fn()
    const decoratePrint = vi.fn((doc: PurchaseRequisition) => ({
      ...doc,
      supplierName: '供应商甲'
    }))
    const list = createList({ printRequisition, decoratePrint })

    expect(await list.openDetail(row())).toBe(true)
    expect(list.detailVisible.value).toBe(true)
    expect(list.detail.value?.remark).toBe('detail')

    expect(await list.handlePrint(row())).toBe(true)
    expect(printRequisition).toHaveBeenCalledWith(
      expect.objectContaining({ supplierName: '供应商甲' })
    )
  })

  it('runs lifecycle actions after confirmation and aborts when cancelled', async () => {
    const submit = vi.fn(async () => ({}))
    const confirm = vi.fn(async () => true)
    const onSuccess = vi.fn()
    const list = createList({ submit, confirm, onSuccess })

    expect(await list.act(row(), 'submit')).toBe(true)
    expect(confirm).toHaveBeenCalledWith(
      'purchaseRequisition.message.submitConfirm:PRQ001',
      'purchaseRequisition.prompt',
      expect.objectContaining({ type: 'warning' })
    )
    expect(submit).toHaveBeenCalledWith('rq1')
    expect(onSuccess).toHaveBeenCalledWith('purchaseRequisition.message.done')

    const cancelled = createList({
      confirm: vi.fn(async () => { throw new Error('cancel') }),
      submit
    })
    submit.mockClear()
    expect(await cancelled.act(row(), 'submit')).toBe(false)
    expect(submit).not.toHaveBeenCalled()
  })

  it('reports load and action failures', async () => {
    const onError = vi.fn()
    const list = createList({
      getRequisitions: vi.fn(async () => { throw new Error('boom') }),
      submit: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    await list.loadData()
    expect(onError).toHaveBeenCalledWith('purchaseRequisition.message.loadFailed')
    expect(list.loading.value).toBe(false)

    expect(await list.act(row(), 'submit')).toBe(false)
    expect(onError).toHaveBeenCalledWith('purchaseRequisition.message.failed')
  })
})
