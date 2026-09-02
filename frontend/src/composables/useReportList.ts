import { computed, reactive, ref } from 'vue'

import type {
  FinanceSettlementReportRow,
  InventoryBalanceReportRow,
  InventoryTransactionReportRow,
  InventoryValuationReportRow,
  OrderReportRow,
  ProductionCostReportRow,
  ReportQuery
} from '@/api/workflow'
import type { PageResponse } from '@/types/common'
import {
  getReportTabLabel,
  reportKeys,
  type ReportKey,
  type ReportRecord,
  type ReportState
} from './useReportPresentation'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type ExportReport = (params: ReportQuery) => Promise<Blob>

export const createEmptyReportState = (): ReportState => ({
  loading: false,
  pageNo: 1,
  pageSize: 10,
  total: 0,
  records: []
})

export const buildReportParams = (
  key: ReportKey,
  state: Pick<ReportState, 'pageNo' | 'pageSize'>,
  keyword: string,
  dateRange: [string, string] | null
): ReportQuery => {
  const normalizedKeyword = keyword.trim()
  const params: ReportQuery = {
    pageNo: state.pageNo,
    pageSize: state.pageSize
  }
  if (key === 'purchase' || key === 'sales') {
    params.keyword = normalizedKeyword || undefined
    params.orderDateFrom = dateRange?.[0]
    params.orderDateTo = dateRange?.[1]
  } else if (key === 'inventoryTransaction') {
    params.bizNo = normalizedKeyword || undefined
    params.occurredTimeFrom = dateRange?.[0] ? `${dateRange[0]}T00:00:00` : undefined
    params.occurredTimeTo = dateRange?.[1] ? `${dateRange[1]}T23:59:59` : undefined
  } else if (key === 'financeSettlement') {
    params.bizDateFrom = dateRange?.[0]
    params.bizDateTo = dateRange?.[1]
  } else if (key === 'inventoryValuation') {
    params.keyword = normalizedKeyword || undefined
    params.periodStart = dateRange?.[0]
    params.asOfDate = dateRange?.[1]
  } else if (key === 'productionCost') {
    params.keyword = normalizedKeyword || undefined
    params.plannedStartDateFrom = dateRange?.[0]
    params.plannedStartDateTo = dateRange?.[1]
  }
  return params
}

export const useReportList = (
  t: Translate,
  options: {
    getPurchaseOrderReport: (params: ReportQuery) => Promise<PageResponse<OrderReportRow>>
    getSalesOrderReport: (params: ReportQuery) => Promise<PageResponse<OrderReportRow>>
    getInventoryBalanceReport: (params: ReportQuery) => Promise<PageResponse<InventoryBalanceReportRow>>
    getInventoryTransactionReport: (params: ReportQuery) => Promise<PageResponse<InventoryTransactionReportRow>>
    getFinanceSettlementReport: (params: ReportQuery) => Promise<PageResponse<FinanceSettlementReportRow>>
    getInventoryValuationReport: (params: ReportQuery) => Promise<PageResponse<InventoryValuationReportRow>>
    getProductionCostReport: (params: ReportQuery) => Promise<PageResponse<ProductionCostReportRow>>
    exportPurchaseOrderReport: ExportReport
    exportSalesOrderReport: ExportReport
    exportInventoryBalanceReport: ExportReport
    exportInventoryTransactionReport: ExportReport
    exportFinanceSettlementReport: ExportReport
    exportInventoryValuationReport: ExportReport
    exportProductionCostReport: ExportReport
    downloadBlob: (blob: Blob, fileName: string) => void
    now?: () => number
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const activeKey = ref<ReportKey>('purchase')
  const dateRange = ref<[string, string] | null>(null)
  const queryForm = reactive({ keyword: '' })
  const reportStates = reactive<Record<ReportKey, ReportState>>(
    Object.fromEntries(reportKeys.map((key) => [key, createEmptyReportState()])) as Record<ReportKey, ReportState>
  )
  const activeState = computed(() => reportStates[activeKey.value])

  const loaders: Record<ReportKey, (params: ReportQuery) => Promise<PageResponse<ReportRecord>>> = {
    purchase: options.getPurchaseOrderReport,
    sales: options.getSalesOrderReport,
    inventoryBalance: options.getInventoryBalanceReport,
    inventoryTransaction: options.getInventoryTransactionReport,
    financeSettlement: options.getFinanceSettlementReport,
    inventoryValuation: options.getInventoryValuationReport,
    productionCost: options.getProductionCostReport
  }
  const exporters: Record<ReportKey, ExportReport> = {
    purchase: options.exportPurchaseOrderReport,
    sales: options.exportSalesOrderReport,
    inventoryBalance: options.exportInventoryBalanceReport,
    inventoryTransaction: options.exportInventoryTransactionReport,
    financeSettlement: options.exportFinanceSettlementReport,
    inventoryValuation: options.exportInventoryValuationReport,
    productionCost: options.exportProductionCostReport
  }

  const buildParams = (key = activeKey.value, state = reportStates[key]) =>
    buildReportParams(key, state, queryForm.keyword, dateRange.value)

  const loadActiveReport = async () => {
    const key = activeKey.value
    const state = reportStates[key]
    state.loading = true
    try {
      const page = await loaders[key](buildParams(key, state))
      state.records = page.records || []
      state.total = page.total || 0
      state.pageNo = page.pageNo || state.pageNo
      state.pageSize = page.pageSize || state.pageSize
      return true
    } catch {
      options.onError?.(t('financeReportPages.reports.message.loadFailed'))
      return false
    } finally {
      state.loading = false
    }
  }

  const handleQuery = () => {
    activeState.value.pageNo = 1
    return loadActiveReport()
  }

  const handleReset = () => {
    dateRange.value = null
    queryForm.keyword = ''
    return handleQuery()
  }

  const handleTabChange = () => {
    if (activeState.value.records.length === 0) {
      return loadActiveReport()
    }
    return Promise.resolve(true)
  }

  const handlePageChange = (page: number) => {
    activeState.value.pageNo = page
    return loadActiveReport()
  }

  const handleSizeChange = (size: number) => {
    activeState.value.pageSize = size
    activeState.value.pageNo = 1
    return loadActiveReport()
  }

  const handleExport = async () => {
    const key = activeKey.value
    try {
      const blob = await exporters[key](buildParams(key, reportStates[key]))
      options.downloadBlob(blob, t('financeReportPages.reports.fileName', {
        report: getReportTabLabel(t, key),
        timestamp: options.now?.() ?? Date.now()
      }))
      options.onSuccess?.(t('financeReportPages.reports.message.exported'))
      return true
    } catch {
      options.onError?.(t('financeReportPages.reports.message.exportFailed'))
      return false
    }
  }

  return {
    activeKey,
    activeState,
    buildParams,
    dateRange,
    handleExport,
    handlePageChange,
    handleQuery,
    handleReset,
    handleSizeChange,
    handleTabChange,
    loadActiveReport,
    queryForm,
    reportStates
  }
}
