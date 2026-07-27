import { reactive, ref } from 'vue'

import type {
  BusinessTraceDocument,
  BusinessTraceQuery,
  BusinessTraceResponse
} from '@/api/businessTrace'
import type {
  BusinessTimelineEvent
} from '@/api/businessTimeline'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export const createEmptyBusinessTrace = (): BusinessTraceResponse => ({
  keyword: '',
  documents: [],
  timeline: [],
  summary: {
    documentCount: 0,
    timelineCount: 0,
    openReceivableAmount: 0,
    openPayableAmount: 0,
    inventoryMovementQuantity: 0,
    failedOperationCount: 0,
    openExceptionTicketCount: 0
  },
  exceptionTickets: [],
  generatedAt: ''
})

/** Keep contract-scanned identity: document-trace routes pass through unchanged. */
export const normalizeTraceRoute = (target: string) => target

/**
 * Keyword search + business timeline drawer for the traces page.
 * Router navigation stays on the page.
 */
export const useBusinessTraceList = (
  t: Translate,
  options: {
    getBusinessTrace: (params: BusinessTraceQuery) => Promise<BusinessTraceResponse>
    getBusinessTimeline: (params: {
      pageNo: number
      pageSize: number
      businessType: string
      businessId: string
      businessNo?: string
    }) => Promise<PageResponse<BusinessTimelineEvent>>
    createBusinessTimelineComment: (data: {
      businessType: string
      businessId: string
      businessNo?: string
      content: string
    }) => Promise<unknown>
    onKeywordChange?: (keyword: string) => void
    onKeywordClear?: () => void
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
    onInfo?: Notify
  }
) => {
  const loading = ref(false)
  const trace = ref<BusinessTraceResponse>(createEmptyBusinessTrace())
  const queryForm = reactive({
    keyword: ''
  })

  const businessTimelineVisible = ref(false)
  const businessTimelineLoading = ref(false)
  const timelineCommentSubmitting = ref(false)
  const selectedTimelineDocument = ref<BusinessTraceDocument>()
  const businessTimelineEvents = ref<BusinessTimelineEvent[]>([])
  const businessTimelineTotal = ref(0)
  const businessTimelineQuery = reactive({
    pageNo: 1,
    pageSize: 20
  })
  const timelineCommentForm = reactive({
    content: ''
  })

  const handleSearch = async () => {
    const keyword = queryForm.keyword.trim()
    if (!keyword) {
      trace.value = createEmptyBusinessTrace()
      options.onKeywordClear?.()
      return false
    }
    loading.value = true
    try {
      trace.value = await options.getBusinessTrace({ keyword })
      options.onKeywordChange?.(keyword)
      return true
    } catch {
      options.onError?.(t('financeReportPages.traces.message.loadFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  const handleReset = () => {
    queryForm.keyword = ''
    trace.value = createEmptyBusinessTrace()
    options.onKeywordClear?.()
  }

  const applyKeyword = async (keyword?: string) => {
    if (!keyword?.trim()) return false
    queryForm.keyword = keyword.trim()
    return handleSearch()
  }

  const openBusinessTimeline = (
    businessType: string,
    businessId: string,
    businessNo: string,
    row: BusinessTraceDocument
  ) => {
    selectedTimelineDocument.value = {
      ...row,
      documentType: businessType,
      documentId: businessId,
      bizNo: businessNo
    }
    businessTimelineQuery.pageNo = 1
    timelineCommentForm.content = ''
    businessTimelineVisible.value = true
    return loadBusinessTimeline()
  }

  const loadBusinessTimeline = async () => {
    if (!selectedTimelineDocument.value) return false
    businessTimelineLoading.value = true
    try {
      const page = await options.getBusinessTimeline({
        pageNo: businessTimelineQuery.pageNo,
        pageSize: businessTimelineQuery.pageSize,
        businessType: selectedTimelineDocument.value.documentType,
        businessId: selectedTimelineDocument.value.documentId,
        businessNo: selectedTimelineDocument.value.bizNo
      })
      businessTimelineEvents.value = page.records || []
      businessTimelineTotal.value = page.total || 0
      return true
    } catch {
      options.onError?.(t('financeReportPages.traces.message.timelineLoadFailed'))
      return false
    } finally {
      businessTimelineLoading.value = false
    }
  }

  const handleTimelinePageChange = async (page: number) => {
    businessTimelineQuery.pageNo = page
    return loadBusinessTimeline()
  }

  const handleTimelineSizeChange = async (size: number) => {
    businessTimelineQuery.pageSize = size
    businessTimelineQuery.pageNo = 1
    return loadBusinessTimeline()
  }

  const submitTimelineComment = async () => {
    if (!selectedTimelineDocument.value) return false
    const content = timelineCommentForm.content.trim()
    if (!content) {
      options.onWarning?.(t('financeReportPages.traces.message.commentRequired'))
      return false
    }
    timelineCommentSubmitting.value = true
    try {
      await options.createBusinessTimelineComment({
        businessType: selectedTimelineDocument.value.documentType,
        businessId: selectedTimelineDocument.value.documentId,
        businessNo: selectedTimelineDocument.value.bizNo,
        content
      })
      timelineCommentForm.content = ''
      options.onSuccess?.(t('financeReportPages.traces.message.commentSubmitted'))
      businessTimelineQuery.pageNo = 1
      await loadBusinessTimeline()
      return true
    } catch {
      options.onError?.(t('financeReportPages.traces.message.commentFailed'))
      return false
    } finally {
      timelineCommentSubmitting.value = false
    }
  }

  const resolveRouteTarget = (target?: string) => {
    if (!target) {
      options.onInfo?.(t('financeReportPages.traces.message.noRoute'))
      return null
    }
    return normalizeTraceRoute(target)
  }

  return {
    applyKeyword,
    businessTimelineEvents,
    businessTimelineLoading,
    businessTimelineQuery,
    businessTimelineTotal,
    businessTimelineVisible,
    handleReset,
    handleSearch,
    handleTimelinePageChange,
    handleTimelineSizeChange,
    loadBusinessTimeline,
    loading,
    openBusinessTimeline,
    queryForm,
    resolveRouteTarget,
    selectedTimelineDocument,
    submitTimelineComment,
    timelineCommentForm,
    timelineCommentSubmitting,
    trace
  }
}
