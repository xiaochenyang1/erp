<template>
  <div class="reports-container">
    <el-card class="filter-card" shadow="never">
      <el-form :model="queryForm" inline>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="queryForm.keyword" placeholder="单号关键字" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button v-permission="'report:view'" :icon="Download" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="summary-grid">
      <el-card shadow="never" class="summary-item">
        <span>当前报表</span>
        <strong>{{ activeReport.label }}</strong>
      </el-card>
      <el-card shadow="never" class="summary-item">
        <span>记录数</span>
        <strong>{{ activeState.total }}</strong>
      </el-card>
      <el-card shadow="never" class="summary-item">
        <span>金额合计</span>
        <strong>{{ formatMoney(summaryAmount) }}</strong>
      </el-card>
      <el-card shadow="never" class="summary-item">
        <span>当前页</span>
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
        <el-table-column prop="bizNo" label="单据编号" min-width="170" />
        <el-table-column prop="partnerId" :label="activeKey === 'purchase' ? '供应商ID' : '客户ID'" width="120" />
        <el-table-column prop="bizDate" label="业务日期" width="120" />
        <el-table-column prop="status" label="单据状态" width="120">
          <template #default="{ row }">
            <el-tag>{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="approvalStatus" label="审批状态" width="120" />
        <el-table-column prop="fulfillmentStatus" :label="activeKey === 'purchase' ? '收货状态' : '发货状态'" width="120" />
        <el-table-column prop="totalQuantity" label="数量" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.totalQuantity) }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="金额" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="totalTaxAmount" label="税额" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalTaxAmount) }}</template>
        </el-table-column>
      </el-table>

      <el-table v-else-if="activeKey === 'inventoryBalance'" v-loading="activeState.loading" :data="activeState.records" border stripe>
        <el-table-column prop="warehouseId" label="仓库ID" width="120" />
        <el-table-column prop="productId" label="产品ID" width="120" />
        <el-table-column prop="qtyOnHand" label="现存量" width="130" align="right">
          <template #default="{ row }">{{ formatNumber(row.qtyOnHand) }}</template>
        </el-table-column>
        <el-table-column prop="qtyReserved" label="预留量" width="130" align="right">
          <template #default="{ row }">{{ formatNumber(row.qtyReserved) }}</template>
        </el-table-column>
        <el-table-column prop="qtyAvailable" label="可用量" width="130" align="right">
          <template #default="{ row }">{{ formatNumber(row.qtyAvailable) }}</template>
        </el-table-column>
        <el-table-column prop="amountOnHand" label="库存金额" width="150" align="right">
          <template #default="{ row }">{{ formatMoney(row.amountOnHand) }}</template>
        </el-table-column>
        <el-table-column prop="updatedTime" label="更新时间" min-width="180" />
      </el-table>

      <el-table v-else-if="activeKey === 'inventoryTransaction'" v-loading="activeState.loading" :data="activeState.records" border stripe>
        <el-table-column prop="bizNo" label="业务单号" min-width="160" />
        <el-table-column prop="bizType" label="业务类型" width="140" />
        <el-table-column prop="direction" label="方向" width="100">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'IN' ? 'success' : 'warning'">{{ row.direction }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="warehouseId" label="仓库ID" width="110" />
        <el-table-column prop="productId" label="产品ID" width="110" />
        <el-table-column prop="qty" label="数量" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.qty) }}</template>
        </el-table-column>
        <el-table-column prop="unitCost" label="单位成本" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.unitCost) }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="occurredTime" label="发生时间" min-width="180" />
      </el-table>

      <el-table v-else v-loading="activeState.loading" :data="activeState.records" border stripe>
        <el-table-column prop="direction" label="方向" width="110">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'RECEIVABLE' ? 'success' : 'warning'">
              {{ row.direction === 'RECEIVABLE' ? '应收' : '应付' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bizNo" label="结算单号" min-width="160" />
        <el-table-column prop="partnerId" label="往来方ID" width="120" />
        <el-table-column prop="bizDate" label="业务日期" width="120" />
        <el-table-column prop="sourceType" label="来源类型" width="130" />
        <el-table-column prop="sourceNo" label="来源单号" min-width="160" />
        <el-table-column prop="originalAmount" label="原始金额" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.originalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="settledAmount" label="已结金额" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.settledAmount) }}</template>
        </el-table-column>
        <el-table-column prop="remainingAmount" label="未结金额" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.remainingAmount) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120" />
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
import { ElMessage } from 'element-plus'
import { Download, Refresh, Search } from '@element-plus/icons-vue'
import { downloadBlob } from '@/utils/download'
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

const reportTabs: Array<{ key: ReportKey; label: string }> = [
  { key: 'purchase', label: '采购订单' },
  { key: 'sales', label: '销售订单' },
  { key: 'inventoryBalance', label: '库存余额' },
  { key: 'inventoryTransaction', label: '库存流水' },
  { key: 'financeSettlement', label: '应收应付' }
]

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
const activeReport = computed(() => reportTabs.find((item) => item.key === activeKey.value) || reportTabs[0])
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
    downloadBlob(blob, `${activeKey.value}-${Date.now()}.csv`)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

const formatMoney = (amount?: number) => {
  return `¥${Number(amount || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`
}

const formatNumber = (value?: number) => {
  return Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

const isReportKey = (value: unknown): value is ReportKey => {
  return typeof value === 'string' && reportTabs.some((item) => item.key === value)
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
