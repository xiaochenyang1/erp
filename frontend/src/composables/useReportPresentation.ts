import { computed, type ComputedRef, type Ref } from 'vue'

import type {
  FinanceSettlementReportRow,
  InventoryBalanceReportRow,
  InventoryTransactionReportRow,
  OrderReportRow
} from '@/api/workflow'
import { formatLocalizedCurrency, formatLocalizedNumber } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string

export const reportKeys = [
  'purchase',
  'sales',
  'inventoryBalance',
  'inventoryTransaction',
  'financeSettlement'
] as const

export type ReportKey = (typeof reportKeys)[number]
export type ReportRecord =
  | OrderReportRow
  | InventoryBalanceReportRow
  | InventoryTransactionReportRow
  | FinanceSettlementReportRow

export interface ReportState {
  loading: boolean
  pageNo: number
  pageSize: number
  total: number
  records: ReportRecord[]
}

const reportTabMessageKeys: Record<ReportKey, string> = {
  purchase: 'financeReportPages.reports.tabs.purchase',
  sales: 'financeReportPages.reports.tabs.sales',
  inventoryBalance: 'financeReportPages.reports.tabs.inventoryBalance',
  inventoryTransaction: 'financeReportPages.reports.tabs.inventoryTransaction',
  financeSettlement: 'financeReportPages.reports.tabs.financeSettlement'
}

const reportStatusMessageKeys: Record<string, string> = {
  DRAFT: 'financeReportPages.reports.status.draft',
  SUBMITTED: 'financeReportPages.reports.status.submitted',
  APPROVED: 'financeReportPages.reports.status.approved',
  REJECTED: 'financeReportPages.reports.status.rejected',
  CONFIRMED: 'financeReportPages.reports.status.confirmed',
  CLOSED: 'financeReportPages.reports.status.closed',
  CANCELLED: 'financeReportPages.reports.status.cancelled',
  NOT_SUBMITTED: 'financeReportPages.reports.status.notSubmitted',
  PENDING: 'financeReportPages.reports.status.pending',
  NOT_RECEIVED: 'financeReportPages.reports.status.notReceived',
  PARTIALLY_RECEIVED: 'financeReportPages.reports.status.partiallyReceived',
  PARTIAL_RECEIVED: 'financeReportPages.reports.status.partiallyReceived',
  RECEIVED: 'financeReportPages.reports.status.received',
  NOT_DELIVERED: 'financeReportPages.reports.status.notDelivered',
  PARTIALLY_DELIVERED: 'financeReportPages.reports.status.partiallyDelivered',
  PARTIAL_DELIVERED: 'financeReportPages.reports.status.partiallyDelivered',
  DELIVERED: 'financeReportPages.reports.status.delivered',
  UNSETTLED: 'financeReportPages.reports.status.unsettled',
  PARTIALLY_SETTLED: 'financeReportPages.reports.status.partiallySettled',
  SETTLED: 'financeReportPages.reports.status.settled',
  OFFSET: 'financeReportPages.reports.status.offset'
}

const reportBusinessTypeMessageKeys: Record<string, string> = {
  PURCHASE_RECEIPT: 'financeReportPages.reports.businessTypeValue.purchaseReceipt',
  PURCHASE_RETURN: 'financeReportPages.reports.businessTypeValue.purchaseReturn',
  SALES_DELIVERY: 'financeReportPages.reports.businessTypeValue.salesDelivery',
  SALES_RETURN: 'financeReportPages.reports.businessTypeValue.salesReturn',
  INVENTORY_ADJUSTMENT: 'financeReportPages.reports.businessTypeValue.inventoryAdjustment',
  INVENTORY_TRANSFER: 'financeReportPages.reports.businessTypeValue.inventoryTransfer',
  PRODUCTION_ISSUE: 'financeReportPages.reports.businessTypeValue.productionIssue',
  PRODUCTION_COMPLETION: 'financeReportPages.reports.businessTypeValue.productionCompletion'
}

export const isReportKey = (value: unknown): value is ReportKey =>
  typeof value === 'string' && reportKeys.includes(value as ReportKey)

export const getReportTabLabel = (t: Translate, key: ReportKey) =>
  t(reportTabMessageKeys[key])

export const sumReportAmount = (records: ReportRecord[]) =>
  records.reduce((sum, row) => {
    if ('totalAmount' in row) return sum + Number(row.totalAmount || 0)
    if ('amountOnHand' in row) return sum + Number(row.amountOnHand || 0)
    if ('amount' in row) return sum + Number(row.amount || 0)
    if ('remainingAmount' in row) return sum + Number(row.remainingAmount || 0)
    return sum
  }, 0)

export const useReportPresentation = (
  t: Translate,
  activeKey: Ref<ReportKey>,
  activeState: ComputedRef<ReportState>
) => {
  const reportTabs = computed(() => reportKeys.map((key) => ({
    key,
    label: getReportTabLabel(t, key)
  })))

  const activeReport = computed(() =>
    reportTabs.value.find((item) => item.key === activeKey.value) || reportTabs.value[0]
  )
  const pageCount = computed(() =>
    Math.max(1, Math.ceil(activeState.value.total / activeState.value.pageSize))
  )
  const summaryAmount = computed(() => sumReportAmount(activeState.value.records))

  const formatMoney = (amount?: number) =>
    formatLocalizedCurrency(Number(amount || 0))

  const formatNumber = (value?: number) =>
    formatLocalizedNumber(Number(value || 0), {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    })

  const reportStatusLabel = (status?: string) => {
    if (!status) return '-'
    const key = reportStatusMessageKeys[status]
    return key ? t(key) : status
  }

  const reportDirectionLabel = (direction?: string) => {
    if (direction === 'IN') return t('financeReportPages.reports.directionValue.inbound')
    if (direction === 'OUT') return t('financeReportPages.reports.directionValue.outbound')
    return direction || '-'
  }

  const reportBusinessTypeLabel = (type?: string) => {
    if (!type) return '-'
    const key = reportBusinessTypeMessageKeys[type]
    return key ? t(key) : type
  }

  return {
    activeReport,
    formatMoney,
    formatNumber,
    isReportKey,
    pageCount,
    reportBusinessTypeLabel,
    reportDirectionLabel,
    reportStatusLabel,
    reportTabs,
    summaryAmount
  }
}
