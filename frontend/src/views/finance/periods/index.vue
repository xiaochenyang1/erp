<template>
  <div class="app-container period-page">
    <el-card shadow="never" class="toolbar-card">
      <el-form inline>
        <el-form-item :label="$t('financeReportPages.periods.accountYear')">
          <el-input-number
            v-model="queryYear"
            :min="2000"
            :max="2199"
            :controls="false"
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadData">{{ $t('financeReportPages.common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ $t('financeReportPages.common.reset') }}</el-button>
          <el-button
            v-permission="'finance:period:manage'"
            type="success"
            :icon="Calendar"
            :loading="generateLoading"
            @click="handleGenerate"
          >
            {{ $t('financeReportPages.periods.generate') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="status-overview">
      <div v-for="item in statusSummary" :key="item.key" class="summary-item">
        <span class="summary-label">{{ item.label }}</span>
        <strong :class="['summary-value', item.key]">{{ item.value }}</strong>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>{{ $t('financeReportPages.periods.management') }}</span>
          <el-button :icon="Refresh" text @click="loadData">{{ $t('financeReportPages.common.refresh') }}</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="periodMonth" :label="$t('financeReportPages.periods.period')" width="120" fixed>
          <template #default="{ row }">
            <span class="period-month">{{ formatMonth(row.periodMonth) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="periodYear" :label="$t('financeReportPages.periods.year')" width="100" align="center" />
        <el-table-column prop="startDate" :label="$t('financeReportPages.common.startDate')" width="130" align="center" />
        <el-table-column prop="endDate" :label="$t('financeReportPages.common.endDate')" width="130" align="center" />
        <el-table-column prop="status" :label="$t('financeReportPages.common.status')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lockedTime" :label="$t('financeReportPages.periods.lockTime')" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.lockedTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="closedTime" :label="$t('financeReportPages.periods.closeTime')" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.closedTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="reopenedTime" :label="$t('financeReportPages.periods.unlockTime')" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.reopenedTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="$t('financeReportPages.common.remark')" min-width="160" show-overflow-tooltip />
        <el-table-column :label="$t('financeReportPages.common.actions')" width="380" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleCheck(row)">
              {{ $t('financeReportPages.periods.check') }}
            </el-button>
            <el-button
              v-if="row.status === 'OPEN' || row.status === 'LOCKED'"
              v-permission="'finance:period:close'"
              type="success"
              link
              :icon="Guide"
              @click="openWizard(row)"
            >
              {{ $t('financeReportPages.periods.closeWizard') }}
            </el-button>
            <el-button type="primary" link :icon="DataAnalysis" @click="openReconciliation(row)">
              {{ $t('financeReportPages.periods.reconcile') }}
            </el-button>
            <el-button
              v-if="row.status === 'OPEN'"
              v-permission="'finance:period:close'"
              type="warning"
              link
              :icon="Lock"
              @click="handleLock(row)"
            >
              {{ $t('financeReportPages.periods.lock') }}
            </el-button>
            <el-button
              v-if="row.status === 'LOCKED'"
              v-permission="'finance:period:close'"
              type="success"
              link
              :icon="CircleCheck"
              @click="handleClose(row)"
            >
              {{ $t('financeReportPages.periods.closePeriod') }}
            </el-button>
            <el-button
              v-if="row.status === 'LOCKED'"
              v-permission="'finance:period:reopen'"
              type="danger"
              link
              :icon="RefreshLeft"
              @click="handleUnlock(row)"
            >
              {{ $t('financeReportPages.periods.unlock') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="closeCheckVisible"
      :title="$t('financeReportPages.periods.monthCloseCheck')"
      width="820px"
      class="period-dialog"
    >
      <div v-loading="closeCheckLoading">
        <el-alert
          v-if="closeCheckResult"
          :title="closeCheckResult.passed ? $t('financeReportPages.periods.checkPassedTitle') : $t('financeReportPages.periods.checkFailedTitle')"
          :type="closeCheckResult.passed ? 'success' : 'error'"
          :closable="false"
          show-icon
        />

        <el-table
          v-if="closeCheckResult && (closeCheckResult.checks?.length || 0) > 0"
          :data="closeCheckResult.checks"
          border
          stripe
          class="dialog-table"
        >
          <el-table-column prop="category" :label="$t('financeReportPages.periods.category')" width="90" />
          <el-table-column prop="title" :label="$t('financeReportPages.periods.checkItem')" width="160" />
          <el-table-column :label="$t('financeReportPages.periods.result')" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.passed ? 'success' : 'danger'" size="small">
                {{ row.passed ? $t('financeReportPages.periods.passed') : $t('financeReportPages.periods.blocked') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" :label="$t('financeReportPages.periods.explanation')" min-width="280" show-overflow-tooltip />
          <el-table-column prop="metric" :label="$t('financeReportPages.periods.metric')" width="120" align="right">
            <template #default="{ row }">{{ formatAmount(row.metric) }}</template>
          </el-table-column>
        </el-table>

        <el-table
          v-else-if="closeCheckResult && closeCheckResult.issues.length > 0"
          :data="closeCheckResult.issues"
          border
          stripe
          class="dialog-table"
        >
          <el-table-column prop="type" :label="$t('financeReportPages.periods.issueType')" width="210">
            <template #default="{ row }">
              <el-tag :type="getIssueSeverity(row.type)" size="small">
                {{ getIssueTypeLabel(row.type) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" :label="$t('financeReportPages.periods.explanation')" min-width="260" />
          <el-table-column prop="amount" :label="$t('financeReportPages.periods.metric')" width="140" align="right">
            <template #default="{ row }">
              {{ formatAmount(row.amount) }}
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-else-if="closeCheckResult && closeCheckResult.passed" :description="$t('financeReportPages.periods.noBlockingIssues')" />
      </div>
      <template #footer>
        <el-button @click="closeCheckVisible = false">{{ $t('financeReportPages.common.close') }}</el-button>
        <el-button
          v-if="activePeriod?.status === 'OPEN'"
          v-permission="'finance:period:close'"
          type="warning"
          :disabled="!closeCheckResult?.passed"
          @click="activePeriod && handleLock(activePeriod)"
        >
          {{ $t('financeReportPages.periods.lockAfterCheck') }}
        </el-button>
        <el-button
          v-if="activePeriod?.status === 'LOCKED'"
          v-permission="'finance:period:close'"
          type="success"
          @click="activePeriod && handleClose(activePeriod)"
        >
          {{ $t('financeReportPages.periods.continueClosing') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 结账向导 -->
    <el-drawer
      v-model="wizardVisible"
      :title="$t('financeReportPages.periods.wizardTitle', { period: wizardPeriod?.periodMonth || '' })"
      size="560px"
      destroy-on-close
    >
      <div v-loading="wizardLoading" class="wizard-body">
        <el-steps :active="wizardStep" finish-status="success" align-center>
          <el-step :title="$t('financeReportPages.periods.overview')" />
          <el-step :title="$t('financeReportPages.periods.monthCloseCheck')" />
          <el-step :title="$t('financeReportPages.periods.lockOrClose')" />
        </el-steps>

        <section v-if="wizardStep === 0" class="wizard-panel">
          <el-descriptions :column="1" border>
            <el-descriptions-item :label="$t('financeReportPages.periods.period')">{{ wizardPeriod?.periodMonth }}</el-descriptions-item>
            <el-descriptions-item :label="$t('financeReportPages.periods.dateSpan')">
              {{ wizardPeriod?.startDate }} ~ {{ wizardPeriod?.endDate }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('financeReportPages.periods.currentStatus')">
              <el-tag :type="getStatusType(wizardPeriod?.status)" size="small">
                {{ getStatusLabel(wizardPeriod?.status) }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
          <el-alert
            class="wizard-alert"
            type="info"
            :closable="false"
            show-icon
            :title="$t('financeReportPages.periods.suggestedOrder')"
          />
        </section>

        <section v-else-if="wizardStep === 1" class="wizard-panel">
          <el-alert
            v-if="wizardCheck"
            :title="wizardCheck.passed ? $t('financeReportPages.periods.allChecksPassed') : $t('financeReportPages.periods.blockingCount', { count: wizardCheck.issues.length })"
            :type="wizardCheck.passed ? 'success' : 'error'"
            :closable="false"
            show-icon
          />
          <el-table
            v-if="wizardCheck?.checks?.length"
            :data="wizardCheck.checks"
            border
            stripe
            class="dialog-table"
          >
            <el-table-column prop="category" :label="$t('financeReportPages.periods.category')" width="80" />
            <el-table-column prop="title" :label="$t('financeReportPages.periods.checkItem')" width="140" />
            <el-table-column :label="$t('financeReportPages.periods.result')" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.passed ? 'success' : 'danger'" size="small">
                  {{ row.passed ? $t('financeReportPages.periods.passed') : $t('financeReportPages.periods.blocked') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" :label="$t('financeReportPages.periods.explanation')" min-width="200" show-overflow-tooltip />
          </el-table>
          <el-button class="wizard-refresh" :icon="Refresh" @click="runWizardCheck">{{ $t('financeReportPages.periods.recheck') }}</el-button>
        </section>

        <section v-else class="wizard-panel">
          <el-result
            :icon="wizardCheck?.passed || wizardPeriod?.status === 'LOCKED' ? 'success' : 'warning'"
            :title="wizardActionTitle"
            :sub-title="wizardActionSubtitle"
          />
          <div class="wizard-actions">
            <el-button
              v-if="wizardPeriod?.status === 'OPEN'"
              v-permission="'finance:period:close'"
              type="warning"
              :disabled="!wizardCheck?.passed"
              :loading="wizardActionLoading"
              @click="wizardLock"
            >
              {{ $t('financeReportPages.periods.lockPeriod') }}
            </el-button>
            <el-button
              v-if="wizardPeriod?.status === 'LOCKED'"
              v-permission="'finance:period:close'"
              type="success"
              :loading="wizardActionLoading"
              @click="wizardClose"
            >
              {{ $t('financeReportPages.periods.confirmClose') }}
            </el-button>
            <el-button
              v-if="wizardPeriod?.status === 'LOCKED'"
              v-permission="'finance:period:reopen'"
              type="danger"
              plain
              :loading="wizardActionLoading"
              @click="wizardUnlock"
            >
              {{ $t('financeReportPages.periods.unlock') }}
            </el-button>
          </div>
        </section>
      </div>
      <template #footer>
        <div class="wizard-footer">
          <el-button :disabled="wizardStep === 0" @click="wizardStep = Math.max(0, wizardStep - 1)">{{ $t('financeReportPages.periods.previous') }}</el-button>
          <el-button
            v-if="wizardStep < 2"
            type="primary"
            @click="nextWizardStep"
          >
            {{ $t('financeReportPages.periods.next') }}
          </el-button>
          <el-button v-else @click="wizardVisible = false">{{ $t('financeReportPages.common.finish') }}</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      v-model="reconciliationVisible"
      :title="$t('financeReportPages.periods.inventoryFinanceReconciliation')"
      width="920px"
      class="period-dialog"
    >
      <div v-loading="reconciliationLoading">
        <div v-if="reconciliationResult" class="reconciliation-summary">
          <div class="reconciliation-item">
            <span>{{ $t('financeReportPages.periods.inventoryNetAmount') }}</span>
            <strong>{{ formatAmount(reconciliationResult.inventoryNetAmount) }}</strong>
          </div>
          <div class="reconciliation-item">
            <span>{{ $t('financeReportPages.periods.financeInventoryNetAmount') }}</span>
            <strong>{{ formatAmount(reconciliationResult.financeInventoryNetAmount) }}</strong>
          </div>
          <div class="reconciliation-item">
            <span>{{ $t('financeReportPages.periods.differenceAmount') }}</span>
            <strong :class="reconciliationResult.balanced ? 'balanced' : 'unbalanced'">
              {{ formatAmount(reconciliationResult.differenceAmount) }}
            </strong>
          </div>
          <div class="reconciliation-item">
            <span>{{ $t('financeReportPages.periods.reconciliationStatus') }}</span>
            <el-tag :type="reconciliationResult.balanced ? 'success' : 'danger'">
              {{ reconciliationResult.balanced ? $t('financeReportPages.periods.balanced') : $t('financeReportPages.periods.unbalanced') }}
            </el-tag>
          </div>
        </div>

        <div class="difference-toolbar">
          <el-select
            v-model="differenceType"
            :placeholder="$t('financeReportPages.periods.differenceType')"
            clearable
            style="width: 180px"
            @change="loadDifferences"
          >
            <el-option
              v-for="option in differenceTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-button :icon="Refresh" @click="loadDifferences">{{ $t('financeReportPages.periods.refreshDifferences') }}</el-button>
        </div>

        <el-table
          v-loading="differenceLoading"
          :data="differences"
          border
          stripe
          class="dialog-table"
        >
          <el-table-column prop="sourceNo" :label="$t('financeReportPages.periods.sourceNo')" min-width="160" show-overflow-tooltip />
          <el-table-column prop="sourceType" :label="$t('financeReportPages.periods.sourceType')" width="130">
            <template #default="{ row }">
              {{ getSourceTypeLabel(row.sourceType) }}
            </template>
          </el-table-column>
          <el-table-column prop="inventoryAmount" :label="$t('financeReportPages.periods.inventoryAmount')" width="130" align="right">
            <template #default="{ row }">
              {{ formatAmount(row.inventoryAmount) }}
            </template>
          </el-table-column>
          <el-table-column prop="financeAmount" :label="$t('financeReportPages.periods.financeAmount')" width="130" align="right">
            <template #default="{ row }">
              {{ formatAmount(row.financeAmount) }}
            </template>
          </el-table-column>
          <el-table-column prop="differenceAmount" :label="$t('financeReportPages.periods.differenceAmount')" width="130" align="right">
            <template #default="{ row }">
              <span :class="Number(row.differenceAmount) === 0 ? 'balanced' : 'unbalanced'">
                {{ formatAmount(row.differenceAmount) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="differenceType" :label="$t('financeReportPages.periods.differenceType')" width="140">
            <template #default="{ row }">
              <el-tag :type="getDifferenceTypeTag(row.differenceType)" size="small">
                {{ getDifferenceTypeLabel(row.differenceType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('financeReportPages.common.details')" width="90" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link :icon="View" @click="openDifferenceDetail(row)">
                {{ $t('financeReportPages.common.details') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="reconciliationVisible = false">{{ $t('financeReportPages.common.close') }}</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="differenceDetailVisible"
      :title="$t('financeReportPages.periods.differenceDetail')"
      size="86%"
      class="difference-detail-drawer"
    >
      <div v-loading="differenceDetailLoading" class="difference-detail">
        <template v-if="differenceDetail">
          <div class="detail-heading">
            <div>
              <div class="detail-source-no">{{ differenceDetail.sourceNo }}</div>
              <div class="detail-source-type">{{ getSourceTypeLabel(differenceDetail.sourceType) }}</div>
            </div>
            <el-tag :type="getDifferenceTypeTag(differenceDetail.differenceType)">
              {{ getDifferenceTypeLabel(differenceDetail.differenceType) }}
            </el-tag>
          </div>

          <div class="detail-summary">
            <div class="detail-summary-item">
              <span>{{ $t('financeReportPages.periods.inventoryAmount') }}</span>
              <strong>{{ formatAmount(differenceDetail.inventoryAmount) }}</strong>
            </div>
            <div class="detail-summary-item">
              <span>{{ $t('financeReportPages.periods.financeAmount') }}</span>
              <strong>{{ formatAmount(differenceDetail.financeAmount) }}</strong>
            </div>
            <div class="detail-summary-item">
              <span>{{ $t('financeReportPages.periods.differenceAmount') }}</span>
              <strong :class="Number(differenceDetail.differenceAmount) === 0 ? 'balanced' : 'unbalanced'">
                {{ formatAmount(differenceDetail.differenceAmount) }}
              </strong>
            </div>
          </div>

          <section class="detail-section">
            <div class="section-title">{{ $t('financeReportPages.periods.inventoryTransactions') }}</div>
            <el-table
              :data="differenceDetail.inventoryTransactions"
              border
              stripe
              :empty-text="$t('financeReportPages.periods.noInventoryTransactions')"
            >
              <el-table-column prop="occurredTime" :label="$t('financeReportPages.periods.occurredTime')" width="170">
                <template #default="{ row }">
                  {{ formatDateTime(row.occurredTime) }}
                </template>
              </el-table-column>
              <el-table-column prop="direction" :label="$t('financeReportPages.periods.direction')" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.direction === 'IN' ? 'success' : 'warning'" size="small">
                    {{ row.direction === 'IN' ? $t('financeReportPages.periods.inbound') : $t('financeReportPages.periods.outbound') }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="qty" :label="$t('financeReportPages.periods.quantity')" width="120" align="right">
                <template #default="{ row }">
                  {{ formatQty(row.qty) }}
                </template>
              </el-table-column>
              <el-table-column prop="amount" :label="$t('financeReportPages.common.amount')" width="130" align="right">
                <template #default="{ row }">
                  {{ formatAmount(row.amount) }}
                </template>
              </el-table-column>
              <el-table-column prop="remark" :label="$t('financeReportPages.common.remark')" min-width="180" show-overflow-tooltip />
            </el-table>
          </section>

          <section class="detail-section">
            <div class="section-title">{{ $t('financeReportPages.periods.voucherEntries') }}</div>
            <el-table
              :data="differenceDetail.voucherEntries"
              border
              stripe
              :empty-text="$t('financeReportPages.periods.noVoucherEntries')"
            >
              <el-table-column prop="voucherNo" :label="$t('financeReportPages.periods.voucherNo')" min-width="150" show-overflow-tooltip />
              <el-table-column prop="bizDate" :label="$t('financeReportPages.common.bizDate')" width="120" align="center" />
              <el-table-column prop="lineNo" :label="$t('financeReportPages.common.lineNo')" width="80" align="center" />
              <el-table-column :label="$t('financeReportPages.common.subject')" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.subjectCode }} {{ row.subjectName }}
                </template>
              </el-table-column>
              <el-table-column prop="debitAmount" :label="$t('financeReportPages.common.debit')" width="130" align="right">
                <template #default="{ row }">
                  {{ formatAmount(row.debitAmount) }}
                </template>
              </el-table-column>
              <el-table-column prop="creditAmount" :label="$t('financeReportPages.common.credit')" width="130" align="right">
                <template #default="{ row }">
                  {{ formatAmount(row.creditAmount) }}
                </template>
              </el-table-column>
              <el-table-column prop="summary" :label="$t('financeReportPages.common.summary')" min-width="180" show-overflow-tooltip />
            </el-table>
          </section>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { formatLocalizedDateTime, formatLocalizedNumber } from '@/utils/locale'
import {
  Calendar,
  CircleCheck,
  DataAnalysis,
  Guide,
  Lock,
  Refresh,
  RefreshLeft,
  Search,
  View
} from '@element-plus/icons-vue'
import {
  checkAccountPeriodClose,
  closeAccountPeriod,
  generateAccountPeriods,
  getAccountPeriods,
  getInventoryFinanceDifferenceDetail,
  getInventoryFinanceDifferences,
  getInventoryFinanceReconciliation,
  lockAccountPeriod,
  reopenAccountPeriod as unlockAccountPeriod,
  type AccountPeriod,
  type AccountPeriodCloseCheck,
  type InventoryFinanceDifference,
  type InventoryFinanceDifferenceDetail,
  type InventoryFinanceReconciliation
} from '@/api/finance'

const { t } = useI18n()
const currentYear = new Date().getFullYear()
const queryYear = ref(currentYear)
const loading = ref(false)
const generateLoading = ref(false)
const tableData = ref<AccountPeriod[]>([])

const closeCheckVisible = ref(false)
const closeCheckLoading = ref(false)
const closeCheckResult = ref<AccountPeriodCloseCheck>()

const wizardVisible = ref(false)
const wizardLoading = ref(false)
const wizardActionLoading = ref(false)
const wizardStep = ref(0)
const wizardPeriod = ref<AccountPeriod>()
const wizardCheck = ref<AccountPeriodCloseCheck>()

const reconciliationVisible = ref(false)
const reconciliationLoading = ref(false)
const differenceLoading = ref(false)
const reconciliationResult = ref<InventoryFinanceReconciliation>()
const differences = ref<InventoryFinanceDifference[]>([])
const differenceType = ref('')
const activePeriod = ref<AccountPeriod>()
const differenceDetailVisible = ref(false)
const differenceDetailLoading = ref(false)
const differenceDetail = ref<InventoryFinanceDifferenceDetail>()

const differenceTypeOptions = computed(() => [
  { label: t('financeReportPages.periods.difference.inventoryOnly'), value: 'INVENTORY_ONLY' },
  { label: t('financeReportPages.periods.difference.financeOnly'), value: 'FINANCE_ONLY' },
  { label: t('financeReportPages.periods.difference.amountMismatch'), value: 'AMOUNT_MISMATCH' }
])

const statusSummary = computed(() => {
  const count = (status: string) => tableData.value.filter(item => item.status === status).length
  return [
    { key: 'open', label: t('financeReportPages.periods.statusSummary.open'), value: count('OPEN') },
    { key: 'locked', label: t('financeReportPages.periods.statusSummary.locked'), value: count('LOCKED') },
    { key: 'closed', label: t('financeReportPages.periods.statusSummary.closed'), value: count('CLOSED') },
    { key: 'total', label: t('financeReportPages.periods.statusSummary.total'), value: tableData.value.length }
  ]
})

const loadData = async () => {
  loading.value = true
  try {
    const periods = await getAccountPeriods(queryYear.value)
    tableData.value = periods || []
  } catch (error) {
    ElMessage.error(t('financeReportPages.periods.message.loadFailed'))
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  queryYear.value = currentYear
  loadData()
}

const handleGenerate = async () => {
  try {
    await ElMessageBox.confirm(
      t('financeReportPages.periods.message.generateConfirm', { year: queryYear.value }),
      t('financeReportPages.periods.message.generateTitle'),
      { type: 'warning' }
    )
    generateLoading.value = true
    const periods = await generateAccountPeriods(queryYear.value)
    tableData.value = periods || []
    ElMessage.success(t('financeReportPages.periods.message.generated'))
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('financeReportPages.periods.message.generateFailed'))
    }
  } finally {
    generateLoading.value = false
  }
}

const handleCheck = async (row: AccountPeriod) => {
  activePeriod.value = row
  closeCheckVisible.value = true
  closeCheckLoading.value = true
  closeCheckResult.value = undefined
  try {
    closeCheckResult.value = await checkAccountPeriodClose(row.id)
  } catch (error) {
    ElMessage.error(t('financeReportPages.periods.message.checkFailed'))
  } finally {
    closeCheckLoading.value = false
  }
}

const openWizard = async (row: AccountPeriod) => {
  wizardPeriod.value = row
  wizardStep.value = 0
  wizardCheck.value = undefined
  wizardVisible.value = true
}

const runWizardCheck = async () => {
  if (!wizardPeriod.value) return
  wizardLoading.value = true
  try {
    wizardCheck.value = await checkAccountPeriodClose(wizardPeriod.value.id)
  } catch {
    ElMessage.error(t('financeReportPages.periods.message.checkFailed'))
  } finally {
    wizardLoading.value = false
  }
}

const nextWizardStep = async () => {
  if (wizardStep.value === 0) {
    wizardStep.value = 1
    await runWizardCheck()
    return
  }
  if (wizardStep.value === 1) {
    wizardStep.value = 2
  }
}

const wizardActionTitle = computed(() => {
  if (wizardPeriod.value?.status === 'CLOSED') return t('financeReportPages.periods.wizardAction.closed')
  if (wizardPeriod.value?.status === 'LOCKED') return t('financeReportPages.periods.wizardAction.locked')
  if (wizardCheck.value?.passed) return t('financeReportPages.periods.wizardAction.ready')
  return t('financeReportPages.periods.wizardAction.blocked')
})

const wizardActionSubtitle = computed(() => {
  if (!wizardPeriod.value) return ''
  if (wizardPeriod.value.status === 'CLOSED') {
    return t('financeReportPages.periods.wizardAction.closedSubtitle', { period: wizardPeriod.value.periodMonth })
  }
  if (wizardPeriod.value.status === 'LOCKED') {
    return t('financeReportPages.periods.wizardAction.lockedSubtitle')
  }
  return wizardCheck.value?.passed
    ? t('financeReportPages.periods.wizardAction.readySubtitle')
    : t('financeReportPages.periods.wizardAction.blockedSubtitle')
})

const refreshWizardPeriod = async () => {
  await loadData()
  if (!wizardPeriod.value) return
  wizardPeriod.value = tableData.value.find((item) => String(item.id) === String(wizardPeriod.value?.id))
}

const wizardLock = async () => {
  if (!wizardPeriod.value) return
  wizardActionLoading.value = true
  try {
    await handleLock(wizardPeriod.value)
    await refreshWizardPeriod()
    await runWizardCheck()
  } finally {
    wizardActionLoading.value = false
  }
}

const wizardClose = async () => {
  if (!wizardPeriod.value) return
  wizardActionLoading.value = true
  try {
    await handleClose(wizardPeriod.value)
    await refreshWizardPeriod()
    wizardVisible.value = false
  } finally {
    wizardActionLoading.value = false
  }
}

const wizardUnlock = async () => {
  if (!wizardPeriod.value) return
  wizardActionLoading.value = true
  try {
    await handleUnlock(wizardPeriod.value)
    await refreshWizardPeriod()
  } finally {
    wizardActionLoading.value = false
  }
}

const handleLock = async (row: AccountPeriod) => {
  try {
    const check = await checkAccountPeriodClose(row.id)
    if (!check.passed) {
      activePeriod.value = row
      closeCheckResult.value = check
      closeCheckVisible.value = true
      ElMessage.warning(t('financeReportPages.periods.message.checkBlocksLock'))
      return
    }

    await ElMessageBox.confirm(t('financeReportPages.periods.message.lockConfirm', { period: row.periodMonth }), t('financeReportPages.periods.lockPeriod'), {
      type: 'warning'
    })
    await lockAccountPeriod(row.id)
    ElMessage.success(t('financeReportPages.periods.message.locked'))
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('financeReportPages.periods.message.lockFailed'))
    }
  }
}

const handleClose = async (row: AccountPeriod) => {
  // 结账前主动跑月结检查并回显：后端 close() 本身不再校验，带病结账风险需在确认弹窗暴露给用户
  let check: AccountPeriodCloseCheck | undefined
  try {
    check = await checkAccountPeriodClose(row.id)
  } catch (error) {
    ElMessage.error(t('financeReportPages.periods.message.checkBlocksClose'))
    return
  }

  if (check && !check.passed) {
    activePeriod.value = row
    closeCheckResult.value = check
    closeCheckVisible.value = true
    try {
      await ElMessageBox.confirm(
        t('financeReportPages.periods.message.riskyCloseConfirm', {
          count: check.issues.length,
          period: row.periodMonth
        }),
        t('financeReportPages.periods.message.riskyCloseTitle'),
        {
          type: 'error',
          confirmButtonText: t('financeReportPages.periods.message.closeAnyway'),
          cancelButtonText: t('financeReportPages.common.cancel'),
          confirmButtonClass: 'el-button--danger'
        }
      )
    } catch {
      return
    }
  } else {
    try {
      await ElMessageBox.confirm(
        t('financeReportPages.periods.message.safeCloseConfirm', { period: row.periodMonth }),
        t('financeReportPages.periods.message.closeTitle'),
        { type: 'warning' }
      )
    } catch {
      return
    }
  }

  try {
    await closeAccountPeriod(row.id)
    ElMessage.success(t('financeReportPages.periods.message.closed'))
    closeCheckVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error(t('financeReportPages.periods.message.closeFailed'))
  }
}

const handleUnlock = async (row: AccountPeriod) => {
  try {
    await ElMessageBox.confirm(
      t('financeReportPages.periods.message.unlockConfirm', { period: row.periodMonth }),
      t('financeReportPages.periods.message.unlockTitle'),
      {
        type: 'warning',
        confirmButtonText: t('financeReportPages.periods.message.confirmUnlock'),
        cancelButtonText: t('financeReportPages.common.cancel')
      }
    )
    await unlockAccountPeriod(row.id)
    ElMessage.success(t('financeReportPages.periods.message.unlocked'))
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('financeReportPages.periods.message.unlockFailed'))
    }
  }
}

const openReconciliation = async (row: AccountPeriod) => {
  activePeriod.value = row
  reconciliationVisible.value = true
  reconciliationResult.value = undefined
  differences.value = []
  differenceType.value = ''
  await loadReconciliation()
}

const loadReconciliation = async () => {
  if (!activePeriod.value) return

  reconciliationLoading.value = true
  differenceLoading.value = true
  try {
    const [summary, rows] = await Promise.all([
      getInventoryFinanceReconciliation(activePeriod.value.id),
      getInventoryFinanceDifferences(activePeriod.value.id)
    ])
    reconciliationResult.value = summary
    differences.value = rows || []
  } catch (error) {
    ElMessage.error(t('financeReportPages.periods.message.reconciliationLoadFailed'))
  } finally {
    reconciliationLoading.value = false
    differenceLoading.value = false
  }
}

const loadDifferences = async () => {
  if (!activePeriod.value) return

  differenceLoading.value = true
  try {
    const rows = await getInventoryFinanceDifferences(activePeriod.value.id, {
      differenceType: differenceType.value || undefined
    })
    differences.value = rows || []
  } catch (error) {
    ElMessage.error(t('financeReportPages.periods.message.differencesLoadFailed'))
  } finally {
    differenceLoading.value = false
  }
}

const openDifferenceDetail = async (row: InventoryFinanceDifference) => {
  if (!activePeriod.value) return

  differenceDetailVisible.value = true
  differenceDetailLoading.value = true
  differenceDetail.value = undefined
  try {
    differenceDetail.value = await getInventoryFinanceDifferenceDetail(
      activePeriod.value.id,
      row.sourceType,
      row.sourceNo
    )
  } catch (error) {
    ElMessage.error(t('financeReportPages.periods.message.differenceDetailLoadFailed'))
  } finally {
    differenceDetailLoading.value = false
  }
}

const formatMonth = (periodMonth: string) => {
  return periodMonth || '-'
}

const formatDateTime = (value?: string) => {
  return formatLocalizedDateTime(value) || '-'
}

const formatAmount = (amount?: number | string) => {
  const value = Number(amount ?? 0)
  return Number.isFinite(value)
    ? formatLocalizedNumber(value, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : '0.00'
}

const formatQty = (qty?: number | string) => {
  const value = Number(qty ?? 0)
  return Number.isFinite(value)
    ? formatLocalizedNumber(value, { minimumFractionDigits: 4, maximumFractionDigits: 4 })
    : '0.0000'
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    OPEN: t('financeReportPages.periods.status.open'),
    LOCKED: t('financeReportPages.periods.status.locked'),
    CLOSED: t('financeReportPages.periods.status.closed')
  }
  return map[status] || status
}

const getStatusType = (status: string): any => {
  const map: Record<string, string> = {
    OPEN: 'primary',
    LOCKED: 'warning',
    CLOSED: 'success'
  }
  return map[status] || 'info'
}

const getIssueTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    OPEN_DOCUMENTS: t('financeReportPages.periods.issue.openDocuments'),
    INVENTORY_FINANCE_RECONCILIATION: t('financeReportPages.periods.issue.inventoryFinanceReconciliation'),
    VOUCHER_ENTRY_MISSING: t('financeReportPages.periods.issue.voucherEntryMissing'),
    VOUCHER_UNBALANCED: t('financeReportPages.periods.issue.voucherUnbalanced'),
    PAYMENT_ALLOCATION_MISMATCH: t('financeReportPages.periods.issue.paymentAllocationMismatch'),
    RECEIPT_ALLOCATION_MISMATCH: t('financeReportPages.periods.issue.receiptAllocationMismatch'),
    SETTLEMENT_AMOUNT_INVALID: t('financeReportPages.periods.issue.settlementAmountInvalid'),
    BANK_STATEMENT_UNMATCHED: t('financeReportPages.periods.issue.bankStatementUnmatched'),
    INVENTORY_BALANCE_NEGATIVE: t('financeReportPages.periods.issue.inventoryBalanceNegative')
  }
  return map[type] || type
}

const getIssueSeverity = (type: string): any => {
  return type === 'INVENTORY_FINANCE_RECONCILIATION' ? 'danger' : 'warning'
}

const getDifferenceTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    INVENTORY_ONLY: t('financeReportPages.periods.difference.inventoryOnly'),
    FINANCE_ONLY: t('financeReportPages.periods.difference.financeOnly'),
    AMOUNT_MISMATCH: t('financeReportPages.periods.difference.amountMismatch')
  }
  return map[type] || type
}

const getDifferenceTypeTag = (type: string): any => {
  const map: Record<string, string> = {
    INVENTORY_ONLY: 'warning',
    FINANCE_ONLY: 'info',
    AMOUNT_MISMATCH: 'danger'
  }
  return map[type] || 'info'
}

const getSourceTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    PURCHASE_RECEIPT: t('financeReportPages.periods.source.purchaseReceipt'),
    PURCHASE_RETURN: t('financeReportPages.periods.source.purchaseReturn'),
    SALES_DELIVERY: t('financeReportPages.periods.source.salesDelivery'),
    SALES_RETURN: t('financeReportPages.periods.source.salesReturn'),
    INVENTORY_ADJUSTMENT: t('financeReportPages.periods.source.inventoryAdjustment'),
    INVENTORY_TRANSFER: t('financeReportPages.periods.source.inventoryTransfer')
  }
  return map[type] || type || '-'
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.period-page {
  padding: 20px;
}

.toolbar-card,
.table-card {
  margin-bottom: 20px;
}

.toolbar-card :deep(.el-card__body) {
  padding-bottom: 0;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.status-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(140px, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.summary-item {
  min-height: 78px;
  padding: 16px 18px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
}

.summary-label {
  font-size: 13px;
  color: #6b7280;
}

.summary-value {
  font-size: 26px;
  line-height: 1;
  color: #111827;
}

.summary-value.open {
  color: #2563eb;
}

.summary-value.locked {
  color: #d97706;
}

.summary-value.closed {
  color: #059669;
}

.period-month {
  font-weight: 600;
  color: #111827;
}

.dialog-table {
  margin-top: 16px;
}

.reconciliation-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(140px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.reconciliation-item {
  min-height: 72px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  background: #ffffff;
}

.reconciliation-item span {
  font-size: 12px;
  color: #6b7280;
}

.reconciliation-item strong {
  font-size: 18px;
  color: #111827;
}

.difference-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.balanced {
  color: #059669;
  font-weight: 600;
}

.unbalanced {
  color: #dc2626;
  font-weight: 600;
}

.difference-detail {
  min-height: 320px;
}

.detail-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.detail-source-no {
  font-size: 18px;
  line-height: 1.3;
  font-weight: 700;
  color: #111827;
  overflow-wrap: anywhere;
}

.detail-source-type {
  margin-top: 4px;
  font-size: 13px;
  color: #6b7280;
}

.detail-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(140px, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.detail-summary-item {
  min-height: 68px;
  padding: 12px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
}

.detail-summary-item span {
  font-size: 12px;
  color: #6b7280;
}

.detail-summary-item strong {
  font-size: 18px;
  color: #111827;
}

.detail-section {
  margin-top: 20px;
}

.section-title {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}

.wizard-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 420px;
}

.wizard-panel {
  margin-top: 8px;
}

.wizard-alert {
  margin-top: 16px;
}

.wizard-refresh {
  margin-top: 12px;
}

.wizard-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 8px;
}

.wizard-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 960px) {
  .status-overview,
  .reconciliation-summary,
  .detail-summary {
    grid-template-columns: repeat(2, minmax(140px, 1fr));
  }
}

@media (max-width: 640px) {
  .period-page {
    padding: 12px;
  }

  .status-overview,
  .reconciliation-summary,
  .detail-summary {
    grid-template-columns: 1fr;
  }

  .detail-heading {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
