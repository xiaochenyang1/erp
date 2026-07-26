import { describe, expect, it, vi } from 'vitest'

import type { InventoryAdjustment } from '@/api/inventory'
import type { Product } from '@/api/masterdata'
import { useInventoryAdjustmentForm } from './useInventoryAdjustmentForm'

const t = (key: string, params?: Record<string, unknown>) => {
  if (params?.expected != null) return `${key}:${params.expected}/${params.actual}`
  return key
}

const products: Product[] = [
  {
    id: 'p1',
    code: 'P-001',
    name: '普通件',
    purchasePrice: 12,
    lotControlled: false,
    shelfLifeControlled: false,
    serialControlled: false
  } as any,
  {
    id: 'p2',
    code: 'P-002',
    name: '批次件',
    purchasePrice: 30,
    lotControlled: true,
    shelfLifeControlled: false,
    serialControlled: false
  } as any,
  {
    id: 'p3',
    code: 'P-003',
    name: '序列号件',
    purchasePrice: 50,
    lotControlled: false,
    shelfLifeControlled: false,
    serialControlled: true
  } as any
]

const createForm = (overrides: Partial<Parameters<typeof useInventoryAdjustmentForm>[1]> = {}) =>
  useInventoryAdjustmentForm(t, {
    getAdjustment: vi.fn(async () => ({
      id: 'a1',
      adjustmentNo: 'ADJ001',
      warehouseId: 1,
      adjustmentDate: '2026-07-20',
      type: 'LOSS',
      status: 'DRAFT',
      items: [{ productId: 'p2', quantity: 5, lotNo: 'L1' }]
    } as InventoryAdjustment)),
    createAdjustment: vi.fn(async () => ({})),
    findProduct: (productId) => products.find((item) => String(item.id) === String(productId)),
    onSuccess: vi.fn(),
    onError: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  })

describe('inventory adjustment form', () => {
  it('opens create mode defaulting to a gain on the business date', () => {
    const form = createForm()
    form.handleCreate()

    expect(form.dialogVisible.value).toBe(true)
    expect(form.isView.value).toBe(false)
    expect(form.formData.type).toBe('GAIN')
    expect(form.formData.items).toEqual([])
    expect(form.formData.adjustmentDate).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('loads detail in view mode, hydrating control flags', async () => {
    const form = createForm()

    await form.handleView({ id: 'a1' } as InventoryAdjustment)

    expect(form.isView.value).toBe(true)
    expect(form.dialogVisible.value).toBe(true)
    expect(form.formData.type).toBe('LOSS')
    expect(form.formData.items[0].lotControlled).toBe(true)
  })

  it('reports detail load failures without opening the dialog', async () => {
    const onError = vi.fn()
    const form = createForm({
      getAdjustment: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    await form.handleView({ id: 'a1' } as InventoryAdjustment)
    expect(form.dialogVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('inventoryAdjustments.message.detailLoadFailed')
  })

  it('tracks the selected warehouse only once one is chosen', () => {
    const form = createForm()
    form.handleCreate()
    expect(form.selectedWarehouseId.value).toBeUndefined()

    form.formData.warehouseId = 7
    expect(form.selectedWarehouseId.value).toBe(7)
  })

  it('adds, removes and hydrates lines with the product cost', async () => {
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
      unitCost: 50,
      serialControlled: true
    })
  })

  it('ignores product changes for rows that no longer exist', async () => {
    const form = createForm()
    form.handleCreate()
    await expect(form.handleProductChange(3)).resolves.toBeUndefined()
  })

  it('requires at least one line before submitting', async () => {
    const onWarning = vi.fn()
    const createAdjustment = vi.fn(async () => ({}))
    const form = createForm({ onWarning, createAdjustment })
    form.handleCreate()

    expect(await form.handleSubmit()).toBe(false)
    expect(createAdjustment).not.toHaveBeenCalled()
    expect(onWarning).toHaveBeenCalledWith('inventoryAdjustments.validation.itemRequired')
  })

  it('validates lot and serial capture against the adjusted quantity', async () => {
    const onWarning = vi.fn()
    const createAdjustment = vi.fn(async () => ({}))
    const form = createForm({ onWarning, createAdjustment })
    form.handleCreate()

    form.formData.items = [{
      productId: 'p2',
      quantity: 3,
      lotControlled: true,
      lotNo: ''
    } as any]
    expect(await form.handleSubmit()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('inventoryAdjustments.validation.lotRequired')

    form.formData.items = [{
      productId: 'p3',
      quantity: 2,
      serialControlled: true,
      serialNos: 'SN1'
    } as any]
    expect(await form.handleSubmit()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('inventoryAdjustments.validation.serialCountMismatch:2/1')
    expect(createAdjustment).not.toHaveBeenCalled()

    form.formData.items[0].serialNos = 'SN1,SN2'
    expect(await form.handleSubmit()).toBe(true)
    expect(createAdjustment).toHaveBeenCalled()
  })

  it('creates the draft with the chosen warehouse and type, then reloads', async () => {
    const createAdjustment = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const onSuccess = vi.fn()
    const form = createForm({ createAdjustment, onSubmitted, onSuccess })

    form.handleCreate()
    form.formData.warehouseId = 3
    form.formData.type = 'LOSS'
    form.formData.items = [{ productId: 'p1', quantity: 1 } as any]

    expect(await form.handleSubmit()).toBe(true)
    expect(createAdjustment).toHaveBeenCalledWith(expect.objectContaining({
      warehouseId: 3,
      type: 'LOSS'
    }))
    expect(form.dialogVisible.value).toBe(false)
    expect(onSuccess).toHaveBeenCalledWith('inventoryAdjustments.message.success')
    expect(onSubmitted).toHaveBeenCalled()
  })

  it('keeps the dialog open and clears loading when submit fails', async () => {
    const onError = vi.fn()
    const form = createForm({
      createAdjustment: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    form.handleCreate()
    form.formData.items = [{ productId: 'p1', quantity: 1 } as any]

    expect(await form.handleSubmit()).toBe(false)
    expect(form.dialogVisible.value).toBe(true)
    expect(form.submitLoading.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('inventoryAdjustments.message.failed')
  })
})
