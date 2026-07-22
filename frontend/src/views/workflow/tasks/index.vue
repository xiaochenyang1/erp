<template>
  <div class="workflow-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="业务类型">
          <el-select v-model="queryParams.businessType" placeholder="请选择业务类型" clearable style="width: 170px">
            <el-option label="采购订单" value="PURCHASE_ORDER" />
            <el-option label="销售订单" value="SALES_ORDER" />
            <el-option label="费用单" value="EXPENSE" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务单号">
          <el-input v-model="queryParams.businessNo" placeholder="请输入业务单号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 140px">
            <el-option label="待审批" value="PENDING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>审批待办</span>
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="title" label="任务标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="businessType" label="业务类型" width="130">
          <template #default="{ row }">{{ businessTypeLabel(row.businessType) }}</template>
        </el-table-column>
        <el-table-column prop="businessNo" label="业务单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="taskStatusType(row.status)">{{ taskStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdTime) }}</template>
        </el-table-column>
        <el-table-column prop="updatedTime" label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updatedTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(toTaskRow(row))">查看</el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              v-permission="'workflow:approve'"
              link
              type="success"
              @click="openApprove(toTaskRow(row))"
            >
              通过
            </el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              v-permission="'workflow:reject'"
              link
              type="danger"
              @click="openReject(toTaskRow(row))"
            >
              驳回
            </el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              v-permission="'workflow:approve'"
              link
              type="warning"
              @click="openTransfer(toTaskRow(row))"
            >
              转签
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.pageNo"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadData"
        @current-change="loadData"
      />
    </el-card>

    <el-dialog v-model="detailVisible" title="审批任务详情" width="760px">
      <el-descriptions v-if="currentTask" :column="2" border>
        <el-descriptions-item label="任务标题" :span="2">{{ currentTask.title }}</el-descriptions-item>
        <el-descriptions-item label="业务类型">{{ businessTypeLabel(currentTask.businessType) }}</el-descriptions-item>
        <el-descriptions-item label="业务单号">{{ currentTask.businessNo }}</el-descriptions-item>
        <el-descriptions-item label="业务ID">{{ currentTask.businessId }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ taskStatusLabel(currentTask.status) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(currentTask.createdTime) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatTime(currentTask.updatedTime) }}</el-descriptions-item>
      </el-descriptions>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="currentTask?.status === 'PENDING'"
          v-permission="'workflow:approve'"
          type="success"
          :loading="submitLoading"
          @click="openApprove(currentTask)"
        >
          审批通过
        </el-button>
        <el-button
          v-if="currentTask?.status === 'PENDING'"
          v-permission="'workflow:reject'"
          type="danger"
          :loading="submitLoading"
          @click="openReject(currentTask)"
        >
          驳回
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="actionVisible" :title="actionTitle" width="520px" @close="resetAction">
      <el-form :model="actionForm" label-width="90px">
        <el-form-item :label="actionMode === 'approve' ? '审批意见' : '驳回原因'" :required="actionMode === 'reject'">
          <el-input
            v-model="actionForm.comment"
            type="textarea"
            :rows="4"
            maxlength="255"
            show-word-limit
            :placeholder="actionMode === 'approve' ? '请输入审批意见' : '请输入驳回原因'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionVisible = false">取消</el-button>
        <el-button :type="actionMode === 'approve' ? 'success' : 'danger'" :loading="submitLoading" @click="handleConfirmAction">
          确定
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="transferVisible" title="转签任务" width="480px">
      <el-form label-width="100px">
        <el-form-item label="转签给" required>
          <el-select v-model="transferUserId" filterable style="width: 100%" placeholder="选择用户">
            <el-option v-for="u in transferUsers" :key="u.id" :label="u.username || u.realName || u.id" :value="String(u.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="actionForm.comment" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTransfer">确定转签</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  approveWorkflowTask,
  getWorkflowTask,
  getWorkflowTasks,
  rejectWorkflowTask,
  transferWorkflowTask,
  type WorkflowTask,
  type WorkflowTaskQuery
} from '@/api/workflow'
import { getUsers } from '@/api/system'
import { formatLocalizedDateTime } from '@/utils/locale'

const route = useRoute()
const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref<WorkflowTask[]>([])
const total = ref(0)
const detailVisible = ref(false)
const actionVisible = ref(false)
const currentTask = ref<WorkflowTask | null>(null)
const actionMode = ref<'approve' | 'reject'>('approve')

const readQueryString = (key: string) => {
  const value = route.query[key]
  return typeof value === 'string' ? value : ''
}

const queryParams = reactive<WorkflowTaskQuery>({
  pageNo: 1,
  pageSize: 10,
  businessType: readQueryString('businessType'),
  businessId: readQueryString('businessId'),
  businessNo: readQueryString('businessNo'),
  status: readQueryString('status') || 'PENDING'
})

const actionForm = reactive({
  comment: ''
})

const actionTitle = computed(() => (actionMode.value === 'approve' ? '审批通过' : '审批驳回'))

const loadData = async () => {
  loading.value = true
  try {
    const response = await getWorkflowTasks(cleanQuery(queryParams))
    tableData.value = response.records
    total.value = response.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  loadData()
}

const handleReset = () => {
  queryParams.businessType = ''
  queryParams.businessId = undefined
  queryParams.businessNo = ''
  queryParams.status = 'PENDING'
  handleQuery()
}

const handleView = async (row: WorkflowTask) => {
  currentTask.value = await getWorkflowTask(row.id)
  detailVisible.value = true
}

const openApprove = (row: WorkflowTask) => {
  currentTask.value = row
  actionMode.value = 'approve'
  actionVisible.value = true
}

const transferVisible = ref(false)
const transferUserId = ref('')
const transferUsers = ref<any[]>([])
const openTransfer = async (row: WorkflowTask) => {
  currentTask.value = row
  transferUserId.value = ''
  try {
    const page = await getUsers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    transferUsers.value = page.records || []
  } catch {
    transferUsers.value = []
  }
  transferVisible.value = true
}
const submitTransfer = async () => {
  if (!currentTask.value || !transferUserId.value) {
    ElMessage.warning('请选择转签用户')
    return
  }
  try {
    await transferWorkflowTask({ taskId: currentTask.value.id, targetUserId: transferUserId.value, comment: actionForm.comment })
    ElMessage.success('转签成功')
    transferVisible.value = false
    loadData()
  } catch {
    ElMessage.error('转签失败')
  }
}
const openReject = (row: WorkflowTask) => {
  currentTask.value = row
  actionMode.value = 'reject'
  actionVisible.value = true
}

const handleConfirmAction = async () => {
  if (!currentTask.value) return
  if (actionMode.value === 'reject' && !actionForm.comment.trim()) {
    ElMessage.warning('请输入驳回原因')
    return
  }

  submitLoading.value = true
  try {
    if (actionMode.value === 'approve') {
      await approveWorkflowTask({ taskId: currentTask.value.id, comment: actionForm.comment.trim() || undefined })
      ElMessage.success('审批通过')
    } else {
      await rejectWorkflowTask({ taskId: currentTask.value.id, reason: actionForm.comment.trim() })
      ElMessage.success('已驳回')
    }
    actionVisible.value = false
    detailVisible.value = false
    await loadData()
  } finally {
    submitLoading.value = false
  }
}

const resetAction = () => {
  actionForm.comment = ''
}

const toTaskRow = (row: unknown) => row as WorkflowTask

const cleanQuery = (query: WorkflowTaskQuery): WorkflowTaskQuery => ({
  pageNo: query.pageNo,
  pageSize: query.pageSize,
  businessType: query.businessType || undefined,
  businessId: query.businessId || undefined,
  businessNo: query.businessNo || undefined,
  status: query.status || undefined
})

const businessTypeLabel = (type?: string) => {
  const map: Record<string, string> = {
    PURCHASE_ORDER: '采购订单',
    SALES_ORDER: '销售订单',
    EXPENSE: '费用单'
  }
  return type ? map[type] || type : '-'
}

const taskStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    PENDING: '待审批',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    CANCELLED: '已取消'
  }
  return map[status] || status
}

const taskStatusType = (status: string) => {
  const map: Record<string, 'info' | 'warning' | 'success' | 'danger'> = {
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
    CANCELLED: 'info'
  }
  return map[status] || 'info'
}

const formatTime = (value?: string) => {
  return formatLocalizedDateTime(value) || '-'
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.workflow-container {
  padding: 20px;

  .search-card,
  .table-card {
    margin-bottom: 20px;
  }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }
}
</style>
