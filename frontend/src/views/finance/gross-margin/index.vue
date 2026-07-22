<template>
  <div class="page">
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="期间">
          <el-date-picker
            v-model="range"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadData">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-if="summary" class="cards">
      <div class="card"><div class="l">销售额</div><div class="v">{{ money(summary.salesAmount) }}</div></div>
      <div class="card"><div class="l">成本(进价近似)</div><div class="v">{{ money(summary.costAmount) }}</div></div>
      <div class="card"><div class="l">毛利</div><div class="v">{{ money(summary.grossMargin) }}</div></div>
      <div class="card"><div class="l">毛利率%</div><div class="v">{{ summary.marginRate }}</div></div>
    </div>

    <el-card v-if="summary" shadow="never">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="成本按商品采购价×发货数量近似估算，非标准成本核算。"
        style="margin-bottom: 12px"
      />
      <el-table :data="summary.lines" border stripe>
        <el-table-column prop="productCode" label="编码" width="120" />
        <el-table-column prop="productName" label="品名" min-width="160" />
        <el-table-column prop="salesQty" label="销量" width="100" align="right" />
        <el-table-column prop="salesAmount" label="销售额" width="120" align="right">
          <template #default="{ row }">{{ money(row.salesAmount) }}</template>
        </el-table-column>
        <el-table-column prop="costAmount" label="成本" width="120" align="right">
          <template #default="{ row }">{{ money(row.costAmount) }}</template>
        </el-table-column>
        <el-table-column prop="grossMargin" label="毛利" width="120" align="right">
          <template #default="{ row }">{{ money(row.grossMargin) }}</template>
        </el-table-column>
        <el-table-column prop="marginRate" label="毛利率%" width="100" align="right" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getGrossMarginSummary, type GrossMarginSummary } from '@/api/finance'
import { formatLocalizedNumber } from '@/utils/locale'

const range = ref<string[]>([])
const loading = ref(false)
const summary = ref<GrossMarginSummary>()
const money = (v: number) => formatLocalizedNumber(Number(v || 0), { minimumFractionDigits: 2, maximumFractionDigits: 2 })

const loadData = async () => {
  if (!range.value || range.value.length !== 2) {
    ElMessage.warning('请选择日期区间')
    return
  }
  loading.value = true
  try {
    summary.value = await getGrossMarginSummary(range.value[0], range.value[1])
  } catch {
    ElMessage.error('加载毛利失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  const now = new Date()
  const from = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10)
  range.value = [from, now.toISOString().slice(0, 10)]
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
