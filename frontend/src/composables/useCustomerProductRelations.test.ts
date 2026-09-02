import { computed } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type { Customer, CustomerProductRelation, Product } from '@/api/masterdata'
import { useCustomerProductRelations } from './useCustomerProductRelations'

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

const customer = { id: '9', customerCode: 'C001', customerName: 'Acme', status: 'ACTIVE' } as Customer

const relation = (overrides: Partial<CustomerProductRelation> = {}): CustomerProductRelation => ({
  id: '1',
  customerId: '9',
  productId: '41',
  productCode: 'P-001',
  productName: 'Widget',
  customerProductCode: 'C-001',
  customerProductName: 'Client widget',
  deliveryPreference: 'Tuesday',
  packagingPreference: 'Carton',
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
  const getCustomerProductRelations = vi.fn(async () => [relation()])
  const saveCustomerProductRelation = vi.fn(async () => relation())
  const deleteCustomerProductRelation = vi.fn(async () => ({}))
  const getProducts = vi.fn(async () => productPage())
  const confirm = vi.fn(async () => true)
  const onSuccess = vi.fn()
  const onError = vi.fn()
  const api = {
    getCustomerProductRelations,
    saveCustomerProductRelation,
    deleteCustomerProductRelation,
    getProducts,
    confirm,
    interpolate,
    onSuccess,
    onError,
    ...overrides
  }
  return { api, relations: useCustomerProductRelations(texts, api as never) }
}

describe('customer product relations', () => {
  it('loads relations and active products when opened', async () => {
    const { api, relations } = setup()

    await relations.openRelations(customer)

    expect(relations.relationVisible.value).toBe(true)
    expect(relations.relationOwner.value?.id).toBe('9')
    expect(relations.relationRows.value).toHaveLength(1)
    expect(relations.relationProducts.value).toHaveLength(1)
    expect(api.getCustomerProductRelations).toHaveBeenCalledWith('9')
    expect(api.getProducts).toHaveBeenCalledWith({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    expect(relations.relationLoading.value).toBe(false)
  })

  it('reports load failure and clears rows', async () => {
    const { api, relations } = setup({
      getCustomerProductRelations: vi.fn(async () => {
        throw new Error('boom')
      })
    })

    await relations.openRelations(customer)

    expect(relations.relationRows.value).toEqual([])
    expect(api.onError).toHaveBeenCalledWith('load failed')
  })

  it('ignores a stale relation response', async () => {
    let resolveFirst: ((rows: CustomerProductRelation[]) => void) | undefined
    const getCustomerProductRelations = vi
      .fn()
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveFirst = resolve as (rows: CustomerProductRelation[]) => void
      }))
      .mockImplementationOnce(async () => [relation({ id: '2', productCode: 'P-002' })])
    const { relations } = setup({ getCustomerProductRelations })

    relations.relationOwner.value = customer
    const first = relations.loadRelations()
    const second = relations.loadRelations()
    resolveFirst?.([relation({ id: '1', productCode: 'P-STALE' })])
    await Promise.all([first, second])

    expect(relations.relationRows.value.map((row) => row.productCode)).toEqual(['P-002'])
    expect(relations.relationLoading.value).toBe(false)
  })

  it('switches the form between create and edit', async () => {
    const { relations } = setup()
    await relations.openRelations(customer)

    relations.handleRelationCreate()
    expect(relations.relationFormVisible.value).toBe(true)
    expect(relations.relationFormTitle.value).toBe('Add relation')
    expect(relations.relationForm.productId).toBe('')

    relations.handleRelationEdit(relation())
    expect(relations.relationFormTitle.value).toBe('Edit relation')
    expect(relations.relationForm.id).toBe('1')
    expect(relations.relationForm.productId).toBe('41')
    expect(relations.relationForm.deliveryPreference).toBe('Tuesday')
  })

  it('saves a relation, reloads rows and drops blank text fields', async () => {
    const { api, relations } = setup()
    await relations.openRelations(customer)
    relations.handleRelationCreate()
    relations.relationForm.productId = '41'
    relations.relationForm.customerProductCode = 'C-009'

    const saved = await relations.handleRelationSubmit()

    expect(saved).toBe(true)
    expect(api.saveCustomerProductRelation).toHaveBeenCalledWith('9', {
      productId: '41',
      customerProductCode: 'C-009',
      customerProductName: undefined,
      deliveryPreference: undefined,
      packagingPreference: undefined,
      remark: undefined
    })
    expect(api.onSuccess).toHaveBeenCalledWith('saved')
    expect(relations.relationFormVisible.value).toBe(false)
    expect(api.getCustomerProductRelations).toHaveBeenCalledTimes(2)
  })

  it('refuses to submit without a product', async () => {
    const { api, relations } = setup()
    await relations.openRelations(customer)
    relations.handleRelationCreate()

    const saved = await relations.handleRelationSubmit()

    expect(saved).toBe(false)
    expect(api.saveCustomerProductRelation).not.toHaveBeenCalled()
    expect(api.onError).toHaveBeenCalledWith('select product')
  })

  it('surfaces save failures without closing the form', async () => {
    const { api, relations } = setup({
      saveCustomerProductRelation: vi.fn(async () => {
        throw new Error('boom')
      })
    })
    await relations.openRelations(customer)
    relations.handleRelationCreate()
    relations.relationForm.productId = '41'

    const saved = await relations.handleRelationSubmit()

    expect(saved).toBe(false)
    expect(relations.relationFormVisible.value).toBe(true)
    expect(api.onError).toHaveBeenCalledWith('save failed')
    expect(relations.relationSubmitting.value).toBe(false)
  })

  it('confirms before deleting and reloads afterwards', async () => {
    const { api, relations } = setup()
    await relations.openRelations(customer)

    const removed = await relations.handleRelationDelete(relation())

    expect(removed).toBe(true)
    expect(api.confirm).toHaveBeenCalledWith('Remove P-001 / Widget?', 'Confirm', expect.objectContaining({ type: 'warning' }))
    expect(api.deleteCustomerProductRelation).toHaveBeenCalledWith('9', '1')
    expect(api.onSuccess).toHaveBeenCalledWith('deleted')
    expect(api.getCustomerProductRelations).toHaveBeenCalledTimes(2)
  })

  it('keeps the relation when the confirm dialog is cancelled', async () => {
    const { api, relations } = setup({
      confirm: vi.fn(async () => {
        throw new Error('cancel')
      })
    })
    await relations.openRelations(customer)

    const removed = await relations.handleRelationDelete(relation())

    expect(removed).toBe(false)
    expect(api.deleteCustomerProductRelation).not.toHaveBeenCalled()
    expect(api.onError).not.toHaveBeenCalled()
  })

  it('falls back to the product id when the product is no longer visible', async () => {
    const { relations } = setup()

    expect(relations.productLabel(relation({ productCode: undefined, productName: undefined }))).toBe('41')
  })

  it('resets state on close', async () => {
    const { relations } = setup()
    await relations.openRelations(customer)

    relations.closeRelations()

    expect(relations.relationVisible.value).toBe(false)
    expect(relations.relationOwner.value).toBeNull()
    expect(relations.relationRows.value).toEqual([])
    expect(relations.relationForm.productId).toBe('')
  })
})
