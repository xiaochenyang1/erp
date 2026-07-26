import { reactive, ref } from 'vue'

import type {
  Menu,
  Role,
  RoleDataScope,
  RoleDataScopeAssignRequest,
  RoleSaveRequest
} from '@/api/system'
import type { Warehouse } from '@/api/masterdata'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

const emptyForm = (): RoleSaveRequest => ({
  code: '',
  name: '',
  permissions: [],
  status: 'ACTIVE',
  remark: ''
})

/**
 * Create/edit role dialog plus menu permission and data-scope dialogs.
 * ElTree checked keys are provided by the page via getCheckedMenuIds.
 */
export const useSystemRoleForm = (
  t: Translate,
  options: {
    getRole: (id: string | number) => Promise<Role>
    createRole: (data: RoleSaveRequest) => Promise<unknown>
    updateRole: (id: string | number, data: RoleSaveRequest) => Promise<unknown>
    getMenuTree: () => Promise<Menu[]>
    getAssignedRoleMenus: (id: string | number) => Promise<{ menuIds: string[] }>
    assignRoleMenus: (id: string | number, menuIds: Array<string | number>) => Promise<unknown>
    getWarehouses: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<Warehouse>>
    getAssignedRoleDataScope: (id: string | number) => Promise<RoleDataScope>
    assignRoleDataScope: (
      id: string | number,
      data: RoleDataScopeAssignRequest
    ) => Promise<unknown>
    /** Page supplies checked + half-checked keys from ElTree. */
    getCheckedMenuIds?: () => Array<string | number>
    onError?: Notify
    onSuccess?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const dialogVisible = ref(false)
  const submitLoading = ref(false)
  const dialogTitle = ref('')
  const isEdit = ref(false)
  const currentId = ref<string | number>('')
  const formData = reactive<RoleSaveRequest>(emptyForm())

  const permissionDialogVisible = ref(false)
  const permissionSubmitLoading = ref(false)
  const currentRoleId = ref<string | number>('')
  const currentRoleName = ref('')
  const selectedPermissions = ref<string[]>([])
  const permissionTree = ref<Menu[]>([])

  const dataScopeDialogVisible = ref(false)
  const dataScopeLoading = ref(false)
  const dataScopeSubmitLoading = ref(false)
  const warehouses = ref<Warehouse[]>([])
  const dataScopeForm = reactive({
    hasAllScope: false,
    deptScoped: false,
    postScoped: false,
    selfScoped: false,
    warehouseIds: [] as string[]
  })

  const resetForm = () => {
    Object.assign(formData, emptyForm(), { permissions: [] as string[] })
    formData.permissions = []
  }

  const handleCreate = () => {
    dialogTitle.value = t('systemRoles.dialog.add')
    isEdit.value = false
    currentId.value = ''
    resetForm()
    dialogVisible.value = true
  }

  const handleEdit = async (row: Role) => {
    dialogTitle.value = t('systemRoles.dialog.edit')
    isEdit.value = true
    currentId.value = row.id
    try {
      const data = await options.getRole(row.id)
      Object.assign(formData, {
        code: data.code,
        name: data.name,
        permissions: data.permissions || [],
        status: data.status || 'ACTIVE',
        remark: data.remark || ''
      })
      dialogVisible.value = true
      return true
    } catch {
      options.onError?.(t('systemRoles.message.detailLoadFailed'))
      return false
    }
  }

  const handleSubmit = async () => {
    submitLoading.value = true
    try {
      if (isEdit.value) {
        await options.updateRole(currentId.value, { ...formData })
      } else {
        await options.createRole({ ...formData })
      }
      options.onSuccess?.(t('systemRoles.message.operationSuccess'))
      dialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('systemRoles.message.operationFailed'))
      return false
    } finally {
      submitLoading.value = false
    }
  }

  const handlePermission = async (row: Role) => {
    currentRoleId.value = row.id
    currentRoleName.value = row.name || row.code
    try {
      const [menus, assignment] = await Promise.all([
        options.getMenuTree(),
        options.getAssignedRoleMenus(row.id)
      ])
      permissionTree.value = menus || []
      selectedPermissions.value = (assignment.menuIds || []).map(String)
      permissionDialogVisible.value = true
      return selectedPermissions.value
    } catch {
      options.onError?.(t('systemRoles.message.permissionsLoadFailed'))
      return null
    }
  }

  const handleSavePermission = async () => {
    const menuIds = [...new Set((options.getCheckedMenuIds?.() || []).map(String))]
    if (menuIds.length === 0) {
      options.onError?.(t('systemRoles.message.menuRequired'))
      return false
    }
    permissionSubmitLoading.value = true
    try {
      await options.assignRoleMenus(currentRoleId.value, menuIds)
      options.onSuccess?.(t('systemRoles.message.permissionsSaved'))
      permissionDialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('systemRoles.message.permissionsSaveFailed'))
      return false
    } finally {
      permissionSubmitLoading.value = false
    }
  }

  const resetDataScopeForm = () => {
    dataScopeForm.hasAllScope = false
    dataScopeForm.deptScoped = false
    dataScopeForm.postScoped = false
    dataScopeForm.selfScoped = false
    dataScopeForm.warehouseIds = []
  }

  const handleAssignDataScope = async (row: Role) => {
    currentRoleId.value = row.id
    currentRoleName.value = row.name || row.code
    dataScopeDialogVisible.value = true
    dataScopeLoading.value = true
    try {
      if (!warehouses.value.length) {
        const page = await options.getWarehouses({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
        warehouses.value = page.records || []
      }
      const scope = await options.getAssignedRoleDataScope(row.id)
      dataScopeForm.hasAllScope = !!scope.hasAllScope
      dataScopeForm.deptScoped = !!scope.deptScoped
      dataScopeForm.postScoped = !!scope.postScoped
      dataScopeForm.selfScoped = !!scope.selfScoped
      dataScopeForm.warehouseIds = (scope.warehouseIds || []).map(String)
      return true
    } catch {
      resetDataScopeForm()
      options.onError?.(t('systemRoles.message.dataScopeLoadFailed'))
      return false
    } finally {
      dataScopeLoading.value = false
    }
  }

  const submitDataScopeAssignment = async () => {
    if (!currentRoleId.value) return false
    dataScopeSubmitLoading.value = true
    try {
      await options.assignRoleDataScope(currentRoleId.value, {
        hasAllScope: dataScopeForm.hasAllScope,
        deptScoped: dataScopeForm.deptScoped,
        postScoped: dataScopeForm.postScoped,
        selfScoped: dataScopeForm.selfScoped,
        warehouseIds: dataScopeForm.warehouseIds
      })
      options.onSuccess?.(t('systemRoles.message.dataScopeSaved'))
      dataScopeDialogVisible.value = false
      return true
    } catch {
      options.onError?.(t('systemRoles.message.dataScopeSaveFailed'))
      return false
    } finally {
      dataScopeSubmitLoading.value = false
    }
  }

  return {
    currentRoleId,
    currentRoleName,
    dataScopeDialogVisible,
    dataScopeForm,
    dataScopeLoading,
    dataScopeSubmitLoading,
    dialogTitle,
    dialogVisible,
    formData,
    handleAssignDataScope,
    handleCreate,
    handleEdit,
    handlePermission,
    handleSavePermission,
    handleSubmit,
    isEdit,
    permissionDialogVisible,
    permissionSubmitLoading,
    permissionTree,
    resetForm,
    selectedPermissions,
    submitDataScopeAssignment,
    submitLoading,
    warehouses
  }
}
