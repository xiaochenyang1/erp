import { describe, expect, it, vi } from 'vitest'

import type { ExceptionSlaPolicy } from '@/api/exceptionSlaPolicy'
import { useExceptionSlaPolicyForm } from './useExceptionSlaPolicyForm'

const t = (key: string) => key

const policy = {
  id: 'p1',
  category: 'GENERAL',
  priority: 'HIGH',
  dueHours: 24,
  escalationEnabled: true,
  escalateToPriority: 'URGENT',
  enabled: true,
  remark: '备注'
} as ExceptionSlaPolicy

const createForm = (overrides: Partial<Parameters<typeof useExceptionSlaPolicyForm>[1]> = {}) =>
  useExceptionSlaPolicyForm(t, {
    updatePolicy: vi.fn(async () => ({})),
    categoryLabel: (value) => `cat:${value}`,
    priorityLabel: (value) => `pri:${value}`,
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('exception SLA policy form', () => {
  it('opens the edit dialog and builds the target label', () => {
    const form = createForm()
    form.openEditDialog(policy)

    expect(form.editDialogVisible.value).toBe(true)
    expect(form.editTargetLabel.value).toBe('cat:GENERAL / pri:HIGH')
    expect(form.editForm).toMatchObject({
      dueHours: 24,
      escalationEnabled: true,
      escalateToPriority: 'URGENT',
      enabled: true,
      remark: '备注'
    })
  })

  it('saves trimmed remark and refreshes the list', async () => {
    const updatePolicy = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const form = createForm({ updatePolicy, onSubmitted })
    form.openEditDialog(policy)
    form.editForm.dueHours = 12
    form.editForm.remark = '  更新  '

    expect(await form.handleSaveEdit()).toBe(true)
    expect(updatePolicy).toHaveBeenCalledWith('p1', {
      dueHours: 12,
      escalationEnabled: true,
      escalateToPriority: 'URGENT',
      enabled: true,
      remark: '更新'
    })
    expect(form.editDialogVisible.value).toBe(false)
    expect(onSubmitted).toHaveBeenCalled()
  })

  it('reports save failures without closing the dialog', async () => {
    const onError = vi.fn()
    const form = createForm({
      updatePolicy: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    form.openEditDialog(policy)

    expect(await form.handleSaveEdit()).toBe(false)
    expect(form.editDialogVisible.value).toBe(true)
    expect(onError).toHaveBeenCalledWith('exceptionSlaPolicy.message.saveFailed')
    expect(form.editSubmitting.value).toBe(false)
  })

  it('no-ops save when no target is selected', async () => {
    const updatePolicy = vi.fn(async () => ({}))
    const form = createForm({ updatePolicy })
    expect(await form.handleSaveEdit()).toBe(false)
    expect(updatePolicy).not.toHaveBeenCalled()
  })
})
