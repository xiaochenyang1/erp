import { describe, expect, it, vi } from 'vitest'

import type { DictItem, DictType } from '@/api/system'
import { useSystemDictForm } from './useSystemDictForm'

const t = (key: string) => key

const type = {
  id: 't1',
  code: 'GENDER',
  name: '性别',
  status: 'ACTIVE',
  remark: '备注'
} as DictType

const item = {
  id: 'i1',
  typeCode: 'GENDER',
  label: '男',
  value: 'M',
  orderNum: 1,
  status: 'ACTIVE'
} as DictItem

const createForm = (overrides: Partial<Parameters<typeof useSystemDictForm>[1]> = {}) =>
  useSystemDictForm(t, {
    getDictType: vi.fn(async () => type),
    createDictType: vi.fn(async () => ({})),
    updateDictType: vi.fn(async () => ({})),
    createDictItem: vi.fn(async () => ({})),
    updateDictItem: vi.fn(async () => ({})),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('system dict form', () => {
  it('opens type create/edit dialogs and submits create/update', async () => {
    const createDictType = vi.fn(async () => ({}))
    const updateDictType = vi.fn(async () => ({}))
    const onTypeSubmitted = vi.fn()
    const form = createForm({ createDictType, updateDictType, onTypeSubmitted })

    form.handleAddType()
    expect(form.typeDialogVisible.value).toBe(true)
    expect(form.typeDialogTitle.value).toBe('systemDicts.dialog.addType')
    form.typeFormData.code = 'STATUS'
    form.typeFormData.name = '状态'
    expect(await form.handleTypeSubmit()).toBe(true)
    expect(createDictType).toHaveBeenCalledWith(expect.objectContaining({
      code: 'STATUS',
      name: '状态'
    }))

    expect(await form.handleEditType({ id: 't1' } as DictType)).toBe(true)
    form.typeFormData.name = '性别2'
    expect(await form.handleTypeSubmit()).toBe(true)
    expect(updateDictType).toHaveBeenCalledWith('t1', expect.objectContaining({ name: '性别2' }))
    expect(onTypeSubmitted).toHaveBeenCalled()
  })

  it('opens item create/edit dialogs and submits create/update', async () => {
    const createDictItem = vi.fn(async () => ({}))
    const updateDictItem = vi.fn(async () => ({}))
    const onItemSubmitted = vi.fn()
    const form = createForm({ createDictItem, updateDictItem, onItemSubmitted })

    form.handleAddItem('GENDER')
    expect(form.itemFormData.typeCode).toBe('GENDER')
    form.itemFormData.label = '女'
    form.itemFormData.value = 'F'
    expect(await form.handleItemSubmit()).toBe(true)
    expect(createDictItem).toHaveBeenCalledWith(expect.objectContaining({
      typeCode: 'GENDER',
      label: '女',
      value: 'F'
    }))

    form.handleEditItem(item)
    form.itemFormData.label = '男性'
    expect(await form.handleItemSubmit()).toBe(true)
    expect(updateDictItem).toHaveBeenCalledWith('i1', expect.objectContaining({ label: '男性' }))
    expect(onItemSubmitted).toHaveBeenCalled()
  })

  it('reports type detail load failures', async () => {
    const onError = vi.fn()
    const form = createForm({
      getDictType: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    expect(await form.handleEditType({ id: 't1' } as DictType)).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemDicts.message.loadTypeDetailFailed')
  })
})
