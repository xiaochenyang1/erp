<template>
  <div class="exception-rule-page">
    <el-card shadow="never" class="filter-panel">
      <el-form :model="ruleQueryForm" inline @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="ruleQueryForm.keyword"
            class="keyword-input"
            clearable
            placeholder="规则名称、编码、说明"
            @keyup.enter="handleRuleQuery"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="ruleQueryForm.ruleType" clearable placeholder="全部" class="type-select">
            <el-option v-for="item in ruleTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="ruleQueryForm.enabled" clearable placeholder="全部" class="state-select">
            <el-option label="启用" value="true" />
            <el-option label="停用" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item class="filter-actions">
          <el-button type="primary" :icon="Search" :loading="ruleLoading" @click="handleRuleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="handleRuleReset">重置</el-button>
          <el-button
            v-permission="'exception-rule:execute'"
            type="success"
            :icon="AlarmClock"
            :loading="scanAllLoading"
            @click="handleScanAll"
          >
            扫描全部
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="summary-grid">
      <el-card v-for="item in summaryItems" :key="item.label" shadow="never" class="summary-card">
        <div class="summary-icon" :class="item.tone">
          <el-icon><component :is="item.icon" /></el-icon>
        </div>
        <div class="summary-content">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </el-card>
    </div>

    <el-alert
      v-if="scanResults.length"
      class="scan-result"
      :type="scanResults.some((item) => item.status === 'FAILED') ? 'warning' : 'success'"
      show-icon
      :closable="true"
      @close="scanResults = []"
    >
      <template #title>
        <span>
          扫描完成：命中 {{ totalScanHits }} 条，新建工单 {{ totalScanTickets }} 张，复用工单 {{ totalScanDuplicates }} 张
        </span>
      </template>
    </el-alert>

    <el-card shadow="never" class="table-panel">
      <template #header>
        <div class="panel-header">
          <span>异常规则</span>
          <el-text type="info" size="small">本页 {{ ruleData.length }} 条 / 共 {{ rulePagination.total }} 条</el-text>
        </div>
      </template>

      <el-table v-loading="ruleLoading" :data="ruleData" border stripe row-key="id">
        <el-table-column prop="ruleName" label="规则" min-width="220" fixed="left" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="rule-title">
              <strong>{{ row.ruleName }}</strong>
              <span>{{ row.ruleCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="ruleType" label="类型" width="150">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ ruleTypeLabel(row.ruleType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="阈值" width="128" align="center">
          <template #default="{ row }">{{ thresholdLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="96" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="priorityType(row.priority)">
              {{ priorityLabel(row.priority) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="92" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assigneeUserId" label="处理人" width="96" align="center">
          <template #default="{ row }">{{ row.assigneeUserId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="scheduleIntervalMinutes" label="间隔" width="92" align="right">
          <template #default="{ row }">{{ row.scheduleIntervalMinutes || 60 }} 分钟</template>
        </el-table-column>
        <el-table-column prop="lastScanStatus" label="扫描状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.lastScanStatus" size="small" :type="scanStatusType(row.lastScanStatus)">
              {{ scanStatusLabel(row.lastScanStatus) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastHitCount" label="命中" width="80" align="right">
          <template #default="{ row }">{{ row.lastHitCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="lastTicketCreatedCount" label="新工单" width="90" align="right">
          <template #default="{ row }">{{ row.lastTicketCreatedCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="lastScanTime" label="最近扫描" width="160">
          <template #default="{ row }">{{ formatDateTime(row.lastScanTime) }}</template>
        </el-table-column>
        <el-table-column prop="nextScanTime" label="下次扫描" width="160">
          <template #default="{ row }">{{ formatDateTime(row.nextScanTime) }}</template>
        </el-table-column>
        <el-table-column label="说明" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ row.lastErrorMessage || row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="238" align="center" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button
                v-permission="'exception-rule:execute'"
                link
                type="primary"
                size="small"
                :icon="AlarmClock"
                :disabled="!row.enabled"
                :loading="scanRuleLoadingId === row.id"
                @click="handleScanRule(row)"
              >
                扫描
              </el-button>
              <el-button
                v-permission="'exception-rule:manage'"
                link
                type="primary"
                size="small"
                :icon="EditPen"
                @click="openEditDialog(row)"
              >
                配置
              </el-button>
              <el-button
                v-permission="'exception-rule:manage'"
                link
                :type="row.enabled ? 'warning' : 'success'"
                size="small"
                :icon="SwitchButton"
                :loading="toggleLoadingId === row.id"
                @click="handleToggleRule(row)"
              >
                {{ row.enabled ? '停用' : '启用' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="rulePagination.page"
        v-model:page-size="rulePagination.size"
        :total="rulePagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="handleRulePageChange"
        @current-change="handleRulePageChange"
      />
    </el-card>

    <el-card shadow="never" class="table-panel">
      <template #header>
        <div class="hit-header">
          <div class="panel-header-title">
            <span>规则命中</span>
            <el-text type="info" size="small">最近命中 {{ hitPagination.total }} 条</el-text>
          </div>
          <el-form :model="hitQueryForm" inline class="hit-filter" @submit.prevent>
            <el-form-item label="类型">
              <el-select v-model="hitQueryForm.ruleType" clearable placeholder="全部" class="hit-type-select">
                <el-option v-for="item in ruleTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="来源">
              <el-input
                v-model="hitQueryForm.sourceNo"
                clearable
                placeholder="来源编号"
                class="hit-source-input"
                @keyup.enter="handleHitQuery"
              />
            </el-form-item>
            <el-form-item label="工单">
              <el-input
                v-model="hitQueryForm.ticketId"
                clearable
                class="hit-ticket-input"
                placeholder="工单ID"
              />
            </el-form-item>
            <el-form-item>
              <el-button :icon="Search" :loading="hitLoading" @click="handleHitQuery">筛选</el-button>
              <el-button :icon="Refresh" @click="handleHitReset">重置</el-button>
            </el-form-item>
          </el-form>
        </div>
      </template>

      <el-table v-loading="hitLoading" :data="hitData" border stripe row-key="id">
        <el-table-column prop="title" label="命中事项" min-width="260" fixed="left" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="hit-title">
              <strong>{{ row.title }}</strong>
              <span>{{ row.description || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="ruleType" label="类型" width="150">
          <template #default="{ row }">{{ ruleTypeLabel(row.ruleType) }}</template>
        </el-table-column>
        <el-table-column label="来源" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="source-cell">
              <span>{{ row.sourceNo || row.sourceId || '-' }}</span>
              <el-text type="info" size="small">{{ row.sourceType }}</el-text>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="triggerValue" label="触发值" width="110" align="right">
          <template #default="{ row }">{{ row.triggerValue || '-' }}</template>
        </el-table-column>
        <el-table-column prop="thresholdValue" label="阈值" width="100" align="right">
          <template #default="{ row }">{{ row.thresholdValue || '-' }}</template>
        </el-table-column>
        <el-table-column prop="ticketId" label="工单" width="110" align="center">
          <template #default="{ row }">
            <el-button v-if="row.ticketId" link type="primary" size="small" :icon="Tickets" @click="openTicket(row)">
              {{ row.ticketId }}
            </el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="hitCount" label="次数" width="76" align="right">
          <template #default="{ row }">{{ row.hitCount || 1 }}</template>
        </el-table-column>
        <el-table-column prop="firstHitTime" label="首次命中" width="160">
          <template #default="{ row }">{{ formatDateTime(row.firstHitTime) }}</template>
        </el-table-column>
        <el-table-column prop="lastHitTime" label="最近命中" width="160">
          <template #default="{ row }">{{ formatDateTime(row.lastHitTime) }}</template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="hitPagination.page"
        v-model:page-size="hitPagination.size"
        :total="hitPagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="handleHitPageChange"
        @current-change="handleHitPageChange"
      />
    </el-card>

    <el-dialog v-model="editDialogVisible" title="配置异常规则" width="620px" destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="96px">
        <el-form-item label="规则">
          <el-input :model-value="editTarget?.ruleName || ''" disabled />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="阈值" prop="thresholdValue">
            <el-input-number
              v-model="editForm.thresholdValue"
              :min="0"
              :controls="false"
              class="form-control"
              placeholder="阈值"
            />
          </el-form-item>
          <el-form-item label="单位" prop="thresholdUnit">
            <el-select v-model="editForm.thresholdUnit" class="form-control">
              <el-option v-for="item in thresholdUnitOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="优先级" prop="priority">
            <el-select v-model="editForm.priority" class="form-control">
              <el-option v-for="item in priorityOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="处理人">
            <el-input
              v-model="editForm.assigneeUserId"
              clearable
              class="form-control"
              placeholder="用户ID"
            />
          </el-form-item>
          <el-form-item label="扫描间隔" prop="scheduleIntervalMinutes">
            <el-input-number
              v-model="editForm.scheduleIntervalMinutes"
              :min="5"
              :max="10080"
              :step="5"
              class="form-control"
              placeholder="分钟"
            />
          </el-form-item>
        </div>
        <el-form-item label="说明">
          <el-input
            v-model="editForm.remark"
            type="textarea"
            :rows="3"
            maxlength="512"
            show-word-limit
            placeholder="说明扫描口径或处理要求"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="handleSaveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import {
  AlarmClock,
  CircleCheck,
  CircleClose,
  EditPen,
  Finished,
  Refresh,
  Search,
  SwitchButton,
  Tickets,
  Warning
} from '@element-plus/icons-vue'
import {
  disableExceptionRule,
  enableExceptionRule,
  getExceptionRuleHits,
  getExceptionRules,
  scanAllExceptionRules,
  scanExceptionRule,
  updateExceptionRule,
  type ExceptionRule,
  type ExceptionRuleHit,
  type ExceptionRuleHitQuery,
  type ExceptionRuleQuery,
  type ExceptionRuleScanResult,
  type ExceptionRuleUpdateRequest
} from '@/api/exceptionRule'

type Option = { label: string; value: string }
type EnabledFilter = '' | 'true' | 'false'

const router = useRouter()

const ruleTypeOptions: Option[] = [
  { label: '低库存', value: 'LOW_STOCK' },
  { label: '应收逾期', value: 'RECEIVABLE_OVERDUE' },
  { label: '应付逾期', value: 'PAYABLE_OVERDUE' },
  { label: '失败操作', value: 'OPERATION_FAILURE' }
]

const thresholdUnitOptions: Option[] = [
  { label: '数量', value: 'QTY' },
  { label: '天', value: 'DAYS' },
  { label: '分钟', value: 'MINUTES' },
  { label: '次数', value: 'COUNT' }
]

const priorityOptions: Option[] = [
  { label: '低', value: 'LOW' },
  { label: '中', value: 'MEDIUM' },
  { label: '高', value: 'HIGH' },
  { label: '紧急', value: 'URGENT' }
]

const ruleQueryForm = reactive({
  keyword: '',
  ruleType: '',
  enabled: '' as EnabledFilter
})

const hitQueryForm = reactive<ExceptionRuleHitQuery>({
  ruleType: '',
  sourceNo: '',
  ticketId: undefined
})

const rulePagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const hitPagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const ruleLoading = ref(false)
const hitLoading = ref(false)
const scanAllLoading = ref(false)
const scanRuleLoadingId = ref<string>()
const toggleLoadingId = ref<string>()
const editSubmitting = ref(false)

const ruleData = ref<ExceptionRule[]>([])
const hitData = ref<ExceptionRuleHit[]>([])
const scanResults = ref<ExceptionRuleScanResult[]>([])

const editDialogVisible = ref(false)
const editFormRef = ref<FormInstance>()
const editTarget = ref<ExceptionRule>()
const editForm = reactive<ExceptionRuleUpdateRequest>({
  thresholdValue: undefined,
  thresholdUnit: '',
  priority: '',
  assigneeUserId: undefined,
  scheduleIntervalMinutes: 60,
  remark: ''
})

const editRules: FormRules = {
  thresholdValue: [{ required: true, message: '请输入阈值', trigger: 'blur' }],
  thresholdUnit: [{ required: true, message: '请选择单位', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
  scheduleIntervalMinutes: [{ required: true, message: '请输入扫描间隔', trigger: 'blur' }]
}

const summaryItems = computed(() => [
  {
    label: '启用规则',
    value: ruleData.value.filter((item) => item.enabled).length,
    icon: CircleCheck,
    tone: 'green'
  },
  {
    label: '停用规则',
    value: ruleData.value.filter((item) => !item.enabled).length,
    icon: CircleClose,
    tone: 'gray'
  },
  {
    label: '最近命中',
    value: ruleData.value.reduce((sum, item) => sum + (item.lastHitCount || 0), 0),
    icon: Warning,
    tone: 'orange'
  },
  {
    label: '新建工单',
    value: ruleData.value.reduce((sum, item) => sum + (item.lastTicketCreatedCount || 0), 0),
    icon: Tickets,
    tone: 'blue'
  },
  {
    label: '扫描失败',
    value: ruleData.value.filter((item) => item.lastScanStatus === 'FAILED').length,
    icon: Finished,
    tone: 'red'
  }
])

const totalScanHits = computed(() => scanResults.value.reduce((sum, item) => sum + item.hitCount, 0))
const totalScanTickets = computed(() => scanResults.value.reduce((sum, item) => sum + item.ticketCreatedCount, 0))
const totalScanDuplicates = computed(() => scanResults.value.reduce((sum, item) => sum + item.duplicateTicketCount, 0))

const loadRules = async () => {
  ruleLoading.value = true
  try {
    const page = await getExceptionRules(buildRuleQueryParams())
    ruleData.value = page.records || []
    rulePagination.total = page.total || 0
  } finally {
    ruleLoading.value = false
  }
}

const loadHits = async () => {
  hitLoading.value = true
  try {
    const page = await getExceptionRuleHits(buildHitQueryParams())
    hitData.value = page.records || []
    hitPagination.total = page.total || 0
  } finally {
    hitLoading.value = false
  }
}

const buildRuleQueryParams = (): ExceptionRuleQuery => ({
  keyword: ruleQueryForm.keyword?.trim() || undefined,
  ruleType: ruleQueryForm.ruleType || undefined,
  enabled: ruleQueryForm.enabled === '' ? undefined : ruleQueryForm.enabled === 'true',
  pageNo: rulePagination.page,
  pageSize: rulePagination.size
})

const buildHitQueryParams = (): ExceptionRuleHitQuery => ({
  ruleType: hitQueryForm.ruleType || undefined,
  sourceNo: hitQueryForm.sourceNo?.trim() || undefined,
  ticketId: normalizeOptionalId(hitQueryForm.ticketId),
  pageNo: hitPagination.page,
  pageSize: hitPagination.size
})

const handleRuleQuery = () => {
  rulePagination.page = 1
  loadRules()
}

const handleRuleReset = () => {
  ruleQueryForm.keyword = ''
  ruleQueryForm.ruleType = ''
  ruleQueryForm.enabled = ''
  rulePagination.page = 1
  loadRules()
}

const handleRulePageChange = () => {
  loadRules()
}

const handleHitQuery = () => {
  hitPagination.page = 1
  loadHits()
}

const handleHitReset = () => {
  hitQueryForm.ruleType = ''
  hitQueryForm.sourceNo = ''
  hitQueryForm.ticketId = undefined
  hitPagination.page = 1
  loadHits()
}

const handleHitPageChange = () => {
  loadHits()
}

const handleScanRule = async (row: ExceptionRule) => {
  if (!row.enabled) {
    ElMessage.warning('规则已停用')
    return
  }
  scanRuleLoadingId.value = row.id
  try {
    const result = await scanExceptionRule(row.id)
    scanResults.value = [result]
    ElMessage.success(`扫描完成，命中 ${result.hitCount} 条`)
    await Promise.all([loadRules(), loadHits()])
  } finally {
    scanRuleLoadingId.value = undefined
  }
}

const handleScanAll = async () => {
  scanAllLoading.value = true
  try {
    scanResults.value = await scanAllExceptionRules()
    ElMessage.success(`扫描完成，命中 ${totalScanHits.value} 条`)
    await Promise.all([loadRules(), loadHits()])
  } finally {
    scanAllLoading.value = false
  }
}

const handleToggleRule = async (row: ExceptionRule) => {
  toggleLoadingId.value = row.id
  try {
    if (row.enabled) {
      await disableExceptionRule(row.id)
      ElMessage.success('规则已停用')
    } else {
      await enableExceptionRule(row.id)
      ElMessage.success('规则已启用')
    }
    await loadRules()
  } finally {
    toggleLoadingId.value = undefined
  }
}

const openEditDialog = (row: ExceptionRule) => {
  editTarget.value = row
  editForm.thresholdValue = Number(row.thresholdValue || 0)
  editForm.thresholdUnit = row.thresholdUnit
  editForm.priority = row.priority
  editForm.assigneeUserId = row.assigneeUserId
  editForm.scheduleIntervalMinutes = row.scheduleIntervalMinutes || 60
  editForm.remark = row.remark || ''
  editDialogVisible.value = true
}

const handleSaveEdit = async () => {
  if (!editTarget.value) return
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid) return
  editSubmitting.value = true
  try {
    await updateExceptionRule(editTarget.value.id, {
      thresholdValue: editForm.thresholdValue,
      thresholdUnit: editForm.thresholdUnit,
      priority: editForm.priority,
      assigneeUserId: normalizeOptionalId(editForm.assigneeUserId),
      scheduleIntervalMinutes: editForm.scheduleIntervalMinutes,
      remark: editForm.remark?.trim() || undefined
    })
    ElMessage.success('规则配置已保存')
    editDialogVisible.value = false
    await loadRules()
  } finally {
    editSubmitting.value = false
  }
}

const openTicket = (row: ExceptionRuleHit) => {
  router.push({
    path: '/exception-tickets',
    query: row.sourceNo ? { sourceNo: row.sourceNo } : undefined
  })
}

const ruleTypeLabel = (value?: string) => {
  return ruleTypeOptions.find((item) => item.value === value)?.label || value || '-'
}

const priorityLabel = (value?: string) => {
  return priorityOptions.find((item) => item.value === value)?.label || value || '-'
}

const thresholdUnitLabel = (value?: string) => {
  return thresholdUnitOptions.find((item) => item.value === value)?.label || value || '-'
}

const thresholdLabel = (row: ExceptionRule) => {
  return `${row.thresholdValue ?? 0} ${thresholdUnitLabel(row.thresholdUnit)}`
}

const priorityType = (value?: string) => {
  const typeMap: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    LOW: 'info',
    MEDIUM: 'primary',
    HIGH: 'warning',
    URGENT: 'danger'
  }
  return value ? typeMap[value] || 'info' : 'info'
}

const scanStatusLabel = (value?: string) => {
  const labels: Record<string, string> = {
    SUCCESS: '成功',
    FAILED: '失败',
    SKIPPED: '跳过'
  }
  return value ? labels[value] || value : '-'
}

const scanStatusType = (value?: string) => {
  const typeMap: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    SUCCESS: 'success',
    FAILED: 'danger',
    SKIPPED: 'info'
  }
  return value ? typeMap[value] || 'info' : 'info'
}

const formatDateTime = (value?: string) => {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

const normalizeOptionalId = (value?: string | number) => {
  const normalized = value == null ? '' : String(value).trim()
  return normalized || undefined
}

onMounted(async () => {
  await Promise.all([loadRules(), loadHits()])
})
</script>

<style scoped lang="scss">
.exception-rule-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;

  .filter-panel {
    :deep(.el-card__body) {
      padding-bottom: 2px;
    }
  }

  .keyword-input {
    width: 240px;
  }

  .type-select {
    width: 150px;
  }

  .state-select {
    width: 112px;
  }

  .filter-actions {
    :deep(.el-button) {
      min-height: 32px;
      transition-duration: 150ms;
      transition-property: scale, background-color, border-color, color;
      transition-timing-function: ease-out;

      &:active {
        scale: 0.96;
      }
    }
  }

  .summary-grid {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 12px;
  }

  .summary-card {
    :deep(.el-card__body) {
      display: flex;
      min-height: 78px;
      align-items: center;
      gap: 12px;
    }
  }

  .summary-icon {
    display: inline-flex;
    width: 36px;
    height: 36px;
    flex: 0 0 36px;
    align-items: center;
    justify-content: center;
    border-radius: 8px;
    font-size: 18px;

    &.blue {
      color: #2563eb;
      background: #eff6ff;
    }

    &.green {
      color: #059669;
      background: #ecfdf5;
    }

    &.orange {
      color: #d97706;
      background: #fffbeb;
    }

    &.red {
      color: #dc2626;
      background: #fef2f2;
    }

    &.gray {
      color: #4b5563;
      background: #f3f4f6;
    }
  }

  .summary-content {
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 6px;

    span {
      color: #606266;
      font-size: 13px;
    }

    strong {
      color: #303133;
      font-size: 20px;
      font-variant-numeric: tabular-nums;
      font-weight: 700;
      line-height: 1.15;
    }
  }

  .scan-result {
    border-radius: 8px;
  }

  .panel-header,
  .panel-header-title {
    display: flex;
    min-height: 24px;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .hit-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
  }

  .hit-filter {
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;

    :deep(.el-form-item) {
      margin-bottom: 0;
    }
  }

  .hit-type-select {
    width: 138px;
  }

  .hit-source-input {
    width: 150px;
  }

  .hit-ticket-input {
    width: 112px;
  }

  .rule-title,
  .hit-title,
  .source-cell {
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 4px;

    strong {
      overflow: hidden;
      color: #303133;
      font-size: 14px;
      font-weight: 650;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    span {
      overflow: hidden;
      color: #606266;
      font-size: 12px;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .row-actions {
    display: flex;
    min-height: 28px;
    align-items: center;
    justify-content: center;
    gap: 2px;
    white-space: nowrap;
  }

  .pagination {
    margin-top: 18px;
    justify-content: flex-end;
  }

  .form-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    column-gap: 12px;
  }

  .form-control {
    width: 100%;
  }
}

@media (max-width: 1280px) {
  .exception-rule-page {
    .summary-grid {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }

    .hit-header {
      flex-direction: column;
    }

    .hit-filter {
      justify-content: flex-start;
    }
  }
}

@media (max-width: 720px) {
  .exception-rule-page {
    .keyword-input,
    .type-select,
    .state-select,
    .hit-type-select,
    .hit-source-input,
    .hit-ticket-input {
      width: 100%;
    }

    .summary-grid,
    .form-grid {
      grid-template-columns: 1fr;
    }

    .panel-header,
    .panel-header-title {
      align-items: flex-start;
      flex-direction: column;
    }
  }
}
</style>
