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
        v-model:current-page="pagination.pageNo"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px; justify-content: flex-end"
        @size-change="loadData"
        @current-change="loadData"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" @close="handleDialogClose">
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
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { CircleCheck, CircleClose, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { formatBusinessDate, formatLocalizedCurrency, formatLocalizedDate } from '@/utils/locale'
import { printFinanceInvoice } from '@/utils/bizPrint'
import {
  cancelFinanceInvoice,
  createFinanceInvoice,
  getFinanceInvoice,
  getFinanceInvoices,
  postFinanceInvoice,
  updateFinanceInvoice,
  type FinanceInvoice
} from '@/api/finance'

const { t } = useI18n()

const queryForm = reactive({
  status: '',
  invoiceType: '',
  partnerName: '',
  dateFrom: '',
  dateTo: ''
})
const dateRange = ref<string[]>([])
const loading = ref(false)
const tableData = ref<FinanceInvoice[]>([])
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const dialogTitle = computed(() => ({
  create: t('financeReportPages.invoices.dialog.create'),
  edit: t('financeReportPages.invoices.dialog.edit')
})[dialogMode.value])
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const pagination = reactive({
  pageNo: 1,
  pageSize: 20,
  total: 0
})

const formData = reactive({
  id: '' as string | number,
  invoiceType: 'INPUT',
  partnerName: '',
  invoiceDate: '',
  amount: 0,
  taxAmount: 0,
  relatedBizType: '',
  relatedBizId: '',
  remark: ''
})

const formRules = computed<FormRules>(() => ({
  invoiceType: [{ required: true, message: t('financeReportPages.invoices.validation.type'), trigger: 'change' }],
  partnerName: [{ required: true, message: t('financeReportPages.invoices.validation.partner'), trigger: 'blur' }],
  invoiceDate: [{ required: true, message: t('financeReportPages.invoices.validation.date'), trigger: 'change' }],
  amount: [{ required: true, message: t('financeReportPages.invoices.validation.amount'), trigger: 'blur' }],
  taxAmount: [{ required: true, message: t('financeReportPages.invoices.validation.taxAmount'), trigger: 'blur' }]
}))

const loadData = async () => {
  loading.value = true
  try {
    const res = await getFinanceInvoices({
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
      status: queryForm.status || undefined,
      invoiceType: queryForm.invoiceType || undefined,
      partnerName: queryForm.partnerName || undefined,
      dateFrom: queryForm.dateFrom || undefined,
      dateTo: queryForm.dateTo || undefined
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch {
    ElMessage.error(t('financeReportPages.invoices.message.loadFailed'))
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  if (dateRange.value?.length === 2) {
    queryForm.dateFrom = dateRange.value[0]
    queryForm.dateTo = dateRange.value[1]
  } else {
    queryForm.dateFrom = ''
    queryForm.dateTo = ''
  }
  pagination.pageNo = 1
  loadData()
}

const handleReset = () => {
  Object.assign(queryForm, { status: '', invoiceType: '', partnerName: '', dateFrom: '', dateTo: '' })
  dateRange.value = []
  pagination.pageNo = 1
  loadData()
}

const handleAdd = () => {
  resetForm()
  dialogMode.value = 'create'
  formData.invoiceDate = today()
  dialogVisible.value = true
}

const handleEdit = async (row: FinanceInvoice) => {
  try {
    const invoice = await getFinanceInvoice(row.id)
    Object.assign(formData, {
      id: invoice.id,
      invoiceType: invoice.invoiceType,
      partnerName: invoice.partnerName,
      invoiceDate: invoice.invoiceDate,
      amount: Number(invoice.amount || 0),
      taxAmount: Number(invoice.taxAmount || 0),
      relatedBizType: invoice.relatedBizType || '',
      relatedBizId: invoice.relatedBizId || '',
      remark: invoice.remark || ''
    })
    dialogMode.value = 'edit'
    dialogVisible.value = true
  } catch {
    ElMessage.error(t('financeReportPages.invoices.message.detailLoadFailed'))
  }
}

const handlePrint = async (row: FinanceInvoice) => {
  try {
    const detail = await getFinanceInvoice(row.id)
    printFinanceInvoice(detail)
  } catch {
    ElMessage.error(t('financeReportPages.invoices.message.printLoadFailed'))
  }
}

const handlePost = async (row: FinanceInvoice) => {
  try {
    await ElMessageBox.confirm(t('financeReportPages.invoices.message.postConfirm', { no: row.invoiceNo }), t('financeReportPages.common.prompt'), { type: 'warning' })
    await postFinanceInvoice(row.id)
    ElMessage.success(t('financeReportPages.invoices.message.posted'))
    loadData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(t('financeReportPages.invoices.message.postFailed'))
  }
}

const handleCancel = async (row: FinanceInvoice) => {
  try {
    await ElMessageBox.confirm(t('financeReportPages.invoices.message.cancelConfirm', { no: row.invoiceNo }), t('financeReportPages.common.prompt'), { type: 'warning' })
    await cancelFinanceInvoice(row.id)
    ElMessage.success(t('financeReportPages.invoices.message.cancelled'))
    loadData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(t('financeReportPages.invoices.message.cancelFailed'))
  }
}

const handleSave = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      const payload = {
        invoiceType: formData.invoiceType,
        partnerName: formData.partnerName,
        invoiceDate: formData.invoiceDate,
        amount: formData.amount,
        taxAmount: formData.taxAmount,
        relatedBizType: formData.relatedBizType || undefined,
        relatedBizId: formData.relatedBizId || undefined,
        remark: formData.remark || undefined
      }
      if (formData.id) {
        await updateFinanceInvoice(formData.id, payload)
      } else {
        await createFinanceInvoice(payload)
      }
      ElMessage.success(t('financeReportPages.invoices.message.saved'))
      dialogVisible.value = false
      loadData()
    } catch {
      ElMessage.error(t('financeReportPages.invoices.message.saveFailed'))
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.clearValidate()
  resetForm()
}

const resetForm = () => {
  Object.assign(formData, {
    id: '',
    invoiceType: 'INPUT',
    partnerName: '',
    invoiceDate: '',
    amount: 0,
    taxAmount: 0,
    relatedBizType: '',
    relatedBizId: '',
    remark: ''
  })
}

const formatAmount = (amount?: number) => formatLocalizedCurrency(Number(amount || 0))

const formatDate = (value?: string) => formatLocalizedDate(value)

const typeLabel = (type: string) => {
  const map: Record<string, string> = {
    INPUT: t('financeReportPages.invoices.typeValue.input'),
    OUTPUT: t('financeReportPages.invoices.typeValue.output')
  }
  return map[type] || type
}

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: t('financeReportPages.invoices.status.draft'),
    POSTED: t('financeReportPages.invoices.status.posted'),
    CANCELLED: t('financeReportPages.invoices.status.cancelled')
  }
  return map[status] || status
}

const statusType = (status: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' => {
  const map: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
    DRAFT: 'info',
    POSTED: 'success',
    CANCELLED: 'danger'
  }
  return map[status] || 'info'
}

const today = () => formatBusinessDate()

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
