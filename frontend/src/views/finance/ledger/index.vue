<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item :label="$t('financeReportPages.ledger.subject')">
          <el-tree-select
            v-model="queryForm.subjectId"
            :data="subjectOptions"
            :props="{ label: 'name', value: 'id' }"
            :placeholder="$t('financeReportPages.ledger.subjectPlaceholder')"
            clearable
            filterable
            check-strictly
            style="width: 250px"
          />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.common.dateRange')">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            :range-separator="$t('financeReportPages.common.rangeSeparator')"
            :start-placeholder="$t('financeReportPages.common.startDate')"
            :end-placeholder="$t('financeReportPages.common.endDate')"
            value-format="YYYY-MM-DD"
            style="width: 280px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ $t('financeReportPages.common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ $t('financeReportPages.common.reset') }}</el-button>
          <el-button v-permission="'finance:ledger:view'" :icon="Download" @click="handleExport">{{ $t('financeReportPages.ledger.export') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane :label="$t('financeReportPages.ledger.generalTab')" name="general">
          <el-table
            v-loading="generalLoading"
            :data="generalLedger"
            border
            stripe
            :summary-method="getGeneralSummary"
            show-summary
          >
            <el-table-column prop="subjectCode" :label="$t('financeReportPages.common.subjectCode')" width="150" />
            <el-table-column prop="subjectName" :label="$t('financeReportPages.common.subjectName')" width="200" />
            <el-table-column prop="debitAmount" :label="$t('financeReportPages.common.debitAmount')" width="150" align="right">
              <template #default="{ row }">
                {{ formatAmount(row.debitAmount) }}
              </template>
            </el-table-column>
            <el-table-column prop="creditAmount" :label="$t('financeReportPages.common.creditAmount')" width="150" align="right">
              <template #default="{ row }">
                {{ formatAmount(row.creditAmount) }}
              </template>
            </el-table-column>
            <el-table-column :label="$t('financeReportPages.common.actions')" width="120" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="handleViewDetail(row)">
                  {{ $t('financeReportPages.ledger.viewDetail') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="$t('financeReportPages.ledger.detailTab')" name="detail">
          <el-table
            v-loading="detailLoading"
            :data="detailLedger"
            border
            stripe
          >
            <el-table-column prop="bizDate" :label="$t('financeReportPages.common.bizDate')" width="130">
              <template #default="{ row }">{{ formatDate(row.bizDate) }}</template>
            </el-table-column>
            <el-table-column prop="voucherId" :label="$t('financeReportPages.ledger.voucherId')" width="180" />
            <el-table-column prop="lineNo" :label="$t('financeReportPages.common.lineNo')" width="80" align="center" />
            <el-table-column prop="subjectCode" :label="$t('financeReportPages.common.subjectCode')" width="120" />
            <el-table-column prop="subjectName" :label="$t('financeReportPages.common.subjectName')" width="150" />
            <el-table-column prop="summary" :label="$t('financeReportPages.common.summary')" min-width="200" show-overflow-tooltip />
            <el-table-column prop="debitAmount" :label="$t('financeReportPages.common.debitAmount')" width="140" align="right">
              <template #default="{ row }">
                {{ row.debitAmount ? formatAmount(row.debitAmount) : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="creditAmount" :label="$t('financeReportPages.common.creditAmount')" width="140" align="right">
              <template #default="{ row }">
                {{ row.creditAmount ? formatAmount(row.creditAmount) : '-' }}
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            :current-page="pagination.page"
            :page-size="pagination.size"
            :total="pagination.total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            style="margin-top: 20px; justify-content: flex-end"
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Download, Refresh, Search } from '@element-plus/icons-vue'
import { downloadBlob } from '@/utils/download'
import {
  exportLedger,
  getAccountSubjectTree,
  getLedgerEntries,
  getLedgerSummary
} from '@/api/finance'
import { useFinanceLedgerList } from '@/composables/useFinanceLedgerList'
import { useFinanceLedgerPresentation } from '@/composables/useFinanceLedgerPresentation'

const { t } = useI18n()

const {
  formatAmount,
  formatDate,
  getGeneralSummary
} = useFinanceLedgerPresentation(t)

const {
  activeTab,
  dateRange,
  detailLedger,
  detailLoading,
  generalLedger,
  generalLoading,
  handleExport,
  handlePageChange,
  handleQuery,
  handleReset,
  handleSizeChange,
  handleTabChange,
  handleViewDetail,
  loadGeneralLedger,
  loadSubjects,
  pagination,
  queryForm,
  subjectOptions
} = useFinanceLedgerList(t, {
  getAccountSubjectTree,
  getLedgerSummary,
  getLedgerEntries,
  exportLedger,
  downloadBlob,
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message)
})

onMounted(async () => {
  await loadSubjects()
  await loadGeneralLedger()
})
</script>

<style scoped>
.app-container {
  padding: 20px;
}

.search-card {
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}
</style>
