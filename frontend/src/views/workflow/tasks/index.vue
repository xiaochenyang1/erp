<template>
  <div class="workflow-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item :label="t('workflow.businessType')">
          <el-select v-model="queryParams.businessType" :placeholder="t('workflow.selectBusinessType')" clearable style="width: 170px">
            <el-option :label="t('workflow.purchaseOrder')" value="PURCHASE_ORDER" />
            <el-option :label="t('workflow.salesOrder')" value="SALES_ORDER" />
            <el-option :label="t('workflow.expense')" value="EXPENSE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('workflow.businessNo')">
          <el-input v-model="queryParams.businessNo" :placeholder="t('workflow.inputBusinessNo')" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item :label="t('workflow.status')">
          <el-select v-model="queryParams.status" :placeholder="t('workflow.selectStatus')" clearable style="width: 140px">
            <el-option :label="t('workflow.pending')" value="PENDING" />
            <el-option :label="t('workflow.approved')" value="APPROVED" />
            <el-option :label="t('workflow.rejected')" value="REJECTED" />
            <el-option :label="t('workflow.cancelled')" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ t('workflow.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('workflow.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ t('workflow.tasks') }}</span>
          <el-button :icon="Refresh" @click="loadData">{{ t('workflow.refresh') }}</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="title" :label="t('workflow.title')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="businessType" :label="t('workflow.businessType')" width="130">
          <template #default="{ row }">{{ businessTypeLabel(row.businessType) }}</template>
        </el-table-column>
        <el-table-column prop="businessNo" :label="t('workflow.businessNo')" min-width="170" show-overflow-tooltip />
        <el-table-column prop="status" :label="t('workflow.status')" width="110">
          <template #default="{ row }">
            <el-tag :type="taskStatusType(row.status)">{{ taskStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" :label="t('workflow.createdTime')" width="180">
          <template #default="{ row }">{{ formatTime(row.createdTime) }}</template>
        </el-table-column>
        <el-table-column prop="updatedTime" :label="t('workflow.updatedTime')" width="180">
          <template #default="{ row }">{{ formatTime(row.updatedTime) }}</template>
        </el-table-column>
        <el-table-column prop="dueTime" :label="t('workflow.dueTime')" width="190">
          <template #default="{ row }">
            <el-tag v-if="row.overdue" type="danger" size="small">{{ t('workflow.overdue') }}</el-tag>
            <span class="due-time">{{ formatTime(row.dueTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('workflow.actions')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(toTaskRow(row))">{{ t('workflow.view') }}</el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              v-permission="'workflow:approve'"
              link
              type="success"
              @click="openApprove(toTaskRow(row))"
            >
              {{ t('workflow.approve') }}
            </el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              v-permission="'workflow:reject'"
              link
              type="danger"
              @click="openReject(toTaskRow(row))"
            >
              {{ t('workflow.reject') }}
            </el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              v-permission="'workflow:approve'"
              link
              type="warning"
              @click="openTransfer(toTaskRow(row))"
            >
              {{ t('workflow.transfer') }}
            </el-button>
            <el-button
              v-if="row.status === 'PENDING' && row.overdue"
              v-permission="'workflow:escalate'"
              link
              type="danger"
              @click="openEscalate(toTaskRow(row))"
            >
              {{ t('workflow.escalate') }}
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

    <el-dialog v-model="detailVisible" :title="t('workflow.detail')" width="760px">
      <el-descriptions v-if="currentTask" :column="2" border>
        <el-descriptions-item :label="t('workflow.title')" :span="2">{{ currentTask.title }}</el-descriptions-item>
        <el-descriptions-item :label="t('workflow.businessType')">{{ businessTypeLabel(currentTask.businessType) }}</el-descriptions-item>
        <el-descriptions-item :label="t('workflow.businessNo')">{{ currentTask.businessNo }}</el-descriptions-item>
        <el-descriptions-item :label="t('workflow.businessId')">{{ currentTask.businessId }}</el-descriptions-item>
        <el-descriptions-item :label="t('workflow.status')">{{ taskStatusLabel(currentTask.status) }}</el-descriptions-item>
        <el-descriptions-item :label="t('workflow.createdTime')">{{ formatTime(currentTask.createdTime) }}</el-descriptions-item>
        <el-descriptions-item :label="t('workflow.updatedTime')">{{ formatTime(currentTask.updatedTime) }}</el-descriptions-item>
        <el-descriptions-item :label="t('workflow.deadline')">{{ formatTime(currentTask.dueTime) }}</el-descriptions-item>
        <el-descriptions-item :label="t('workflow.escalationCount')">{{ currentTask.escalationCount || 0 }}</el-descriptions-item>
      </el-descriptions>

      <template #footer>
        <el-button @click="detailVisible = false">{{ t('workflow.close') }}</el-button>
        <el-button
          v-if="currentTask?.status === 'PENDING'"
          v-permission="'workflow:approve'"
          type="success"
          :loading="submitLoading"
          @click="openApprove(currentTask)"
        >
          {{ t('workflow.approveAction') }}
        </el-button>
        <el-button
          v-if="currentTask?.status === 'PENDING'"
          v-permission="'workflow:reject'"
          type="danger"
          :loading="submitLoading"
          @click="openReject(currentTask)"
        >
          {{ t('workflow.reject') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="escalateVisible" :title="t('workflow.escalationTitle')" width="480px">
      <el-form label-width="100px">
        <el-form-item :label="t('workflow.escalateTo')" required>
          <el-select v-model="escalateUserId" filterable style="width: 100%" :placeholder="t('workflow.selectAssignee')">
            <el-option v-for="u in escalateUsers" :key="u.id" :label="u.username || u.realName || u.id" :value="String(u.id)" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('workflow.escalationComment')">
          <el-input v-model="escalateComment" type="textarea" :rows="3" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="escalateVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="danger" :loading="submitLoading" @click="submitEscalate">{{ t('workflow.confirmEscalation') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="actionVisible" :title="actionTitle" width="520px" @close="resetAction">
      <el-form :model="actionForm" label-width="90px">
        <el-form-item :label="actionMode === 'approve' ? t('workflow.approvalComment') : t('workflow.rejectionReason')" :required="actionMode === 'reject'">
          <el-input
            v-model="actionForm.comment"
            type="textarea"
            :rows="4"
            maxlength="255"
            show-word-limit
            :placeholder="actionMode === 'approve' ? t('workflow.inputApprovalComment') : t('workflow.inputRejectionReason')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button :type="actionMode === 'approve' ? 'success' : 'danger'" :loading="submitLoading" @click="handleConfirmAction">
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="transferVisible" :title="t('workflow.transferTitle')" width="480px">
      <el-form label-width="100px">
        <el-form-item :label="t('workflow.transferTo')" required>
          <el-select v-model="transferUserId" filterable style="width: 100%" :placeholder="t('workflow.selectUser')">
            <el-option v-for="u in transferUsers" :key="u.id" :label="u.username || u.realName || u.id" :value="String(u.id)" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('workflow.comment')">
          <el-input v-model="actionForm.comment" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitTransfer">{{ t('workflow.confirmTransfer') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  approveWorkflowTask,
  escalateWorkflowTask,
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
const { t } = useI18n()
const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref<WorkflowTask[]>([])
const total = ref(0)
const detailVisible = ref(false)
const actionVisible = ref(false)
const currentTask = ref<WorkflowTask | null>(null)
const actionMode = ref<'approve' | 'reject'>('approve')
const escalateVisible = ref(false)
const escalateUserId = ref('')
const escalateComment = ref('')
const escalateUsers = ref<any[]>([])

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

const actionTitle = computed(() => (actionMode.value === 'approve' ? t('workflow.approveAction') : t('workflow.reject')))

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
    ElMessage.warning(t('workflow.selectTransferUser'))
    return
  }
  try {
    await transferWorkflowTask({ taskId: currentTask.value.id, targetUserId: transferUserId.value, comment: actionForm.comment })
    ElMessage.success(t('workflow.transferSuccess'))
    transferVisible.value = false
    loadData()
  } catch {
    ElMessage.error(t('workflow.transferFailed'))
  }
}
const openEscalate = async (row: WorkflowTask) => {
  currentTask.value = row
  escalateUserId.value = ''
  escalateComment.value = ''
  try {
    const page = await getUsers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    escalateUsers.value = (page.records || []).filter((user: any) => String(user.id) !== row.approverUserId)
  } catch {
    escalateUsers.value = []
  }
  escalateVisible.value = true
}
const submitEscalate = async () => {
  if (!currentTask.value || !escalateUserId.value) {
    ElMessage.warning(t('workflow.selectEscalationUser'))
    return
  }
  submitLoading.value = true
  try {
    await escalateWorkflowTask({
      taskId: currentTask.value.id,
      targetUserId: escalateUserId.value,
      comment: escalateComment.value.trim() || undefined
    })
    ElMessage.success(t('workflow.escalationSuccess'))
    escalateVisible.value = false
    await loadData()
  } finally {
    submitLoading.value = false
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
    ElMessage.warning(t('workflow.inputRejectionReason'))
    return
  }

  submitLoading.value = true
  try {
    if (actionMode.value === 'approve') {
      await approveWorkflowTask({ taskId: currentTask.value.id, comment: actionForm.comment.trim() || undefined })
      ElMessage.success(t('workflow.approvalSuccess'))
    } else {
      await rejectWorkflowTask({ taskId: currentTask.value.id, reason: actionForm.comment.trim() })
      ElMessage.success(t('workflow.rejectedSuccess'))
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
    PURCHASE_ORDER: t('workflow.purchaseOrder'),
    SALES_ORDER: t('workflow.salesOrder'),
    EXPENSE: t('workflow.expense')
  }
  return type ? map[type] || type : '-'
}

const taskStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    PENDING: t('workflow.pending'),
    APPROVED: t('workflow.approved'),
    REJECTED: t('workflow.rejected'),
    CANCELLED: t('workflow.cancelled')
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

  .due-time {
    margin-left: 6px;
  }
}
</style>
