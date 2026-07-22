<template>
  <div v-loading="loading" class="dashboard-container">
    <div class="welcome-section">
      <div class="welcome-text">
        <h2>{{ t('dashboard.welcome', { name: userName }) }}</h2>
        <p class="welcome-subtitle">{{ currentTime }} | {{ t('dashboard.source') }}</p>
        <p class="onboarding-tip">{{ t('dashboard.onboarding') }}</p>
      </div>
      <div class="welcome-actions">
        <el-button type="primary" :icon="Plus" @click="handleQuickAction('/purchase/orders')">
          {{ t('dashboard.newPurchase') }}
        </el-button>
        <el-button type="success" :icon="Plus" @click="handleQuickAction('/sales/orders')">
          {{ t('dashboard.newSales') }}
        </el-button>
        <el-button type="warning" :icon="Search" @click="handleQuickAction('/inventory/stocks')">
          {{ t('dashboard.stockQuery') }}
        </el-button>
      </div>
    </div>

    <el-row :gutter="16" class="stat-cards">
      <el-col :xs="24" :sm="12" :lg="4">
        <div class="stat-card" style="border-left: 4px solid #409eff" @click="handleQuickAction('/purchase/orders')">
          <div class="stat-card-icon" style="background-color: #ecf5ff; color: #409eff">
            <el-icon :size="30"><ShoppingCart /></el-icon>
          </div>
          <div class="stat-card-content">
            <div class="stat-card-title">{{ t('dashboard.todayPurchaseOrders') }}</div>
            <div class="stat-card-value">{{ formatNumber(summary.todayPurchaseOrders) }}</div>
            <div class="stat-card-trend">
              <el-tag size="small">{{ t('dashboard.today') }}</el-tag>
            </div>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="12" :lg="4">
        <div class="stat-card" style="border-left: 4px solid #67c23a" @click="handleQuickAction('/sales/orders')">
          <div class="stat-card-icon" style="background-color: #f0f9eb; color: #67c23a">
            <el-icon :size="30"><Sell /></el-icon>
          </div>
          <div class="stat-card-content">
            <div class="stat-card-title">{{ t('dashboard.todaySalesAmount') }}</div>
            <div class="stat-card-value">{{ formatCurrency(summary.todaySalesAmount) }}</div>
            <div class="stat-card-trend">
              <el-tag size="small" type="success">{{ t('dashboard.today') }}</el-tag>
            </div>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="12" :lg="4">
        <div class="stat-card" style="border-left: 4px solid #e6a23c" @click="handleQuickAction('/inventory/alerts')">
          <div class="stat-card-icon" style="background-color: #fdf6ec; color: #e6a23c">
            <el-icon :size="30"><Box /></el-icon>
          </div>
          <div class="stat-card-content">
            <div class="stat-card-title">{{ t('dashboard.stockAlerts') }}</div>
            <div class="stat-card-value">{{ formatNumber(summary.lowStockAlerts) }}</div>
            <div class="stat-card-trend">
              <el-tag size="small" type="warning">{{ t('dashboard.attention') }}</el-tag>
            </div>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="12" :lg="4">
        <div class="stat-card" style="border-left: 4px solid #f56c6c" @click="handleQuickAction(summary.overdueApprovals > 0 ? '/workflow/tasks?status=PENDING&overdueOnly=true' : '/workflow/tasks')">
          <div class="stat-card-icon" style="background-color: #fef0f0; color: #f56c6c">
            <el-icon :size="30"><DocumentChecked /></el-icon>
          </div>
          <div class="stat-card-content">
            <div class="stat-card-title">{{ t('dashboard.pendingApprovals') }}</div>
            <div class="stat-card-value">{{ formatNumber(summary.pendingApprovals) }}</div>
            <div class="stat-card-trend">
              <el-tag v-if="summary.overdueApprovals > 0" size="small" type="danger">
                {{ t('dashboard.overdueApprovals', { count: formatNumber(summary.overdueApprovals) }) }}
              </el-tag>
              <el-tag v-else size="small" type="warning">{{ t('dashboard.pending') }}</el-tag>
            </div>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="12" :lg="4">
        <div class="stat-card" style="border-left: 4px solid #626aef" @click="handleQuickAction('/finance/receivables')">
          <div class="stat-card-icon" style="background-color: #f0f2ff; color: #626aef">
            <el-icon :size="30"><Money /></el-icon>
          </div>
          <div class="stat-card-content">
            <div class="stat-card-title">{{ t('dashboard.openReceivables') }}</div>
            <div class="stat-card-value">{{ formatNumber(summary.openReceivables) }}</div>
            <div class="stat-card-trend">
              <span class="metric-sub">{{ formatCurrency(summary.openReceivableAmount) }}</span>
            </div>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="12" :lg="4">
        <div class="stat-card" style="border-left: 4px solid #909399" @click="handleQuickAction('/finance/payables')">
          <div class="stat-card-icon" style="background-color: #f4f4f5; color: #606266">
            <el-icon :size="30"><Tickets /></el-icon>
          </div>
          <div class="stat-card-content">
            <div class="stat-card-title">{{ t('dashboard.openPayables') }}</div>
            <div class="stat-card-value">{{ formatNumber(summary.openPayables) }}</div>
            <div class="stat-card-trend">
              <span class="metric-sub">{{ formatCurrency(summary.openPayableAmount) }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 应收/应付账龄 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :xs="24" :lg="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>{{ t('dashboard.receivableAging') }}</span>
              <el-button text type="primary" @click="handleQuickAction('/finance/aging')">{{ t('dashboard.details') }}</el-button>
            </div>
          </template>
          <el-table v-loading="agingLoading" :data="aging?.receivableBuckets || []" size="small" border>
            <el-table-column prop="label" :label="t('dashboard.bucket')" min-width="100" />
            <el-table-column prop="count" :label="t('dashboard.count')" width="80" align="right" />
            <el-table-column prop="amount" :label="t('dashboard.amount')" min-width="120" align="right">
              <template #default="{ row }">{{ formatCurrency(row.amount) }}</template>
            </el-table-column>
          </el-table>
          <div class="aging-total">
            {{ t('dashboard.total') }} {{ formatCurrency(aging?.receivableTotal) }} · {{ t('dashboard.overdueTop') }} {{ aging?.overdueReceivables?.length || 0 }}
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>{{ t('dashboard.payableAging') }}</span>
              <el-button text type="primary" @click="handleQuickAction('/finance/aging')">{{ t('dashboard.details') }}</el-button>
            </div>
          </template>
          <el-table v-loading="agingLoading" :data="aging?.payableBuckets || []" size="small" border>
            <el-table-column prop="label" :label="t('dashboard.bucket')" min-width="100" />
            <el-table-column prop="count" :label="t('dashboard.count')" width="80" align="right" />
            <el-table-column prop="amount" :label="t('dashboard.amount')" min-width="120" align="right">
              <template #default="{ row }">{{ formatCurrency(row.amount) }}</template>
            </el-table-column>
          </el-table>
          <div class="aging-total">
            {{ t('dashboard.total') }} {{ formatCurrency(aging?.payableTotal) }} · {{ t('dashboard.overdueTop') }} {{ aging?.overduePayables?.length || 0 }}
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="24">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>{{ t('dashboard.topSkus') }}</span>
              <span class="generated-time">{{ t('dashboard.last30Days') }}</span>
            </div>
          </template>
          <el-table :data="dashboard.topSkus" size="small" border>
            <el-table-column type="index" :label="t('dashboard.rank')" width="70" align="center" />
            <el-table-column prop="productCode" :label="t('dashboard.productCode')" min-width="130" />
            <el-table-column prop="productName" :label="t('dashboard.productName')" min-width="180" />
            <el-table-column prop="quantity" :label="t('dashboard.salesQuantity')" min-width="120" align="right">
              <template #default="{ row }">{{ formatNumber(row.quantity) }} {{ row.unitName || '' }}</template>
            </el-table-column>
            <el-table-column prop="amount" :label="t('dashboard.salesAmount')" min-width="140" align="right">
              <template #default="{ row }">{{ formatCurrency(row.amount) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :xs="24" :lg="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>{{ t('dashboard.operationsOverview') }}</span>
              <el-button text @click="loadDashboard">{{ t('dashboard.refresh') }}</el-button>
            </div>
          </template>
          <div ref="operationsChartRef" style="height: 320px"></div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>{{ t('dashboard.fundExposure') }}</span>
              <span class="generated-time">{{ generatedTimeText }}</span>
            </div>
          </template>
          <div ref="settlementChartRef" style="height: 320px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :xs="24" :lg="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>{{ t('dashboard.myTodos') }}</span>
              <el-badge :value="todos.length" class="item">
                <el-button text @click="handleViewAllTodos">{{ t('dashboard.viewApprovals') }}</el-button>
              </el-badge>
            </div>
          </template>
          <el-empty v-if="todos.length === 0" :description="t('dashboard.noTodos')" :image-size="120" />
          <div v-else class="todo-list">
            <div
              v-for="todo in todos"
              :key="todo.id"
              class="todo-item"
              @click="handleTodoClick(todo)"
            >
              <div class="todo-icon">
                <el-icon :color="getTodoColor(todo.type)">
                  <component :is="getTodoIcon(todo.type)" />
                </el-icon>
              </div>
              <div class="todo-content">
                <div class="todo-title">{{ todo.title }}</div>
                <div class="todo-description">{{ todo.description || '-' }}</div>
                <div class="todo-meta">
                  <el-tag size="small" :type="getTodoTagType(todo.priority)">
                    {{ formatPriority(todo.priority) }}
                  </el-tag>
                  <span class="todo-time">{{ formatDateTime(todo.occurredAt) }}</span>
                </div>
              </div>
              <div class="todo-action">
                <el-button type="primary" link>{{ t('dashboard.process') }}</el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>{{ t('dashboard.quickActionsTitle') }}</span>
            </div>
          </template>
          <div class="quick-actions">
            <div
              v-for="action in quickActions"
              :key="action.name"
              class="quick-action-item"
              @click="handleQuickAction(action.route)"
            >
              <div class="quick-action-icon" :style="{ backgroundColor: action.color + '20', color: action.color }">
                <el-icon :size="28">
                  <component :is="action.icon" />
                </el-icon>
              </div>
              <div class="quick-action-text">{{ action.name }}</div>
            </div>
          </div>
        </el-card>

        <el-card style="margin-top: 20px">
          <template #header>
            <div class="card-header">
              <span>{{ t('dashboard.stockAlerts') }}</span>
              <el-button text type="warning" @click="handleViewAlerts">
                {{ t('dashboard.viewAllAlerts', { count: formatNumber(summary.lowStockAlerts) }) }}
              </el-button>
            </div>
          </template>
          <el-empty v-if="lowStock.length === 0" :description="t('dashboard.noInventoryAlerts')" :image-size="80" />
          <div v-else class="alert-list">
            <div v-for="alert in lowStock" :key="alert.ruleId" class="alert-item">
              <el-icon color="#e6a23c" :size="18"><Warning /></el-icon>
              <span class="alert-text">{{ t('dashboard.lowStockItem', { productId: alert.productId, warehouseId: alert.warehouseId }) }}</span>
              <el-tag size="small" type="warning">{{ t('dashboard.shortage', { count: formatNumber(alert.shortageQty) }) }}</el-tag>
            </div>
          </div>
        </el-card>

        <el-card style="margin-top: 20px">
          <template #header>
            <div class="card-header">
              <span>{{ t('dashboard.failedOperationsTitle') }}</span>
              <el-button text type="danger" @click="handleQuickAction('/system/logs')">{{ t('dashboard.viewLogs') }}</el-button>
            </div>
          </template>
          <el-empty v-if="failedOperations.length === 0" :description="t('dashboard.noFailedOperations')" :image-size="80" />
          <div v-else class="alert-list">
            <div v-for="log in failedOperations" :key="log.id" class="alert-item">
              <el-icon color="#f56c6c" :size="18"><CircleClose /></el-icon>
              <span class="alert-text">{{ log.module || '-' }} / {{ log.operation || '-' }} / {{ log.bizNo || '-' }}</span>
              <el-tag size="small" type="danger">{{ formatDateTime(log.operationTime) }}</el-tag>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '@/store/modules/app'
import { useUserStore } from '@/store/modules/user'
import { ElMessage } from 'element-plus'
import {
  ShoppingCart,
  Sell,
  Box,
  DocumentChecked,
  Warning,
  Plus,
  Search,
  Money,
  Tickets,
  CircleClose
} from '@element-plus/icons-vue'
import { BarChart, PieChart, type BarSeriesOption, type PieSeriesOption } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
  type GridComponentOption,
  type LegendComponentOption,
  type TooltipComponentOption
} from 'echarts/components'
import { init, use, type ComposeOption, type EChartsType } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import {
  getOperationsDashboard,
  type OperationsDashboard,
  type OperationsDashboardFailedOperation,
  type OperationsDashboardLowStock,
  type OperationsDashboardTodo
} from '@/api/dashboard'
import { getFinanceAgingSummary, type FinanceAgingSummary } from '@/api/finance'
import { formatLocalizedCurrency, formatLocalizedDateTime, formatLocalizedNumber } from '@/utils/locale'

use([BarChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

type DashboardChartOption = ComposeOption<
  | GridComponentOption
  | LegendComponentOption
  | TooltipComponentOption
  | BarSeriesOption
  | PieSeriesOption
>

const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const { t } = useI18n()

const emptyDashboard: OperationsDashboard = {
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
}

const userName = computed(() => userStore.userInfo?.realName || t('dashboard.user'))
const currentTime = ref('')
const loading = ref(false)
const agingLoading = ref(false)
const dashboard = ref<OperationsDashboard>(emptyDashboard)
const aging = ref<FinanceAgingSummary>()

const summary = computed(() => dashboard.value.summary)
const todos = computed<OperationsDashboardTodo[]>(() => dashboard.value.todos || [])
const lowStock = computed<OperationsDashboardLowStock[]>(() => dashboard.value.lowStock || [])
const failedOperations = computed<OperationsDashboardFailedOperation[]>(() => dashboard.value.failedOperations || [])
const displayPreferences = computed(() => ({
  locale: appStore.locale,
  timeZone: appStore.timeZone
}))
const generatedTimeText = computed(() => {
  return dashboard.value.generatedAt
    ? t('dashboard.updatedAt', { time: formatDateTime(dashboard.value.generatedAt) })
    : t('dashboard.waitingData')
})

const quickActions = computed(() => [
  { name: t('dashboard.quickPurchaseOrders'), icon: 'ShoppingCart', color: '#409eff', route: '/purchase/orders' },
  { name: t('dashboard.quickSalesOrders'), icon: 'Sell', color: '#67c23a', route: '/sales/orders' },
  { name: t('dashboard.quickInventoryStocks'), icon: 'Box', color: '#e6a23c', route: '/inventory/stocks' },
  { name: t('dashboard.quickFinanceVouchers'), icon: 'Tickets', color: '#f56c6c', route: '/finance/vouchers' },
  { name: t('dashboard.quickFinancePayments'), icon: 'Money', color: '#909399', route: '/finance/payments' },
  { name: t('dashboard.quickProductionOrders'), icon: 'List', color: '#606266', route: '/production/orders' }
])

const operationsChartRef = ref<HTMLDivElement>()
const settlementChartRef = ref<HTMLDivElement>()
let operationsChart: EChartsType | null = null
let settlementChart: EChartsType | null = null

const updateTime = () => {
  const now = new Date()
  const { locale, timeZone } = displayPreferences.value
  currentTime.value = new Intl.DateTimeFormat(locale, {
    timeZone,
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long'
  }).format(now)
}

watch([() => appStore.locale, () => appStore.timeZone], () => {
  updateTime()
  updateCharts()
})

const loadDashboard = async () => {
  loading.value = true
  agingLoading.value = true
  try {
    const [dash, agingSummary] = await Promise.all([
      getOperationsDashboard(),
      getFinanceAgingSummary().catch(() => undefined)
    ])
    dashboard.value = dash
    aging.value = agingSummary
    updateCharts()
  } catch (error) {
    dashboard.value = emptyDashboard
    ElMessage.error(t('dashboard.loadFailed'))
    updateCharts()
  } finally {
    loading.value = false
    agingLoading.value = false
  }
}

const initCharts = () => {
  if (operationsChartRef.value) {
    operationsChart = init(operationsChartRef.value)
  }
  if (settlementChartRef.value) {
    settlementChart = init(settlementChartRef.value)
  }
  updateCharts()
}

const updateCharts = () => {
  updateOperationsChart()
  updateSettlementChart()
}

const updateOperationsChart = () => {
  if (!operationsChart) return
  const option: DashboardChartOption = {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: [
        t('dashboard.pendingApprovals'),
        t('dashboard.stockAlerts'),
        t('dashboard.openReceivables'),
        t('dashboard.openPayables'),
        t('dashboard.failedOperationsShort')
      ]
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: t('dashboard.operationsCount'),
        type: 'bar',
        barWidth: 32,
        data: [
          summary.value.pendingApprovals,
          summary.value.lowStockAlerts,
          summary.value.openReceivables,
          summary.value.openPayables,
          failedOperations.value.length
        ],
        itemStyle: {
          color: '#409eff',
          borderRadius: [4, 4, 0, 0]
        }
      }
    ]
  }
  operationsChart.setOption(option)
}

const updateSettlementChart = () => {
  if (!settlementChart) return
  const option: DashboardChartOption = {
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => `${params.name}: ${formatCurrency(Number(params.value || 0))}`
    },
    legend: {
      orient: 'vertical',
      right: '8%',
      top: 'center'
    },
    series: [
      {
        name: t('dashboard.amount'),
        type: 'pie',
        radius: ['42%', '70%'],
        center: ['40%', '50%'],
        itemStyle: {
          borderRadius: 6,
          borderColor: '#fff',
          borderWidth: 2
        },
        data: [
          { value: summary.value.openReceivableAmount, name: t('dashboard.openReceivables'), itemStyle: { color: '#626aef' } },
          { value: summary.value.openPayableAmount, name: t('dashboard.openPayables'), itemStyle: { color: '#909399' } },
          { value: summary.value.todaySalesAmount, name: t('dashboard.todaySalesAmount'), itemStyle: { color: '#67c23a' } }
        ]
      }
    ]
  }
  settlementChart.setOption(option)
}

const formatNumber = (num?: number) => {
  return formatLocalizedNumber(Number(num || 0), {}, displayPreferences.value)
}

const formatCurrency = (num?: number) => {
  return formatLocalizedCurrency(Number(num || 0), {}, displayPreferences.value)
}

const formatDateTime = (value?: string) => {
  return formatLocalizedDateTime(value, {}, displayPreferences.value) || '-'
}

const formatPriority = (priority: string) => {
  const labels: Record<string, string> = {
    HIGH: t('dashboard.priority.high'),
    MEDIUM: t('dashboard.priority.medium'),
    LOW: t('dashboard.priority.low')
  }
  return labels[priority] || priority || t('dashboard.priority.low')
}

const getTodoIcon = (type: string) => {
  const icons: Record<string, string> = {
    WORKFLOW: 'DocumentChecked',
    LOW_STOCK: 'Warning',
    RECEIVABLE_OVERDUE: 'Money',
    PAYABLE_OVERDUE: 'Tickets',
    FAILED_OPERATION: 'CircleClose'
  }
  return icons[type] || 'Document'
}

const getTodoColor = (type: string) => {
  const colors: Record<string, string> = {
    WORKFLOW: '#f56c6c',
    LOW_STOCK: '#e6a23c',
    RECEIVABLE_OVERDUE: '#626aef',
    PAYABLE_OVERDUE: '#909399',
    FAILED_OPERATION: '#f56c6c'
  }
  return colors[type] || '#409eff'
}

const getTodoTagType = (priority: string) => {
  const types: Record<string, 'danger' | 'warning' | 'info' | 'success'> = {
    HIGH: 'danger',
    MEDIUM: 'warning',
    LOW: 'info'
  }
  return types[priority] || 'info'
}

const handleQuickAction = (route: string) => {
  router.push(route)
}

const handleTodoClick = (todo: OperationsDashboardTodo) => {
  if (todo.route) {
    router.push(todo.route)
  }
}

const handleViewAllTodos = () => {
  router.push('/workflow/tasks')
}

const handleViewAlerts = () => {
  router.push('/inventory/alerts')
}

const handleResize = () => {
  operationsChart?.resize()
  settlementChart?.resize()
}

onMounted(async () => {
  updateTime()
  initCharts()
  window.addEventListener('resize', handleResize)
  await loadDashboard()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  operationsChart?.dispose()
  settlementChart?.dispose()
})
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
  background-color: #f5f7fa;
}

.welcome-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px;
  margin-bottom: 20px;
  background: #1f2937;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(31, 41, 55, 0.18);
  color: white;
}

.welcome-text h2 {
  margin: 0 0 8px 0;
  font-size: 24px;
  font-weight: 600;
}

.onboarding-tip {
  margin: 6px 0 0;
  color: #909399;
  font-size: 12px;
}
.welcome-subtitle {
  margin: 0;
  opacity: 0.82;
  font-size: 14px;
}

.welcome-actions {
  display: flex;
  gap: 12px;
}

.stat-cards {
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 122px;
  padding: 20px;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.08);
  transition: all 0.2s;
  cursor: pointer;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px 0 rgba(0, 0, 0, 0.1);
}

.stat-card-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 8px;
  flex-shrink: 0;
}

.stat-card-content {
  flex: 1;
  min-width: 0;
}

.stat-card-title {
  margin-bottom: 8px;
  font-size: 13px;
  color: #909399;
}

.stat-card-value {
  margin-bottom: 6px;
  font-size: 24px;
  font-weight: 650;
  color: #303133;
  word-break: break-all;
}

.stat-card-trend {
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 22px;
  font-size: 12px;
}

.metric-sub {
  color: #606266;
}

.aging-total {
  margin-top: 10px;
  color: #6b7280;
  font-size: 12px;
  text-align: right;
}
.generated-time {
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.todo-list {
  max-height: 430px;
  overflow-y: auto;
}

.todo-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 4px;
  border-bottom: 1px solid #ebeef5;
  cursor: pointer;
  transition: all 0.2s;
}

.todo-item:last-child {
  border-bottom: none;
}

.todo-item:hover {
  background-color: #f5f7fa;
}

.todo-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background-color: #f5f7fa;
  flex-shrink: 0;
}

.todo-content {
  flex: 1;
  min-width: 0;
}

.todo-title {
  margin-bottom: 4px;
  font-size: 14px;
  color: #303133;
}

.todo-description {
  margin-bottom: 6px;
  font-size: 12px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.todo-time {
  font-size: 12px;
  color: #909399;
}

.todo-action {
  flex-shrink: 0;
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.quick-action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  min-height: 116px;
  padding: 18px;
  background-color: #f5f7fa;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.quick-action-item:hover {
  background-color: #ebeef5;
  transform: translateY(-2px);
}

.quick-action-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 54px;
  height: 54px;
  border-radius: 8px;
}

.quick-action-text {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.alert-list {
  max-height: 240px;
  overflow-y: auto;
}

.alert-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  border-bottom: 1px solid #ebeef5;
}

.alert-item:last-child {
  border-bottom: none;
}

.alert-text {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .welcome-section {
    flex-direction: column;
    gap: 16px;
    text-align: center;
  }

  .welcome-actions {
    width: 100%;
    justify-content: center;
    flex-wrap: wrap;
  }

  .quick-actions {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 480px) {
  .dashboard-container {
    padding: 12px;
  }

  .welcome-section {
    padding: 16px;
  }

  .stat-card {
    padding: 16px;
  }

  .stat-card-value {
    font-size: 22px;
  }

  .quick-actions {
    grid-template-columns: 1fr;
  }
}
</style>
