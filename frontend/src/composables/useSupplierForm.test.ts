import { computed } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type { Supplier } from '@/api/masterdata'
import { useSupplierForm } from './useSupplierForm'

const texts = computed(() => ({
  editSupplier: 'Edit',
  createSupplier: 'Create',
  validationEnterCode: 'code',
  validationCodeLength: 'code length',
  validationEnterName: 'name',
  validationNameLength: 'name length',
  validationSettlementMethod: 'settlement',
  validationMobile: 'mobile',
  validationEmail: 'email',
  updateSuccess: 'updated',
  createSuccess: 'created',
  updateFailed: 'update failed',
  createFailed: 'create failed'
}))

describe('supplier form', () => {
  it('creates and edits supplier form state', async () => {
    const createSupplier = vi.fn(async () => ({}))
    const updateSupplier = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const onCompleted = vi.fn()
    const form = useSupplierForm(texts, {
      createSupplier,
      updateSupplier,
      onSuccess,
      onCompleted
    })

    form.handleCreate()
    expect(form.dialogVisible.value).toBe(true)
    expect(form.dialogTitle.value).toBe('Create')
    expect(form.formData.settlementMethod).toBe('BANK_TRANSFER')

    form.handleEdit({
      id: '1',
      supplierCode: 'S001',
      supplierName: 'Vendor',
      contactName: 'Bob',
      contactPhone: '13800138000',
      settlementMethod: 'CASH',
      creditPeriod: 15,
      status: 'ACTIVE'
    } as Supplier)
    expect(form.formData.id).toBe('1')
    expect(form.formData.creditPeriod).toBe(15)
    expect(form.dialogTitle.value).toBe('Edit')

    await form.handleSubmit({
      code: 'S001',
      name: 'Vendor',
      contact: 'Bob',
      mobile: '13800138000',
      email: 'b@c.com',
      settlementMethod: 'CASH',
      creditPeriod: 15,
      status: 'ACTIVE'
    })
    expect(updateSupplier).toHaveBeenCalled()
    expect(onSuccess).toHaveBeenCalledWith('updated')
    expect(onCompleted).toHaveBeenCalled()
  })

  it('clears zero credit period when editing', () => {
    const form = useSupplierForm(texts, {
      createSupplier: vi.fn(async () => ({})),
      updateSupplier: vi.fn(async () => ({}))
    })

    form.handleEdit({
      id: '2',
      supplierCode: 'S002',
      supplierName: 'Cash Vendor',
      creditPeriod: 0,
      status: 'ACTIVE'
    } as Supplier)
    expect(form.formData.creditPeriod).toBeUndefined()
  })
})
