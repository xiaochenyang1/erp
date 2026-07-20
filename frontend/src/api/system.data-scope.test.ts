import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
const put = vi.fn()

vi.mock('@/utils/request', () => ({
  request: {
    get: (...args: unknown[]) => get(...args),
    put: (...args: unknown[]) => put(...args),
    post: vi.fn(),
    delete: vi.fn()
  }
}))

import {
  assignRoleDataScope,
  assignUserDataScope,
  getAssignedRoleDataScope,
  getAssignedUserDataScope
} from '@/api/system'

beforeEach(() => {
  get.mockReset()
  put.mockReset()
})

describe('user data-scope API 归一化', () => {
  it('getAssignedUserDataScope 把 userId/warehouseIds/effective* 大整数归一化为字符串', async () => {
    get.mockResolvedValue({
      userId: '9007199254740993',
      hasAllScope: false,
      deptScoped: true,
      postScoped: false,
      selfScoped: true,
      warehouseIds: ['9007199254740995', '9007199254740997'],
      effectiveHasAllScope: false,
      effectiveDeptScoped: true,
      effectivePostScoped: true,
      effectiveSelfScoped: true,
      effectiveWarehouseIds: ['9007199254740995', '9007199254740999']
    })

    const scope = await getAssignedUserDataScope('9007199254740993')

    expect(scope.userId).toBe('9007199254740993')
    expect(scope.deptScoped).toBe(true)
    expect(scope.selfScoped).toBe(true)
    expect(scope.warehouseIds).toEqual(['9007199254740995', '9007199254740997'])
    expect(scope.effectivePostScoped).toBe(true)
    expect(scope.effectiveWarehouseIds).toEqual(['9007199254740995', '9007199254740999'])
    expect(get).toHaveBeenCalledWith('/system/users/9007199254740993/data-scope')
  })

  it('assignUserDataScope 提交布尔与仓库列表并归一化响应', async () => {
    put.mockResolvedValue({
      userId: 42,
      hasAllScope: true,
      deptScoped: true,
      postScoped: true,
      selfScoped: true,
      warehouseIds: [1, 2],
      effectiveHasAllScope: true,
      effectiveDeptScoped: false,
      effectivePostScoped: false,
      effectiveSelfScoped: false,
      effectiveWarehouseIds: []
    })

    const scope = await assignUserDataScope(42, {
      hasAllScope: true,
      deptScoped: true,
      postScoped: true,
      selfScoped: true,
      warehouseIds: [1, 2]
    })

    expect(put).toHaveBeenCalledWith('/system/users/42/data-scope', {
      hasAllScope: true,
      deptScoped: true,
      postScoped: true,
      selfScoped: true,
      warehouseIds: [1, 2]
    })
    expect(scope.userId).toBe('42')
    expect(scope.hasAllScope).toBe(true)
    expect(scope.effectiveHasAllScope).toBe(true)
    expect(scope.warehouseIds).toEqual(['1', '2'])
  })
})

describe('role data-scope API 归一化', () => {
  it('getAssignedRoleDataScope 把 roleId/warehouseIds 归一化为字符串', async () => {
    get.mockResolvedValue({
      roleId: '9007199254741001',
      hasAllScope: false,
      deptScoped: false,
      postScoped: true,
      selfScoped: false,
      warehouseIds: ['9007199254741003']
    })

    const scope = await getAssignedRoleDataScope('9007199254741001')

    expect(scope.roleId).toBe('9007199254741001')
    expect(scope.postScoped).toBe(true)
    expect(scope.warehouseIds).toEqual(['9007199254741003'])
    expect(get).toHaveBeenCalledWith('/system/roles/9007199254741001/data-scope')
  })

  it('assignRoleDataScope 提交并归一化响应', async () => {
    put.mockResolvedValue({
      roleId: 3002,
      hasAllScope: true,
      deptScoped: false,
      postScoped: false,
      selfScoped: false,
      warehouseIds: []
    })

    const scope = await assignRoleDataScope(3002, {
      hasAllScope: true,
      warehouseIds: []
    })

    expect(put).toHaveBeenCalledWith('/system/roles/3002/data-scope', {
      hasAllScope: true,
      deptScoped: false,
      postScoped: false,
      selfScoped: false,
      warehouseIds: []
    })
    expect(scope.roleId).toBe('3002')
    expect(scope.hasAllScope).toBe(true)
  })
})
