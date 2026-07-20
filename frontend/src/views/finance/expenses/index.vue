<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="请选择" clearable style="width: 140px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="待审批" value="PENDING" />
            <el-option label="已批准" value="APPROVED" />
            <el-option label="已过账" value="POSTED" />
            <el-option label="已驳回" value="REJECTED" />
            <el-option label="已作废" value="CANCELLED" />
          </el-select>
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
          <span>费用管理</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">新增费用</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="expenseNo" label="费用单号" width="170" />
        <el-table-column prop="expenseDate" label="费用日期" width="120" />
        <el-table-column prop="subjectId" label="费用科目" min-width="180">
          <template #default="{ row }">{{ subjectName(row.subjectId) }}</template>
        </el-table-column>
        <el-table-column prop="paymentSubjectId" label="支付科目" min-width="180">
          <template #default="{ row }">{{ subjectName(row.paymentSubjectId) }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="费用金额" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="voucherNo" label="凭证" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.voucherNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="凭证校验" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.voucherNo" :type="row.voucherBalanced && row.amountMatched ? 'success' : 'danger'" size="small">
              {{ row.voucherBalanced && row.amountMatched ? '平衡' : '异常' }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="380" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'finance:expense:manage'" type="primary" link :icon="View" @click="handleView(row)">查看</el-button>
            <el-button
              v-permission="'finance:expense:manage'"
              type="primary"
              link
              :icon="DataAnalysis"
              @click="handleReconciliation(row)"
            >
              对账
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'REJECTED'"
              v-permission="'finance:expense:manage'"
              type="primary"
              link
              :icon="Edit"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'REJECTED'"
              v-permission="'finance:expense:manage'"
              type="success"
              link
              :icon="Promotion"
              @click="handleSubmit(row)"
            >
              提交
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'REJECTED'"
              v-permission="'finance:expense:manage'"
              type="danger"
              link
              :icon="CircleClose"
              @click="handleCancel(row)"
            >
              作废
            </el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              v-permission="'finance:expense:manage'"
              type="success"
              link
              :icon="CircleCheck"
              @click="handleApprove(row)"
            >
              审批
            </el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              v-permission="'finance:expense:manage'"
              type="danger"
              link
              :icon="CircleClose"
              @click="handleReject(row)"
            >
              驳回
            </el-button>
            <el-button
              v-if="row.status === 'APPROVED'"
              v-permission="'finance:expense:manage'"
              type="warning"
              link
              :icon="Money"
              @click="handlePost(row)"
            >
              过账
            </el-button>
            <el-button
              v-if="row.status === 'POSTED' && !row.reversed"
              v-permission="'finance:expense:manage'"
              type="danger"
              link
              :icon="RefreshLeft"
              @click="handleReverse(row)"
            >
              红冲
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
        @size-change="loadData"
        @current-change="loadData"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-form-item label="费用科目" prop="subjectId">
          <el-select v-model="formData.subjectId" placeholder="请选择费用科目" filterable style="width: 100%">
            <el-option
              v-for="subject in expenseSubjects"
              :key="subject.id"
              :label="subjectLabel(subject)"
              :value="String(subject.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="支付科目" prop="paymentSubjectId">
          <el-select v-model="formData.paymentSubjectId" placeholder="请选择支付科目" filterable style="width: 100%">
            <el-option
              v-for="subject in paymentSubjects"
              :key="subject.id"
              :label="subjectLabel(subject)"
              :value="String(subject.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="费用日期" prop="expenseDate">
          <el-date-picker
            v-model="formData.expenseDate"
            type="date"
            placeholder="请选择日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="费用金额" prop="amount">
          <el-input-number v-model="formData.amount" :min="0.01" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="viewDialogVisible" title="费用详情" width="760px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="费用单号">{{ viewData.expenseNo }}</el-descriptions-item>
        <el-descriptions-item label="费用日期">{{ viewData.expenseDate }}</el-descriptions-item>
        <el-descriptions-item label="费用科目">{{ subjectName(viewData.subjectId) }}</el-descriptions-item>
        <el-descriptions-item label="支付科目">{{ subjectName(viewData.paymentSubjectId) }}</el-descriptions-item>
        <el-descriptions-item label="费用金额">{{ formatAmount(viewData.amount) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(viewData.status)">
            {{ getStatusLabel(viewData.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="凭证号">{{ viewData.voucherNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="凭证状态">{{ viewData.voucherStatus || '-' }}</el-descriptions-item>
        <el-descriptions-item label="凭证分录">{{ viewData.voucherEntryCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="凭证金额">{{ viewData.voucherAmount == null ? '-' : formatAmount(viewData.voucherAmount) }}</el-descriptions-item>
        <el-descriptions-item label="红冲凭证">{{ viewData.reversalVoucherNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="红冲状态">{{ viewData.reversed ? '已红冲' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ viewData.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="viewDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reconciliationDialogVisible" title="费用对账" width="980px" destroy-on-close>
      <div v-loading="reconciliationLoading">
        <template v-if="reconciliationData">
          <el-alert
            class="reconciliation-alert"
            :title="reconciliationPassed ? '费用、凭证与分录核对一致' : '费用对账存在异常，请核查凭证和分录'"
            :type="reconciliationPassed ? 'success' : 'warning'"
            show-icon
            :closable="false"
          />
          <el-descriptions :column="3" border>
            <el-descriptions-item label="费用单号">{{ reconciliationData.expense.expenseNo }}</el-descriptions-item>
            <el-descriptions-item label="费用金额">{{ formatAmount(reconciliationData.expense.amount) }}</el-descriptions-item>
            <el-descriptions-item label="原凭证">{{ reconciliationData.voucher?.voucherNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="借方合计">{{ formatAmount(reconciliationData.debitTotal) }}</el-descriptions-item>
            <el-descriptions-item label="贷方合计">{{ formatAmount(reconciliationData.creditTotal) }}</el-descriptions-item>
            <el-descriptions-item label="红冲凭证">{{ reconciliationData.reversalVoucher?.voucherNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="凭证存在">
              <el-tag :type="reconciliationData.voucherMissing ? 'danger' : 'success'">
                {{ reconciliationData.voucherMissing ? '缺失' : '正常' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="分录平衡">
              <el-tag :type="checkTagType(reconciliationData.voucherBalanced)">
                {{ checkLabel(reconciliationData.voucherBalanced) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="金额匹配">
              <el-tag :type="checkTagType(reconciliationData.amountMatched)">
                {{ checkLabel(reconciliationData.amountMatched) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="凭证关联">
              <el-tag :type="checkTagType(reconciliationData.voucherLinkedToExpense)">
                {{ checkLabel(reconciliationData.voucherLinkedToExpense) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="红冲平衡">
              <el-tag :type="checkTagType(!reconciliationData.reversed || reconciliationData.reversalVoucherBalanced)">
                {{ reconciliationData.reversed ? checkLabel(reconciliationData.reversalVoucherBalanced) : '未红冲' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="红冲金额">
              <el-tag :type="checkTagType(!reconciliationData.reversed || reconciliationData.reversalAmountMatched)">
                {{ reconciliationData.reversed ? checkLabel(reconciliationData.reversalAmountMatched) : '未红冲' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>

          <section class="entry-section" aria-label="原凭证分录">
            <div class="section-title">原凭证分录</div>
            <el-table :data="reconciliationData.entries" border stripe>
              <el-table-column prop="lineNo" label="行号" width="80" />
              <el-table-column prop="subjectCode" label="科目编码" width="140" />
              <el-table-column prop="subjectName" label="科目名称" min-width="180" show-overflow-tooltip />
              <el-table-column prop="debitAmount" label="借方金额" width="130" align="right">
                <template #default="{ row }">{{ formatAmount(row.debitAmount) }}</template>
              </el-table-column>
              <el-table-column prop="creditAmount" label="贷方金额" width="130" align="right">
                <template #default="{ row }">{{ formatAmount(row.creditAmount) }}</template>
              </el-table-column>
              <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
            </el-table>
          </section>

          <section class="entry-section" aria-label="红冲分录">
            <div class="section-title">红冲分录</div>
            <el-table :data="reconciliationData.reversalEntries" border stripe>
              <el-table-column prop="lineNo" label="行号" width="80" />
              <el-table-column prop="subjectCode" label="科目编码" width="140" />
              <el-table-column prop="subjectName" label="科目名称" min-width="180" show-overflow-tooltip />
              <el-table-column prop="debitAmount" label="借方金额" width="130" align="right">
                <template #default="{ row }">{{ formatAmount(row.debitAmount) }}</template>
              </el-table-column>
              <el-table-column prop="creditAmount" label="贷方金额" width="130" align="right">
                <template #default="{ row }">{{ formatAmount(row.creditAmount) }}</template>
              </el-table-column>
              <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
            </el-table>
          </section>
        </template>
      </div>
      <template #footer>
        <el-button @click="reconciliationDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rejectDialogVisible" title="驳回费用" width="500px">
      <el-form :model="rejectForm" label-width="80px">
        <el-form-item label="驳回原因" required>
          <el-input v-model="rejectForm.reason" type="textarea" :rows="4" placeholder="请输入驳回原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitLoading" @click="handleConfirmReject">确定驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
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
  postExpense,
  rejectExpense,
  reverseExpense,
  submitExpense,
  updateExpense,
  type AccountSubject,
  type Expense,
  type ExpenseReconciliation
} from '@/api/finance'

let queryForm = reactive({
  status: '',
  dateFrom: '',
  dateTo: ''
})
const dateRange = ref<string[]>([])
const loading = ref(false)
const tableData = ref<Expense[]>([])
const subjectOptions = ref<AccountSubject[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const viewDialogVisible = ref(false)
const viewData = ref<Expense>({} as Expense)
const rejectDialogVisible = ref(false)
const reconciliationDialogVisible = ref(false)
const reconciliationLoading = ref(false)
const reconciliationData = ref<ExpenseReconciliation>()

const pagination = reactive({
  pageNo: 1,
  pageSize: 20,
  total: 0
})

const formData = reactive({
  id: '' as string | number,
  subjectId: '',
  paymentSubjectId: '',
  expenseDate: '',
  amount: 0,
  remark: ''
})

const rejectForm = reactive({
  id: '' as string | number,
  reason: ''
})

const formRules: FormRules = {
  subjectId: [{ required: true, message: '请选择费用科目', trigger: 'change' }],
  paymentSubjectId: [{ required: true, message: '请选择支付科目', trigger: 'change' }],
  expenseDate: [{ required: true, message: '请选择费用日期', trigger: 'change' }],
  amount: [{ required: true, message: '请输入费用金额', trigger: 'blur' }]
}

const subjectMap = computed(() => new Map(subjectOptions.value.map((subject) => [String(subject.id), subject])))
const expenseSubjects = computed(() => subjectOptions.value.filter((subject) => subject.status === 'ACTIVE' && subject.category === 'EXPENSE'))
const paymentSubjects = computed(() =>
  subjectOptions.value.filter((subject) => subject.status === 'ACTIVE' && ['ASSET', 'LIABILITY'].includes(String(subject.category)))
)
const reconciliationPassed = computed(() => {
  const data = reconciliationData.value
  if (!data) return false
  const reversalPassed = !data.reversed || (data.reversalVoucherBalanced && data.reversalAmountMatched)
  return !data.voucherMissing && !data.entriesMissing && data.voucherBalanced && data.amountMatched && data.voucherLinkedToExpense && reversalPassed
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await getExpenses({
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
      status: queryForm.status || undefined,
      dateFrom: queryForm.dateFrom || undefined,
      dateTo: queryForm.dateTo || undefined
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const loadSubjects = async () => {
  try {
    const subjects = await getAccountSubjectTree()
    subjectOptions.value = flattenSubjects(subjects || [])
  } catch (error) {
    ElMessage.error('加载会计科目失败')
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
  Object.assign(queryForm, { status: '', dateFrom: '', dateTo: '' })
  dateRange.value = []
  pagination.pageNo = 1
  loadData()
}

const handleAdd = () => {
  resetForm()
  dialogTitle.value = '新增费用'
  formData.expenseDate = today()
  dialogVisible.value = true
}

const handleEdit = async (row: Expense) => {
  try {
    const expense = await getExpense(row.id)
    Object.assign(formData, {
      id: expense.id,
      subjectId: String(expense.subjectId),
      paymentSubjectId: String(expense.paymentSubjectId),
      expenseDate: expense.expenseDate,
      amount: Number(expense.amount || 0),
      remark: expense.remark || ''
    })
    dialogTitle.value = '编辑费用'
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载费用详情失败')
  }
}

const handleView = async (row: Expense) => {
  try {
    viewData.value = await getExpense(row.id)
    viewDialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载费用详情失败')
  }
}

const handleReconciliation = async (row: Expense) => {
  reconciliationDialogVisible.value = true
  reconciliationLoading.value = true
  reconciliationData.value = undefined
  try {
    reconciliationData.value = await getExpenseReconciliation(row.id)
  } catch (error) {
    ElMessage.error('加载费用对账失败')
  } finally {
    reconciliationLoading.value = false
  }
}

const handleSubmit = async (row: Expense) => {
  try {
    await ElMessageBox.confirm(`确定要提交费用单"${row.expenseNo}"吗？`, '提示', { type: 'warning' })
    await submitExpense(row.id)
    ElMessage.success('提交成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('提交失败')
  }
}

const handleApprove = async (row: Expense) => {
  try {
    await ElMessageBox.confirm(`确定要批准费用单"${row.expenseNo}"吗？`, '提示', { type: 'warning' })
    await approveExpense(row.id)
    ElMessage.success('审批通过')
    loadData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('审批失败')
  }
}

const handlePost = async (row: Expense) => {
  try {
    await ElMessageBox.confirm(`确定要将费用单"${row.expenseNo}"过账吗？`, '提示', { type: 'warning' })
    await postExpense(row.id)
    ElMessage.success('过账成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('过账失败')
  }
}

const handleReverse = async (row: Expense) => {
  try {
    await ElMessageBox.confirm(`确定要红冲费用单"${row.expenseNo}"吗？`, '提示', { type: 'warning' })
    await reverseExpense(row.id)
    ElMessage.success('红冲成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('红冲失败')
  }
}

const handleCancel = async (row: Expense) => {
  try {
    await ElMessageBox.confirm(`确定要作废费用单"${row.expenseNo}"吗？`, '提示', { type: 'warning' })
    await cancelExpense(row.id)
    ElMessage.success('作废成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('作废失败')
  }
}

const handleReject = (row: Expense) => {
  rejectForm.id = row.id
  rejectForm.reason = ''
  rejectDialogVisible.value = true
}

const handleConfirmReject = async () => {
  if (!rejectForm.reason.trim()) {
    ElMessage.warning('请输入驳回原因')
    return
  }
  submitLoading.value = true
  try {
    await rejectExpense(rejectForm.id, rejectForm.reason)
    ElMessage.success('驳回成功')
    rejectDialogVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error('驳回失败')
  } finally {
    submitLoading.value = false
  }
}

const handleSave = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      const payload = {
        expenseDate: formData.expenseDate,
        subjectId: formData.subjectId,
        paymentSubjectId: formData.paymentSubjectId,
        amount: formData.amount,
        remark: formData.remark
      }
      if (formData.id) {
        await updateExpense(formData.id, payload)
      } else {
        await createExpense(payload)
      }
      ElMessage.success('保存成功')
      dialogVisible.value = false
      loadData()
    } catch (error) {
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
    subjectId: '',
    paymentSubjectId: '',
    expenseDate: '',
    amount: 0,
    remark: ''
  })
}

const flattenSubjects = (subjects: AccountSubject[]): AccountSubject[] =>
  subjects.flatMap((subject) => [subject, ...flattenSubjects(subject.children || [])])

const subjectLabel = (subject: AccountSubject) => `${subject.code || subject.subjectCode} - ${subject.name || subject.subjectName}`

const subjectName = (id?: string | number) => {
  if (id == null) return '-'
  const subject = subjectMap.value.get(String(id))
  return subject ? subjectLabel(subject) : `科目 ${id}`
}

const formatAmount = (amount?: number) =>
  Number(amount || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

const checkLabel = (passed: boolean) => (passed ? '正常' : '异常')

const checkTagType = (passed: boolean): 'success' | 'danger' => (passed ? 'success' : 'danger')

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    PENDING: '待审批',
    APPROVED: '已批准',
    REJECTED: '已驳回',
    POSTED: '已过账',
    CANCELLED: '已作废'
  }
  return map[status] || status
}

const getStatusType = (status: string) => {
  const map: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
    DRAFT: 'info',
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
    POSTED: 'primary',
    CANCELLED: 'info'
  }
  return map[status] || 'info'
}

const today = () => new Date().toISOString().slice(0, 10)

onMounted(() => {
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
