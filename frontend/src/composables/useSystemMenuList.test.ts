import { describe, expect, it, vi } from 'vitest'

import type { Menu } from '@/api/system'
import { useSystemMenuList } from './useSystemMenuList'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.name != null ? `${key}:${params.name}` : key

const row = (overrides: Partial<Menu> = {}) =>
  ({ id: 'm1', name: '系统', status: 'ACTIVE', type: 'MENU', orderNum: 1, ...overrides }) as Menu

const createList = (overrides: Partial<Parameters<typeof useSystemMenuList>[1]> = {}) =>
  useSystemMenuList(t, {
    getMenuTree: vi.fn(async () => [row()]),
    deleteMenu: vi.fn(async () => ({})),
    enableMenu: vi.fn(async () => ({})),
    buildParentTree: (data) => [{
      id: '0',
      name: 'root',
      children: data,
      orderNum: 0,
      type: 'MENU',
      status: 'ACTIVE'
    }],
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('system menu list', () => {
  it('loads tree data into table and parent options', async () => {
    const list = createList()
    expect(await list.loadData()).toBe(true)
    expect(list.tableData.value).toEqual([row()])
    expect(list.menuTree.value[0].children).toEqual([row()])
  })

  it('reports load failures', async () => {
    const onError = vi.fn()
    const list = createList({
      getMenuTree: vi.fn(async () => {
        throw new Error('boom')
      }),
      onError
    })
    expect(await list.loadData()).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemMenu.message.loadFailed')
  })

  it('disables and enables after confirmation', async () => {
    const deleteMenu = vi.fn(async () => ({}))
    const enableMenu = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({ deleteMenu, enableMenu, onSuccess })

    expect(await list.handleDisable(row())).toBe(true)
    expect(deleteMenu).toHaveBeenCalledWith('m1')
    expect(onSuccess).toHaveBeenCalledWith('systemMenu.message.disabled')

    expect(await list.handleEnable(row({ status: 'INACTIVE' }))).toBe(true)
    expect(enableMenu).toHaveBeenCalledWith('m1')
    expect(onSuccess).toHaveBeenCalledWith('systemMenu.message.enabled')
  })

  it('aborts disable when confirmation is cancelled', async () => {
    const deleteMenu = vi.fn(async () => ({}))
    const list = createList({
      confirm: vi.fn(async () => {
        throw new Error('cancel')
      }),
      deleteMenu
    })
    expect(await list.handleDisable(row())).toBe(false)
    expect(deleteMenu).not.toHaveBeenCalled()
  })
})
