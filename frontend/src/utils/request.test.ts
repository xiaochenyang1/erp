import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const axiosMock = vi.hoisted(() => {
  const requestUse = vi.fn()
  const responseUse = vi.fn()
  const service = Object.assign(vi.fn(), {
    interceptors: {
      request: { use: requestUse },
      response: { use: responseUse }
    },
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn()
  })
  return {
    refreshPost: vi.fn(),
    responseUse,
    service
  }
})

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => axiosMock.service),
    post: axiosMock.refreshPost
  }
}))

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), success: vi.fn() }
}))

import { configureAuthSessionHandlers } from './authSession'
import { useMenuStore } from '@/store/modules/menu'
import { useUserStore } from '@/store/modules/user'
import './request'

const resetRuntimeState = vi.fn()
const redirectToLogin = vi.fn()
const rejectResponse = axiosMock.responseUse.mock.calls[0]?.[1] as (
  error: unknown
) => Promise<unknown>

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  axiosMock.refreshPost.mockReset()
  resetRuntimeState.mockReset()
  resetRuntimeState.mockImplementation(() => useUserStore().clearSessionState())
  redirectToLogin.mockReset()
  configureAuthSessionHandlers({ resetRuntimeState, redirectToLogin })
})

afterEach(() => {
  configureAuthSessionHandlers({})
})

describe('request authentication recovery', () => {
  it('clears persisted and runtime state when silent refresh fails', async () => {
    const refreshFailure = new Error('refresh rejected')
    axiosMock.refreshPost.mockRejectedValue(refreshFailure)
    const userStore = useUserStore()
    const menuStore = useMenuStore()
    userStore.token = 'expired-access'
    userStore.userInfo = { id: '1', username: 'previous-user' }
    userStore.permissions = ['finance:receipt:view']
    menuStore.menuTree = [{ id: '10', menuType: 'MENU', path: '/finance/payments' }]
    menuStore.loaded = true
    localStorage.setItem('token', 'expired-access')
    localStorage.setItem('refreshToken', 'expired-refresh')

    await expect(rejectResponse({
      config: { url: '/finance/payments', headers: {} },
      response: { status: 401 }
    })).rejects.toBe(refreshFailure)

    expect(axiosMock.refreshPost).toHaveBeenCalledWith(
      expect.stringMatching(/\/auth\/refresh$/),
      { refreshToken: 'expired-refresh' }
    )
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
    expect(resetRuntimeState).toHaveBeenCalledTimes(1)
    expect(redirectToLogin).toHaveBeenCalledTimes(1)
    expect(userStore.token).toBe('')
    expect(userStore.userInfo).toBeNull()
    expect(userStore.permissions).toEqual([])
    expect(menuStore.menuTree).toEqual([])
    expect(menuStore.loaded).toBe(false)
  })
})
