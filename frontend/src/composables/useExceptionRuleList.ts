import { computed, reactive, ref } from 'vue'

import type {
  ExceptionRule,
  ExceptionRuleHit,
  ExceptionRuleHitQuery,
  ExceptionRuleQuery,
  ExceptionRuleScanResult
} from '@/api/exceptionRule'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export type EnabledFilter = '' | 'true' | 'false'

export const normalizeOptionalId = (value?: string | number) => {
  const normalized = value == null ? '' : String(value).trim()
  return normalized || undefined
}

/**
 * Rule list, hit list, scan and enable/disable for the exception rule page.
 * Ticket navigation stays on the page so it can use the router.
 */
export const useExceptionRuleList = (
  t: Translate,
  options: {
    getRules: (params: ExceptionRuleQuery) => Promise<PageResponse<ExceptionRule>>
    getHits: (params: ExceptionRuleHitQuery) => Promise<PageResponse<ExceptionRuleHit>>
    scanRule: (id: string | number) => Promise<ExceptionRuleScanResult>
    scanAll: () => Promise<ExceptionRuleScanResult[]>
    enableRule: (id: string | number) => Promise<unknown>
    disableRule: (id: string | number) => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
  }
) => {
  const ruleQueryForm = reactive({
    keyword: '',
    ruleType: '',
    enabled: '' as EnabledFilter
  })

  const hitQueryForm = reactive<ExceptionRuleHitQuery>({
    ruleType: '',
    sourceNo: '',
    ticketId: undefined
  })

  const rulePagination = reactive({
    page: 1,
    size: 20,
    total: 0
  })

  const hitPagination = reactive({
    page: 1,
    size: 20,
    total: 0
  })

  const ruleLoading = ref(false)
  const hitLoading = ref(false)
  const scanAllLoading = ref(false)
  const scanRuleLoadingId = ref<string>()
  const toggleLoadingId = ref<string>()

  const ruleData = ref<ExceptionRule[]>([])
  const hitData = ref<ExceptionRuleHit[]>([])
  const scanResults = ref<ExceptionRuleScanResult[]>([])

  const totalScanHits = computed(() =>
    scanResults.value.reduce((sum, item) => sum + item.hitCount, 0)
  )
  const totalScanTickets = computed(() =>
    scanResults.value.reduce((sum, item) => sum + item.ticketCreatedCount, 0)
  )
  const totalScanDuplicates = computed(() =>
    scanResults.value.reduce((sum, item) => sum + item.duplicateTicketCount, 0)
  )

  const buildRuleQueryParams = (): ExceptionRuleQuery => ({
    keyword: ruleQueryForm.keyword?.trim() || undefined,
    ruleType: ruleQueryForm.ruleType || undefined,
    enabled: ruleQueryForm.enabled === '' ? undefined : ruleQueryForm.enabled === 'true',
    pageNo: rulePagination.page,
    pageSize: rulePagination.size
  })

  const buildHitQueryParams = (): ExceptionRuleHitQuery => ({
    ruleType: hitQueryForm.ruleType || undefined,
    sourceNo: hitQueryForm.sourceNo?.trim() || undefined,
    ticketId: normalizeOptionalId(hitQueryForm.ticketId),
    pageNo: hitPagination.page,
    pageSize: hitPagination.size
  })

  const loadRules = async () => {
    ruleLoading.value = true
    try {
      const page = await options.getRules(buildRuleQueryParams())
      ruleData.value = page.records || []
      rulePagination.total = page.total || 0
    } catch {
      options.onError?.(t('exceptionRule.message.rulesLoadFailed'))
    } finally {
      ruleLoading.value = false
    }
  }

  const loadHits = async () => {
    hitLoading.value = true
    try {
      const page = await options.getHits(buildHitQueryParams())
      hitData.value = page.records || []
      hitPagination.total = page.total || 0
    } catch {
      options.onError?.(t('exceptionRule.message.hitsLoadFailed'))
    } finally {
      hitLoading.value = false
    }
  }

  const handleRuleQuery = async () => {
    rulePagination.page = 1
    await loadRules()
  }

  const handleRuleReset = async () => {
    ruleQueryForm.keyword = ''
    ruleQueryForm.ruleType = ''
    ruleQueryForm.enabled = ''
    rulePagination.page = 1
    await loadRules()
  }

  const handleRulePageChange = async (page: number) => {
    rulePagination.page = page
    await loadRules()
  }

  const handleRuleSizeChange = async (size: number) => {
    rulePagination.size = size
    rulePagination.page = 1
    await loadRules()
  }

  const handleHitQuery = async () => {
    hitPagination.page = 1
    await loadHits()
  }

  const handleHitReset = async () => {
    hitQueryForm.ruleType = ''
    hitQueryForm.sourceNo = ''
    hitQueryForm.ticketId = undefined
    hitPagination.page = 1
    await loadHits()
  }

  const handleHitPageChange = async (page: number) => {
    hitPagination.page = page
    await loadHits()
  }

  const handleHitSizeChange = async (size: number) => {
    hitPagination.size = size
    hitPagination.page = 1
    await loadHits()
  }

  const handleScanRule = async (row: ExceptionRule) => {
    if (!row.enabled) {
      options.onWarning?.(t('exceptionRule.message.ruleDisabled'))
      return false
    }
    scanRuleLoadingId.value = row.id
    try {
      const result = await options.scanRule(row.id)
      scanResults.value = [result]
      options.onSuccess?.(t('exceptionRule.message.scanComplete', { count: result.hitCount }))
      await Promise.all([loadRules(), loadHits()])
      return true
    } catch {
      options.onError?.(t('exceptionRule.message.scanFailed'))
      return false
    } finally {
      scanRuleLoadingId.value = undefined
    }
  }

  const handleScanAll = async () => {
    scanAllLoading.value = true
    try {
      scanResults.value = await options.scanAll()
      options.onSuccess?.(
        t('exceptionRule.message.scanComplete', { count: totalScanHits.value })
      )
      await Promise.all([loadRules(), loadHits()])
      return true
    } catch {
      options.onError?.(t('exceptionRule.message.scanFailed'))
      return false
    } finally {
      scanAllLoading.value = false
    }
  }

  const handleToggleRule = async (row: ExceptionRule) => {
    toggleLoadingId.value = row.id
    try {
      if (row.enabled) {
        await options.disableRule(row.id)
        options.onSuccess?.(t('exceptionRule.message.disabled'))
      } else {
        await options.enableRule(row.id)
        options.onSuccess?.(t('exceptionRule.message.enabled'))
      }
      await loadRules()
      return true
    } catch {
      options.onError?.(t('exceptionRule.message.toggleFailed'))
      return false
    } finally {
      toggleLoadingId.value = undefined
    }
  }

  return {
    handleHitPageChange,
    handleHitQuery,
    handleHitReset,
    handleHitSizeChange,
    handleRulePageChange,
    handleRuleQuery,
    handleRuleReset,
    handleRuleSizeChange,
    handleScanAll,
    handleScanRule,
    handleToggleRule,
    hitData,
    hitLoading,
    hitPagination,
    hitQueryForm,
    loadHits,
    loadRules,
    ruleData,
    ruleLoading,
    rulePagination,
    ruleQueryForm,
    scanAllLoading,
    scanResults,
    scanRuleLoadingId,
    toggleLoadingId,
    totalScanDuplicates,
    totalScanHits,
    totalScanTickets
  }
}
