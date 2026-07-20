<template>
  <div class="qc-inspection-page">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="检验单号">
          <el-input v-model="searchForm.keyword" placeholder="检验单号" clearable style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 150px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已提交" value="SUBMITTED" />
            <el-option label="已判定" value="JUDGED" />
            <el-option label="已作废" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="searchForm.inspectionType" placeholder="全部" clearable style="width: 140px">
            <el-option label="来料 IQC" value="IQC" />
            <el-option label="出库 OQC" value="OQC" />
            <el-option label="过程 IPQC" value="IPQC" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <el-button v-permission="'qc:inspection:create'" type="primary" :icon="Plus" @click="handleCreate">新建检验单</el-button>
        <el-button v-permission="'qc:inspection:view'" :icon="Download" @click="handleExport">导出</el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column prop="inspectionNo" label="检验单号" min-width="170" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            {{ row.inspectionType === 'OQC' ? '出库 OQC' : row.inspectionType === 'IPQC' ? '过程 IPQC' : '来料 IQC' }}
          </template>
        </el-table-column>
        <el-table-column label="来源单" min-width="170">
          <template #default="{ row }">
            <span v-if="row.inspectionType === 'OQC'">出库 {{ row.deliveryId || '-' }}</span>
            <span v-else-if="row.inspectionType === 'IPQC'">工单 {{ row.productionOrderId || row.orderId || '-' }}</span>
            <span v-else>入库 {{ row.receiptId || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="inspectionDate" label="检验日期" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalQty" label="检验数量" width="110" align="right" />
        <el-table-column prop="qualifiedQty" label="合格数量" width="110" align="right" />
        <el-table-column prop="unqualifiedQty" label="不合格数量" width="110" align="right" />
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">详情</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'qc:inspection:update'"
              link
              type="primary"
              @click="handleEdit(row)"
            >编辑</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'qc:inspection:submit'"
              link
              type="primary"
              @click="handleSubmit(row)"
            >提交</el-button>
            <el-button
              v-if="row.status === 'SUBMITTED'"
              v-permission="'qc:inspection:judge'"
              link
              type="success"
              @click="handleJudge(row)"
            >判定</el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'SUBMITTED'"
              v-permission="'qc:inspection:cancel'"
              link
              type="danger"
              @click="handleCancel(row)"
            >作废</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="searchForm.pageNo"
        :page-size="searchForm.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>

    <!-- 新建检验单 -->
    <el-dialog v-model="createVisible" title="新建检验单" width="560px">
      <el-form :model="createForm" label-width="110px">
        <el-form-item label="检验类型" required>
          <el-radio-group v-model="createForm.inspectionType" @change="onCreateTypeChange">
            <el-radio-button value="IQC">来料 IQC</el-radio-button>
            <el-radio-button value="OQC">出库 OQC</el-radio-button>
            <el-radio-button value="IPQC">过程 IPQC</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="createForm.inspectionType === 'IQC'" label="采购入库单" required>
          <el-select
            v-model="createForm.receiptId"
            placeholder="选择草稿状态的采购入库单"
            filterable
            style="width: 100%"
            :loading="sourceLoading"
          >
            <el-option
              v-for="receipt in draftReceipts"
              :key="receipt.id"
              :label="`${receipt.receiptNo}（数量 ${receipt.totalQuantity}）`"
              :value="receipt.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-else-if="createForm.inspectionType === 'OQC'" label="销售出库单" required>
          <el-select
            v-model="createForm.deliveryId"
            placeholder="选择草稿状态的销售出库单"
            filterable
            style="width: 100%"
            :loading="sourceLoading"
          >
            <el-option
              v-for="delivery in draftDeliveries"
              :key="delivery.id"
              :label="`${delivery.deliveryNo}（数量 ${delivery.totalQuantity}）`"
              :value="delivery.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-else-if="createForm.inspectionType === 'IPQC'" label="生产工单ID" required>
          <el-input v-model="createForm.productionOrderId" placeholder="已下达/已领料的生产工单ID" />
        </el-form-item>
        <el-form-item label="检验日期" required>
          <el-date-picker v-model="createForm.inspectionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 编辑草稿 -->
    <el-dialog v-model="editVisible" :title="editDialogTitle" width="860px" destroy-on-close>
      <div class="dialog-sub">
        检验单号：{{ editingInspectionNo }}　来源单据不可改；仅草稿可编辑检验日期、备注与行检验数量
      </div>
      <el-form :model="editForm" label-width="110px" style="margin-bottom: 8px">
        <el-form-item :label="editForm.inspectionType === 'OQC' ? '销售出库单' : '采购入库单'">
          <el-input
            :model-value="String((editForm.inspectionType === 'OQC' ? editForm.deliveryId : editForm.receiptId) || '')"
            disabled
          />
        </el-form-item>
        <el-form-item label="检验日期" required>
          <el-date-picker v-model="editForm.inspectionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <el-table :data="editLines" border size="small">
        <el-table-column prop="lineNo" label="行" width="60" />
        <el-table-column prop="productId" label="商品ID" min-width="140" />
        <el-table-column label="检验数量" width="150">
          <template #default="{ row }">
            <el-input-number
              v-model="row.inspectedQty"
              :min="0"
              :precision="4"
              :controls="false"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="不合格原因" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.defectReason" placeholder="选填" maxlength="255" />
          </template>
        </el-table-column>
        <el-table-column label="行备注" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.remark" placeholder="选填" maxlength="255" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 判定检验单 -->
    <el-dialog v-model="judgeVisible" title="判定检验单" width="820px">
      <div class="dialog-sub">检验单号：{{ current?.inspectionNo }}　合格+不合格数量之和须等于检验数量</div>
      <el-table :data="judgeLines" border size="small">
        <el-table-column prop="lineNo" label="行" width="60" />
        <el-table-column prop="productId" label="商品ID" min-width="150" />
        <el-table-column prop="inspectedQty" label="检验数量" width="110" align="right" />
        <el-table-column label="合格数量" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row.qualifiedQty" :min="0" :max="row.inspectedQty" :precision="4" :controls="false" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="不合格数量" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row.unqualifiedQty" :min="0" :max="row.inspectedQty" :precision="4" :controls="false" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="不合格原因" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.defectReason" placeholder="选填" maxlength="255" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="judgeVisible = false">取消</el-button>
        <el-button type="success" :loading="submitting" @click="confirmJudge">确认判定</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="检验单详情" width="820px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="检验单号">{{ current?.inspectionNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="current" :type="statusType(current.status)">{{ statusText(current.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="类型">{{ current?.inspectionType === 'OQC' ? '出库 OQC' : '来料 IQC' }}</el-descriptions-item>
        <el-descriptions-item :label="current?.inspectionType === 'OQC' ? '销售出库单' : '采购入库单'">
          {{ current?.inspectionType === 'OQC' ? (current?.deliveryId || '-') : (current?.receiptId || '-') }}
        </el-descriptions-item>
        <el-descriptions-item label="检验日期">{{ current?.inspectionDate }}</el-descriptions-item>
        <el-descriptions-item label="检验数量">{{ current?.totalQty }}</el-descriptions-item>
        <el-descriptions-item label="合格 / 不合格">{{ current?.qualifiedQty }} / {{ current?.unqualifiedQty }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ current?.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="current?.lines || []" border size="small" style="margin-top: 12px">
        <el-table-column prop="lineNo" label="行" width="60" />
        <el-table-column prop="productId" label="商品ID" min-width="150" />
        <el-table-column prop="inspectedQty" label="检验数量" width="110" align="right" />
        <el-table-column prop="qualifiedQty" label="合格数量" width="110" align="right" />
        <el-table-column prop="unqualifiedQty" label="不合格数量" width="110" align="right" />
        <el-table-column prop="defectReason" label="不合格原因" min-width="140" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Download } from '@element-plus/icons-vue'
import {
  getQcInspections,
  getQcInspection,
  createQcInspection,
  updateQcInspection,
  submitQcInspection,
  judgeQcInspection,
  cancelQcInspection,
  exportQcInspections,
  type QcInspection,
  type QcInspectionQuery,
  type QcInspectionType
} from '@/api/qc'
import { getPurchaseReceipts, type PurchaseReceipt } from '@/api/purchase'
import { getSalesDeliveries, type SalesDelivery } from '@/api/sales'
import { downloadBlob } from '@/utils/download'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref<QcInspection[]>([])
const total = ref(0)
const current = ref<QcInspection>()

const searchForm = reactive<QcInspectionQuery>({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  status: '',
  inspectionType: ''
})

const statusText = (status: string) => ({
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  JUDGED: '已判定',
  CANCELLED: '已作废'
}[status] || status)

const statusType = (status: string) => ({
  DRAFT: 'info',
  SUBMITTED: 'warning',
  JUDGED: 'success',
  CANCELLED: 'danger'
}[status] || 'info') as 'info' | 'warning' | 'success' | 'danger'

const loadData = async () => {
  loading.value = true
  try {
    const res = await getQcInspections(searchForm)
    tableData.value = res.records
    total.value = res.total
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  searchForm.pageNo = 1
  loadData()
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.status = ''
  searchForm.inspectionType = ''
  searchForm.pageNo = 1
  loadData()
}

const handlePageChange = (page: number) => {
  searchForm.pageNo = page
  loadData()
}

const handleSizeChange = (size: number) => {
  searchForm.pageSize = size
  searchForm.pageNo = 1
  loadData()
}

// ---- 新建 ----
const createVisible = ref(false)
const sourceLoading = ref(false)
const draftReceipts = ref<PurchaseReceipt[]>([])
const draftDeliveries = ref<SalesDelivery[]>([])
const createForm = reactive<{
  inspectionType: QcInspectionType
  receiptId: string | number | ''
  deliveryId: string | number | ''
  productionOrderId: string
  inspectionDate: string
  remark: string
}>({
  inspectionType: 'IQC',
  receiptId: '',
  deliveryId: '',
  productionOrderId: '',
  inspectionDate: '',
  remark: ''
})

const today = () => {
  const d = new Date()
  const m = `${d.getMonth() + 1}`.padStart(2, '0')
  const day = `${d.getDate()}`.padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const loadCreateSources = async () => {
  sourceLoading.value = true
  try {
    if (createForm.inspectionType === 'OQC') {
      const res = await getSalesDeliveries({ pageNo: 1, pageSize: 100, status: 'DRAFT' })
      draftDeliveries.value = res.records
    } else {
      const res = await getPurchaseReceipts({ pageNo: 1, pageSize: 100, status: 'DRAFT' })
      draftReceipts.value = res.records
    }
  } catch {
    // 拦截器已提示
  } finally {
    sourceLoading.value = false
  }
}

const handleCreate = async () => {
  createForm.inspectionType = 'IQC'
  createForm.receiptId = ''
  createForm.deliveryId = ''
  createForm.productionOrderId = ''
  createForm.inspectionDate = today()
  createForm.remark = ''
  createVisible.value = true
  await loadCreateSources()
}

const onCreateTypeChange = async () => {
  createForm.receiptId = ''
  createForm.deliveryId = ''
  createForm.productionOrderId = ''
  await loadCreateSources()
}

const confirmCreate = async () => {
  if (createForm.inspectionType === 'OQC') {
    if (!createForm.deliveryId) {
      ElMessage.warning('请选择销售出库单')
      return
    }
  } else if (!createForm.receiptId) {
    ElMessage.warning('请选择采购入库单')
    return
  }
  if (!createForm.inspectionDate) {
    ElMessage.warning('请选择检验日期')
    return
  }
  submitting.value = true
  try {
    await createQcInspection({
      inspectionType: createForm.inspectionType,
      receiptId: createForm.inspectionType === 'IQC' ? createForm.receiptId : undefined,
      deliveryId: createForm.inspectionType === 'OQC' ? createForm.deliveryId : undefined,
      productionOrderId: createForm.inspectionType === 'IPQC' ? createForm.productionOrderId : undefined,
      inspectionDate: createForm.inspectionDate,
      remark: createForm.remark || undefined
    })
    ElMessage.success('创建成功')
    createVisible.value = false
    loadData()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

// ---- 提交 ----
const handleSubmit = async (row: QcInspection) => {
  try {
    await ElMessageBox.confirm(`确认提交检验单「${row.inspectionNo}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await submitQcInspection(row.id)
    ElMessage.success('已提交')
    loadData()
  } catch {
    // 拦截器已提示
  }
}

// ---- 编辑草稿 ----
const editVisible = ref(false)
const editingId = ref<string | number | null>(null)
const editingInspectionNo = ref('')
const editForm = reactive<{
  inspectionType: string
  receiptId: string | number | ''
  deliveryId: string | number | ''
  productionOrderId: string
  inspectionDate: string
  remark: string
}>({
  inspectionType: 'IQC',
  receiptId: '',
  deliveryId: '',
  productionOrderId: '',
  inspectionDate: '',
  remark: ''
})
const editLines = ref<Array<{
  lineId: string | number
  lineNo: number
  productId: string | number
  inspectedQty: number
  defectReason: string
  remark: string
}>>([])

const editDialogTitle = computed(() =>
  editForm.inspectionType === 'OQC' ? '编辑出库检验单' : '编辑来料检验单'
)

const resetEditForm = () => {
  editingId.value = null
  editingInspectionNo.value = ''
  editForm.inspectionType = 'IQC'
  editForm.receiptId = ''
  editForm.deliveryId = ''
  editForm.inspectionDate = ''
  editForm.remark = ''
  editLines.value = []
}

const handleEdit = async (row: QcInspection) => {
  if (row.status !== 'DRAFT') {
    ElMessage.warning('仅草稿状态可编辑')
    return
  }
  try {
    const detail = await getQcInspection(row.id)
    if (detail.status !== 'DRAFT') {
      ElMessage.warning('仅草稿状态可编辑')
      return
    }
    editingId.value = detail.id
    editingInspectionNo.value = detail.inspectionNo
    editForm.inspectionType = detail.inspectionType || 'IQC'
    editForm.receiptId = detail.receiptId ?? ''
    editForm.deliveryId = detail.deliveryId ?? ''
    editForm.inspectionDate = detail.inspectionDate
    editForm.remark = detail.remark || ''
    editLines.value = detail.lines.map((line) => ({
      lineId: line.id,
      lineNo: line.lineNo,
      productId: line.productId,
      inspectedQty: Number(line.inspectedQty ?? 0),
      defectReason: line.defectReason || '',
      remark: line.remark || ''
    }))
    editVisible.value = true
  } catch {
    // 拦截器已提示
  }
}

const confirmEdit = async () => {
  if (editingId.value == null) {
    ElMessage.warning('未加载到可编辑的检验单')
    return
  }
  if (!editForm.inspectionDate) {
    ElMessage.warning('请选择检验日期')
    return
  }
  for (const line of editLines.value) {
    if (line.inspectedQty == null || Number(line.inspectedQty) < 0) {
      ElMessage.warning(`第 ${line.lineNo} 行：检验数量不能为负数`)
      return
    }
  }
  submitting.value = true
  try {
    await updateQcInspection(editingId.value, {
      inspectionDate: editForm.inspectionDate,
      remark: editForm.remark?.trim() || undefined,
      lines: editLines.value.map((line) => ({
        lineId: line.lineId,
        inspectedQty: Number(line.inspectedQty),
        defectReason: line.defectReason?.trim() || undefined,
        remark: line.remark?.trim() || undefined
      }))
    })
    ElMessage.success('保存成功')
    editVisible.value = false
    resetEditForm()
    loadData()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

// ---- 判定 ----
const judgeVisible = ref(false)
const judgeLines = ref<Array<{ lineId: string | number; productId: string | number; lineNo: number; inspectedQty: number; qualifiedQty: number; unqualifiedQty: number; defectReason: string }>>([])

const handleJudge = async (row: QcInspection) => {
  try {
    const detail = await getQcInspection(row.id)
    current.value = detail
    judgeLines.value = detail.lines.map((line) => ({
      lineId: line.id,
      productId: line.productId,
      lineNo: line.lineNo,
      inspectedQty: line.inspectedQty,
      qualifiedQty: line.inspectedQty,
      unqualifiedQty: 0,
      defectReason: line.defectReason || ''
    }))
    judgeVisible.value = true
  } catch {
    // 拦截器已提示
  }
}

const confirmJudge = async () => {
  for (const line of judgeLines.value) {
    if (Number(line.qualifiedQty) + Number(line.unqualifiedQty) !== Number(line.inspectedQty)) {
      ElMessage.warning(`第 ${line.lineNo} 行：合格数量 + 不合格数量必须等于检验数量`)
      return
    }
  }
  if (!current.value) return
  submitting.value = true
  try {
    await judgeQcInspection(current.value.id, {
      lines: judgeLines.value.map((line) => ({
        lineId: line.lineId,
        qualifiedQty: line.qualifiedQty,
        unqualifiedQty: line.unqualifiedQty,
        defectReason: line.defectReason || undefined
      }))
    })
    ElMessage.success('判定完成')
    judgeVisible.value = false
    loadData()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

// ---- 作废 ----
const handleCancel = async (row: QcInspection) => {
  try {
    await ElMessageBox.confirm(`确认作废检验单「${row.inspectionNo}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await cancelQcInspection(row.id)
    ElMessage.success('已作废')
    loadData()
  } catch {
    // 拦截器已提示
  }
}

// ---- 详情 ----
const detailVisible = ref(false)
const handleView = async (row: QcInspection) => {
  try {
    current.value = await getQcInspection(row.id)
    detailVisible.value = true
  } catch {
    // 拦截器已提示
  }
}

// ---- 导出 ----
const handleExport = async () => {
  try {
    const blob = await exportQcInspections(searchForm)
    downloadBlob(blob, `来料检验单_${today()}.csv`)
    ElMessage.success('导出成功')
  } catch {
    // 拦截器已提示
  }
}

onMounted(loadData)
</script>

<style scoped>
.qc-inspection-page {
  padding: 16px;
}
.search-card {
  margin-bottom: 12px;
}
.toolbar {
  margin-bottom: 12px;
  display: flex;
  gap: 8px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.dialog-sub {
  margin-bottom: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
