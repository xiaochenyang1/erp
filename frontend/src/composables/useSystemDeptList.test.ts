import { describe, expect, it, vi } from 'vitest'

import type { Dept } from '@/api/system'
import { useSystemDeptList } from './useSystemDeptList'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.name != null ? `${key}:${params.name}` : key

const row = (overrides: Partial<Dept> = {}) =>
  ({ id: 'd1', name: '研发', status: 'ACTIVE', orderNum: 1, ...overrides }) as Dept

const createList = (overrides: Partial<Parameters<typeof useSystemDeptList>[1]> = {}) =>
  useSystemDeptList(t, {
    getDeptTree: vi.fn(async () => [row()]),
    deleteDept: vi.fn(async () => ({})),
    enableDept: vi.fn(async () => ({})),
    buildParentTree: (data) => [{ id: '0', name: 'root', children: data, orderNum: 0, status: 'ACTIVE' }],
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('system dept list', () => {
  it('loads tree data into table and parent options', async () => {
    const list = createList()
    expect(await list.loadData()).toBe(true)
    expect(list.tableData.value).toEqual([row()])
    expect(list.deptTree.value[0].id).toBe('0')
    expect(list.deptTree.value[0].children).toEqual([row()])
  })

  it('reports load failures', async () => {
    const onError = vi.fn()
    const list = createList({
      getDeptTree: vi.fn(async () => {
        throw new Error('boom')
      }),
      onError
    })
    expect(await list.loadData()).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemDept.message.loadFailed')
  })

  it('disables and enables after confirmation', async () => {
    const deleteDept = vi.fn(async () => ({}))
    const enableDept = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({ deleteDept, enableDept, onSuccess })

    expect(await list.handleDisable(row())).toBe(true)
    expect(deleteDept).toHaveBeenCalledWith('d1')
    expect(onSuccess).toHaveBeenCalledWith('systemDept.message.disabled')

    expect(await list.handleEnable(row({ status: 'INACTIVE' }))).toBe(true)
    expect(enableDept).toHaveBeenCalledWith('d1')
    expect(onSuccess).toHaveBeenCalledWith('systemDept.message.enabled')
  })

  it('aborts disable when confirmation is cancelled', async () => {
    const deleteDept = vi.fn(async () => ({}))
    const list = createList({
      confirm: vi.fn(async () => {
        throw new Error('cancel')
      }),
      deleteDept
    })
    expect(await list.handleDisable(row())).toBe(false)
    expect(deleteDept).not.toHaveBeenCalled()
  })
})
