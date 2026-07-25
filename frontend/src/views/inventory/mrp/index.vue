<template>
  <div class="page">
    <el-card shadow="never">
      <div class="toolbar">
        <div>
          <b>{{ t('inventoryMrp.title') }}</b>
          <span class="tip">{{ t('inventoryMrp.description') }}</span>
        </div>
        <el-button v-permission="'inventory:mrp:run'" type="primary" :loading="loading" @click="runMrp">
          {{ t('inventoryMrp.run') }}
        </el-button>
      </div>
    </el-card>

    <el-alert
      v-if="result"
      :title="t('inventoryMrp.summary', { date: result.asOfDate, purchaseCount: result.purchaseCount, productionCount: result.productionCount })"
      type="success"
      :closable="false"
      show-icon
    />

    <el-row :gutter="12">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>{{ t('inventoryMrp.productionSuggestions') }}</template>
          <el-table :data="result?.productionLines || []" border stripe max-height="480">
            <el-table-column prop="productCode" :label="t('inventoryMrp.productCode')" width="110" />
            <el-table-column prop="productName" :label="t('inventoryMrp.productName')" min-width="120" />
            <el-table-column prop="demandQty" :label="t('inventoryMrp.demandQty')" width="90" align="right" />
            <el-table-column prop="onHandQty" :label="t('inventoryMrp.onHandQty')" width="90" align="right" />
            <el-table-column prop="openSupplyQty" :label="t('inventoryMrp.openSupplyQty')" width="100" align="right" />
            <el-table-column prop="netQty" :label="t('inventoryMrp.netQty')" width="90" align="right" />
            <el-table-column prop="bomId" label="BOM" width="120" />
            <el-table-column prop="reason" :label="t('inventoryMrp.reason')" min-width="140" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>{{ t('inventoryMrp.purchaseSuggestions') }}</template>
          <el-table :data="result?.purchaseLines || []" border stripe max-height="480">
            <el-table-column prop="productCode" :label="t('inventoryMrp.productCode')" width="110" />
            <el-table-column prop="productName" :label="t('inventoryMrp.productName')" min-width="120" />
            <el-table-column prop="demandQty" :label="t('inventoryMrp.demandQty')" width="90" align="right" />
            <el-table-column prop="onHandQty" :label="t('inventoryMrp.onHandQty')" width="90" align="right" />
            <el-table-column prop="openSupplyQty" :label="t('inventoryMrp.openSupplyQty')" width="100" align="right" />
            <el-table-column prop="netQty" :label="t('inventoryMrp.netQty')" width="90" align="right" />
            <el-table-column prop="reason" :label="t('inventoryMrp.reason')" min-width="140" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { runMrpPlan, type MrpRunResult } from '@/api/inventory'

const { t } = useI18n()
const loading = ref(false)
const result = ref<MrpRunResult>()

const runMrp = async () => {
  loading.value = true
  try {
    result.value = await runMrpPlan()
    ElMessage.success(t('inventoryMrp.message.succeeded'))
  } catch {
    ElMessage.error(t('inventoryMrp.message.failed'))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.tip { margin-left: 12px; color: #6b7280; font-size: 12px; }
</style>
