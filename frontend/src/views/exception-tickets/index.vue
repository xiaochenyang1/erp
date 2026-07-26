<template>
  <div class="exception-ticket-page">
    <el-card shadow="never" class="filter-panel">
      <el-form :model="queryForm" inline @submit.prevent>
        <el-form-item :label="t('exceptionTicket.keyword')">
          <el-input
            v-model="queryForm.keyword"
            class="keyword-input"
            clearable
            :placeholder="t('exceptionTicket.keywordPlaceholder')"
            @keyup.enter="handleQuery"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item :label="t('exceptionTicket.status')">
          <el-select v-model="queryForm.status" clearable :placeholder="t('exceptionTicket.all')" class="small-select">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('exceptionTicket.priority')">
          <el-select v-model="queryForm.priority" clearable :placeholder="t('exceptionTicket.all')" class="small-select">
            <el-option v-for="item in priorityOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('exceptionTicket.category')">
          <el-select v-model="queryForm.category" clearable filterable :placeholder="t('exceptionTicket.all')" class="category-select">
            <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('exceptionTicket.assignee')">
          <el-input
            v-model="queryForm.assigneeUserId"
            clearable
            class="user-input"
            :placeholder="t('exceptionTicket.userIdPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('exceptionTicket.sourceNo')">
          <el-input v-model="queryForm.sourceNo" clearable :placeholder="t('exceptionTicket.sourceDocumentPlaceholder')" class="source-input" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="queryForm.overdueOnly">{{ t('exceptionTicket.overdueOnly') }}</el-checkbox>
        </el-form-item>
        <el-form-item class="filter-actions">
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleQuery">{{ t('exceptionTicket.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('exceptionTicket.reset') }}</el-button>
          <el-button v-permission="'exception-ticket:manage'" type="success" :icon="Plus" @click="openCreateDialog">
            {{ t('exceptionTicket.create') }}
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

    <el-card shadow="never" class="table-panel">
      <template #header>
        <div class="panel-header">
          <span>{{ t('exceptionTicket.title') }}</span>
          <el-text type="info" size="small">{{ t('exceptionTicket.pageSummary', { current: tableData.length, total: pagination.total }) }}</el-text>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe row-key="id">
        <el-table-column type="expand" width="42">
          <template #default="{ row }">
            <div class="event-panel">
              <el-empty v-if="!row.events?.length" :description="t('exceptionTicket.noEvents')" :image-size="88" />
              <el-timeline v-else>
                <el-timeline-item
                  v-for="event in row.events"
                  :key="event.id || `${event.action}-${event.createdTime}`"
                  :timestamp="formatDateTime(event.createdTime)"
                  :type="eventType(event.action)"
                  placement="top"
                >
                  <div class="event-row">
                    <strong>{{ actionLabel(event.action) }}</strong>
                    <span>{{ event.fromStatus ? statusLabel(event.fromStatus) : '-' }} -> {{ statusLabel(event.toStatus) }}</span>
                    <el-text type="info" size="small">{{ t('exceptionTicket.operator', { id: event.operatorUserId || '-' }) }}</el-text>
                  </div>
                  <p>{{ event.comment || '-' }}</p>
                </el-timeline-item>
              </el-timeline>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="ticketNo" :label="t('exceptionTicket.ticketNo')" width="170" fixed="left" />
        <el-table-column :label="t('exceptionTicket.issue')" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="ticket-title">
              <strong>{{ row.title }}</strong>
              <span>{{ row.description || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="category" :label="t('exceptionTicket.category')" width="130">
          <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
        </el-table-column>
        <el-table-column prop="priority" :label="t('exceptionTicket.priority')" width="96" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="priorityType(row.priority)" effect="plain">
              {{ priorityLabel(row.priority) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('exceptionTicket.status')" width="104" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('exceptionTicket.source')" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="source-cell">
              <span>{{ row.sourceNo || '-' }}</span>
              <el-text type="info" size="small">{{ row.sourceType || '-' }}</el-text>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="assigneeUserId" :label="t('exceptionTicket.assignee')" width="100" align="center">
          <template #default="{ row }">{{ row.assigneeUserId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="dueTime" :label="t('exceptionTicket.dueTime')" width="160">
          <template #default="{ row }">
            <el-text :type="isOverdue(row) ? 'danger' : 'info'">
              {{ formatDateTime(row.dueTime) || '-' }}
            </el-text>
          </template>
        </el-table-column>
        <el-table-column prop="updatedTime" :label="t('exceptionTicket.updatedAt')" width="160">
          <template #default="{ row }">{{ formatDateTime(row.updatedTime) }}</template>
        </el-table-column>
        <el-table-column :label="t('exceptionTicket.operations')" width="280" align="center" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button
                v-if="row.traceable"
                v-permission="'report:view'"
                link
                type="primary"
                size="small"
                :icon="Search"
                @click="goBusinessTrace(row)"
              >
                {{ t('exceptionTicket.trace') }}
              </el-button>
              <el-button
                v-if="row.status !== 'CLOSED'"
                v-permission="'exception-ticket:manage'"
                link
                type="primary"
                size="small"
                :icon="User"
                @click="openActionDialog('assign', row)"
              >
                {{ t('exceptionTicket.assign') }}
              </el-button>
              <el-button
                v-if="row.status === 'OPEN' || row.status === 'PROCESSING'"
                v-permission="'exception-ticket:manage'"
                link
                type="primary"
                size="small"
                :icon="Clock"
                @click="openActionDialog('start', row)"
              >
                {{ t('exceptionTicket.start') }}
              </el-button>
              <el-button
                v-if="row.status === 'OPEN' || row.status === 'PROCESSING' || row.status === 'RESOLVED'"
                v-permission="'exception-ticket:manage'"
                link
                type="success"
                size="small"
                :icon="Finished"
                @click="openActionDialog('resolve', row)"
              >
                {{ t('exceptionTicket.resolve') }}
              </el-button>
              <el-button
                v-if="row.status === 'RESOLVED'"
                v-permission="'exception-ticket:manage'"
                link
                type="danger"
                size="small"
                :icon="Close"
                @click="openActionDialog('close', row)"
              >
                {{ t('exceptionTicket.close') }}
              </el-button>
            </div>
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

    <el-dialog v-model="createDialogVisible" :title="t('exceptionTicket.createDialog')" width="720px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="92px">
        <el-form-item :label="t('exceptionTicket.subjectTitle')" prop="title">
          <el-input v-model="createForm.title" maxlength="128" show-word-limit :placeholder="t('exceptionTicket.titlePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('exceptionTicket.description')">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            maxlength="1024"
            show-word-limit
            :placeholder="t('exceptionTicket.descriptionPlaceholder')"
          />
        </el-form-item>
        <div class="form-grid">
          <el-form-item :label="t('exceptionTicket.category')" prop="category">
            <el-select v-model="createForm.category" filterable class="form-control">
              <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('exceptionTicket.priority')" prop="priority">
            <el-select v-model="createForm.priority" class="form-control">
              <el-option v-for="item in priorityOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('exceptionTicket.assignee')">
            <el-input
              v-model="createForm.assigneeUserId"
              clearable
              class="form-control"
              :placeholder="t('exceptionTicket.userIdPlaceholder')"
            />
          </el-form-item>
          <el-form-item :label="t('exceptionTicket.dueTime')">
            <el-date-picker
              v-model="createForm.dueTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              :placeholder="t('exceptionTicket.selectTime')"
              class="form-control"
            />
          </el-form-item>
          <el-form-item :label="t('exceptionTicket.sourceType')">
            <el-input v-model="createForm.sourceType" placeholder="LOW_STOCK / ORDER" />
          </el-form-item>
          <el-form-item :label="t('exceptionTicket.sourceId')">
            <el-input v-model="createForm.sourceId" clearable class="form-control" :placeholder="t('exceptionTicket.sourceIdPlaceholder')" />
          </el-form-item>
        </div>
        <el-form-item :label="t('exceptionTicket.sourceNo')">
          <el-input v-model="createForm.sourceNo" maxlength="128" :placeholder="t('exceptionTicket.businessNoPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('exceptionTicket.sourceRoute')">
          <el-input v-model="createForm.sourceRoute" maxlength="512" placeholder="/reports/traces?keyword=..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ t('exceptionTicket.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleCreate">{{ t('exceptionTicket.createAction') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="actionDialogVisible" :title="actionDialogTitle" width="520px" destroy-on-close>
      <el-form :model="actionForm" label-width="86px">
        <el-form-item v-if="actionMode === 'assign'" :label="t('exceptionTicket.assignee')">
          <el-input
            v-model="actionForm.assigneeUserId"
            clearable
            class="form-control"
            :placeholder="t('exceptionTicket.userIdPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('exceptionTicket.actionComment')">
          <el-input
            v-model="actionForm.comment"
            type="textarea"
            :rows="4"
            maxlength="512"
            show-word-limit
            :placeholder="actionPlaceholder"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionDialogVisible = false">{{ t('exceptionTicket.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleAction">{{ t('exceptionTicket.confirm') }}</el-button>
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
import { Clock, Close, Finished, Plus, Refresh, Search, User, Warning } from '@element-plus/icons-vue'
import {
  assignExceptionTicket,
  closeExceptionTicket,
  createExceptionTicket,
  getExceptionTickets,
  resolveExceptionTicket,
  startExceptionTicket,
  type ExceptionTicket
} from '@/api/exceptionTicket'
import { useExceptionTicketPresentation } from '@/composables/useExceptionTicketPresentation'
import { useExceptionTicketList } from '@/composables/useExceptionTicketList'
import { useExceptionTicketForm } from '@/composables/useExceptionTicketForm'

const router = useRouter()
const { t } = useI18n()
const createFormRef = ref<FormInstance>()

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
} = useExceptionTicketList(t, {
  getTickets: getExceptionTickets,
  onError: notify.onError
})

const {
  actionLabel,
  categoryLabel,
  categoryOptions,
  eventType,
  formatDateTime,
  isOverdue,
  priorityLabel,
  priorityOptions,
  priorityType,
  statusLabel,
  statusOptions,
  statusType,
  summaryItems
} = useExceptionTicketPresentation(t, tableData, {
  warning: Warning,
  clock: Clock,
  finished: Finished
})

const {
  actionDialogTitle,
  actionDialogVisible,
  actionForm,
  actionMode,
  actionPlaceholder,
  createDialogVisible,
  createForm,
  handleAction,
  handleCreate: createTicket,
  openActionDialog,
  openCreateDialog,
  submitLoading
} = useExceptionTicketForm(t, {
  createTicket: createExceptionTicket,
  assignTicket: assignExceptionTicket,
  startTicket: startExceptionTicket,
  resolveTicket: resolveExceptionTicket,
  closeTicket: closeExceptionTicket,
  onSubmitted: loadData,
  ...notify
})

const createRules = computed<FormRules>(() => ({
  title: [{ required: true, message: t('exceptionTicket.validation.title'), trigger: 'blur' }],
  category: [{ required: true, message: t('exceptionTicket.validation.category'), trigger: 'change' }],
  priority: [{ required: true, message: t('exceptionTicket.validation.priority'), trigger: 'change' }]
}))

const handleCreate = async () => {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  await createTicket()
}

const goBusinessTrace = (row: ExceptionTicket) => {
  if (row.traceRoute) {
    router.push(row.traceRoute)
    return
  }
  if (row.traceKeyword) {
    router.push({ path: '/reports/traces', query: { keyword: row.traceKeyword } })
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.exception-ticket-page {
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

  .small-select {
    width: 118px;
  }

  .category-select {
    width: 142px;
  }

  .source-input {
    width: 150px;
  }

  .user-input {
    width: 112px;
  }

  .filter-actions {
    :deep(.el-button) {
      min-height: 32px;
      transition-property: scale, background-color, border-color, color;
      transition-duration: 150ms;
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
      align-items: center;
      gap: 12px;
      min-height: 78px;
    }
  }

  .summary-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    flex: 0 0 36px;
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

    &.purple {
      color: #7c3aed;
      background: #f5f3ff;
    }
  }

  .summary-content {
    display: flex;
    flex-direction: column;
    gap: 6px;
    min-width: 0;

    span {
      color: #606266;
      font-size: 13px;
      text-wrap: pretty;
    }

    strong {
      color: #303133;
      font-size: 20px;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
      line-height: 1.15;
    }
  }

  .panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    min-height: 24px;
  }

  .ticket-title,
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

  .row-actions {
    display: flex;
    min-height: 28px;
    align-items: center;
    justify-content: center;
    gap: 2px;
    white-space: nowrap;
  }

  .event-panel {
    padding: 4px 16px 0;

    :deep(.el-timeline) {
      padding-left: 4px;
    }

    p {
      margin: 4px 0 0;
      color: #606266;
      line-height: 1.5;
      text-wrap: pretty;
    }
  }

  .event-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;

    strong,
    span {
      font-variant-numeric: tabular-nums;
    }
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
  .exception-ticket-page {
    .summary-grid {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }
  }
}

@media (max-width: 720px) {
  .exception-ticket-page {
    .keyword-input,
    .small-select,
    .category-select,
    .source-input,
    .user-input {
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
