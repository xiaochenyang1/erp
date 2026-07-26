<template>
  <div class="inventory-adjustments-container page-container">
    <!-- 查询表单 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item :label="$t('inventoryAdjustments.adjustmentNo')">
          <el-input
            v-model="queryParams.adjustmentNo"
            :placeholder="$t('inventoryAdjustments.placeholder.adjustmentNo')"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item :label="$t('inventoryAdjustments.warehouse')">
          <el-select
            v-model="queryParams.warehouseId"
            :placeholder="$t('inventoryAdjustments.placeholder.warehouse')"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="warehouse in warehouses"
              :key="warehouse.id"
              :label="warehouseLabel(warehouse)"
              :value="warehouse.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventoryAdjustments.adjustmentType')">
          <el-select
            v-model="queryParams.type"
            :placeholder="$t('inventoryAdjustments.placeholder.adjustmentType')"
            clearable
            style="width: 150px"
          >
            <el-option :label="$t('inventoryAdjustments.type.gain')" value="GAIN" />
            <el-option :label="$t('inventoryAdjustments.type.loss')" value="LOSS" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventoryAdjustments.statusLabel')">
          <el-select
            v-model="queryParams.status"
            :placeholder="$t('inventoryAdjustments.placeholder.status')"
            clearable
            style="width: 150px"
          >
            <el-option :label="$t('inventoryAdjustments.status.draft')" value="DRAFT" />
            <el-option :label="$t('inventoryAdjustments.status.completed')" value="POSTED" />
            <el-option :label="$t('inventoryAdjustments.status.cancelled')" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventoryAdjustments.dateRange')">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            :range-separator="$t('inventoryAdjustments.rangeSeparator')"
            :start-placeholder="$t('inventoryAdjustments.placeholder.startDate')"
            :end-placeholder="$t('inventoryAdjustments.placeholder.endDate')"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <el-icon><Search /></el-icon>
            {{ $t('inventoryAdjustments.action.search') }}
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            {{ $t('inventoryAdjustments.action.reset') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮 -->
    <el-card class="toolbar-card" shadow="never">
      <div class="btn-group">
        <el-button v-permission="'inventory:adjustment:create'" type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          {{ $t('inventoryAdjustments.action.create') }}
        </el-button>
      </div>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <!-- 骨架屏 -->
      <div v-if="loading && tableData.length === 0" class="skeleton-wrapper">
        <el-skeleton :rows="8" animated />
      </div>

      <!-- 数据表格 -->
      <el-table
        v-else
        v-loading="loading"
        :data="tableData"
        border
        stripe
      >
        <el-table-column prop="adjustmentNo" :label="$t('inventoryAdjustments.adjustmentNo')" width="180" />
        <el-table-column prop="warehouseName" :label="$t('inventoryAdjustments.warehouse')" width="150" />
        <el-table-column prop="adjustmentDate" :label="$t('inventoryAdjustments.adjustmentDate')" width="120" />
        <el-table-column prop="type" :label="$t('inventoryAdjustments.adjustmentType')" width="100">
          <template #default="{ row }">
            <el-tag :type="typeTagType(row.type)">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('inventoryAdjustments.statusLabel')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="$t('inventoryAdjustments.remark')" show-overflow-tooltip />
        <el-table-column prop="createdBy" :label="$t('inventoryAdjustments.createdBy')" width="120" />
        <el-table-column prop="createdAt" :label="$t('inventoryAdjustments.createdTime')" width="160" />
        <el-table-column :label="$t('inventoryAdjustments.actions')" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">
              {{ $t('inventoryAdjustments.action.view') }}
            </el-button>
            <el-button link type="primary" @click="handlePrint(row)">
              {{ $t('inventoryAdjustments.action.print') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'inventory:adjustment:post'"
              link
              type="success"
              @click="handleComplete(row)"
            >
              {{ $t('inventoryAdjustments.action.post') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'inventory:adjustment:cancel'"
              link
              type="danger"
              @click="handleCancel(row)"
            >
              {{ $t('inventoryAdjustments.action.cancel') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空状态 -->
      <el-empty
        v-if="!loading && tableData.length === 0"
        :description="$t('inventoryAdjustments.empty')"
        :image-size="120"
      />

      <!-- 分页 -->
      <el-pagination
        v-if="tableData.length > 0"
        v-model:current-page="queryParams.pageNo"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleQuery"
        @current-change="handlePageChange"
      />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="80%"
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
            <el-form-item :label="$t('inventoryAdjustments.warehouse')" prop="warehouseId">
              <el-select
                v-model="formData.warehouseId"
                :placeholder="$t('inventoryAdjustments.placeholder.warehouse')"
                style="width: 100%"
                :disabled="isView"
              >
                <el-option
                  v-for="warehouse in warehouses"
                  :key="warehouse.id"
                  :label="warehouseLabel(warehouse)"
                  :value="warehouse.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('inventoryAdjustments.adjustmentDate')" prop="adjustmentDate">
              <el-date-picker
                v-model="formData.adjustmentDate"
                type="date"
                :placeholder="$t('inventoryAdjustments.placeholder.adjustmentDate')"
                style="width: 100%"
                value-format="YYYY-MM-DD"
                :disabled="isView"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('inventoryAdjustments.adjustmentType')" prop="type">
              <el-select
                v-model="formData.type"
                :placeholder="$t('inventoryAdjustments.placeholder.adjustmentType')"
                style="width: 100%"
                :disabled="isView"
              >
                <el-option :label="$t('inventoryAdjustments.type.gain')" value="GAIN" />
                <el-option :label="$t('inventoryAdjustments.type.loss')" value="LOSS" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$t('inventoryAdjustments.remark')">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="2"
            :placeholder="$t('inventoryAdjustments.placeholder.remark')"
            :disabled="isView"
          />
        </el-form-item>

        <!-- 调整明细 -->
        <el-divider content-position="left">{{ $t('inventoryAdjustments.details') }}</el-divider>
        <el-button
          v-if="!isView"
          type="primary"
          size="small"
          @click="handleAddItem"
          class="mb-sm"
        >
          <el-icon><Plus /></el-icon>
          {{ $t('inventoryAdjustments.action.addProduct') }}
        </el-button>
        <el-table :data="formData.items" border>
          <el-table-column :label="$t('inventoryAdjustments.product')" prop="productId" width="250">
            <template #default="{ row, $index }">
              <el-select
                v-model="row.productId"
                :placeholder="$t('inventoryAdjustments.placeholder.product')"
                filterable
                style="width: 100%"
                :disabled="isView"
                @change="handleProductChange($index)"
              >
                <el-option
                  v-for="product in products"
                  :key="product.id"
                  :label="productLabel(product)"
                  :value="product.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$t('inventoryAdjustments.productCode')" prop="productCode" width="150" />
          <el-table-column :label="$t('inventoryAdjustments.productName')" prop="productName" width="150" />
          <el-table-column :label="$t('inventoryAdjustments.adjustmentQuantity')" prop="quantity" width="150">
            <template #default="{ row }">
              <el-input-number
                v-model="row.quantity"
                :min="0"
                :precision="2"
                :disabled="isView"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column :label="$t('inventoryAdjustments.location')" width="160">
            <template #default="{ row }">
              <el-select
                v-model="row.locationId"
                clearable
                filterable
                :placeholder="$t('inventoryAdjustments.placeholder.location')"
                :disabled="isView"
                style="width: 100%"
              >
                <el-option
                  v-for="location in locationsForSelectedWarehouse"
                  :key="location.id"
                  :label="locationLabel(location)"
                  :value="location.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$t('inventoryAdjustments.serialNos')" min-width="220">
            <template #default="{ row }">
              <el-input
                v-model="row.serialNos"
                type="textarea"
                :rows="row.serialControlled ? 2 : 1"
                :placeholder="row.serialControlled
                  ? $t('inventoryAdjustments.placeholder.serialNos')
                  : $t('inventoryAdjustments.placeholder.remark')"
                :disabled="isView || row.serialControlled === false"
              />
              <div
                v-if="row.serialControlled"
                class="serial-progress"
                :class="{ 'serial-progress--ok': serialCaptureProgress(row.serialNos, row.quantity).complete }"
              >
                {{ $t('inventoryAdjustments.serialProgress', serialCaptureProgress(row.serialNos, row.quantity)) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="$t('inventoryAdjustments.lotNo')" width="130">
            <template #default="{ row }">
              <el-input
                v-model="row.lotNo"
                :placeholder="row.lotControlled
                  ? $t('inventoryAdjustments.placeholder.lotNo')
                  : $t('inventoryAdjustments.placeholder.remark')"
                :disabled="isView || row.lotControlled === false"
              />
            </template>
          </el-table-column>
          <el-table-column :label="$t('inventoryAdjustments.productionDate')" width="150">
            <template #default="{ row }">
              <el-date-picker
                v-model="row.productionDate"
                type="date"
                value-format="YYYY-MM-DD"
                :placeholder="$t('inventoryAdjustments.placeholder.productionDate')"
                :disabled="isView || row.lotControlled === false"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column :label="$t('inventoryAdjustments.expiryDate')" width="150">
            <template #default="{ row }">
              <el-date-picker
                v-model="row.expiryDate"
                type="date"
                value-format="YYYY-MM-DD"
                :placeholder="row.shelfLifeControlled
                  ? $t('inventoryAdjustments.placeholder.expiryDate')
                  : $t('inventoryAdjustments.placeholder.remark')"
                :disabled="isView || (row.shelfLifeControlled === false && row.lotControlled === false)"
                style="width: 100%"
              />
            </template>
          </el-table-column>

          <el-table-column :label="$t('inventoryAdjustments.reason')" prop="reason">
            <template #default="{ row }">
              <el-input
                v-model="row.reason"
                :placeholder="$t('inventoryAdjustments.placeholder.reason')"
                :disabled="isView"
              />
            </template>
          </el-table-column>
          <el-table-column v-if="!isView" :label="$t('inventoryAdjustments.actions')" width="80" fixed="right">
            <template #default="{ $index }">
              <el-button
                link
                type="danger"
                @click="handleDeleteItem($index)"
              >
                {{ $t('inventoryAdjustments.action.delete') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>

      <template #footer>
        <div class="btn-group">
          <el-button @click="dialogVisible = false">{{ $t('inventoryAdjustments.action.cancel') }}</el-button>
          <el-button v-if="!isView" type="primary" :loading="submitLoading" @click="submitForm">
            {{ $t('inventoryAdjustments.action.confirm') }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getInventoryAdjustments,
  getInventoryAdjustment,
  createInventoryAdjustment,
  completeInventoryAdjustment,
  cancelInventoryAdjustment
} from '@/api/inventory'
import { getLocations, getProducts, getWarehouses } from '@/api/masterdata'
import { serialCaptureProgress } from '@/utils/productLines'
import { printInventoryAdjustment } from '@/utils/bizPrint'
import { useInventoryAdjustmentList } from '@/composables/useInventoryAdjustmentList'
import { useInventoryAdjustmentFormPresentation } from '@/composables/useInventoryAdjustmentPresentation'
import { useInventoryAdjustmentForm } from '@/composables/useInventoryAdjustmentForm'

const { t } = useI18n()

const formRef = ref<FormInstance>()

const {
  dateRange,
  handleCancel,
  handleComplete,
  handlePageChange,
  handlePrint,
  handleQuery,
  handleReset,
  loadData,
  loadOptions,
  loading,
  locations,
  products,
  queryParams,
  tableData,
  total,
  warehouses
} = useInventoryAdjustmentList(t, {
  getAdjustments: getInventoryAdjustments,
  getAdjustment: getInventoryAdjustment,
  postAdjustment: completeInventoryAdjustment,
  cancelAdjustment: cancelInventoryAdjustment,
  getWarehouses,
  getProducts,
  getLocations,
  printAdjustment: printInventoryAdjustment,
  confirm: (message, title, confirmOptions) =>
    ElMessageBox.confirm(message, title, confirmOptions as any),
  decorateRows: (rows) => withWarehouseName(rows),
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message)
})

const {
  dialogTitle,
  dialogVisible,
  formData,
  handleAddItem,
  handleCreate,
  handleDeleteItem,
  handleProductChange,
  handleSubmit,
  handleView,
  isView,
  selectedWarehouseId,
  submitLoading
} = useInventoryAdjustmentForm(t, {
  getAdjustment: getInventoryAdjustment,
  createAdjustment: createInventoryAdjustment,
  findProduct: (productId) => findProduct(productId),
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message),
  onWarning: (message) => ElMessage.warning(message),
  onSubmitted: loadData
})

const {
  findProduct,
  locationLabel,
  locationsForSelectedWarehouse,
  productLabel,
  statusLabel,
  statusTagType,
  typeLabel,
  typeTagType,
  warehouseLabel,
  withWarehouseName
} = useInventoryAdjustmentFormPresentation(
  t,
  { warehouses, products, locations },
  selectedWarehouseId
)

const formRules: FormRules = {
  warehouseId: [{ required: true, message: t('inventoryAdjustments.validation.warehouse'), trigger: 'change' }],
  adjustmentDate: [{ required: true, message: t('inventoryAdjustments.validation.adjustmentDate'), trigger: 'change' }],
  type: [{ required: true, message: t('inventoryAdjustments.validation.adjustmentType'), trigger: 'change' }]
}

watch(dialogVisible, (visible) => {
  if (!visible) formRef.value?.clearValidate()
})

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    await handleSubmit()
  })
}

onMounted(async () => {
  await loadOptions()
  await loadData()
})
</script>

<style scoped>
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
