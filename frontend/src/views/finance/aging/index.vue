<template>
  <div class="aging-page">
    <el-card shadow="never" class="toolbar-card">
      <el-form inline>
        <el-form-item :label="t('financeAging.asOfDate')">
          <el-date-picker
            v-model="asOfDate"
            type="date"
            value-format="YYYY-MM-DD"
            :placeholder="t('financeAging.todayPlaceholder')"
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="loadData">{{ t('financeAging.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('financeAging.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="summary-row">
      <div class="summary-card ar">
        <div class="label">{{ t('financeAging.receivableTotal') }}</div>
        <div class="value">{{ formatMoney(summary?.receivableTotal) }}</div>
        <div class="sub">{{ t('financeAging.asOfDateValue', { date: formatDate(summary?.asOfDate || asOfDate || today) }) }}</div>
      </div>
      <div class="summary-card ap">
        <div class="label">{{ t('financeAging.payableTotal') }}</div>
        <div class="value">{{ formatMoney(summary?.payableTotal) }}</div>
        <div class="sub">{{ t('financeAging.outstandingOnly') }}</div>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" class="table-card">
          <template #header>
            <div class="card-header">
              <span>{{ t('financeAging.receivableBuckets') }}</span>
            </div>
          </template>
          <el-table v-loading="loading" :data="summary?.receivableBuckets || []" border stripe>
            <el-table-column prop="label" :label="t('financeAging.bucket')" min-width="120">
              <template #default="{ row }">{{ agingBucketLabel(row.code, row.label) }}</template>
            </el-table-column>
            <el-table-column prop="count" :label="t('financeAging.count')" width="90" align="right">
              <template #default="{ row }">{{ formatNumber(row.count) }}</template>
            </el-table-column>
            <el-table-column prop="amount" :label="t('financeAging.amount')" min-width="140" align="right">
              <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="table-card">
          <template #header>
            <div class="card-header">
              <span>{{ t('financeAging.payableBuckets') }}</span>
            </div>
          </template>
          <el-table v-loading="loading" :data="summary?.payableBuckets || []" border stripe>
            <el-table-column prop="label" :label="t('financeAging.bucket')" min-width="120">
              <template #default="{ row }">{{ agingBucketLabel(row.code, row.label) }}</template>
            </el-table-column>
            <el-table-column prop="count" :label="t('financeAging.count')" width="90" align="right">
              <template #default="{ row }">{{ formatNumber(row.count) }}</template>
            </el-table-column>
            <el-table-column prop="amount" :label="t('financeAging.amount')" min-width="140" align="right">
              <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="list-row">
      <el-col :span="12">
        <el-card shadow="never" class="table-card">
          <template #header>
            <div class="card-header">
              <span>{{ t('financeAging.overdueReceivables') }}</span>
              <el-button text type="primary" @click="goReceivables">{{ t('financeAging.receivablesLedger') }}</el-button>
            </div>
          </template>
          <el-table v-loading="loading" :data="summary?.overdueReceivables || []" border stripe max-height="420">
            <el-table-column prop="docNo" :label="t('financeAging.receivableNo')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="partnerName" :label="t('financeAging.customer')" min-width="120" show-overflow-tooltip />
            <el-table-column prop="bizDate" :label="t('financeAging.bizDate')" width="120">
              <template #default="{ row }">{{ formatDate(row.bizDate) }}</template>
            </el-table-column>
            <el-table-column prop="agingDays" :label="t('financeAging.agingDays')" width="90" align="right">
              <template #default="{ row }">{{ formatNumber(row.agingDays) }}</template>
            </el-table-column>
            <el-table-column prop="remainingAmount" :label="t('financeAging.outstandingAmount')" width="140" align="right">
              <template #default="{ row }">{{ formatMoney(row.remainingAmount) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="table-card">
          <template #header>
            <div class="card-header">
              <span>{{ t('financeAging.overduePayables') }}</span>
              <el-button text type="primary" @click="goPayables">{{ t('financeAging.payablesLedger') }}</el-button>
            </div>
          </template>
          <el-table v-loading="loading" :data="summary?.overduePayables || []" border stripe max-height="420">
            <el-table-column prop="docNo" :label="t('financeAging.payableNo')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="partnerName" :label="t('financeAging.supplier')" min-width="120" show-overflow-tooltip />
            <el-table-column prop="bizDate" :label="t('financeAging.bizDate')" width="120">
              <template #default="{ row }">{{ formatDate(row.bizDate) }}</template>
            </el-table-column>
            <el-table-column prop="agingDays" :label="t('financeAging.agingDays')" width="90" align="right">
              <template #default="{ row }">{{ formatNumber(row.agingDays) }}</template>
            </el-table-column>
            <el-table-column prop="remainingAmount" :label="t('financeAging.outstandingAmount')" width="140" align="right">
              <template #default="{ row }">{{ formatMoney(row.remainingAmount) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getFinanceAgingSummary, type FinanceAgingSummary } from '@/api/finance'
import {
  formatBusinessDate,
  formatLocalizedCurrency,
  formatLocalizedDate,
  formatLocalizedNumber
} from '@/utils/locale'

const router = useRouter()
const { t } = useI18n()
const loading = ref(false)
const summary = ref<FinanceAgingSummary>()
const today = formatBusinessDate()
const asOfDate = ref(today)

const formatMoney = (value?: number) => formatLocalizedCurrency(Number(value ?? 0))
const formatNumber = (value?: number) => formatLocalizedNumber(Number(value ?? 0))
const formatDate = (value?: string) => formatLocalizedDate(value) || '-'
const agingBucketLabel = (code: string, fallback: string) => {
  const keyMap: Record<string, string> = {
    D0_30: 'financeAging.bucketLabel.d0_30',
    D31_60: 'financeAging.bucketLabel.d31_60',
    D61_90: 'financeAging.bucketLabel.d61_90',
    D90_PLUS: 'financeAging.bucketLabel.d90Plus'
  }
  return keyMap[code] ? t(keyMap[code]) : fallback
}

const loadData = async () => {
  loading.value = true
  try {
    summary.value = await getFinanceAgingSummary(asOfDate.value || undefined)
  } catch {
    ElMessage.error(t('financeAging.message.loadFailed'))
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  asOfDate.value = today
  loadData()
}

const goReceivables = () => router.push('/finance/receivables')
const goPayables = () => router.push('/finance/payables')

onMounted(loadData)
</script>

<style scoped>
.aging-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.toolbar-card :deep(.el-card__body) {
  padding: 16px 16px 4px;
}
.summary-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.summary-card {
  border-radius: 10px;
  padding: 18px 20px;
  color: #111827;
  border: 1px solid #e5e7eb;
  background: linear-gradient(135deg, #f8fafc 0%, #ffffff 70%);
}
.summary-card.ar {
  border-color: #bfdbfe;
  background: linear-gradient(135deg, #eff6ff 0%, #ffffff 75%);
}
.summary-card.ap {
  border-color: #fde68a;
  background: linear-gradient(135deg, #fffbeb 0%, #ffffff 75%);
}
.summary-card .label {
  font-size: 13px;
  color: #6b7280;
}
.summary-card .value {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 700;
}
.summary-card .sub {
  margin-top: 6px;
  font-size: 12px;
  color: #9ca3af;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.list-row {
  margin-top: 0;
}
@media (max-width: 960px) {
  .summary-row {
    grid-template-columns: 1fr;
  }
}
</style>
