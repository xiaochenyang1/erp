import { describe, expect, it, vi } from 'vitest'

import type { BOM, BOMItem } from '@/api/production'
import type { Product } from '@/api/masterdata'
import { useProductionBomForm } from './useProductionBomForm'

const t = (key: string) => key

const detail = {
  id: 'b1',
  bomCode: 'BOM001',
  productId: 'p1',
  baseQty: 2,
  remark: '备注',
  items: [
    {
      materialId: 'm1',
      materialCode: 'RM-01',
      materialName: '原料',
      quantity: 3,
      unit: 'kg',
      scrapRate: 1
    }
  ]
} as unknown as BOM

const products: Product[] = [
  {
    id: 'm2',
    productCode: 'RM-02',
    productName: '原料2',
    unitName: 'pcs'
  } as Product
]

const createForm = (overrides: Partial<Parameters<typeof useProductionBomForm>[1]> = {}) =>
  useProductionBomForm(t, {
    getBOM: vi.fn(async () => detail),
    createBOM: vi.fn(async () => ({})),
    updateBOM: vi.fn(async () => ({})),
    getProducts: () => products,
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  })

describe('production BOM form', () => {
  it('opens a blank create form with default base quantity', () => {
    const form = createForm()
    form.handleAdd()

    expect(form.dialogVisible.value).toBe(true)
    expect(form.formData.id).toBeUndefined()
    expect(form.formData.baseQty).toBe(1)
    expect(form.formData.items).toEqual([])
    expect(form.dialogTitle.value).toBe('productionBom.dialog.create')
  })

  it('loads the detail for edit and clones material lines', async () => {
    const getBOM = vi.fn(async () => detail)
    const form = createForm({ getBOM })

    expect(await form.handleEdit({ id: 'b1' } as BOM)).toBe(true)
    expect(getBOM).toHaveBeenCalledWith('b1')
    expect(form.formData).toMatchObject({
      id: 'b1',
      productId: 'p1',
      baseQty: 2,
      remark: '备注'
    })
    expect(form.formData.items).toHaveLength(1)
    expect(form.formData.items[0]).not.toBe(detail.items[0])
    expect(form.dialogTitle.value).toBe('productionBom.dialog.edit')
  })

  it('reports detail failures without opening the dialog', async () => {
    const onError = vi.fn()
    const form = createForm({
      getBOM: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    expect(await form.handleEdit({ id: 'b1' } as BOM)).toBe(false)
    expect(form.dialogVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('productionBom.message.detailLoadFailed')
  })

  it('hydrates material code/name/unit when a product is selected', () => {
    const form = createForm()
    const row = {
      materialId: '',
      materialCode: '',
      materialName: '',
      quantity: 1,
      unit: '',
      scrapRate: 0
    } as BOMItem

    form.handleMaterialChange('m2', row)
    expect(row).toMatchObject({
      materialId: 'm2',
      materialCode: 'RM-02',
      materialName: '原料2',
      unit: 'pcs'
    })
  })

  it('adds and removes material lines', () => {
    const form = createForm()
    form.handleAdd()
    form.handleAddItem()
    form.handleAddItem()
    expect(form.formData.items).toHaveLength(2)
    form.handleDeleteItem(0)
    expect(form.formData.items).toHaveLength(1)
  })

  it('blocks submit without materials or product and warns', async () => {
    const onWarning = vi.fn()
    const form = createForm({ onWarning })
    form.handleAdd()

    expect(await form.handleSubmit()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('productionBom.validation.materials')

    form.handleAddItem()
    expect(await form.handleSubmit()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('productionBom.validation.product')
  })

  it('creates when no id is present and updates when editing', async () => {
    const createBOM = vi.fn(async () => ({}))
    const updateBOM = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const form = createForm({ createBOM, updateBOM, onSubmitted })

    form.handleAdd()
    form.formData.productId = 'p1'
    form.handleAddItem()
    form.formData.items[0].materialId = 'm2'
    form.formData.items[0].quantity = 4
    expect(await form.handleSubmit()).toBe(true)
    expect(createBOM).toHaveBeenCalledWith({
      productId: 'p1',
      baseQty: 1,
      items: form.formData.items,
      remark: ''
    })
    expect(onSubmitted).toHaveBeenCalled()

    await form.handleEdit({ id: 'b1' } as BOM)
    form.formData.baseQty = 5
    expect(await form.handleSubmit()).toBe(true)
    expect(updateBOM).toHaveBeenCalledWith('b1', expect.objectContaining({
      productId: 'p1',
      baseQty: 5
    }))
  })

  it('reports action failures without closing the dialog', async () => {
    const onError = vi.fn()
    const form = createForm({
      createBOM: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    form.handleAdd()
    form.formData.productId = 'p1'
    form.handleAddItem()
    expect(await form.handleSubmit()).toBe(false)
    expect(form.dialogVisible.value).toBe(true)
    expect(onError).toHaveBeenCalledWith('productionBom.message.actionFailed')
    expect(form.submitLoading.value).toBe(false)
  })
})
