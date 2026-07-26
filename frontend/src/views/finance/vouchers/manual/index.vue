<template>
  <div class="manual-voucher-page">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item :label="$t('financeReportPages.manualVouchers.voucherNo')">
          <el-input v-model="queryForm.voucherNo" :placeholder="$t('financeReportPages.manualVouchers.voucherNoPlaceholder')" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.common.status')">
          <el-select v-model="queryForm.status" :placeholder="$t('financeReportPages.manualVouchers.allStatuses')" clearable style="width: 150px">
            <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.manualVouchers.dateFrom')">
          <el-date-picker v-model="queryForm.dateFrom" type="date" value-format="YYYY-MM-DD" :placeholder="$t('financeReportPages.common.startDate')" style="width: 160px" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.manualVouchers.dateTo')">
          <el-date-picker v-model="queryForm.dateTo" type="date" value-format="YYYY-MM-DD" :placeholder="$t('financeReportPages.common.endDate')" style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ $t('financeReportPages.common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ $t('financeReportPages.common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>{{ $t('financeReportPages.manualVouchers.title') }}</span>
          <el-button
            v-permission="'finance:voucher:manage'"
            type="primary"
            :icon="Plus"
            @click="openCreate"
          >
            {{ $t('financeReportPages.manualVouchers.create') }}
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="voucherNo" :label="$t('financeReportPages.manualVouchers.voucherNo')" width="200" />
        <el-table-column prop="bizDate" :label="$t('financeReportPages.manualVouchers.voucherDate')" width="120" />
        <el-table-column prop="amount" :label="$t('financeReportPages.common.amount')" width="150" align="right">
          <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('financeReportPages.common.status')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="$t('financeReportPages.common.summary')" min-width="180" show-overflow-tooltip />
        <el-table-column :label="$t('financeReportPages.common.actions')" width="380" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">{{ $t('financeReportPages.common.view') }}</el-button>
            <el-button link type="primary" @click="handlePrint(row)">{{ $t('financeReportPages.common.print') }}</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'finance:voucher:manage'"
              link
              type="primary"
              @click="openEdit(row)"
            >{{ $t('financeReportPages.common.edit') }}</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'finance:voucher:manage'"
              link
              type="primary"
              @click="handleSubmit(row)"
            >{{ $t('financeReportPages.common.submit') }}</el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              v-permission="'finance:voucher:approve'"
              link
              type="success"
              @click="handleApprove(row)"
            >{{ $t('financeReportPages.common.approve') }}</el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              v-permission="'finance:voucher:approve'"
              link
              type="warning"
              @click="openReject(row)"
            >{{ $t('financeReportPages.common.reject') }}</el-button>
            <el-button
              v-if="row.status === 'APPROVED'"
              v-permission="'finance:voucher:post'"
              link
              type="success"
              @click="handlePost(row)"
            >{{ $t('financeReportPages.common.post') }}</el-button>
            <el-button
              v-if="row.status === 'POSTED'"
              v-permission="'finance:voucher:post'"
              link
              type="danger"
              @click="openCancel(row)"
            >{{ $t('financeReportPages.common.void') }}</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'finance:voucher:manage'"
              link
              type="danger"
              @click="handleDelete(row)"
            >{{ $t('financeReportPages.common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryForm.pageNo"
        v-model:page-size="queryForm.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleQuery"
        @current-change="handlePageChange"
      />
    </el-card>

    <!-- 录入/编辑弹窗 -->
    <el-dialog
      v-model="editVisible"
      :title="editMode === 'create' ? $t('financeReportPages.manualVouchers.createTitle') : $t('financeReportPages.manualVouchers.editTitle')"
      width="900px"
      @close="resetEditForm"
    >
      <el-form ref="editFormRef" :model="editForm" label-width="90px">
        <el-form-item :label="$t('financeReportPages.manualVouchers.voucherDate')" prop="bizDate" required>
          <el-date-picker v-model="editForm.bizDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('financeReportPages.manualVouchers.selectVoucherDate')" style="width: 220px" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.common.summary')">
          <el-input v-model="editForm.remark" :placeholder="$t('financeReportPages.manualVouchers.wholeVoucherSummary')" style="width: 100%" />
        </el-form-item>
      </el-form>

      <el-table :data="editForm.lines" border size="small" class="entry-table">
        <el-table-column type="index" :label="$t('financeReportPages.manualVouchers.line')" width="50" />
        <el-table-column :label="$t('financeReportPages.manualVouchers.accountSubject')" min-width="220">
          <template #default="{ row }">
            <el-select
              v-model="row.subjectId"
              filterable
              :placeholder="$t('financeReportPages.manualVouchers.selectSubject')"
              style="width: 100%"
            >
              <el-option
                v-for="s in subjects"
                :key="s.id"
                :label="subjectLabel(s)"
                :value="s.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column :label="$t('financeReportPages.common.debitAmount')" width="150">
          <template #default="{ row }">
            <el-input-number
              v-model="row.debitAmount"
              :min="0"
              :precision="2"
              :controls="false"
              style="width: 100%"
              @change="onDebitChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column :label="$t('financeReportPages.common.creditAmount')" width="150">
          <template #default="{ row }">
            <el-input-number
              v-model="row.creditAmount"
              :min="0"
              :precision="2"
              :controls="false"
              style="width: 100%"
              @change="onCreditChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column :label="$t('financeReportPages.manualVouchers.lineSummary')" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.summary" :placeholder="$t('financeReportPages.manualVouchers.lineSummary')" />
          </template>
        </el-table-column>
        <el-table-column :label="$t('financeReportPages.common.actions')" width="70" align="center">
          <template #default="{ $index }">
            <el-button
              link
              type="danger"
              :disabled="editForm.lines.length <= 2"
              @click="removeLine($index)"
            >{{ $t('financeReportPages.common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="entry-toolbar">
        <el-button link type="primary" :icon="Plus" @click="addLine">{{ $t('financeReportPages.manualVouchers.addLine') }}</el-button>
        <div class="balance-summary">
          <span>{{ $t('financeReportPages.manualVouchers.debitTotal') }}<strong>{{ formatAmount(debitTotal) }}</strong></span>
          <span>{{ $t('financeReportPages.manualVouchers.creditTotal') }}<strong>{{ formatAmount(creditTotal) }}</strong></span>
          <el-tag :type="balanced ? 'success' : 'danger'" size="small">
            {{ balanced ? $t('financeReportPages.manualVouchers.balanced') : $t('financeReportPages.manualVouchers.unbalanced') }}
          </el-tag>
        </div>
      </div>

      <template #footer>
        <el-button @click="editVisible = false">{{ $t('financeReportPages.common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" :disabled="!canSave" @click="handleSave">{{ $t('financeReportPages.manualVouchers.saveDraft') }}</el-button>
      </template>
    </el-dialog>

    <!-- 驳回原因弹窗 -->
    <el-dialog v-model="rejectVisible" :title="$t('financeReportPages.manualVouchers.rejectTitle')" width="480px">
      <el-form label-width="80px">
        <el-form-item :label="$t('financeReportPages.manualVouchers.rejectReason')" required>
          <el-input v-model="rejectReason" type="textarea" :rows="3" :placeholder="$t('financeReportPages.manualVouchers.rejectReasonPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">{{ $t('financeReportPages.common.cancel') }}</el-button>
        <el-button type="warning" :loading="rejecting" :disabled="!rejectReason.trim()" @click="handleReject">{{ $t('financeReportPages.manualVouchers.confirmReject') }}</el-button>
      </template>
    </el-dialog>

    <!-- 作废原因弹窗 -->
    <el-dialog
      v-model="cancelVisible"
      :title="$t('financeReportPages.manualVouchers.cancelTitle')"
      width="520px"
      :close-on-click-modal="!cancelling"
      :close-on-press-escape="!cancelling"
      :show-close="!cancelling"
    >
      <el-alert
        v-if="cancellingRow"
        type="warning"
        :closable="false"
        show-icon
        class="cancel-alert"
      >
        <template #title>
          {{ $t('financeReportPages.manualVouchers.cancelWarning', { no: cancellingRow.voucherNo }) }}
        </template>
      </el-alert>
      <el-form label-width="90px" class="cancel-form">
        <el-form-item :label="$t('financeReportPages.manualVouchers.cancelReason')" required>
          <el-input
            v-model="cancelReason"
            type="textarea"
            :rows="4"
            maxlength="512"
            show-word-limit
            :placeholder="$t('financeReportPages.manualVouchers.cancelReasonPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="cancelling" @click="cancelVisible = false">{{ $t('financeReportPages.common.cancel') }}</el-button>
        <el-button
          type="danger"
          :loading="cancelling"
          :disabled="cancelling || !cancelReason.trim()"
          @click="handleCancel"
        >
          {{ $t('financeReportPages.manualVouchers.confirmCancel') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="$t('financeReportPages.manualVouchers.detailTitle')" width="820px">
      <div v-if="currentVoucher">
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="$t('financeReportPages.manualVouchers.voucherNo')">{{ currentVoucher.voucherNo }}</el-descriptions-item>
          <el-descriptions-item :label="$t('financeReportPages.manualVouchers.voucherDate')">{{ currentVoucher.bizDate }}</el-descriptions-item>
          <el-descriptions-item :label="$t('financeReportPages.common.status')">
            <el-tag :type="statusTagType(currentVoucher.status)">{{ statusLabel(currentVoucher.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('financeReportPages.common.amount')">{{ formatAmount(currentVoucher.amount) }}</el-descriptions-item>
          <el-descriptions-item :label="$t('financeReportPages.common.summary')" :span="2">{{ currentVoucher.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('financeReportPages.manualVouchers.originalPostedVoucherId')">{{ currentVoucher.postedVoucherId || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('financeReportPages.manualVouchers.reversalVoucherId')">{{ currentVoucher.reversalVoucherId || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('financeReportPages.manualVouchers.submittedTime')">{{ currentVoucher.submittedTime || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('financeReportPages.manualVouchers.approvedTime')">{{ currentVoucher.approvedTime || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('financeReportPages.manualVouchers.postedTime')">{{ currentVoucher.postedTime || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('financeReportPages.manualVouchers.cancelledTime')">{{ currentVoucher.cancelledTime || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="currentVoucher.cancelReason" :label="$t('financeReportPages.manualVouchers.cancelReason')" :span="2">
            {{ currentVoucher.cancelReason }}
          </el-descriptions-item>
          <el-descriptions-item v-if="currentVoucher.rejectReason" :label="$t('financeReportPages.manualVouchers.rejectReason')" :span="2">
            {{ currentVoucher.rejectReason }}
          </el-descriptions-item>
        </el-descriptions>

        <el-table :data="currentVoucher.lines" border style="margin-top: 16px">
          <el-table-column type="index" :label="$t('financeReportPages.manualVouchers.line')" width="50" />
          <el-table-column prop="subjectCode" :label="$t('financeReportPages.common.subjectCode')" width="110" />
          <el-table-column prop="subjectName" :label="$t('financeReportPages.common.subjectName')" min-width="150" />
          <el-table-column prop="debitAmount" :label="$t('financeReportPages.common.debitAmount')" width="140" align="right">
            <template #default="{ row }">{{ formatAmount(row.debitAmount) }}</template>
          </el-table-column>
          <el-table-column prop="creditAmount" :label="$t('financeReportPages.common.creditAmount')" width="140" align="right">
            <template #default="{ row }">{{ formatAmount(row.creditAmount) }}</template>
          </el-table-column>
          <el-table-column prop="summary" :label="$t('financeReportPages.manualVouchers.lineSummary')" min-width="140" show-overflow-tooltip />
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { printVoucher } from '@/utils/bizPrint'
import {
  approveManualVoucher,
  cancelManualVoucher,
  createManualVoucher,
  deleteManualVoucher,
  getAccountSubjects,
  getManualVoucher,
  getManualVouchers,
  postManualVoucher,
  rejectManualVoucher,
  submitManualVoucher,
  updateManualVoucher
} from '@/api/finance'
import { useManualVoucherPresentation } from '@/composables/useManualVoucherPresentation'
import { useManualVoucherList } from '@/composables/useManualVoucherList'
import { useManualVoucherForm } from '@/composables/useManualVoucherForm'

const { t } = useI18n()

const notify = {
  onError: (message: string) => ElMessage.error(message),
  onSuccess: (message: string) => ElMessage.success(message),
  onWarning: (message: string) => ElMessage.warning(message)
}

const {
  formatAmount,
  statusLabel,
  statusOptions,
  statusTagType,
  subjectLabel
} = useManualVoucherPresentation(t)

const {
  cancelReason,
  cancelVisible,
  cancelling,
  cancellingRow,
  currentVoucher,
  detailVisible,
  handleApprove,
  handleCancel,
  handleDelete,
  handlePageChange,
  handlePost,
  handlePrint,
  handleQuery,
  handleReject,
  handleReset,
  handleSubmit,
  loadData,
  loadSubjects,
  loading,
  openCancel,
  openDetail,
  openReject,
  queryForm,
  rejectReason,
  rejectVisible,
  rejecting,
  subjects,
  tableData,
  total
} = useManualVoucherList(t, {
  getVouchers: getManualVouchers,
  getVoucher: getManualVoucher,
  getSubjects: getAccountSubjects,
  submitVoucher: submitManualVoucher,
  approveVoucher: approveManualVoucher,
  postVoucher: postManualVoucher,
  deleteVoucher: deleteManualVoucher,
  cancelVoucher: cancelManualVoucher,
  rejectVoucher: rejectManualVoucher,
  printVoucher,
  decoratePrint: (detail) => ({
    ...detail,
    sourceType: 'MANUAL',
    sourceTypeLabel: t('financeReportPages.manualVouchers.title'),
    statusLabel: statusLabel(detail.status),
    entries: detail.lines || []
  }),
  confirm: (message, title, options) => ElMessageBox.confirm(message, title, options as any),
  ...notify
})

const {
  addLine,
  balanced,
  canSave,
  creditTotal,
  debitTotal,
  editForm,
  editMode,
  editVisible,
  handleSave,
  onCreditChange,
  onDebitChange,
  openCreate,
  openEdit,
  removeLine,
  resetEditForm,
  saving
} = useManualVoucherForm(t, {
  getVoucher: getManualVoucher,
  createVoucher: createManualVoucher,
  updateVoucher: updateManualVoucher,
  ensureSubjects: async () => {
    if (subjects.value.length === 0) await loadSubjects()
  },
  onSubmitted: loadData,
  ...notify
})

onMounted(() => {
  loadData()
  loadSubjects()
})
</script>

<style scoped>
.manual-voucher-page {
  padding: 20px;
}

.search-card,
.table-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.entry-table {
  margin-top: 8px;
}

.entry-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}

.balance-summary {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 14px;
}

.balance-summary strong {
  color: #111827;
}

.cancel-alert {
  margin-bottom: 16px;
}

.cancel-form {
  margin-top: 4px;
}

.el-pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
