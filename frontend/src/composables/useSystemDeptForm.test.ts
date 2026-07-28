import { describe, expect, it, vi } from 'vitest'

import type { Dept } from '@/api/system'
import { useSystemDeptForm } from './useSystemDeptForm'

const t = (key: string) => key

const detail = {
  id: 'd1',
  parentId: '0',
  name: '研发',
  code: 'RD',
  manager: 'Alice',
  contact: '138',
  orderNum: 2,
  status: 'ACTIVE'
} as unknown as Dept

const createForm = (overrides: Partial<Parameters<typeof useSystemDeptForm>[1]> = {}) =>
  useSystemDeptForm(t, {
    getDept: vi.fn(async () => detail),
    createDept: vi.fn(async () => ({})),
    updateDept: vi.fn(async () => ({})),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('system dept form', () => {
  it('opens create under optional parent and edit dialogs', async () => {
    const form = createForm()
    form.handleCreate(null)
    expect(form.dialogVisible.value).toBe(true)
    expect(form.isEdit.value).toBe(false)
    expect(form.formData.parentId).toBeUndefined()

    form.handleCreate({ id: 'p1', name: '总部', orderNum: 0, status: 'ACTIVE' } as Dept)
    expect(form.formData.parentId).toBe('p1')

    expect(await form.handleEdit({ id: 'd1' } as Dept)).toBe(true)
    expect(form.isEdit.value).toBe(true)
    expect(form.formData).toMatchObject({
      name: '研发',
      code: 'RD',
      manager: 'Alice',
      contact: '138',
      orderNum: 2
    })
  })

  it('creates and updates departments', async () => {
    const createDept = vi.fn(async () => ({}))
    const updateDept = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const form = createForm({ createDept, updateDept, onSubmitted })

    form.handleCreate(null)
    form.formData.name = '销售'
    form.formData.code = 'SALES'
    form.formData.orderNum = 3
    expect(await form.handleSubmit()).toBe(true)
    expect(createDept).toHaveBeenCalledWith(expect.objectContaining({
      name: '销售',
      code: 'SALES',
      orderNum: 3
    }))

    await form.handleEdit({ id: 'd1' } as Dept)
    form.formData.name = '研发二部'
    expect(await form.handleSubmit()).toBe(true)
    expect(updateDept).toHaveBeenCalledWith('d1', expect.objectContaining({
      name: '研发二部'
    }))
    expect(onSubmitted).toHaveBeenCalled()
  })

  it('reports detail and save failures', async () => {
    const onError = vi.fn()
    const form = createForm({
      getDept: vi.fn(async () => {
        throw new Error('boom')
      }),
      createDept: vi.fn(async () => {
        throw new Error('boom')
      }),
      onError
    })

    expect(await form.handleEdit({ id: 'd1' } as Dept)).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemDept.message.detailLoadFailed')

    form.handleCreate(null)
    form.formData.name = 'X'
    expect(await form.handleSubmit()).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemDept.message.saveFailed')
  })
})
