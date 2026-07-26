import { computed } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type { Customer } from '@/api/masterdata'
import { useCustomerForm } from './useCustomerForm'

const texts = computed(() => ({
  editCustomer: 'Edit',
  createCustomer: 'Create',
  validationEnterCode: 'code',
  validationCodeLength: 'code length',
  validationEnterName: 'name',
  validationNameLength: 'name length',
  validationSettlementMethod: 'settlement',
  validationCreditLimit: 'credit',
  validationMobile: 'mobile',
  validationEmail: 'email',
  updateSuccess: 'updated',
  createSuccess: 'created',
  updateFailed: 'update failed',
  createFailed: 'create failed'
}))

describe('customer form', () => {
  it('creates and edits customer form state', async () => {
    const createCustomer = vi.fn(async () => ({}))
    const updateCustomer = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const onCompleted = vi.fn()
    const form = useCustomerForm(texts, {
      createCustomer,
      updateCustomer,
      onSuccess,
      onCompleted
    })

    form.handleCreate()
    expect(form.dialogVisible.value).toBe(true)
    expect(form.dialogTitle.value).toBe('Create')
    expect(form.formData.type).toBe('COMPANY')

    form.handleEdit({
      id: '1',
      customerCode: 'C001',
      customerName: 'Acme',
      type: 'INDIVIDUAL',
      settlementMethod: 'CASH',
      creditLimit: 1000,
      status: 'ACTIVE'
    } as Customer)
    expect(form.formData.id).toBe('1')
    expect(form.dialogTitle.value).toBe('Edit')

    await form.handleSubmit({
      code: 'C001',
      name: 'Acme',
      type: 'INDIVIDUAL',
      contact: 'Ann',
      mobile: '13800138000',
      email: 'a@b.com',
      settlementMethod: 'CASH',
      creditLimit: 1000,
      status: 'ACTIVE'
    })
    expect(updateCustomer).toHaveBeenCalled()
    expect(onSuccess).toHaveBeenCalledWith('updated')
    expect(onCompleted).toHaveBeenCalled()
  })
})
