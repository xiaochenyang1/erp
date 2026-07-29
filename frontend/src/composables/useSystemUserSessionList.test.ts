import { describe, expect, it, vi } from 'vitest'

import type { UserSession } from '@/api/userSession'
import type { PageResponse } from '@/types/common'
import { useSystemUserSessionList } from './useSystemUserSessionList'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

const session = (overrides: Partial<UserSession> = {}): UserSession => ({
  id: 'session-1',
  userId: 'user-1',
  username: 'alice',
  status: 'ACTIVE',
  ...overrides
})

const page = (
  records: UserSession[] = [],
  total = records.length
): PageResponse<UserSession> => ({
  records,
  total,
  pageNo: 1,
  pageSize: 20
})

const createList = (
  overrides: Partial<Parameters<typeof useSystemUserSessionList>[1]> = {}
) => useSystemUserSessionList(t, {
  getUserSessions: vi.fn(async () => page([session()])),
  revokeUserSession: vi.fn(async () => ({})),
  revokeUserSessionsByUser: vi.fn(async () => ({})),
  sessionUserLabel: vi.fn((row) => row.username || String(row.userId)),
  confirm: vi.fn(async () => true),
  onError: vi.fn(),
  onSuccess: vi.fn(),
  ...overrides
})

describe('system user session list', () => {
  it('queries from the first page and stores the returned page', async () => {
    const getUserSessions = vi.fn(async () => page([session()], 37))
    const list = createList({ getUserSessions })
    list.queryForm.username = 'alice'
    list.queryForm.status = 'ACTIVE'
    list.pagination.page = 4

    expect(await list.handleQuery()).toBe(true)
    expect(getUserSessions).toHaveBeenCalledWith({
      username: 'alice',
      status: 'ACTIVE',
      pageNo: 1,
      pageSize: 20
    })
    expect(list.tableData.value).toEqual([session()])
    expect(list.pagination.total).toBe(37)
  })

  it('changes page and size, then resets filters and the current page', async () => {
    const getUserSessions = vi.fn(async () => page())
    const list = createList({ getUserSessions })

    expect(await list.handlePageChange(3)).toBe(true)
    expect(list.pagination.page).toBe(3)
    expect(getUserSessions).toHaveBeenLastCalledWith(expect.objectContaining({
      pageNo: 3,
      pageSize: 20
    }))

    expect(await list.handleSizeChange(50)).toBe(true)
    expect(list.pagination).toMatchObject({ page: 1, size: 50 })
    expect(getUserSessions).toHaveBeenLastCalledWith(expect.objectContaining({
      pageNo: 1,
      pageSize: 50
    }))

    list.queryForm.username = 'alice'
    list.queryForm.status = 'REVOKED'
    list.pagination.page = 2
    expect(await list.handleReset()).toBe(true)
    expect(list.queryForm).toMatchObject({ username: '', status: '' })
    expect(list.pagination.page).toBe(1)
    expect(getUserSessions).toHaveBeenLastCalledWith({
      username: '',
      status: '',
      pageNo: 1,
      pageSize: 50
    })
  })

  it('silently stops cancelled revocations without calling either API', async () => {
    const revokeUserSession = vi.fn(async () => ({}))
    const revokeUserSessionsByUser = vi.fn(async () => ({}))
    const onError = vi.fn()
    const list = createList({
      confirm: vi.fn(async () => { throw 'cancel' }),
      onError,
      revokeUserSession,
      revokeUserSessionsByUser
    })

    expect(await list.handleRevoke(session())).toBe(false)
    expect(await list.handleRevokeUser(session())).toBe(false)
    expect(revokeUserSession).not.toHaveBeenCalled()
    expect(revokeUserSessionsByUser).not.toHaveBeenCalled()
    expect(onError).not.toHaveBeenCalled()
  })

  it('revokes one session by session id, notifies success and refreshes', async () => {
    const confirm = vi.fn(async () => true)
    const revokeUserSession = vi.fn(async () => ({}))
    const getUserSessions = vi.fn(async () => page())
    const onSuccess = vi.fn()
    const list = createList({ confirm, getUserSessions, onSuccess, revokeUserSession })

    expect(await list.handleRevoke(session({ id: '9007199254740993' }))).toBe(true)
    expect(confirm).toHaveBeenCalledWith(
      'userSessions.message.revokeConfirm:{"user":"alice"}',
      'userSessions.message.prompt',
      { type: 'warning' }
    )
    expect(revokeUserSession).toHaveBeenCalledWith('9007199254740993')
    expect(onSuccess).toHaveBeenCalledWith('userSessions.message.revoked')
    expect(getUserSessions).toHaveBeenCalledOnce()
  })

  it('revokes all sessions by user id, notifies success and refreshes', async () => {
    const revokeUserSessionsByUser = vi.fn(async () => ({}))
    const getUserSessions = vi.fn(async () => page())
    const onSuccess = vi.fn()
    const list = createList({
      getUserSessions,
      onSuccess,
      revokeUserSessionsByUser
    })

    expect(await list.handleRevokeUser(session({ userId: '9007199254740995' }))).toBe(true)
    expect(revokeUserSessionsByUser).toHaveBeenCalledWith('9007199254740995')
    expect(onSuccess).toHaveBeenCalledWith('userSessions.message.userRevoked')
    expect(getUserSessions).toHaveBeenCalledOnce()
  })

  it('reports single-session and user-session revoke failures', async () => {
    const getUserSessions = vi.fn(async () => page())
    const onError = vi.fn()
    const list = createList({
      getUserSessions,
      onError,
      revokeUserSession: vi.fn(async () => { throw new Error('single failed') }),
      revokeUserSessionsByUser: vi.fn(async () => { throw new Error('bulk failed') })
    })

    expect(await list.handleRevoke(session())).toBe(false)
    expect(onError).toHaveBeenCalledWith('userSessions.message.revokeFailed')
    expect(await list.handleRevokeUser(session())).toBe(false)
    expect(onError).toHaveBeenCalledWith('userSessions.message.revokeUserFailed')
    expect(getUserSessions).not.toHaveBeenCalled()
  })

  it('reports unexpected confirmation failures without calling revoke APIs', async () => {
    const revokeUserSession = vi.fn(async () => ({}))
    const revokeUserSessionsByUser = vi.fn(async () => ({}))
    const onError = vi.fn()
    const list = createList({
      confirm: vi.fn(async () => { throw new Error('dialog unavailable') }),
      onError,
      revokeUserSession,
      revokeUserSessionsByUser
    })

    expect(await list.handleRevoke(session())).toBe(false)
    expect(onError).toHaveBeenCalledWith('userSessions.message.revokeFailed')
    expect(await list.handleRevokeUser(session())).toBe(false)
    expect(onError).toHaveBeenCalledWith('userSessions.message.revokeUserFailed')
    expect(revokeUserSession).not.toHaveBeenCalled()
    expect(revokeUserSessionsByUser).not.toHaveBeenCalled()
  })

  it('reports list failures and always restores loading', async () => {
    let rejectRequest: ((reason?: unknown) => void) | undefined
    const getUserSessions = vi.fn(() => new Promise<PageResponse<UserSession>>((_, reject) => {
      rejectRequest = reject
    }))
    const onError = vi.fn()
    const list = createList({ getUserSessions, onError })

    const pending = list.loadData()
    expect(list.loading.value).toBe(true)
    rejectRequest?.(new Error('network'))

    expect(await pending).toBe(false)
    expect(list.loading.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('userSessions.message.loadFailed')
  })
})
