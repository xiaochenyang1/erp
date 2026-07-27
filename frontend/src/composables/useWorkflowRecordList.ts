import { reactive, ref } from 'vue'

import type {
  WorkflowRecord,
  WorkflowRecordQuery,
  WorkflowWithdrawRequest
} from '@/api/workflow'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export const cleanWorkflowRecordQuery = (query: WorkflowRecordQuery): WorkflowRecordQuery => ({
  pageNo: query.pageNo,
  pageSize: query.pageSize,
  businessType: query.businessType || undefined,
  businessId: query.businessId || undefined,
  businessNo: query.businessNo || undefined,
  action: query.action || undefined
})

export const filterBusinessRecords = (
  records: WorkflowRecord[],
  filters: { businessNo?: string; action?: string }
) =>
  (records || []).filter((record) => {
    const businessNoMatched =
      !filters.businessNo || record.businessNo?.includes(filters.businessNo)
    const actionMatched = !filters.action || record.action === filters.action
    return businessNoMatched && actionMatched
  })

export const paginateRecords = <T>(
  records: T[],
  pageNo = 1,
  pageSize = 10
) => {
  const start = (pageNo - 1) * pageSize
  return records.slice(start, start + pageSize)
}

/**
 * Workflow record list with optional business-scoped query and withdraw.
 * Route seed values are injected so the composable stays router-free.
 */
export const useWorkflowRecordList = (
  t: Translate,
  options: {
    getWorkflowRecords: (params: WorkflowRecordQuery) => Promise<PageResponse<WorkflowRecord>>
    getBusinessWorkflowRecords: (
      businessType: string,
      businessId: string | number
    ) => Promise<WorkflowRecord[]>
    withdrawWorkflow: (data: WorkflowWithdrawRequest) => Promise<unknown>
    initialQuery?: Partial<WorkflowRecordQuery>
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const loading = ref(false)
  const tableData = ref<WorkflowRecord[]>([])
  const total = ref(0)
  const withdrawVisible = ref(false)
  const withdrawSubmitting = ref(false)
  const currentRecord = ref<WorkflowRecord | null>(null)
  const withdrawForm = reactive({
    comment: ''
  })

  const queryParams = reactive<WorkflowRecordQuery>({
    pageNo: options.initialQuery?.pageNo || 1,
    pageSize: options.initialQuery?.pageSize || 10,
    businessType: options.initialQuery?.businessType || '',
    businessId: options.initialQuery?.businessId,
    businessNo: options.initialQuery?.businessNo || '',
    action: options.initialQuery?.action || ''
  })

  const loadData = async () => {
    loading.value = true
    try {
      if (queryParams.businessType && queryParams.businessId) {
        const records = await options.getBusinessWorkflowRecords(
          queryParams.businessType,
          queryParams.businessId
        )
        const filteredRecords = filterBusinessRecords(records, {
          businessNo: queryParams.businessNo,
          action: queryParams.action
        })
        total.value = filteredRecords.length
        tableData.value = paginateRecords(
          filteredRecords,
          queryParams.pageNo,
          queryParams.pageSize
        )
      } else {
        const response = await options.getWorkflowRecords(cleanWorkflowRecordQuery(queryParams))
        tableData.value = response.records || []
        total.value = response.total || 0
      }
      return true
    } catch {
      options.onError?.(t('workflowRecord.message.loadFailed'))
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
    queryParams.action = ''
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

  const openWithdraw = (row: WorkflowRecord) => {
    currentRecord.value = row
    withdrawVisible.value = true
  }

  const submitWithdraw = async () => {
    if (!currentRecord.value) return false
    withdrawSubmitting.value = true
    try {
      await options.withdrawWorkflow({
        businessType: currentRecord.value.businessType,
        businessId: currentRecord.value.businessId,
        comment: withdrawForm.comment.trim() || undefined
      })
      options.onSuccess?.(t('workflowRecord.message.withdrawSuccess'))
      withdrawVisible.value = false
      await loadData()
      return true
    } catch {
      options.onError?.(t('workflowRecord.message.withdrawFailed'))
      return false
    } finally {
      withdrawSubmitting.value = false
    }
  }

  const resetWithdraw = () => {
    withdrawForm.comment = ''
    currentRecord.value = null
  }

  return {
    currentRecord,
    handlePageChange,
    handleQuery,
    handleReset,
    handleSizeChange,
    loadData,
    loading,
    openWithdraw,
    queryParams,
    resetWithdraw,
    submitWithdraw,
    tableData,
    total,
    withdrawForm,
    withdrawSubmitting,
    withdrawVisible
  }
}
