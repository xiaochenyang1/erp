import { computed, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type { Product } from '@/api/masterdata'
import { useProductForm } from './useProductForm'

const texts = computed(() => ({
  editProduct: 'Edit',
  createProduct: 'Create',
  validationEnterCode: 'code',
  validationCodeLength: 'code length',
  validationEnterName: 'name',
  validationNameLength: 'name length',
  validationProductType: 'type',
  validationCategory: 'category',
  validationUnit: 'unit',
  validationConversionFactor: 'factor',
  validationSalePrice: 'sale',
  validationCostPrice: 'cost',
  validationTaxRate: 'tax',
  updateSuccess: 'updated',
  createSuccess: 'created',
  updateFailed: 'update failed',
  createFailed: 'create failed'
}))

describe('product form', () => {
  it('creates and edits product form state', async () => {
    const createProduct = vi.fn(async () => ({}))
    const updateProduct = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const onCompleted = vi.fn()
    const form = useProductForm(texts, {
      createProduct,
      updateProduct,
      onSuccess,
      onCompleted
    })

    form.handleCreate()
    expect(form.dialogVisible.value).toBe(true)
    expect(form.dialogTitle.value).toBe('Create')
    expect(form.formData.productType).toBe('PHYSICAL')

    form.handleEdit({
      id: '1',
      code: 'P001',
      name: 'Item',
      productType: 'SERVICE',
      unitPrice: 10,
      costPrice: 8,
      taxRate: 6,
      status: 'ACTIVE'
    } as Product)
    expect(form.formData.id).toBe('1')
    expect(form.dialogTitle.value).toBe('Edit')

    await form.handleSubmit({ name: 'Item 2' })
    expect(updateProduct).toHaveBeenCalled()
    expect(onSuccess).toHaveBeenCalledWith('updated')
    expect(onCompleted).toHaveBeenCalled()
  })
})
