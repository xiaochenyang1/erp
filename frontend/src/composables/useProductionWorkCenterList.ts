import { reactive, ref } from 'vue'

import type { WorkCenter, WorkCenterQuery } from '@/api/production'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title: string,
  options?: { type?: string }
) => Promise<unknown>

/**
 * Query, print and enable/disable for production work centers.
 */
export const useProductionWorkCenterList = (
  t: Translate,
  options: {
    getWorkCenters: (params: WorkCenterQuery) => Promise<PageResponse<WorkCenter>>
    getWorkCenter: (id: string | number) => Promise<WorkCenter>
    enableWorkCenter: (id: string | number) => Promise<unknown>
    disableWorkCenter: (id: string | number) => Promise<unknown>
    printWorkCenter: (doc: WorkCenter) => void
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const queryForm = reactive({
    keyword: '',
    status: ''
  })

  const loading = ref(false)
  const tableData = ref<WorkCenter[]>([])
  const pagination = reactive({
    page: 1,
    size: 20,
    total: 0
  })

  const loadData = async () => {
    loading.value = true
    try {
      const res = await options.getWorkCenters({
        keyword: queryForm.keyword || undefined,
        status: queryForm.status || undefined,
        pageNo: pagination.page,
        pageSize: pagination.size
      })
      tableData.value = res.records || []
      pagination.total = res.total || 0
    } catch {
      options.onError?.(t('productionWorkCenter.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const handleQuery = async () => {
    pagination.page = 1
    await loadData()
  }

  const handleReset = async () => {
    queryForm.keyword = ''
    queryForm.status = ''
    pagination.page = 1
    await loadData()
  }

  const handlePageChange = async (page: number) => {
    pagination.page = page
    await loadData()
  }

  const handleSizeChange = async (size: number) => {
    pagination.size = size
    pagination.page = 1
    await loadData()
  }

  const handlePrint = async (row: WorkCenter) => {
    try {
      const detail = await options.getWorkCenter(row.id)
      options.printWorkCenter(detail)
      return true
    } catch {
      options.onError?.(t('productionWorkCenter.message.printLoadFailed'))
      return false
    }
  }

  const handleEnable = async (row: WorkCenter) => {
    try {
      await options.confirm(
        t('productionWorkCenter.message.enableConfirm', { name: row.workCenterName }),
        t('productionWorkCenter.message.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    try {
      await options.enableWorkCenter(row.id)
      options.onSuccess?.(t('productionWorkCenter.message.enabled'))
      await loadData()
      return true
    } catch {
      // Global interceptor already surfaces API errors.
      return false
    }
  }

  const handleDisable = async (row: WorkCenter) => {
    try {
      await options.confirm(
        t('productionWorkCenter.message.disableConfirm', { name: row.workCenterName }),
        t('productionWorkCenter.message.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    try {
      await options.disableWorkCenter(row.id)
      options.onSuccess?.(t('productionWorkCenter.message.disabled'))
      await loadData()
      return true
    } catch {
      // Global interceptor already surfaces API errors.
      return false
    }
  }

  return {
    handleDisable,
    handleEnable,
    handlePageChange,
    handlePrint,
    handleQuery,
    handleReset,
    handleSizeChange,
    loadData,
    loading,
    pagination,
    queryForm,
    tableData
  }
}
