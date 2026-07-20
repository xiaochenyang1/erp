<template>
  <div class="app-container">
    <el-card shadow="never" class="header-card">
      <div class="header-row">
        <div>
          <div class="page-title">可观测性</div>
          <div class="page-subtitle">业务健康状态生成时间：{{ health.generatedAt || '-' }}</div>
        </div>
        <div class="header-actions">
          <el-tag :type="platformHealthTagType(platformHealth.status)" size="large">
            平台状态：{{ platformHealthLabel(platformHealth.status) }}
          </el-tag>
          <el-tag :type="health.overallStatus === 'UP' ? 'success' : 'warning'" size="large">
            {{ health.overallStatus === 'UP' ? '业务正常' : '存在风险' }}
          </el-tag>
          <el-tag v-if="systemProfile" type="info" size="large">
            环境：{{ systemProfile.scope }}
          </el-tag>
          <el-button type="primary" :icon="Refresh" :loading="loading || platformHealthLoading" @click="loadData">
            刷新
          </el-button>
        </div>
      </div>
    </el-card>

    <el-row :gutter="16" class="metric-row">
      <el-col v-for="check in health.checks" :key="check.code" :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="metric-card">
          <div class="metric-top">
            <el-tag :type="check.status === 'UP' ? 'success' : 'warning'">
              {{ check.status === 'UP' ? '正常' : '预警' }}
            </el-tag>
            <span class="metric-code">{{ check.code }}</span>
          </div>
          <div class="metric-name">{{ check.name }}</div>
          <div class="metric-value">{{ check.count }}</div>
          <div class="metric-threshold">阈值：{{ check.threshold }}</div>
          <div class="metric-summary">{{ check.summary }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="table-card">
      <template #header>
        <span>业务健康检查</span>
      </template>
      <el-table v-loading="loading" :data="health.checks" border stripe>
        <el-table-column prop="name" label="检查项" min-width="180" />
        <el-table-column prop="code" label="编码" min-width="220" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'UP' ? 'success' : 'warning'">
              {{ row.status === 'UP' ? '正常' : '预警' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="count" label="数量" width="100" align="right" />
        <el-table-column prop="threshold" label="阈值" width="100" align="right" />
        <el-table-column prop="summary" label="说明" min-width="260" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getBusinessHealth, getSystemHealth, type BusinessHealth, type SystemHealth } from '@/api/observability'
import { getSystemProfile, type SystemProfile } from '@/api/system'

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
    console.error('加载业务健康失败:', error)
    ElMessage.error('加载业务健康失败')
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
    ElMessage.error('加载平台状态失败')
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
    UP: '正常',
    DOWN: '异常',
    OUT_OF_SERVICE: '停服',
    UNKNOWN: '未知'
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
