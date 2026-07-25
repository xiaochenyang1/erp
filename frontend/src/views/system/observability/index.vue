<template>
  <div class="app-container">
    <el-card shadow="never" class="header-card">
      <div class="header-row">
        <div>
          <div class="page-title">{{ $t('systemObservability.title') }}</div>
          <div class="page-subtitle">
            {{ $t('systemObservability.generatedAt', { time: formatLocalizedDateTime(health.generatedAt) || '-' }) }}
          </div>
        </div>
        <div class="header-actions">
          <el-tag :type="platformHealthTagType(platformHealth.status)" size="large">
            {{ $t('systemObservability.platformStatus', { status: platformHealthLabel(platformHealth.status) }) }}
          </el-tag>
          <el-tag :type="health.overallStatus === 'UP' ? 'success' : 'warning'" size="large">
            {{ health.overallStatus === 'UP' ? $t('systemObservability.businessHealthy') : $t('systemObservability.businessAtRisk') }}
          </el-tag>
          <el-tag v-if="systemProfile" type="info" size="large">
            {{ $t('systemObservability.environment', { scope: systemProfile.scope }) }}
          </el-tag>
          <el-button type="primary" :icon="Refresh" :loading="loading || platformHealthLoading" @click="loadData">
            {{ $t('systemObservability.refresh') }}
          </el-button>
        </div>
      </div>
    </el-card>

    <el-row :gutter="16" class="metric-row">
      <el-col v-for="check in health.checks" :key="check.code" :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="metric-card">
          <div class="metric-top">
            <el-tag :type="check.status === 'UP' ? 'success' : 'warning'">
              {{ check.status === 'UP' ? $t('systemObservability.healthy') : $t('systemObservability.warning') }}
            </el-tag>
            <span class="metric-code">{{ check.code }}</span>
          </div>
          <div class="metric-name">{{ check.name }}</div>
          <div class="metric-value">{{ check.count }}</div>
          <div class="metric-threshold">{{ $t('systemObservability.thresholdValue', { value: check.threshold }) }}</div>
          <div class="metric-summary">{{ check.summary }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="table-card">
      <template #header>
        <span>{{ $t('systemObservability.businessHealthChecks') }}</span>
      </template>
      <el-table v-loading="loading" :data="health.checks" border stripe>
        <el-table-column prop="name" :label="$t('systemObservability.checkItem')" min-width="180" />
        <el-table-column prop="code" :label="$t('systemObservability.code')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="status" :label="$t('systemObservability.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'UP' ? 'success' : 'warning'">
              {{ row.status === 'UP' ? $t('systemObservability.healthy') : $t('systemObservability.warning') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="count" :label="$t('systemObservability.count')" width="100" align="right" />
        <el-table-column prop="threshold" :label="$t('systemObservability.threshold')" width="100" align="right" />
        <el-table-column prop="summary" :label="$t('systemObservability.description')" min-width="260" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getBusinessHealth, getSystemHealth, type BusinessHealth, type SystemHealth } from '@/api/observability'
import { getSystemProfile, type SystemProfile } from '@/api/system'
import { formatLocalizedDateTime } from '@/utils/locale'

const { t } = useI18n()

const loading = ref(false)
const platformHealthLoading = ref(false)
const platformHealth = ref<SystemHealth>({
  status: 'UNKNOWN'
})
const health = ref<BusinessHealth>({
  overallStatus: 'UP',
  generatedAt: '',
  checks: []
})
const systemProfile = ref<SystemProfile | null>(null)

const loadBusinessHealth = async () => {
  loading.value = true
  try {
    health.value = await getBusinessHealth()
  } catch (error) {
    console.error('Failed to load business health:', error)
    ElMessage.error(t('systemObservability.message.businessHealthLoadFailed'))
  } finally {
    loading.value = false
  }
}

const loadPlatformHealth = async () => {
  platformHealthLoading.value = true
  try {
    platformHealth.value = await getSystemHealth()
  } catch {
    platformHealth.value = { status: 'DOWN' }
    ElMessage.error(t('systemObservability.message.platformHealthLoadFailed'))
  } finally {
    platformHealthLoading.value = false
  }
}

const loadData = async () => {
  await Promise.all([loadBusinessHealth(), loadPlatformHealth(), loadSystemProfile()])
}

const loadSystemProfile = async () => {
  try {
    systemProfile.value = await getSystemProfile()
  } catch {
    systemProfile.value = null
  }
}

const platformHealthLabel = (status: string) => {
  const map: Record<string, string> = {
    UP: t('systemObservability.platformHealth.up'),
    DOWN: t('systemObservability.platformHealth.down'),
    OUT_OF_SERVICE: t('systemObservability.platformHealth.outOfService'),
    UNKNOWN: t('systemObservability.platformHealth.unknown')
  }
  return map[status] || status
}

const platformHealthTagType = (status: string) => {
  if (status === 'UP') return 'success'
  if (status === 'DOWN' || status === 'OUT_OF_SERVICE') return 'danger'
  return 'info'
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.app-container {
  padding: 20px;
}

.header-card,
.table-card {
  margin-bottom: 20px;
}

.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
}

.page-subtitle {
  margin-top: 6px;
  color: #606266;
}

.metric-row {
  margin-bottom: 20px;
}

.metric-card {
  min-height: 180px;
  margin-bottom: 16px;
}

.metric-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.metric-code {
  color: #909399;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-name {
  margin-top: 14px;
  font-weight: 600;
}

.metric-value {
  margin-top: 10px;
  font-size: 28px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.metric-threshold,
.metric-summary {
  margin-top: 6px;
  color: #606266;
  font-size: 13px;
}
</style>
