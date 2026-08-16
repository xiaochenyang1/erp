<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item :label="$t('financeReportPages.invoices.type')">
          <el-select v-model="queryForm.invoiceType" :placeholder="$t('financeReportPages.common.selectPlaceholder')" clearable style="width: 140px">
            <el-option :label="$t('financeReportPages.invoices.typeValue.input')" value="INPUT" />
            <el-option :label="$t('financeReportPages.invoices.typeValue.output')" value="OUTPUT" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.common.status')">
          <el-select v-model="queryForm.status" :placeholder="$t('financeReportPages.common.selectPlaceholder')" clearable style="width: 140px">
            <el-option :label="$t('financeReportPages.invoices.status.draft')" value="DRAFT" />
            <el-option :label="$t('financeReportPages.invoices.status.posted')" value="POSTED" />
            <el-option :label="$t('financeReportPages.invoices.status.cancelled')" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.invoices.partner')">
          <el-input v-model="queryForm.partnerName" :placeholder="$t('financeReportPages.invoices.partnerPlaceholder')" clearable style="width: 180px" />
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
          <span>{{ $t('financeReportPages.invoices.title') }}</span>
          <el-button v-permission="'finance:invoice:manage'" type="primary" :icon="Plus" @click="handleAdd">
            {{ $t('financeReportPages.invoices.create') }}
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="invoiceNo" :label="$t('financeReportPages.invoices.registrationNo')" width="170" />
        <el-table-column prop="invoiceType" :label="$t('financeReportPages.invoices.type')" width="100" align="center">
          <template #default="{ row }">{{ typeLabel(row.invoiceType) }}</template>
        </el-table-column>
        <el-table-column prop="partnerName" :label="$t('financeReportPages.invoices.partner')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="amount" :label="$t('financeReportPages.common.amount')" width="140" align="right">
          <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="taxAmount" :label="$t('financeReportPages.invoices.taxAmount')" width="140" align="right">
          <template #default="{ row }">{{ formatAmount(row.taxAmount) }}</template>
        </el-table-column>
        <el-table-column prop="invoiceDate" :label="$t('financeReportPages.invoices.invoiceDate')" width="130">
          <template #default="{ row }">{{ formatDate(row.invoiceDate) }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('financeReportPages.common.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="$t('financeReportPages.common.remark')" min-width="160" show-overflow-tooltip />
        <el-table-column :label="$t('financeReportPages.common.actions')" width="320" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handlePrint(row)">{{ $t('financeReportPages.common.print') }}</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'finance:invoice:manage'"
              type="primary"
              link
              :icon="Edit"
              @click="handleEdit(row)"
            >
              {{ $t('financeReportPages.common.edit') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'finance:invoice:manage'"
              type="success"
              link
              :icon="CircleCheck"
              @click="handlePost(row)"
            >
              {{ $t('financeReportPages.common.confirm') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'POSTED'"
              v-permission="'finance:invoice:manage'"
              type="danger"
              link
              :icon="CircleClose"
              @click="handleCancel(row)"
            >
              {{ $t('financeReportPages.common.void') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        :current-page="pagination.pageNo"
        :page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px; justify-content: flex-end"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" @close="onDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-form-item :label="$t('financeReportPages.invoices.invoiceType')" prop="invoiceType">
          <el-select v-model="formData.invoiceType" :placeholder="$t('financeReportPages.common.selectPlaceholder')" style="width: 100%">
            <el-option :label="$t('financeReportPages.invoices.typeValue.input')" value="INPUT" />
            <el-option :label="$t('financeReportPages.invoices.typeValue.output')" value="OUTPUT" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.invoices.partner')" prop="partnerName">
          <el-input v-model="formData.partnerName" :placeholder="$t('financeReportPages.invoices.partnerNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.invoices.invoiceDate')" prop="invoiceDate">
          <el-date-picker
            v-model="formData.invoiceDate"
            type="date"
            :placeholder="$t('financeReportPages.invoices.selectDate')"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.common.amount')" prop="amount">
          <el-input-number v-model="formData.amount" :min="0.01" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.invoices.taxAmount')" prop="taxAmount">
          <el-input-number v-model="formData.taxAmount" :min="0" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.invoices.relatedBizType')">
          <el-input v-model="formData.relatedBizType" :placeholder="$t('financeReportPages.invoices.relatedBizTypePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.invoices.relatedBizId')">
          <el-input v-model="formData.relatedBizId" :placeholder="$t('financeReportPages.invoices.optionalPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.common.remark')">
          <el-input v-model="formData.remark" type="textarea" :rows="3" :placeholder="$t('financeReportPages.invoices.remarkPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('financeReportPages.common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSave">{{ $t('financeReportPages.common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { CircleCheck, CircleClose, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { printFinanceInvoice } from '@/utils/bizPrint'
import {
  cancelFinanceInvoice,
  createFinanceInvoice,
  getFinanceInvoice,
  getFinanceInvoices,
  postFinanceInvoice,
  updateFinanceInvoice
} from '@/api/finance'
import { useFinanceInvoiceForm } from '@/composables/useFinanceInvoiceForm'
import { useFinanceInvoiceList } from '@/composables/useFinanceInvoiceList'
import { useFinanceInvoicePresentation } from '@/composables/useFinanceInvoicePresentation'

const { t } = useI18n()
const formRef = ref<FormInstance>()

const {
  formatAmount,
  formatDate,
  statusLabel,
  statusType,
  typeLabel
} = useFinanceInvoicePresentation(t)

const {
  dateRange,
  handleCancel,
  handlePageChange,
  handlePost,
  handlePrint,
  handleQuery,
  handleReset,
  handleSizeChange,
  loadData,
  loading,
  pagination,
  queryForm,
  tableData
} = useFinanceInvoiceList(t, {
  getFinanceInvoices,
  getFinanceInvoice,
  postFinanceInvoice,
  cancelFinanceInvoice,
  printFinanceInvoice,
  confirm: (message, title, options) => ElMessageBox.confirm(message, title, options as any),
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message)
})

const {
  dialogTitle,
  dialogVisible,
  formData,
  handleAdd,
  handleDialogClose,
  handleEdit,
  submitLoading,
  submitSave
} = useFinanceInvoiceForm(t, {
  getFinanceInvoice,
  createFinanceInvoice,
  updateFinanceInvoice,
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message),
  onSubmitted: async () => { await loadData() }
})

const formRules = computed<FormRules>(() => ({
  invoiceType: [{ required: true, message: t('financeReportPages.invoices.validation.type'), trigger: 'change' }],
  partnerName: [{ required: true, message: t('financeReportPages.invoices.validation.partner'), trigger: 'blur' }],
  invoiceDate: [{ required: true, message: t('financeReportPages.invoices.validation.date'), trigger: 'change' }],
  amount: [{ required: true, message: t('financeReportPages.invoices.validation.amount'), trigger: 'blur' }],
  taxAmount: [{ required: true, message: t('financeReportPages.invoices.validation.taxAmount'), trigger: 'blur' }]
}))

const handleSave = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    await submitSave()
  })
}

const onDialogClose = () => {
  formRef.value?.clearValidate()
  handleDialogClose()
}

onMounted(() => {
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
</style>
