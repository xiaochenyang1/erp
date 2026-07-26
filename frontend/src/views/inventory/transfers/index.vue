<template>
  <div class="inventory-transfers-container">
    <!-- 查询表单 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item :label="$t('inventoryTransfers.transferNo')">
          <el-input
            v-model="queryParams.transferNo"
            :placeholder="$t('inventoryTransfers.placeholder.transferNo')"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item :label="$t('inventoryTransfers.fromWarehouse')">
          <el-select
            v-model="queryParams.fromWarehouseId"
            :placeholder="$t('inventoryTransfers.placeholder.fromWarehouse')"
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="warehouse in warehouses"
              :key="warehouse.id"
              :label="warehouseLabel(warehouse)"
              :value="warehouse.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventoryTransfers.toWarehouse')">
          <el-select
            v-model="queryParams.toWarehouseId"
            :placeholder="$t('inventoryTransfers.placeholder.toWarehouse')"
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="warehouse in warehouses"
              :key="warehouse.id"
              :label="warehouseLabel(warehouse)"
              :value="warehouse.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventoryTransfers.statusLabel')">
          <el-select
            v-model="queryParams.status"
            :placeholder="$t('inventoryTransfers.placeholder.status')"
            clearable
            style="width: 150px"
          >
            <el-option :label="$t('inventoryTransfers.status.draft')" value="DRAFT" />
            <el-option :label="$t('inventoryTransfers.status.completed')" value="COMPLETED" />
            <el-option :label="$t('inventoryTransfers.status.cancelled')" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventoryTransfers.dateRange')">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            :range-separator="$t('inventoryTransfers.rangeSeparator')"
            :start-placeholder="$t('inventoryTransfers.placeholder.startDate')"
            :end-placeholder="$t('inventoryTransfers.placeholder.endDate')"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <el-icon><Search /></el-icon>
            {{ $t('inventoryTransfers.action.search') }}
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            {{ $t('inventoryTransfers.action.reset') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮 -->
    <el-card class="toolbar-card" shadow="never">
      <el-button v-permission="'inventory:transfer:create'" type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        {{ $t('inventoryTransfers.action.create') }}
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
        <el-table-column prop="transferNo" :label="$t('inventoryTransfers.transferNo')" width="180" />
        <el-table-column prop="fromWarehouseName" :label="$t('inventoryTransfers.fromWarehouse')" width="140" />
        <el-table-column prop="toWarehouseName" :label="$t('inventoryTransfers.toWarehouse')" width="140" />
        <el-table-column prop="transferDate" :label="$t('inventoryTransfers.transferDate')" width="120" />
        <el-table-column prop="status" :label="$t('inventoryTransfers.statusLabel')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="$t('inventoryTransfers.remark')" show-overflow-tooltip />
        <el-table-column prop="createdBy" :label="$t('inventoryTransfers.createdBy')" width="120" />
        <el-table-column prop="createdAt" :label="$t('inventoryTransfers.createdTime')" width="160" />
        <el-table-column :label="$t('inventoryTransfers.actions')" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">
              {{ $t('inventoryTransfers.action.view') }}
            </el-button>
            <el-button link type="primary" @click="handlePrint(row)">
              {{ $t('inventoryTransfers.action.print') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'inventory:transfer:post'"
              link
              type="success"
              @click="handlePost(row)"
            >
              {{ $t('inventoryTransfers.action.post') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'inventory:transfer:cancel'"
              link
              type="danger"
              @click="handleCancel(row)"
            >
              {{ $t('inventoryTransfers.action.cancel') }}
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
        @current-change="handlePageChange"
      />
    </el-card>

    <!-- 新增/查看对话框 -->
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
            <el-form-item :label="$t('inventoryTransfers.fromWarehouse')" prop="fromWarehouseId">
              <el-select
                v-model="formData.fromWarehouseId"
                :placeholder="$t('inventoryTransfers.placeholder.fromWarehouse')"
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
            <el-form-item :label="$t('inventoryTransfers.toWarehouse')" prop="toWarehouseId">
              <el-select
                v-model="formData.toWarehouseId"
                :placeholder="$t('inventoryTransfers.placeholder.toWarehouse')"
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
            <el-form-item :label="$t('inventoryTransfers.transferDate')" prop="transferDate">
              <el-date-picker
                v-model="formData.transferDate"
                type="date"
                :placeholder="$t('inventoryTransfers.placeholder.transferDate')"
                style="width: 100%"
                value-format="YYYY-MM-DD"
                :disabled="isView"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$t('inventoryTransfers.remark')">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="2"
            :placeholder="$t('inventoryTransfers.placeholder.remark')"
            :disabled="isView"
          />
        </el-form-item>

        <!-- 调拨明细 -->
        <el-divider content-position="left">{{ $t('inventoryTransfers.details') }}</el-divider>
        <el-button
          v-if="!isView"
          type="primary"
          size="small"
          @click="handleAddItem"
          style="margin-bottom: 10px"
        >
          <el-icon><Plus /></el-icon>
          {{ $t('inventoryTransfers.action.addProduct') }}
        </el-button>
        <el-table :data="formData.items" border>
          <el-table-column :label="$t('inventoryTransfers.product')" prop="productId" width="250">
            <template #default="{ row, $index }">
              <el-select
                v-model="row.productId"
                :placeholder="$t('inventoryTransfers.placeholder.product')"
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
          <el-table-column :label="$t('inventoryTransfers.productCode')" prop="productCode" width="150" />
          <el-table-column :label="$t('inventoryTransfers.productName')" prop="productName" width="150" />
          <el-table-column :label="$t('inventoryTransfers.transferQuantity')" prop="quantity" width="150">
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
          <el-table-column :label="$t('inventoryTransfers.fromLocation')" width="150">
            <template #default="{ row }">
              <el-select
                v-model="row.fromLocationId"
                clearable
                filterable
                :placeholder="$t('inventoryTransfers.placeholder.fromLocation')"
                :disabled="isView"
                style="width: 100%"
              >
                <el-option
                  v-for="location in locationsForFromWarehouse"
                  :key="location.id"
                  :label="locationLabel(location)"
                  :value="location.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$t('inventoryTransfers.toLocation')" width="150">
            <template #default="{ row }">
              <el-select
                v-model="row.toLocationId"
                clearable
                filterable
                :placeholder="$t('inventoryTransfers.placeholder.toLocation')"
                :disabled="isView"
                style="width: 100%"
              >
                <el-option
                  v-for="location in locationsForToWarehouse"
                  :key="location.id"
                  :label="locationLabel(location)"
                  :value="location.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$t('inventoryTransfers.serialNos')" min-width="220">
            <template #default="{ row }">
              <el-input
                v-model="row.serialNos"
                type="textarea"
                :rows="row.serialControlled ? 2 : 1"
                :placeholder="row.serialControlled
                  ? $t('inventoryTransfers.placeholder.serialNos')
                  : $t('inventoryTransfers.placeholder.remark')"
                :disabled="isView || row.serialControlled === false"
              />
              <div
                v-if="row.serialControlled"
                class="serial-progress"
                :class="{ 'serial-progress--ok': serialCaptureProgress(row.serialNos, row.quantity).complete }"
              >
                {{ $t('inventoryTransfers.serialProgress', serialCaptureProgress(row.serialNos, row.quantity)) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="$t('inventoryTransfers.lotNo')" width="130">
            <template #default="{ row }">
              <el-input
                v-model="row.lotNo"
                :placeholder="row.lotControlled
                  ? $t('inventoryTransfers.placeholder.lotNo')
                  : $t('inventoryTransfers.placeholder.remark')"
                :disabled="isView || row.lotControlled === false"
              />
            </template>
          </el-table-column>
          <el-table-column :label="$t('inventoryTransfers.productionDate')" width="150">
            <template #default="{ row }">
              <el-date-picker
                v-model="row.productionDate"
                type="date"
                value-format="YYYY-MM-DD"
                :placeholder="$t('inventoryTransfers.placeholder.productionDate')"
                :disabled="isView || row.lotControlled === false"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column :label="$t('inventoryTransfers.expiryDate')" width="150">
            <template #default="{ row }">
              <el-date-picker
                v-model="row.expiryDate"
                type="date"
                value-format="YYYY-MM-DD"
                :placeholder="row.shelfLifeControlled
                  ? $t('inventoryTransfers.placeholder.expiryDate')
                  : $t('inventoryTransfers.placeholder.remark')"
                :disabled="isView || (row.shelfLifeControlled === false && row.lotControlled === false)"
                style="width: 100%"
              />
            </template>
          </el-table-column>

          <el-table-column :label="$t('inventoryTransfers.remark')" prop="remark">
            <template #default="{ row }">
              <el-input
                v-model="row.remark"
                :placeholder="$t('inventoryTransfers.placeholder.remark')"
                :disabled="isView"
              />
            </template>
          </el-table-column>
          <el-table-column v-if="!isView" :label="$t('inventoryTransfers.actions')" width="80" fixed="right">
            <template #default="{ $index }">
              <el-button
                link
                type="danger"
                @click="handleDeleteItem($index)"
              >
                {{ $t('inventoryTransfers.action.delete') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('inventoryTransfers.action.cancel') }}</el-button>
        <el-button v-if="!isView" type="primary" :loading="submitLoading" @click="submitForm">
          {{ $t('inventoryTransfers.action.confirm') }}
        </el-button>
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
  getInventoryTransfers,
  getInventoryTransfer,
  createInventoryTransfer,
  shipInventoryTransfer,
  cancelInventoryTransfer
} from '@/api/inventory'
import { getLocations, getProducts, getWarehouses } from '@/api/masterdata'
import { serialCaptureProgress } from '@/utils/productLines'
import { printInventoryTransfer } from '@/utils/bizPrint'
import { useInventoryTransferList } from '@/composables/useInventoryTransferList'
import { useInventoryTransferFormPresentation } from '@/composables/useInventoryTransferPresentation'
import { useInventoryTransferForm } from '@/composables/useInventoryTransferForm'

const { t } = useI18n()

const formRef = ref<FormInstance>()

const {
  dateRange,
  handleCancel,
  handlePageChange,
  handlePost,
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
} = useInventoryTransferList(t, {
  getTransfers: getInventoryTransfers,
  getTransfer: getInventoryTransfer,
  postTransfer: shipInventoryTransfer,
  cancelTransfer: cancelInventoryTransfer,
  getWarehouses,
  getProducts,
  getLocations,
  printTransfer: printInventoryTransfer,
  confirm: (message, title, confirmOptions) =>
    ElMessageBox.confirm(message, title, confirmOptions as any),
  decorateRows: (rows) => withWarehouseNames(rows),
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
  selectedFromWarehouseId,
  selectedToWarehouseId,
  submitLoading
} = useInventoryTransferForm(t, {
  getTransfer: getInventoryTransfer,
  createTransfer: createInventoryTransfer,
  findProduct: (productId) => findProduct(productId),
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message),
  onWarning: (message) => ElMessage.warning(message),
  onSubmitted: loadData
})

const {
  findProduct,
  locationLabel,
  locationsForFromWarehouse,
  locationsForToWarehouse,
  productLabel,
  statusLabel,
  statusTagType,
  warehouseLabel,
  withWarehouseNames
} = useInventoryTransferFormPresentation(
  t,
  { warehouses, products, locations },
  { fromWarehouseId: selectedFromWarehouseId, toWarehouseId: selectedToWarehouseId }
)

const formRules: FormRules = {
  fromWarehouseId: [{ required: true, message: t('inventoryTransfers.validation.fromWarehouse'), trigger: 'change' }],
  toWarehouseId: [{ required: true, message: t('inventoryTransfers.validation.toWarehouse'), trigger: 'change' }],
  transferDate: [{ required: true, message: t('inventoryTransfers.validation.transferDate'), trigger: 'change' }]
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

<style scoped lang="scss">
.inventory-transfers-container {
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
