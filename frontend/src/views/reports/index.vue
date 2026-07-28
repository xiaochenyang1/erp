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
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
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
  getSalesOrderReport
} from '@/api/workflow'
import { useReportList } from '@/composables/useReportList'
import { useReportPresentation } from '@/composables/useReportPresentation'

const { t } = useI18n()
const route = useRoute()
const {
  activeKey,
  activeState,
  dateRange,
  handleExport,
  handlePageChange,
  handleQuery,
  handleReset,
  handleSizeChange,
  handleTabChange,
  loadActiveReport,
  queryForm
} = useReportList(t, {
  getPurchaseOrderReport,
  getSalesOrderReport,
  getInventoryBalanceReport,
  getInventoryTransactionReport,
  getFinanceSettlementReport,
  exportPurchaseOrderReport,
  exportSalesOrderReport,
  exportInventoryBalanceReport,
  exportInventoryTransactionReport,
  exportFinanceSettlementReport,
  downloadBlob,
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message)
})
const {
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
} = useReportPresentation(t, activeKey, activeState)

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
