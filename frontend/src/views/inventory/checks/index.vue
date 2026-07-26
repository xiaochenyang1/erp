<template>
  <div class="inventory-checks-container">
    <!-- 查询表单 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item :label="$t('inventoryChecks.checkNo')">
          <el-input
            v-model="queryParams.checkNo"
            :placeholder="$t('inventoryChecks.placeholder.checkNo')"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item :label="$t('inventoryChecks.warehouse')">
          <el-select
            v-model="queryParams.warehouseId"
            :placeholder="$t('inventoryChecks.placeholder.warehouse')"
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
        <el-form-item :label="$t('inventoryChecks.statusLabel')">
          <el-select
            v-model="queryParams.status"
            :placeholder="$t('inventoryChecks.placeholder.status')"
            clearable
            style="width: 150px"
          >
            <el-option :label="$t('inventoryChecks.status.counted')" value="COUNTED" />
            <el-option :label="$t('inventoryChecks.status.adjusted')" value="ADJUSTED" />
            <el-option :label="$t('inventoryChecks.status.cancelled')" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventoryChecks.dateRange')">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            :range-separator="$t('inventoryChecks.rangeSeparator')"
            :start-placeholder="$t('inventoryChecks.placeholder.startDate')"
            :end-placeholder="$t('inventoryChecks.placeholder.endDate')"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <el-icon><Search /></el-icon>
            {{ $t('inventoryChecks.action.search') }}
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            {{ $t('inventoryChecks.action.reset') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮 -->
    <el-card class="toolbar-card" shadow="never">
      <el-button v-permission="'inventory:check:create'" type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        {{ $t('inventoryChecks.action.create') }}
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
        <el-table-column prop="checkNo" :label="$t('inventoryChecks.checkNo')" width="180" />
        <el-table-column prop="warehouseName" :label="$t('inventoryChecks.warehouse')" width="150" />
        <el-table-column prop="checkDate" :label="$t('inventoryChecks.checkDate')" width="120" />
        <el-table-column prop="status" :label="$t('inventoryChecks.statusLabel')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="$t('inventoryChecks.remark')" show-overflow-tooltip />
        <el-table-column prop="createdBy" :label="$t('inventoryChecks.createdBy')" width="120" />
        <el-table-column prop="createdAt" :label="$t('inventoryChecks.createdTime')" width="160" />
        <el-table-column :label="$t('inventoryChecks.actions')" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">
              {{ $t('inventoryChecks.action.view') }}
            </el-button>
            <el-button link type="primary" @click="handlePrint(row)">
              {{ $t('inventoryChecks.action.print') }}
            </el-button>
            <el-button
              v-if="row.status === 'COUNTED'"
              v-permission="'inventory:check:create'"
              link
              type="primary"
              @click="handleEdit(row)"
            >
              {{ $t('inventoryChecks.action.edit') }}
            </el-button>
            <el-button
              v-if="row.status === 'COUNTED'"
              v-permission="'inventory:check:adjust'"
              link
              type="success"
              @click="handleComplete(row)"
            >
              {{ $t('inventoryChecks.action.adjust') }}
            </el-button>
            <el-button
              v-if="row.status === 'COUNTED'"
              v-permission="'inventory:check:cancel'"
              link
              type="danger"
              @click="handleCancel(row)"
            >
              {{ $t('inventoryChecks.action.cancel') }}
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
        @size-change="handlePageChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <!-- 新增/编辑对话框 -->
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
          <el-col :span="12">
            <el-form-item :label="$t('inventoryChecks.warehouse')" prop="warehouseId">
              <el-select
                v-model="formData.warehouseId"
                :placeholder="$t('inventoryChecks.placeholder.warehouse')"
                style="width: 100%"
                :disabled="isView || isEdit"
                @change="handleWarehouseChange"
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
          <el-col :span="12">
            <el-form-item :label="$t('inventoryChecks.checkDate')" prop="checkDate">
              <el-date-picker
                v-model="formData.checkDate"
                type="date"
                :placeholder="$t('inventoryChecks.placeholder.checkDate')"
                style="width: 100%"
                value-format="YYYY-MM-DD"
                :disabled="isView || isEdit"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$t('inventoryChecks.remark')">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="2"
            :placeholder="$t('inventoryChecks.placeholder.remark')"
            :disabled="isView || isEdit"
          />
        </el-form-item>

        <!-- 盘点明细 -->
        <el-divider content-position="left">{{ $t('inventoryChecks.details') }}</el-divider>
        <el-button
          v-if="!isView && !isEdit"
          type="primary"
          size="small"
          @click="handleAddItem"
          style="margin-bottom: 10px"
        >
          <el-icon><Plus /></el-icon>
          {{ $t('inventoryChecks.action.addProduct') }}
        </el-button>
        <el-table :data="formData.items" border max-height="400">
          <el-table-column :label="$t('inventoryChecks.product')" prop="productId" width="250">
            <template #default="{ row, $index }">
              <el-select
                v-model="row.productId"
                :placeholder="$t('inventoryChecks.placeholder.product')"
                filterable
                style="width: 100%"
                :disabled="isView || isEdit"
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
          <el-table-column :label="$t('inventoryChecks.productCode')" prop="productCode" width="130" />
          <el-table-column :label="$t('inventoryChecks.productName')" prop="productName" width="150" />
          <el-table-column :label="$t('inventoryChecks.location')" width="160">
            <template #default="{ row }">
              <el-select
                v-model="row.locationId"
                clearable
                filterable
                :placeholder="$t('inventoryChecks.placeholder.location')"
                :disabled="isView || isEdit"
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
          <el-table-column :label="$t('inventoryChecks.lotNo')" width="130">
            <template #default="{ row }">
              <el-input
                v-model="row.lotNo"
                :placeholder="row.lotControlled
                  ? $t('inventoryChecks.placeholder.lotNo')
                  : $t('inventoryChecks.placeholder.remark')"
                :disabled="isView || row.lotControlled === false"
              />
            </template>
          </el-table-column>
          <el-table-column :label="$t('inventoryChecks.serialNos')" min-width="220">
            <template #default="{ row }">
              <el-input
                v-model="row.serialNos"
                type="textarea"
                :rows="row.serialControlled ? 2 : 1"
                :placeholder="row.serialControlled
                  ? $t('inventoryChecks.placeholder.serialNos')
                  : $t('inventoryChecks.placeholder.remark')"
                :disabled="isView || row.serialControlled === false"
              />
              <div
                v-if="row.serialControlled"
                class="serial-progress"
                :class="{ 'serial-progress--ok': serialCaptureProgress(row.serialNos, row.actualQuantity).complete }"
              >
                {{ $t('inventoryChecks.serialProgress', serialCaptureProgress(row.serialNos, row.actualQuantity)) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="$t('inventoryChecks.productionDate')" width="150">
            <template #default="{ row }">
              <el-date-picker
                v-model="row.productionDate"
                type="date"
                value-format="YYYY-MM-DD"
                :placeholder="$t('inventoryChecks.placeholder.productionDate')"
                :disabled="isView || row.lotControlled === false"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column :label="$t('inventoryChecks.expiryDate')" width="150">
            <template #default="{ row }">
              <el-date-picker
                v-model="row.expiryDate"
                type="date"
                value-format="YYYY-MM-DD"
                :placeholder="row.shelfLifeControlled
                  ? $t('inventoryChecks.placeholder.expiryDate')
                  : $t('inventoryChecks.placeholder.remark')"
                :disabled="isView || (row.shelfLifeControlled === false && row.lotControlled === false)"
                style="width: 100%"
              />
            </template>
          </el-table-column>

          <el-table-column :label="$t('inventoryChecks.bookQuantity')" prop="bookQuantity" width="120">
            <template #default="{ row }">
              {{ row.bookQuantity || 0 }}
            </template>
          </el-table-column>
          <el-table-column :label="$t('inventoryChecks.actualQuantity')" prop="actualQuantity" width="130">
            <template #default="{ row }">
              <el-input-number
                v-model="row.actualQuantity"
                :min="0"
                :precision="2"
                :disabled="isView"
                style="width: 100%"
                @change="handleQuantityChange(row)"
              />
            </template>
          </el-table-column>
          <el-table-column :label="$t('inventoryChecks.difference')" prop="difference" width="100">
            <template #default="{ row }">
              <span :class="differenceClass(row.difference)">
                {{ row.difference || 0 }}
              </span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('inventoryChecks.remark')" prop="remark">
            <template #default="{ row }">
              <el-input
                v-model="row.remark"
                :placeholder="$t('inventoryChecks.placeholder.remark')"
                :disabled="isView"
              />
            </template>
          </el-table-column>
          <el-table-column v-if="!isView && !isEdit" :label="$t('inventoryChecks.actions')" width="80" fixed="right">
            <template #default="{ $index }">
              <el-button
                link
                type="danger"
                @click="handleDeleteItem($index)"
              >
                {{ $t('inventoryChecks.action.delete') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('inventoryChecks.action.cancel') }}</el-button>
        <el-button v-if="!isView" type="primary" :loading="submitLoading" @click="submitForm">
          {{ $t('inventoryChecks.action.confirm') }}
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
  getInventoryChecks,
  getInventoryCheck,
  createInventoryCheck,
  updateInventoryCheck,
  completeInventoryCheck,
  cancelInventoryCheck,
  getInventoryStocks
} from '@/api/inventory'
import { getLocations, getProducts, getWarehouses } from '@/api/masterdata'
import { printInventoryCheck } from '@/utils/bizPrint'
import { serialCaptureProgress } from '@/utils/productLines'
import { useInventoryCheckList } from '@/composables/useInventoryCheckList'
import { useInventoryCheckFormPresentation } from '@/composables/useInventoryCheckPresentation'
import { useInventoryCheckForm } from '@/composables/useInventoryCheckForm'

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
} = useInventoryCheckList(t, {
  getChecks: getInventoryChecks,
  getCheck: getInventoryCheck,
  completeCheck: completeInventoryCheck,
  cancelCheck: cancelInventoryCheck,
  getWarehouses,
  getProducts,
  getLocations,
  printCheck: printInventoryCheck,
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
  handleEdit,
  handleProductChange,
  handleQuantityChange,
  handleSubmit,
  handleView,
  handleWarehouseChange,
  isEdit,
  isView,
  selectedWarehouseId,
  submitLoading
} = useInventoryCheckForm(t, {
  getCheck: getInventoryCheck,
  createCheck: createInventoryCheck,
  updateCheck: updateInventoryCheck,
  getStocks: getInventoryStocks,
  findProduct: (productId) => findProduct(productId),
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message),
  onWarning: (message) => ElMessage.warning(message),
  onSubmitted: loadData
})

const {
  differenceClass,
  findProduct,
  locationLabel,
  locationsForSelectedWarehouse,
  productLabel,
  statusLabel,
  statusTagType,
  warehouseLabel,
  withWarehouseName
} = useInventoryCheckFormPresentation(
  t,
  { warehouses, products, locations },
  selectedWarehouseId
)

const formRules: FormRules = {
  warehouseId: [{ required: true, message: t('inventoryChecks.validation.warehouse'), trigger: 'change' }],
  checkDate: [{ required: true, message: t('inventoryChecks.validation.checkDate'), trigger: 'change' }]
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
.inventory-checks-container {
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

  .text-danger {
    color: #f56c6c;
    font-weight: bold;
  }

  .text-success {
    color: #67c23a;
    font-weight: bold;
  }
}
</style>
