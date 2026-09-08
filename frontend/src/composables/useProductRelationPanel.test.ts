import { describe, expect, it, vi } from 'vitest'

import type { Product } from '@/api/masterdata'
import { useProductRelationPanel, type ProductRelationRow } from './useProductRelationPanel'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

const product = (id: string, code: string, name: string) =>
  ({ id, productCode: code, productName: name } as Product)

const relation = (overrides: Partial<ProductRelationRow> = {}): ProductRelationRow => ({
  id: 'r1',
  productId: '11',
  productCode: 'P001',
  productName: 'Widget',
  ownerProductCode: 'X-1',
  ownerProductName: 'Partner widget',
  deliveryPreference: 'split shipments',
  packagingPreference: 'pallet',
  minPurchaseQty: 5,
  leadTimeDays: 7,
  defaultSupplier: true,
  remark: 'note',
  status: 'ACTIVE',
  ...overrides
})

const createPanel = (overrides: Partial<Parameters<typeof useProductRelationPanel>[1]> = {}) =>
  useProductRelationPanel(t, {
    loadRelations: vi.fn(async () => [relation()]),
    saveRelation: vi.fn(async () => ({})),
    removeRelation: vi.fn(async () => ({})),
    loadProducts: vi.fn(async () => [product('11', 'P001', 'Widget'), product('12', 'P002', 'Gadget')]),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  })

describe('product relation panel', () => {
  it('loads relations and product options when opened', async () => {
    const loadRelations = vi.fn(async () => [relation()])
    const loadProducts = vi.fn(async () => [product('11', 'P001', 'Widget')])
    const panel = createPanel({ loadRelations, loadProducts })

    expect(await panel.open(77)).toBe(true)
    expect(panel.ownerId.value).toBe('77')
    expect(loadRelations).toHaveBeenCalledWith('77')
    expect(loadProducts).toHaveBeenCalledOnce()
    expect(panel.rows.value).toEqual([relation()])
    expect(panel.loading.value).toBe(false)
  })

  it('reports load failures and clears the stale data', async () => {
    const onError = vi.fn()
    const panel = createPanel({
      loadRelations: vi.fn(async () => { throw new Error('network') }),
      loadProducts: vi.fn(async () => { throw new Error('network') }),
      onError
    })

    expect(await panel.open('9')).toBe(false)
    expect(panel.rows.value).toEqual([])
    expect(panel.products.value).toEqual([])
    expect(onError).toHaveBeenCalledWith('productRelation.message.loadFailed')
    expect(onError).toHaveBeenCalledWith('productRelation.message.productsFailed')
    expect(panel.loading.value).toBe(false)
  })

  it('offers only unmapped products, plus the product pinned by the editor', async () => {
    const panel = createPanel()
    await panel.open('77')

    panel.openCreate()
    expect(panel.selectableProducts.value.map((item) => item.id)).toEqual(['12'])

    panel.openEdit(relation())
    expect(panel.selectableProducts.value.map((item) => item.id)).toEqual(['11', '12'])
  })

  it('pins the product identity while editing and clears the form on cancel', async () => {
    const panel = createPanel()
    await panel.open('77')

    panel.openEdit(relation({ minPurchaseQty: undefined, leadTimeDays: undefined, defaultSupplier: undefined }))
    expect(panel.editingId.value).toBe('r1')
    expect(panel.editorVisible.value).toBe(true)
    expect(panel.form).toMatchObject({
      productId: '11',
      ownerProductCode: 'X-1',
      ownerProductName: 'Partner widget',
      minPurchaseQty: 0,
      leadTimeDays: 0,
      defaultSupplier: false,
      remark: 'note'
    })

    panel.closeEditor()
    expect(panel.editorVisible.value).toBe(false)
    expect(panel.editingId.value).toBeNull()
    expect(panel.form.productId).toBe('')
  })

  it('refuses to save without a product', async () => {
    const saveRelation = vi.fn(async () => ({}))
    const onWarning = vi.fn()
    const panel = createPanel({ saveRelation, onWarning })
    await panel.open('77')
    panel.openCreate()

    expect(await panel.submit()).toBe(false)
    expect(saveRelation).not.toHaveBeenCalled()
    expect(onWarning).toHaveBeenCalledWith('productRelation.validation.product')
  })

  it('saves the relation, closes the editor and refreshes the rows', async () => {
    const loadRelations = vi.fn(async () => [relation()])
    const saveRelation = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const panel = createPanel({ loadRelations, saveRelation, onSuccess })
    await panel.open('77')

    panel.openCreate()
    panel.form.productId = '12'
    panel.form.ownerProductCode = 'PARTNER-12'
    panel.form.minPurchaseQty = 10

    expect(await panel.submit()).toBe(true)
    expect(saveRelation).toHaveBeenCalledWith('77', expect.objectContaining({
      productId: '12',
      ownerProductCode: 'PARTNER-12',
      minPurchaseQty: 10
    }))
    expect(onSuccess).toHaveBeenCalledWith('productRelation.message.saved')
    expect(panel.editorVisible.value).toBe(false)
    expect(loadRelations).toHaveBeenCalledTimes(2)
    expect(panel.saving.value).toBe(false)
  })
  it('keeps the editor open when the save fails', async () => {
    const onError = vi.fn()
    const panel = createPanel({
      saveRelation: vi.fn(async () => { throw new Error('conflict') }),
      onError
    })
    await panel.open('77')
    panel.openCreate()
    panel.form.productId = '12'

    expect(await panel.submit()).toBe(false)
    expect(onError).toHaveBeenCalledWith('productRelation.message.saveFailed')
    expect(panel.editorVisible.value).toBe(true)
    expect(panel.saving.value).toBe(false)
  })

  it('confirms the deletion with the product label, then deletes and refreshes', async () => {
    const confirm = vi.fn(async () => true)
    const loadRelations = vi.fn(async () => [relation()])
    const removeRelation = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const panel = createPanel({ confirm, loadRelations, removeRelation, onSuccess })
    await panel.open('77')

    expect(await panel.remove(relation())).toBe(true)
    expect(confirm).toHaveBeenCalledWith(
      'productRelation.message.confirmDelete:{"product":"P001 Widget"}',
      'productRelation.message.prompt'
    )
    expect(removeRelation).toHaveBeenCalledWith('77', 'r1')
    expect(onSuccess).toHaveBeenCalledWith('productRelation.message.deleted')
    expect(loadRelations).toHaveBeenCalledTimes(2)
  })

  it('stops a cancelled deletion and reports backend failures', async () => {
    const removeRelation = vi.fn(async () => ({}))
    const onError = vi.fn()
    const cancelled = createPanel({
      confirm: vi.fn(async () => { throw 'cancel' }),
      removeRelation,
      onError
    })
    await cancelled.open('77')

    expect(await cancelled.remove(relation())).toBe(false)
    expect(removeRelation).not.toHaveBeenCalled()
    expect(onError).not.toHaveBeenCalled()

    const failed = createPanel({
      removeRelation: vi.fn(async () => { throw new Error('network') }),
      onError
    })
    await failed.open('77')
    expect(await failed.remove(relation())).toBe(false)
    expect(onError).toHaveBeenCalledWith('productRelation.message.deleteFailed')
  })

  it('labels relations whose product was disabled or deleted', () => {
    const panel = createPanel()

    expect(panel.relationProductLabel(relation())).toBe('P001 Widget')
    expect(panel.relationProductLabel(relation({ productCode: undefined, productName: undefined })))
      .toBe('productRelation.productUnavailable')
    expect(panel.productLabel({ id: '3', code: 'P003', name: 'Legacy alias' } as Product)).toBe('P003 Legacy alias')
  })

  it('drops the owner context on reset so a reopen cannot reload the previous partner', async () => {
    const loadRelations = vi.fn(async () => [relation()])
    const panel = createPanel({ loadRelations })
    await panel.open('77')

    panel.reset()
    expect(panel.ownerId.value).toBe('')
    expect(panel.rows.value).toEqual([])
    expect(panel.products.value).toEqual([])
    expect(await panel.loadRows()).toBe(false)
    expect(loadRelations).toHaveBeenCalledOnce()
  })
})
