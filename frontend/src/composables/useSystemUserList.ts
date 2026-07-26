import { reactive, ref } from 'vue'

import type {
  Dept,
  Post,
  Role,
  User,
  UserQuery
} from '@/api/system'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title: string,
  options?: { type?: string }
) => Promise<unknown>
type Prompt = (
  message: string,
  title: string,
  options?: {
    inputType?: string
    inputPlaceholder?: string
    confirmButtonText?: string
    cancelButtonText?: string
  }
) => Promise<{ value: string }>

/**
 * Query, master-data options and enable/disable/reset-password for system users.
 * Role and data-scope dialogs live in the form composable.
 */
export const useSystemUserList = (
  t: Translate,
  options: {
    getUsers: (params: UserQuery) => Promise<PageResponse<User>>
    getDeptTree: () => Promise<Dept[]>
    getAllPosts: () => Promise<Post[]>
    getAllRoles: () => Promise<Role[]>
    deleteUser: (id: string | number) => Promise<unknown>
    enableUser: (id: string | number) => Promise<unknown>
    resetUserPassword: (id: string | number, newPassword: string) => Promise<unknown>
    confirm: Confirm
    prompt: Prompt
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const queryParams = reactive<UserQuery>({
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    deptId: undefined,
    postId: undefined,
    status: ''
  })

  const loading = ref(false)
  const tableData = ref<User[]>([])
  const total = ref(0)
  const depts = ref<Dept[]>([])
  const posts = ref<Post[]>([])
  const roles = ref<Role[]>([])

  const loadData = async () => {
    loading.value = true
    try {
      const page = await options.getUsers({
        pageNo: queryParams.pageNo,
        pageSize: queryParams.pageSize,
        keyword: queryParams.keyword || undefined,
        deptId: queryParams.deptId,
        postId: queryParams.postId,
        status: queryParams.status || undefined
      })
      tableData.value = page.records || []
      total.value = page.total || 0
    } catch {
      options.onError?.(t('systemUsers.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const loadOptions = async () => {
    try {
      const [deptTree, postList, roleList] = await Promise.all([
        options.getDeptTree(),
        options.getAllPosts(),
        options.getAllRoles()
      ])
      depts.value = deptTree || []
      posts.value = postList || []
      roles.value = roleList || []
    } catch {
      options.onError?.(t('systemUsers.message.optionsLoadFailed'))
    }
  }

  const handleQuery = async () => {
    queryParams.pageNo = 1
    await loadData()
  }

  const handleReset = async () => {
    queryParams.keyword = ''
    queryParams.deptId = undefined
    queryParams.postId = undefined
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

  const handleDisable = async (row: User) => {
    try {
      await options.confirm(
        t('systemUsers.message.disableConfirm', { username: row.username }),
        t('systemUsers.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    try {
      await options.deleteUser(row.id)
      options.onSuccess?.(t('systemUsers.message.disableSuccess'))
      await loadData()
      return true
    } catch {
      options.onError?.(t('systemUsers.message.disableFailed'))
      return false
    }
  }

  const handleEnable = async (row: User) => {
    try {
      await options.confirm(
        t('systemUsers.message.enableConfirm', { username: row.username }),
        t('systemUsers.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    try {
      await options.enableUser(row.id)
      options.onSuccess?.(t('systemUsers.message.enableSuccess'))
      await loadData()
      return true
    } catch {
      options.onError?.(t('systemUsers.message.enableFailed'))
      return false
    }
  }

  const handleResetPassword = async (row: User) => {
    try {
      const { value } = await options.prompt(
        t('systemUsers.message.newPassword'),
        t('systemUsers.message.resetPasswordTitle', { username: row.username }),
        {
          inputType: 'password',
          inputPlaceholder: t('systemUsers.message.passwordRule'),
          confirmButtonText: t('systemUsers.confirm'),
          cancelButtonText: t('systemUsers.cancel')
        }
      )
      await options.resetUserPassword(row.id, value)
      options.onSuccess?.(t('systemUsers.message.passwordReset'))
      return true
    } catch (error) {
      if (error !== 'cancel') {
        options.onError?.(t('systemUsers.message.passwordResetFailed'))
      }
      return false
    }
  }

  return {
    depts,
    handleDisable,
    handleEnable,
    handlePageChange,
    handleQuery,
    handleReset,
    handleResetPassword,
    handleSizeChange,
    loadData,
    loadOptions,
    loading,
    posts,
    queryParams,
    roles,
    tableData,
    total
  }
}
