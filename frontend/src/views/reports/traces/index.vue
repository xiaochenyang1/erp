<template>
  <div class="trace-page">
    <el-card shadow="never" class="query-panel">
      <el-form :model="queryForm" inline @submit.prevent>
        <el-form-item :label="$t('financeReportPages.traces.businessKeyword')">
          <el-input
            v-model="queryForm.keyword"
            class="keyword-input"
            clearable
            :placeholder="$t('financeReportPages.traces.keywordPlaceholder')"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">
            {{ $t('financeReportPages.common.search') }}
          </el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ $t('financeReportPages.common.reset') }}</el-button>
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

    <div class="trace-layout">
      <el-card shadow="never" class="documents-panel">
        <template #header>
          <div class="panel-header">
            <span>{{ $t('financeReportPages.traces.matchedDocuments') }}</span>
            <el-tag size="small">{{ trace.documents.length }}</el-tag>
          </div>
        </template>

        <el-empty
          v-if="!loading && trace.documents.length === 0"
          :description="$t('financeReportPages.traces.documentEmpty')"
          :image-size="120"
        />
        <el-table v-else v-loading="loading" :data="trace.documents" border stripe>
          <el-table-column prop="documentLabel" :label="$t('financeReportPages.traces.type')" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="documentTagType(row.documentType)">
                {{ row.documentLabel }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="bizNo" :label="$t('financeReportPages.traces.documentNo')" min-width="160" />
          <el-table-column prop="title" :label="$t('financeReportPages.traces.title')" min-width="150" show-overflow-tooltip />
          <el-table-column prop="status" :label="$t('financeReportPages.common.status')" width="110">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ traceStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="bizDate" :label="$t('financeReportPages.traces.date')" width="120" />
          <el-table-column prop="totalAmount" :label="$t('financeReportPages.common.amount')" width="130" align="right">
            <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
          </el-table-column>
          <el-table-column :label="$t('financeReportPages.common.actions')" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="Link" @click="goRoute(row.route)">{{ $t('financeReportPages.traces.jump') }}</el-button>
              <el-button
                link
                type="primary"
                @click="openBusinessTimeline(row.documentType, row.documentId, row.bizNo, row)"
              >
                {{ $t('financeReportPages.traces.activity') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never" class="exceptions-panel">
        <template #header>
          <div class="panel-header">
            <span>{{ $t('financeReportPages.traces.relatedExceptions') }}</span>
            <el-tag size="small" :type="trace.summary.openExceptionTicketCount > 0 ? 'warning' : 'info'">
              {{ $t('financeReportPages.traces.openCount', { count: trace.summary.openExceptionTicketCount }) }}
            </el-tag>
          </div>
        </template>

        <el-empty
          v-if="!loading && trace.exceptionTickets.length === 0"
          :description="$t('financeReportPages.traces.exceptionEmpty')"
          :image-size="96"
        />
        <el-table v-else v-loading="loading" :data="trace.exceptionTickets" border stripe>
          <el-table-column prop="ticketNo" :label="$t('financeReportPages.traces.ticketNo')" min-width="170" show-overflow-tooltip />
          <el-table-column prop="title" :label="$t('financeReportPages.traces.exceptionItem')" min-width="190" show-overflow-tooltip />
          <el-table-column prop="priority" :label="$t('financeReportPages.traces.priority')" width="92" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="priorityTagType(row.priority)" effect="plain">
                {{ priorityLabel(row.priority) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" :label="$t('financeReportPages.common.status')" width="96" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="ticketStatusTagType(row.status)">
                {{ ticketStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="assigneeUserId" :label="$t('financeReportPages.traces.assignee')" width="92" align="center">
            <template #default="{ row }">{{ row.assigneeUserId || '-' }}</template>
          </el-table-column>
          <el-table-column prop="dueTime" :label="$t('financeReportPages.traces.dueTime')" width="160">
            <template #default="{ row }">{{ formatDateTime(row.dueTime) || '-' }}</template>
          </el-table-column>
          <el-table-column :label="$t('financeReportPages.common.actions')" width="96" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="Link" @click="goRoute(row.route)">{{ $t('financeReportPages.common.view') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never" class="timeline-panel">
        <template #header>
          <div class="panel-header">
            <span>{{ $t('financeReportPages.traces.lifecycleTimeline') }}</span>
            <el-text type="info" size="small">{{ trace.generatedAt ? $t('financeReportPages.traces.generatedAt', { time: formatDateTime(trace.generatedAt) }) : '' }}</el-text>
          </div>
        </template>

        <el-skeleton v-if="loading" :rows="6" animated />
        <el-empty
          v-else-if="trace.timeline.length === 0"
          :description="$t('financeReportPages.traces.timelineEmpty')"
          :image-size="120"
        />
        <el-timeline v-else>
          <el-timeline-item
            v-for="event in trace.timeline"
            :key="event.id"
            :timestamp="formatDateTime(event.occurredAt)"
            :type="timelineItemType(event.severity)"
            placement="top"
          >
            <div class="event-card" @click="goRoute(event.route)">
              <div class="event-title-row">
                <div class="event-title">
                  <el-icon><component :is="eventIcon(event.eventType)" /></el-icon>
                  <span>{{ event.title }}</span>
                </div>
                <el-tag size="small" :type="eventTagType(event.severity)">
                  {{ traceStatusLabel(event.status || event.eventType) }}
                </el-tag>
              </div>
              <div class="event-biz-no">{{ event.bizNo || '-' }}</div>
              <div class="event-description">{{ event.description || '-' }}</div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </el-card>
    </div>

    <el-dialog v-model="businessTimelineVisible" :title="$t('financeReportPages.traces.businessActivity')" width="760px">
      <template v-if="selectedTimelineDocument">
        <el-descriptions :column="2" border class="timeline-document">
          <el-descriptions-item :label="$t('financeReportPages.traces.documentType')">{{ selectedTimelineDocument.documentLabel }}</el-descriptions-item>
          <el-descriptions-item :label="$t('financeReportPages.traces.documentNo')">{{ selectedTimelineDocument.bizNo }}</el-descriptions-item>
          <el-descriptions-item :label="$t('financeReportPages.traces.businessType')">{{ selectedTimelineDocument.documentType }}</el-descriptions-item>
          <el-descriptions-item :label="$t('financeReportPages.traces.businessId')">{{ selectedTimelineDocument.documentId }}</el-descriptions-item>
        </el-descriptions>

        <el-form :model="timelineCommentForm" class="timeline-comment-form">
          <el-form-item>
            <el-input
              v-model="timelineCommentForm.content"
              type="textarea"
              :rows="3"
              maxlength="1024"
              show-word-limit
              :placeholder="$t('financeReportPages.traces.commentPlaceholder')"
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              :loading="timelineCommentSubmitting"
              @click="submitTimelineComment"
            >
              {{ $t('financeReportPages.traces.submitComment') }}
            </el-button>
            <el-button :icon="Refresh" @click="loadBusinessTimeline">{{ $t('financeReportPages.traces.refreshActivity') }}</el-button>
          </el-form-item>
        </el-form>

        <el-skeleton v-if="businessTimelineLoading" :rows="5" animated />
        <el-empty
          v-else-if="businessTimelineEvents.length === 0"
          :description="$t('financeReportPages.traces.activityEmpty')"
          :image-size="100"
        />
        <el-timeline v-else>
          <el-timeline-item
            v-for="event in businessTimelineEvents"
            :key="event.id"
            :timestamp="formatDateTime(event.createdTime)"
            :type="businessTimelineItemType(event.eventType)"
          >
            <div class="business-event-card">
              <div class="business-event-header">
                <el-tag size="small" :type="businessTimelineTagType(event.eventType)">
                  {{ businessTimelineEventLabel(event.eventType) }}
                </el-tag>
                <span>{{ $t('financeReportPages.traces.operator', { id: event.operatorUserId || '-' }) }}</span>
              </div>
              <div class="business-event-content">{{ event.content }}</div>
              <div v-if="event.attachmentId" class="business-event-extra">
                {{ $t('financeReportPages.traces.attachmentId', { id: event.attachmentId }) }}
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>

        <el-pagination
          v-model:current-page="businessTimelineQuery.pageNo"
          v-model:page-size="businessTimelineQuery.pageSize"
          :total="businessTimelineTotal"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadBusinessTimeline"
          @current-change="loadBusinessTimeline"
        />
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  Box,
  Clock,
  Connection,
  Document,
  Link,
  Money,
  Refresh,
  Search,
  Warning
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { formatLocalizedCurrency, formatLocalizedDateTime, formatLocalizedNumber } from '@/utils/locale'
import {
  getBusinessTrace,
  type BusinessTraceDocument,
  type BusinessTraceResponse
} from '@/api/businessTrace'
import {
  createBusinessTimelineComment,
  getBusinessTimeline,
  type BusinessTimelineEvent
} from '@/api/businessTimeline'

const emptyTrace = (): BusinessTraceResponse => ({
  keyword: '',
  documents: [],
  timeline: [],
  summary: {
    documentCount: 0,
    timelineCount: 0,
    openReceivableAmount: 0,
    openPayableAmount: 0,
    inventoryMovementQuantity: 0,
    failedOperationCount: 0,
    openExceptionTicketCount: 0
  },
  exceptionTickets: [],
  generatedAt: ''
})

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const loading = ref(false)
const trace = ref<BusinessTraceResponse>(emptyTrace())
const businessTimelineVisible = ref(false)
const businessTimelineLoading = ref(false)
const timelineCommentSubmitting = ref(false)
const selectedTimelineDocument = ref<BusinessTraceDocument>()
const businessTimelineEvents = ref<BusinessTimelineEvent[]>([])
const businessTimelineTotal = ref(0)
const queryForm = reactive({
  keyword: ''
})
const businessTimelineQuery = reactive({
  pageNo: 1,
  pageSize: 20
})
const timelineCommentForm = reactive({
  content: ''
})

const summaryItems = computed(() => [
  {
    label: t('financeReportPages.traces.relatedDocuments'),
    value: trace.value.summary.documentCount,
    icon: Document,
    tone: 'blue'
  },
  {
    label: t('financeReportPages.traces.timelineEvents'),
    value: trace.value.summary.timelineCount,
    icon: Clock,
    tone: 'green'
  },
  {
    label: t('financeReportPages.traces.openReceivables'),
    value: formatMoney(trace.value.summary.openReceivableAmount),
    icon: Money,
    tone: 'orange'
  },
  {
    label: t('financeReportPages.traces.openPayables'),
    value: formatMoney(trace.value.summary.openPayableAmount),
    icon: Money,
    tone: 'red'
  },
  {
    label: t('financeReportPages.traces.inventoryMovement'),
    value: formatNumber(trace.value.summary.inventoryMovementQuantity),
    icon: Box,
    tone: 'purple'
  },
  {
    label: t('financeReportPages.traces.failedOperations'),
    value: trace.value.summary.failedOperationCount,
    icon: Warning,
    tone: 'red'
  },
  {
    label: t('financeReportPages.traces.openExceptions'),
    value: trace.value.summary.openExceptionTicketCount,
    icon: Warning,
    tone: 'orange'
  }
])

const handleSearch = async () => {
  const keyword = queryForm.keyword.trim()
  if (!keyword) {
    trace.value = emptyTrace()
    return
  }
  loading.value = true
  try {
    trace.value = await getBusinessTrace({ keyword })
    router.replace({ path: route.path, query: { keyword } })
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  queryForm.keyword = ''
  trace.value = emptyTrace()
  router.replace({ path: route.path })
}

const goRoute = (target?: string) => {
  if (!target) {
    ElMessage.info(t('financeReportPages.traces.message.noRoute'))
    return
  }
  router.push(normalizeTraceRoute(target))
}

const normalizeTraceRoute = (target: string) => target

const openBusinessTimeline = (
  businessType: string,
  businessId: string,
  businessNo: string,
  row: BusinessTraceDocument
) => {
  selectedTimelineDocument.value = {
    ...row,
    documentType: businessType,
    documentId: businessId,
    bizNo: businessNo
  }
  businessTimelineQuery.pageNo = 1
  timelineCommentForm.content = ''
  businessTimelineVisible.value = true
  loadBusinessTimeline()
}

const loadBusinessTimeline = async () => {
  if (!selectedTimelineDocument.value) return
  businessTimelineLoading.value = true
  try {
    const page = await getBusinessTimeline({
      pageNo: businessTimelineQuery.pageNo,
      pageSize: businessTimelineQuery.pageSize,
      businessType: selectedTimelineDocument.value.documentType,
      businessId: selectedTimelineDocument.value.documentId,
      businessNo: selectedTimelineDocument.value.bizNo
    })
    businessTimelineEvents.value = page.records
    businessTimelineTotal.value = page.total
  } finally {
    businessTimelineLoading.value = false
  }
}

const submitTimelineComment = async () => {
  if (!selectedTimelineDocument.value) return
  const content = timelineCommentForm.content.trim()
  if (!content) {
    ElMessage.warning(t('financeReportPages.traces.message.commentRequired'))
    return
  }

  timelineCommentSubmitting.value = true
  try {
    await createBusinessTimelineComment({
      businessType: selectedTimelineDocument.value.documentType,
      businessId: selectedTimelineDocument.value.documentId,
      businessNo: selectedTimelineDocument.value.bizNo,
      content
    })
    timelineCommentForm.content = ''
    ElMessage.success(t('financeReportPages.traces.message.commentSubmitted'))
    businessTimelineQuery.pageNo = 1
    await loadBusinessTimeline()
  } finally {
    timelineCommentSubmitting.value = false
  }
}

const formatMoney = (amount?: number) => {
  return formatLocalizedCurrency(Number(amount || 0))
}

const formatNumber = (value?: number) => {
  return formatLocalizedNumber(Number(value || 0), {
    minimumFractionDigits: 4,
    maximumFractionDigits: 4
  })
}

const formatDateTime = (value?: string) => {
  return formatLocalizedDateTime(value)
}

const documentTagType = (type: string) => {
  if (type.includes('SALES')) return 'success'
  if (type.includes('PURCHASE')) return 'primary'
  if (type.includes('RECEIVABLE')) return 'warning'
  if (type.includes('PAYABLE')) return 'danger'
  return 'info'
}

const timelineItemType = (severity?: string) => {
  if (severity === 'ERROR') return 'danger'
  if (severity === 'WARNING') return 'warning'
  return 'primary'
}

const eventTagType = (severity?: string) => {
  if (severity === 'ERROR') return 'danger'
  if (severity === 'WARNING') return 'warning'
  return 'info'
}

const priorityLabel = (priority?: string) => {
  const map: Record<string, string> = {
    LOW: t('financeReportPages.traces.priorityLabel.low'),
    MEDIUM: t('financeReportPages.traces.priorityLabel.medium'),
    HIGH: t('financeReportPages.traces.priorityLabel.high'),
    URGENT: t('financeReportPages.traces.priorityLabel.urgent')
  }
  return priority ? map[priority] || priority : '-'
}

const priorityTagType = (priority?: string) => {
  const map: Record<string, 'info' | 'primary' | 'success' | 'warning' | 'danger'> = {
    LOW: 'info',
    MEDIUM: 'primary',
    HIGH: 'warning',
    URGENT: 'danger'
  }
  return priority ? map[priority] || 'info' : 'info'
}

const ticketStatusLabel = (status?: string) => {
  const map: Record<string, string> = {
    OPEN: t('financeReportPages.traces.ticketStatus.open'),
    PROCESSING: t('financeReportPages.traces.ticketStatus.processing'),
    RESOLVED: t('financeReportPages.traces.ticketStatus.resolved'),
    CLOSED: t('financeReportPages.traces.ticketStatus.closed')
  }
  return status ? map[status] || status : '-'
}

const ticketStatusTagType = (status?: string) => {
  const map: Record<string, 'info' | 'primary' | 'success' | 'warning' | 'danger'> = {
    OPEN: 'warning',
    PROCESSING: 'primary',
    RESOLVED: 'success',
    CLOSED: 'info'
  }
  return status ? map[status] || 'info' : 'info'
}

const traceStatusLabel = (status?: string) => {
  const labels: Record<string, string> = {
    DRAFT: t('financeReportPages.traces.status.draft'),
    SUBMITTED: t('financeReportPages.traces.status.submitted'),
    PENDING: t('financeReportPages.traces.status.pending'),
    APPROVED: t('financeReportPages.traces.status.approved'),
    REJECTED: t('financeReportPages.traces.status.rejected'),
    POSTED: t('financeReportPages.traces.status.posted'),
    COMPLETED: t('financeReportPages.traces.status.completed'),
    CANCELLED: t('financeReportPages.traces.status.cancelled'),
    CLOSED: t('financeReportPages.traces.status.closed'),
    SUCCESS: t('financeReportPages.traces.status.success'),
    FAILED: t('financeReportPages.traces.status.failed')
  }
  return status ? labels[status] || status : '-'
}

const eventIcon = (type: string) => {
  const iconMap: Record<string, unknown> = {
    ORDER: Document,
    FULFILLMENT: Box,
    FINANCE: Money,
    INVENTORY: Box,
    WORKFLOW: Connection,
    OPERATION_LOG: Warning
  }
  return iconMap[type] || Document
}

const businessTimelineEventLabel = (type: string) => {
  const map: Record<string, string> = {
    COMMENT: t('financeReportPages.traces.event.comment'),
    ATTACHMENT_UPLOADED: t('financeReportPages.traces.event.uploaded'),
    ATTACHMENT_DELETED: t('financeReportPages.traces.event.deleted')
  }
  return map[type] || type
}

const businessTimelineTagType = (type: string) => {
  const map: Record<string, 'info' | 'primary' | 'success' | 'warning' | 'danger'> = {
    COMMENT: 'primary',
    ATTACHMENT_UPLOADED: 'success',
    ATTACHMENT_DELETED: 'warning'
  }
  return map[type] || 'info'
}

const businessTimelineItemType = (type: string) => {
  if (type === 'ATTACHMENT_DELETED') return 'warning'
  if (type === 'ATTACHMENT_UPLOADED') return 'success'
  return 'primary'
}

const applyKeywordFromRoute = () => {
  const keyword = route.query.keyword
  if (typeof keyword === 'string' && keyword.trim()) {
    queryForm.keyword = keyword
    handleSearch()
  }
}

onMounted(applyKeywordFromRoute)

// 已在追踪页时，Navbar 全局搜索只改变 query.keyword，需 watch 触发重新检索
watch(() => route.query.keyword, applyKeywordFromRoute)
</script>

<style scoped lang="scss">
.trace-page {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .query-panel {
    :deep(.el-card__body) {
      padding-bottom: 2px;
    }
  }

  .keyword-input {
    width: 360px;
  }

  .summary-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
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
    }

    strong {
      color: #303133;
      font-size: 18px;
      font-weight: 700;
      line-height: 1.2;
      word-break: break-word;
    }
  }

  .trace-layout {
    display: grid;
    grid-template-columns: minmax(0, 1.35fr) minmax(360px, 0.65fr);
    gap: 16px;
    align-items: start;
  }

  .documents-panel,
  .exceptions-panel,
  .timeline-panel {
    min-width: 0;
  }

  .panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    min-height: 24px;
  }

  .event-card {
    padding: 10px 12px;
    border: 1px solid #ebeef5;
    border-radius: 6px;
    background: #fff;
    cursor: pointer;
    transition:
      border-color 0.2s,
      box-shadow 0.2s;

    &:hover {
      border-color: #409eff;
      box-shadow: 0 4px 12px rgba(64, 158, 255, 0.12);
    }
  }

  .event-title-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .event-title {
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
    color: #303133;
    font-size: 14px;
    font-weight: 600;

    span {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .event-biz-no {
    margin-top: 6px;
    color: #303133;
    font-size: 13px;
    font-weight: 600;
  }

  .event-description {
    margin-top: 4px;
    color: #606266;
    font-size: 13px;
    line-height: 1.5;
    word-break: break-word;
  }

  .timeline-document {
    margin-bottom: 14px;
  }

  .timeline-comment-form {
    margin-bottom: 18px;

    :deep(.el-form-item:last-child) {
      margin-bottom: 0;
    }
  }

  .business-event-card {
    padding: 10px 12px;
    border: 1px solid #ebeef5;
    border-radius: 6px;
    background: #fff;
  }

  .business-event-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    color: #909399;
    font-size: 12px;
  }

  .business-event-content {
    margin-top: 8px;
    color: #303133;
    line-height: 1.6;
    word-break: break-word;
  }

  .business-event-extra {
    margin-top: 6px;
    color: #909399;
    font-size: 12px;
  }
}

@media (max-width: 1280px) {
  .trace-page {
    .summary-grid {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }

    .trace-layout {
      grid-template-columns: 1fr;
    }
  }
}

@media (max-width: 720px) {
  .trace-page {
    .keyword-input {
      width: 100%;
    }

    .summary-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }
}
</style>
