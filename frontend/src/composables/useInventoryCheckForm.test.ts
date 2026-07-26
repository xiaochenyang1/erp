import { describe, expect, it, vi } from 'vitest'

import type { InventoryCheck } from '@/api/inventory'
import type { Product } from '@/api/masterdata'
import { useInventoryCheckForm } from './useInventoryCheckForm'

const t = (key: string, params?: Record<string, unknown>) => {
  if (params?.expected != null) return `${key}:${params.expected}/${params.actual}`
  return key
}

const products: Product[] = [
  {
    id: 'p1',
    code: 'P-001',
    name: '普通件',
    lotControlled: false,
    shelfLifeControlled: false,
    serialControlled: false
  } as any,
  {
    id: 'p2',
    code: 'P-002',
    name: '批次件',
    lotControlled: true,
    shelfLifeControlled: false,
    serialControlled: false
  } as any,
  {
    id: 'p3',
    code: 'P-003',
    name: '序列号件',
    lotControlled: false,
    shelfLifeControlled: false,
    serialControlled: true
  } as any
]

const createForm = (overrides: Partial<Parameters<typeof useInventoryCheckForm>[1]> = {}) =>
  useInventoryCheckForm(t, {
    getCheck: vi.fn(async () => ({
      id: 'c1',
      checkNo: 'CK001',
      warehouseId: 1,
      checkDate: '2026-07-20',
      status: 'COUNTED',
      items: [{ productId: 'p2', bookQuantity: 5, actualQuantity: 5, lotNo: 'L1' }]
    } as InventoryCheck)),
    createCheck: vi.fn(async () => ({})),
    updateCheck: vi.fn(async () => ({})),
    getStocks: vi.fn(async () => ({
      records: [
        { productId: 'p1', productCode: 'P-001', productName: '普通件', quantity: 8, locationId: 'l1' }
      ],
      total: 1
    } as any)),
    findProduct: (productId) => products.find((item) => String(item.id) === String(productId)),
    onSuccess: vi.fn(),
    onError: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  })

describe('inventory check form', () => {
  it('opens create mode with a business-date default and empty lines', () => {
    const form = createForm()
    form.handleCreate()

    expect(form.dialogVisible.value).toBe(true)
    expect(form.isView.value).toBe(false)
    expect(form.isEdit.value).toBe(false)
    expect(form.formData.items).toEqual([])
    expect(form.formData.checkDate).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('loads detail in view and edit modes, hydrating control flags', async () => {
    const form = createForm()

    await form.handleView({ id: 'c1' } as InventoryCheck)
    expect(form.isView.value).toBe(true)
    expect(form.currentId.value).toBe('')
    expect(form.formData.items[0].lotControlled).toBe(true)

    await form.handleEdit({ id: 'c1' } as InventoryCheck)
    expect(form.isEdit.value).toBe(true)
    expect(form.isView.value).toBe(false)
    expect(form.currentId.value).toBe('c1')
  })

  it('reports detail load failures without opening the dialog', async () => {
    const onError = vi.fn()
    const form = createForm({
      getCheck: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    await form.handleView({ id: 'c1' } as InventoryCheck)
    expect(form.dialogVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('inventoryChecks.message.detailLoadFailed')
  })

  it('prefills lines from warehouse stock and exposes the selected warehouse', async () => {
    const getStocks = vi.fn(async () => ({
      records: [
        { productId: 'p2', productCode: 'P-002', productName: '批次件', quantity: 3, locationId: 'l9' }
      ],
      total: 1
    } as any))
    const form = createForm({ getStocks })

    form.handleCreate()
    expect(form.selectedWarehouseId.value).toBeUndefined()

    form.formData.warehouseId = 7
    await form.handleWarehouseChange()

    expect(getStocks).toHaveBeenCalledWith({ pageNo: 1, pageSize: 1000, warehouseId: 7 })
    expect(form.selectedWarehouseId.value).toBe(7)
    expect(form.formData.items).toHaveLength(1)
    expect(form.formData.items[0]).toMatchObject({
      productId: 'p2',
      bookQuantity: 3,
      locationId: 'l9',
      lotControlled: true,
      actualQuantity: undefined
    })
  })

  it('skips the stock lookup until a warehouse is chosen and surfaces failures', async () => {
    const getStocks = vi.fn(async () => ({ records: [], total: 0 } as any))
    const form = createForm({ getStocks })
    form.handleCreate()

    await form.handleWarehouseChange()
    expect(getStocks).not.toHaveBeenCalled()

    const onError = vi.fn()
    const failing = createForm({
      getStocks: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    failing.formData.warehouseId = 2
    await failing.handleWarehouseChange()
    expect(onError).toHaveBeenCalledWith('inventoryChecks.message.stockLoadFailed')
  })

  it('adds, removes and hydrates lines, and derives the difference from counted qty', async () => {
    const form = createForm()
    form.handleCreate()

    form.handleAddItem()
    form.handleAddItem()
    expect(form.formData.items).toHaveLength(2)

    form.handleDeleteItem(1)
    expect(form.formData.items).toHaveLength(1)

    form.formData.items[0].productId = 'p3'
    await form.handleProductChange(0)
    expect(form.formData.items[0]).toMatchObject({
      productCode: 'P-003',
      productName: '序列号件',
      serialControlled: true,
      bookQuantity: 0
    })

    form.formData.items[0].bookQuantity = 10
    form.formData.items[0].actualQuantity = 7
    form.handleQuantityChange(form.formData.items[0])
    expect(form.formData.items[0].difference).toBe(-3)
  })

  it('requires at least one line before submitting', async () => {
    const onWarning = vi.fn()
    const createCheck = vi.fn(async () => ({}))
    const form = createForm({ onWarning, createCheck })
    form.handleCreate()

    const submitted = await form.handleSubmit()
    expect(submitted).toBe(false)
    expect(createCheck).not.toHaveBeenCalled()
    expect(onWarning).toHaveBeenCalledWith('inventoryChecks.validation.itemRequired')
  })

  it('validates lot/serial capture against the counted quantity', async () => {
    const onWarning = vi.fn()
    const createCheck = vi.fn(async () => ({}))
    const form = createForm({ onWarning, createCheck })
    form.handleCreate()

    form.formData.items = [{
      productId: 'p3',
      bookQuantity: 2,
      actualQuantity: 2,
      serialNos: 'SN1',
      serialControlled: true
    } as any]

    expect(await form.handleSubmit()).toBe(false)
    expect(createCheck).not.toHaveBeenCalled()
    expect(onWarning).toHaveBeenCalledWith('inventoryChecks.validation.serialCountMismatch:2/1')

    form.formData.items[0].serialNos = 'SN1 SN2'
    expect(await form.handleSubmit()).toBe(true)
    expect(createCheck).toHaveBeenCalled()
  })

  it('creates in new mode and updates lines only in edit mode', async () => {
    const createCheck = vi.fn(async () => ({}))
    const updateCheck = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const form = createForm({ createCheck, updateCheck, onSubmitted })

    form.handleCreate()
    form.formData.warehouseId = 3
    form.formData.items = [{ productId: 'p1', bookQuantity: 1, actualQuantity: 1 } as any]
    expect(await form.handleSubmit()).toBe(true)
    expect(createCheck).toHaveBeenCalledWith(expect.objectContaining({ warehouseId: 3 }))
    expect(form.dialogVisible.value).toBe(false)
    expect(onSubmitted).toHaveBeenCalled()

    await form.handleEdit({ id: 'c1' } as InventoryCheck)
    expect(await form.handleSubmit()).toBe(true)
    expect(updateCheck).toHaveBeenCalledWith('c1', {
      items: expect.arrayContaining([expect.objectContaining({ productId: 'p2' })])
    })
  })

  it('keeps the dialog open and clears loading when submit fails', async () => {
    const onError = vi.fn()
    const form = createForm({
      createCheck: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    form.handleCreate()
    form.formData.items = [{ productId: 'p1', bookQuantity: 1, actualQuantity: 1 } as any]

    expect(await form.handleSubmit()).toBe(false)
    expect(form.dialogVisible.value).toBe(true)
    expect(form.submitLoading.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('inventoryChecks.message.failed')
  })
})
