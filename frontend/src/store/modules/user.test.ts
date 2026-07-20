import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// user store 依赖 router / element-plus / auth API，测试只关心权限状态与登录态，
// 把这些副作用依赖 mock 掉。
vi.mock('@/router', () => ({ default: { push: vi.fn() } }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn() } }))
const loginMock = vi.fn()
vi.mock('@/api/auth', () => ({
  login: (...args: unknown[]) => loginMock(...args),
  logout: vi.fn(),
  getUserInfo: vi.fn()
}))

import { useUserStore } from '@/store/modules/user'

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  loginMock.mockReset()
})

describe('user store', () => {
  it('hasPermission 对空权限码恒放行', () => {
    const store = useUserStore()
    expect(store.hasPermission('')).toBe(true)
  })

  it('hasPermission 命中/未命中', () => {
    const store = useUserStore()
    store.permissions = ['production:routing:view', 'production:routing:create']
    expect(store.hasPermission('production:routing:view')).toBe(true)
    expect(store.hasPermission('production:routing:delete')).toBe(false)
  })

  it('doLogin 写入 token 与权限并落 localStorage', async () => {
    loginMock.mockResolvedValue({
      accessToken: 'access-x',
      refreshToken: 'refresh-y',
      user: { id: '1', username: 'admin' },
      permissions: ['dashboard:view']
    })
    const store = useUserStore()
    await store.doLogin({ username: 'admin', password: 'x' })
    expect(store.token).toBe('access-x')
    expect(store.permissions).toEqual(['dashboard:view'])
    expect(localStorage.getItem('token')).toBe('access-x')
    expect(localStorage.getItem('refreshToken')).toBe('refresh-y')
  })
})
