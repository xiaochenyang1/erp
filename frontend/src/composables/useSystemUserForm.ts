import { computed, reactive, ref, type Ref } from 'vue'

import type {
  Role,
  User,
  UserDataScope,
  UserDataScopeAssignRequest,
  UserSaveRequest
} from '@/api/system'
import type { Warehouse } from '@/api/masterdata'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export interface SystemUserFormState {
  id?: string
  username: string
  password?: string
  employeeNo?: string
  realName: string
  email?: string
  mobile?: string
  avatar?: string
  deptId?: string | number
  postId?: string | number
  remark?: string
}

const emptyForm = (): SystemUserFormState => ({
  id: undefined,
  username: '',
  password: '',
  employeeNo: '',
  realName: '',
  email: '',
  mobile: '',
  avatar: '',
  deptId: undefined,
  postId: undefined,
  remark: ''
})

/**
 * Create/edit user dialog plus role assignment and data-scope dialogs.
 */
export const useSystemUserForm = (
  t: Translate,
  options: {
    getUser: (id: string | number) => Promise<User>
    createUser: (data: UserSaveRequest) => Promise<unknown>
    updateUser: (id: string | number, data: UserSaveRequest) => Promise<unknown>
    getAllRoles: () => Promise<Role[]>
    getAssignedUserRoles: (id: string | number) => Promise<{ roleIds: string[] }>
    assignUserRoles: (id: string | number, roleIds: Array<string | number>) => Promise<unknown>
    getWarehouses: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<Warehouse>>
    getAssignedUserDataScope: (id: string | number) => Promise<UserDataScope>
    assignUserDataScope: (
      id: string | number,
      data: UserDataScopeAssignRequest
    ) => Promise<UserDataScope>
    roles: Ref<Role[]>
    warehouseOptionLabel: (warehouse: Warehouse) => string
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const dialogVisible = ref(false)
  const dialogTitle = ref('')
  const submitLoading = ref(false)
  const formData = reactive<SystemUserFormState>(emptyForm())

  const roleDialogVisible = ref(false)
  const roleLoading = ref(false)
  const roleSubmitLoading = ref(false)
  const currentUserId = ref<string | number>('')
  const currentUsername = ref('')
  const selectedRoleIds = ref<string[]>([])

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
  const effectiveScope = reactive({
    hasAllScope: false,
    deptScoped: false,
    postScoped: false,
    selfScoped: false,
    warehouseIds: [] as string[]
  })

  const effectiveScopeSummary = computed(() => {
    if (effectiveScope.hasAllScope) {
      return { tags: [t('systemUsers.allData')] }
    }
    const tags: string[] = []
    if (effectiveScope.deptScoped) tags.push(t('systemUsers.ownDepartment'))
    if (effectiveScope.postScoped) tags.push(t('systemUsers.ownPost'))
    if (effectiveScope.selfScoped) tags.push(t('systemUsers.selfOnly'))
    for (const id of effectiveScope.warehouseIds) {
      const warehouse = warehouses.value.find((item) => String(item.id) === String(id))
      tags.push(
        warehouse
          ? t('systemUsers.warehouseScopeTag', {
            warehouse: options.warehouseOptionLabel(warehouse)
          })
          : t('systemUsers.warehouseFallback', { id })
      )
    }
    return { tags }
  })

  const resetForm = () => {
    Object.assign(formData, emptyForm())
  }

  const handleCreate = () => {
    dialogTitle.value = t('systemUsers.dialog.add')
    resetForm()
    dialogVisible.value = true
  }

  const handleEdit = async (row: User) => {
    dialogTitle.value = t('systemUsers.dialog.edit')
    try {
      const user = await options.getUser(row.id)
      Object.assign(formData, {
        id: user.id,
        username: user.username,
        password: '',
        employeeNo: user.employeeNo || '',
        realName: user.realName,
        email: user.email || '',
        mobile: user.mobile || '',
        avatar: user.avatar || '',
        deptId: user.deptId,
        postId: user.postId,
        remark: user.remark || ''
      })
      dialogVisible.value = true
      return true
    } catch {
      options.onError?.(t('systemUsers.message.detailLoadFailed'))
      return false
    }
  }

  const handleSubmit = async () => {
    submitLoading.value = true
    try {
      if (formData.id) {
        await options.updateUser(formData.id, {
          employeeNo: formData.employeeNo,
          realName: formData.realName,
          email: formData.email || undefined,
          mobile: formData.mobile,
          avatar: formData.avatar || undefined,
          deptId: formData.deptId,
          postId: formData.postId,
          remark: formData.remark
        })
        options.onSuccess?.(t('systemUsers.message.updateSuccess'))
      } else {
        await options.createUser({
          username: formData.username,
          password: formData.password,
          employeeNo: formData.employeeNo,
          realName: formData.realName,
          email: formData.email || undefined,
          mobile: formData.mobile,
          avatar: formData.avatar || undefined,
          deptId: formData.deptId,
          postId: formData.postId,
          remark: formData.remark
        })
        options.onSuccess?.(t('systemUsers.message.createSuccess'))
      }
      dialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('systemUsers.message.saveFailed'))
      return false
    } finally {
      submitLoading.value = false
    }
  }

  const handleAssignRoles = async (row: User) => {
    currentUserId.value = row.id
    currentUsername.value = row.username
    roleDialogVisible.value = true
    roleLoading.value = true
    selectedRoleIds.value = []
    try {
      if (options.roles.value.length === 0) {
        options.roles.value = await options.getAllRoles()
      }
      const assignment = await options.getAssignedUserRoles(row.id)
      selectedRoleIds.value = (assignment.roleIds || []).map(String)
      return true
    } catch {
      options.onError?.(t('systemUsers.message.rolesLoadFailed'))
      return false
    } finally {
      roleLoading.value = false
    }
  }

  const submitRoleAssignment = async () => {
    if (selectedRoleIds.value.length === 0) {
      options.onWarning?.(t('systemUsers.message.roleRequired'))
      return false
    }
    roleSubmitLoading.value = true
    try {
      await options.assignUserRoles(currentUserId.value, selectedRoleIds.value)
      options.onSuccess?.(t('systemUsers.message.rolesSaved'))
      roleDialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('systemUsers.message.rolesSaveFailed'))
      return false
    } finally {
      roleSubmitLoading.value = false
    }
  }

  const applyEffectiveScope = (scope: {
    effectiveHasAllScope?: boolean
    effectiveDeptScoped?: boolean
    effectivePostScoped?: boolean
    effectiveSelfScoped?: boolean
    effectiveWarehouseIds?: string[]
  }) => {
    effectiveScope.hasAllScope = !!scope.effectiveHasAllScope
    effectiveScope.deptScoped = !!scope.effectiveDeptScoped
    effectiveScope.postScoped = !!scope.effectivePostScoped
    effectiveScope.selfScoped = !!scope.effectiveSelfScoped
    effectiveScope.warehouseIds = (scope.effectiveWarehouseIds || []).map(String)
  }

  const resetDataScopeForm = () => {
    dataScopeForm.hasAllScope = false
    dataScopeForm.deptScoped = false
    dataScopeForm.postScoped = false
    dataScopeForm.selfScoped = false
    dataScopeForm.warehouseIds = []
    effectiveScope.hasAllScope = false
    effectiveScope.deptScoped = false
    effectiveScope.postScoped = false
    effectiveScope.selfScoped = false
    effectiveScope.warehouseIds = []
  }

  const handleAssignDataScope = async (row: User) => {
    currentUserId.value = row.id
    currentUsername.value = row.username
    dataScopeDialogVisible.value = true
    dataScopeLoading.value = true
    try {
      if (!warehouses.value.length) {
        const page = await options.getWarehouses({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
        warehouses.value = page.records || []
      }
      const scope = await options.getAssignedUserDataScope(row.id)
      dataScopeForm.hasAllScope = !!scope.hasAllScope
      dataScopeForm.deptScoped = !!scope.deptScoped
      dataScopeForm.postScoped = !!scope.postScoped
      dataScopeForm.selfScoped = !!scope.selfScoped
      dataScopeForm.warehouseIds = (scope.warehouseIds || []).map(String)
      applyEffectiveScope(scope)
      return true
    } catch {
      resetDataScopeForm()
      options.onError?.(t('systemUsers.message.dataScopeLoadFailed'))
      return false
    } finally {
      dataScopeLoading.value = false
    }
  }

  const submitDataScopeAssignment = async () => {
    if (!currentUserId.value) return false
    dataScopeSubmitLoading.value = true
    try {
      const saved = await options.assignUserDataScope(currentUserId.value, {
        hasAllScope: dataScopeForm.hasAllScope,
        deptScoped: dataScopeForm.deptScoped,
        postScoped: dataScopeForm.postScoped,
        selfScoped: dataScopeForm.selfScoped,
        warehouseIds: dataScopeForm.warehouseIds
      })
      applyEffectiveScope(saved)
      options.onSuccess?.(t('systemUsers.message.dataScopeSaved'))
      dataScopeDialogVisible.value = false
      return true
    } catch {
      options.onError?.(t('systemUsers.message.dataScopeSaveFailed'))
      return false
    } finally {
      dataScopeSubmitLoading.value = false
    }
  }

  return {
    currentUserId,
    currentUsername,
    dataScopeDialogVisible,
    dataScopeForm,
    dataScopeLoading,
    dataScopeSubmitLoading,
    dialogTitle,
    dialogVisible,
    effectiveScope,
    effectiveScopeSummary,
    formData,
    handleAssignDataScope,
    handleAssignRoles,
    handleCreate,
    handleEdit,
    handleSubmit,
    resetForm,
    roleDialogVisible,
    roleLoading,
    roleSubmitLoading,
    selectedRoleIds,
    submitDataScopeAssignment,
    submitLoading,
    submitRoleAssignment,
    warehouses
  }
}
