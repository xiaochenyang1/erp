import { reactive, ref } from 'vue'

import type { UserSession, UserSessionQuery } from '@/api/userSession'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title: string,
  options?: { type?: 'warning' }
) => Promise<unknown>

/** User-session query, pagination and revoke workflows. */
export const useSystemUserSessionList = (
  t: Translate,
  options: {
    getUserSessions: (params: UserSessionQuery) => Promise<PageResponse<UserSession>>
    revokeUserSession: (sessionId: string | number) => Promise<unknown>
    revokeUserSessionsByUser: (userId: string | number) => Promise<unknown>
    sessionUserLabel: (session: UserSession) => string
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const queryForm = reactive<UserSessionQuery>({
    username: '',
    status: ''
  })
  const pagination = reactive({
    page: 1,
    size: 20,
    total: 0
  })
  const loading = ref(false)
  const tableData = ref<UserSession[]>([])

  const buildQueryParams = (): UserSessionQuery => ({
    ...queryForm,
    pageNo: pagination.page,
    pageSize: pagination.size
  })

  const loadData = async () => {
    loading.value = true
    try {
      const page = await options.getUserSessions(buildQueryParams())
      tableData.value = page.records || []
      pagination.total = page.total || 0
      return true
    } catch {
      options.onError?.(t('userSessions.message.loadFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  const handleQuery = () => {
    pagination.page = 1
    return loadData()
  }

  const handleReset = () => {
    queryForm.username = ''
    queryForm.status = ''
    pagination.page = 1
    return loadData()
  }

  const handlePageChange = (page: number) => {
    pagination.page = page
    return loadData()
  }

  const handleSizeChange = (size: number) => {
    pagination.size = size
    pagination.page = 1
    return loadData()
  }

  const confirmRevoke = async (messageKey: string, row: UserSession) => {
    try {
      await options.confirm(
        t(messageKey, { user: options.sessionUserLabel(row) }),
        t('userSessions.message.prompt'),
        { type: 'warning' }
      )
      return true
    } catch (error) {
      if (error === 'cancel') return false
      throw error
    }
  }

  const handleRevoke = async (row: UserSession) => {
    try {
      if (!await confirmRevoke('userSessions.message.revokeConfirm', row)) {
        return false
      }
      await options.revokeUserSession(row.id)
      options.onSuccess?.(t('userSessions.message.revoked'))
      await loadData()
      return true
    } catch {
      options.onError?.(t('userSessions.message.revokeFailed'))
      return false
    }
  }

  const handleRevokeUser = async (row: UserSession) => {
    try {
      if (!await confirmRevoke('userSessions.message.revokeUserConfirm', row)) {
        return false
      }
      await options.revokeUserSessionsByUser(row.userId)
      options.onSuccess?.(t('userSessions.message.userRevoked'))
      await loadData()
      return true
    } catch {
      options.onError?.(t('userSessions.message.revokeUserFailed'))
      return false
    }
  }

  return {
    buildQueryParams,
    handlePageChange,
    handleQuery,
    handleReset,
    handleRevoke,
    handleRevokeUser,
    handleSizeChange,
    loadData,
    loading,
    pagination,
    queryForm,
    tableData
  }
}
