<template>
  <div class="page">
    <el-card shadow="never">
      <el-form inline>
        <el-form-item :label="t('financeGrossMargin.period')">
          <el-date-picker
            v-model="range"
            type="daterange"
            value-format="YYYY-MM-DD"
            :start-placeholder="t('financeGrossMargin.startDate')"
            :end-placeholder="t('financeGrossMargin.endDate')"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadData">{{ t('financeGrossMargin.search') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-if="summary" class="cards">
      <div class="card"><div class="l">{{ t('financeGrossMargin.salesAmount') }}</div><div class="v">{{ money(summary.salesAmount) }}</div></div>
      <div class="card"><div class="l">{{ t('financeGrossMargin.costApprox') }}</div><div class="v">{{ money(summary.costAmount) }}</div></div>
      <div class="card"><div class="l">{{ t('financeGrossMargin.grossMargin') }}</div><div class="v">{{ money(summary.grossMargin) }}</div></div>
      <div class="card"><div class="l">{{ t('financeGrossMargin.marginRate') }}</div><div class="v">{{ percentage(summary.marginRate) }}</div></div>
    </div>

    <el-card v-if="summary" shadow="never">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        :title="t('financeGrossMargin.costNotice')"
        style="margin-bottom: 12px"
      />
      <el-table :data="summary.lines" border stripe>
        <el-table-column prop="productCode" :label="t('financeGrossMargin.productCode')" width="120" />
        <el-table-column prop="productName" :label="t('financeGrossMargin.productName')" min-width="160" />
        <el-table-column prop="salesQty" :label="t('financeGrossMargin.salesQuantity')" width="110" align="right">
          <template #default="{ row }">{{ quantity(row.salesQty) }}</template>
        </el-table-column>
        <el-table-column prop="salesAmount" :label="t('financeGrossMargin.salesAmount')" width="140" align="right">
          <template #default="{ row }">{{ money(row.salesAmount) }}</template>
        </el-table-column>
        <el-table-column prop="costAmount" :label="t('financeGrossMargin.costAmount')" width="140" align="right">
          <template #default="{ row }">{{ money(row.costAmount) }}</template>
        </el-table-column>
        <el-table-column prop="grossMargin" :label="t('financeGrossMargin.grossMargin')" width="140" align="right">
          <template #default="{ row }">{{ money(row.grossMargin) }}</template>
        </el-table-column>
        <el-table-column prop="marginRate" :label="t('financeGrossMargin.marginRate')" width="110" align="right">
          <template #default="{ row }">{{ percentage(row.marginRate) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getGrossMarginSummary, type GrossMarginSummary } from '@/api/finance'
import {
  formatLocalizedCurrency,
  formatLocalizedNumber,
  getBusinessMonthDateRange
} from '@/utils/locale'

const { t } = useI18n()
const range = ref<string[]>([])
const loading = ref(false)
const summary = ref<GrossMarginSummary>()
const money = (value?: number) => formatLocalizedCurrency(Number(value ?? 0))
const quantity = (value?: number) => formatLocalizedNumber(Number(value ?? 0), { maximumFractionDigits: 4 })
const percentage = (value?: number) => formatLocalizedNumber(Number(value ?? 0) / 100, {
  style: 'percent',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})

const loadData = async () => {
  if (!range.value || range.value.length !== 2) {
    ElMessage.warning(t('financeGrossMargin.message.selectRange'))
    return
  }
  loading.value = true
  try {
    summary.value = await getGrossMarginSummary(range.value[0], range.value[1])
  } catch {
    ElMessage.error(t('financeGrossMargin.message.loadFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  range.value = getBusinessMonthDateRange()
  loadData()
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.card { border: 1px solid #e5e7eb; border-radius: 8px; padding: 14px; background: #fff; }
.card .l { color: #6b7280; font-size: 12px; }
.card .v { margin-top: 6px; font-size: 22px; font-weight: 700; }
@media (max-width: 960px) { .cards { grid-template-columns: 1fr 1fr; } }
</style>
