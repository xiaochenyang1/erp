<template>
  <div class="sales-orders-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item :label="t('salesOrder.keyword')">
          <el-input
            v-model="queryParams.keyword"
            :placeholder="t('salesOrder.orderNo')"
            clearable
            style="width: 220px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item :label="t('salesOrder.customer')">
          <el-select v-model="queryParams.customerId" :placeholder="t('salesOrder.selectCustomer')" clearable filterable style="width: 220px">
            <el-option v-for="customer in customers" :key="customer.id" :label="customer.name" :value="customer.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('salesOrder.orderStatus')">
          <el-select v-model="queryParams.status" :placeholder="t('salesOrder.selectStatus')" clearable style="width: 150px">
            <el-option :label="t('salesOrder.status.draft')" value="DRAFT" />
            <el-option :label="t('salesOrder.status.submitted')" value="SUBMITTED" />
            <el-option :label="t('salesOrder.status.approved')" value="APPROVED" />
            <el-option :label="t('salesOrder.status.rejected')" value="REJECTED" />
            <el-option :label="t('salesOrder.status.cancelled')" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('salesOrder.approvalStatus')">
          <el-select v-model="queryParams.approvalStatus" :placeholder="t('salesOrder.selectApprovalStatus')" clearable style="width: 150px">
            <el-option :label="t('salesOrder.status.notSubmitted')" value="NOT_SUBMITTED" />
            <el-option :label="t('salesOrder.status.submitted')" value="IN_APPROVAL" />
            <el-option :label="t('salesOrder.status.approved')" value="APPROVED" />
            <el-option :label="t('salesOrder.status.rejected')" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ t('salesOrder.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('salesOrder.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ t('salesOrder.title') }}</span>
          <el-button v-permission="'sales:order:create'" type="primary" :icon="Plus" @click="handleCreate">{{ t('salesOrder.create') }}</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="orderNo" :label="t('salesOrder.orderNo')" min-width="170" fixed />
        <el-table-column prop="customerName" :label="t('salesOrder.customer')" min-width="160" />
        <el-table-column prop="orderDate" :label="t('salesOrder.orderDate')" width="120" />
        <el-table-column prop="deliveryDate" :label="t('salesOrder.deliveryDate')" width="120" />
        <el-table-column prop="totalQuantity" :label="t('salesOrder.quantity')" width="110" align="right">
          <template #default="{ row }">{{ formatNumber(row.totalQuantity) }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" :label="t('salesOrder.amount')" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="t('salesOrder.orderStatus')" width="110">
          <template #default="{ row }">
            <el-tag>{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="approvalStatus" :label="t('salesOrder.approvalStatus')" width="110">
          <template #default="{ row }">
            <el-tag :type="approvalTagType(row.approvalStatus)">{{ approvalText(row.approvalStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deliveryStatus" :label="t('salesOrder.deliveryStatus')" width="110">
          <template #default="{ row }">{{ deliveryText(row.deliveryStatus) }}</template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('salesOrder.remark')" min-width="160" show-overflow-tooltip />
        <el-table-column :label="t('salesOrder.actions')" width="310" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="handleView(row)">{{ t('salesOrder.view') }}</el-button>
            <el-button link type="primary" @click="handlePrint(row)">{{ t('salesOrder.print') }}</el-button>
            <el-button v-permission="'sales:order:create'" link type="primary" @click="handleCopy(row)">{{ t('salesOrder.copy') }}</el-button>
            <el-button v-if="canEdit(row)" v-permission="'sales:order:update'" link type="primary" :icon="Edit" @click="handleEdit(row)">{{ t('salesOrder.edit') }}</el-button>
            <el-button v-if="canSubmit(row)" v-permission="'sales:order:submit'" link type="success" @click="handleSubmitOrder(row)">{{ t('salesOrder.submit') }}</el-button>
            <el-button v-if="canApprove(row)" v-permission="'sales:order:approve'" link type="success" @click="handleApprove(row)">{{ t('salesOrder.approve') }}</el-button>
            <el-button v-if="canReject(row)" v-permission="'sales:order:reject'" link type="warning" @click="handleReject(row)">{{ t('salesOrder.reject') }}</el-button>
            <el-button v-if="canUnapprove(row)" v-permission="'sales:order:unapprove'" link type="warning" @click="handleUnapprove(row)">{{ t('salesOrder.unapprove') }}</el-button>
            <el-button v-if="canCancel(row)" v-permission="'sales:order:cancel'" link type="danger" @click="handleCancel(row)">{{ t('salesOrder.cancel') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.pageNo"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleQuery"
        @current-change="loadData"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="980px" @close="resetForm">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item :label="t('salesOrder.customer')" prop="customerId">
              <el-select
                v-model="formData.customerId"
                :placeholder="t('salesOrder.selectCustomer')"
                filterable
                style="width: 100%"
                :disabled="isView"
                @change="onCustomerOrDateChange"
              >
                <el-option v-for="customer in customers" :key="customer.id" :label="customer.name" :value="customer.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('salesOrder.warehouse')" prop="warehouseId">
              <el-select v-model="formData.warehouseId" :placeholder="t('salesOrder.selectWarehouse')" filterable style="width: 100%" :disabled="isView">
                <el-option v-for="warehouse in warehouses" :key="warehouse.id" :label="warehouse.name" :value="warehouse.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item :label="t('salesOrder.orderDate')" prop="orderDate">
              <el-date-picker
                v-model="formData.orderDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%"
                :disabled="isView"
                @change="onCustomerOrDateChange"
              />
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item :label="t('salesOrder.deliveryDate')">
              <el-date-picker v-model="formData.deliveryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" :disabled="isView" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item :label="t('salesOrder.remark')">
          <el-input v-model="formData.remark" type="textarea" :rows="2" :placeholder="t('salesOrder.remarkPlaceholder')" :disabled="isView" />
        </el-form-item>

        <div v-if="formData.customerId && !isView" v-loading="creditPreviewLoading" class="credit-preview-card">
          <div class="credit-preview-header">
            <div>
              <div class="credit-preview-title">{{ t('salesOrder.creditPreview') }}</div>
              <div class="credit-preview-subtitle">{{ t('salesOrder.creditFormula') }}</div>
            </div>
            <el-tag v-if="creditPreview" :type="creditPreview.exceeded ? 'danger' : 'success'">
              {{ creditPreview.unlimited ? t('salesOrder.unlimitedCustomer') : creditPreview.exceeded ? t('salesOrder.exceededAfterSubmit') : t('salesOrder.sufficientCredit') }}
            </el-tag>
          </div>

          <template v-if="creditPreview">
            <div class="credit-preview-grid">
              <div class="credit-preview-item">
                <div class="preview-label">{{ t('salesOrder.creditLimit') }}</div>
                <div class="preview-value" :class="{ quiet: creditPreview.unlimited }">
                  {{ creditPreview.unlimited ? t('salesOrder.unlimited') : formatMoney(creditPreview.creditLimit) }}
                </div>
              </div>
              <div class="credit-preview-item">
                <div class="preview-label">{{ t('salesOrder.outstandingReceivable') }}</div>
                <div class="preview-value">{{ formatMoney(creditPreview.outstandingReceivable) }}</div>
              </div>
              <div class="credit-preview-item">
                <div class="preview-label">{{ t('salesOrder.openOrderExposure') }}</div>
                <div class="preview-value">{{ formatMoney(creditPreview.openOrderExposure) }}</div>
              </div>
              <div class="credit-preview-item">
                <div class="preview-label">{{ t('salesOrder.currentExposure') }}</div>
                <div class="preview-value">{{ formatMoney(creditPreview.currentExposure) }}</div>
              </div>
              <div class="credit-preview-item">
                <div class="preview-label">{{ t('salesOrder.orderTaxAmount') }}</div>
                <div class="preview-value">{{ formatMoney(creditPreview.orderAmount) }}</div>
              </div>
              <div class="credit-preview-item">
                <div class="preview-label">{{ t('salesOrder.availableAfterSubmit') }}</div>
                <div class="preview-value" :class="{ danger: !creditPreview.unlimited && Number(creditPreview.projectedAvailableCredit ?? 0) < 0 }">
                  {{ creditPreview.unlimited ? t('salesOrder.unlimited') : formatMoney(creditPreview.projectedAvailableCredit) }}
                </div>
              </div>
            </div>

            <el-alert
              v-if="creditPreview.exceeded"
              type="error"
              :closable="false"
              show-icon
              :title="t('salesOrder.expectedExceeded', { amount: formatMoney(creditExceededAmount) })"
              :description="t('salesOrder.exceededDescription', { current: formatMoney(creditPreview.currentExposure), order: formatMoney(creditPreview.orderAmount), projected: formatMoney(creditPreview.projectedExposure), limit: formatMoney(creditPreview.creditLimit) })"
            />
            <el-alert
              v-else-if="creditPreview.unlimited"
              type="info"
              :closable="false"
              show-icon
              :title="t('salesOrder.noCreditLimit')"
              :description="t('salesOrder.unlimitedDescription', { current: formatMoney(creditPreview.currentExposure) })"
            />
            <el-alert
              v-else
              type="success"
              :closable="false"
              show-icon
              :title="t('salesOrder.projectedExposure', { amount: formatMoney(creditPreview.projectedExposure) })"
              :description="t('salesOrder.availableDescription', { amount: formatMoney(creditPreview.projectedAvailableCredit) })"
            />
          </template>
        </div>

        <div class="line-toolbar">
          <span>{{ t('salesOrder.details') }}</span>
          <el-button v-if="!isView" type="primary" :icon="Plus" @click="addLine">{{ t('salesOrder.addLine') }}</el-button>
        </div>

        <el-table :data="formData.items" border>
          <el-table-column :label="t('salesOrder.product')" min-width="240">
            <template #default="{ row }">
              <el-select v-model="row.productId" :placeholder="t('salesOrder.selectProduct')" filterable style="width: 100%" :disabled="isView" @change="onProductChange(row)">
                <el-option
                  v-for="product in products"
                  :key="product.id"
                  :label="`${product.code || product.productCode || product.id} - ${product.name || product.productName || '-'}`"
                  :value="product.id"
                />
              </el-select>
            </template>
          </el-table-column>
                    <el-table-column :label="t('salesOrder.auxQty')" width="130">
            <template #default="{ row, $index }">
              <el-input-number v-model="row.auxQty" :min="0" :precision="4" :controls="false" :disabled="!row.auxUnitName" style="width: 100%" @change="handleAuxQtyChange($index)" />
              <div v-if="row.auxUnitName" class="price-hint">{{ row.auxUnitName }} × {{ row.conversionFactor }}</div>
            </template>
          </el-table-column>
          <el-table-column :label="t('salesOrder.quantity')" width="150">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" :precision="2" :disabled="isView" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column :label="t('salesOrder.unitPrice')" width="150">
            <template #default="{ row }">
              <el-input-number v-model="row.price" :min="0" :precision="2" :disabled="isView" style="width: 100%" />
              <div v-if="row.minPrice != null" class="price-hint">
                {{ t('salesOrder.minimumPrice', { amount: formatMoney(row.minPrice) }) }}
                <span v-if="row.priceLevel">· {{ row.priceLevel === 'CUSTOMER' ? t('salesOrder.customerPrice') : t('salesOrder.generalPrice') }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="t('salesOrder.taxRate')" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.taxRate" :min="0" :max="1" :step="0.01" :precision="4" :disabled="isView" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column :label="t('salesOrder.amount')" width="130" align="right">
            <template #default="{ row }">{{ formatMoney(lineAmount(row)) }}</template>
          </el-table-column>
          <el-table-column :label="t('salesOrder.remark')" min-width="150">
            <template #default="{ row }">
              <el-input v-model="row.remark" :placeholder="t('salesOrder.remark')" :disabled="isView" />
            </template>
          </el-table-column>
          <el-table-column v-if="!isView" :label="t('salesOrder.actions')" width="90" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" :icon="Delete" @click="removeLine($index)">{{ t('salesOrder.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('salesOrder.close') }}</el-button>
        <el-button v-if="!isView" type="primary" :loading="submitLoading" @click="handleSave">{{ t('salesOrder.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import {
  getSalesOrders,
  getSalesOrder,
  createSalesOrder,
  updateSalesOrder,
  previewSalesOrderCredit,
  submitSalesOrder,
  approveSalesOrder,
  rejectSalesOrder,
  unapproveSalesOrder,
  cancelSalesOrder,
  resolveSalesPrice
} from '@/api/sales'
import { getCustomers, getProducts, getWarehouses } from '@/api/masterdata'
import { printSalesOrder } from '@/utils/bizPrint'
import { formatBusinessDate } from '@/utils/locale'
import { useSalesOrderPresentation } from '@/composables/useSalesOrderPresentation'
import { useSalesOrderList } from '@/composables/useSalesOrderList'
import { useSalesOrderForm } from '@/composables/useSalesOrderForm'

const route = useRoute()
const { t } = useI18n()
const readQueryString = (key: string) => {
  const value = route.query[key]
  return Array.isArray(value) ? value[0] || '' : typeof value === 'string' ? value : ''
}

const {
  customers,
  handleApprove,
  handleCancel,
  handlePrint,
  handleQuery,
  handleReject,
  handleReset,
  handleSubmitOrder,
  handleUnapprove,
  loadData,
  loadOptions,
  loading,
  products,
  queryParams,
  tableData,
  total,
  warehouses
} = useSalesOrderList(t, {
  getOrders: getSalesOrders,
  getOrder: getSalesOrder,
  submitOrder: submitSalesOrder,
  approveOrder: approveSalesOrder,
  unapproveOrder: unapproveSalesOrder,
  rejectOrder: rejectSalesOrder,
  cancelOrder: cancelSalesOrder,
  getCustomers,
  getWarehouses,
  getProducts,
  printOrder: printSalesOrder,
  confirm: (message, title, opts) => ElMessageBox.confirm(message, title, opts as any),
  prompt: (message, title, opts) => ElMessageBox.prompt(message, title, opts) as any,
  initialKeyword: readQueryString('keyword'),
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message)
})

const {
  approvalTagType,
  approvalText,
  canApprove,
  canCancel,
  canEdit,
  canReject,
  canSubmit,
  canUnapprove,
  deliveryText,
  formatMoney,
  formatNumber,
  lineAmount,
  statusText
} = useSalesOrderPresentation(t)

const {
  addLine,
  creditExceededAmount,
  creditPreview,
  creditPreviewLoading,
  dialogTitle,
  dialogVisible,
  formData,
  formRef,
  formRules,
  handleAuxQtyChange,
  handleCopy,
  handleCreate,
  handleEdit,
  handleSave,
  handleView,
  isView,
  onCustomerOrDateChange,
  onProductChange,
  removeLine,
  resetForm,
  submitLoading
} = useSalesOrderForm(t, {
  products,
  getOrder: getSalesOrder,
  createOrder: createSalesOrder,
  updateOrder: updateSalesOrder,
  previewCredit: previewSalesOrderCredit,
  resolvePrice: resolveSalesPrice,
  formatBusinessDate,
  formatMoney,
  lineAmount,
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message),
  onWarning: (message) => ElMessage.warning(message),
  onCompleted: () => loadData()
})

onMounted(async () => {
  try {
    await loadOptions()
  } catch (error) {
    console.error(t('salesOrder.message.optionsLoadFailed'), error)
  }
  loadData()
})
</script>

<style scoped lang="scss">
.sales-orders-container {
  padding: 20px;

  .search-card,
  .table-card {
    margin-bottom: 20px;
  }

  .card-header,
  .line-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .line-toolbar {
    margin: 12px 0;
    font-weight: 600;
  }

  .credit-preview-card {
    margin-bottom: 16px;
    padding: 16px;
    border: 1px solid #ebeef5;
    border-radius: 12px;
    background: linear-gradient(135deg, #f8fbff 0%, #fdfefe 100%);
  }

  .credit-preview-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 14px;
  }

  .credit-preview-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }

  .credit-preview-subtitle {
    margin-top: 4px;
    color: #909399;
    font-size: 12px;
  }

  .credit-preview-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
    margin-bottom: 14px;
  }

  .credit-preview-item {
    padding: 12px;
    border-radius: 10px;
    background: #fff;
    border: 1px solid #edf2f7;
  }

  .preview-label {
    color: #909399;
    font-size: 12px;
    margin-bottom: 6px;
  }

  .preview-value {
    color: #303133;
    font-size: 18px;
    font-weight: 600;

    &.danger {
      color: #f56c6c;
    }

    &.quiet {
      color: #67c23a;
    }
  }

  .price-hint {
    margin-top: 2px;
    color: #909399;
    font-size: 12px;
    line-height: 1.3;
  }

  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }

  @media (max-width: 900px) {
    .credit-preview-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }

  @media (max-width: 640px) {
    .credit-preview-header {
      flex-direction: column;
      align-items: stretch;
    }

    .credit-preview-grid {
      grid-template-columns: 1fr;
    }
  }
}
</style>
