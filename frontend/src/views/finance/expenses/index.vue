<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item :label="$t('financeReportPages.common.status')">
          <el-select v-model="queryForm.status" :placeholder="$t('financeReportPages.common.selectPlaceholder')" clearable style="width: 140px">
            <el-option :label="$t('financeReportPages.expenses.status.draft')" value="DRAFT" />
            <el-option :label="$t('financeReportPages.expenses.status.pending')" value="PENDING" />
            <el-option :label="$t('financeReportPages.expenses.status.approved')" value="APPROVED" />
            <el-option :label="$t('financeReportPages.expenses.status.posted')" value="POSTED" />
            <el-option :label="$t('financeReportPages.expenses.status.rejected')" value="REJECTED" />
            <el-option :label="$t('financeReportPages.expenses.status.cancelled')" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.common.dateRange')">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            :range-separator="$t('financeReportPages.common.rangeSeparator')"
            :start-placeholder="$t('financeReportPages.common.startDate')"
            :end-placeholder="$t('financeReportPages.common.endDate')"
            value-format="YYYY-MM-DD"
            style="width: 280px"
          />
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
          <span>{{ $t('financeReportPages.expenses.title') }}</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">{{ $t('financeReportPages.expenses.newExpense') }}</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="expenseNo" :label="$t('financeReportPages.expenses.expenseNo')" width="170" />
        <el-table-column prop="expenseDate" :label="$t('financeReportPages.expenses.expenseDate')" width="120" />
        <el-table-column prop="subjectId" :label="$t('financeReportPages.expenses.expenseSubject')" min-width="180">
          <template #default="{ row }">{{ subjectName(row.subjectId) }}</template>
        </el-table-column>
        <el-table-column prop="paymentSubjectId" :label="$t('financeReportPages.expenses.paymentSubject')" min-width="180">
          <template #default="{ row }">{{ subjectName(row.paymentSubjectId) }}</template>
        </el-table-column>
        <el-table-column prop="amount" :label="$t('financeReportPages.expenses.expenseAmount')" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('financeReportPages.common.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="voucherNo" :label="$t('financeReportPages.expenses.voucher')" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.voucherNo || '-' }}</template>
        </el-table-column>
        <el-table-column :label="$t('financeReportPages.expenses.voucherValidation')" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.voucherNo" :type="row.voucherBalanced && row.amountMatched ? 'success' : 'danger'" size="small">
              {{ row.voucherBalanced && row.amountMatched ? $t('financeReportPages.expenses.balanced') : $t('financeReportPages.common.abnormal') }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="$t('financeReportPages.common.remark')" min-width="160" show-overflow-tooltip />
        <el-table-column :label="$t('financeReportPages.common.actions')" width="430" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'finance:expense:manage'" type="primary" link :icon="View" @click="handleView(row)">{{ $t('financeReportPages.common.view') }}</el-button>
            <el-button v-permission="'finance:expense:manage'" type="primary" link @click="handlePrint(row)">{{ $t('financeReportPages.common.print') }}</el-button>
            <el-button
              v-permission="'finance:expense:manage'"
              type="primary"
              link
              :icon="DataAnalysis"
              @click="handleReconciliation(row)"
            >
              {{ $t('financeReportPages.expenses.reconciliation') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'REJECTED'"
              v-permission="'finance:expense:manage'"
              type="primary"
              link
              :icon="Edit"
              @click="handleEdit(row)"
            >
              {{ $t('financeReportPages.common.edit') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'REJECTED'"
              v-permission="'finance:expense:manage'"
              type="success"
              link
              :icon="Promotion"
              @click="handleSubmit(row)"
            >
              {{ $t('financeReportPages.common.submit') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'REJECTED'"
              v-permission="'finance:expense:manage'"
              type="danger"
              link
              :icon="CircleClose"
              @click="handleCancel(row)"
            >
              {{ $t('financeReportPages.common.void') }}
            </el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              v-permission="'finance:expense:manage'"
              type="success"
              link
              :icon="CircleCheck"
              @click="handleApprove(row)"
            >
              {{ $t('financeReportPages.common.approve') }}
            </el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              v-permission="'finance:expense:manage'"
              type="danger"
              link
              :icon="CircleClose"
              @click="handleReject(row)"
            >
              {{ $t('financeReportPages.common.reject') }}
            </el-button>
            <el-button
              v-if="row.status === 'APPROVED'"
              v-permission="'finance:expense:manage'"
              type="warning"
              link
              :icon="Money"
              @click="handlePost(row)"
            >
              {{ $t('financeReportPages.common.post') }}
            </el-button>
            <el-button
              v-if="row.status === 'POSTED' && !row.reversed"
              v-permission="'finance:expense:manage'"
              type="danger"
              link
              :icon="RefreshLeft"
              @click="handleReverse(row)"
            >
              {{ $t('financeReportPages.expenses.reverse') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.pageNo"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-form-item :label="$t('financeReportPages.expenses.expenseSubject')" prop="subjectId">
          <el-select v-model="formData.subjectId" :placeholder="$t('financeReportPages.expenses.selectExpenseSubject')" filterable style="width: 100%">
            <el-option
              v-for="subject in expenseSubjects"
              :key="subject.id"
              :label="subjectLabel(subject)"
              :value="String(subject.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.expenses.department')">
          <el-select v-model="formData.deptId" clearable filterable :placeholder="$t('financeReportPages.expenses.selectDepartment')" style="width: 100%">
            <el-option v-for="dept in departmentOptions" :key="dept.id" :label="deptLabel(dept)" :value="String(dept.id)" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.expenses.paymentSubject')" prop="paymentSubjectId">
          <el-select v-model="formData.paymentSubjectId" :placeholder="$t('financeReportPages.expenses.selectPaymentSubject')" filterable style="width: 100%">
            <el-option
              v-for="subject in paymentSubjects"
              :key="subject.id"
              :label="subjectLabel(subject)"
              :value="String(subject.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.expenses.expenseDate')" prop="expenseDate">
          <el-date-picker
            v-model="formData.expenseDate"
            type="date"
            :placeholder="$t('financeReportPages.expenses.selectDate')"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.expenses.expenseAmount')" prop="amount">
          <el-input-number v-model="formData.amount" :min="0.01" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-alert
          v-if="budgetPreview"
          :type="budgetPreview.overrun ? 'warning' : 'success'"
          :title="budgetPreview.overrun ? $t('financeReportPages.expenses.budgetOverrun') : $t('financeReportPages.expenses.budgetAvailable')"
          :description="$t('financeReportPages.expenses.budgetPreview', { available: formatAmount(budgetPreview.availableAmount), projected: formatAmount(budgetPreview.projectedAvailableAmount) })"
          :closable="false"
          show-icon
          class="budget-preview"
        />
        <el-form-item :label="$t('financeReportPages.common.remark')" prop="remark">
          <el-input v-model="formData.remark" type="textarea" :rows="3" :placeholder="$t('financeReportPages.expenses.remarkPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('financeReportPages.common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSave">{{ $t('financeReportPages.common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="viewDialogVisible" :title="$t('financeReportPages.expenses.detailTitle')" width="760px">
      <el-descriptions :column="2" border>
        <el-descriptions-item :label="$t('financeReportPages.expenses.expenseNo')">{{ viewData.expenseNo }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.expenses.expenseDate')">{{ viewData.expenseDate }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.expenses.expenseSubject')">{{ subjectName(viewData.subjectId) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.expenses.paymentSubject')">{{ subjectName(viewData.paymentSubjectId) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.expenses.department')">{{ deptName(viewData.deptId) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.expenses.budgetState')">{{ viewData.budgetState || 'NONE' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.expenses.expenseAmount')">{{ formatAmount(viewData.amount) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.common.status')">
          <el-tag :type="getStatusType(viewData.status)">
            {{ getStatusLabel(viewData.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.expenses.voucherNo')">{{ viewData.voucherNo || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.expenses.voucherStatus')">{{ viewData.voucherStatus ? getStatusLabel(viewData.voucherStatus) : '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.expenses.voucherEntries')">{{ viewData.voucherEntryCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.expenses.voucherAmount')">{{ viewData.voucherAmount == null ? '-' : formatAmount(viewData.voucherAmount) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.expenses.reversalVoucher')">{{ viewData.reversalVoucherNo || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.expenses.reversalStatus')">{{ viewData.reversed ? $t('financeReportPages.expenses.reversed') : '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.common.remark')" :span="2">{{ viewData.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="viewDialogVisible = false">{{ $t('financeReportPages.common.close') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reconciliationDialogVisible" :title="$t('financeReportPages.expenses.reconciliationTitle')" width="980px" destroy-on-close>
      <div v-loading="reconciliationLoading">
        <template v-if="reconciliationData">
          <el-alert
            class="reconciliation-alert"
            :title="reconciliationPassed ? $t('financeReportPages.expenses.reconciliationPassed') : $t('financeReportPages.expenses.reconciliationFailed')"
            :type="reconciliationPassed ? 'success' : 'warning'"
            show-icon
            :closable="false"
          />
          <el-descriptions :column="3" border>
            <el-descriptions-item :label="$t('financeReportPages.expenses.expenseNo')">{{ reconciliationData.expense.expenseNo }}</el-descriptions-item>
            <el-descriptions-item :label="$t('financeReportPages.expenses.expenseAmount')">{{ formatAmount(reconciliationData.expense.amount) }}</el-descriptions-item>
            <el-descriptions-item :label="$t('financeReportPages.expenses.originalVoucher')">{{ reconciliationData.voucher?.voucherNo || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$t('financeReportPages.expenses.debitTotal')">{{ formatAmount(reconciliationData.debitTotal) }}</el-descriptions-item>
            <el-descriptions-item :label="$t('financeReportPages.expenses.creditTotal')">{{ formatAmount(reconciliationData.creditTotal) }}</el-descriptions-item>
            <el-descriptions-item :label="$t('financeReportPages.expenses.reversalVoucher')">{{ reconciliationData.reversalVoucher?.voucherNo || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$t('financeReportPages.expenses.voucherExists')">
              <el-tag :type="reconciliationData.voucherMissing ? 'danger' : 'success'">
                {{ reconciliationData.voucherMissing ? $t('financeReportPages.expenses.missing') : $t('financeReportPages.common.normal') }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('financeReportPages.expenses.entryBalanced')">
              <el-tag :type="checkTagType(reconciliationData.voucherBalanced)">
                {{ checkLabel(reconciliationData.voucherBalanced) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('financeReportPages.expenses.amountMatched')">
              <el-tag :type="checkTagType(reconciliationData.amountMatched)">
                {{ checkLabel(reconciliationData.amountMatched) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('financeReportPages.expenses.voucherLinked')">
              <el-tag :type="checkTagType(reconciliationData.voucherLinkedToExpense)">
                {{ checkLabel(reconciliationData.voucherLinkedToExpense) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('financeReportPages.expenses.reversalBalanced')">
              <el-tag :type="checkTagType(!reconciliationData.reversed || reconciliationData.reversalVoucherBalanced)">
                {{ reconciliationData.reversed ? checkLabel(reconciliationData.reversalVoucherBalanced) : $t('financeReportPages.expenses.notReversed') }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('financeReportPages.expenses.reversalAmount')">
              <el-tag :type="checkTagType(!reconciliationData.reversed || reconciliationData.reversalAmountMatched)">
                {{ reconciliationData.reversed ? checkLabel(reconciliationData.reversalAmountMatched) : $t('financeReportPages.expenses.notReversed') }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>

          <section class="entry-section" :aria-label="$t('financeReportPages.expenses.originalEntries')">
            <div class="section-title">{{ $t('financeReportPages.expenses.originalEntries') }}</div>
            <el-table :data="reconciliationData.entries" border stripe>
              <el-table-column prop="lineNo" :label="$t('financeReportPages.common.lineNo')" width="80" />
              <el-table-column prop="subjectCode" :label="$t('financeReportPages.common.subjectCode')" width="140" />
              <el-table-column prop="subjectName" :label="$t('financeReportPages.common.subjectName')" min-width="180" show-overflow-tooltip />
              <el-table-column prop="debitAmount" :label="$t('financeReportPages.common.debitAmount')" width="130" align="right">
                <template #default="{ row }">{{ formatAmount(row.debitAmount) }}</template>
              </el-table-column>
              <el-table-column prop="creditAmount" :label="$t('financeReportPages.common.creditAmount')" width="130" align="right">
                <template #default="{ row }">{{ formatAmount(row.creditAmount) }}</template>
              </el-table-column>
              <el-table-column prop="summary" :label="$t('financeReportPages.common.summary')" min-width="180" show-overflow-tooltip />
            </el-table>
          </section>

          <section class="entry-section" :aria-label="$t('financeReportPages.expenses.reversalEntries')">
            <div class="section-title">{{ $t('financeReportPages.expenses.reversalEntries') }}</div>
            <el-table :data="reconciliationData.reversalEntries" border stripe>
              <el-table-column prop="lineNo" :label="$t('financeReportPages.common.lineNo')" width="80" />
              <el-table-column prop="subjectCode" :label="$t('financeReportPages.common.subjectCode')" width="140" />
              <el-table-column prop="subjectName" :label="$t('financeReportPages.common.subjectName')" min-width="180" show-overflow-tooltip />
              <el-table-column prop="debitAmount" :label="$t('financeReportPages.common.debitAmount')" width="130" align="right">
                <template #default="{ row }">{{ formatAmount(row.debitAmount) }}</template>
              </el-table-column>
              <el-table-column prop="creditAmount" :label="$t('financeReportPages.common.creditAmount')" width="130" align="right">
                <template #default="{ row }">{{ formatAmount(row.creditAmount) }}</template>
              </el-table-column>
              <el-table-column prop="summary" :label="$t('financeReportPages.common.summary')" min-width="180" show-overflow-tooltip />
            </el-table>
          </section>
        </template>
      </div>
      <template #footer>
        <el-button @click="reconciliationDialogVisible = false">{{ $t('financeReportPages.common.close') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rejectDialogVisible" :title="$t('financeReportPages.expenses.rejectTitle')" width="500px">
      <el-form :model="rejectForm" label-width="80px">
        <el-form-item :label="$t('financeReportPages.expenses.rejectReason')" required>
          <el-input v-model="rejectForm.reason" type="textarea" :rows="4" :placeholder="$t('financeReportPages.expenses.rejectReasonPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">{{ $t('financeReportPages.common.cancel') }}</el-button>
        <el-button type="danger" :loading="submitLoading" @click="handleConfirmReject">{{ $t('financeReportPages.expenses.confirmReject') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { printExpense } from '@/utils/bizPrint'
import {
  CircleCheck,
  CircleClose,
  DataAnalysis,
  Edit,
  Money,
  Plus,
  Promotion,
  Refresh,
  RefreshLeft,
  Search,
  View
} from '@element-plus/icons-vue'
import {
  approveExpense,
  cancelExpense,
  createExpense,
  getAccountSubjectTree,
  getExpense,
  getExpenseReconciliation,
  getExpenses,
  getBudgetExecution,
  postExpense,
  rejectExpense,
  reverseExpense,
  submitExpense,
  updateExpense
} from '@/api/finance'
import { getDeptTree, type Dept } from '@/api/system'
import { useExpensePresentation } from '@/composables/useExpensePresentation'
import { useExpenseList } from '@/composables/useExpenseList'
import { useExpenseForm } from '@/composables/useExpenseForm'

const { t } = useI18n()

const formRef = ref<FormInstance>()
const departmentOptions = ref<Dept[]>([])
const budgetPreview = ref<Awaited<ReturnType<typeof getBudgetExecution>>>()
const flattenDepartments = (items: Dept[]): Dept[] => items.flatMap((item) => [item, ...flattenDepartments(item.children || [])])
const deptLabel = (dept: Dept) => `${dept.code || dept.deptCode || ''} ${dept.name || dept.deptName || ''}`.trim()
const deptName = (id?: string | number) => departmentOptions.value.find((item) => String(item.id) === String(id))?.name || '-'

const notify = {
  onError: (message: string) => ElMessage.error(message),
  onSuccess: (message: string) => ElMessage.success(message),
  onWarning: (message: string) => ElMessage.warning(message)
}

const {
  dateRange,
  handleApprove,
  handleCancel,
  handlePageChange,
  handlePost,
  handlePrint,
  handleQuery,
  handleReconciliation,
  handleReset,
  handleReverse,
  handleSizeChange,
  handleSubmit,
  handleView,
  loadData,
  loadSubjects,
  loading,
  pagination,
  queryForm,
  reconciliationData,
  reconciliationDialogVisible,
  reconciliationLoading,
  subjectOptions,
  tableData,
  viewData,
  viewDialogVisible
} = useExpenseList(t, {
  getExpenses,
  getExpense,
  getSubjectTree: getAccountSubjectTree,
  getReconciliation: getExpenseReconciliation,
  submitExpense,
  approveExpense,
  postExpense,
  reverseExpense,
  cancelExpense,
  printExpense,
  decoratePrint: (detail) => ({
    ...detail,
    subjectName: rawSubjectName(detail.subjectId) || String(detail.subjectId),
    paymentSubjectName: rawSubjectName(detail.paymentSubjectId) || String(detail.paymentSubjectId)
  }),
  confirm: (message, title, options) => ElMessageBox.confirm(message, title, options as any),
  onError: notify.onError,
  onSuccess: notify.onSuccess
})

const {
  checkLabel,
  checkTagType,
  expenseSubjects,
  formatAmount,
  getStatusLabel,
  getStatusType,
  paymentSubjects,
  rawSubjectName,
  reconciliationPassed,
  subjectLabel,
  subjectName
} = useExpensePresentation(t, { subjects: subjectOptions, reconciliation: reconciliationData })

const {
  dialogTitle,
  dialogVisible,
  formData,
  handleAdd,
  handleConfirmReject,
  handleEdit,
  handleReject,
  handleSave: save,
  rejectDialogVisible,
  rejectForm,
  resetForm,
  submitLoading
} = useExpenseForm(t, {
  getExpense,
  createExpense,
  updateExpense,
  rejectExpense,
  onSubmitted: loadData,
  ...notify
})

const formRules = computed<FormRules>(() => ({
  subjectId: [{ required: true, message: t('financeReportPages.expenses.validation.expenseSubject'), trigger: 'change' }],
  paymentSubjectId: [{ required: true, message: t('financeReportPages.expenses.validation.paymentSubject'), trigger: 'change' }],
  expenseDate: [{ required: true, message: t('financeReportPages.expenses.validation.expenseDate'), trigger: 'change' }],
  amount: [{ required: true, message: t('financeReportPages.expenses.validation.amount'), trigger: 'blur' }]
}))

const handleSave = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    await save()
  })
}

const handleDialogClose = () => {
  formRef.value?.clearValidate()
  resetForm()
}

let previewSequence = 0
watch(() => [formData.expenseDate, formData.deptId, formData.subjectId, formData.amount], async () => {
  const sequence = ++previewSequence
  if (!formData.expenseDate || !formData.subjectId || !formData.amount) {
    budgetPreview.value = undefined
    return
  }
  const [year, month] = formData.expenseDate.split('-').map(Number)
  try {
    const result = await getBudgetExecution({ budgetYear: year, periodMonth: month, deptId: formData.deptId || undefined, subjectId: formData.subjectId, amount: formData.amount })
    if (sequence === previewSequence) budgetPreview.value = result
  } catch {
    if (sequence === previewSequence) budgetPreview.value = undefined
  }
}, { immediate: false })

onMounted(async () => {
  try { departmentOptions.value = flattenDepartments(await getDeptTree()) } catch { departmentOptions.value = [] }
  loadSubjects()
  loadData()
})
</script>

<style scoped>
.app-container {
  padding: 20px;
}

.search-card,
.table-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.reconciliation-alert {
  margin-bottom: 16px;
}

.entry-section {
  margin-top: 18px;
}

.section-title {
  margin-bottom: 8px;
  font-weight: 600;
  color: #303133;
}
</style>
