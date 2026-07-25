<template>
  <div class="page">
    <el-card shadow="never">
      <div class="toolbar">
        <div>
          <b>{{ t('inventoryMrp.title') }}</b>
          <span class="tip">{{ t('inventoryMrp.description') }}</span>
        </div>
        <div class="actions">
          <el-button @click="loadHistory">{{ t('inventoryMrp.history') }}</el-button>
          <el-button v-permission="'inventory:mrp:run'" type="primary" :loading="loading" @click="runMrp">
            {{ t('inventoryMrp.run') }}
          </el-button>
        </div>
      </div>
    </el-card>

    <el-alert
      v-if="result"
      :title="t('inventoryMrp.summary', {
        date: result.asOfDate,
        purchaseCount: result.purchaseCount,
        productionCount: result.productionCount
      }) + (result.runNo ? ` · ${result.runNo}` : '')"
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
            <el-table-column prop="netQty" :label="t('inventoryMrp.netQty')" width="90" align="right" />
            <el-table-column prop="status" :label="t('inventoryMrp.lineStatus')" width="100" />
            <el-table-column prop="convertedBizNo" :label="t('inventoryMrp.convertedDoc')" min-width="120" />
            <el-table-column prop="reason" :label="t('inventoryMrp.reason')" min-width="120" show-overflow-tooltip />
            <el-table-column :label="t('inventoryMrp.actions')" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'OPEN'"
                  v-permission="'inventory:mrp:convert'"
                  link
                  type="primary"
                  :loading="convertingId === String(row.id)"
                  @click="convertLine(row, 'PRODUCTION')"
                >
                  {{ t('inventoryMrp.convertMo') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>{{ t('inventoryMrp.purchaseSuggestions') }}</template>
          <el-table :data="result?.purchaseLines || []" border stripe max-height="480">
            <el-table-column prop="productCode" :label="t('inventoryMrp.productCode')" width="110" />
            <el-table-column prop="productName" :label="t('inventoryMrp.productName')" min-width="120" />
            <el-table-column prop="netQty" :label="t('inventoryMrp.netQty')" width="90" align="right" />
            <el-table-column prop="status" :label="t('inventoryMrp.lineStatus')" width="100" />
            <el-table-column prop="convertedBizNo" :label="t('inventoryMrp.convertedDoc')" min-width="120" />
            <el-table-column prop="reason" :label="t('inventoryMrp.reason')" min-width="120" show-overflow-tooltip />
            <el-table-column :label="t('inventoryMrp.actions')" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'OPEN'"
                  v-permission="'inventory:mrp:convert'"
                  link
                  type="primary"
                  :loading="convertingId === String(row.id)"
                  @click="convertLine(row, 'PURCHASE')"
                >
                  {{ t('inventoryMrp.convertPo') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>{{ t('inventoryMrp.historyTitle') }}</template>
      <el-table v-loading="historyLoading" :data="history" border stripe>
        <el-table-column prop="runNo" :label="t('inventoryMrp.runNo')" min-width="140" />
        <el-table-column prop="asOfDate" :label="t('inventoryMrp.asOfDate')" width="120" />
        <el-table-column prop="status" :label="t('inventoryMrp.status')" width="100" />
        <el-table-column prop="purchaseCount" :label="t('inventoryMrp.purchaseCount')" width="100" align="right" />
        <el-table-column prop="productionCount" :label="t('inventoryMrp.productionCount')" width="100" align="right" />
        <el-table-column prop="createdTime" :label="t('inventoryMrp.createdTime')" min-width="170" />
        <el-table-column :label="t('inventoryMrp.actions')" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRun(row.id)">{{ t('inventoryMrp.open') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  convertMrpLine,
  getMrpRun,
  getMrpRuns,
  runMrpPlan,
  type MrpRunResult,
  type MrpRunSummary,
  type MrpSuggestionLine
} from '@/api/inventory'

const { t } = useI18n()
const loading = ref(false)
const historyLoading = ref(false)
const convertingId = ref<string | null>(null)
const result = ref<MrpRunResult>()
const history = ref<MrpRunSummary[]>([])

const runMrp = async () => {
  loading.value = true
  try {
    result.value = await runMrpPlan()
    ElMessage.success(t('inventoryMrp.message.succeeded'))
    await loadHistory()
  } catch {
    ElMessage.error(t('inventoryMrp.message.failed'))
  } finally {
    loading.value = false
  }
}

const loadHistory = async () => {
  historyLoading.value = true
  try {
    const page = await getMrpRuns({ pageNo: 1, pageSize: 20 })
    history.value = page.records || []
  } catch {
    // interceptor surfaces error
  } finally {
    historyLoading.value = false
  }
}

const openRun = async (id: string | number) => {
  try {
    result.value = await getMrpRun(id)
  } catch {
    ElMessage.error(t('inventoryMrp.message.loadFailed'))
  }
}

const convertLine = async (row: MrpSuggestionLine, type: 'PURCHASE' | 'PRODUCTION') => {
  if (!result.value?.id || row.id == null) return
  convertingId.value = String(row.id)
  try {
    const converted = await convertMrpLine(result.value.id, row.id)
    ElMessage.success(
      type === 'PURCHASE'
        ? t('inventoryMrp.message.convertedPo', { orderNo: converted.convertedBizNo || '' })
        : t('inventoryMrp.message.convertedMo', { orderNo: converted.convertedBizNo || '' })
    )
    result.value = await getMrpRun(result.value.id)
    await loadHistory()
  } catch {
    ElMessage.error(t('inventoryMrp.message.convertFailed'))
  } finally {
    convertingId.value = null
  }
}

onMounted(() => {
  loadHistory()
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.actions { display: flex; gap: 8px; }
.tip { margin-left: 12px; color: #6b7280; font-size: 12px; }
</style>
