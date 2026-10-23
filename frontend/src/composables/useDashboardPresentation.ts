import type { OperationsDashboardTodo } from '@/api/dashboard'
import { formatLocalizedCurrency, formatLocalizedDateTime, formatLocalizedNumber } from '@/utils/locale'
import type { DisplayPreferences } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'danger' | 'warning' | 'info' | 'success' | 'primary'
/** Labels, formatters and todo visual helpers for the operations dashboard. */
export const useDashboardPresentation = (
  t: Translate,
  getPreferences: () => DisplayPreferences = () => ({ locale: 'zh-CN', timeZone: 'Asia/Shanghai' })
) => {
  const formatNumber = (num?: number) =>
    formatLocalizedNumber(Number(num || 0), {}, getPreferences())

  const formatCurrency = (num?: number) =>
    formatLocalizedCurrency(Number(num || 0), {}, getPreferences())

  const formatDateTime = (value?: string) =>
    formatLocalizedDateTime(value, {}, getPreferences()) || '-'

  const formatPriority = (priority?: string) => {
    const labels: Record<string, string> = {
      HIGH: t('dashboard.priority.high'),
      MEDIUM: t('dashboard.priority.medium'),
      LOW: t('dashboard.priority.low')
    }
    return labels[priority || ''] || priority || t('dashboard.priority.low')
  }

  const getTodoIcon = (type?: string) => {
    const icons: Record<string, string> = {
      WORKFLOW: 'DocumentChecked',
      LOW_STOCK: 'Warning',
      RECEIVABLE_OVERDUE: 'Money',
      PAYABLE_OVERDUE: 'Tickets',
      FAILED_OPERATION: 'CircleClose'
    }
    return icons[type || ''] || 'Document'
  }

  const getTodoColor = (type?: string) => {
    const colors: Record<string, string> = {
      WORKFLOW: '#f56c6c',
      LOW_STOCK: '#e6a23c',
      RECEIVABLE_OVERDUE: '#626aef',
      PAYABLE_OVERDUE: '#909399',
      FAILED_OPERATION: '#f56c6c'
    }
    return colors[type || ''] || '#409eff'
  }

  const getTodoTagType = (priority?: string): TagType => {
    const types: Record<string, TagType> = {
      HIGH: 'danger',
      MEDIUM: 'warning',
      LOW: 'info'
    }
    return types[priority || ''] || 'info'
  }

  const quickActions = () => [
    { name: t('dashboard.quickPurchaseOrders'), icon: 'ShoppingCart', color: '#409eff', route: '/purchase/orders' },
    { name: t('dashboard.quickSalesOrders'), icon: 'Sell', color: '#67c23a', route: '/sales/orders' },
    { name: t('dashboard.quickInventoryStocks'), icon: 'Box', color: '#e6a23c', route: '/inventory/stocks' },
    { name: t('dashboard.quickFinanceVouchers'), icon: 'Tickets', color: '#f56c6c', route: '/finance/vouchers' },
    { name: t('dashboard.quickFinancePayments'), icon: 'Money', color: '#909399', route: '/finance/payments' },
    { name: t('dashboard.quickProductionOrders'), icon: 'List', color: '#606266', route: '/production/orders' }
  ]

  const formatCurrentDate = (date = new Date()) => {
    const { locale, timeZone } = getPreferences()
    return new Intl.DateTimeFormat(locale, {
      timeZone,
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      weekday: 'long'
    }).format(date)
  }

  const resolveTodoRoute = (todo: OperationsDashboardTodo) => todo.route || ''

  return {
    formatCurrency,
    formatCurrentDate,
    formatDateTime,
    formatNumber,
    formatPriority,
    getTodoColor,
    getTodoIcon,
    getTodoTagType,
    quickActions,
    resolveTodoRoute
  }
}
