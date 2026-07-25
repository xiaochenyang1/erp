<template>
  <div class="workflow-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item :label="$t('workflowRecord.businessType')">
          <el-select v-model="queryParams.businessType" :placeholder="$t('workflowRecord.selectBusinessType')" clearable style="width: 170px">
            <el-option :label="$t('workflowRecord.businessTypes.purchaseOrder')" value="PURCHASE_ORDER" />
            <el-option :label="$t('workflowRecord.businessTypes.salesOrder')" value="SALES_ORDER" />
            <el-option :label="$t('workflowRecord.businessTypes.expense')" value="EXPENSE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('workflowRecord.businessNo')">
          <el-input v-model="queryParams.businessNo" :placeholder="$t('workflowRecord.businessNoPlaceholder')" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item :label="$t('workflowRecord.businessId')">
          <el-input v-model="queryParams.businessId" :placeholder="$t('workflowRecord.businessIdPlaceholder')" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item :label="$t('workflowRecord.action')">
          <el-select v-model="queryParams.action" :placeholder="$t('workflowRecord.selectAction')" clearable style="width: 140px">
            <el-option :label="$t('workflowRecord.actions.submit')" value="SUBMIT" />
            <el-option :label="$t('workflowRecord.actions.approve')" value="APPROVE" />
            <el-option :label="$t('workflowRecord.actions.reject')" value="REJECT" />
            <el-option :label="$t('workflowRecord.actions.withdraw')" value="WITHDRAW" />
            <el-option :label="$t('workflowRecord.actions.cancel')" value="CANCEL" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ $t('workflowRecord.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ $t('workflowRecord.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ $t('workflowRecord.title') }}</span>
          <el-button :icon="Refresh" @click="loadData">{{ $t('workflowRecord.refresh') }}</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="businessType" :label="$t('workflowRecord.businessType')" width="130">
          <template #default="{ row }">{{ businessTypeLabel(row.businessType) }}</template>
        </el-table-column>
        <el-table-column prop="businessNo" :label="$t('workflowRecord.businessNo')" min-width="170" show-overflow-tooltip />
        <el-table-column prop="businessId" :label="$t('workflowRecord.businessId')" width="110" />
        <el-table-column prop="action" :label="$t('workflowRecord.action')" width="110">
          <template #default="{ row }">
            <el-tag :type="actionType(row.action)">{{ actionLabel(row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operatorUserId" :label="$t('workflowRecord.operatorId')" width="120" />
        <el-table-column prop="comment" :label="$t('workflowRecord.comment')" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.comment || '-' }}</template>
        </el-table-column>
        <el-table-column prop="actionTime" :label="$t('workflowRecord.actionTime')" width="180">
          <template #default="{ row }">{{ formatTime(row.actionTime) }}</template>
        </el-table-column>
        <el-table-column :label="$t('workflowRecord.operations')" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="warning" @click="openWithdraw(row)">{{ $t('workflowRecord.withdraw') }}</el-button>
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

    <el-dialog v-model="withdrawVisible" :title="$t('workflowRecord.withdrawDialog')" width="520px" @close="resetWithdraw">
      <el-alert
        v-if="currentRecord"
        :title="$t('workflowRecord.recordSummary', { type: businessTypeLabel(currentRecord.businessType), no: currentRecord.businessNo || currentRecord.businessId })"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      />
      <el-form :model="withdrawForm" label-width="90px">
        <el-form-item :label="$t('workflowRecord.withdrawComment')">
          <el-input
            v-model="withdrawForm.comment"
            type="textarea"
            :rows="4"
            maxlength="255"
            show-word-limit
            :placeholder="$t('workflowRecord.withdrawCommentPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="withdrawVisible = false">{{ $t('workflowRecord.cancel') }}</el-button>
        <el-button type="warning" :loading="withdrawSubmitting" @click="submitWithdraw">{{ $t('workflowRecord.confirmWithdraw') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { formatLocalizedDateTime } from '@/utils/locale'
import {
  getBusinessWorkflowRecords,
  getWorkflowRecords,
  withdrawWorkflow,
  type WorkflowRecord,
  type WorkflowRecordQuery
} from '@/api/workflow'

const route = useRoute()
const { t } = useI18n()
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
    ElMessage.success(t('workflowRecord.message.withdrawSuccess'))
    withdrawVisible.value = false
    await loadData()
  } catch (error) {
    ElMessage.error(t('workflowRecord.message.withdrawFailed'))
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
    PURCHASE_ORDER: t('workflowRecord.businessTypes.purchaseOrder'),
    SALES_ORDER: t('workflowRecord.businessTypes.salesOrder'),
    EXPENSE: t('workflowRecord.businessTypes.expense')
  }
  return type ? map[type] || type : '-'
}

const actionLabel = (action: string) => {
  const map: Record<string, string> = {
    SUBMIT: t('workflowRecord.actions.submit'),
    APPROVE: t('workflowRecord.actions.approve'),
    REJECT: t('workflowRecord.actions.reject'),
    WITHDRAW: t('workflowRecord.actions.withdraw'),
    CANCEL: t('workflowRecord.actions.cancel')
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
