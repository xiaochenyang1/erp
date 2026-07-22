<template>
  <div class="trace-page">
    <el-card shadow="never" class="query-panel">
      <el-form :model="queryForm" inline @submit.prevent>
        <el-form-item label="业务关键字">
          <el-input
            v-model="queryForm.keyword"
            class="keyword-input"
            clearable
            placeholder="输入订单号、收发货单号、应收应付单号"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">
            查询
          </el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
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
            <span>匹配单据</span>
            <el-tag size="small">{{ trace.documents.length }}</el-tag>
          </div>
        </template>

        <el-empty
          v-if="!loading && trace.documents.length === 0"
          description="输入业务单号后查看关联单据"
          :image-size="120"
        />
        <el-table v-else v-loading="loading" :data="trace.documents" border stripe>
          <el-table-column prop="documentLabel" label="类型" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="documentTagType(row.documentType)">
                {{ row.documentLabel }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="bizNo" label="单据编号" min-width="160" />
          <el-table-column prop="title" label="摘要" min-width="150" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.status || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="bizDate" label="日期" width="120" />
          <el-table-column prop="totalAmount" label="金额" width="130" align="right">
            <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="Link" @click="goRoute(row.route)">跳转</el-button>
              <el-button
                link
                type="primary"
                @click="openBusinessTimeline(row.documentType, row.documentId, row.bizNo, row)"
              >
                动态
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never" class="exceptions-panel">
        <template #header>
          <div class="panel-header">
            <span>关联异常</span>
            <el-tag size="small" :type="trace.summary.openExceptionTicketCount > 0 ? 'warning' : 'info'">
              未关 {{ trace.summary.openExceptionTicketCount }}
            </el-tag>
          </div>
        </template>

        <el-empty
          v-if="!loading && trace.exceptionTickets.length === 0"
          description="暂无关联异常工单"
          :image-size="96"
        />
        <el-table v-else v-loading="loading" :data="trace.exceptionTickets" border stripe>
          <el-table-column prop="ticketNo" label="工单号" min-width="170" show-overflow-tooltip />
          <el-table-column prop="title" label="异常事项" min-width="190" show-overflow-tooltip />
          <el-table-column prop="priority" label="优先级" width="92" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="priorityTagType(row.priority)" effect="plain">
                {{ priorityLabel(row.priority) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="96" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="ticketStatusTagType(row.status)">
                {{ ticketStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="assigneeUserId" label="处理人" width="92" align="center">
            <template #default="{ row }">{{ row.assigneeUserId || '-' }}</template>
          </el-table-column>
          <el-table-column prop="dueTime" label="截止时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.dueTime) || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="96" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="Link" @click="goRoute(row.route)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never" class="timeline-panel">
        <template #header>
          <div class="panel-header">
            <span>生命周期时间线</span>
            <el-text type="info" size="small">{{ trace.generatedAt ? `生成于 ${formatDateTime(trace.generatedAt)}` : '' }}</el-text>
          </div>
        </template>

        <el-skeleton v-if="loading" :rows="6" animated />
        <el-empty
          v-else-if="trace.timeline.length === 0"
          description="暂无追踪事件"
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
                  {{ event.status || event.eventType }}
                </el-tag>
              </div>
              <div class="event-biz-no">{{ event.bizNo || '-' }}</div>
              <div class="event-description">{{ event.description || '-' }}</div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </el-card>
    </div>

    <el-dialog v-model="businessTimelineVisible" title="业务动态" width="760px">
      <template v-if="selectedTimelineDocument">
        <el-descriptions :column="2" border class="timeline-document">
          <el-descriptions-item label="单据类型">{{ selectedTimelineDocument.documentLabel }}</el-descriptions-item>
          <el-descriptions-item label="单据编号">{{ selectedTimelineDocument.bizNo }}</el-descriptions-item>
          <el-descriptions-item label="业务类型">{{ selectedTimelineDocument.documentType }}</el-descriptions-item>
          <el-descriptions-item label="业务ID">{{ selectedTimelineDocument.documentId }}</el-descriptions-item>
        </el-descriptions>

        <el-form :model="timelineCommentForm" class="timeline-comment-form">
          <el-form-item>
            <el-input
              v-model="timelineCommentForm.content"
              type="textarea"
              :rows="3"
              maxlength="1024"
              show-word-limit
              placeholder="补充业务备注"
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              :loading="timelineCommentSubmitting"
              @click="submitTimelineComment"
            >
              提交备注
            </el-button>
            <el-button :icon="Refresh" @click="loadBusinessTimeline">刷新动态</el-button>
          </el-form-item>
        </el-form>

        <el-skeleton v-if="businessTimelineLoading" :rows="5" animated />
        <el-empty
          v-else-if="businessTimelineEvents.length === 0"
          description="暂无业务动态"
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
                <span>操作人 {{ event.operatorUserId || '-' }}</span>
              </div>
              <div class="business-event-content">{{ event.content }}</div>
              <div v-if="event.attachmentId" class="business-event-extra">
                附件ID：{{ event.attachmentId }}
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
import { formatLocalizedDateTime, formatLocalizedNumber } from '@/utils/locale'
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
    label: '关联单据',
    value: trace.value.summary.documentCount,
    icon: Document,
    tone: 'blue'
  },
  {
    label: '时间线事件',
    value: trace.value.summary.timelineCount,
    icon: Clock,
    tone: 'green'
  },
  {
    label: '未结应收',
    value: formatMoney(trace.value.summary.openReceivableAmount),
    icon: Money,
    tone: 'orange'
  },
  {
    label: '未结应付',
    value: formatMoney(trace.value.summary.openPayableAmount),
    icon: Money,
    tone: 'red'
  },
  {
    label: '库存流转数量',
    value: formatNumber(trace.value.summary.inventoryMovementQuantity),
    icon: Box,
    tone: 'purple'
  },
  {
    label: '失败操作',
    value: trace.value.summary.failedOperationCount,
    icon: Warning,
    tone: 'red'
  },
  {
    label: '未关异常',
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
    ElMessage.info('该事件暂无跳转目标')
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
    ElMessage.warning('请输入业务备注')
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
    ElMessage.success('备注已提交')
    businessTimelineQuery.pageNo = 1
    await loadBusinessTimeline()
  } finally {
    timelineCommentSubmitting.value = false
  }
}

const formatMoney = (amount?: number) => {
  return `¥${formatLocalizedNumber(Number(amount || 0), {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`
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
    LOW: '低',
    MEDIUM: '中',
    HIGH: '高',
    URGENT: '紧急'
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
    OPEN: '待处理',
    PROCESSING: '处理中',
    RESOLVED: '已解决',
    CLOSED: '已关闭'
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
    COMMENT: '业务备注',
    ATTACHMENT_UPLOADED: '上传附件',
    ATTACHMENT_DELETED: '删除附件'
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
