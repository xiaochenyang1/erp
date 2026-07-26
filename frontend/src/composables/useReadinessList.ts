import { reactive, ref } from 'vue'

import type {
  ReadinessPreflight,
  ReadinessRun,
  ReadinessRunDetail,
  ReadinessRunQuery
} from '@/api/readiness'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

/**
 * Preflight, run list, detail drawer and record-preflight action.
 * Dialog submit flows live in useReadinessForms.
 */
export const useReadinessList = (
  t: Translate,
  options: {
    getPreflight: () => Promise<ReadinessPreflight>
    getRuns: (params: ReadinessRunQuery) => Promise<PageResponse<ReadinessRun>>
    getRunDetail: (id: string | number) => Promise<ReadinessRunDetail>
    recordPreflightEvidence: (runId: string | number) => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const queryForm = reactive<ReadinessRunQuery>({
    pageNo: 1,
    pageSize: 20,
    releaseCommit: '',
    environment: '',
    status: '',
    decision: ''
  })

  const preflight = ref<ReadinessPreflight>({
    overallStatus: '',
    checkedAt: '',
    items: []
  })
  const preflightLoading = ref(false)
  const runLoading = ref(false)
  const runData = ref<ReadinessRun[]>([])
  const runTotal = ref(0)

  const detailVisible = ref(false)
  const selectedDetail = ref<ReadinessRunDetail | null>(null)
  const selectedRun = ref<ReadinessRun | null>(null)

  const loadPreflight = async () => {
    preflightLoading.value = true
    try {
      preflight.value = await options.getPreflight()
    } catch {
      options.onError?.(t('systemReadiness.message.loadPreflightFailed'))
    } finally {
      preflightLoading.value = false
    }
  }

  const loadRuns = async () => {
    runLoading.value = true
    try {
      const res = await options.getRuns({
        pageNo: queryForm.pageNo,
        pageSize: queryForm.pageSize,
        releaseCommit: queryForm.releaseCommit || undefined,
        environment: queryForm.environment || undefined,
        status: queryForm.status || undefined,
        decision: queryForm.decision || undefined
      })
      runData.value = res.records || []
      runTotal.value = res.total || 0
    } catch {
      options.onError?.(t('systemReadiness.message.loadRunsFailed'))
    } finally {
      runLoading.value = false
    }
  }

  const handleQuery = async () => {
    queryForm.pageNo = 1
    await loadRuns()
  }

  const handleReset = async () => {
    Object.assign(queryForm, {
      pageNo: 1,
      pageSize: queryForm.pageSize,
      releaseCommit: '',
      environment: '',
      status: '',
      decision: ''
    })
    await loadRuns()
  }

  const handlePageChange = async (page: number) => {
    queryForm.pageNo = page
    await loadRuns()
  }

  const handleSizeChange = async (size: number) => {
    queryForm.pageSize = size
    queryForm.pageNo = 1
    await loadRuns()
  }

  const openDetail = async (row: ReadinessRun) => {
    try {
      selectedDetail.value = await options.getRunDetail(row.id)
      selectedRun.value = selectedDetail.value.run
      detailVisible.value = true
      return true
    } catch {
      options.onError?.(t('systemReadiness.message.loadRunDetailFailed'))
      return false
    }
  }

  const refreshDetail = async () => {
    if (!selectedDetail.value) return false
    try {
      selectedDetail.value = await options.getRunDetail(selectedDetail.value.run.id)
      selectedRun.value = selectedDetail.value.run
      return true
    } catch {
      options.onError?.(t('systemReadiness.message.loadRunDetailFailed'))
      return false
    }
  }

  const handleRecordPreflight = async (row: ReadinessRun) => {
    try {
      await options.recordPreflightEvidence(row.id)
      options.onSuccess?.(t('systemReadiness.message.preflightRecorded'))
      await loadRuns()
      if (selectedDetail.value?.run.id === row.id) {
        await refreshDetail()
      }
      return true
    } catch {
      options.onError?.(t('systemReadiness.message.recordPreflightFailed'))
      return false
    }
  }

  return {
    detailVisible,
    handlePageChange,
    handleQuery,
    handleRecordPreflight,
    handleReset,
    handleSizeChange,
    loadPreflight,
    loadRuns,
    openDetail,
    preflight,
    preflightLoading,
    queryForm,
    refreshDetail,
    runData,
    runLoading,
    runTotal,
    selectedDetail,
    selectedRun
  }
}
