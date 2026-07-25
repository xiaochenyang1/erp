<template>
  <div class="app-container">
    <el-card shadow="never" class="summary-card">
      <div class="summary-row">
        <div>
          <div class="summary-title">{{ $t('systemNotifications.title') }}</div>
          <div class="summary-subtitle">{{ $t('systemNotifications.unreadSummary', { count: formatLocalizedNumber(unreadCount) }) }}</div>
        </div>
        <div class="summary-actions">
          <el-button
            v-permission="'system:notification:manage'"
            :disabled="!selectedRows.length"
            @click="handleMarkSelectedRead"
          >
            {{ $t('systemNotifications.markSelectedRead', { count: formatLocalizedNumber(selectedRows.length) }) }}
          </el-button>
          <el-button v-permission="'system:notification:manage'" type="primary" :icon="Check" @click="handleMarkAllRead">
            {{ $t('systemNotifications.markAllRead') }}
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item :label="$t('systemNotifications.category')">
          <el-input v-model="queryForm.category" :placeholder="$t('systemNotifications.categoryPlaceholder')" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item :label="$t('systemNotifications.type')">
          <el-input v-model="queryForm.notificationType" :placeholder="$t('systemNotifications.typePlaceholder')" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item :label="$t('systemNotifications.status')">
          <el-select v-model="readStatus" :placeholder="$t('systemNotifications.selectStatus')" clearable style="width: 140px">
            <el-option :label="$t('systemNotifications.unread')" value="UNREAD" />
            <el-option :label="$t('systemNotifications.all')" value="ALL" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ $t('systemNotifications.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ $t('systemNotifications.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe @selection-change="onSelectionChange">
        <el-table-column type="selection" width="48" :selectable="(row: Notification) => !row.readFlag" />
        <el-table-column prop="title" :label="$t('systemNotifications.notificationTitle')" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <el-badge is-dot :hidden="row.readFlag" class="notice-badge">
              <span>{{ row.title }}</span>
            </el-badge>
          </template>
        </el-table-column>
        <el-table-column prop="category" :label="$t('systemNotifications.category')" width="130">
          <template #default="{ row }">{{ row.category || '-' }}</template>
        </el-table-column>
        <el-table-column prop="notificationType" :label="$t('systemNotifications.type')" width="150">
          <template #default="{ row }">{{ row.notificationType || row.type || '-' }}</template>
        </el-table-column>
        <el-table-column prop="bizNo" :label="$t('systemNotifications.businessNo')" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.bizNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="readFlag" :label="$t('systemNotifications.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.readFlag ? 'info' : 'warning'">
              {{ row.readFlag ? $t('systemNotifications.read') : $t('systemNotifications.unread') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" :label="$t('systemNotifications.createdAt')" width="190">
          <template #default="{ row }">{{ formatLocalizedDateTime(row.createdTime) || '-' }}</template>
        </el-table-column>
        <el-table-column :label="$t('systemNotifications.actions')" width="170" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleView(row)">{{ $t('systemNotifications.view') }}</el-button>
            <el-button v-if="!row.readFlag" v-permission="'system:notification:manage'" type="success" link @click="handleMarkRead(row)">{{ $t('systemNotifications.markRead') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handlePageChange"
        @current-change="handlePageChange"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <el-dialog v-model="detailVisible" :title="$t('systemNotifications.detailTitle')" width="720px">
      <el-descriptions :column="2" border>
        <el-descriptions-item :label="$t('systemNotifications.notificationTitle')" :span="2">{{ detailData.title }}</el-descriptions-item>
        <el-descriptions-item :label="$t('systemNotifications.category')">{{ detailData.category || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('systemNotifications.type')">{{ detailData.notificationType || detailData.type || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('systemNotifications.businessType')">{{ detailData.bizType || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('systemNotifications.businessNo')">{{ detailData.bizNo || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('systemNotifications.status')">{{ detailData.readFlag ? $t('systemNotifications.read') : $t('systemNotifications.unread') }}</el-descriptions-item>
        <el-descriptions-item :label="$t('systemNotifications.createdAt')">{{ formatLocalizedDateTime(detailData.createdTime) || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('systemNotifications.content')" :span="2">{{ detailData.content }}</el-descriptions-item>
        <el-descriptions-item v-if="detailData.targetUrl" :label="$t('systemNotifications.targetUrl')" :span="2">
          {{ detailData.targetUrl }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Check, Refresh, Search, View } from '@element-plus/icons-vue'
import { formatLocalizedDateTime, formatLocalizedNumber } from '@/utils/locale'
import {
  getNotifications,
  getUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
  markNotificationsReadBatch,
  type Notification,
  type NotificationQuery
} from '@/api/notification'

const { t } = useI18n()

const queryForm = reactive<NotificationQuery>({
  category: '',
  notificationType: ''
})
const readStatus = ref('ALL')
const unreadCount = ref(0)
const selectedRows = ref<Notification[]>([])

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const loading = ref(false)
const tableData = ref<Notification[]>([])
const detailVisible = ref(false)
const detailData = ref<Notification>({} as Notification)

const onSelectionChange = (rows: Notification[]) => {
  selectedRows.value = rows
}

const buildQueryParams = (): NotificationQuery => ({
  ...queryForm,
  unreadOnly: readStatus.value === 'UNREAD' ? true : undefined,
  pageNo: pagination.page,
  pageSize: pagination.size
})

const loadUnreadCount = async () => {
  const res = await getUnreadCount()
  unreadCount.value = res.unreadCount
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getNotifications(buildQueryParams())
    tableData.value = res.records || []
    pagination.total = res.total || 0
    await loadUnreadCount()
  } catch (error) {
    console.error('Failed to load notifications:', error)
    ElMessage.error(t('systemNotifications.message.loadFailed'))
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  pagination.page = 1
  loadData()
}

const handleReset = () => {
  queryForm.category = ''
  queryForm.notificationType = ''
  readStatus.value = 'ALL'
  pagination.page = 1
  loadData()
}

const handlePageChange = () => {
  loadData()
}

const handleView = async (row: Notification) => {
  detailData.value = row
  detailVisible.value = true
  if (!row.readFlag) {
    await handleMarkRead(row, false)
  }
}

const handleMarkRead = async (row: Notification, showMessage = true) => {
  try {
    await markNotificationRead(row.recipientId)
    if (showMessage) ElMessage.success(t('systemNotifications.message.markedRead'))
    loadData()
  } catch (error) {
    ElMessage.error(t('systemNotifications.message.markReadFailed'))
  }
}

const handleMarkAllRead = async () => {
  try {
    await markAllNotificationsRead()
    ElMessage.success(t('systemNotifications.message.allMarkedRead'))
    selectedRows.value = []
    loadData()
  } catch (error) {
    ElMessage.error(t('systemNotifications.message.markAllReadFailed'))
  }
}

const handleMarkSelectedRead = async () => {
  const ids = selectedRows.value.filter((r) => !r.readFlag).map((r) => r.recipientId)
  if (!ids.length) {
    ElMessage.warning(t('systemNotifications.message.selectUnread'))
    return
  }
  try {
    const res = await markNotificationsReadBatch(ids)
    ElMessage.success(t('systemNotifications.message.batchMarkedRead', {
      count: formatLocalizedNumber(res?.updated ?? ids.length)
    }))
    selectedRows.value = []
    loadData()
  } catch (error) {
    ElMessage.error(t('systemNotifications.message.batchMarkReadFailed'))
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.app-container {
  padding: 20px;
}

.summary-card,
.search-card,
.table-card {
  margin-bottom: 20px;
}

.summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.summary-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.summary-title {
  font-size: 18px;
  font-weight: 600;
}

.summary-subtitle {
  margin-top: 6px;
  color: #606266;
}

.notice-badge {
  line-height: 1;
}
</style>
