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
      <div class="dialog-sub">{{ t('qcInspection.judgeHint', { no: judgingInspectionNo || '' }) }}</div>
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
import { computed, onMounted } from 'vue'
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
  exportQcInspections
} from '@/api/qc'
import { getPurchaseReceipts } from '@/api/purchase'
import { getSalesDeliveries } from '@/api/sales'
import { downloadBlob } from '@/utils/download'
import { printQcInspection } from '@/utils/bizPrint'
import { useQcInspectionPresentation } from '@/composables/useQcInspectionPresentation'
import { useQcInspectionList } from '@/composables/useQcInspectionList'
import { useQcInspectionCreate } from '@/composables/useQcInspectionCreate'
import { useQcInspectionEdit } from '@/composables/useQcInspectionEdit'

const { t } = useI18n()

const {
  inspectionTypeText,
  sourceDocumentId,
  sourceDocumentLabel,
  sourceDocumentText,
  statusText,
  statusType
} = useQcInspectionPresentation(t)

const {
  current,
  detailVisible,
  handleCancel,
  handleExport,
  handlePageChange,
  handlePrint,
  handleReset,
  handleSearch,
  handleSizeChange,
  handleSubmit,
  handleView,
  loadData,
  loading,
  searchForm,
  tableData,
  total
} = useQcInspectionList(t, {
  getInspections: getQcInspections,
  getInspection: getQcInspection,
  submitInspection: submitQcInspection,
  cancelInspection: cancelQcInspection,
  exportInspections: exportQcInspections,
  printInspection: printQcInspection,
  downloadBlob,
  confirm: (message, title, options) =>
    ElMessageBox.confirm(message, title, options as any),
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message)
})

const {
  confirmCreate,
  createForm,
  createVisible,
  draftDeliveries,
  draftReceipts,
  handleCreate,
  onCreateTypeChange,
  sourceLoading,
  submitting: createSubmitting
} = useQcInspectionCreate(t, {
  createInspection: createQcInspection,
  getReceipts: getPurchaseReceipts,
  getDeliveries: getSalesDeliveries,
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message),
  onWarning: (message) => ElMessage.warning(message),
  onSubmitted: loadData
})

const {
  confirmEdit,
  confirmJudge,
  editDialogTitle,
  editForm,
  editLines,
  editSourceDocumentId,
  editVisible,
  editingInspectionNo,
  handleEdit,
  handleJudge,
  judgeLines,
  judgeVisible,
  judgingInspectionNo,
  submitting: editSubmitting
} = useQcInspectionEdit(t, {
  getInspection: getQcInspection,
  updateInspection: updateQcInspection,
  judgeInspection: judgeQcInspection,
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message),
  onWarning: (message) => ElMessage.warning(message),
  onSubmitted: loadData
})

const submitting = computed(() => createSubmitting.value || editSubmitting.value)
const currentSourceDocumentId = computed(() => sourceDocumentId(current.value))

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
