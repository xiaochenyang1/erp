import { describe, it, expect, vi, beforeEach } from 'vitest'

// mock 掉真实 HTTP 层：只验证 API 层对后端响应的归一化(雪花 ID → 字符串)。
// 这是最高价值的确定性逻辑 —— 后端 Long ID 若被前端当 number 处理会丢精度。
const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock('@/utils/request', () => ({
  request: {
    get: (...args: unknown[]) => get(...args),
    post: (...args: unknown[]) => post(...args),
    put: (...args: unknown[]) => put(...args),
    delete: vi.fn()
  }
}))

import {
  login,
  getUserInfo,
  getRuntimeMenuTree,
  updatePreferences
} from '@/api/auth'

beforeEach(() => {
  get.mockReset()
  post.mockReset()
  put.mockReset()
})

describe('auth API 归一化', () => {
  it('login 把 user.id 大整数归一化为字符串', async () => {
    // 后端 JacksonConfig 契约:Long 序列化为 JSON 字符串,避免前端 number 丢精度。
    post.mockResolvedValue({
      accessToken: 'a',
      refreshToken: 'r',
      user: { id: '9007199254740993', username: 'admin' },
      permissions: ['system:user:view']
    })
    const res = await login({ username: 'admin', password: 'x' })
    expect(res.user.id).toBe('9007199254740993')
    expect(typeof res.user.id).toBe('string')
    expect(res.permissions).toEqual(['system:user:view'])
  })

  it('login 归一化 dataScope.warehouseIds 为字符串数组', async () => {
    post.mockResolvedValue({
      accessToken: 'a',
      refreshToken: 'r',
      user: {
        id: '1',
        username: 'admin',
        dataScope: { hasAllScope: false, warehouseIds: ['9007199254740995', '9007199254740997'] }
      },
      permissions: []
    })
    const res = await login({ username: 'admin', password: 'x' })
    expect(res.user.dataScope?.warehouseIds).toEqual(['9007199254740995', '9007199254740997'])
  })

  it('getUserInfo 归一化 id 并保留 permissions', async () => {
    get.mockResolvedValue({ id: '9007199254740993', username: 'runtime_smoke', permissions: ['dashboard:view'] })
    const res = await getUserInfo()
    expect(res.id).toBe('9007199254740993')
    expect(res.permissions).toEqual(['dashboard:view'])
  })

  it('getRuntimeMenuTree 递归归一化 id/parentId 为字符串', async () => {
    get.mockResolvedValue([
      {
        id: 5001,
        parentId: null,
        path: '/system',
        children: [{ id: 5002, parentId: 5001, path: '/system/users', children: [] }]
      }
    ])
    const tree = await getRuntimeMenuTree()
    expect(tree[0].id).toBe('5001')
    expect(tree[0].parentId).toBeUndefined()
    expect(tree[0].children?.[0].id).toBe('5002')
    expect(tree[0].children?.[0].parentId).toBe('5001')
  })

  it('updatePreferences 调用专用偏好接口', async () => {
    put.mockResolvedValue({ id: '1', username: 'admin', locale: 'en-US', timeZone: 'UTC' })
    const result = await updatePreferences({ locale: 'en-US', timeZone: 'UTC' })
    expect(put).toHaveBeenCalledWith('/auth/preferences', { locale: 'en-US', timeZone: 'UTC' })
    expect(result.locale).toBe('en-US')
  })
})
