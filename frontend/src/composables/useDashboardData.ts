import { computed, ref } from 'vue'

import type {
  OperationsDashboard,
  OperationsDashboardFailedOperation,
  OperationsDashboardLowStock,
  OperationsDashboardTodo
} from '@/api/dashboard'
import type { FinanceAgingSummary } from '@/api/finance'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export const createEmptyDashboard = (): OperationsDashboard => ({
  summary: {
    pendingApprovals: 0,
    overdueApprovals: 0,
    lowStockAlerts: 0,
    openReceivables: 0,
    openReceivableAmount: 0,
    openPayables: 0,
    openPayableAmount: 0,
    todayPurchaseOrders: 0,
    todaySalesAmount: 0
  },
  todos: [],
  lowStock: [],
  failedOperations: [],
  topSkus: [],
  generatedAt: ''
})

/**
 * Loads operations dashboard + aging summary.
 * Chart rendering stays on the page with ECharts lifecycle.
 */
export const useDashboardData = (
  t: Translate,
  options: {
    getDashboard: () => Promise<OperationsDashboard>
    getAgingSummary: () => Promise<FinanceAgingSummary>
    formatDateTime: (value?: string) => string
    onError?: Notify
    onLoaded?: () => void
  }
) => {
  const loading = ref(false)
  const agingLoading = ref(false)
  const dashboard = ref<OperationsDashboard>(createEmptyDashboard())
  const aging = ref<FinanceAgingSummary>()

  const summary = computed(() => dashboard.value.summary)
  const todos = computed<OperationsDashboardTodo[]>(() => dashboard.value.todos || [])
  const lowStock = computed<OperationsDashboardLowStock[]>(() => dashboard.value.lowStock || [])
  const failedOperations = computed<OperationsDashboardFailedOperation[]>(
    () => dashboard.value.failedOperations || []
  )
  const topSkus = computed(() => dashboard.value.topSkus || [])
  const generatedTimeText = computed(() =>
    dashboard.value.generatedAt
      ? t('dashboard.updatedAt', { time: options.formatDateTime(dashboard.value.generatedAt) })
      : t('dashboard.waitingData')
  )

  const loadDashboard = async () => {
    loading.value = true
    agingLoading.value = true
    try {
      const [dash, agingSummary] = await Promise.all([
        options.getDashboard(),
        options.getAgingSummary().catch(() => undefined)
      ])
      dashboard.value = dash
      aging.value = agingSummary
      options.onLoaded?.()
      return true
    } catch {
      dashboard.value = createEmptyDashboard()
      aging.value = undefined
      options.onError?.(t('dashboard.loadFailed'))
      options.onLoaded?.()
      return false
    } finally {
      loading.value = false
      agingLoading.value = false
    }
  }

  return {
    aging,
    agingLoading,
    dashboard,
    failedOperations,
    generatedTimeText,
    loadDashboard,
    loading,
    lowStock,
    summary,
    todos,
    topSkus
  }
}
