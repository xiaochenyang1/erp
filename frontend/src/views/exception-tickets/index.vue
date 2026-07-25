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
        @size-change="handlePageChange"
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
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Clock, Close, Finished, Plus, Refresh, Search, User, Warning } from '@element-plus/icons-vue'
import { formatLocalizedDateTime } from '@/utils/locale'
import {
  assignExceptionTicket,
  closeExceptionTicket,
  createExceptionTicket,
  getExceptionTickets,
  resolveExceptionTicket,
  startExceptionTicket,
  type ExceptionTicket,
  type ExceptionTicketCreateRequest,
  type ExceptionTicketPriority,
  type ExceptionTicketQuery,
  type ExceptionTicketStatus
} from '@/api/exceptionTicket'

type Option = { label: string; value: string }
type ActionMode = 'assign' | 'start' | 'resolve' | 'close'

const router = useRouter()
const { t } = useI18n()

const statusOptions = computed<Option[]>(() => [
  { label: t('exceptionTicket.statuses.open'), value: 'OPEN' },
  { label: t('exceptionTicket.statuses.processing'), value: 'PROCESSING' },
  { label: t('exceptionTicket.statuses.resolved'), value: 'RESOLVED' },
  { label: t('exceptionTicket.statuses.closed'), value: 'CLOSED' }
])

const priorityOptions = computed<Option[]>(() => [
  { label: t('exceptionTicket.priorities.low'), value: 'LOW' },
  { label: t('exceptionTicket.priorities.medium'), value: 'MEDIUM' },
  { label: t('exceptionTicket.priorities.high'), value: 'HIGH' },
  { label: t('exceptionTicket.priorities.urgent'), value: 'URGENT' }
])

const categoryOptions = computed<Option[]>(() => [
  { label: t('exceptionTicket.categories.general'), value: 'GENERAL' },
  { label: t('exceptionTicket.categories.lowStock'), value: 'LOW_STOCK' },
  { label: t('exceptionTicket.categories.paymentOverdue'), value: 'PAYMENT_OVERDUE' },
  { label: t('exceptionTicket.categories.deliveryDelay'), value: 'DELIVERY_DELAY' },
  { label: t('exceptionTicket.categories.qualityIssue'), value: 'QUALITY_ISSUE' },
  { label: t('exceptionTicket.categories.systemError'), value: 'SYSTEM_ERROR' }
])

const queryForm = reactive<ExceptionTicketQuery>({
  keyword: '',
  status: '',
  priority: '',
  category: '',
  assigneeUserId: undefined,
  sourceNo: '',
  overdueOnly: false
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref<ExceptionTicket[]>([])

const createDialogVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive<ExceptionTicketCreateRequest>({
  category: 'GENERAL',
  priority: 'HIGH',
  title: '',
  description: '',
  sourceType: '',
  sourceId: '',
  sourceNo: '',
  sourceRoute: '',
  assigneeUserId: undefined,
  dueTime: ''
})

const createRules = computed<FormRules>(() => ({
  title: [{ required: true, message: t('exceptionTicket.validation.title'), trigger: 'blur' }],
  category: [{ required: true, message: t('exceptionTicket.validation.category'), trigger: 'change' }],
  priority: [{ required: true, message: t('exceptionTicket.validation.priority'), trigger: 'change' }]
}))

const actionDialogVisible = ref(false)
const actionMode = ref<ActionMode>('assign')
const actionTarget = ref<ExceptionTicket>()
const actionForm = reactive({
  assigneeUserId: undefined as string | undefined,
  comment: ''
})

const summaryItems = computed(() => [
  {
    label: t('exceptionTicket.summary.open'),
    value: countByStatus('OPEN'),
    icon: Warning,
    tone: 'orange'
  },
  {
    label: t('exceptionTicket.summary.processing'),
    value: countByStatus('PROCESSING'),
    icon: Clock,
    tone: 'blue'
  },
  {
    label: t('exceptionTicket.summary.resolved'),
    value: countByStatus('RESOLVED'),
    icon: Finished,
    tone: 'green'
  },
  {
    label: t('exceptionTicket.summary.overdue'),
    value: tableData.value.filter(isOverdue).length,
    icon: Warning,
    tone: 'red'
  },
  {
    label: t('exceptionTicket.summary.highPriority'),
    value: tableData.value.filter((item) => item.priority === 'HIGH' || item.priority === 'URGENT').length,
    icon: Warning,
    tone: 'purple'
  }
])

const actionDialogTitle = computed(() => {
  const titleMap: Record<ActionMode, string> = {
    assign: t('exceptionTicket.dialog.assign'),
    start: t('exceptionTicket.dialog.start'),
    resolve: t('exceptionTicket.dialog.resolve'),
    close: t('exceptionTicket.dialog.close')
  }
  const ticketNo = actionTarget.value?.ticketNo ? ` - ${actionTarget.value.ticketNo}` : ''
  return `${titleMap[actionMode.value]}${ticketNo}`
})

const actionPlaceholder = computed(() => {
  const placeholderMap: Record<ActionMode, string> = {
    assign: t('exceptionTicket.actionPlaceholders.assign'),
    start: t('exceptionTicket.actionPlaceholders.start'),
    resolve: t('exceptionTicket.actionPlaceholders.resolve'),
    close: t('exceptionTicket.actionPlaceholders.close')
  }
  return placeholderMap[actionMode.value]
})

const loadData = async () => {
  loading.value = true
  try {
    const page = await getExceptionTickets(buildQueryParams())
    tableData.value = page.records || []
    pagination.total = page.total || 0
  } finally {
    loading.value = false
  }
}

const buildQueryParams = (): ExceptionTicketQuery => ({
  ...queryForm,
  assigneeUserId: normalizeOptionalId(queryForm.assigneeUserId),
  keyword: queryForm.keyword || undefined,
  status: queryForm.status || undefined,
  priority: queryForm.priority || undefined,
  category: queryForm.category || undefined,
  sourceNo: queryForm.sourceNo || undefined,
  overdueOnly: queryForm.overdueOnly || undefined,
  pageNo: pagination.page,
  pageSize: pagination.size
})

const handleQuery = () => {
  pagination.page = 1
  loadData()
}

const handlePageChange = () => {
  loadData()
}

const handleReset = () => {
  queryForm.keyword = ''
  queryForm.status = ''
  queryForm.priority = ''
  queryForm.category = ''
  queryForm.assigneeUserId = undefined
  queryForm.sourceNo = ''
  queryForm.overdueOnly = false
  pagination.page = 1
  loadData()
}

const openCreateDialog = () => {
  resetCreateForm()
  createDialogVisible.value = true
}

const resetCreateForm = () => {
  createForm.category = 'GENERAL'
  createForm.priority = 'HIGH'
  createForm.title = ''
  createForm.description = ''
  createForm.sourceType = ''
  createForm.sourceId = ''
  createForm.sourceNo = ''
  createForm.sourceRoute = ''
  createForm.assigneeUserId = undefined
  createForm.dueTime = ''
}

const handleCreate = async () => {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    await createExceptionTicket({
      ...createForm,
      dueTime: toIsoDateTime(createForm.dueTime),
      sourceType: createForm.sourceType?.trim() || undefined,
      sourceId: createForm.sourceId?.trim() || undefined,
      sourceNo: createForm.sourceNo?.trim() || undefined,
      sourceRoute: createForm.sourceRoute?.trim() || undefined,
      assigneeUserId: normalizeOptionalId(createForm.assigneeUserId),
      description: createForm.description?.trim() || undefined
    })
    ElMessage.success(t('exceptionTicket.message.created'))
    createDialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

const openActionDialog = (mode: ActionMode, row: ExceptionTicket) => {
  actionMode.value = mode
  actionTarget.value = row
  actionForm.assigneeUserId = row.assigneeUserId
  actionForm.comment = ''
  actionDialogVisible.value = true
}

const handleAction = async () => {
  if (!actionTarget.value) return
  submitLoading.value = true
  try {
    const id = actionTarget.value.id
    const comment = actionForm.comment?.trim() || undefined
    if (actionMode.value === 'assign') {
      await assignExceptionTicket(id, { assigneeUserId: normalizeOptionalId(actionForm.assigneeUserId), comment })
    } else if (actionMode.value === 'start') {
      await startExceptionTicket(id, { comment })
    } else if (actionMode.value === 'resolve') {
      await resolveExceptionTicket(id, { comment })
    } else {
      await closeExceptionTicket(id, { comment })
    }
    ElMessage.success(t('exceptionTicket.message.actionSubmitted'))
    actionDialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
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

const countByStatus = (status: ExceptionTicketStatus) => {
  return tableData.value.filter((item) => item.status === status).length
}

const statusLabel = (status?: string) => {
  return statusOptions.value.find((item) => item.value === status)?.label || status || '-'
}

const priorityLabel = (priority?: string) => {
  return priorityOptions.value.find((item) => item.value === priority)?.label || priority || '-'
}

const categoryLabel = (category?: string) => {
  return categoryOptions.value.find((item) => item.value === category)?.label || category || '-'
}

const actionLabel = (action?: string) => {
  const labelMap: Record<string, string> = {
    CREATE: t('exceptionTicket.eventActions.create'),
    ASSIGN: t('exceptionTicket.eventActions.assign'),
    START: t('exceptionTicket.eventActions.start'),
    RESOLVE: t('exceptionTicket.eventActions.resolve'),
    CLOSE: t('exceptionTicket.eventActions.close')
  }
  return action ? labelMap[action] || action : '-'
}

const statusType = (status: string) => {
  const typeMap: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    OPEN: 'warning',
    PROCESSING: 'primary',
    RESOLVED: 'success',
    CLOSED: 'info'
  }
  return typeMap[status] || 'info'
}

const priorityType = (priority: ExceptionTicketPriority | string) => {
  const typeMap: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    LOW: 'info',
    MEDIUM: 'primary',
    HIGH: 'warning',
    URGENT: 'danger'
  }
  return typeMap[priority] || 'info'
}

const eventType = (action: string) => {
  if (action === 'CLOSE') return 'info'
  if (action === 'RESOLVE') return 'success'
  if (action === 'ASSIGN') return 'warning'
  return 'primary'
}

const isOverdue = (ticket: ExceptionTicket) => {
  if (!ticket.dueTime || ticket.status === 'RESOLVED' || ticket.status === 'CLOSED') return false
  return Date.parse(ticket.dueTime) < Date.now()
}

const formatDateTime = (value?: string) => {
  return formatLocalizedDateTime(value)
}

const normalizeOptionalId = (value?: string | number) => {
  const normalized = value == null ? '' : String(value).trim()
  return normalized || undefined
}

const toIsoDateTime = (value?: string) => {
  return value ? value.replace(' ', 'T') : undefined
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
