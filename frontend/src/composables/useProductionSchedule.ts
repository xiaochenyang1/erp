import { computed, ref } from 'vue'

import type {
  ProductionScheduleRequest,
  ProductionScheduleResult
} from '@/api/production'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

const formatDate = (date: Date) => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const defaultRange = (): [string, string] => {
  const start = new Date()
  const end = new Date(start)
  end.setDate(end.getDate() + 14)
  return [formatDate(start), formatDate(end)]
}

export const useProductionSchedule = (
  t: Translate,
  options: {
    previewSchedule: (data: ProductionScheduleRequest) => Promise<ProductionScheduleResult>
    runSchedule: (data: ProductionScheduleRequest) => Promise<ProductionScheduleResult>
    confirm: (message: string, title: string) => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const dateRange = ref<[string, string]>(defaultRange())
  const loading = ref(false)
  const running = ref(false)
  const result = ref<ProductionScheduleResult>()
  const hasResult = computed(() => result.value != null)

  const request = (): ProductionScheduleRequest => {
    const [startDate, endDate] = dateRange.value || []
    if (!startDate || !endDate) throw new Error(t('productionSchedule.validation.dateRange'))
    return { startDate, endDate }
  }

  const preview = async () => {
    loading.value = true
    try {
      result.value = await options.previewSchedule(request())
      return true
    } catch (error) {
      options.onError?.(error instanceof Error ? error.message : t('productionSchedule.message.previewFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  const execute = async () => {
    let payload: ProductionScheduleRequest
    try {
      payload = request()
    } catch (error) {
      options.onError?.(error instanceof Error ? error.message : t('productionSchedule.validation.dateRange'))
      return false
    }
    try {
      await options.confirm(
        t('productionSchedule.message.runConfirm', {
          startDate: payload.startDate,
          endDate: payload.endDate
        }),
        t('productionSchedule.message.runTitle')
      )
    } catch {
      return false
    }
    running.value = true
    try {
      result.value = await options.runSchedule(payload)
      options.onSuccess?.(t('productionSchedule.message.runSucceeded', {
        scheduled: result.value.scheduledCount,
        unscheduled: result.value.unscheduledCount
      }))
      return true
    } catch {
      options.onError?.(t('productionSchedule.message.runFailed'))
      return false
    } finally {
      running.value = false
    }
  }

  return {
    dateRange,
    execute,
    hasResult,
    loading,
    preview,
    result,
    running
  }
}
