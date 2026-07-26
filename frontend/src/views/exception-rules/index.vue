<template>
  <div class="exception-rule-page">
    <el-card shadow="never" class="filter-panel">
      <el-form :model="ruleQueryForm" inline @submit.prevent>
        <el-form-item :label="t('exceptionRule.keyword')">
          <el-input
            v-model="ruleQueryForm.keyword"
            class="keyword-input"
            clearable
            :placeholder="t('exceptionRule.keywordPlaceholder')"
            @keyup.enter="handleRuleQuery"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item :label="t('exceptionRule.type')">
          <el-select v-model="ruleQueryForm.ruleType" clearable :placeholder="t('exceptionRule.all')" class="type-select">
            <el-option v-for="item in ruleTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('exceptionRule.status')">
          <el-select v-model="ruleQueryForm.enabled" clearable :placeholder="t('exceptionRule.all')" class="state-select">
            <el-option :label="t('exceptionRule.enabled')" value="true" />
            <el-option :label="t('exceptionRule.disabled')" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item class="filter-actions">
          <el-button type="primary" :icon="Search" :loading="ruleLoading" @click="handleRuleQuery">{{ t('exceptionRule.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleRuleReset">{{ t('exceptionRule.reset') }}</el-button>
          <el-button
            v-permission="'exception-rule:execute'"
            type="success"
            :icon="AlarmClock"
            :loading="scanAllLoading"
            @click="handleScanAll"
          >
            {{ t('exceptionRule.scanAll') }}
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
          {{ t('exceptionRule.scanSummary', { hits: totalScanHits, tickets: totalScanTickets, duplicates: totalScanDuplicates }) }}
        </span>
      </template>
    </el-alert>

    <el-card shadow="never" class="table-panel">
      <template #header>
        <div class="panel-header">
          <span>{{ t('exceptionRule.title') }}</span>
          <el-text type="info" size="small">{{ t('exceptionRule.pageSummary', { current: ruleData.length, total: rulePagination.total }) }}</el-text>
        </div>
      </template>

      <el-table v-loading="ruleLoading" :data="ruleData" border stripe row-key="id">
        <el-table-column prop="ruleName" :label="t('exceptionRule.rule')" min-width="220" fixed="left" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="rule-title">
              <strong>{{ row.ruleName }}</strong>
              <span>{{ row.ruleCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="ruleType" :label="t('exceptionRule.type')" width="150">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ ruleTypeLabel(row.ruleType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('exceptionRule.threshold')" width="128" align="center">
          <template #default="{ row }">{{ thresholdLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="priority" :label="t('exceptionRule.priority')" width="96" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="priorityType(row.priority)">
              {{ priorityLabel(row.priority) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" :label="t('exceptionRule.status')" width="92" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? t('exceptionRule.enabled') : t('exceptionRule.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assigneeUserId" :label="t('exceptionRule.assignee')" width="96" align="center">
          <template #default="{ row }">{{ row.assigneeUserId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="scheduleIntervalMinutes" :label="t('exceptionRule.interval')" width="92" align="right">
          <template #default="{ row }">{{ t('exceptionRule.minutes', { count: row.scheduleIntervalMinutes || 60 }) }}</template>
        </el-table-column>
        <el-table-column prop="lastScanStatus" :label="t('exceptionRule.scanStatus')" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.lastScanStatus" size="small" :type="scanStatusType(row.lastScanStatus)">
              {{ scanStatusLabel(row.lastScanStatus) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastHitCount" :label="t('exceptionRule.hits')" width="80" align="right">
          <template #default="{ row }">{{ row.lastHitCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="lastTicketCreatedCount" :label="t('exceptionRule.newTickets')" width="90" align="right">
          <template #default="{ row }">{{ row.lastTicketCreatedCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="lastScanTime" :label="t('exceptionRule.recentScan')" width="160">
          <template #default="{ row }">{{ formatDateTime(row.lastScanTime) }}</template>
        </el-table-column>
        <el-table-column prop="nextScanTime" :label="t('exceptionRule.nextScan')" width="160">
          <template #default="{ row }">{{ formatDateTime(row.nextScanTime) }}</template>
        </el-table-column>
        <el-table-column :label="t('exceptionRule.description')" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ row.lastErrorMessage || row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('exceptionRule.operations')" width="238" align="center" fixed="right">
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
                {{ t('exceptionRule.scan') }}
              </el-button>
              <el-button
                v-permission="'exception-rule:manage'"
                link
                type="primary"
                size="small"
                :icon="EditPen"
                @click="openEditDialog(row)"
              >
                {{ t('exceptionRule.configure') }}
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
                {{ row.enabled ? t('exceptionRule.disable') : t('exceptionRule.enable') }}
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
        @size-change="handleRuleSizeChange"
        @current-change="handleRulePageChange"
      />
    </el-card>

    <el-card shadow="never" class="table-panel">
      <template #header>
        <div class="hit-header">
          <div class="panel-header-title">
            <span>{{ t('exceptionRule.hitTitle') }}</span>
            <el-text type="info" size="small">{{ t('exceptionRule.hitSummary', { total: hitPagination.total }) }}</el-text>
          </div>
          <el-form :model="hitQueryForm" inline class="hit-filter" @submit.prevent>
            <el-form-item :label="t('exceptionRule.type')">
              <el-select v-model="hitQueryForm.ruleType" clearable :placeholder="t('exceptionRule.all')" class="hit-type-select">
                <el-option v-for="item in ruleTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('exceptionRule.source')">
              <el-input
                v-model="hitQueryForm.sourceNo"
                clearable
                :placeholder="t('exceptionRule.sourceNoPlaceholder')"
                class="hit-source-input"
                @keyup.enter="handleHitQuery"
              />
            </el-form-item>
            <el-form-item :label="t('exceptionRule.ticket')">
              <el-input
                v-model="hitQueryForm.ticketId"
                clearable
                class="hit-ticket-input"
                :placeholder="t('exceptionRule.ticketIdPlaceholder')"
              />
            </el-form-item>
            <el-form-item>
              <el-button :icon="Search" :loading="hitLoading" @click="handleHitQuery">{{ t('exceptionRule.filter') }}</el-button>
              <el-button :icon="Refresh" @click="handleHitReset">{{ t('exceptionRule.reset') }}</el-button>
            </el-form-item>
          </el-form>
        </div>
      </template>

      <el-table v-loading="hitLoading" :data="hitData" border stripe row-key="id">
        <el-table-column prop="title" :label="t('exceptionRule.hitSubject')" min-width="260" fixed="left" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="hit-title">
              <strong>{{ row.title }}</strong>
              <span>{{ row.description || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="ruleType" :label="t('exceptionRule.type')" width="150">
          <template #default="{ row }">{{ ruleTypeLabel(row.ruleType) }}</template>
        </el-table-column>
        <el-table-column :label="t('exceptionRule.source')" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="source-cell">
              <span>{{ row.sourceNo || row.sourceId || '-' }}</span>
              <el-text type="info" size="small">{{ row.sourceType }}</el-text>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="triggerValue" :label="t('exceptionRule.triggerValue')" width="110" align="right">
          <template #default="{ row }">{{ row.triggerValue || '-' }}</template>
        </el-table-column>
        <el-table-column prop="thresholdValue" :label="t('exceptionRule.threshold')" width="100" align="right">
          <template #default="{ row }">{{ row.thresholdValue || '-' }}</template>
        </el-table-column>
        <el-table-column prop="ticketId" :label="t('exceptionRule.ticket')" width="110" align="center">
          <template #default="{ row }">
            <el-button v-if="row.ticketId" link type="primary" size="small" :icon="Tickets" @click="openTicket(row)">
              {{ row.ticketId }}
            </el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="hitCount" :label="t('exceptionRule.count')" width="76" align="right">
          <template #default="{ row }">{{ row.hitCount || 1 }}</template>
        </el-table-column>
        <el-table-column prop="firstHitTime" :label="t('exceptionRule.firstHit')" width="160">
          <template #default="{ row }">{{ formatDateTime(row.firstHitTime) }}</template>
        </el-table-column>
        <el-table-column prop="lastHitTime" :label="t('exceptionRule.recentHit')" width="160">
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
        @size-change="handleHitSizeChange"
        @current-change="handleHitPageChange"
      />
    </el-card>

    <el-dialog v-model="editDialogVisible" :title="t('exceptionRule.dialogTitle')" width="620px" destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="96px">
        <el-form-item :label="t('exceptionRule.rule')">
          <el-input :model-value="editTarget?.ruleName || ''" disabled />
        </el-form-item>
        <div class="form-grid">
          <el-form-item :label="t('exceptionRule.threshold')" prop="thresholdValue">
            <el-input-number
              v-model="editForm.thresholdValue"
              :min="0"
              :controls="false"
              class="form-control"
              :placeholder="t('exceptionRule.threshold')"
            />
          </el-form-item>
          <el-form-item :label="t('exceptionRule.unit')" prop="thresholdUnit">
            <el-select v-model="editForm.thresholdUnit" class="form-control">
              <el-option v-for="item in thresholdUnitOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('exceptionRule.priority')" prop="priority">
            <el-select v-model="editForm.priority" class="form-control">
              <el-option v-for="item in priorityOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('exceptionRule.assignee')">
            <el-input
              v-model="editForm.assigneeUserId"
              clearable
              class="form-control"
              :placeholder="t('exceptionRule.userIdPlaceholder')"
            />
          </el-form-item>
          <el-form-item :label="t('exceptionRule.scanInterval')" prop="scheduleIntervalMinutes">
            <el-input-number
              v-model="editForm.scheduleIntervalMinutes"
              :min="5"
              :max="10080"
              :step="5"
              class="form-control"
              :placeholder="t('exceptionRule.minuteUnit')"
            />
          </el-form-item>
        </div>
        <el-form-item :label="t('exceptionRule.description')">
          <el-input
            v-model="editForm.remark"
            type="textarea"
            :rows="3"
            maxlength="512"
            show-word-limit
            :placeholder="t('exceptionRule.descriptionPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">{{ t('exceptionRule.cancel') }}</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="handleSaveEdit">{{ t('exceptionRule.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
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
  type ExceptionRuleHit
} from '@/api/exceptionRule'
import { useExceptionRulePresentation } from '@/composables/useExceptionRulePresentation'
import { useExceptionRuleList } from '@/composables/useExceptionRuleList'
import { useExceptionRuleForm } from '@/composables/useExceptionRuleForm'

const router = useRouter()
const { t } = useI18n()
const editFormRef = ref<FormInstance>()

const notify = {
  onError: (message: string) => ElMessage.error(message),
  onSuccess: (message: string) => ElMessage.success(message),
  onWarning: (message: string) => ElMessage.warning(message)
}

const {
  handleHitPageChange,
  handleHitQuery,
  handleHitReset,
  handleHitSizeChange,
  handleRulePageChange,
  handleRuleQuery,
  handleRuleReset,
  handleRuleSizeChange,
  handleScanAll,
  handleScanRule,
  handleToggleRule,
  hitData,
  hitLoading,
  hitPagination,
  hitQueryForm,
  loadHits,
  loadRules,
  ruleData,
  ruleLoading,
  rulePagination,
  ruleQueryForm,
  scanAllLoading,
  scanResults,
  scanRuleLoadingId,
  toggleLoadingId,
  totalScanDuplicates,
  totalScanHits,
  totalScanTickets
} = useExceptionRuleList(t, {
  getRules: getExceptionRules,
  getHits: getExceptionRuleHits,
  scanRule: scanExceptionRule,
  scanAll: scanAllExceptionRules,
  enableRule: enableExceptionRule,
  disableRule: disableExceptionRule,
  ...notify
})

const {
  formatDateTime,
  priorityLabel,
  priorityOptions,
  priorityType,
  ruleTypeLabel,
  ruleTypeOptions,
  scanStatusLabel,
  scanStatusType,
  summaryItems,
  thresholdLabel,
  thresholdUnitOptions
} = useExceptionRulePresentation(t, ruleData, {
  circleCheck: CircleCheck,
  circleClose: CircleClose,
  warning: Warning,
  tickets: Tickets,
  finished: Finished
})

const {
  editDialogVisible,
  editForm,
  editSubmitting,
  editTarget,
  handleSaveEdit: saveEdit,
  openEditDialog
} = useExceptionRuleForm(t, {
  updateRule: updateExceptionRule,
  onSubmitted: loadRules,
  onError: notify.onError,
  onSuccess: notify.onSuccess
})

const editRules = computed<FormRules>(() => ({
  thresholdValue: [{ required: true, message: t('exceptionRule.validation.threshold'), trigger: 'blur' }],
  thresholdUnit: [{ required: true, message: t('exceptionRule.validation.unit'), trigger: 'change' }],
  priority: [{ required: true, message: t('exceptionRule.validation.priority'), trigger: 'change' }],
  scheduleIntervalMinutes: [{ required: true, message: t('exceptionRule.validation.scanInterval'), trigger: 'blur' }]
}))

const handleSaveEdit = async () => {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid) return
  await saveEdit()
}

const openTicket = (row: ExceptionRuleHit) => {
  router.push({
    path: '/exception-tickets',
    query: row.sourceNo ? { sourceNo: row.sourceNo } : undefined
  })
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
