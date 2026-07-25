<template>
  <div class="reports-container">
    <el-card class="filter-card" shadow="never">
      <el-form :model="queryForm" inline>
        <el-form-item :label="$t('financeReportPages.common.dateRange')">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            :range-separator="$t('financeReportPages.common.rangeSeparator')"
            :start-placeholder="$t('financeReportPages.common.startDate')"
            :end-placeholder="$t('financeReportPages.common.endDate')"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.common.keyword')">
          <el-input v-model="queryForm.keyword" :placeholder="$t('financeReportPages.reports.keywordPlaceholder')" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ $t('financeReportPages.common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ $t('financeReportPages.common.reset') }}</el-button>
          <el-button v-permission="'report:view'" :icon="Download" @click="handleExport">{{ $t('financeReportPages.reports.export') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="summary-grid">
      <el-card shadow="never" class="summary-item">
        <span>{{ $t('financeReportPages.reports.currentReport') }}</span>
        <strong>{{ activeReport.label }}</strong>
      </el-card>
      <el-card shadow="never" class="summary-item">
        <span>{{ $t('financeReportPages.reports.recordCount') }}</span>
        <strong>{{ activeState.total }}</strong>
      </el-card>
      <el-card shadow="never" class="summary-item">
        <span>{{ $t('financeReportPages.reports.amountTotal') }}</span>
        <strong>{{ formatMoney(summaryAmount) }}</strong>
      </el-card>
      <el-card shadow="never" class="summary-item">
        <span>{{ $t('financeReportPages.reports.currentPage') }}</span>
        <strong>{{ activeState.pageNo }} / {{ pageCount }}</strong>
      </el-card>
    </div>

    <el-card class="table-card" shadow="never">
      <el-tabs v-model="activeKey" @tab-change="handleTabChange">
        <el-tab-pane
          v-for="item in reportTabs"
          :key="item.key"
          :label="item.label"
          :name="item.key"
        />
      </el-tabs>

      <el-table v-if="activeKey === 'purchase' || activeKey === 'sales'" v-loading="activeState.loading" :data="activeState.records" border stripe>
        <el-table-column prop="bizNo" :label="$t('financeReportPages.reports.documentNo')" min-width="170" />
        <el-table-column prop="partnerId" :label="activeKey === 'purchase' ? $t('financeReportPages.reports.supplierId') : $t('financeReportPages.reports.customerId')" width="120" />
        <el-table-column prop="bizDate" :label="$t('financeReportPages.common.bizDate')" width="120" />
        <el-table-column prop="status" :label="$t('financeReportPages.reports.documentStatus')" width="120">
          <template #default="{ row }">
            <el-tag>{{ reportStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="approvalStatus" :label="$t('financeReportPages.reports.approvalStatus')" width="120">
          <template #default="{ row }">{{ reportStatusLabel(row.approvalStatus) }}</template>
        </el-table-column>
        <el-table-column prop="fulfillmentStatus" :label="activeKey === 'purchase' ? $t('financeReportPages.reports.receiptStatus') : $t('financeReportPages.reports.deliveryStatus')" width="120">
          <template #default="{ row }">{{ reportStatusLabel(row.fulfillmentStatus) }}</template>
        </el-table-column>
        <el-table-column prop="totalQuantity" :label="$t('financeReportPages.reports.quantity')" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.totalQuantity) }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" :label="$t('financeReportPages.common.amount')" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="totalTaxAmount" :label="$t('financeReportPages.reports.taxAmount')" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalTaxAmount) }}</template>
        </el-table-column>
      </el-table>

      <el-table v-else-if="activeKey === 'inventoryBalance'" v-loading="activeState.loading" :data="activeState.records" border stripe>
        <el-table-column prop="warehouseId" :label="$t('financeReportPages.reports.warehouseId')" width="120" />
        <el-table-column prop="productId" :label="$t('financeReportPages.reports.productId')" width="120" />
        <el-table-column prop="qtyOnHand" :label="$t('financeReportPages.reports.quantityOnHand')" width="130" align="right">
          <template #default="{ row }">{{ formatNumber(row.qtyOnHand) }}</template>
        </el-table-column>
        <el-table-column prop="qtyReserved" :label="$t('financeReportPages.reports.quantityReserved')" width="130" align="right">
          <template #default="{ row }">{{ formatNumber(row.qtyReserved) }}</template>
        </el-table-column>
        <el-table-column prop="qtyAvailable" :label="$t('financeReportPages.reports.quantityAvailable')" width="130" align="right">
          <template #default="{ row }">{{ formatNumber(row.qtyAvailable) }}</template>
        </el-table-column>
        <el-table-column prop="amountOnHand" :label="$t('financeReportPages.reports.inventoryAmount')" width="150" align="right">
          <template #default="{ row }">{{ formatMoney(row.amountOnHand) }}</template>
        </el-table-column>
        <el-table-column prop="updatedTime" :label="$t('financeReportPages.reports.updatedTime')" min-width="180" />
      </el-table>

      <el-table v-else-if="activeKey === 'inventoryTransaction'" v-loading="activeState.loading" :data="activeState.records" border stripe>
        <el-table-column prop="bizNo" :label="$t('financeReportPages.reports.businessNo')" min-width="160" />
        <el-table-column prop="bizType" :label="$t('financeReportPages.reports.businessType')" width="140">
          <template #default="{ row }">{{ reportBusinessTypeLabel(row.bizType) }}</template>
        </el-table-column>
        <el-table-column prop="direction" :label="$t('financeReportPages.reports.direction')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'IN' ? 'success' : 'warning'">{{ reportDirectionLabel(row.direction) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="warehouseId" :label="$t('financeReportPages.reports.warehouseId')" width="110" />
        <el-table-column prop="productId" :label="$t('financeReportPages.reports.productId')" width="110" />
        <el-table-column prop="qty" :label="$t('financeReportPages.reports.quantity')" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.qty) }}</template>
        </el-table-column>
        <el-table-column prop="unitCost" :label="$t('financeReportPages.reports.unitCost')" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.unitCost) }}</template>
        </el-table-column>
        <el-table-column prop="amount" :label="$t('financeReportPages.common.amount')" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="occurredTime" :label="$t('financeReportPages.reports.occurredTime')" min-width="180" />
      </el-table>

      <el-table v-else v-loading="activeState.loading" :data="activeState.records" border stripe>
        <el-table-column prop="direction" :label="$t('financeReportPages.reports.direction')" width="110">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'RECEIVABLE' ? 'success' : 'warning'">
              {{ row.direction === 'RECEIVABLE' ? $t('financeReportPages.reports.receivable') : $t('financeReportPages.reports.payable') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bizNo" :label="$t('financeReportPages.reports.settlementNo')" min-width="160" />
        <el-table-column prop="partnerId" :label="$t('financeReportPages.reports.partnerId')" width="120" />
        <el-table-column prop="bizDate" :label="$t('financeReportPages.common.bizDate')" width="120" />
        <el-table-column prop="sourceType" :label="$t('financeReportPages.reports.sourceType')" width="130">
          <template #default="{ row }">{{ reportBusinessTypeLabel(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column prop="sourceNo" :label="$t('financeReportPages.reports.sourceNo')" min-width="160" />
        <el-table-column prop="originalAmount" :label="$t('financeReportPages.reports.originalAmount')" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.originalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="settledAmount" :label="$t('financeReportPages.reports.settledAmount')" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.settledAmount) }}</template>
        </el-table-column>
        <el-table-column prop="remainingAmount" :label="$t('financeReportPages.reports.remainingAmount')" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.remainingAmount) }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('financeReportPages.common.status')" width="120">
          <template #default="{ row }">{{ reportStatusLabel(row.status) }}</template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="activeState.pageNo"
        v-model:page-size="activeState.pageSize"
        :total="activeState.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadActiveReport"
        @current-change="loadActiveReport"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Download, Refresh, Search } from '@element-plus/icons-vue'
import { downloadBlob } from '@/utils/download'
import { formatLocalizedCurrency, formatLocalizedNumber } from '@/utils/locale'
import {
  exportFinanceSettlementReport,
  exportInventoryBalanceReport,
  exportInventoryTransactionReport,
  exportPurchaseOrderReport,
  exportSalesOrderReport,
  getFinanceSettlementReport,
  getInventoryBalanceReport,
  getInventoryTransactionReport,
  getPurchaseOrderReport,
  getSalesOrderReport,
  type FinanceSettlementReportRow,
  type InventoryBalanceReportRow,
  type InventoryTransactionReportRow,
  type OrderReportRow,
  type ReportQuery
} from '@/api/workflow'

type ReportKey = 'purchase' | 'sales' | 'inventoryBalance' | 'inventoryTransaction' | 'financeSettlement'
type ReportRecord = OrderReportRow | InventoryBalanceReportRow | InventoryTransactionReportRow | FinanceSettlementReportRow

interface ReportState {
  loading: boolean
  pageNo: number
  pageSize: number
  total: number
  records: ReportRecord[]
}

const { t } = useI18n()

const reportTabs = computed<Array<{ key: ReportKey; label: string }>>(() => [
  { key: 'purchase', label: t('financeReportPages.reports.tabs.purchase') },
  { key: 'sales', label: t('financeReportPages.reports.tabs.sales') },
  { key: 'inventoryBalance', label: t('financeReportPages.reports.tabs.inventoryBalance') },
  { key: 'inventoryTransaction', label: t('financeReportPages.reports.tabs.inventoryTransaction') },
  { key: 'financeSettlement', label: t('financeReportPages.reports.tabs.financeSettlement') }
])

const route = useRoute()
const activeKey = ref<ReportKey>('purchase')
const dateRange = ref<[string, string] | null>(null)
const queryForm = reactive({ keyword: '' })
const reportStates = reactive<Record<ReportKey, ReportState>>({
  purchase: emptyState(),
  sales: emptyState(),
  inventoryBalance: emptyState(),
  inventoryTransaction: emptyState(),
  financeSettlement: emptyState()
})

const activeState = computed(() => reportStates[activeKey.value])
const activeReport = computed(() => reportTabs.value.find((item) => item.key === activeKey.value) || reportTabs.value[0])
const pageCount = computed(() => Math.max(1, Math.ceil(activeState.value.total / activeState.value.pageSize)))
const summaryAmount = computed(() => {
  return activeState.value.records.reduce((sum, row) => {
    if ('totalAmount' in row) return sum + Number(row.totalAmount || 0)
    if ('amountOnHand' in row) return sum + Number(row.amountOnHand || 0)
    if ('amount' in row) return sum + Number(row.amount || 0)
    if ('remainingAmount' in row) return sum + Number(row.remainingAmount || 0)
    return sum
  }, 0)
})

function emptyState(): ReportState {
  return {
    loading: false,
    pageNo: 1,
    pageSize: 10,
    total: 0,
    records: []
  }
}

const handleQuery = () => {
  activeState.value.pageNo = 1
  loadActiveReport()
}

const handleReset = () => {
  dateRange.value = null
  queryForm.keyword = ''
  handleQuery()
}

const handleTabChange = () => {
  if (activeState.value.records.length === 0) {
    loadActiveReport()
  }
}

const loadActiveReport = async () => {
  const state = activeState.value
  state.loading = true
  try {
    const params = buildParams(activeKey.value, state)
    const page = await fetchReport(activeKey.value, params)
    state.records = page.records
    state.total = page.total
    state.pageNo = page.pageNo
    state.pageSize = page.pageSize
  } finally {
    state.loading = false
  }
}

const fetchReport = (key: ReportKey, params: ReportQuery) => {
  const loaders = {
    purchase: getPurchaseOrderReport,
    sales: getSalesOrderReport,
    inventoryBalance: getInventoryBalanceReport,
    inventoryTransaction: getInventoryTransactionReport,
    financeSettlement: getFinanceSettlementReport
  }
  return loaders[key](params)
}

const buildParams = (key: ReportKey, state: ReportState): ReportQuery => {
  const params: ReportQuery = {
    pageNo: state.pageNo,
    pageSize: state.pageSize
  }
  if (key === 'purchase' || key === 'sales') {
    params.keyword = queryForm.keyword || undefined
    params.orderDateFrom = dateRange.value?.[0]
    params.orderDateTo = dateRange.value?.[1]
  } else if (key === 'inventoryTransaction') {
    params.bizNo = queryForm.keyword || undefined
    params.occurredTimeFrom = dateRange.value?.[0] ? `${dateRange.value[0]}T00:00:00` : undefined
    params.occurredTimeTo = dateRange.value?.[1] ? `${dateRange.value[1]}T23:59:59` : undefined
  } else if (key === 'financeSettlement') {
    params.bizDateFrom = dateRange.value?.[0]
    params.bizDateTo = dateRange.value?.[1]
  }
  return params
}

const handleExport = async () => {
  try {
    const params = buildParams(activeKey.value, activeState.value)
    const exporters = {
      purchase: exportPurchaseOrderReport,
      sales: exportSalesOrderReport,
      inventoryBalance: exportInventoryBalanceReport,
      inventoryTransaction: exportInventoryTransactionReport,
      financeSettlement: exportFinanceSettlementReport
    }
    const blob = await exporters[activeKey.value](params)
    downloadBlob(blob, t('financeReportPages.reports.fileName', {
      report: activeReport.value.label,
      timestamp: Date.now()
    }))
    ElMessage.success(t('financeReportPages.reports.message.exported'))
  } catch (error) {
    ElMessage.error(t('financeReportPages.reports.message.exportFailed'))
  }
}

const formatMoney = (amount?: number) => {
  return formatLocalizedCurrency(Number(amount || 0))
}

const formatNumber = (value?: number) => {
  return formatLocalizedNumber(Number(value || 0), {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

const reportStatusLabel = (status?: string) => {
  const labels: Record<string, string> = {
    DRAFT: t('financeReportPages.reports.status.draft'),
    SUBMITTED: t('financeReportPages.reports.status.submitted'),
    APPROVED: t('financeReportPages.reports.status.approved'),
    REJECTED: t('financeReportPages.reports.status.rejected'),
    CONFIRMED: t('financeReportPages.reports.status.confirmed'),
    CLOSED: t('financeReportPages.reports.status.closed'),
    CANCELLED: t('financeReportPages.reports.status.cancelled'),
    NOT_SUBMITTED: t('financeReportPages.reports.status.notSubmitted'),
    PENDING: t('financeReportPages.reports.status.pending'),
    NOT_RECEIVED: t('financeReportPages.reports.status.notReceived'),
    PARTIALLY_RECEIVED: t('financeReportPages.reports.status.partiallyReceived'),
    PARTIAL_RECEIVED: t('financeReportPages.reports.status.partiallyReceived'),
    RECEIVED: t('financeReportPages.reports.status.received'),
    NOT_DELIVERED: t('financeReportPages.reports.status.notDelivered'),
    PARTIALLY_DELIVERED: t('financeReportPages.reports.status.partiallyDelivered'),
    PARTIAL_DELIVERED: t('financeReportPages.reports.status.partiallyDelivered'),
    DELIVERED: t('financeReportPages.reports.status.delivered'),
    UNSETTLED: t('financeReportPages.reports.status.unsettled'),
    PARTIALLY_SETTLED: t('financeReportPages.reports.status.partiallySettled'),
    SETTLED: t('financeReportPages.reports.status.settled'),
    OFFSET: t('financeReportPages.reports.status.offset')
  }
  return status ? labels[status] || status : '-'
}

const reportDirectionLabel = (direction?: string) => {
  if (direction === 'IN') return t('financeReportPages.reports.directionValue.inbound')
  if (direction === 'OUT') return t('financeReportPages.reports.directionValue.outbound')
  return direction || '-'
}

const reportBusinessTypeLabel = (type?: string) => {
  const labels: Record<string, string> = {
    PURCHASE_RECEIPT: t('financeReportPages.reports.businessTypeValue.purchaseReceipt'),
    PURCHASE_RETURN: t('financeReportPages.reports.businessTypeValue.purchaseReturn'),
    SALES_DELIVERY: t('financeReportPages.reports.businessTypeValue.salesDelivery'),
    SALES_RETURN: t('financeReportPages.reports.businessTypeValue.salesReturn'),
    INVENTORY_ADJUSTMENT: t('financeReportPages.reports.businessTypeValue.inventoryAdjustment'),
    INVENTORY_TRANSFER: t('financeReportPages.reports.businessTypeValue.inventoryTransfer'),
    PRODUCTION_ISSUE: t('financeReportPages.reports.businessTypeValue.productionIssue'),
    PRODUCTION_COMPLETION: t('financeReportPages.reports.businessTypeValue.productionCompletion')
  }
  return type ? labels[type] || type : '-'
}

const isReportKey = (value: unknown): value is ReportKey => {
  return typeof value === 'string' && reportTabs.value.some((item) => item.key === value)
}

onMounted(() => {
  if (isReportKey(route.query.tab)) {
    activeKey.value = route.query.tab
  }
  const routeKeyword = route.query.keyword || route.query.bizNo
  if (typeof routeKeyword === 'string') {
    queryForm.keyword = routeKeyword
  }
  loadActiveReport()
})
</script>

<style scoped lang="scss">
.reports-container {
  padding: 20px;

  .filter-card,
  .table-card {
    margin-bottom: 20px;
  }

  .summary-grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
    margin-bottom: 20px;
  }

  .summary-item {
    :deep(.el-card__body) {
      display: flex;
      flex-direction: column;
      gap: 8px;
      min-height: 76px;
    }

    span {
      color: #606266;
      font-size: 13px;
    }

    strong {
      color: #303133;
      font-size: 20px;
      font-weight: 700;
    }
  }

  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }
}

@media (max-width: 960px) {
  .reports-container {
    .summary-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }
}
</style>
