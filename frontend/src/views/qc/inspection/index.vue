<template>
  <div class="qc-inspection-page">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item :label="t('qcInspection.inspectionNo')">
          <el-input v-model="searchForm.keyword" :placeholder="t('qcInspection.inspectionNo')" clearable style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item :label="t('qcInspection.statusLabel')">
          <el-select v-model="searchForm.status" :placeholder="t('qcInspection.all')" clearable style="width: 150px">
            <el-option :label="t('qcInspection.status.draft')" value="DRAFT" />
            <el-option :label="t('qcInspection.status.submitted')" value="SUBMITTED" />
            <el-option :label="t('qcInspection.status.judged')" value="JUDGED" />
            <el-option :label="t('qcInspection.status.cancelled')" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('qcInspection.typeLabel')">
          <el-select v-model="searchForm.inspectionType" :placeholder="t('qcInspection.all')" clearable style="width: 140px">
            <el-option :label="t('qcInspection.type.iqc')" value="IQC" />
            <el-option :label="t('qcInspection.type.oqc')" value="OQC" />
            <el-option :label="t('qcInspection.type.ipqc')" value="IPQC" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ t('qcInspection.search') }}</el-button>
          <el-button @click="handleReset">{{ t('qcInspection.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <el-button v-permission="'qc:inspection:create'" type="primary" :icon="Plus" @click="handleCreate">{{ t('qcInspection.create') }}</el-button>
        <el-button v-permission="'qc:inspection:view'" :icon="Download" @click="handleExport">{{ t('qcInspection.export') }}</el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column prop="inspectionNo" :label="t('qcInspection.inspectionNo')" min-width="170" />
        <el-table-column :label="t('qcInspection.typeLabel')" width="120">
          <template #default="{ row }">
            {{ inspectionTypeText(row.inspectionType) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('qcInspection.sourceDocument')" min-width="170">
          <template #default="{ row }">
            {{ sourceDocumentText(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="inspectionDate" :label="t('qcInspection.inspectionDate')" width="120" />
        <el-table-column :label="t('qcInspection.statusLabel')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalQty" :label="t('qcInspection.inspectedQuantity')" width="110" align="right" />
        <el-table-column prop="qualifiedQty" :label="t('qcInspection.qualifiedQuantity')" width="110" align="right" />
        <el-table-column prop="unqualifiedQty" :label="t('qcInspection.unqualifiedQuantity')" width="110" align="right" />
        <el-table-column :label="t('qcInspection.actions')" width="400" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">{{ t('qcInspection.detail') }}</el-button>
            <el-button link type="primary" @click="handlePrint(row)">{{ t('qcInspection.print') }}</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'qc:inspection:update'"
              link
              type="primary"
              @click="handleEdit(row)"
            >{{ t('qcInspection.edit') }}</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'qc:inspection:submit'"
              link
              type="primary"
              @click="handleSubmit(row)"
            >{{ t('qcInspection.submit') }}</el-button>
            <el-button
              v-if="row.status === 'SUBMITTED'"
              v-permission="'qc:inspection:judge'"
              link
              type="success"
              @click="handleJudge(row)"
            >{{ t('qcInspection.judge') }}</el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'SUBMITTED'"
              v-permission="'qc:inspection:cancel'"
              link
              type="danger"
              @click="handleCancel(row)"
            >{{ t('qcInspection.cancelInspection') }}</el-button>
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
    <el-dialog v-model="createVisible" :title="t('qcInspection.dialog.create')" width="560px">
      <el-form :model="createForm" label-width="110px">
        <el-form-item :label="t('qcInspection.inspectionType')" required>
          <el-radio-group v-model="createForm.inspectionType" @change="onCreateTypeChange">
            <el-radio-button value="IQC">{{ t('qcInspection.type.iqc') }}</el-radio-button>
            <el-radio-button value="OQC">{{ t('qcInspection.type.oqc') }}</el-radio-button>
            <el-radio-button value="IPQC">{{ t('qcInspection.type.ipqc') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="createForm.inspectionType === 'IQC'" :label="t('qcInspection.purchaseReceipt')" required>
          <el-select
            v-model="createForm.receiptId"
            :placeholder="t('qcInspection.selectDraftReceipt')"
            filterable
            style="width: 100%"
            :loading="sourceLoading"
          >
            <el-option
              v-for="receipt in draftReceipts"
              :key="receipt.id"
              :label="t('qcInspection.sourceOption', { no: receipt.receiptNo, quantity: receipt.totalQuantity })"
              :value="receipt.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-else-if="createForm.inspectionType === 'OQC'" :label="t('qcInspection.salesDelivery')" required>
          <el-select
            v-model="createForm.deliveryId"
            :placeholder="t('qcInspection.selectDraftDelivery')"
            filterable
            style="width: 100%"
            :loading="sourceLoading"
          >
            <el-option
              v-for="delivery in draftDeliveries"
              :key="delivery.id"
              :label="t('qcInspection.sourceOption', { no: delivery.deliveryNo, quantity: delivery.totalQuantity })"
              :value="delivery.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-else-if="createForm.inspectionType === 'IPQC'" :label="t('qcInspection.productionOrderId')" required>
          <el-input v-model="createForm.productionOrderId" :placeholder="t('qcInspection.productionOrderPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('qcInspection.inspectionDate')" required>
          <el-date-picker v-model="createForm.inspectionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('qcInspection.remark')">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">{{ t('qcInspection.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmCreate">{{ t('qcInspection.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 编辑草稿 -->
    <el-dialog v-model="editVisible" :title="editDialogTitle" width="860px" destroy-on-close>
      <div class="dialog-sub">
        {{ t('qcInspection.editHint', { no: editingInspectionNo }) }}
      </div>
      <el-form :model="editForm" label-width="110px" style="margin-bottom: 8px">
        <el-form-item :label="sourceDocumentLabel(editForm.inspectionType)">
          <el-input
            :model-value="String(editSourceDocumentId || '')"
            disabled
          />
        </el-form-item>
        <el-form-item :label="t('qcInspection.inspectionDate')" required>
          <el-date-picker v-model="editForm.inspectionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('qcInspection.remark')">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <el-table :data="editLines" border size="small">
        <el-table-column prop="lineNo" :label="t('qcInspection.line')" width="60" />
        <el-table-column prop="productId" :label="t('qcInspection.productId')" min-width="140" />
        <el-table-column :label="t('qcInspection.inspectedQuantity')" width="150">
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
        <el-table-column :label="t('qcInspection.defectReason')" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.defectReason" :placeholder="t('qcInspection.optional')" maxlength="255" />
          </template>
        </el-table-column>
        <el-table-column :label="t('qcInspection.lineRemark')" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.remark" :placeholder="t('qcInspection.optional')" maxlength="255" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="editVisible = false">{{ t('qcInspection.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmEdit">{{ t('qcInspection.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 判定检验单 -->
    <el-dialog v-model="judgeVisible" :title="t('qcInspection.dialog.judge')" width="820px">
      <div class="dialog-sub">{{ t('qcInspection.judgeHint', { no: current?.inspectionNo || '' }) }}</div>
      <el-table :data="judgeLines" border size="small">
        <el-table-column prop="lineNo" :label="t('qcInspection.line')" width="60" />
        <el-table-column prop="productId" :label="t('qcInspection.productId')" min-width="150" />
        <el-table-column prop="inspectedQty" :label="t('qcInspection.inspectedQuantity')" width="110" align="right" />
        <el-table-column :label="t('qcInspection.qualifiedQuantity')" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row.qualifiedQty" :min="0" :max="row.inspectedQty" :precision="4" :controls="false" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column :label="t('qcInspection.unqualifiedQuantity')" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row.unqualifiedQty" :min="0" :max="row.inspectedQty" :precision="4" :controls="false" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column :label="t('qcInspection.defectReason')" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.defectReason" :placeholder="t('qcInspection.optional')" maxlength="255" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="judgeVisible = false">{{ t('qcInspection.cancel') }}</el-button>
        <el-button type="success" :loading="submitting" @click="confirmJudge">{{ t('qcInspection.confirmJudge') }}</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" :title="t('qcInspection.dialog.detail')" width="820px">
      <el-descriptions :column="2" border>
        <el-descriptions-item :label="t('qcInspection.inspectionNo')">{{ current?.inspectionNo }}</el-descriptions-item>
        <el-descriptions-item :label="t('qcInspection.statusLabel')">
          <el-tag v-if="current" :type="statusType(current.status)">{{ statusText(current.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('qcInspection.typeLabel')">{{ inspectionTypeText(current?.inspectionType) }}</el-descriptions-item>
        <el-descriptions-item :label="sourceDocumentLabel(current?.inspectionType)">
          {{ currentSourceDocumentId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('qcInspection.inspectionDate')">{{ current?.inspectionDate }}</el-descriptions-item>
        <el-descriptions-item :label="t('qcInspection.inspectedQuantity')">{{ current?.totalQty }}</el-descriptions-item>
        <el-descriptions-item :label="t('qcInspection.qualifiedUnqualified')">{{ current?.qualifiedQty }} / {{ current?.unqualifiedQty }}</el-descriptions-item>
        <el-descriptions-item :label="t('qcInspection.remark')" :span="2">{{ current?.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="current?.lines || []" border size="small" style="margin-top: 12px">
        <el-table-column prop="lineNo" :label="t('qcInspection.line')" width="60" />
        <el-table-column prop="productId" :label="t('qcInspection.productId')" min-width="150" />
        <el-table-column prop="inspectedQty" :label="t('qcInspection.inspectedQuantity')" width="110" align="right" />
        <el-table-column prop="qualifiedQty" :label="t('qcInspection.qualifiedQuantity')" width="110" align="right" />
        <el-table-column prop="unqualifiedQty" :label="t('qcInspection.unqualifiedQuantity')" width="110" align="right" />
        <el-table-column prop="defectReason" :label="t('qcInspection.defectReason')" min-width="140" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
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
import { printQcInspection } from '@/utils/bizPrint'

const { t } = useI18n()

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
  DRAFT: t('qcInspection.status.draft'),
  SUBMITTED: t('qcInspection.status.submitted'),
  JUDGED: t('qcInspection.status.judged'),
  CANCELLED: t('qcInspection.status.cancelled')
}[status] || status)

const inspectionTypeText = (type?: string) => ({
  IQC: t('qcInspection.type.iqc'),
  OQC: t('qcInspection.type.oqc'),
  IPQC: t('qcInspection.type.ipqc')
}[type || ''] || type || '-')

const sourceDocumentLabel = (type?: string) => {
  if (type === 'OQC') return t('qcInspection.salesDelivery')
  if (type === 'IPQC') return t('qcInspection.productionOrderId')
  return t('qcInspection.purchaseReceipt')
}

const sourceDocumentId = (inspection?: Partial<QcInspection>) => {
  if (!inspection) return ''
  if (inspection.inspectionType === 'OQC') return inspection.deliveryId
  if (inspection.inspectionType === 'IPQC') return inspection.productionOrderId || inspection.orderId
  return inspection.receiptId
}

const sourceDocumentText = (inspection: QcInspection) => {
  const id = sourceDocumentId(inspection) || '-'
  if (inspection.inspectionType === 'OQC') return t('qcInspection.sourceOutbound', { id })
  if (inspection.inspectionType === 'IPQC') return t('qcInspection.sourceProduction', { id })
  return t('qcInspection.sourceInbound', { id })
}

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
    ElMessage.error(t('qcInspection.message.loadFailed'))
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
  if (createForm.inspectionType === 'IPQC') {
    draftReceipts.value = []
    draftDeliveries.value = []
    return
  }
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
    ElMessage.error(t('qcInspection.message.sourcesLoadFailed'))
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
      ElMessage.warning(t('qcInspection.validation.delivery'))
      return
    }
  } else if (createForm.inspectionType === 'IPQC') {
    if (!createForm.productionOrderId.trim()) {
      ElMessage.warning(t('qcInspection.validation.productionOrder'))
      return
    }
  } else {
    if (!createForm.receiptId) {
      ElMessage.warning(t('qcInspection.validation.receipt'))
      return
    }
  }
  if (!createForm.inspectionDate) {
    ElMessage.warning(t('qcInspection.validation.date'))
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
    ElMessage.success(t('qcInspection.message.created'))
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
    await ElMessageBox.confirm(t('qcInspection.message.submitConfirm', { no: row.inspectionNo }), t('qcInspection.message.prompt'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await submitQcInspection(row.id)
    ElMessage.success(t('qcInspection.message.submitted'))
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

const editDialogTitle = computed(() => {
  if (editForm.inspectionType === 'OQC') return t('qcInspection.dialog.editOqc')
  if (editForm.inspectionType === 'IPQC') return t('qcInspection.dialog.editIpqc')
  return t('qcInspection.dialog.editIqc')
})
const editSourceDocumentId = computed(() => {
  if (editForm.inspectionType === 'OQC') return editForm.deliveryId
  if (editForm.inspectionType === 'IPQC') return editForm.productionOrderId
  return editForm.receiptId
})
const currentSourceDocumentId = computed(() => sourceDocumentId(current.value))

const resetEditForm = () => {
  editingId.value = null
  editingInspectionNo.value = ''
  editForm.inspectionType = 'IQC'
  editForm.receiptId = ''
  editForm.deliveryId = ''
  editForm.productionOrderId = ''
  editForm.inspectionDate = ''
  editForm.remark = ''
  editLines.value = []
}

const handleEdit = async (row: QcInspection) => {
  if (row.status !== 'DRAFT') {
    ElMessage.warning(t('qcInspection.validation.draftOnly'))
    return
  }
  try {
    const detail = await getQcInspection(row.id)
    if (detail.status !== 'DRAFT') {
      ElMessage.warning(t('qcInspection.validation.draftOnly'))
      return
    }
    editingId.value = detail.id
    editingInspectionNo.value = detail.inspectionNo
    editForm.inspectionType = detail.inspectionType || 'IQC'
    editForm.receiptId = detail.receiptId ?? ''
    editForm.deliveryId = detail.deliveryId ?? ''
    editForm.productionOrderId = String(detail.productionOrderId ?? detail.orderId ?? '')
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
    ElMessage.error(t('qcInspection.message.detailLoadFailed'))
  }
}

const confirmEdit = async () => {
  if (editingId.value == null) {
    ElMessage.warning(t('qcInspection.validation.editableMissing'))
    return
  }
  if (!editForm.inspectionDate) {
    ElMessage.warning(t('qcInspection.validation.date'))
    return
  }
  for (const line of editLines.value) {
    if (line.inspectedQty == null || Number(line.inspectedQty) < 0) {
      ElMessage.warning(t('qcInspection.validation.negativeQuantity', { line: line.lineNo }))
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
    ElMessage.success(t('qcInspection.message.saved'))
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
    ElMessage.error(t('qcInspection.message.detailLoadFailed'))
  }
}

const confirmJudge = async () => {
  for (const line of judgeLines.value) {
    if (Number(line.qualifiedQty) + Number(line.unqualifiedQty) !== Number(line.inspectedQty)) {
      ElMessage.warning(t('qcInspection.validation.judgeQuantity', { line: line.lineNo }))
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
    ElMessage.success(t('qcInspection.message.judged'))
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
    await ElMessageBox.confirm(t('qcInspection.message.cancelConfirm', { no: row.inspectionNo }), t('qcInspection.message.prompt'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await cancelQcInspection(row.id)
    ElMessage.success(t('qcInspection.message.cancelled'))
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
    ElMessage.error(t('qcInspection.message.detailLoadFailed'))
  }
}

const handlePrint = async (row: QcInspection) => {
  try {
    const detail = await getQcInspection(row.id)
    printQcInspection(detail)
  } catch {
    ElMessage.error(t('qcInspection.message.printLoadFailed'))
  }
}

// ---- 导出 ----
const handleExport = async () => {
  try {
    const blob = await exportQcInspections(searchForm)
    downloadBlob(blob, t('qcInspection.message.exportFile', { date: today() }))
    ElMessage.success(t('qcInspection.message.exported'))
  } catch {
    ElMessage.error(t('qcInspection.message.exportFailed'))
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
