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
        <el-form-item label="业务ID">
          <el-input v-model="queryParams.businessId" placeholder="请输入业务ID" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="动作">
          <el-select v-model="queryParams.action" placeholder="请选择动作" clearable style="width: 140px">
            <el-option label="提交" value="SUBMIT" />
            <el-option label="通过" value="APPROVE" />
            <el-option label="驳回" value="REJECT" />
            <el-option label="撤回" value="WITHDRAW" />
            <el-option label="取消" value="CANCEL" />
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
          <span>审批记录</span>
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="businessType" label="业务类型" width="130">
          <template #default="{ row }">{{ businessTypeLabel(row.businessType) }}</template>
        </el-table-column>
        <el-table-column prop="businessNo" label="业务单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="businessId" label="业务ID" width="110" />
        <el-table-column prop="action" label="动作" width="110">
          <template #default="{ row }">
            <el-tag :type="actionType(row.action)">{{ actionLabel(row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operatorUserId" label="操作人ID" width="120" />
        <el-table-column prop="comment" label="意见" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.comment || '-' }}</template>
        </el-table-column>
        <el-table-column prop="actionTime" label="操作时间" width="180">
          <template #default="{ row }">{{ formatTime(row.actionTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="warning" @click="openWithdraw(row)">撤回</el-button>
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

    <el-dialog v-model="withdrawVisible" title="撤回审批" width="520px" @close="resetWithdraw">
      <el-alert
        v-if="currentRecord"
        :title="`${businessTypeLabel(currentRecord.businessType)}：${currentRecord.businessNo || currentRecord.businessId}`"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      />
      <el-form :model="withdrawForm" label-width="90px">
        <el-form-item label="撤回说明">
          <el-input
            v-model="withdrawForm.comment"
            type="textarea"
            :rows="4"
            maxlength="255"
            show-word-limit
            placeholder="请输入撤回说明"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="withdrawVisible = false">取消</el-button>
        <el-button type="warning" :loading="withdrawSubmitting" @click="submitWithdraw">确认撤回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  getBusinessWorkflowRecords,
  getWorkflowRecords,
  withdrawWorkflow,
  type WorkflowRecord,
  type WorkflowRecordQuery
} from '@/api/workflow'

const route = useRoute()
const loading = ref(false)
const tableData = ref<WorkflowRecord[]>([])
const total = ref(0)
const withdrawVisible = ref(false)
const withdrawSubmitting = ref(false)
const currentRecord = ref<WorkflowRecord | null>(null)
const withdrawForm = reactive({
  comment: ''
})

const readQueryString = (key: string) => {
  const value = route.query[key]
  return typeof value === 'string' ? value : ''
}

const queryParams = reactive<WorkflowRecordQuery>({
  pageNo: 1,
  pageSize: 10,
  businessType: readQueryString('businessType'),
  businessId: readQueryString('businessId'),
  businessNo: readQueryString('businessNo'),
  action: readQueryString('action')
})

const loadData = async () => {
  loading.value = true
  try {
    if (queryParams.businessType && queryParams.businessId) {
      const records = await getBusinessWorkflowRecords(queryParams.businessType, queryParams.businessId)
      const filteredRecords = filterBusinessRecords(records)
      total.value = filteredRecords.length
      tableData.value = paginateRecords(filteredRecords)
    } else {
      const response = await getWorkflowRecords(cleanQuery(queryParams))
      tableData.value = response.records
      total.value = response.total
    }
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
  queryParams.action = ''
  handleQuery()
}

const openWithdraw = (row: WorkflowRecord) => {
  currentRecord.value = row
  withdrawVisible.value = true
}

const submitWithdraw = async () => {
  if (!currentRecord.value) return
  withdrawSubmitting.value = true
  try {
    await withdrawWorkflow({
      businessType: currentRecord.value.businessType,
      businessId: currentRecord.value.businessId,
      comment: withdrawForm.comment.trim() || undefined
    })
    ElMessage.success('撤回成功')
    withdrawVisible.value = false
    await loadData()
  } catch (error) {
    ElMessage.error('撤回失败，请确认当前用户是提交人且审批尚未处理')
  } finally {
    withdrawSubmitting.value = false
  }
}

const resetWithdraw = () => {
  withdrawForm.comment = ''
  currentRecord.value = null
}

const cleanQuery = (query: WorkflowRecordQuery): WorkflowRecordQuery => ({
  pageNo: query.pageNo,
  pageSize: query.pageSize,
  businessType: query.businessType || undefined,
  businessId: query.businessId || undefined,
  businessNo: query.businessNo || undefined,
  action: query.action || undefined
})

const filterBusinessRecords = (records: WorkflowRecord[]) => {
  return records.filter((record) => {
    const businessNoMatched = !queryParams.businessNo || record.businessNo?.includes(queryParams.businessNo)
    const actionMatched = !queryParams.action || record.action === queryParams.action
    return businessNoMatched && actionMatched
  })
}

const paginateRecords = (records: WorkflowRecord[]) => {
  const pageNo = queryParams.pageNo || 1
  const pageSize = queryParams.pageSize || 10
  const start = (pageNo - 1) * pageSize
  return records.slice(start, start + pageSize)
}

const businessTypeLabel = (type?: string) => {
  const map: Record<string, string> = {
    PURCHASE_ORDER: '采购订单',
    SALES_ORDER: '销售订单',
    EXPENSE: '费用单'
  }
  return type ? map[type] || type : '-'
}

const actionLabel = (action: string) => {
  const map: Record<string, string> = {
    SUBMIT: '提交',
    APPROVE: '通过',
    REJECT: '驳回',
    WITHDRAW: '撤回',
    CANCEL: '取消'
  }
  return map[action] || action
}

const actionType = (action: string) => {
  const map: Record<string, 'info' | 'warning' | 'success' | 'danger'> = {
    SUBMIT: 'info',
    APPROVE: 'success',
    REJECT: 'danger',
    WITHDRAW: 'warning',
    CANCEL: 'info'
  }
  return map[action] || 'info'
}

const formatTime = (value?: string) => {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
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
