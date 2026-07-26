import { computed } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type { Warehouse } from '@/api/masterdata'
import { useWarehouseForm } from './useWarehouseForm'

const texts = computed(() => ({
  editWarehouse: 'Edit',
  createWarehouse: 'Create',
  validationEnterCode: 'code',
  validationCodeLength: 'code length',
  validationEnterName: 'name',
  validationNameLength: 'name length',
  validationDepartment: 'department',
  validationManager: 'manager',
  updateSuccess: 'updated',
  createSuccess: 'created',
  updateFailed: 'update failed',
  createFailed: 'create failed'
}))

describe('warehouse form', () => {
  it('creates and edits warehouse form state', async () => {
    const createWarehouse = vi.fn(async () => ({}))
    const updateWarehouse = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const onCompleted = vi.fn()
    const form = useWarehouseForm(texts, {
      createWarehouse,
      updateWarehouse,
      onSuccess,
      onCompleted
    })

    form.handleCreate()
    expect(form.dialogVisible.value).toBe(true)
    expect(form.dialogTitle.value).toBe('Create')
    expect(form.formData.status).toBe('ACTIVE')

    form.handleEdit({
      id: '1',
      warehouseCode: 'W001',
      warehouseName: 'Main',
      deptId: '10',
      managerUserId: '20',
      address: 'A1',
      status: 'ACTIVE'
    } as Warehouse)
    expect(form.formData.id).toBe('1')
    expect(form.formData.deptId).toBe('10')
    expect(form.dialogTitle.value).toBe('Edit')

    await form.handleSubmit({
      code: 'W001',
      name: 'Main',
      deptId: '10',
      managerUserId: '20',
      address: 'A1',
      status: 'ACTIVE'
    })
    expect(updateWarehouse).toHaveBeenCalled()
    expect(onSuccess).toHaveBeenCalledWith('updated')
    expect(onCompleted).toHaveBeenCalled()
  })
})
