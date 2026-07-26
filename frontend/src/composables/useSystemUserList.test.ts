import { describe, expect, it, vi } from 'vitest'

import type { User } from '@/api/system'
import { useSystemUserList } from './useSystemUserList'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.username != null ? `${key}:${params.username}` : key

const row = (overrides: Partial<User> = {}) =>
  ({ id: 'u1', username: 'alice', realName: 'Alice', status: 'ACTIVE', ...overrides }) as User

const createList = (overrides: Partial<Parameters<typeof useSystemUserList>[1]> = {}) =>
  useSystemUserList(t, {
    getUsers: vi.fn(async () => ({ records: [row()], total: 1 } as any)),
    getDeptTree: vi.fn(async () => [{ id: '1', name: '总部' }] as any),
    getAllPosts: vi.fn(async () => [{ id: 'p1', name: '工程师' }] as any),
    getAllRoles: vi.fn(async () => [{ id: 'r1', name: '管理员' }] as any),
    deleteUser: vi.fn(async () => ({})),
    enableUser: vi.fn(async () => ({})),
    resetUserPassword: vi.fn(async () => ({})),
    confirm: vi.fn(async () => true),
    prompt: vi.fn(async () => ({ value: 'NewPass1!' })),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('system user list', () => {
  it('sends filled filters and resets paging on query', async () => {
    const getUsers = vi.fn(async () => ({ records: [], total: 4 } as any))
    const list = createList({ getUsers })

    list.queryParams.pageNo = 5
    list.queryParams.keyword = 'al'
    list.queryParams.deptId = '1'
    list.queryParams.status = 'ACTIVE'
    await list.handleQuery()

    expect(getUsers).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 20,
      keyword: 'al',
      deptId: '1',
      postId: undefined,
      status: 'ACTIVE'
    })
    expect(list.total.value).toBe(4)
  })

  it('pages and resets independently', async () => {
    const getUsers = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getUsers })

    await list.handlePageChange(3)
    expect(list.queryParams.pageNo).toBe(3)

    await list.handleSizeChange(50)
    expect(list.queryParams.pageSize).toBe(50)
    expect(list.queryParams.pageNo).toBe(1)

    list.queryParams.keyword = 'x'
    await list.handleReset()
    expect(list.queryParams.keyword).toBe('')
    expect(list.queryParams.pageNo).toBe(1)
  })

  it('loads options and reports load failures', async () => {
    const list = createList()
    await list.loadOptions()
    expect(list.depts.value).toEqual([{ id: '1', name: '总部' }])
    expect(list.posts.value).toEqual([{ id: 'p1', name: '工程师' }])
    expect(list.roles.value).toEqual([{ id: 'r1', name: '管理员' }])

    const onError = vi.fn()
    const failing = createList({
      getUsers: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await failing.loadData()
    expect(onError).toHaveBeenCalledWith('systemUsers.message.loadFailed')
  })

  it('disables, enables and resets passwords after confirmation', async () => {
    const deleteUser = vi.fn(async () => ({}))
    const enableUser = vi.fn(async () => ({}))
    const resetUserPassword = vi.fn(async () => ({}))
    const confirm = vi.fn(async () => true)
    const prompt = vi.fn(async () => ({ value: 'Secret1!' }))
    const onSuccess = vi.fn()
    const list = createList({ deleteUser, enableUser, resetUserPassword, confirm, prompt, onSuccess })

    expect(await list.handleDisable(row())).toBe(true)
    expect(deleteUser).toHaveBeenCalledWith('u1')
    expect(onSuccess).toHaveBeenCalledWith('systemUsers.message.disableSuccess')

    expect(await list.handleEnable(row({ status: 'INACTIVE' }))).toBe(true)
    expect(enableUser).toHaveBeenCalledWith('u1')

    expect(await list.handleResetPassword(row())).toBe(true)
    expect(resetUserPassword).toHaveBeenCalledWith('u1', 'Secret1!')
    expect(onSuccess).toHaveBeenCalledWith('systemUsers.message.passwordReset')
  })

  it('aborts disable when confirmation is cancelled', async () => {
    const deleteUser = vi.fn(async () => ({}))
    const list = createList({
      confirm: vi.fn(async () => { throw new Error('cancel') }),
      deleteUser
    })
    expect(await list.handleDisable(row())).toBe(false)
    expect(deleteUser).not.toHaveBeenCalled()
  })
})
