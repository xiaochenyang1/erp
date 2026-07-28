import { describe, expect, it, vi } from 'vitest'

import type { Menu } from '@/api/system'
import { useSystemMenuForm } from './useSystemMenuForm'

const t = (key: string) => key

const detail = {
  id: 'm1',
  parentId: '0',
  name: '用户管理',
  path: '/system/users',
  component: 'system/users/index',
  icon: 'User',
  orderNum: 2,
  type: 'MENU',
  permission: 'system:user:view',
  status: 'ACTIVE'
} as unknown as Menu

const createForm = (overrides: Partial<Parameters<typeof useSystemMenuForm>[1]> = {}) =>
  useSystemMenuForm(t, {
    getMenu: vi.fn(async () => detail),
    createMenu: vi.fn(async () => ({})),
    updateMenu: vi.fn(async () => ({})),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('system menu form', () => {
  it('opens create under optional parent and edit dialogs', async () => {
    const form = createForm()
    form.handleCreate(null)
    expect(form.dialogVisible.value).toBe(true)
    expect(form.isEdit.value).toBe(false)
    expect(form.formData.type).toBe('MENU')

    form.handleCreate({ id: 'p1', name: '系统', orderNum: 0, type: 'MENU', status: 'ACTIVE' } as Menu)
    expect(form.formData.parentId).toBe('p1')

    expect(await form.handleEdit({ id: 'm1' } as Menu)).toBe(true)
    expect(form.isEdit.value).toBe(true)
    expect(form.formData).toMatchObject({
      name: '用户管理',
      path: '/system/users',
      component: 'system/users/index',
      permission: 'system:user:view'
    })
  })

  it('creates and updates menus', async () => {
    const createMenu = vi.fn(async () => ({}))
    const updateMenu = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const form = createForm({ createMenu, updateMenu, onSubmitted })

    form.handleCreate(null)
    form.formData.name = '角色'
    form.formData.path = '/system/roles'
    form.formData.type = 'MENU'
    expect(await form.handleSubmit()).toBe(true)
    expect(createMenu).toHaveBeenCalledWith(expect.objectContaining({
      name: '角色',
      path: '/system/roles',
      type: 'MENU'
    }))

    await form.handleEdit({ id: 'm1' } as Menu)
    form.formData.name = '用户'
    expect(await form.handleSubmit()).toBe(true)
    expect(updateMenu).toHaveBeenCalledWith('m1', expect.objectContaining({
      name: '用户'
    }))
    expect(onSubmitted).toHaveBeenCalled()
  })

  it('reports detail and save failures', async () => {
    const onError = vi.fn()
    const form = createForm({
      getMenu: vi.fn(async () => {
        throw new Error('boom')
      }),
      createMenu: vi.fn(async () => {
        throw new Error('boom')
      }),
      onError
    })

    expect(await form.handleEdit({ id: 'm1' } as Menu)).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemMenu.message.detailLoadFailed')

    form.handleCreate(null)
    form.formData.name = 'X'
    expect(await form.handleSubmit()).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemMenu.message.saveFailed')
  })
})
