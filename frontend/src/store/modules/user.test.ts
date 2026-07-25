import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// user store 依赖 element-plus / auth API，测试只关心权限状态与登录态，
// 把这些副作用依赖 mock 掉。
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn() } }))
const loginMock = vi.fn()
const getUserInfoMock = vi.fn()
vi.mock('@/api/auth', () => ({
  login: (...args: unknown[]) => loginMock(...args),
  logout: vi.fn(),
  getUserInfo: (...args: unknown[]) => getUserInfoMock(...args)
}))

import { useUserStore } from '@/store/modules/user'
import { useMenuStore } from '@/store/modules/menu'

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  loginMock.mockReset()
  getUserInfoMock.mockReset()
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

  it('getUserInfo 应用后端返回的语言与时区偏好', async () => {
    getUserInfoMock.mockResolvedValue({
      id: '1', username: 'admin', locale: 'en-US', timeZone: 'UTC', permissions: []
    })
    const store = useUserStore()
    await store.getUserInfo()
    expect(localStorage.getItem('locale')).toBe('en-US')
    expect(localStorage.getItem('timeZone')).toBe('UTC')
    expect(document.documentElement.lang).toBe('en-US')
  })

  it('getUserInfo 失败时清空用户、权限和上一账号的运行时菜单', async () => {
    const failure = new Error('user info unavailable')
    getUserInfoMock.mockRejectedValue(failure)
    const store = useUserStore()
    const menuStore = useMenuStore()
    store.token = 'stale-access'
    store.userInfo = { id: '1', username: 'previous-user' }
    store.permissions = ['finance:receipt:view']
    menuStore.menuTree = [{ id: '10', menuType: 'MENU', path: '/finance/payments' }]
    menuStore.loaded = true
    localStorage.setItem('token', 'stale-access')
    localStorage.setItem('refreshToken', 'stale-refresh')
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})

    await expect(store.getUserInfo()).rejects.toBe(failure)

    consoleError.mockRestore()
    expect(store.token).toBe('')
    expect(store.userInfo).toBeNull()
    expect(store.permissions).toEqual([])
    expect(menuStore.menuTree).toEqual([])
    expect(menuStore.loaded).toBe(false)
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
  })
})
