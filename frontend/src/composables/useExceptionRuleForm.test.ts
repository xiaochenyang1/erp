import { describe, expect, it, vi } from 'vitest'

import type { ExceptionRule } from '@/api/exceptionRule'
import { useExceptionRuleForm } from './useExceptionRuleForm'

const t = (key: string) => key

const rule = {
  id: 'r1',
  ruleCode: 'R1',
  ruleName: '低库存',
  ruleType: 'LOW_STOCK',
  category: 'STOCK',
  priority: 'HIGH',
  thresholdValue: 10,
  thresholdUnit: 'QTY',
  enabled: true,
  assigneeUserId: '8',
  scheduleIntervalMinutes: 30,
  remark: '备注'
} as ExceptionRule

const createForm = (overrides: Partial<Parameters<typeof useExceptionRuleForm>[1]> = {}) =>
  useExceptionRuleForm(t, {
    updateRule: vi.fn(async () => ({})),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('exception rule form', () => {
  it('opens the edit dialog with current rule values', () => {
    const form = createForm()
    form.openEditDialog(rule)

    expect(form.editDialogVisible.value).toBe(true)
    expect(form.editTarget.value?.id).toBe('r1')
    expect(form.editForm).toMatchObject({
      thresholdValue: 10,
      thresholdUnit: 'QTY',
      priority: 'HIGH',
      assigneeUserId: '8',
      scheduleIntervalMinutes: 30,
      remark: '备注'
    })
  })

  it('saves trimmed optional fields and refreshes the list', async () => {
    const updateRule = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const form = createForm({ updateRule, onSubmitted })
    form.openEditDialog(rule)
    form.editForm.assigneeUserId = ' 3 '
    form.editForm.remark = '  更新  '

    expect(await form.handleSaveEdit()).toBe(true)
    expect(updateRule).toHaveBeenCalledWith('r1', {
      thresholdValue: 10,
      thresholdUnit: 'QTY',
      priority: 'HIGH',
      assigneeUserId: '3',
      scheduleIntervalMinutes: 30,
      remark: '更新'
    })
    expect(form.editDialogVisible.value).toBe(false)
    expect(onSubmitted).toHaveBeenCalled()
  })

  it('reports save failures without closing the dialog', async () => {
    const onError = vi.fn()
    const form = createForm({
      updateRule: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    form.openEditDialog(rule)

    expect(await form.handleSaveEdit()).toBe(false)
    expect(form.editDialogVisible.value).toBe(true)
    expect(onError).toHaveBeenCalledWith('exceptionRule.message.saveFailed')
    expect(form.editSubmitting.value).toBe(false)
  })

  it('no-ops save when no target is selected', async () => {
    const updateRule = vi.fn(async () => ({}))
    const form = createForm({ updateRule })
    expect(await form.handleSaveEdit()).toBe(false)
    expect(updateRule).not.toHaveBeenCalled()
  })
})
