import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

import type { Role, User, UserDataScope } from '@/api/system'
import { useSystemUserForm } from './useSystemUserForm'

const t = (key: string) => key

const detail = {
  id: 'u1',
  username: 'alice',
  realName: 'Alice',
  employeeNo: 'E01',
  email: 'a@x.com',
  mobile: '138',
  deptId: '1',
  postId: 'p1',
  remark: '备注'
} as unknown as User

const createForm = (overrides: Partial<Parameters<typeof useSystemUserForm>[1]> = {}) =>
  useSystemUserForm(t, {
    getUser: vi.fn(async () => detail),
    createUser: vi.fn(async () => ({})),
    updateUser: vi.fn(async () => ({})),
    getAllRoles: vi.fn(async () => [{ id: 'r1', name: '管理员' }] as Role[]),
    getAssignedUserRoles: vi.fn(async () => ({ roleIds: ['r1'] })),
    assignUserRoles: vi.fn(async () => ({})),
    getWarehouses: vi.fn(async () => ({ records: [{ id: 'w1', name: '主仓', code: 'WH1' }], total: 1 } as any)),
    getAssignedUserDataScope: vi.fn(async () => ({
      userId: 'u1',
      hasAllScope: false,
      deptScoped: true,
      postScoped: false,
      selfScoped: false,
      warehouseIds: ['w1'],
      effectiveHasAllScope: false,
      effectiveDeptScoped: true,
      effectivePostScoped: false,
      effectiveSelfScoped: false,
      effectiveWarehouseIds: ['w1']
    } as UserDataScope)),
    assignUserDataScope: vi.fn(async () => ({
      userId: 'u1',
      hasAllScope: true,
      deptScoped: false,
      postScoped: false,
      selfScoped: false,
      warehouseIds: [],
      effectiveHasAllScope: true,
      effectiveDeptScoped: false,
      effectivePostScoped: false,
      effectiveSelfScoped: false,
      effectiveWarehouseIds: []
    } as UserDataScope)),
    roles: ref<Role[]>([]),
    warehouseOptionLabel: (warehouse) => warehouse.name || String(warehouse.id),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  })

describe('system user form', () => {
  it('opens create and edit dialogs', async () => {
    const form = createForm()
    form.handleCreate()
    expect(form.dialogVisible.value).toBe(true)
    expect(form.dialogTitle.value).toBe('systemUsers.dialog.add')
    expect(form.formData.username).toBe('')

    expect(await form.handleEdit({ id: 'u1' } as User)).toBe(true)
    expect(form.dialogTitle.value).toBe('systemUsers.dialog.edit')
    expect(form.formData).toMatchObject({
      id: 'u1',
      username: 'alice',
      realName: 'Alice',
      employeeNo: 'E01',
      deptId: '1'
    })
  })

  it('creates and updates users', async () => {
    const createUser = vi.fn(async () => ({}))
    const updateUser = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const form = createForm({ createUser, updateUser, onSubmitted })

    form.handleCreate()
    form.formData.username = 'bob'
    form.formData.password = 'Pass1!'
    form.formData.realName = 'Bob'
    expect(await form.handleSubmit()).toBe(true)
    expect(createUser).toHaveBeenCalledWith(expect.objectContaining({
      username: 'bob',
      password: 'Pass1!',
      realName: 'Bob'
    }))

    await form.handleEdit({ id: 'u1' } as User)
    form.formData.realName = 'Alice2'
    expect(await form.handleSubmit()).toBe(true)
    expect(updateUser).toHaveBeenCalledWith('u1', expect.objectContaining({
      realName: 'Alice2'
    }))
    expect(onSubmitted).toHaveBeenCalled()
  })

  it('loads and saves role assignments', async () => {
    const roles = ref<Role[]>([])
    const assignUserRoles = vi.fn(async () => ({}))
    const form = createForm({ roles, assignUserRoles })

    expect(await form.handleAssignRoles({ id: 'u1', username: 'alice' } as User)).toBe(true)
    expect(roles.value).toEqual([{ id: 'r1', name: '管理员' }])
    expect(form.selectedRoleIds.value).toEqual(['r1'])

    form.selectedRoleIds.value = []
    expect(await form.submitRoleAssignment()).toBe(false)

    form.selectedRoleIds.value = ['r1', 'r2']
    expect(await form.submitRoleAssignment()).toBe(true)
    expect(assignUserRoles).toHaveBeenCalledWith('u1', ['r1', 'r2'])
  })

  it('loads and saves data scopes with effective summary', async () => {
    const form = createForm()
    expect(await form.handleAssignDataScope({ id: 'u1', username: 'alice' } as User)).toBe(true)
    expect(form.dataScopeForm.deptScoped).toBe(true)
    expect(form.dataScopeForm.warehouseIds).toEqual(['w1'])
    expect(form.effectiveScopeSummary.value.tags).toContain('systemUsers.ownDepartment')

    form.dataScopeForm.hasAllScope = true
    expect(await form.submitDataScopeAssignment()).toBe(true)
    expect(form.effectiveScopeSummary.value.tags).toEqual(['systemUsers.allData'])
  })

  it('reports detail/role/data-scope failures', async () => {
    const onError = vi.fn()
    const form = createForm({
      getUser: vi.fn(async () => { throw new Error('boom') }),
      getAssignedUserRoles: vi.fn(async () => { throw new Error('boom') }),
      getAssignedUserDataScope: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    expect(await form.handleEdit({ id: 'u1' } as User)).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemUsers.message.detailLoadFailed')
    expect(await form.handleAssignRoles({ id: 'u1', username: 'alice' } as User)).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemUsers.message.rolesLoadFailed')
    expect(await form.handleAssignDataScope({ id: 'u1', username: 'alice' } as User)).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemUsers.message.dataScopeLoadFailed')
  })
})
