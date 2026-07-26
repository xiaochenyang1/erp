import { reactive, ref } from 'vue'

import type {
  AuditLog,
  AuditLogQuery,
  LoginLog,
  LoginLogQuery,
  OperationLog,
  OperationLogQuery
} from '@/api/system'
import type { PageResponse } from '@/types/common'
import { useSystemLogPresentation } from './useSystemLogPresentation'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export type SystemLogTab = 'operation' | 'login' | 'audit'

/**
 * Three-tab log queries: operation / login / audit.
 * URL seed values are injected so the composable stays router-free.
 */
export const useSystemLogList = (
  t: Translate,
  options: {
    getOperationLogs: (params: OperationLogQuery) => Promise<PageResponse<OperationLog>>
    getOperationLog: (id: string | number) => Promise<OperationLog>
    exportOperationLogs: (params: OperationLogQuery) => Promise<Blob>
    getLoginLogs: (params: LoginLogQuery) => Promise<PageResponse<LoginLog>>
    getAuditLogs: (params: AuditLogQuery) => Promise<PageResponse<AuditLog>>
    downloadBlob: (blob: Blob, filename: string) => void
    initialBizNo?: string
    initialAuditBusinessNo?: string
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const { toEndDateTime, toStartDateTime } = useSystemLogPresentation()

  const activeTab = ref<SystemLogTab>('operation')

  const queryForm = reactive<OperationLogQuery>({
    module: '',
    operation: '',
    bizNo: options.initialBizNo || '',
    operatorName: '',
    status: '',
    startDate: '',
    endDate: ''
  })

  const loginQueryForm = reactive<LoginLogQuery>({
    username: '',
    result: '',
    loginTimeFrom: '',
    loginTimeTo: ''
  })

  const auditQueryForm = reactive<AuditLogQuery>({
    auditType: '',
    businessType: '',
    businessNo: options.initialAuditBusinessNo || options.initialBizNo || '',
    action: '',
    operatorName: '',
    auditTimeFrom: '',
    auditTimeTo: ''
  })

  const dateRange = ref<string[]>([])
  const loginDateRange = ref<string[]>([])
  const auditDateRange = ref<string[]>([])

  const loading = ref(false)
  const tableData = ref<OperationLog[]>([])
  const loginLoading = ref(false)
  const loginTableData = ref<LoginLog[]>([])
  const auditLoading = ref(false)
  const auditTableData = ref<AuditLog[]>([])

  const pagination = reactive({ page: 1, size: 20, total: 0 })
  const loginPagination = reactive({ page: 1, size: 20, total: 0 })
  const auditPagination = reactive({ page: 1, size: 20, total: 0 })

  const detailDialogVisible = ref(false)
  const detailData = ref<OperationLog>({} as OperationLog)

  const syncDateRange = () => {
    if (dateRange.value?.length === 2) {
      queryForm.startDate = dateRange.value[0]
      queryForm.endDate = dateRange.value[1]
    } else {
      queryForm.startDate = ''
      queryForm.endDate = ''
    }
  }

  const syncLoginDateRange = () => {
    if (loginDateRange.value?.length === 2) {
      loginQueryForm.loginTimeFrom = toStartDateTime(loginDateRange.value[0])
      loginQueryForm.loginTimeTo = toEndDateTime(loginDateRange.value[1])
    } else {
      loginQueryForm.loginTimeFrom = ''
      loginQueryForm.loginTimeTo = ''
    }
  }

  const syncAuditDateRange = () => {
    if (auditDateRange.value?.length === 2) {
      auditQueryForm.auditTimeFrom = toStartDateTime(auditDateRange.value[0])
      auditQueryForm.auditTimeTo = toEndDateTime(auditDateRange.value[1])
    } else {
      auditQueryForm.auditTimeFrom = ''
      auditQueryForm.auditTimeTo = ''
    }
  }

  const buildQueryParams = (): OperationLogQuery => ({
    module: queryForm.module || undefined,
    operation: queryForm.operation || undefined,
    bizNo: queryForm.bizNo || undefined,
    operatorName: queryForm.operatorName || undefined,
    status: queryForm.status || undefined,
    startDate: queryForm.startDate || undefined,
    endDate: queryForm.endDate || undefined,
    pageNo: pagination.page,
    pageSize: pagination.size
  })

  const buildLoginQueryParams = (): LoginLogQuery => ({
    username: loginQueryForm.username?.trim() || undefined,
    result: loginQueryForm.result || undefined,
    loginTimeFrom: loginQueryForm.loginTimeFrom || undefined,
    loginTimeTo: loginQueryForm.loginTimeTo || undefined,
    pageNo: loginPagination.page,
    pageSize: loginPagination.size
  })

  const buildAuditQueryParams = (): AuditLogQuery => ({
    auditType: auditQueryForm.auditType?.trim() || undefined,
    businessType: auditQueryForm.businessType?.trim() || undefined,
    businessNo: auditQueryForm.businessNo?.trim() || undefined,
    action: auditQueryForm.action?.trim() || undefined,
    operatorName: auditQueryForm.operatorName?.trim() || undefined,
    auditTimeFrom: auditQueryForm.auditTimeFrom || undefined,
    auditTimeTo: auditQueryForm.auditTimeTo || undefined,
    pageNo: auditPagination.page,
    pageSize: auditPagination.size
  })

  const loadData = async () => {
    loading.value = true
    try {
      const res = await options.getOperationLogs(buildQueryParams())
      tableData.value = res.records || []
      pagination.total = res.total || 0
    } catch {
      options.onError?.(t('systemLogs.message.loadDataFailed'))
    } finally {
      loading.value = false
    }
  }

  const loadLoginLogs = async () => {
    loginLoading.value = true
    try {
      const res = await options.getLoginLogs(buildLoginQueryParams())
      loginTableData.value = res.records || []
      loginPagination.total = res.total || 0
    } catch {
      options.onError?.(t('systemLogs.message.loadLoginLogsFailed'))
    } finally {
      loginLoading.value = false
    }
  }

  const loadAuditLogs = async () => {
    auditLoading.value = true
    try {
      const res = await options.getAuditLogs(buildAuditQueryParams())
      auditTableData.value = res.records || []
      auditPagination.total = res.total || 0
    } catch {
      options.onError?.(t('systemLogs.message.loadAuditLogsFailed'))
    } finally {
      auditLoading.value = false
    }
  }

  const handleQuery = async () => {
    syncDateRange()
    pagination.page = 1
    await loadData()
  }

  const handlePageChange = async (page: number) => {
    syncDateRange()
    pagination.page = page
    await loadData()
  }

  const handleSizeChange = async (size: number) => {
    syncDateRange()
    pagination.size = size
    pagination.page = 1
    await loadData()
  }

  const handleReset = async () => {
    queryForm.module = ''
    queryForm.operation = ''
    queryForm.bizNo = ''
    queryForm.operatorName = ''
    queryForm.status = ''
    queryForm.startDate = ''
    queryForm.endDate = ''
    dateRange.value = []
    pagination.page = 1
    await loadData()
  }

  const handleLoginQuery = async () => {
    syncLoginDateRange()
    loginPagination.page = 1
    await loadLoginLogs()
  }

  const handleLoginPageChange = async (page: number) => {
    syncLoginDateRange()
    loginPagination.page = page
    await loadLoginLogs()
  }

  const handleLoginSizeChange = async (size: number) => {
    syncLoginDateRange()
    loginPagination.size = size
    loginPagination.page = 1
    await loadLoginLogs()
  }

  const handleLoginReset = async () => {
    loginQueryForm.username = ''
    loginQueryForm.result = ''
    loginQueryForm.loginTimeFrom = ''
    loginQueryForm.loginTimeTo = ''
    loginDateRange.value = []
    loginPagination.page = 1
    await loadLoginLogs()
  }

  const handleAuditQuery = async () => {
    syncAuditDateRange()
    auditPagination.page = 1
    await loadAuditLogs()
  }

  const handleAuditPageChange = async (page: number) => {
    syncAuditDateRange()
    auditPagination.page = page
    await loadAuditLogs()
  }

  const handleAuditSizeChange = async (size: number) => {
    syncAuditDateRange()
    auditPagination.size = size
    auditPagination.page = 1
    await loadAuditLogs()
  }

  const handleAuditReset = async () => {
    auditQueryForm.auditType = ''
    auditQueryForm.businessType = ''
    auditQueryForm.businessNo = ''
    auditQueryForm.action = ''
    auditQueryForm.operatorName = ''
    auditQueryForm.auditTimeFrom = ''
    auditQueryForm.auditTimeTo = ''
    auditDateRange.value = []
    auditPagination.page = 1
    await loadAuditLogs()
  }

  const handleTabChange = async (tab?: string) => {
    const next = (tab || activeTab.value) as SystemLogTab
    activeTab.value = next
    if (next === 'login' && loginTableData.value.length === 0) {
      await loadLoginLogs()
    }
    if (next === 'audit' && auditTableData.value.length === 0) {
      await loadAuditLogs()
    }
  }

  const handleView = async (row: OperationLog) => {
    try {
      detailData.value = await options.getOperationLog(row.id)
      detailDialogVisible.value = true
      return true
    } catch {
      options.onError?.(t('systemLogs.message.loadDetailFailed'))
      return false
    }
  }

  const handleExport = async () => {
    try {
      syncDateRange()
      const blob = await options.exportOperationLogs(buildQueryParams())
      options.downloadBlob(blob, `${t('systemLogs.exportFileName')}_${Date.now()}.csv`)
      options.onSuccess?.(t('systemLogs.message.exportSuccess'))
      return true
    } catch {
      options.onError?.(t('systemLogs.message.exportFailed'))
      return false
    }
  }

  return {
    activeTab,
    auditDateRange,
    auditLoading,
    auditPagination,
    auditQueryForm,
    auditTableData,
    dateRange,
    detailData,
    detailDialogVisible,
    handleAuditPageChange,
    handleAuditQuery,
    handleAuditReset,
    handleAuditSizeChange,
    handleExport,
    handleLoginPageChange,
    handleLoginQuery,
    handleLoginReset,
    handleLoginSizeChange,
    handlePageChange,
    handleQuery,
    handleReset,
    handleSizeChange,
    handleTabChange,
    handleView,
    loadAuditLogs,
    loadData,
    loadLoginLogs,
    loading,
    loginDateRange,
    loginLoading,
    loginPagination,
    loginQueryForm,
    loginTableData,
    pagination,
    queryForm,
    tableData
  }
}
