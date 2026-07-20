<template>
  <div class="app-container period-page">
    <el-card shadow="never" class="toolbar-card">
      <el-form inline>
        <el-form-item label="会计年度">
          <el-input-number
            v-model="queryYear"
            :min="2000"
            :max="2199"
            :controls="false"
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button
            v-permission="'finance:period:manage'"
            type="success"
            :icon="Calendar"
            :loading="generateLoading"
            @click="handleGenerate"
          >
            生成年期间
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
          <span>会计期间管理</span>
          <el-button :icon="Refresh" text @click="loadData">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="periodMonth" label="期间" width="120" fixed>
          <template #default="{ row }">
            <span class="period-month">{{ formatMonth(row.periodMonth) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="periodYear" label="年度" width="100" align="center" />
        <el-table-column prop="startDate" label="开始日期" width="130" align="center" />
        <el-table-column prop="endDate" label="结束日期" width="130" align="center" />
        <el-table-column prop="status" label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lockedTime" label="锁定时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.lockedTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="closedTime" label="结账时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.closedTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="reopenedTime" label="解锁时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.reopenedTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="380" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleCheck(row)">
              检查
            </el-button>
            <el-button
              v-if="row.status === 'OPEN' || row.status === 'LOCKED'"
              v-permission="'finance:period:close'"
              type="success"
              link
              :icon="Guide"
              @click="openWizard(row)"
            >
              结账向导
            </el-button>
            <el-button type="primary" link :icon="DataAnalysis" @click="openReconciliation(row)">
              对账
            </el-button>
            <el-button
              v-if="row.status === 'OPEN'"
              v-permission="'finance:period:close'"
              type="warning"
              link
              :icon="Lock"
              @click="handleLock(row)"
            >
              锁定
            </el-button>
            <el-button
              v-if="row.status === 'LOCKED'"
              v-permission="'finance:period:close'"
              type="success"
              link
              :icon="CircleCheck"
              @click="handleClose(row)"
            >
              结账
            </el-button>
            <el-button
              v-if="row.status === 'LOCKED'"
              v-permission="'finance:period:reopen'"
              type="danger"
              link
              :icon="RefreshLeft"
              @click="handleUnlock(row)"
            >
              解锁
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="closeCheckVisible"
      title="月结检查"
      width="820px"
      class="period-dialog"
    >
      <div v-loading="closeCheckLoading">
        <el-alert
          v-if="closeCheckResult"
          :title="closeCheckResult.passed ? '检查通过，可以继续锁定或结账' : '检查未通过，请先处理以下问题'"
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
          <el-table-column prop="category" label="分类" width="90" />
          <el-table-column prop="title" label="检查项" width="160" />
          <el-table-column label="结果" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.passed ? 'success' : 'danger'" size="small">
                {{ row.passed ? '通过' : '阻塞' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="280" show-overflow-tooltip />
          <el-table-column prop="metric" label="数量/金额" width="120" align="right">
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
          <el-table-column prop="type" label="问题类型" width="210">
            <template #default="{ row }">
              <el-tag :type="getIssueSeverity(row.type)" size="small">
                {{ getIssueTypeLabel(row.type) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="260" />
          <el-table-column prop="amount" label="数量/金额" width="140" align="right">
            <template #default="{ row }">
              {{ formatAmount(row.amount) }}
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-else-if="closeCheckResult && closeCheckResult.passed" description="没有发现阻塞项" />
      </div>
      <template #footer>
        <el-button @click="closeCheckVisible = false">关闭</el-button>
        <el-button
          v-if="activePeriod?.status === 'OPEN'"
          v-permission="'finance:period:close'"
          type="warning"
          :disabled="!closeCheckResult?.passed"
          @click="activePeriod && handleLock(activePeriod)"
        >
          检查通过后锁定
        </el-button>
        <el-button
          v-if="activePeriod?.status === 'LOCKED'"
          v-permission="'finance:period:close'"
          type="success"
          @click="activePeriod && handleClose(activePeriod)"
        >
          继续结账
        </el-button>
      </template>
    </el-dialog>

    <!-- 结账向导 -->
    <el-drawer
      v-model="wizardVisible"
      :title="`期间结账向导 · ${wizardPeriod?.periodMonth || ''}`"
      size="560px"
      destroy-on-close
    >
      <div v-loading="wizardLoading" class="wizard-body">
        <el-steps :active="wizardStep" finish-status="success" align-center>
          <el-step title="概览" />
          <el-step title="月结检查" />
          <el-step title="锁定/结账" />
        </el-steps>

        <section v-if="wizardStep === 0" class="wizard-panel">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="期间">{{ wizardPeriod?.periodMonth }}</el-descriptions-item>
            <el-descriptions-item label="起止">
              {{ wizardPeriod?.startDate }} ~ {{ wizardPeriod?.endDate }}
            </el-descriptions-item>
            <el-descriptions-item label="当前状态">
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
            title="建议顺序：生成年期间 → 跑月结检查 → 锁定 → 结账。锁定前必须检查通过。"
          />
        </section>

        <section v-else-if="wizardStep === 1" class="wizard-panel">
          <el-alert
            v-if="wizardCheck"
            :title="wizardCheck.passed ? '全部检查通过' : `发现 ${wizardCheck.issues.length} 项阻塞`"
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
            <el-table-column prop="category" label="分类" width="80" />
            <el-table-column prop="title" label="检查项" width="140" />
            <el-table-column label="结果" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.passed ? 'success' : 'danger'" size="small">
                  {{ row.passed ? '通过' : '阻塞' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="说明" min-width="200" show-overflow-tooltip />
          </el-table>
          <el-button class="wizard-refresh" :icon="Refresh" @click="runWizardCheck">重新检查</el-button>
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
              锁定期间
            </el-button>
            <el-button
              v-if="wizardPeriod?.status === 'LOCKED'"
              v-permission="'finance:period:close'"
              type="success"
              :loading="wizardActionLoading"
              @click="wizardClose"
            >
              确认结账
            </el-button>
            <el-button
              v-if="wizardPeriod?.status === 'LOCKED'"
              v-permission="'finance:period:reopen'"
              type="danger"
              plain
              :loading="wizardActionLoading"
              @click="wizardUnlock"
            >
              解锁
            </el-button>
          </div>
        </section>
      </div>
      <template #footer>
        <div class="wizard-footer">
          <el-button :disabled="wizardStep === 0" @click="wizardStep = Math.max(0, wizardStep - 1)">上一步</el-button>
          <el-button
            v-if="wizardStep < 2"
            type="primary"
            @click="nextWizardStep"
          >
            下一步
          </el-button>
          <el-button v-else @click="wizardVisible = false">完成</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      v-model="reconciliationVisible"
      title="库存财务对账"
      width="920px"
      class="period-dialog"
    >
      <div v-loading="reconciliationLoading">
        <div v-if="reconciliationResult" class="reconciliation-summary">
          <div class="reconciliation-item">
            <span>库存净额</span>
            <strong>{{ formatAmount(reconciliationResult.inventoryNetAmount) }}</strong>
          </div>
          <div class="reconciliation-item">
            <span>财务库存科目净额</span>
            <strong>{{ formatAmount(reconciliationResult.financeInventoryNetAmount) }}</strong>
          </div>
          <div class="reconciliation-item">
            <span>差异金额</span>
            <strong :class="reconciliationResult.balanced ? 'balanced' : 'unbalanced'">
              {{ formatAmount(reconciliationResult.differenceAmount) }}
            </strong>
          </div>
          <div class="reconciliation-item">
            <span>对账状态</span>
            <el-tag :type="reconciliationResult.balanced ? 'success' : 'danger'">
              {{ reconciliationResult.balanced ? '平衡' : '不平衡' }}
            </el-tag>
          </div>
        </div>

        <div class="difference-toolbar">
          <el-select
            v-model="differenceType"
            placeholder="差异类型"
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
          <el-button :icon="Refresh" @click="loadDifferences">刷新差异</el-button>
        </div>

        <el-table
          v-loading="differenceLoading"
          :data="differences"
          border
          stripe
          class="dialog-table"
        >
          <el-table-column prop="sourceNo" label="来源单号" min-width="160" show-overflow-tooltip />
          <el-table-column prop="sourceType" label="来源类型" width="130">
            <template #default="{ row }">
              {{ getSourceTypeLabel(row.sourceType) }}
            </template>
          </el-table-column>
          <el-table-column prop="inventoryAmount" label="库存金额" width="130" align="right">
            <template #default="{ row }">
              {{ formatAmount(row.inventoryAmount) }}
            </template>
          </el-table-column>
          <el-table-column prop="financeAmount" label="财务金额" width="130" align="right">
            <template #default="{ row }">
              {{ formatAmount(row.financeAmount) }}
            </template>
          </el-table-column>
          <el-table-column prop="differenceAmount" label="差异金额" width="130" align="right">
            <template #default="{ row }">
              <span :class="Number(row.differenceAmount) === 0 ? 'balanced' : 'unbalanced'">
                {{ formatAmount(row.differenceAmount) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="differenceType" label="差异类型" width="140">
            <template #default="{ row }">
              <el-tag :type="getDifferenceTypeTag(row.differenceType)" size="small">
                {{ getDifferenceTypeLabel(row.differenceType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="明细" width="90" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link :icon="View" @click="openDifferenceDetail(row)">
                明细
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="reconciliationVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="differenceDetailVisible"
      title="对账差异明细"
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
              <span>库存金额</span>
              <strong>{{ formatAmount(differenceDetail.inventoryAmount) }}</strong>
            </div>
            <div class="detail-summary-item">
              <span>财务金额</span>
              <strong>{{ formatAmount(differenceDetail.financeAmount) }}</strong>
            </div>
            <div class="detail-summary-item">
              <span>差异金额</span>
              <strong :class="Number(differenceDetail.differenceAmount) === 0 ? 'balanced' : 'unbalanced'">
                {{ formatAmount(differenceDetail.differenceAmount) }}
              </strong>
            </div>
          </div>

          <section class="detail-section">
            <div class="section-title">库存流水</div>
            <el-table
              :data="differenceDetail.inventoryTransactions"
              border
              stripe
              empty-text="无库存流水"
            >
              <el-table-column prop="occurredTime" label="发生时间" width="170">
                <template #default="{ row }">
                  {{ formatDateTime(row.occurredTime) }}
                </template>
              </el-table-column>
              <el-table-column prop="direction" label="方向" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.direction === 'IN' ? 'success' : 'warning'" size="small">
                    {{ row.direction === 'IN' ? '入库' : '出库' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="qty" label="数量" width="120" align="right">
                <template #default="{ row }">
                  {{ formatQty(row.qty) }}
                </template>
              </el-table-column>
              <el-table-column prop="amount" label="金额" width="130" align="right">
                <template #default="{ row }">
                  {{ formatAmount(row.amount) }}
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
            </el-table>
          </section>

          <section class="detail-section">
            <div class="section-title">财务凭证分录</div>
            <el-table
              :data="differenceDetail.voucherEntries"
              border
              stripe
              empty-text="无财务分录"
            >
              <el-table-column prop="voucherNo" label="凭证号" min-width="150" show-overflow-tooltip />
              <el-table-column prop="bizDate" label="业务日期" width="120" align="center" />
              <el-table-column prop="lineNo" label="行号" width="80" align="center" />
              <el-table-column label="科目" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.subjectCode }} {{ row.subjectName }}
                </template>
              </el-table-column>
              <el-table-column prop="debitAmount" label="借方" width="130" align="right">
                <template #default="{ row }">
                  {{ formatAmount(row.debitAmount) }}
                </template>
              </el-table-column>
              <el-table-column prop="creditAmount" label="贷方" width="130" align="right">
                <template #default="{ row }">
                  {{ formatAmount(row.creditAmount) }}
                </template>
              </el-table-column>
              <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
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

const differenceTypeOptions = [
  { label: '仅库存有记录', value: 'INVENTORY_ONLY' },
  { label: '仅财务有记录', value: 'FINANCE_ONLY' },
  { label: '金额不一致', value: 'AMOUNT_MISMATCH' }
]

const statusSummary = computed(() => {
  const count = (status: string) => tableData.value.filter(item => item.status === status).length
  return [
    { key: 'open', label: '打开期间', value: count('OPEN') },
    { key: 'locked', label: '锁定期间', value: count('LOCKED') },
    { key: 'closed', label: '已结账期间', value: count('CLOSED') },
    { key: 'total', label: '期间总数', value: tableData.value.length }
  ]
})

const loadData = async () => {
  loading.value = true
  try {
    const periods = await getAccountPeriods(queryYear.value)
    tableData.value = periods || []
  } catch (error) {
    ElMessage.error('加载会计期间失败')
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
      `确定生成 ${queryYear.value} 年 12 个会计期间吗？已存在的期间会保留。`,
      '生成会计期间',
      { type: 'warning' }
    )
    generateLoading.value = true
    const periods = await generateAccountPeriods(queryYear.value)
    tableData.value = periods || []
    ElMessage.success('会计期间生成成功')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('生成会计期间失败')
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
    ElMessage.error('月结检查失败')
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
    ElMessage.error('月结检查失败')
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
  if (wizardPeriod.value?.status === 'CLOSED') return '期间已结账'
  if (wizardPeriod.value?.status === 'LOCKED') return '期间已锁定，可确认结账'
  if (wizardCheck.value?.passed) return '检查通过，可锁定期间'
  return '请先处理阻塞项再锁定'
})

const wizardActionSubtitle = computed(() => {
  if (!wizardPeriod.value) return ''
  if (wizardPeriod.value.status === 'CLOSED') {
    return `${wizardPeriod.value.periodMonth} 已结账，仅可查询。`
  }
  if (wizardPeriod.value.status === 'LOCKED') {
    return '结账后本期业务将不可再处理。'
  }
  return wizardCheck.value?.passed
    ? '锁定后仍可解锁（仅最新锁定期间），结账前请再次确认。'
    : '可返回上一步查看检查明细。'
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
      ElMessage.warning('月结检查未通过，不能锁定')
      return
    }

    await ElMessageBox.confirm(`确定锁定 ${row.periodMonth} 会计期间吗？`, '锁定期间', {
      type: 'warning'
    })
    await lockAccountPeriod(row.id)
    ElMessage.success('会计期间已锁定')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('锁定会计期间失败')
    }
  }
}

const handleClose = async (row: AccountPeriod) => {
  // 结账前主动跑月结检查并回显：后端 close() 本身不再校验，带病结账风险需在确认弹窗暴露给用户
  let check: AccountPeriodCloseCheck | undefined
  try {
    check = await checkAccountPeriodClose(row.id)
  } catch (error) {
    ElMessage.error('月结检查失败，暂不能结账')
    return
  }

  if (check && !check.passed) {
    activePeriod.value = row
    closeCheckResult.value = check
    closeCheckVisible.value = true
    try {
      await ElMessageBox.confirm(
        `月结检查发现 ${check.issues.length} 项待处理问题（见“月结检查”弹窗）。` +
          `结账后本期业务将不可再处理，且这些问题会被固化。确定仍要结账 ${row.periodMonth} 吗？`,
        '带风险结账确认',
        { type: 'error', confirmButtonText: '仍要结账', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
      )
    } catch {
      return
    }
  } else {
    try {
      await ElMessageBox.confirm(
        `月结检查通过。确定结账 ${row.periodMonth} 会计期间吗？结账后将不能继续处理本期业务。`,
        '期间结账',
        { type: 'warning' }
      )
    } catch {
      return
    }
  }

  try {
    await closeAccountPeriod(row.id)
    ElMessage.success('会计期间已结账')
    closeCheckVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error('会计期间结账失败')
  }
}

const handleUnlock = async (row: AccountPeriod) => {
  try {
    await ElMessageBox.confirm(
      `确定解锁 ${row.periodMonth} 会计期间吗？` +
        `解锁会把 ${row.periodMonth} 从“已锁定”退回“打开”，本期将重新允许录入和过账。` +
        `注意：后端只允许解锁“最新锁定期间”，已结账期间无法解锁。确定解锁吗？`,
      '解锁期间',
      { type: 'warning', confirmButtonText: '确定解锁', cancelButtonText: '取消' }
    )
    await unlockAccountPeriod(row.id)
    ElMessage.success('会计期间已解锁')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('解锁会计期间失败')
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
    ElMessage.error('加载对账数据失败')
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
    ElMessage.error('加载对账差异失败')
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
    ElMessage.error('加载对账差异明细失败')
  } finally {
    differenceDetailLoading.value = false
  }
}

const formatMonth = (periodMonth: string) => {
  return periodMonth || '-'
}

const formatDateTime = (value?: string) => {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

const formatAmount = (amount?: number | string) => {
  const value = Number(amount ?? 0)
  return Number.isFinite(value)
    ? value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : '0.00'
}

const formatQty = (qty?: number | string) => {
  const value = Number(qty ?? 0)
  return Number.isFinite(value)
    ? value.toLocaleString('zh-CN', { minimumFractionDigits: 4, maximumFractionDigits: 4 })
    : '0.0000'
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    OPEN: '打开',
    LOCKED: '已锁定',
    CLOSED: '已结账'
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
    OPEN_DOCUMENTS: '未完结业务单据',
    INVENTORY_FINANCE_RECONCILIATION: '库存财务不一致',
    VOUCHER_ENTRY_MISSING: '凭证缺少分录',
    VOUCHER_UNBALANCED: '凭证借贷不平',
    PAYMENT_ALLOCATION_MISMATCH: '付款核销不一致',
    RECEIPT_ALLOCATION_MISMATCH: '收款核销不一致',
    SETTLEMENT_AMOUNT_INVALID: '应收应付金额异常',
    BANK_STATEMENT_UNMATCHED: '银行流水未匹配',
    INVENTORY_BALANCE_NEGATIVE: '负库存余额'
  }
  return map[type] || type
}

const getIssueSeverity = (type: string): any => {
  return type === 'INVENTORY_FINANCE_RECONCILIATION' ? 'danger' : 'warning'
}

const getDifferenceTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    INVENTORY_ONLY: '仅库存有记录',
    FINANCE_ONLY: '仅财务有记录',
    AMOUNT_MISMATCH: '金额不一致'
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
    PURCHASE_RECEIPT: '采购收货',
    PURCHASE_RETURN: '采购退货',
    SALES_DELIVERY: '销售发货',
    SALES_RETURN: '销售退货',
    INVENTORY_ADJUSTMENT: '库存调整',
    INVENTORY_TRANSFER: '库存调拨'
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
