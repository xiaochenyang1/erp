import { computed, ref } from 'vue'

import type {
  ProductionCostPeriod,
  ProductionCostPeriodCloseBatch,
  ProductionCostPeriodCloseEvidence,
  ProductionCostPeriodCloseItem,
  ProductionCostPeriodClosePreview
} from '@/api/production'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string

interface Options {
  loadPeriods: () => Promise<ProductionCostPeriod[]>
  loadPreview: (periodId: string | number) => Promise<ProductionCostPeriodClosePreview>
  closeBatch: (
    periodId: string | number,
    payload: { orderIds: Array<string | number>; remark?: string }
  ) => Promise<ProductionCostPeriodCloseBatch>
  loadEvidence: (
    periodId: string | number,
    params?: { pageNo?: number; pageSize?: number }
  ) => Promise<PageResponse<ProductionCostPeriodCloseEvidence>>
  confirm?: (message: string, title: string) => Promise<unknown>
  onError?: (message: string) => void
  onSuccess?: (message: string) => void
  onWarning?: (message: string) => void
  onCompleted?: () => void | Promise<void>
}

export const useProductionCostPeriodClose = (t: Translate, options: Options) => {
  const dialogVisible = ref(false)
  const loading = ref(false)
  const submitting = ref(false)
  const evidenceLoading = ref(false)
  const periods = ref<ProductionCostPeriod[]>([])
  const periodId = ref<string>('')
  const preview = ref<ProductionCostPeriodClosePreview | null>(null)
  const selectedItems = ref<ProductionCostPeriodCloseItem[]>([])
  const remark = ref('')
  const batchResult = ref<ProductionCostPeriodCloseBatch | null>(null)
  const evidence = ref<ProductionCostPeriodCloseEvidence[]>([])

  const selectedPeriod = computed(() => periods.value.find((item) => item.id === periodId.value) || null)
  const canSubmit = computed(() => selectedPeriod.value?.status === 'OPEN' && selectedItems.value.length > 0)

  const loadPreview = async () => {
    preview.value = null
    selectedItems.value = []
    if (!periodId.value) {
      return
    }
    loading.value = true
    try {
      preview.value = await options.loadPreview(periodId.value)
    } catch {
      options.onError?.(t('productionOrder.periodCloseLoadFailed'))
    } finally {
      loading.value = false
    }
  }

  const loadEvidence = async () => {
    evidence.value = []
    if (!periodId.value) {
      return
    }
    evidenceLoading.value = true
    try {
      evidence.value = (await options.loadEvidence(periodId.value, { pageNo: 1, pageSize: 20 })).records
    } catch {
      options.onError?.(t('productionOrder.periodCloseEvidenceLoadFailed'))
    } finally {
      evidenceLoading.value = false
    }
  }

  const loadPeriod = async () => {
    batchResult.value = null
    await Promise.all([loadPreview(), loadEvidence()])
  }

  const open = async () => {
    dialogVisible.value = true
    loading.value = true
    try {
      periods.value = (await options.loadPeriods()).slice().sort((left, right) =>
        left.periodMonth.localeCompare(right.periodMonth)
      )
      const earliestOpen = periods.value.find((period) => period.status === 'OPEN')
      periodId.value = earliestOpen?.id || periods.value[periods.value.length - 1]?.id || ''
    } catch {
      options.onError?.(t('productionOrder.periodClosePeriodsLoadFailed'))
    } finally {
      loading.value = false
    }
    await loadPeriod()
  }

  const handleSelectionChange = (items: ProductionCostPeriodCloseItem[]) => {
    selectedItems.value = items
  }

  const submit = async () => {
    if (!canSubmit.value || !preview.value) {
      options.onWarning?.(t('productionOrder.periodCloseSelectRequired'))
      return
    }
    try {
      await options.confirm?.(
        t('productionOrder.periodCloseConfirm', {
          count: selectedItems.value.length,
          period: preview.value.periodMonth
        }),
        t('productionOrder.periodClose')
      )
    } catch {
      return
    }
    submitting.value = true
    try {
      batchResult.value = await options.closeBatch(periodId.value, {
        orderIds: selectedItems.value.map((item) => item.orderId),
        remark: remark.value.trim() || undefined
      })
      if (batchResult.value.failureCount > 0) {
        options.onWarning?.(t('productionOrder.periodClosePartial', {
          success: batchResult.value.successCount,
          failed: batchResult.value.failureCount
        }))
      } else {
        options.onSuccess?.(t('productionOrder.periodCloseSuccess', {
          count: batchResult.value.successCount
        }))
      }
      remark.value = ''
      await Promise.all([loadPreview(), loadEvidence(), options.onCompleted?.()])
    } catch {
      options.onError?.(t('productionOrder.periodCloseFailed'))
    } finally {
      submitting.value = false
    }
  }

  return {
    batchResult,
    canSubmit,
    dialogVisible,
    evidence,
    evidenceLoading,
    handleSelectionChange,
    loadEvidence,
    loadPeriod,
    loadPreview,
    loading,
    open,
    periodId,
    periods,
    preview,
    remark,
    selectedItems,
    selectedPeriod,
    submit,
    submitting
  }
}
