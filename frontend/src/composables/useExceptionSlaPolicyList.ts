import { reactive, ref } from 'vue'

import type {
  ExceptionSlaPolicy,
  ExceptionSlaPolicyQuery
} from '@/api/exceptionSlaPolicy'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export type EnabledFilter = '' | 'true' | 'false'

/** Query and pagination for exception SLA policies. */
export const useExceptionSlaPolicyList = (
  t: Translate,
  options: {
    getPolicies: (params: ExceptionSlaPolicyQuery) => Promise<PageResponse<ExceptionSlaPolicy>>
    onError?: Notify
  }
) => {
  const queryForm = reactive({
    category: '',
    priority: '',
    enabled: '' as EnabledFilter
  })

  const pagination = reactive({
    page: 1,
    size: 20,
    total: 0
  })

  const loading = ref(false)
  const tableData = ref<ExceptionSlaPolicy[]>([])

  const buildQueryParams = (): ExceptionSlaPolicyQuery => ({
    category: queryForm.category || undefined,
    priority: queryForm.priority || undefined,
    enabled: queryForm.enabled === '' ? undefined : queryForm.enabled === 'true',
    pageNo: pagination.page,
    pageSize: pagination.size
  })

  const loadData = async () => {
    loading.value = true
    try {
      const page = await options.getPolicies(buildQueryParams())
      tableData.value = page.records || []
      pagination.total = page.total || 0
    } catch {
      options.onError?.(t('exceptionSlaPolicy.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const handleQuery = async () => {
    pagination.page = 1
    await loadData()
  }

  const handleReset = async () => {
    queryForm.category = ''
    queryForm.priority = ''
    queryForm.enabled = ''
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

  return {
    handlePageChange,
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
