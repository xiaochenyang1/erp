<template>
  <div class="app-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div>
          <h2>{{ t('productionSchedule.title') }}</h2>
          <p>{{ t('productionSchedule.description') }}</p>
        </div>
        <el-form inline>
          <el-form-item :label="t('productionSchedule.horizon')">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              :range-separator="t('productionSchedule.rangeSeparator')"
              :start-placeholder="t('productionSchedule.startDate')"
              :end-placeholder="t('productionSchedule.endDate')"
            />
          </el-form-item>
          <el-form-item>
            <el-button :loading="loading" @click="preview">{{ t('productionSchedule.preview') }}</el-button>
            <el-button
              v-permission="'production:schedule:run'"
              type="primary"
              :loading="running"
              @click="execute"
            >{{ t('productionSchedule.run') }}</el-button>
          </el-form-item>
        </el-form>
      </div>
      <el-alert type="info" :closable="false" :title="t('productionSchedule.ruleHint')" />
    </el-card>

    <template v-if="hasResult && result">
      <div class="summary-grid">
        <el-card shadow="never"><el-statistic :title="t('productionSchedule.orders')" :value="result.orderCount" /></el-card>
        <el-card shadow="never"><el-statistic :title="t('productionSchedule.scheduled')" :value="result.scheduledCount" /></el-card>
        <el-card shadow="never"><el-statistic :title="t('productionSchedule.unscheduled')" :value="result.unscheduledCount" /></el-card>
        <el-card shadow="never"><el-statistic :title="t('productionSchedule.delayed')" :value="result.delayedCount" /></el-card>
      </div>

      <el-card shadow="never" class="section-card">
        <template #header>{{ t('productionSchedule.capacityTitle') }}</template>
        <el-table :data="result.workCenters" border stripe>
          <el-table-column prop="workCenterCode" :label="t('productionSchedule.workCenterCode')" width="150" />
          <el-table-column prop="workCenterName" :label="t('productionSchedule.workCenter')" min-width="180" />
          <el-table-column prop="workdayStartTime" :label="t('productionSchedule.startTime')" width="110">
            <template #default="{ row }">{{ row.workdayStartTime?.slice(0, 5) }}</template>
          </el-table-column>
          <el-table-column prop="dailyCapacityMinutes" :label="t('productionSchedule.dailyCapacity')" width="130" align="right" />
          <el-table-column prop="scheduledMinutes" :label="t('productionSchedule.loadMinutes')" width="120" align="right" />
          <el-table-column :label="t('productionSchedule.utilization')" min-width="180">
            <template #default="{ row }">
              <el-progress :percentage="Math.min(100, row.utilizationPercent)" :status="row.utilizationPercent > 100 ? 'exception' : undefined">
                {{ row.utilizationPercent.toFixed(2) }}%
              </el-progress>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never" class="section-card">
        <template #header>
          <div class="section-header">
            <span>{{ t('productionSchedule.detailTitle') }}</span>
            <el-tag :type="result.committed ? 'success' : 'info'">
              {{ result.committed ? t('productionSchedule.committed') : t('productionSchedule.previewOnly') }}
            </el-tag>
          </div>
        </template>
        <el-table :data="result.items" border stripe>
          <el-table-column prop="orderNo" :label="t('productionSchedule.orderNo')" width="150" fixed="left" />
          <el-table-column prop="dueDate" :label="t('productionSchedule.dueDate')" width="120" />
          <el-table-column prop="lineNo" :label="t('productionSchedule.sequence')" width="70" align="center" />
          <el-table-column prop="operationName" :label="t('productionSchedule.operation')" min-width="150" />
          <el-table-column prop="workCenterName" :label="t('productionSchedule.workCenter')" min-width="150" />
          <el-table-column prop="remainingQty" :label="t('productionSchedule.remainingQty')" width="110" align="right" />
          <el-table-column prop="requiredMinutes" :label="t('productionSchedule.requiredMinutes')" width="120" align="right" />
          <el-table-column prop="scheduledStartTime" :label="t('productionSchedule.scheduledStart')" width="180" />
          <el-table-column prop="scheduledEndTime" :label="t('productionSchedule.scheduledEnd')" width="180" />
          <el-table-column prop="capacityWaitMinutes" :label="t('productionSchedule.waitMinutes')" width="110" align="right" />
          <el-table-column :label="t('productionSchedule.result')" width="110" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.scheduleStatus === 'UNSCHEDULED'" type="danger">{{ t('productionSchedule.status.unscheduled') }}</el-tag>
              <el-tag v-else-if="row.delayed" type="warning">{{ t('productionSchedule.status.delayed') }}</el-tag>
              <el-tag v-else type="success">{{ t('productionSchedule.status.scheduled') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" :label="t('productionSchedule.reason')" min-width="260" show-overflow-tooltip />
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'

import { previewProductionSchedule, runProductionSchedule } from '@/api/production'
import { useProductionSchedule } from '@/composables/useProductionSchedule'

const { t } = useI18n()
const {
  dateRange,
  execute,
  hasResult,
  loading,
  preview,
  result,
  running
} = useProductionSchedule(t, {
  previewSchedule: previewProductionSchedule,
  runSchedule: runProductionSchedule,
  confirm: (message, title) => ElMessageBox.confirm(message, title, { type: 'warning' }),
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message)
})
</script>

<style scoped>
.app-container { padding: 20px; }
.toolbar-card, .section-card { margin-bottom: 20px; }
.toolbar, .section-header { display: flex; justify-content: space-between; align-items: center; gap: 20px; }
.toolbar h2 { margin: 0 0 8px; }
.toolbar p { margin: 0; color: var(--el-text-color-secondary); }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; margin: 20px 0; }
@media (max-width: 1000px) {
  .toolbar { align-items: flex-start; flex-direction: column; }
  .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
