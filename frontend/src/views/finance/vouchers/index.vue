<template>
  <div class="vouchers-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="来源类型">
          <el-select v-model="queryParams.sourceType" placeholder="请选择来源" clearable style="width: 150px">
            <el-option label="费用凭证" value="EXPENSE" />
            <el-option label="红冲凭证" value="EXPENSE_REVERSAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 150px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已审批" value="APPROVED" />
            <el-option label="已过账" value="POSTED" />
            <el-option label="已作废" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="凭证日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />
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
          <span>凭证查询</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="voucherNo" label="凭证单号" min-width="180" />
        <el-table-column prop="sourceType" label="来源" width="130">
          <template #default="{ row }">
            <el-tag :type="sourceTypeTag(row.sourceType)">
              {{ sourceTypeLabel(row.sourceType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bizDate" label="凭证日期" width="120" />
        <el-table-column prop="amount" label="凭证金额" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceNo" label="来源单号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(toVoucherRow(row))">查看</el-button>
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

    <el-dialog v-model="detailVisible" title="凭证详情" width="860px">
      <el-descriptions v-if="currentVoucher" :column="2" border>
        <el-descriptions-item label="凭证单号">{{ currentVoucher.voucherNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(currentVoucher.status) }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ sourceTypeLabel(currentVoucher.sourceType) }}</el-descriptions-item>
        <el-descriptions-item label="凭证日期">{{ currentVoucher.bizDate }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ formatMoney(currentVoucher.amount) }}</el-descriptions-item>
        <el-descriptions-item label="来源单号">{{ currentVoucher.sourceNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ currentVoucher.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table v-loading="detailLoading" :data="detailEntries" border stripe class="detail-table">
        <el-table-column prop="lineNo" label="行号" width="80" />
        <el-table-column prop="subjectCode" label="科目编码" width="140" />
        <el-table-column prop="subjectName" label="科目名称" min-width="180" />
        <el-table-column prop="debitAmount" label="借方金额" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.debitAmount) }}</template>
        </el-table-column>
        <el-table-column prop="creditAmount" label="贷方金额" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.creditAmount) }}</template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { formatLocalizedNumber } from '@/utils/locale'
import {
  getVoucher,
  getVoucherEntries,
  getVouchers,
  type Voucher,
  type VoucherEntry,
  type VoucherQuery
} from '@/api/finance'

const loading = ref(false)
const detailLoading = ref(false)
const tableData = ref<Voucher[]>([])
const detailEntries = ref<VoucherEntry[]>([])
const currentVoucher = ref<Voucher | null>(null)
const total = ref(0)
const detailVisible = ref(false)
const dateRange = ref<[string, string] | null>(null)

const queryParams = reactive<VoucherQuery>({
  pageNo: 1,
  pageSize: 10,
  sourceType: '',
  status: ''
})

const loadData = async () => {
  loading.value = true
  try {
    const response = await getVouchers({
      ...queryParams,
      dateFrom: dateRange.value?.[0],
      dateTo: dateRange.value?.[1]
    })
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
  queryParams.sourceType = ''
  queryParams.status = ''
  dateRange.value = null
  handleQuery()
}

const handleView = async (row: Voucher) => {
  detailVisible.value = true
  detailLoading.value = true
  try {
    currentVoucher.value = await getVoucher(row.id)
    detailEntries.value = await getVoucherEntries(row.id)
  } finally {
    detailLoading.value = false
  }
}

const toVoucherRow = (row: unknown) => row as Voucher

const formatMoney = (amount?: number) => {
  return `¥${formatLocalizedNumber(Number(amount || 0), {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`
}

const sourceTypeLabel = (sourceType?: string) => {
  const map: Record<string, string> = {
    EXPENSE: '费用凭证',
    EXPENSE_REVERSAL: '红冲凭证'
  }
  return sourceType ? map[sourceType] || sourceType : '-'
}

const sourceTypeTag = (sourceType?: string) => {
  const map: Record<string, 'success' | 'warning' | 'info'> = {
    EXPENSE: 'success',
    EXPENSE_REVERSAL: 'warning'
  }
  return sourceType ? map[sourceType] || 'info' : 'info'
}

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    APPROVED: '已审批',
    POSTED: '已过账',
    CANCELLED: '已作废'
  }
  return map[status] || status
}

const statusType = (status: string) => {
  const map: Record<string, 'info' | 'warning' | 'success' | 'danger'> = {
    DRAFT: 'info',
    APPROVED: 'warning',
    POSTED: 'success',
    CANCELLED: 'danger'
  }
  return map[status] || 'info'
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.vouchers-container {
  padding: 20px;

  .search-card,
  .table-card {
    margin-bottom: 20px;
  }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
  }

  .detail-table {
    margin-top: 16px;
  }

  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }
}
</style>
