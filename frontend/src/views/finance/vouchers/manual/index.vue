<template>
  <div class="manual-voucher-page">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item label="凭证号">
          <el-input v-model="queryForm.voucherNo" placeholder="请输入凭证号" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期从">
          <el-date-picker v-model="queryForm.dateFrom" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" style="width: 160px" />
        </el-form-item>
        <el-form-item label="日期至">
          <el-date-picker v-model="queryForm.dateTo" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" style="width: 160px" />
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
          <span>手工凭证</span>
          <el-button
            v-permission="'finance:voucher:manage'"
            type="primary"
            :icon="Plus"
            @click="openCreate"
          >
            录入凭证
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="voucherNo" label="凭证号" width="200" />
        <el-table-column prop="bizDate" label="凭证日期" width="120" />
        <el-table-column prop="amount" label="金额" width="150" align="right">
          <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="摘要" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'finance:voucher:manage'"
              link
              type="primary"
              @click="openEdit(row)"
            >编辑</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'finance:voucher:manage'"
              link
              type="primary"
              @click="handleSubmit(row)"
            >提交</el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              v-permission="'finance:voucher:approve'"
              link
              type="success"
              @click="handleApprove(row)"
            >审批</el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              v-permission="'finance:voucher:approve'"
              link
              type="warning"
              @click="openReject(row)"
            >驳回</el-button>
            <el-button
              v-if="row.status === 'APPROVED'"
              v-permission="'finance:voucher:post'"
              link
              type="success"
              @click="handlePost(row)"
            >过账</el-button>
            <el-button
              v-if="row.status === 'POSTED'"
              v-permission="'finance:voucher:post'"
              link
              type="danger"
              @click="openCancel(row)"
            >作废</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'finance:voucher:manage'"
              link
              type="danger"
              @click="handleDelete(row)"
            >删除</el-button>
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
        @current-change="handleQuery"
      />
    </el-card>

    <!-- 录入/编辑弹窗 -->
    <el-dialog
      v-model="editVisible"
      :title="editMode === 'create' ? '录入手工凭证' : '编辑手工凭证'"
      width="900px"
      @close="resetEditForm"
    >
      <el-form ref="editFormRef" :model="editForm" label-width="90px">
        <el-form-item label="凭证日期" prop="bizDate" required>
          <el-date-picker v-model="editForm.bizDate" type="date" value-format="YYYY-MM-DD" placeholder="选择凭证日期" style="width: 220px" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="editForm.remark" placeholder="整张凭证的摘要" style="width: 100%" />
        </el-form-item>
      </el-form>

      <el-table :data="editForm.lines" border size="small" class="entry-table">
        <el-table-column type="index" label="行" width="50" />
        <el-table-column label="会计科目" min-width="220">
          <template #default="{ row }">
            <el-select
              v-model="row.subjectId"
              filterable
              placeholder="选择科目"
              style="width: 100%"
            >
              <el-option
                v-for="s in subjects"
                :key="s.id"
                :label="`${s.subjectCode} ${s.subjectName}`"
                :value="s.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="借方金额" width="150">
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
        <el-table-column label="贷方金额" width="150">
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
        <el-table-column label="行摘要" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.summary" placeholder="行摘要" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }">
            <el-button
              link
              type="danger"
              :disabled="editForm.lines.length <= 2"
              @click="removeLine($index)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="entry-toolbar">
        <el-button link type="primary" :icon="Plus" @click="addLine">增加分录行</el-button>
        <div class="balance-summary">
          <span>借方合计：<strong>{{ formatAmount(debitTotal) }}</strong></span>
          <span>贷方合计：<strong>{{ formatAmount(creditTotal) }}</strong></span>
          <el-tag :type="balanced ? 'success' : 'danger'" size="small">
            {{ balanced ? '借贷平衡' : '借贷不平' }}
          </el-tag>
        </div>
      </div>

      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!canSave" @click="handleSave">保存草稿</el-button>
      </template>
    </el-dialog>

    <!-- 驳回原因弹窗 -->
    <el-dialog v-model="rejectVisible" title="驳回凭证" width="480px">
      <el-form label-width="80px">
        <el-form-item label="驳回原因" required>
          <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请填写驳回原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="warning" :loading="rejecting" :disabled="!rejectReason.trim()" @click="handleReject">确定驳回</el-button>
      </template>
    </el-dialog>

    <!-- 作废原因弹窗 -->
    <el-dialog
      v-model="cancelVisible"
      title="作废凭证"
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
          作废已过账凭证 {{ cancellingRow.voucherNo }} 后将生成红冲凭证，请填写作废原因。
        </template>
      </el-alert>
      <el-form label-width="90px" class="cancel-form">
        <el-form-item label="作废原因" required>
          <el-input
            v-model="cancelReason"
            type="textarea"
            :rows="4"
            maxlength="512"
            show-word-limit
            placeholder="请填写作废原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="cancelling" @click="cancelVisible = false">取消</el-button>
        <el-button
          type="danger"
          :loading="cancelling"
          :disabled="cancelling || !cancelReason.trim()"
          @click="handleCancel"
        >
          确定作废
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="凭证详情" width="820px">
      <div v-if="currentVoucher">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="凭证号">{{ currentVoucher.voucherNo }}</el-descriptions-item>
          <el-descriptions-item label="凭证日期">{{ currentVoucher.bizDate }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(currentVoucher.status)">{{ statusLabel(currentVoucher.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="金额">{{ formatAmount(currentVoucher.amount) }}</el-descriptions-item>
          <el-descriptions-item label="摘要" :span="2">{{ currentVoucher.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="原过账凭证ID">{{ currentVoucher.postedVoucherId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="红冲凭证ID">{{ currentVoucher.reversalVoucherId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ currentVoucher.submittedTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审批时间">{{ currentVoucher.approvedTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="过账时间">{{ currentVoucher.postedTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="作废时间">{{ currentVoucher.cancelledTime || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="currentVoucher.cancelReason" label="作废原因" :span="2">
            {{ currentVoucher.cancelReason }}
          </el-descriptions-item>
          <el-descriptions-item v-if="currentVoucher.rejectReason" label="驳回原因" :span="2">
            {{ currentVoucher.rejectReason }}
          </el-descriptions-item>
        </el-descriptions>

        <el-table :data="currentVoucher.lines" border style="margin-top: 16px">
          <el-table-column type="index" label="行" width="50" />
          <el-table-column prop="subjectCode" label="科目编码" width="110" />
          <el-table-column prop="subjectName" label="科目名称" min-width="150" />
          <el-table-column prop="debitAmount" label="借方金额" width="140" align="right">
            <template #default="{ row }">{{ formatAmount(row.debitAmount) }}</template>
          </el-table-column>
          <el-table-column prop="creditAmount" label="贷方金额" width="140" align="right">
            <template #default="{ row }">{{ formatAmount(row.creditAmount) }}</template>
          </el-table-column>
          <el-table-column prop="summary" label="行摘要" min-width="140" show-overflow-tooltip />
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
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
  updateManualVoucher,
  type AccountSubject,
  type ManualVoucher,
  type ManualVoucherQuery,
  type ManualVoucherStatus
} from '@/api/finance'

const statusOptions: Array<{ label: string; value: ManualVoucherStatus }> = [
  { label: '草稿', value: 'DRAFT' },
  { label: '待审批', value: 'PENDING' },
  { label: '已审批', value: 'APPROVED' },
  { label: '已过账', value: 'POSTED' },
  { label: '已作废', value: 'CANCELLED' }
]

const queryForm = reactive<ManualVoucherQuery>({
  pageNo: 1,
  pageSize: 20,
  voucherNo: '',
  status: '',
  dateFrom: '',
  dateTo: ''
})

const loading = ref(false)
const tableData = ref<ManualVoucher[]>([])
const total = ref(0)
const subjects = ref<AccountSubject[]>([])

const loadData = async () => {
  loading.value = true
  try {
    const res = await getManualVouchers(queryForm)
    tableData.value = res.records
    total.value = res.total
  } catch {
    ElMessage.error('加载手工凭证失败')
  } finally {
    loading.value = false
  }
}

const loadSubjects = async () => {
  try {
    const res = await getAccountSubjects({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    subjects.value = res.records
  } catch {
    ElMessage.warning('加载会计科目失败，无法选择分录科目')
  }
}

const handleQuery = () => {
  loadData()
}

const handleReset = () => {
  queryForm.voucherNo = ''
  queryForm.status = ''
  queryForm.dateFrom = ''
  queryForm.dateTo = ''
  queryForm.pageNo = 1
  loadData()
}

// ---- 录入/编辑 ----
const editVisible = ref(false)
const editMode = ref<'create' | 'edit'>('create')
const editingId = ref<string>('')
const editFormRef = ref<FormInstance>()
const saving = ref(false)

interface EditLine {
  subjectId: string
  debitAmount: number
  creditAmount: number
  summary: string
}

const editForm = reactive<{ bizDate: string; remark: string; lines: EditLine[] }>({
  bizDate: '',
  remark: '',
  lines: []
})

const emptyLine = (): EditLine => ({ subjectId: '', debitAmount: 0, creditAmount: 0, summary: '' })

const addLine = () => editForm.lines.push(emptyLine())
const removeLine = (index: number) => {
  if (editForm.lines.length > 2) editForm.lines.splice(index, 1)
}

// 借贷互斥：填了借方就清零贷方，反之亦然
const onDebitChange = (row: EditLine) => {
  if (row.debitAmount) row.creditAmount = 0
}
const onCreditChange = (row: EditLine) => {
  if (row.creditAmount) row.debitAmount = 0
}

const debitTotal = computed(() =>
  editForm.lines.reduce((sum, l) => sum + Number(l.debitAmount || 0), 0)
)
const creditTotal = computed(() =>
  editForm.lines.reduce((sum, l) => sum + Number(l.creditAmount || 0), 0)
)
const balanced = computed(() => {
  const d = Math.round(debitTotal.value * 100)
  const c = Math.round(creditTotal.value * 100)
  return d === c && d > 0
})

const canSave = computed(() => {
  if (!editForm.bizDate) return false
  if (editForm.lines.length < 2) return false
  // 每行必须选科目且借贷二选一（且不能两者都为 0 或都非 0）
  for (const l of editForm.lines) {
    if (!l.subjectId) return false
    const hasDebit = Number(l.debitAmount || 0) > 0
    const hasCredit = Number(l.creditAmount || 0) > 0
    if (hasDebit === hasCredit) return false
  }
  return balanced.value
})

const resetEditForm = () => {
  editForm.bizDate = ''
  editForm.remark = ''
  editForm.lines = [emptyLine(), emptyLine()]
  editingId.value = ''
}

const openCreate = async () => {
  editMode.value = 'create'
  resetEditForm()
  if (subjects.value.length === 0) await loadSubjects()
  editVisible.value = true
}

const openEdit = async (row: ManualVoucher) => {
  editMode.value = 'edit'
  editingId.value = row.id
  if (subjects.value.length === 0) await loadSubjects()
  // 拉取完整详情（列表可能不含分录）
  const detail = await getManualVoucher(row.id)
  editForm.bizDate = detail.bizDate
  editForm.remark = detail.remark || ''
  editForm.lines = detail.lines.map((l) => ({
    subjectId: l.subjectId,
    debitAmount: l.debitAmount,
    creditAmount: l.creditAmount,
    summary: l.summary || ''
  }))
  if (editForm.lines.length < 2) {
    while (editForm.lines.length < 2) editForm.lines.push(emptyLine())
  }
  editVisible.value = true
}

const handleSave = async () => {
  if (!canSave.value) {
    ElMessage.warning('请检查分录：每行需选科目、借贷二选一，且借贷合计相等')
    return
  }
  saving.value = true
  try {
    const payload = {
      bizDate: editForm.bizDate,
      remark: editForm.remark,
      lines: editForm.lines.map((l) => ({
        subjectId: l.subjectId,
        debitAmount: Number(l.debitAmount || 0),
        creditAmount: Number(l.creditAmount || 0),
        summary: l.summary
      }))
    }
    if (editMode.value === 'create') {
      await createManualVoucher(payload)
      ElMessage.success('凭证草稿已创建')
    } else {
      await updateManualVoucher(editingId.value, payload)
      ElMessage.success('凭证草稿已更新')
    }
    editVisible.value = false
    loadData()
  } catch {
    ElMessage.error('保存凭证失败')
  } finally {
    saving.value = false
  }
}

// ---- 状态机操作 ----
const handleSubmit = async (row: ManualVoucher) => {
  try {
    await ElMessageBox.confirm(`确定提交凭证 ${row.voucherNo} 进入审批吗？`, '提交审批', { type: 'warning' })
    await submitManualVoucher(row.id)
    ElMessage.success('已提交审批')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('提交失败')
  }
}

const handleApprove = async (row: ManualVoucher) => {
  try {
    await ElMessageBox.confirm(`确定审批通过凭证 ${row.voucherNo} 吗？`, '审批凭证', { type: 'warning' })
    await approveManualVoucher(row.id)
    ElMessage.success('审批通过')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('审批失败')
  }
}

const handlePost = async (row: ManualVoucher) => {
  try {
    await ElMessageBox.confirm(
      `确定过账凭证 ${row.voucherNo} 吗？过账后分录将计入总账，且需作废才能撤销。`,
      '过账凭证',
      { type: 'warning' }
    )
    await postManualVoucher(row.id)
    ElMessage.success('已过账')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('过账失败')
  }
}

const handleDelete = async (row: ManualVoucher) => {
  try {
    await ElMessageBox.confirm(`确定删除草稿凭证 ${row.voucherNo} 吗？`, '删除草稿', {
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    })
    await deleteManualVoucher(row.id)
    ElMessage.success('已删除')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

// ---- 作废 ----
const cancelVisible = ref(false)
const cancelling = ref(false)
const cancelReason = ref('')
const cancellingRow = ref<ManualVoucher | null>(null)

const openCancel = (row: ManualVoucher) => {
  cancellingRow.value = row
  cancelReason.value = ''
  cancelVisible.value = true
}

const handleCancel = async () => {
  if (cancelling.value) return
  const reason = cancelReason.value.trim()
  if (!cancellingRow.value || !reason) return
  cancelling.value = true
  try {
    await cancelManualVoucher(cancellingRow.value.id, reason)
    ElMessage.success('已作废并生成红冲凭证')
    cancelVisible.value = false
    loadData()
  } catch {
    ElMessage.error('作废失败')
  } finally {
    cancelling.value = false
  }
}

// ---- 驳回 ----
const rejectVisible = ref(false)
const rejecting = ref(false)
const rejectReason = ref('')
const rejectingRow = ref<ManualVoucher | null>(null)

const openReject = (row: ManualVoucher) => {
  rejectingRow.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

const handleReject = async () => {
  if (!rejectingRow.value || !rejectReason.value.trim()) return
  rejecting.value = true
  try {
    await rejectManualVoucher(rejectingRow.value.id, rejectReason.value.trim())
    ElMessage.success('已驳回')
    rejectVisible.value = false
    loadData()
  } catch {
    ElMessage.error('驳回失败')
  } finally {
    rejecting.value = false
  }
}

// ---- 详情 ----
const detailVisible = ref(false)
const currentVoucher = ref<ManualVoucher | null>(null)

const openDetail = async (row: ManualVoucher) => {
  try {
    currentVoucher.value = await getManualVoucher(row.id)
    detailVisible.value = true
  } catch {
    ElMessage.error('加载凭证详情失败')
  }
}

// ---- 展示辅助 ----
const statusLabel = (status: string) =>
  statusOptions.find((o) => o.value === status)?.label || status

const statusTagType = (status: string) => {
  switch (status) {
    case 'DRAFT': return 'info'
    case 'PENDING': return 'warning'
    case 'APPROVED': return 'primary'
    case 'POSTED': return 'success'
    case 'CANCELLED': return 'danger'
    default: return 'info'
  }
}

const formatAmount = (amount?: number | string) => {
  const value = Number(amount ?? 0)
  return Number.isFinite(value)
    ? value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : '0.00'
}

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
