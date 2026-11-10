<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item v-if="activeTab === 'general' || activeTab === 'detail'" :label="$t('financeReportPages.ledger.subject')">
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
          <el-button v-if="activeTab === 'general' || activeTab === 'detail'" v-permission="'finance:ledger:view'" :icon="Download" @click="handleExport">{{ $t('financeReportPages.ledger.export') }}</el-button>
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

        <el-tab-pane :label="$t('financeReportPages.ledger.trialTab')" name="trial">
          <el-alert
            v-if="trialBalance"
            :type="trialBalance.openingBalanced && trialBalance.periodBalanced && trialBalance.closingBalanced ? 'success' : 'error'"
            :closable="false"
            :title="trialBalance.openingBalanced && trialBalance.periodBalanced && trialBalance.closingBalanced
              ? $t('financeReportPages.ledger.trialBalanced')
              : $t('financeReportPages.ledger.trialUnbalanced')"
            show-icon
            style="margin-bottom: 16px"
          />
          <el-table v-loading="trialLoading" :data="trialBalance?.rows || []" border stripe>
            <el-table-column prop="subjectCode" :label="$t('financeReportPages.common.subjectCode')" width="120" fixed="left" />
            <el-table-column prop="subjectName" :label="$t('financeReportPages.common.subjectName')" min-width="160" fixed="left" />
            <el-table-column :label="$t('financeReportPages.ledger.openingBalance')" align="center">
              <el-table-column prop="openingDebit" :label="$t('financeReportPages.common.debit')" width="130" align="right">
                <template #default="{ row }">{{ formatAmount(row.openingDebit) }}</template>
              </el-table-column>
              <el-table-column prop="openingCredit" :label="$t('financeReportPages.common.credit')" width="130" align="right">
                <template #default="{ row }">{{ formatAmount(row.openingCredit) }}</template>
              </el-table-column>
            </el-table-column>
            <el-table-column :label="$t('financeReportPages.ledger.periodMovement')" align="center">
              <el-table-column prop="periodDebit" :label="$t('financeReportPages.common.debit')" width="130" align="right">
                <template #default="{ row }">{{ formatAmount(row.periodDebit) }}</template>
              </el-table-column>
              <el-table-column prop="periodCredit" :label="$t('financeReportPages.common.credit')" width="130" align="right">
                <template #default="{ row }">{{ formatAmount(row.periodCredit) }}</template>
              </el-table-column>
            </el-table-column>
            <el-table-column :label="$t('financeReportPages.ledger.closingBalance')" align="center">
              <el-table-column prop="closingDebit" :label="$t('financeReportPages.common.debit')" width="130" align="right">
                <template #default="{ row }">{{ formatAmount(row.closingDebit) }}</template>
              </el-table-column>
              <el-table-column prop="closingCredit" :label="$t('financeReportPages.common.credit')" width="130" align="right">
                <template #default="{ row }">{{ formatAmount(row.closingCredit) }}</template>
              </el-table-column>
            </el-table-column>
          </el-table>
          <el-descriptions v-if="trialBalance" :column="3" border style="margin-top: 16px">
            <el-descriptions-item :label="$t('financeReportPages.ledger.openingTotal')">
              {{ formatAmount(trialBalance.openingDebit) }} / {{ formatAmount(trialBalance.openingCredit) }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('financeReportPages.ledger.periodTotal')">
              {{ formatAmount(trialBalance.periodDebit) }} / {{ formatAmount(trialBalance.periodCredit) }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('financeReportPages.ledger.closingTotal')">
              {{ formatAmount(trialBalance.closingDebit) }} / {{ formatAmount(trialBalance.closingCredit) }}
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane :label="$t('financeReportPages.ledger.balanceSheetTab')" name="balance">
          <el-alert
            v-if="balanceSheet"
            :type="balanceSheet.balanced ? 'success' : 'error'"
            :closable="false"
            :title="balanceSheet.unclassifiedSubjectCodes.length
              ? $t('financeReportPages.ledger.unclassifiedSubjects', { codes: balanceSheet.unclassifiedSubjectCodes.join(', ') })
              : balanceSheet.balanced
                ? $t('financeReportPages.ledger.balanceSheetBalanced')
                : $t('financeReportPages.ledger.balanceSheetUnbalanced', { difference: formatAmount(balanceSheet.difference) })"
            show-icon
            style="margin-bottom: 16px"
          />
          <el-row v-loading="statementLoading" :gutter="16">
            <el-col :span="8">
              <el-card shadow="never">
                <template #header>{{ $t('financeReportPages.ledger.assets') }}</template>
                <el-table :data="balanceSheet?.assetLines || []" size="small">
                  <el-table-column prop="itemName" :label="$t('financeReportPages.ledger.reportItem')" min-width="130" />
                  <el-table-column prop="amount" :label="$t('financeReportPages.common.amount')" width="130" align="right">
                    <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
                  </el-table-column>
                </el-table>
              </el-card>
            </el-col>
            <el-col :span="8">
              <el-card shadow="never">
                <template #header>{{ $t('financeReportPages.ledger.liabilities') }}</template>
                <el-table :data="balanceSheet?.liabilityLines || []" size="small">
                  <el-table-column prop="itemName" :label="$t('financeReportPages.ledger.reportItem')" min-width="130" />
                  <el-table-column prop="amount" :label="$t('financeReportPages.common.amount')" width="130" align="right">
                    <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
                  </el-table-column>
                </el-table>
              </el-card>
            </el-col>
            <el-col :span="8">
              <el-card shadow="never">
                <template #header>{{ $t('financeReportPages.ledger.equity') }}</template>
                <el-table :data="balanceSheet?.equityLines || []" size="small">
                  <el-table-column prop="itemName" :label="$t('financeReportPages.ledger.reportItem')" min-width="130" />
                  <el-table-column prop="amount" :label="$t('financeReportPages.common.amount')" width="130" align="right">
                    <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
                  </el-table-column>
                </el-table>
              </el-card>
            </el-col>
          </el-row>
          <el-descriptions v-if="balanceSheet" :column="4" border style="margin-top: 16px">
            <el-descriptions-item :label="$t('financeReportPages.ledger.totalAssets')">{{ formatAmount(balanceSheet.totalAssets) }}</el-descriptions-item>
            <el-descriptions-item :label="$t('financeReportPages.ledger.totalLiabilities')">{{ formatAmount(balanceSheet.totalLiabilities) }}</el-descriptions-item>
            <el-descriptions-item :label="$t('financeReportPages.ledger.totalEquity')">{{ formatAmount(balanceSheet.totalEquity) }}</el-descriptions-item>
            <el-descriptions-item :label="$t('financeReportPages.ledger.formulaVersion')">{{ balanceSheet.formulaVersion }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane :label="$t('financeReportPages.ledger.incomeStatementTab')" name="income">
          <el-alert
            v-if="incomeStatement?.unclassifiedSubjectCodes.length"
            type="error"
            :closable="false"
            :title="$t('financeReportPages.ledger.unclassifiedSubjects', { codes: incomeStatement.unclassifiedSubjectCodes.join(', ') })"
            show-icon
            style="margin-bottom: 16px"
          />
          <el-row v-loading="statementLoading" :gutter="16">
            <el-col :span="12">
              <el-card shadow="never">
                <template #header>{{ $t('financeReportPages.ledger.revenue') }}</template>
                <el-table :data="incomeStatement?.revenueLines || []" size="small">
                  <el-table-column prop="itemName" :label="$t('financeReportPages.ledger.reportItem')" />
                  <el-table-column prop="amount" :label="$t('financeReportPages.common.amount')" width="160" align="right">
                    <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
                  </el-table-column>
                </el-table>
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card shadow="never">
                <template #header>{{ $t('financeReportPages.ledger.expense') }}</template>
                <el-table :data="incomeStatement?.expenseLines || []" size="small">
                  <el-table-column prop="itemName" :label="$t('financeReportPages.ledger.reportItem')" />
                  <el-table-column prop="amount" :label="$t('financeReportPages.common.amount')" width="160" align="right">
                    <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
                  </el-table-column>
                </el-table>
              </el-card>
            </el-col>
          </el-row>
          <el-descriptions v-if="incomeStatement" :column="4" border style="margin-top: 16px">
            <el-descriptions-item :label="$t('financeReportPages.ledger.totalRevenue')">{{ formatAmount(incomeStatement.totalRevenue) }}</el-descriptions-item>
            <el-descriptions-item :label="$t('financeReportPages.ledger.totalExpense')">{{ formatAmount(incomeStatement.totalExpense) }}</el-descriptions-item>
            <el-descriptions-item :label="$t('financeReportPages.ledger.netProfit')">{{ formatAmount(incomeStatement.netProfit) }}</el-descriptions-item>
            <el-descriptions-item :label="$t('financeReportPages.ledger.formulaVersion')">{{ incomeStatement.formulaVersion }}</el-descriptions-item>
          </el-descriptions>
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
  getBalanceSheet,
  getIncomeStatement,
  getLedgerEntries,
  getLedgerSummary,
  getTrialBalance
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
  balanceSheet,
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
  incomeStatement,
  loadGeneralLedger,
  loadSubjects,
  pagination,
  queryForm,
  statementLoading,
  subjectOptions,
  trialBalance,
  trialLoading
} = useFinanceLedgerList(t, {
  getAccountSubjectTree,
  getBalanceSheet,
  getIncomeStatement,
  getLedgerSummary,
  getLedgerEntries,
  getTrialBalance,
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
