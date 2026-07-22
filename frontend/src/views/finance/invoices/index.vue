<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item label="类型">
          <el-select v-model="queryForm.invoiceType" placeholder="请选择" clearable style="width: 140px">
            <el-option label="进项" value="INPUT" />
            <el-option label="销项" value="OUTPUT" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="请选择" clearable style="width: 140px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已确认" value="POSTED" />
            <el-option label="已作废" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="往来单位">
          <el-input v-model="queryForm.partnerName" placeholder="供应商/客户" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 280px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>发票登记</span>
          <el-button v-permission="'finance:invoice:manage'" type="primary" :icon="Plus" @click="handleAdd">
            新增登记
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="invoiceNo" label="登记号" width="170" />
        <el-table-column prop="invoiceType" label="类型" width="100" align="center">
          <template #default="{ row }">{{ typeLabel(row.invoiceType) }}</template>
        </el-table-column>
        <el-table-column prop="partnerName" label="往来单位" min-width="160" show-overflow-tooltip />
        <el-table-column prop="amount" label="金额" width="120" align="right">
          <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="taxAmount" label="税额" width="120" align="right">
          <template #default="{ row }">{{ formatAmount(row.taxAmount) }}</template>
        </el-table-column>
        <el-table-column prop="invoiceDate" label="发票日期" width="120" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="260" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'finance:invoice:manage'"
              type="primary"
              link
              :icon="Edit"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'finance:invoice:manage'"
              type="success"
              link
              :icon="CircleCheck"
              @click="handlePost(row)"
            >
              确认
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'POSTED'"
              v-permission="'finance:invoice:manage'"
              type="danger"
              link
              :icon="CircleClose"
              @click="handleCancel(row)"
            >
              作废
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
        <el-form-item label="发票类型" prop="invoiceType">
          <el-select v-model="formData.invoiceType" placeholder="请选择" style="width: 100%">
            <el-option label="进项" value="INPUT" />
            <el-option label="销项" value="OUTPUT" />
          </el-select>
        </el-form-item>
        <el-form-item label="往来单位" prop="partnerName">
          <el-input v-model="formData.partnerName" placeholder="供应商/客户名称" />
        </el-form-item>
        <el-form-item label="发票日期" prop="invoiceDate">
          <el-date-picker
            v-model="formData.invoiceDate"
            type="date"
            placeholder="请选择日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="formData.amount" :min="0.01" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="税额" prop="taxAmount">
          <el-input-number v-model="formData.taxAmount" :min="0" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="关联业务类型">
          <el-input v-model="formData.relatedBizType" placeholder="可选，如 PURCHASE_ORDER" />
        </el-form-item>
        <el-form-item label="关联业务ID">
          <el-input v-model="formData.relatedBizId" placeholder="可选" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { CircleCheck, CircleClose, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { formatLocalizedNumber } from '@/utils/locale'
import {
  cancelFinanceInvoice,
  createFinanceInvoice,
  getFinanceInvoice,
  getFinanceInvoices,
  postFinanceInvoice,
  updateFinanceInvoice,
  type FinanceInvoice
} from '@/api/finance'

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
const dialogTitle = ref('新增登记')
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

const formRules: FormRules = {
  invoiceType: [{ required: true, message: '请选择发票类型', trigger: 'change' }],
  partnerName: [{ required: true, message: '请输入往来单位', trigger: 'blur' }],
  invoiceDate: [{ required: true, message: '请选择发票日期', trigger: 'change' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  taxAmount: [{ required: true, message: '请输入税额', trigger: 'blur' }]
}

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
    ElMessage.error('加载发票登记失败')
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
  dialogTitle.value = '新增登记'
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
    dialogTitle.value = '编辑登记'
    dialogVisible.value = true
  } catch {
    ElMessage.error('加载发票详情失败')
  }
}

const handlePost = async (row: FinanceInvoice) => {
  try {
    await ElMessageBox.confirm(`确认发票登记「${row.invoiceNo}」？`, '提示', { type: 'warning' })
    await postFinanceInvoice(row.id)
    ElMessage.success('确认成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('确认失败')
  }
}

const handleCancel = async (row: FinanceInvoice) => {
  try {
    await ElMessageBox.confirm(`作废发票登记「${row.invoiceNo}」？`, '提示', { type: 'warning' })
    await cancelFinanceInvoice(row.id)
    ElMessage.success('作废成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('作废失败')
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
      ElMessage.success('保存成功')
      dialogVisible.value = false
      loadData()
    } catch {
      ElMessage.error('保存失败')
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

const formatAmount = (amount?: number) =>
  formatLocalizedNumber(Number(amount || 0), { minimumFractionDigits: 2, maximumFractionDigits: 2 })

const typeLabel = (type: string) => (type === 'OUTPUT' ? '销项' : type === 'INPUT' ? '进项' : type)

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    POSTED: '已确认',
    CANCELLED: '已作废'
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

const today = () => new Date().toISOString().slice(0, 10)

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
