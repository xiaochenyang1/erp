<template>
  <div class="sales-deliveries-container">
    <!-- 查询表单 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item :label="t('salesDelivery.deliveryNo')">
          <el-input
            v-model="queryParams.deliveryNo"
            :placeholder="t('salesDelivery.deliveryNoPlaceholder')"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item :label="t('salesDelivery.salesOrder')">
          <el-input
            v-model="queryParams.orderId"
            :placeholder="t('salesDelivery.orderIdPlaceholder')"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item :label="t('salesDelivery.customer')">
          <el-select
            v-model="queryParams.customerId"
            :placeholder="t('salesDelivery.selectCustomer')"
            clearable
            filterable
            style="width: 200px"
          >
            <el-option
              v-for="customer in customers"
              :key="customer.id"
              :label="customer.name"
              :value="customer.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('salesDelivery.statusLabel')">
          <el-select
            v-model="queryParams.status"
            :placeholder="t('salesDelivery.selectStatus')"
            clearable
            style="width: 150px"
          >
            <el-option :label="t('salesDelivery.status.draft')" value="DRAFT" />
            <el-option :label="t('salesDelivery.status.posted')" value="POSTED" />
            <el-option :label="t('salesDelivery.status.cancelled')" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('salesDelivery.logisticsStatus')">
          <el-select
            v-model="queryParams.logisticsStatus"
            :placeholder="t('salesDelivery.selectLogisticsStatus')"
            clearable
            style="width: 160px"
          >
            <el-option :label="t('salesDelivery.logistics.pendingShip')" value="PENDING_SHIP" />
            <el-option :label="t('salesDelivery.logistics.pickedUp')" value="PICKED_UP" />
            <el-option :label="t('salesDelivery.logistics.inTransit')" value="IN_TRANSIT" />
            <el-option :label="t('salesDelivery.logistics.delivered')" value="DELIVERED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('salesDelivery.trackingNo')">
          <el-input
            v-model="queryParams.trackingNo"
            :placeholder="t('salesDelivery.trackingPlaceholder')"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item :label="t('salesDelivery.dateRange')">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            :range-separator="t('salesDelivery.rangeSeparator')"
            :start-placeholder="t('salesDelivery.startDate')"
            :end-placeholder="t('salesDelivery.endDate')"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <el-icon><Search /></el-icon>
            {{ t('salesDelivery.search') }}
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            {{ t('salesDelivery.reset') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮 -->
    <el-card class="toolbar-card" shadow="never">
      <el-button v-permission="'sales:delivery:create'" type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        {{ t('salesDelivery.create') }}
      </el-button>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
      >
        <el-table-column prop="deliveryNo" :label="t('salesDelivery.deliveryNo')" width="180" />
        <el-table-column prop="orderNo" :label="t('salesDelivery.salesOrderNo')" width="180" />
        <el-table-column prop="customerName" :label="t('salesDelivery.customer')" width="150" />
        <el-table-column prop="warehouseName" :label="t('salesDelivery.warehouse')" width="140" />
        <el-table-column prop="deliveryDate" :label="t('salesDelivery.deliveryDate')" width="120" />
        <el-table-column prop="status" :label="t('salesDelivery.statusLabel')" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'DRAFT'" type="info">{{ t('salesDelivery.status.draft') }}</el-tag>
            <el-tag v-else-if="row.status === 'POSTED' || row.status === 'COMPLETED'" type="success">{{ t('salesDelivery.status.posted') }}</el-tag>
            <el-tag v-else-if="row.status === 'CANCELLED'" type="danger">{{ t('salesDelivery.status.cancelled') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="carrierName" :label="t('salesDelivery.carrierName')" width="120" show-overflow-tooltip />
        <el-table-column prop="trackingNo" :label="t('salesDelivery.trackingNo')" width="140" show-overflow-tooltip />
        <el-table-column prop="logisticsStatus" :label="t('salesDelivery.logisticsStatus')" width="130">
          <template #default="{ row }">
            <el-tag :type="logisticsStatusType(row.logisticsStatus)" size="small">
              {{ logisticsStatusLabel(row.logisticsStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('salesDelivery.remark')" show-overflow-tooltip />
        <el-table-column prop="createdBy" :label="t('salesDelivery.createdBy')" width="120" />
        <el-table-column prop="createdAt" :label="t('salesDelivery.createdAt')" width="190">
          <template #default="{ row }">{{ formatLocalizedDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('salesDelivery.actions')" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">
              {{ t('salesDelivery.view') }}
            </el-button>
            <el-button link type="primary" @click="handlePrint(row)">
              {{ t('salesDelivery.print') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'sales:delivery:update'"
              link
              type="primary"
              @click="handleEdit(row)"
            >
              {{ t('salesDelivery.edit') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'sales:delivery:post'"
              link
              type="success"
              @click="handlePost(row)"
            >
              {{ t('salesDelivery.post') }}
            </el-button>
            <el-button v-if="row.status==='POSTED'" v-permission="'sales:delivery:update'" link type="primary" @click="advanceLogistics(row)">{{ t('salesDelivery.advanceLogistics') }}</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'sales:delivery:cancel'"
              link
              type="danger"
              @click="handleCancel(row)"
            >
              {{ t('salesDelivery.cancel') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="queryParams.pageNo"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleQuery"
        @current-change="handleQuery"
      />
    </el-card>

    <!-- 新增/查看对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="85%"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="120px"
      >
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="t('salesDelivery.salesOrder')" prop="orderId">
              <el-select
                v-model="formData.orderId"
                :placeholder="t('salesDelivery.selectOrder')"
                style="width: 100%"
                :disabled="isView || !!editingId"
                @change="handleOrderChange"
                filterable
              >
                <el-option
                  v-for="order in orders"
                  :key="order.id"
                  :label="`${order.orderNo} - ${order.customerName}`"
                  :value="order.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('salesDelivery.warehouse')" prop="warehouseId">
              <el-select
                v-model="formData.warehouseId"
                :placeholder="t('salesDelivery.selectWarehouse')"
                style="width: 100%"
                :disabled="isView"
                @change="handleWarehouseChange"
              >
                <el-option
                  v-for="warehouse in warehouses"
                  :key="warehouse.id"
                  :label="warehouse.name"
                  :value="warehouse.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('salesDelivery.deliveryDate')" prop="deliveryDate">
              <el-date-picker
                v-model="formData.deliveryDate"
                type="date"
                :placeholder="t('salesDelivery.selectDeliveryDate')"
                style="width: 100%"
                value-format="YYYY-MM-DD"
                :disabled="isView"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('salesDelivery.carrierName')">
          <el-input v-model="formData.carrierName" :placeholder="t('salesDelivery.carrierPlaceholder')" :disabled="isView" />
        </el-form-item>
        <el-form-item :label="t('salesDelivery.trackingNo')">
          <el-input v-model="formData.trackingNo" :placeholder="t('salesDelivery.trackingPlaceholder')" :disabled="isView" />
        </el-form-item>
        <el-form-item :label="t('salesDelivery.logisticsStatus')">
          <el-select v-model="formData.logisticsStatus" style="width:100%" :disabled="isView">
            <el-option :label="t('salesDelivery.logistics.pendingShip')" value="PENDING_SHIP" />
            <el-option :label="t('salesDelivery.logistics.pickedUp')" value="PICKED_UP" />
            <el-option :label="t('salesDelivery.logistics.inTransit')" value="IN_TRANSIT" />
            <el-option :label="t('salesDelivery.logistics.delivered')" value="DELIVERED" disabled />
          </el-select>
        </el-form-item>
        <el-descriptions
          v-if="isView && (formData.logisticsStatus === 'DELIVERED' || formData.deliveredBy || formData.deliveryProofAttachmentId)"
          :column="3"
          border
          class="delivery-acceptance"
        >
          <el-descriptions-item :label="t('salesDelivery.deliveredBy')">
            {{ formData.deliveredBy || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('salesDelivery.deliveredTime')">
            {{ formData.deliveredTime ? formatLocalizedDateTime(formData.deliveredTime) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('salesDelivery.deliveryProof')">
            <el-button
              v-if="formData.deliveryProofAttachmentId"
              link
              type="primary"
              @click="downloadDeliveryProof(formData)"
            >
              {{ t('salesDelivery.downloadProof') }}
            </el-button>
            <span v-else>-</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-form-item :label="t('salesDelivery.remark')">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="2"
            :placeholder="t('salesDelivery.remarkPlaceholder')"
            :disabled="isView"
          />
        </el-form-item>

        <!-- 发货明细 -->
        <el-divider content-position="left">{{ t('salesDelivery.details') }}</el-divider>
        <div v-if="!isView && formData.items.length > 0" class="scan-toolbar">
          <BarcodeScanField :disabled="scanLoading" @scan="handleBarcodeScan" />
          <el-button class="scan-toolbar__reset" :disabled="scanLoading" @click="resetScanQuantities">
            <el-icon><RefreshLeft /></el-icon>
            {{ t('salesDelivery.clearQuantity') }}
          </el-button>
          <div class="scan-toolbar__summary" aria-live="polite">
            <span>{{ t('salesDelivery.currentQuantity') }} <strong>{{ deliveryQuantityTotal }}</strong></span>
            <span v-if="scanFeedback" class="scan-toolbar__feedback">{{ scanFeedback }}</span>
          </div>
        </div>
        <el-table :data="formData.items" border max-height="400">
          <el-table-column :label="t('salesDelivery.productCode')" prop="productCode" width="150" />
          <el-table-column :label="t('salesDelivery.productName')" prop="productName" width="180" />
          <el-table-column :label="t('salesDelivery.orderedQuantity')" prop="orderedQuantity" width="120" align="right">
            <template #default="{ row }">
              {{ row.orderedQuantity || 0 }}
            </template>
          </el-table-column>
          <el-table-column :label="t('salesDelivery.deliveredQuantity')" prop="deliveredQuantity" width="130" align="right">
            <template #default="{ row }">
              {{ row.deliveredQuantity || 0 }}
            </template>
          </el-table-column>
          <el-table-column :label="t('salesDelivery.currentDeliveryQuantity')" prop="quantity" width="150">
            <template #default="{ row }">
              <el-input-number
                v-model="row.quantity"
                :min="0"
                :max="getDeliveryMaximum(row)"
                :precision="2"
                :disabled="isView"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column :label="t('salesDelivery.auxQty')" width="120" align="center">
            <template #default="{ row }">
              {{ formatAuxQuantity(row.quantity ?? row.qty, row.conversionFactor, row.auxUnitName) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('salesDelivery.location')" width="160">
            <template #default="{ row }">
              <el-select
                v-model="row.locationId"
                clearable
                filterable
                :placeholder="t('salesDelivery.selectLocation')"
                :disabled="isView"
                style="width: 100%"
              >
                <el-option
                  v-for="location in locationsForWarehouse"
                  :key="location.id"
                  :label="`${location.locationCode} ${location.locationName}`"
                  :value="location.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="t('salesDelivery.serialNos')" min-width="220">
            <template #default="{ row }">
              <el-input
                v-model="row.serialNos"
                type="textarea"
                :rows="row.serialControlled ? 2 : 1"
                :placeholder="row.serialControlled
                  ? t('salesDelivery.serialNosPlaceholder')
                  : t('salesDelivery.remarkPlaceholder')"
                :disabled="isView || row.serialControlled === false"
              />
              <div
                v-if="row.serialControlled"
                class="serial-progress"
                :class="{ 'serial-progress--ok': serialCaptureProgress(row.serialNos, row.quantity ?? row.qty).complete }"
              >
                {{ t('salesDelivery.serialProgress', serialCaptureProgress(row.serialNos, row.quantity ?? row.qty)) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="t('salesDelivery.lotNo')" width="140">
            <template #default="{ row }">
              <el-input
                v-model="row.lotNo"
                :placeholder="row.lotControlled
                  ? t('salesDelivery.lotNoPlaceholder')
                  : t('salesDelivery.remarkPlaceholder')"
                :disabled="isView || row.lotControlled === false"
              />
            </template>
          </el-table-column>
          <el-table-column :label="t('salesDelivery.productionDate')" width="150">
            <template #default="{ row }">
              <el-date-picker
                v-model="row.productionDate"
                type="date"
                value-format="YYYY-MM-DD"
                :placeholder="t('salesDelivery.productionDatePlaceholder')"
                :disabled="isView || row.lotControlled === false"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column :label="t('salesDelivery.expiryDate')" width="150">
            <template #default="{ row }">
              <el-date-picker
                v-model="row.expiryDate"
                type="date"
                value-format="YYYY-MM-DD"
                :placeholder="row.shelfLifeControlled
                  ? t('salesDelivery.expiryDatePlaceholder')
                  : t('salesDelivery.remarkPlaceholder')"
                :disabled="isView || (row.shelfLifeControlled === false && row.lotControlled === false)"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column :label="t('salesDelivery.remark')" prop="remark">
            <template #default="{ row }">
              <el-input
                v-model="row.remark"
                :placeholder="t('salesDelivery.remarkPlaceholder')"
                :disabled="isView"
              />
            </template>
          </el-table-column>
        </el-table>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('salesDelivery.cancel') }}</el-button>
        <el-button v-if="!isView" type="primary" :loading="submitLoading" @click="handleSubmit">
          {{ t('salesDelivery.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RefreshLeft } from '@element-plus/icons-vue'
import {
  getSalesDeliveries,
  getSalesDelivery,
  createSalesDelivery,
  updateSalesDelivery,
  cancelSalesDelivery,
  postSalesDelivery,
  updateSalesDeliveryLogistics,
  uploadSalesDeliveryAttachment
} from '@/api/sales'
import { downloadAttachment } from '@/api/attachment'
import { downloadBlob } from '@/utils/download'
import { printSalesDelivery } from '@/utils/bizPrint'
import { getSalesOrders, getSalesOrder } from '@/api/sales'
import {
  getCustomers,
  getLocations,
  getProduct,
  getProductByBarcode,
  getWarehouses
} from '@/api/masterdata'
import { BarcodeScanField } from '@/components/common'
import {
  formatAuxQuantity,
  serialCaptureProgress
} from '@/utils/productLines'
import { formatBusinessDate, formatLocalizedDateTime } from '@/utils/locale'
import { useSalesDeliveryPresentation } from '@/composables/useSalesDeliveryPresentation'
import { useSalesDeliveryList } from '@/composables/useSalesDeliveryList'
import { useSalesDeliveryForm } from '@/composables/useSalesDeliveryForm'

const route = useRoute()
const { t } = useI18n()
const readQueryString = (key: string) => {
  const value = route.query[key]
  return Array.isArray(value) ? value[0] || '' : typeof value === 'string' ? value : ''
}

const {
  advanceLogistics,
  customers,
  dateRange,
  handleCancel,
  handlePost,
  handlePrint,
  handleQuery,
  handleReset,
  loadData,
  loadLocations,
  loadOptions,
  loading,
  locations,
  orders,
  queryParams,
  tableData,
  total,
  warehouses
} = useSalesDeliveryList(t, {
  getDeliveries: getSalesDeliveries,
  getDelivery: getSalesDelivery,
  cancelDelivery: cancelSalesDelivery,
  postDelivery: postSalesDelivery,
  updateLogistics: updateSalesDeliveryLogistics,
  uploadDeliveryAttachment: uploadSalesDeliveryAttachment,
  getCustomers,
  getWarehouses,
  getOrders: getSalesOrders,
  getLocations,
  printDelivery: printSalesDelivery,
  confirm: (message, title, opts) => ElMessageBox.confirm(message, title, opts as any),
  initialDeliveryNo: readQueryString('keyword'),
  initialLogisticsStatus: readQueryString('logisticsStatus'),
  initialTrackingNo: readQueryString('trackingNo'),
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message),
  onInfo: (message) => ElMessage.info(message),
  prompt: (message, title, opts) => ElMessageBox.prompt(message, title, opts as any)
})

const {
  deliveryQuantityTotal,
  dialogTitle,
  dialogVisible,
  editingId,
  formData,
  formRef,
  formRules,
  getDeliveryMaximum,
  handleBarcodeScan,
  handleCreate,
  handleEdit,
  handleOrderChange,
  handleSubmit,
  handleView,
  handleWarehouseChange,
  isView,
  resetScanQuantities,
  scanFeedback,
  scanLoading,
  submitLoading
} = useSalesDeliveryForm(t, {
  orders,
  getDelivery: getSalesDelivery,
  getOrder: getSalesOrder,
  createDelivery: createSalesDelivery,
  updateDelivery: updateSalesDelivery,
  loadProduct: getProduct,
  loadProductByBarcode: getProductByBarcode,
  loadLocations,
  formatBusinessDate,
  confirm: (message, title, opts) => ElMessageBox.confirm(message, title, opts as any),
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message),
  onWarning: (message) => ElMessage.warning(message),
  onCompleted: () => loadData()
})

const {
  logisticsStatusLabel,
  logisticsStatusType,
  locationsForWarehouse
} = useSalesDeliveryPresentation(t, locations, () => formData.warehouseId)

const downloadDeliveryProof = async (delivery: { deliveryProofAttachmentId?: string | number; deliveryNo?: string }) => {
  if (!delivery.deliveryProofAttachmentId) return
  try {
    const blob = await downloadAttachment(delivery.deliveryProofAttachmentId)
    downloadBlob(blob, `${delivery.deliveryNo || 'sales-delivery'}-delivery-proof`)
  } catch {
    ElMessage.error(t('salesDelivery.message.deliveryProofDownloadFailed'))
  }
}

onMounted(async () => {
  await loadOptions()
  loadData()
})
</script>

<style scoped lang="scss">
.sales-deliveries-container {
  padding: 20px;

  .search-card,
  .toolbar-card,
  .table-card {
    margin-bottom: 20px;
  }

  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }
}

.scan-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 44px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.scan-toolbar__reset {
  min-height: 40px;
}

.scan-toolbar__summary {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 132px;
  color: #606266;
  font-size: 13px;
  line-height: 1.4;
}

.scan-toolbar__summary strong,
.scan-toolbar__feedback {
  font-variant-numeric: tabular-nums;
}

.scan-toolbar__feedback {
  color: #067647;
}

@media (max-width: 720px) {
  .scan-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .scan-toolbar__reset {
    width: 100%;
  }
}

.serial-progress {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.2;
}
.serial-progress--ok {
  color: var(--el-color-success);
}
</style>
