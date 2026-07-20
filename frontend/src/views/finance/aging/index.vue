<template>
  <div class="aging-page">
    <el-card shadow="never" class="toolbar-card">
      <el-form inline>
        <el-form-item label="基准日">
          <el-date-picker
            v-model="asOfDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="默认今天"
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="loadData">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="summary-row">
      <div class="summary-card ar">
        <div class="label">应收未结总额</div>
        <div class="value">{{ formatMoney(summary?.receivableTotal) }}</div>
        <div class="sub">基准日 {{ summary?.asOfDate || asOfDate || today }}</div>
      </div>
      <div class="summary-card ap">
        <div class="label">应付未结总额</div>
        <div class="value">{{ formatMoney(summary?.payableTotal) }}</div>
        <div class="sub">仅统计剩余金额 &gt; 0 的未结单据</div>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" class="table-card">
          <template #header>
            <div class="card-header">
              <span>应收账龄分段</span>
            </div>
          </template>
          <el-table v-loading="loading" :data="summary?.receivableBuckets || []" border stripe>
            <el-table-column prop="label" label="账龄段" min-width="120" />
            <el-table-column prop="count" label="笔数" width="90" align="right" />
            <el-table-column prop="amount" label="金额" min-width="140" align="right">
              <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="table-card">
          <template #header>
            <div class="card-header">
              <span>应付账龄分段</span>
            </div>
          </template>
          <el-table v-loading="loading" :data="summary?.payableBuckets || []" border stripe>
            <el-table-column prop="label" label="账龄段" min-width="120" />
            <el-table-column prop="count" label="笔数" width="90" align="right" />
            <el-table-column prop="amount" label="金额" min-width="140" align="right">
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
              <span>应收逾期 TOP20</span>
              <el-button text type="primary" @click="goReceivables">去应收台账</el-button>
            </div>
          </template>
          <el-table v-loading="loading" :data="summary?.overdueReceivables || []" border stripe max-height="420">
            <el-table-column prop="docNo" label="应收单号" min-width="140" show-overflow-tooltip />
            <el-table-column prop="partnerName" label="客户" min-width="120" show-overflow-tooltip />
            <el-table-column prop="bizDate" label="业务日" width="110" />
            <el-table-column prop="agingDays" label="账龄天" width="80" align="right" />
            <el-table-column prop="remainingAmount" label="未结金额" width="120" align="right">
              <template #default="{ row }">{{ formatMoney(row.remainingAmount) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="table-card">
          <template #header>
            <div class="card-header">
              <span>应付逾期 TOP20</span>
              <el-button text type="primary" @click="goPayables">去应付台账</el-button>
            </div>
          </template>
          <el-table v-loading="loading" :data="summary?.overduePayables || []" border stripe max-height="420">
            <el-table-column prop="docNo" label="应付单号" min-width="140" show-overflow-tooltip />
            <el-table-column prop="partnerName" label="供应商" min-width="120" show-overflow-tooltip />
            <el-table-column prop="bizDate" label="业务日" width="110" />
            <el-table-column prop="agingDays" label="账龄天" width="80" align="right" />
            <el-table-column prop="remainingAmount" label="未结金额" width="120" align="right">
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
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getFinanceAgingSummary, type FinanceAgingSummary } from '@/api/finance'

const router = useRouter()
const loading = ref(false)
const summary = ref<FinanceAgingSummary>()
const today = new Date().toISOString().slice(0, 10)
const asOfDate = ref(today)

const formatMoney = (value?: number) =>
  Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

const loadData = async () => {
  loading.value = true
  try {
    summary.value = await getFinanceAgingSummary(asOfDate.value || undefined)
  } catch {
    ElMessage.error('加载账龄分析失败')
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
