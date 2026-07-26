<template>
  <div class="vouchers-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item :label="$t('financeReportPages.vouchers.sourceType')">
          <el-select v-model="queryParams.sourceType" :placeholder="$t('financeReportPages.vouchers.sourcePlaceholder')" clearable style="width: 150px">
            <el-option :label="$t('financeReportPages.vouchers.sourceValue.expense')" value="EXPENSE" />
            <el-option :label="$t('financeReportPages.vouchers.sourceValue.expenseReversal')" value="EXPENSE_REVERSAL" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.common.status')">
          <el-select v-model="queryParams.status" :placeholder="$t('financeReportPages.common.statusPlaceholder')" clearable style="width: 150px">
            <el-option :label="$t('financeReportPages.vouchers.status.draft')" value="DRAFT" />
            <el-option :label="$t('financeReportPages.vouchers.status.approved')" value="APPROVED" />
            <el-option :label="$t('financeReportPages.vouchers.status.posted')" value="POSTED" />
            <el-option :label="$t('financeReportPages.vouchers.status.cancelled')" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.vouchers.voucherDate')">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            :range-separator="$t('financeReportPages.common.rangeSeparator')"
            :start-placeholder="$t('financeReportPages.common.startDate')"
            :end-placeholder="$t('financeReportPages.common.endDate')"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ $t('financeReportPages.common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ $t('financeReportPages.common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ $t('financeReportPages.vouchers.title') }}</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="voucherNo" :label="$t('financeReportPages.vouchers.voucherNo')" min-width="180" />
        <el-table-column prop="sourceType" :label="$t('financeReportPages.vouchers.source')" width="150">
          <template #default="{ row }">
            <el-tag :type="sourceTypeTag(row.sourceType)">
              {{ sourceTypeLabel(row.sourceType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bizDate" :label="$t('financeReportPages.vouchers.voucherDate')" width="130">
          <template #default="{ row }">{{ formatDate(row.bizDate) }}</template>
        </el-table-column>
        <el-table-column prop="amount" :label="$t('financeReportPages.vouchers.voucherAmount')" width="150" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('financeReportPages.common.status')" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceNo" :label="$t('financeReportPages.vouchers.sourceNo')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="remark" :label="$t('financeReportPages.common.remark')" min-width="180" show-overflow-tooltip />
        <el-table-column :label="$t('financeReportPages.common.actions')" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(toVoucherRow(row))">{{ $t('financeReportPages.common.view') }}</el-button>
            <el-button link type="primary" @click="handlePrint(toVoucherRow(row))">{{ $t('financeReportPages.common.print') }}</el-button>
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

    <el-dialog v-model="detailVisible" :title="$t('financeReportPages.vouchers.detailTitle')" width="860px">
      <el-descriptions v-if="currentVoucher" :column="2" border>
        <el-descriptions-item :label="$t('financeReportPages.vouchers.voucherNo')">{{ currentVoucher.voucherNo }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.common.status')">{{ statusLabel(currentVoucher.status) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.vouchers.source')">{{ sourceTypeLabel(currentVoucher.sourceType) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.vouchers.voucherDate')">{{ formatDate(currentVoucher.bizDate) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.common.amount')">{{ formatMoney(currentVoucher.amount) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.vouchers.sourceNo')">{{ currentVoucher.sourceNo || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.common.remark')" :span="2">{{ currentVoucher.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table v-loading="detailLoading" :data="detailEntries" border stripe class="detail-table">
        <el-table-column prop="lineNo" :label="$t('financeReportPages.common.lineNo')" width="80" />
        <el-table-column prop="subjectCode" :label="$t('financeReportPages.common.subjectCode')" width="140" />
        <el-table-column prop="subjectName" :label="$t('financeReportPages.common.subjectName')" min-width="180" />
        <el-table-column prop="debitAmount" :label="$t('financeReportPages.common.debitAmount')" width="150" align="right">
          <template #default="{ row }">{{ formatMoney(row.debitAmount) }}</template>
        </el-table-column>
        <el-table-column prop="creditAmount" :label="$t('financeReportPages.common.creditAmount')" width="150" align="right">
          <template #default="{ row }">{{ formatMoney(row.creditAmount) }}</template>
        </el-table-column>
        <el-table-column prop="summary" :label="$t('financeReportPages.common.summary')" min-width="180" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { formatLocalizedCurrency, formatLocalizedDate } from '@/utils/locale'
import { printVoucher } from '@/utils/bizPrint'
import {
  getVoucher,
  getVoucherEntries,
  getVouchers,
  type Voucher,
  type VoucherEntry,
  type VoucherQuery
} from '@/api/finance'

const { t } = useI18n()

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
  } catch {
    ElMessage.error(t('financeReportPages.vouchers.message.loadFailed'))
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
  currentVoucher.value = null
  detailEntries.value = []
  try {
    const [voucher, entries] = await Promise.all([
      getVoucher(row.id),
      getVoucherEntries(row.id)
    ])
    currentVoucher.value = voucher
    detailEntries.value = entries
  } catch {
    ElMessage.error(t('financeReportPages.vouchers.message.detailLoadFailed'))
  } finally {
    detailLoading.value = false
  }
}

const handlePrint = async (row: Voucher) => {
  try {
    const [voucher, entries] = await Promise.all([
      getVoucher(row.id),
      getVoucherEntries(row.id)
    ])
    printVoucher({
      ...voucher,
      sourceTypeLabel: sourceTypeLabel(voucher.sourceType),
      statusLabel: statusLabel(voucher.status),
      entries
    })
  } catch {
    ElMessage.error(t('financeReportPages.vouchers.message.printLoadFailed'))
  }
}

const toVoucherRow = (row: unknown) => row as Voucher

const formatMoney = (amount?: number) => {
  return formatLocalizedCurrency(Number(amount || 0))
}

const formatDate = (value?: string) => formatLocalizedDate(value)

const sourceTypeLabel = (sourceType?: string) => {
  const map: Record<string, string> = {
    EXPENSE: t('financeReportPages.vouchers.sourceValue.expense'),
    EXPENSE_REVERSAL: t('financeReportPages.vouchers.sourceValue.expenseReversal')
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
    DRAFT: t('financeReportPages.vouchers.status.draft'),
    APPROVED: t('financeReportPages.vouchers.status.approved'),
    POSTED: t('financeReportPages.vouchers.status.posted'),
    CANCELLED: t('financeReportPages.vouchers.status.cancelled')
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
