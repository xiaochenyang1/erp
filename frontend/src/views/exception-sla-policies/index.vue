<template>
  <div class="exception-sla-policy-page">
    <el-card shadow="never" class="filter-panel">
      <el-form :model="queryForm" inline @submit.prevent>
        <el-form-item :label="$t('exceptionSlaPolicy.category')">
          <el-select v-model="queryForm.category" clearable filterable :placeholder="$t('exceptionSlaPolicy.all')" class="category-select">
            <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('exceptionSlaPolicy.priority')">
          <el-select v-model="queryForm.priority" clearable :placeholder="$t('exceptionSlaPolicy.all')" class="priority-select">
            <el-option v-for="item in priorityOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('exceptionSlaPolicy.status')">
          <el-select v-model="queryForm.enabled" clearable :placeholder="$t('exceptionSlaPolicy.all')" class="state-select">
            <el-option :label="$t('exceptionSlaPolicy.enabled')" value="true" />
            <el-option :label="$t('exceptionSlaPolicy.disabled')" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item class="filter-actions">
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleQuery">{{ $t('exceptionSlaPolicy.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ $t('exceptionSlaPolicy.reset') }}</el-button>
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

    <el-card shadow="never" class="table-panel">
      <template #header>
        <div class="panel-header">
          <span>{{ $t('exceptionSlaPolicy.title') }}</span>
          <el-text type="info" size="small">{{ $t('exceptionSlaPolicy.pageSummary', { current: tableData.length, total: pagination.total }) }}</el-text>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe row-key="id">
        <el-table-column prop="category" :label="$t('exceptionSlaPolicy.category')" min-width="180" fixed="left">
          <template #default="{ row }">
            <div class="policy-title">
              <strong>{{ categoryLabel(row.category) }}</strong>
              <span>{{ row.category }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="priority" :label="$t('exceptionSlaPolicy.priority')" width="104" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="priorityType(row.priority)" effect="plain">
              {{ priorityLabel(row.priority) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="dueHours" :label="$t('exceptionSlaPolicy.slaLimit')" width="118" align="right">
          <template #default="{ row }">
            <span class="numeric">{{ $t('exceptionSlaPolicy.hours', { count: row.dueHours }) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="escalationEnabled" :label="$t('exceptionSlaPolicy.overdueEscalation')" width="108" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.escalationEnabled ? 'success' : 'info'">
              {{ row.escalationEnabled ? $t('exceptionSlaPolicy.enabled') : $t('exceptionSlaPolicy.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="escalateToPriority" :label="$t('exceptionSlaPolicy.escalateTo')" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.escalationEnabled" size="small" :type="priorityType(row.escalateToPriority)">
              {{ priorityLabel(row.escalateToPriority) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" :label="$t('exceptionSlaPolicy.policyStatus')" width="104" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? $t('exceptionSlaPolicy.enabled') : $t('exceptionSlaPolicy.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('exceptionSlaPolicy.description')" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column prop="updatedTime" :label="$t('exceptionSlaPolicy.updatedAt')" width="160">
          <template #default="{ row }">{{ formatDateTime(row.updatedTime) }}</template>
        </el-table-column>
        <el-table-column :label="$t('exceptionSlaPolicy.operations')" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'exception-sla-policy:manage'"
              link
              type="primary"
              size="small"
              :icon="EditPen"
              @click="openEditDialog(row)"
            >
              {{ $t('exceptionSlaPolicy.configure') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <el-dialog v-model="editDialogVisible" :title="$t('exceptionSlaPolicy.dialogTitle')" width="620px" destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="104px">
        <el-form-item :label="$t('exceptionSlaPolicy.policy')">
          <el-input :model-value="editTargetLabel" disabled />
        </el-form-item>
        <div class="form-grid">
          <el-form-item :label="$t('exceptionSlaPolicy.slaLimit')" prop="dueHours">
            <el-input-number
              v-model="editForm.dueHours"
              :min="1"
              :max="8760"
              :step="1"
              class="form-control"
              :placeholder="$t('exceptionSlaPolicy.hourUnit')"
            />
          </el-form-item>
          <el-form-item :label="$t('exceptionSlaPolicy.policyStatus')">
            <el-switch v-model="editForm.enabled" :active-text="$t('exceptionSlaPolicy.enabled')" :inactive-text="$t('exceptionSlaPolicy.disabled')" />
          </el-form-item>
          <el-form-item :label="$t('exceptionSlaPolicy.overdueEscalation')">
            <el-switch v-model="editForm.escalationEnabled" :active-text="$t('exceptionSlaPolicy.enabled')" :inactive-text="$t('exceptionSlaPolicy.disabled')" />
          </el-form-item>
          <el-form-item :label="$t('exceptionSlaPolicy.escalationPriority')" prop="escalateToPriority">
            <el-select v-model="editForm.escalateToPriority" class="form-control">
              <el-option v-for="item in priorityOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item :label="$t('exceptionSlaPolicy.description')">
          <el-input
            v-model="editForm.remark"
            type="textarea"
            :rows="3"
            maxlength="512"
            show-word-limit
            :placeholder="$t('exceptionSlaPolicy.descriptionPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">{{ $t('exceptionSlaPolicy.cancel') }}</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="handleSaveEdit">{{ $t('exceptionSlaPolicy.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { AlarmClock, CircleCheck, CircleClose, EditPen, Refresh, Search, TrendCharts, Warning } from '@element-plus/icons-vue'
import {
  getExceptionSlaPolicies,
  updateExceptionSlaPolicy
} from '@/api/exceptionSlaPolicy'
import { useExceptionSlaPolicyPresentation } from '@/composables/useExceptionSlaPolicyPresentation'
import { useExceptionSlaPolicyList } from '@/composables/useExceptionSlaPolicyList'
import { useExceptionSlaPolicyForm } from '@/composables/useExceptionSlaPolicyForm'

const { t } = useI18n()
const editFormRef = ref<FormInstance>()

const notify = {
  onError: (message: string) => ElMessage.error(message),
  onSuccess: (message: string) => ElMessage.success(message)
}

const {
  handlePageChange,
  handleQuery,
  handleReset,
  handleSizeChange,
  loadData,
  loading,
  pagination,
  queryForm,
  tableData
} = useExceptionSlaPolicyList(t, {
  getPolicies: getExceptionSlaPolicies,
  onError: notify.onError
})

const {
  categoryLabel,
  categoryOptions,
  formatDateTime,
  priorityLabel,
  priorityOptions,
  priorityType,
  summaryItems
} = useExceptionSlaPolicyPresentation(t, tableData, {
  circleCheck: CircleCheck,
  circleClose: CircleClose,
  trendCharts: TrendCharts,
  alarmClock: AlarmClock,
  warning: Warning
})

const {
  editDialogVisible,
  editForm,
  editSubmitting,
  editTargetLabel,
  handleSaveEdit: saveEdit,
  openEditDialog
} = useExceptionSlaPolicyForm(t, {
  updatePolicy: updateExceptionSlaPolicy,
  categoryLabel,
  priorityLabel,
  onSubmitted: loadData,
  ...notify
})

const editRules = computed<FormRules>(() => ({
  dueHours: [{ required: true, message: t('exceptionSlaPolicy.validation.dueHours'), trigger: 'blur' }],
  escalateToPriority: [{ required: true, message: t('exceptionSlaPolicy.validation.escalationPriority'), trigger: 'change' }]
}))

const handleSaveEdit = async () => {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid) return
  await saveEdit()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.exception-sla-policy-page {
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

  .category-select {
    width: 156px;
  }

  .priority-select {
    width: 118px;
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
      text-wrap: pretty;
    }

    strong {
      color: #303133;
      font-size: 20px;
      font-variant-numeric: tabular-nums;
      font-weight: 700;
      line-height: 1.15;
    }
  }

  .panel-header {
    display: flex;
    min-height: 24px;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .policy-title {
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
      text-wrap: balance;
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

  .numeric {
    font-variant-numeric: tabular-nums;
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
  .exception-sla-policy-page {
    .summary-grid {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }
  }
}

@media (max-width: 720px) {
  .exception-sla-policy-page {
    .category-select,
    .priority-select,
    .state-select {
      width: 100%;
    }

    .summary-grid,
    .form-grid {
      grid-template-columns: 1fr;
    }

    .panel-header {
      align-items: flex-start;
      flex-direction: column;
    }
  }
}
</style>
