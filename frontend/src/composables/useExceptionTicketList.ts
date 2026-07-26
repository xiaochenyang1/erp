import { reactive, ref } from 'vue'

import type {
  ExceptionTicket,
  ExceptionTicketQuery
} from '@/api/exceptionTicket'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export const normalizeOptionalId = (value?: string | number) => {
  const normalized = value == null ? '' : String(value).trim()
  return normalized || undefined
}

/**
 * Query and pagination for the exception ticket list.
 * Trace navigation stays on the page so it can use the router.
 */
export const useExceptionTicketList = (
  t: Translate,
  options: {
    getTickets: (params: ExceptionTicketQuery) => Promise<PageResponse<ExceptionTicket>>
    onError?: Notify
  }
) => {
  const queryForm = reactive<ExceptionTicketQuery>({
    keyword: '',
    status: '',
    priority: '',
    category: '',
    assigneeUserId: undefined,
    sourceNo: '',
    overdueOnly: false
  })

  const pagination = reactive({
    page: 1,
    size: 20,
    total: 0
  })

  const loading = ref(false)
  const tableData = ref<ExceptionTicket[]>([])

  const buildQueryParams = (): ExceptionTicketQuery => ({
    ...queryForm,
    assigneeUserId: normalizeOptionalId(queryForm.assigneeUserId),
    keyword: queryForm.keyword || undefined,
    status: queryForm.status || undefined,
    priority: queryForm.priority || undefined,
    category: queryForm.category || undefined,
    sourceNo: queryForm.sourceNo || undefined,
    overdueOnly: queryForm.overdueOnly || undefined,
    pageNo: pagination.page,
    pageSize: pagination.size
  })

  const loadData = async () => {
    loading.value = true
    try {
      const page = await options.getTickets(buildQueryParams())
      tableData.value = page.records || []
      pagination.total = page.total || 0
    } catch {
      options.onError?.(t('exceptionTicket.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const handleQuery = async () => {
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

  const handleReset = async () => {
    queryForm.keyword = ''
    queryForm.status = ''
    queryForm.priority = ''
    queryForm.category = ''
    queryForm.assigneeUserId = undefined
    queryForm.sourceNo = ''
    queryForm.overdueOnly = false
    pagination.page = 1
    await loadData()
  }

  return {
    buildQueryParams,
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
