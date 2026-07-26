import { describe, expect, it, vi } from 'vitest'

import type { BOM } from '@/api/production'
import { useProductionBomList } from './useProductionBomList'

const t = (key: string) => key

const row = (overrides: Partial<BOM> = {}) =>
  ({ id: 'b1', bomCode: 'BOM001', productId: 'p1', baseQty: 1, status: 'ACTIVE', items: [], ...overrides }) as BOM

const createList = (overrides: Partial<Parameters<typeof useProductionBomList>[1]> = {}) =>
  useProductionBomList(t, {
    getBOMs: vi.fn(async () => ({ records: [row()], total: 1 } as any)),
    getBOM: vi.fn(async () => row({ items: [{ materialId: 'm1', quantity: 2, unit: 'kg' }] as any })),
    getProducts: vi.fn(async () => ({ records: [{ id: 'p1', productName: '成品' }], total: 1 } as any)),
    printBOM: vi.fn(),
    onError: vi.fn(),
    ...overrides
  })

describe('production BOM list', () => {
  it('sends filled filters and resets paging on query', async () => {
    const getBOMs = vi.fn(async () => ({ records: [], total: 3 } as any))
    const list = createList({ getBOMs })

    list.pagination.page = 5
    list.queryForm.bomCode = 'BOM'
    list.queryForm.productId = 'p1'
    list.queryForm.status = 'ACTIVE'
    await list.handleQuery()

    expect(getBOMs).toHaveBeenCalledWith({
      bomCode: 'BOM',
      productId: 'p1',
      status: 'ACTIVE',
      pageNo: 1,
      pageSize: 20
    })
    expect(list.pagination.total).toBe(3)

    list.queryForm.bomCode = ''
    list.queryForm.productId = undefined
    list.queryForm.status = ''
    await list.handleQuery()
    expect(getBOMs).toHaveBeenLastCalledWith({
      bomCode: undefined,
      productId: undefined,
      status: undefined,
      pageNo: 1,
      pageSize: 20
    })
  })

  it('keeps the page when paging and returns to page 1 on size change or reset', async () => {
    const getBOMs = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getBOMs })

    await list.handlePageChange(4)
    expect(list.pagination.page).toBe(4)
    expect(getBOMs).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 4 }))

    await list.handleSizeChange(50)
    expect(list.pagination.size).toBe(50)
    expect(list.pagination.page).toBe(1)

    list.queryForm.bomCode = 'X'
    list.queryForm.status = 'DISABLED'
    list.pagination.page = 3
    await list.handleReset()

    expect(list.queryForm.bomCode).toBe('')
    expect(list.queryForm.status).toBe('')
    expect(list.queryForm.productId).toBeUndefined()
    expect(list.pagination.page).toBe(1)
  })

  it('reports list load failures and clears loading', async () => {
    const onError = vi.fn()
    const list = createList({
      getBOMs: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    await list.loadData()
    expect(onError).toHaveBeenCalledWith('productionBom.message.loadFailed')
    expect(list.loading.value).toBe(false)
  })

  it('loads product options with the shared ACTIVE page contract', async () => {
    const getProducts = vi.fn(async () => ({ records: [{ id: 'p1' }], total: 1 } as any))
    const list = createList({ getProducts })

    await list.loadProducts()
    expect(getProducts).toHaveBeenCalledWith({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    expect(list.productOptions.value).toEqual([{ id: 'p1' }])
  })

  it('reports product option load failures', async () => {
    const onError = vi.fn()
    const list = createList({
      getProducts: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    await list.loadProducts()
    expect(onError).toHaveBeenCalledWith('productionBom.message.productsLoadFailed')
  })

  it('opens the detail dialog from a fresh fetch', async () => {
    const getBOM = vi.fn(async () => row({ remark: 'detail' }))
    const list = createList({ getBOM })

    expect(await list.handleView(row())).toBe(true)
    expect(getBOM).toHaveBeenCalledWith('b1')
    expect(list.viewDialogVisible.value).toBe(true)
    expect(list.viewData.value.remark).toBe('detail')
  })

  it('reports detail failures without opening the dialog', async () => {
    const onError = vi.fn()
    const list = createList({
      getBOM: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    expect(await list.handleView(row())).toBe(false)
    expect(list.viewDialogVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('productionBom.message.detailLoadFailed')
  })

  it('prints a decorated detail and reports print load failures', async () => {
    const printBOM = vi.fn()
    const decoratePrint = vi.fn((doc: BOM) => ({ ...doc, productName: '成品A' }))
    const list = createList({ printBOM, decoratePrint })

    expect(await list.handlePrint(row())).toBe(true)
    expect(printBOM).toHaveBeenCalledWith(expect.objectContaining({ productName: '成品A' }))

    const onError = vi.fn()
    const failing = createList({
      getBOM: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    expect(await failing.handlePrint(row())).toBe(false)
    expect(onError).toHaveBeenCalledWith('productionBom.message.printLoadFailed')
  })
})
