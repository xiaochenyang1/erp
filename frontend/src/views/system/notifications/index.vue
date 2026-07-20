<template>
  <div class="app-container">
    <el-card shadow="never" class="summary-card">
      <div class="summary-row">
        <div>
          <div class="summary-title">通知中心</div>
          <div class="summary-subtitle">未读通知 {{ unreadCount }} 条</div>
        </div>
        <div class="summary-actions">
          <el-button
            v-permission="'system:notification:manage'"
            :disabled="!selectedRows.length"
            @click="handleMarkSelectedRead"
          >
            批量已读（{{ selectedRows.length }}）
          </el-button>
          <el-button v-permission="'system:notification:manage'" type="primary" :icon="Check" @click="handleMarkAllRead">
            全部已读
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item label="分类">
          <el-input v-model="queryForm.category" placeholder="请输入分类" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-input v-model="queryForm.notificationType" placeholder="请输入类型" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="readStatus" placeholder="请选择" clearable style="width: 140px">
            <el-option label="未读" value="UNREAD" />
            <el-option label="全部" value="ALL" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe @selection-change="onSelectionChange">
        <el-table-column type="selection" width="48" :selectable="(row: Notification) => !row.readFlag" />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <el-badge is-dot :hidden="row.readFlag" class="notice-badge">
              <span>{{ row.title }}</span>
            </el-badge>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="130">
          <template #default="{ row }">{{ row.category || '-' }}</template>
        </el-table-column>
        <el-table-column prop="notificationType" label="类型" width="150">
          <template #default="{ row }">{{ row.notificationType || row.type || '-' }}</template>
        </el-table-column>
        <el-table-column prop="bizNo" label="业务编号" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.bizNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="readFlag" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.readFlag ? 'info' : 'warning'">
              {{ row.readFlag ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleView(row)">查看</el-button>
            <el-button v-if="!row.readFlag" v-permission="'system:notification:manage'" type="success" link @click="handleMarkRead(row)">已读</el-button>
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

    <el-dialog v-model="detailVisible" title="通知详情" width="720px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="标题" :span="2">{{ detailData.title }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ detailData.category || '-' }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ detailData.notificationType || detailData.type || '-' }}</el-descriptions-item>
        <el-descriptions-item label="业务类型">{{ detailData.bizType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="业务编号">{{ detailData.bizNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detailData.readFlag ? '已读' : '未读' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailData.createdTime }}</el-descriptions-item>
        <el-descriptions-item label="内容" :span="2">{{ detailData.content }}</el-descriptions-item>
        <el-descriptions-item v-if="detailData.targetUrl" label="目标地址" :span="2">
          {{ detailData.targetUrl }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Refresh, Search, View } from '@element-plus/icons-vue'
import {
  getNotifications,
  getUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
  markNotificationsReadBatch,
  type Notification,
  type NotificationQuery
} from '@/api/notification'

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
    console.error('加载通知失败:', error)
    ElMessage.error('加载通知失败')
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
    if (showMessage) ElMessage.success('已标记为已读')
    loadData()
  } catch (error) {
    ElMessage.error('标记已读失败')
  }
}

const handleMarkAllRead = async () => {
  try {
    await markAllNotificationsRead()
    ElMessage.success('全部通知已标记为已读')
    selectedRows.value = []
    loadData()
  } catch (error) {
    ElMessage.error('标记全部已读失败')
  }
}

const handleMarkSelectedRead = async () => {
  const ids = selectedRows.value.filter((r) => !r.readFlag).map((r) => r.recipientId)
  if (!ids.length) {
    ElMessage.warning('请先勾选未读通知')
    return
  }
  try {
    const res = await markNotificationsReadBatch(ids)
    ElMessage.success(`已标记 ${res?.updated ?? ids.length} 条为已读`)
    selectedRows.value = []
    loadData()
  } catch (error) {
    ElMessage.error('批量已读失败')
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
