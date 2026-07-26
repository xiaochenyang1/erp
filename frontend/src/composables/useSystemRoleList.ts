import { reactive, ref } from 'vue'

import type { Role, RoleQuery } from '@/api/system'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title: string,
  options?: {
    confirmButtonText?: string
    cancelButtonText?: string
    type?: string
  }
) => Promise<unknown>

/**
 * Query, pagination and enable/disable for system roles.
 * Menu permission and data-scope dialogs live in useSystemRoleForm.
 */
export const useSystemRoleList = (
  t: Translate,
  options: {
    getRoles: (params: RoleQuery) => Promise<PageResponse<Role>>
    deleteRole: (id: string | number) => Promise<unknown>
    enableRole: (id: string | number) => Promise<unknown>
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const queryParams = reactive<RoleQuery>({
    pageNo: 1,
    pageSize: 10,
    code: '',
    name: '',
    status: ''
  })

  const loading = ref(false)
  const tableData = ref<Role[]>([])
  const total = ref(0)

  const loadData = async () => {
    loading.value = true
    try {
      const response = await options.getRoles({
        pageNo: queryParams.pageNo,
        pageSize: queryParams.pageSize,
        code: queryParams.code || undefined,
        name: queryParams.name || undefined,
        status: queryParams.status || undefined
      })
      tableData.value = response.records || []
      total.value = response.total || 0
    } catch {
      options.onError?.(t('systemRoles.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const handleQuery = async () => {
    queryParams.pageNo = 1
    await loadData()
  }

  const handleReset = async () => {
    queryParams.code = ''
    queryParams.name = ''
    queryParams.status = ''
    queryParams.pageNo = 1
    await loadData()
  }

  const handlePageChange = async (page: number) => {
    queryParams.pageNo = page
    await loadData()
  }

  const handleSizeChange = async (size: number) => {
    queryParams.pageSize = size
    queryParams.pageNo = 1
    await loadData()
  }

  const handleDisable = async (row: Role) => {
    try {
      await options.confirm(
        t('systemRoles.message.disableConfirm', { name: row.name }),
        t('systemRoles.prompt'),
        {
          confirmButtonText: t('systemRoles.confirm'),
          cancelButtonText: t('systemRoles.cancel'),
          type: 'warning'
        }
      )
    } catch {
      return false
    }
    try {
      await options.deleteRole(row.id)
      options.onSuccess?.(t('systemRoles.message.disableSuccess'))
      await loadData()
      return true
    } catch {
      options.onError?.(t('systemRoles.message.disableFailed'))
      return false
    }
  }

  const handleEnable = async (row: Role) => {
    try {
      await options.confirm(
        t('systemRoles.message.enableConfirm', { name: row.name }),
        t('systemRoles.prompt'),
        {
          confirmButtonText: t('systemRoles.confirm'),
          cancelButtonText: t('systemRoles.cancel'),
          type: 'warning'
        }
      )
    } catch {
      return false
    }
    try {
      await options.enableRole(row.id)
      options.onSuccess?.(t('systemRoles.message.enableSuccess'))
      await loadData()
      return true
    } catch {
      options.onError?.(t('systemRoles.message.enableFailed'))
      return false
    }
  }

  return {
    handleDisable,
    handleEnable,
    handlePageChange,
    handleQuery,
    handleReset,
    handleSizeChange,
    loadData,
    loading,
    queryParams,
    tableData,
    total
  }
}
