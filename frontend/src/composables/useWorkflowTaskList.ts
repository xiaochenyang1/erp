import { computed, reactive, ref } from 'vue'

import type {
  WorkflowApproveRequest,
  WorkflowRejectRequest,
  WorkflowTask,
  WorkflowTaskQuery
} from '@/api/workflow'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export type WorkflowTaskActionMode = 'approve' | 'reject'

export type WorkflowTaskUserOption = {
  id: string | number
  username?: string
  realName?: string
}

export const cleanWorkflowTaskQuery = (query: WorkflowTaskQuery): WorkflowTaskQuery => ({
  pageNo: query.pageNo,
  pageSize: query.pageSize,
  businessType: query.businessType || undefined,
  businessId: query.businessId || undefined,
  businessNo: query.businessNo || undefined,
  status: query.status || undefined,
  overdueOnly: query.overdueOnly || undefined
})

/**
 * Workflow task list, detail, approve/reject, transfer and escalate.
 * Route seeding stays on the page via initialQuery / applyRouteQuery.
 */
export const useWorkflowTaskList = (
  t: Translate,
  options: {
    getWorkflowTasks: (params: WorkflowTaskQuery) => Promise<PageResponse<WorkflowTask>>
    getWorkflowTask: (id: string | number) => Promise<WorkflowTask>
    approveWorkflowTask: (data: WorkflowApproveRequest) => Promise<unknown>
    rejectWorkflowTask: (data: WorkflowRejectRequest) => Promise<unknown>
    transferWorkflowTask: (data: {
      taskId: string | number
      targetUserId: string | number
      comment?: string
    }) => Promise<unknown>
    escalateWorkflowTask: (data: {
      taskId: string | number
      targetUserId: string | number
      comment?: string
    }) => Promise<WorkflowTask | unknown>
    getUsers: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<WorkflowTaskUserOption>>
    initialQuery?: WorkflowTaskQuery
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
  }
) => {
  const loading = ref(false)
  const submitLoading = ref(false)
  const tableData = ref<WorkflowTask[]>([])
  const total = ref(0)
  const detailVisible = ref(false)
  const actionVisible = ref(false)
  const currentTask = ref<WorkflowTask | null>(null)
  const actionMode = ref<WorkflowTaskActionMode>('approve')
  const escalateVisible = ref(false)
  const escalateUserId = ref('')
  const escalateComment = ref('')
  const escalateUsers = ref<WorkflowTaskUserOption[]>([])
  const transferVisible = ref(false)
  const transferUserId = ref('')
  const transferUsers = ref<WorkflowTaskUserOption[]>([])

  const queryParams = reactive<WorkflowTaskQuery>({
    pageNo: options.initialQuery?.pageNo || 1,
    pageSize: options.initialQuery?.pageSize || 10,
    businessType: options.initialQuery?.businessType || '',
    businessId: options.initialQuery?.businessId,
    businessNo: options.initialQuery?.businessNo || '',
    status: options.initialQuery?.status || 'PENDING',
    overdueOnly: options.initialQuery?.overdueOnly || false
  })

  const actionForm = reactive({
    comment: ''
  })

  const actionTitle = computed(() =>
    actionMode.value === 'approve' ? t('workflow.approveAction') : t('workflow.reject')
  )

  const loadData = async () => {
    loading.value = true
    try {
      const response = await options.getWorkflowTasks(cleanWorkflowTaskQuery(queryParams))
      tableData.value = response.records || []
      total.value = response.total || 0
      return true
    } catch {
      options.onError?.(t('workflow.loadFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  const handleQuery = () => {
    queryParams.pageNo = 1
    return loadData()
  }

  const handleReset = () => {
    queryParams.businessType = ''
    queryParams.businessId = undefined
    queryParams.businessNo = ''
    queryParams.status = 'PENDING'
    queryParams.overdueOnly = false
    return handleQuery()
  }

  const handlePageChange = (page: number) => {
    queryParams.pageNo = page
    return loadData()
  }

  const handleSizeChange = (size: number) => {
    queryParams.pageSize = size
    queryParams.pageNo = 1
    return loadData()
  }

  const applyRouteQuery = (query: WorkflowTaskQuery) => {
    Object.assign(queryParams, query)
    return loadData()
  }

  const toTaskRow = (row: unknown) => row as WorkflowTask

  const handleView = async (row: WorkflowTask) => {
    try {
      currentTask.value = await options.getWorkflowTask(row.id)
      detailVisible.value = true
      return true
    } catch {
      options.onError?.(t('workflow.loadDetailFailed'))
      return false
    }
  }

  const openApprove = (row: WorkflowTask) => {
    currentTask.value = row
    actionMode.value = 'approve'
    actionVisible.value = true
  }

  const openReject = (row: WorkflowTask) => {
    currentTask.value = row
    actionMode.value = 'reject'
    actionVisible.value = true
  }

  const loadActiveUsers = async () => {
    try {
      const page = await options.getUsers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
      return page.records || []
    } catch {
      return [] as WorkflowTaskUserOption[]
    }
  }

  const openTransfer = async (row: WorkflowTask) => {
    currentTask.value = row
    transferUserId.value = ''
    transferUsers.value = await loadActiveUsers()
    transferVisible.value = true
  }

  const submitTransfer = async () => {
    if (!currentTask.value || !transferUserId.value) {
      options.onWarning?.(t('workflow.selectTransferUser'))
      return false
    }
    try {
      await options.transferWorkflowTask({
        taskId: currentTask.value.id,
        targetUserId: transferUserId.value,
        comment: actionForm.comment
      })
      options.onSuccess?.(t('workflow.transferSuccess'))
      transferVisible.value = false
      await loadData()
      return true
    } catch {
      options.onError?.(t('workflow.transferFailed'))
      return false
    }
  }

  const openEscalate = async (row: WorkflowTask) => {
    currentTask.value = row
    escalateUserId.value = ''
    escalateComment.value = ''
    const users = await loadActiveUsers()
    escalateUsers.value = users.filter(
      (user) => String(user.id) !== String(row.approverUserId || '')
    )
    escalateVisible.value = true
  }

  const submitEscalate = async () => {
    if (!currentTask.value || !escalateUserId.value) {
      options.onWarning?.(t('workflow.selectEscalationUser'))
      return false
    }
    submitLoading.value = true
    try {
      await options.escalateWorkflowTask({
        taskId: currentTask.value.id,
        targetUserId: escalateUserId.value,
        comment: escalateComment.value.trim() || undefined
      })
      options.onSuccess?.(t('workflow.escalationSuccess'))
      escalateVisible.value = false
      await loadData()
      return true
    } catch {
      options.onError?.(t('workflow.escalationFailed'))
      return false
    } finally {
      submitLoading.value = false
    }
  }

  const handleConfirmAction = async () => {
    if (!currentTask.value) return false
    if (actionMode.value === 'reject' && !actionForm.comment.trim()) {
      options.onWarning?.(t('workflow.inputRejectionReason'))
      return false
    }

    submitLoading.value = true
    try {
      if (actionMode.value === 'approve') {
        await options.approveWorkflowTask({
          taskId: currentTask.value.id,
          comment: actionForm.comment.trim() || undefined
        })
        options.onSuccess?.(t('workflow.approvalSuccess'))
      } else {
        await options.rejectWorkflowTask({
          taskId: currentTask.value.id,
          reason: actionForm.comment.trim()
        })
        options.onSuccess?.(t('workflow.rejectedSuccess'))
      }
      actionVisible.value = false
      detailVisible.value = false
      await loadData()
      return true
    } catch {
      options.onError?.(
        actionMode.value === 'approve'
          ? t('workflow.approvalFailed')
          : t('workflow.rejectFailed')
      )
      return false
    } finally {
      submitLoading.value = false
    }
  }

  const resetAction = () => {
    actionForm.comment = ''
  }

  return {
    actionForm,
    actionMode,
    actionTitle,
    actionVisible,
    applyRouteQuery,
    currentTask,
    detailVisible,
    escalateComment,
    escalateUserId,
    escalateUsers,
    escalateVisible,
    handleConfirmAction,
    handlePageChange,
    handleQuery,
    handleReset,
    handleSizeChange,
    handleView,
    loadData,
    loading,
    openApprove,
    openEscalate,
    openReject,
    openTransfer,
    queryParams,
    resetAction,
    submitEscalate,
    submitLoading,
    submitTransfer,
    tableData,
    toTaskRow,
    total,
    transferUserId,
    transferUsers,
    transferVisible
  }
}
