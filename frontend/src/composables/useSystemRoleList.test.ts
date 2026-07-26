import { describe, expect, it, vi } from 'vitest'

import type { Role } from '@/api/system'
import { useSystemRoleList } from './useSystemRoleList'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.name != null ? `${key}:${params.name}` : key

const row = (overrides: Partial<Role> = {}) =>
  ({ id: 'r1', code: 'ADMIN', name: '管理员', status: 'ACTIVE', ...overrides }) as Role

const createList = (overrides: Partial<Parameters<typeof useSystemRoleList>[1]> = {}) =>
  useSystemRoleList(t, {
    getRoles: vi.fn(async () => ({ records: [row()], total: 1 } as any)),
    deleteRole: vi.fn(async () => ({})),
    enableRole: vi.fn(async () => ({})),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('system role list', () => {
  it('sends filled filters and resets paging on query', async () => {
    const getRoles = vi.fn(async () => ({ records: [], total: 4 } as any))
    const list = createList({ getRoles })

    list.queryParams.pageNo = 3
    list.queryParams.code = 'AD'
    list.queryParams.name = '管'
    list.queryParams.status = 'ACTIVE'
    await list.handleQuery()

    expect(getRoles).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 10,
      code: 'AD',
      name: '管',
      status: 'ACTIVE'
    })
    expect(list.total.value).toBe(4)
  })

  it('pages and resets independently', async () => {
    const getRoles = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getRoles })

    await list.handlePageChange(2)
    expect(list.queryParams.pageNo).toBe(2)

    await list.handleSizeChange(50)
    expect(list.queryParams.pageSize).toBe(50)
    expect(list.queryParams.pageNo).toBe(1)

    list.queryParams.code = 'X'
    await list.handleReset()
    expect(list.queryParams.code).toBe('')
    expect(list.queryParams.pageNo).toBe(1)
  })

  it('disables and enables after confirmation', async () => {
    const deleteRole = vi.fn(async () => ({}))
    const enableRole = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({ deleteRole, enableRole, onSuccess })

    expect(await list.handleDisable(row())).toBe(true)
    expect(deleteRole).toHaveBeenCalledWith('r1')
    expect(onSuccess).toHaveBeenCalledWith('systemRoles.message.disableSuccess')

    expect(await list.handleEnable(row({ status: 'INACTIVE' }))).toBe(true)
    expect(enableRole).toHaveBeenCalledWith('r1')
    expect(onSuccess).toHaveBeenCalledWith('systemRoles.message.enableSuccess')
  })

  it('aborts disable when confirmation is cancelled', async () => {
    const deleteRole = vi.fn(async () => ({}))
    const list = createList({
      confirm: vi.fn(async () => { throw new Error('cancel') }),
      deleteRole
    })
    expect(await list.handleDisable(row())).toBe(false)
    expect(deleteRole).not.toHaveBeenCalled()
  })

  it('reports load failures', async () => {
    const onError = vi.fn()
    const list = createList({
      getRoles: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await list.loadData()
    expect(onError).toHaveBeenCalledWith('systemRoles.message.loadFailed')
    expect(list.loading.value).toBe(false)
  })
})
