import { describe, expect, it, vi } from 'vitest'

import type { WorkCenter } from '@/api/production'
import { useProductionWorkCenterForm } from './useProductionWorkCenterForm'

const t = (key: string) => key

const createForm = (overrides: Partial<Parameters<typeof useProductionWorkCenterForm>[1]> = {}) =>
  useProductionWorkCenterForm(t, {
    createWorkCenter: vi.fn(async () => ({})),
    updateWorkCenter: vi.fn(async () => ({})),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('production work center form', () => {
  it('opens create and edit forms', () => {
    const form = createForm()
    form.handleAdd()
    expect(form.dialogVisible.value).toBe(true)
    expect(form.isEdit.value).toBe(false)
    expect(form.dialogTitle.value).toBe('productionWorkCenter.dialog.create')

    form.handleEdit({
      id: 'wc1',
      workCenterCode: 'WC01',
      workCenterName: '装配',
      remark: '备注'
    } as WorkCenter)
    expect(form.isEdit.value).toBe(true)
    expect(form.formData).toMatchObject({
      id: 'wc1',
      workCenterCode: 'WC01',
      workCenterName: '装配',
      remark: '备注'
    })
    expect(form.dialogTitle.value).toBe('productionWorkCenter.dialog.edit')
  })

  it('creates and updates work centers', async () => {
    const createWorkCenter = vi.fn(async () => ({}))
    const updateWorkCenter = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const form = createForm({ createWorkCenter, updateWorkCenter, onSubmitted })

    form.handleAdd()
    form.formData.workCenterCode = 'WC02'
    form.formData.workCenterName = '机加'
    expect(await form.handleSubmit()).toBe(true)
    expect(createWorkCenter).toHaveBeenCalledWith({
      workCenterCode: 'WC02',
      workCenterName: '机加',
      remark: ''
    })

    form.handleEdit({
      id: 'wc1',
      workCenterCode: 'WC01',
      workCenterName: '装配',
      remark: ''
    } as WorkCenter)
    form.formData.workCenterName = '装配线'
    expect(await form.handleSubmit()).toBe(true)
    expect(updateWorkCenter).toHaveBeenCalledWith('wc1', {
      workCenterName: '装配线',
      remark: ''
    })
    expect(onSubmitted).toHaveBeenCalled()
  })
})
