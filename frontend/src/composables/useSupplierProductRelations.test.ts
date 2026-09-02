import { computed } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type { Product, Supplier, SupplierProductRelation } from '@/api/masterdata'
import { useSupplierProductRelations } from './useSupplierProductRelations'

const texts = computed(() => ({
  createRelation: 'Add relation',
  editRelation: 'Edit relation',
  relationLoadFailed: 'load failed',
  relationOptionsLoadFailed: 'options failed',
  relationSaveSuccess: 'saved',
  relationSaveFailed: 'save failed',
  relationDeleteSuccess: 'deleted',
  relationDeleteFailed: 'delete failed',
  confirmTitle: 'Confirm',
  confirmDeleteRelation: 'Remove {product}?',
  validationSelectProduct: 'select product'
}))

const supplier = { id: '9', supplierCode: 'S001', supplierName: 'Acme Supply', status: 'ACTIVE' } as Supplier

const relation = (overrides: Partial<SupplierProductRelation> = {}): SupplierProductRelation => ({
  id: '1',
  supplierId: '9',
  productId: '41',
  productCode: 'P-001',
  productName: 'Widget',
  supplierProductCode: 'S-001',
  supplierProductName: 'Vendor widget',
  minPurchaseQty: 5,
  leadTimeDays: 7,
  defaultSupplier: true,
  remark: 'note',
  status: 'ACTIVE',
  ...overrides
})

const productPage = (records: Partial<Product>[] = [{ id: '41', productCode: 'P-001', productName: 'Widget' }]) => ({
  records: records as Product[],
  total: records.length,
  current: 1,
  size: 200
})

const interpolate = (template: string, params: Record<string, string | number>) =>
  template.replace(/\{(\w+)\}/g, (_, key: string) => String(params[key] ?? ''))

const setup = (overrides: Record<string, unknown> = {}) => {
  const getSupplierProductRelations = vi.fn(async () => [relation()])
  const saveSupplierProductRelation = vi.fn(async () => relation())
  const deleteSupplierProductRelation = vi.fn(async () => ({}))
  const getProducts = vi.fn(async () => productPage())
  const confirm = vi.fn(async () => true)
  const onSuccess = vi.fn()
  const onError = vi.fn()
  const api = {
    getSupplierProductRelations,
    saveSupplierProductRelation,
    deleteSupplierProductRelation,
    getProducts,
    confirm,
    interpolate,
    onSuccess,
    onError,
    ...overrides
  }
  return { api, relations: useSupplierProductRelations(texts, api as never) }
}

describe('supplier product relations', () => {
  it('loads relations and active products when opened', async () => {
    const { api, relations } = setup()

    await relations.openRelations(supplier)

    expect(relations.relationVisible.value).toBe(true)
    expect(relations.relationOwner.value?.id).toBe('9')
    expect(relations.relationRows.value).toHaveLength(1)
    expect(relations.relationProducts.value).toHaveLength(1)
    expect(api.getSupplierProductRelations).toHaveBeenCalledWith('9')
    expect(api.getProducts).toHaveBeenCalledWith({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
  })

  it('reports load failure and clears rows', async () => {
    const { api, relations } = setup({
      getSupplierProductRelations: vi.fn(async () => {
        throw new Error('boom')
      })
    })

    await relations.openRelations(supplier)

    expect(relations.relationRows.value).toEqual([])
    expect(api.onError).toHaveBeenCalledWith('load failed')
  })

  it('ignores a stale relation response', async () => {
    let resolveFirst: ((rows: SupplierProductRelation[]) => void) | undefined
    const getSupplierProductRelations = vi
      .fn()
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveFirst = resolve as (rows: SupplierProductRelation[]) => void
      }))
      .mockImplementationOnce(async () => [relation({ id: '2', productCode: 'P-002' })])
    const { relations } = setup({ getSupplierProductRelations })

    relations.relationOwner.value = supplier
    const first = relations.loadRelations()
    const second = relations.loadRelations()
    resolveFirst?.([relation({ id: '1', productCode: 'P-STALE' })])
    await Promise.all([first, second])

    expect(relations.relationRows.value.map((row) => row.productCode)).toEqual(['P-002'])
    expect(relations.relationLoading.value).toBe(false)
  })

  it('loads numeric purchase rules into the edit form', async () => {
    const { relations } = setup()
    await relations.openRelations(supplier)

    relations.handleRelationEdit(relation())

    expect(relations.relationFormTitle.value).toBe('Edit relation')
    expect(relations.relationForm.minPurchaseQty).toBe(5)
    expect(relations.relationForm.leadTimeDays).toBe(7)
    expect(relations.relationForm.defaultSupplier).toBe(true)
  })

  it('sends zero defaults for blank purchase rules', async () => {
    const { api, relations } = setup()
    await relations.openRelations(supplier)
    relations.handleRelationCreate()
    relations.relationForm.productId = '41'
    relations.relationForm.supplierProductCode = 'S-009'

    const saved = await relations.handleRelationSubmit()

    expect(saved).toBe(true)
    expect(api.saveSupplierProductRelation).toHaveBeenCalledWith('9', {
      productId: '41',
      supplierProductCode: 'S-009',
      supplierProductName: undefined,
      minPurchaseQty: 0,
      leadTimeDays: 0,
      defaultSupplier: false,
      remark: undefined
    })
    expect(api.onSuccess).toHaveBeenCalledWith('saved')
    expect(api.getSupplierProductRelations).toHaveBeenCalledTimes(2)
  })

  it('keeps the default supplier flag when submitting', async () => {
    const { api, relations } = setup()
    await relations.openRelations(supplier)
    relations.handleRelationEdit(relation())

    await relations.handleRelationSubmit()

    expect(api.saveSupplierProductRelation).toHaveBeenCalledWith('9', expect.objectContaining({
      defaultSupplier: true,
      minPurchaseQty: 5,
      leadTimeDays: 7
    }))
  })

  it('refuses to submit without a product', async () => {
    const { api, relations } = setup()
    await relations.openRelations(supplier)
    relations.handleRelationCreate()

    const saved = await relations.handleRelationSubmit()

    expect(saved).toBe(false)
    expect(api.saveSupplierProductRelation).not.toHaveBeenCalled()
    expect(api.onError).toHaveBeenCalledWith('select product')
  })

  it('surfaces save failures without closing the form', async () => {
    const { api, relations } = setup({
      saveSupplierProductRelation: vi.fn(async () => {
        throw new Error('boom')
      })
    })
    await relations.openRelations(supplier)
    relations.handleRelationCreate()
    relations.relationForm.productId = '41'

    const saved = await relations.handleRelationSubmit()

    expect(saved).toBe(false)
    expect(relations.relationFormVisible.value).toBe(true)
    expect(api.onError).toHaveBeenCalledWith('save failed')
  })

  it('confirms before deleting and reloads afterwards', async () => {
    const { api, relations } = setup()
    await relations.openRelations(supplier)

    const removed = await relations.handleRelationDelete(relation())

    expect(removed).toBe(true)
    expect(api.confirm).toHaveBeenCalledWith('Remove P-001 / Widget?', 'Confirm', expect.objectContaining({ type: 'warning' }))
    expect(api.deleteSupplierProductRelation).toHaveBeenCalledWith('9', '1')
    expect(api.onSuccess).toHaveBeenCalledWith('deleted')
  })

  it('keeps the relation when the confirm dialog is cancelled', async () => {
    const { api, relations } = setup({
      confirm: vi.fn(async () => {
        throw new Error('cancel')
      })
    })
    await relations.openRelations(supplier)

    const removed = await relations.handleRelationDelete(relation())

    expect(removed).toBe(false)
    expect(api.deleteSupplierProductRelation).not.toHaveBeenCalled()
  })

  it('resets state on close', async () => {
    const { relations } = setup()
    await relations.openRelations(supplier)

    relations.closeRelations()

    expect(relations.relationVisible.value).toBe(false)
    expect(relations.relationOwner.value).toBeNull()
    expect(relations.relationRows.value).toEqual([])
    expect(relations.relationForm.minPurchaseQty).toBe(0)
  })
})
