<template>
  <div class="page">
    <el-card shadow="never">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item :label="t('purchaseRequisition.keyword')">
          <el-input v-model="query.keyword" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item :label="t('purchaseRequisition.status')">
          <el-select v-model="query.status" clearable style="width: 140px">
            <el-option
              v-for="s in statuses"
              :key="s"
              :label="statusLabel(s)"
              :value="s"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ t('purchaseRequisition.search') }}</el-button>
          <el-button @click="handleReset">{{ t('purchaseRequisition.reset') }}</el-button>
          <el-button v-permission="'purchase:requisition:manage'" type="success" @click="openCreate">
            {{ t('purchaseRequisition.create') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="requisitionNo" :label="t('purchaseRequisition.no')" min-width="150" />
        <el-table-column prop="requisitionDate" :label="t('purchaseRequisition.date')" width="120" />
        <el-table-column prop="neededDate" :label="t('purchaseRequisition.neededDate')" width="120">
          <template #default="{ row }">{{ row.neededDate || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="t('purchaseRequisition.status')" width="120">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="approvalStatus" :label="t('purchaseRequisition.approvalStatus')" width="130">
          <template #default="{ row }">{{ approvalLabel(row.approvalStatus) }}</template>
        </el-table-column>
        <el-table-column prop="convertedOrderNo" :label="t('purchaseRequisition.convertedPo')" min-width="140">
          <template #default="{ row }">{{ row.convertedOrderNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('purchaseRequisition.remark')" min-width="140" show-overflow-tooltip />
        <el-table-column :label="t('purchaseRequisition.actions')" width="400" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">{{ t('purchaseRequisition.view') }}</el-button>
            <el-button link type="primary" @click="handlePrint(row)">{{ t('purchaseRequisition.print') }}</el-button>
            <el-button
              v-if="['DRAFT', 'REJECTED'].includes(row.status)"
              v-permission="'purchase:requisition:manage'"
              link
              type="primary"
              @click="openEdit(row)"
            >
              {{ t('purchaseRequisition.edit') }}
            </el-button>
            <el-button
              v-if="['DRAFT', 'REJECTED'].includes(row.status)"
              v-permission="'purchase:requisition:manage'"
              link
              type="success"
              @click="act(row, 'submit')"
            >
              {{ t('purchaseRequisition.submit') }}
            </el-button>
            <el-button
              v-if="row.status === 'SUBMITTED'"
              v-permission="'purchase:requisition:manage'"
              link
              type="success"
              @click="act(row, 'approve')"
            >
              {{ t('purchaseRequisition.approve') }}
            </el-button>
            <el-button
              v-if="row.status === 'SUBMITTED'"
              v-permission="'purchase:requisition:manage'"
              link
              type="warning"
              @click="act(row, 'reject')"
            >
              {{ t('purchaseRequisition.reject') }}
            </el-button>
            <el-button
              v-if="row.status === 'APPROVED'"
              v-permission="'purchase:requisition:manage'"
              link
              type="primary"
              @click="act(row, 'convert')"
            >
              {{ t('purchaseRequisition.convert') }}
            </el-button>
            <el-button
              v-if="!['CONVERTED', 'CANCELLED'].includes(row.status)"
              v-permission="'purchase:requisition:manage'"
              link
              type="danger"
              @click="act(row, 'cancel')"
            >
              {{ t('purchaseRequisition.cancel') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? t('purchaseRequisition.editTitle') : t('purchaseRequisition.createTitle')"
      width="860px"
      destroy-on-close
    >
      <el-form label-width="110px">
        <el-form-item :label="t('purchaseRequisition.date')">
          <el-date-picker v-model="form.requisitionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('purchaseRequisition.neededDate')">
          <el-date-picker v-model="form.neededDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('purchaseRequisition.supplier')">
          <el-select v-model="form.supplierId" clearable filterable style="width: 100%">
            <el-option
              v-for="s in suppliers"
              :key="s.id"
              :label="s.supplierName || s.name"
              :value="String(s.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('purchaseRequisition.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('purchaseRequisition.lines')">
          <div style="width: 100%">
            <el-button size="small" @click="addLine">{{ t('purchaseRequisition.addLine') }}</el-button>
            <el-table :data="form.lines" border style="margin-top: 8px">
              <el-table-column :label="t('purchaseRequisition.product')" min-width="220">
                <template #default="{ row }">
                  <el-select v-model="row.productId" filterable style="width: 100%">
                    <el-option
                      v-for="p in products"
                      :key="p.id"
                      :label="`${p.productCode || p.code || ''} ${p.productName || p.name || ''}`"
                      :value="String(p.id)"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column :label="t('purchaseRequisition.qty')" width="140">
                <template #default="{ row }">
                  <el-input-number v-model="row.qty" :min="0.0001" :controls="false" style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column :label="t('purchaseRequisition.lineRemark')" min-width="160">
                <template #default="{ row }">
                  <el-input v-model="row.remark" :placeholder="t('purchaseRequisition.lineRemarkPlaceholder')" />
                </template>
              </el-table-column>
              <el-table-column width="80">
                <template #default="{ $index }">
                  <el-button link type="danger" @click="removeLine($index)">
                    {{ t('purchaseRequisition.delete') }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('purchaseRequisition.close') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('purchaseRequisition.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="detailVisible"
      :title="t('purchaseRequisition.detailTitle')"
      width="860px"
      destroy-on-close
    >
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('purchaseRequisition.no')">{{ detail.requisitionNo }}</el-descriptions-item>
          <el-descriptions-item :label="t('purchaseRequisition.date')">{{ detail.requisitionDate }}</el-descriptions-item>
          <el-descriptions-item :label="t('purchaseRequisition.neededDate')">{{ detail.neededDate || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('purchaseRequisition.status')">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item :label="t('purchaseRequisition.approvalStatus')">{{ approvalLabel(detail.approvalStatus) }}</el-descriptions-item>
          <el-descriptions-item :label="t('purchaseRequisition.convertedPo')">{{ detail.convertedOrderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('purchaseRequisition.supplier')">{{ supplierLabel(detail.supplierId) }}</el-descriptions-item>
          <el-descriptions-item :label="t('purchaseRequisition.remark')">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-lines-title">{{ t('purchaseRequisition.lines') }}</div>
        <el-table :data="detail.lines || []" border stripe>
          <el-table-column type="index" width="60" :label="t('purchaseRequisition.sequence')" />
          <el-table-column prop="productCode" :label="t('purchaseRequisition.productCode')" width="140" />
          <el-table-column prop="productName" :label="t('purchaseRequisition.productName')" min-width="180" />
          <el-table-column prop="qty" :label="t('purchaseRequisition.qty')" width="120" align="right" />
          <el-table-column prop="remark" :label="t('purchaseRequisition.lineRemark')" min-width="160" show-overflow-tooltip />
        </el-table>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">{{ t('purchaseRequisition.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  approvePurchaseRequisition,
  cancelPurchaseRequisition,
  convertPurchaseRequisition,
  createPurchaseRequisition,
  getPurchaseRequisition,
  getPurchaseRequisitions,
  rejectPurchaseRequisition,
  submitPurchaseRequisition,
  updatePurchaseRequisition,
  type PurchaseRequisition
} from '@/api/purchase'
import { getProducts, getSuppliers } from '@/api/masterdata'
import { printPurchaseRequisition } from '@/utils/bizPrint'
import { usePurchaseRequisitionPresentation } from '@/composables/usePurchaseRequisitionPresentation'
import { usePurchaseRequisitionList } from '@/composables/usePurchaseRequisitionList'
import { usePurchaseRequisitionForm } from '@/composables/usePurchaseRequisitionForm'

const { t } = useI18n()

const notify = {
  onError: (message: string) => ElMessage.error(message),
  onSuccess: (message: string) => ElMessage.success(message),
  onWarning: (message: string) => ElMessage.warning(message)
}

const {
  act,
  detail,
  detailVisible,
  handlePageChange,
  handlePrint,
  handleReset,
  handleSearch,
  handleSizeChange,
  loadData,
  loadOptions,
  loading,
  openDetail,
  products,
  query,
  rows,
  suppliers,
  total
} = usePurchaseRequisitionList(t, {
  getRequisitions: getPurchaseRequisitions,
  getRequisition: getPurchaseRequisition,
  getProducts,
  getSuppliers,
  submit: submitPurchaseRequisition,
  approve: approvePurchaseRequisition,
  reject: rejectPurchaseRequisition,
  cancel: cancelPurchaseRequisition,
  convert: convertPurchaseRequisition,
  printRequisition: printPurchaseRequisition,
  decoratePrint: (detailData) => {
    const productMap = new Map(products.value.map((product) => [String(product.id), product]))
    return {
      ...detailData,
      supplierName: supplierLabel(detailData.supplierId),
      lines: (detailData.lines || []).map((line) => {
        const product = productMap.get(String(line.productId))
        return {
          ...line,
          productCode: line.productCode || product?.productCode || product?.code || line.productId,
          productName: line.productName || product?.productName || product?.name || ''
        }
      })
    } as PurchaseRequisition
  },
  confirm: (message, title, options) => ElMessageBox.confirm(message, title, options as any),
  onError: notify.onError,
  onSuccess: notify.onSuccess
})

const {
  approvalLabel,
  statusLabel,
  statuses,
  supplierLabel
} = usePurchaseRequisitionPresentation(t, suppliers)

const {
  addLine,
  dialogVisible,
  editingId,
  form,
  openCreate,
  openEdit,
  removeLine,
  save,
  saving
} = usePurchaseRequisitionForm(t, {
  getRequisition: getPurchaseRequisition,
  createRequisition: createPurchaseRequisition,
  updateRequisition: updatePurchaseRequisition,
  ensureOptions: loadOptions,
  onSubmitted: loadData,
  ...notify
})

onMounted(loadData)
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.detail-lines-title {
  margin: 16px 0 8px;
  font-weight: 600;
}
</style>
