import { describe, expect, it, vi } from 'vitest'

import type { Role } from '@/api/system'
import { useSystemRoleForm } from './useSystemRoleForm'

const t = (key: string) => key

const detail = {
  id: 'r1',
  code: 'ADMIN',
  name: '管理员',
  status: 'ACTIVE',
  permissions: ['a'],
  remark: '备注'
} as unknown as Role

const createForm = (overrides: Partial<Parameters<typeof useSystemRoleForm>[1]> = {}) =>
  useSystemRoleForm(t, {
    getRole: vi.fn(async () => detail),
    createRole: vi.fn(async () => ({})),
    updateRole: vi.fn(async () => ({})),
    getMenuTree: vi.fn(async () => [{ id: 'm1', name: '系统' }] as any),
    getAssignedRoleMenus: vi.fn(async () => ({ menuIds: ['m1'] })),
    assignRoleMenus: vi.fn(async () => ({})),
    getWarehouses: vi.fn(async () => ({ records: [{ id: 'w1', name: '主仓' }], total: 1 } as any)),
    getAssignedRoleDataScope: vi.fn(async () => ({
      roleId: 'r1',
      hasAllScope: false,
      deptScoped: true,
      postScoped: false,
      selfScoped: false,
      warehouseIds: ['w1']
    } as any)),
    assignRoleDataScope: vi.fn(async () => ({})),
    getCheckedMenuIds: () => ['m1', 'm2'],
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('system role form', () => {
  it('opens create and edit dialogs', async () => {
    const form = createForm()
    form.handleCreate()
    expect(form.dialogVisible.value).toBe(true)
    expect(form.isEdit.value).toBe(false)
    expect(form.dialogTitle.value).toBe('systemRoles.dialog.add')
    expect(form.formData.code).toBe('')

    expect(await form.handleEdit({ id: 'r1' } as Role)).toBe(true)
    expect(form.isEdit.value).toBe(true)
    expect(form.formData).toMatchObject({
      code: 'ADMIN',
      name: '管理员',
      status: 'ACTIVE',
      remark: '备注'
    })
  })

  it('creates and updates roles', async () => {
    const createRole = vi.fn(async () => ({}))
    const updateRole = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const form = createForm({ createRole, updateRole, onSubmitted })

    form.handleCreate()
    form.formData.code = 'AUDITOR'
    form.formData.name = '审计'
    expect(await form.handleSubmit()).toBe(true)
    expect(createRole).toHaveBeenCalledWith(expect.objectContaining({
      code: 'AUDITOR',
      name: '审计'
    }))

    await form.handleEdit({ id: 'r1' } as Role)
    form.formData.name = '管理员2'
    expect(await form.handleSubmit()).toBe(true)
    expect(updateRole).toHaveBeenCalledWith('r1', expect.objectContaining({ name: '管理员2' }))
    expect(onSubmitted).toHaveBeenCalled()
  })

  it('loads and saves menu permissions, requiring at least one menu', async () => {
    const assignRoleMenus = vi.fn(async () => ({}))
    const form = createForm({
      assignRoleMenus,
      getCheckedMenuIds: () => []
    })

    const keys = await form.handlePermission({ id: 'r1', name: '管理员', code: 'ADMIN' } as Role)
    expect(keys).toEqual(['m1'])
    expect(form.permissionTree.value).toEqual([{ id: 'm1', name: '系统' }])
    expect(form.permissionDialogVisible.value).toBe(true)

    expect(await form.handleSavePermission()).toBe(false)

    const saving = createForm({ assignRoleMenus })
    await saving.handlePermission({ id: 'r1', name: '管理员', code: 'ADMIN' } as Role)
    expect(await saving.handleSavePermission()).toBe(true)
    expect(assignRoleMenus).toHaveBeenCalledWith('r1', ['m1', 'm2'])
  })

  it('loads and saves data scopes with ACTIVE warehouse options', async () => {
    const getWarehouses = vi.fn(async () => ({ records: [{ id: 'w1', name: '主仓' }], total: 1 } as any))
    const assignRoleDataScope = vi.fn(async () => ({}))
    const form = createForm({ getWarehouses, assignRoleDataScope })

    expect(await form.handleAssignDataScope({ id: 'r1', name: '管理员', code: 'ADMIN' } as Role)).toBe(true)
    expect(getWarehouses).toHaveBeenCalledWith({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    expect(form.dataScopeForm.deptScoped).toBe(true)
    expect(form.dataScopeForm.warehouseIds).toEqual(['w1'])

    form.dataScopeForm.hasAllScope = true
    expect(await form.submitDataScopeAssignment()).toBe(true)
    expect(assignRoleDataScope).toHaveBeenCalledWith('r1', expect.objectContaining({
      hasAllScope: true
    }))
  })

  it('reports detail/permission/data-scope failures', async () => {
    const onError = vi.fn()
    const form = createForm({
      getRole: vi.fn(async () => { throw new Error('boom') }),
      getMenuTree: vi.fn(async () => { throw new Error('boom') }),
      getAssignedRoleDataScope: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    expect(await form.handleEdit({ id: 'r1' } as Role)).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemRoles.message.detailLoadFailed')
    expect(await form.handlePermission({ id: 'r1', name: '管理员', code: 'ADMIN' } as Role)).toBeNull()
    expect(onError).toHaveBeenCalledWith('systemRoles.message.permissionsLoadFailed')
    expect(await form.handleAssignDataScope({ id: 'r1', name: '管理员', code: 'ADMIN' } as Role)).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemRoles.message.dataScopeLoadFailed')
  })
})
