<template>
  <div class="replenishment-suggestions-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item :label="t('inventoryReplenishment.suggestionNo')">
          <el-input
            v-model="queryParams.suggestionNo"
            :placeholder="t('inventoryReplenishment.suggestionNoPlaceholder')"
            clearable
            style="width: 190px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item :label="t('inventoryReplenishment.statusLabel')">
          <el-select v-model="queryParams.status" :placeholder="t('inventoryReplenishment.all')" clearable style="width: 140px">
            <el-option :label="t('inventoryReplenishment.status.draft')" value="DRAFT" />
            <el-option :label="t('inventoryReplenishment.status.converted')" value="CONVERTED" />
            <el-option :label="t('inventoryReplenishment.status.cancelled')" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('inventoryReplenishment.warehouse')">
          <el-select
            v-model="queryParams.warehouseId"
            :placeholder="t('inventoryReplenishment.selectWarehouse')"
            clearable
            filterable
            style="width: 190px"
          >
            <el-option
              v-for="warehouse in warehouses"
              :key="warehouse.id"
              :label="warehouse.name || warehouse.warehouseName"
              :value="warehouse.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('inventoryReplenishment.product')">
          <el-select
            v-model="queryParams.productId"
            :placeholder="t('inventoryReplenishment.selectProduct')"
            clearable
            filterable
            style="width: 220px"
          >
            <el-option
              v-for="product in products"
              :key="product.id"
              :label="`${product.code || product.productCode} - ${product.name || product.productName}`"
              :value="product.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('inventoryReplenishment.supplier')">
          <el-select
            v-model="queryParams.supplierId"
            :placeholder="t('inventoryReplenishment.selectSupplier')"
            clearable
            filterable
            style="width: 220px"
          >
            <el-option
              v-for="supplier in suppliers"
              :key="supplier.id"
              :label="`${supplier.code || supplier.supplierCode} - ${supplier.name || supplier.supplierName}`"
              :value="supplier.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('inventoryReplenishment.createdAt')">
          <el-date-picker
            v-model="createdRange"
            type="datetimerange"
            :range-separator="t('inventoryReplenishment.rangeSeparator')"
            :start-placeholder="t('inventoryReplenishment.startTime')"
            :end-placeholder="t('inventoryReplenishment.endTime')"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 360px"
            @change="handleCreatedRangeChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <el-icon><Search /></el-icon>
            {{ t('inventoryReplenishment.search') }}
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            {{ t('inventoryReplenishment.reset') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="suggestionNo" :label="t('inventoryReplenishment.suggestionNo')" width="170" fixed />
        <el-table-column prop="status" :label="t('inventoryReplenishment.statusLabel')" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'DRAFT'" type="warning">{{ t('inventoryReplenishment.status.draft') }}</el-tag>
            <el-tag v-else-if="row.status === 'CONVERTED'" type="success">{{ t('inventoryReplenishment.status.converted') }}</el-tag>
            <el-tag v-else type="info">{{ t('inventoryReplenishment.status.cancelled') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fulfillmentStatus" :label="t('inventoryReplenishment.fulfillmentStatus')" width="140" align="center">
          <template #default="{ row }">
            <el-tag :type="fulfillmentStatusMeta(row.fulfillmentStatus).type" effect="plain">
              {{ fulfillmentStatusMeta(row.fulfillmentStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="warehouseName" :label="t('inventoryReplenishment.warehouse')" width="150" show-overflow-tooltip />
        <el-table-column prop="productCode" :label="t('inventoryReplenishment.productCode')" width="140" show-overflow-tooltip />
        <el-table-column prop="productName" :label="t('inventoryReplenishment.productName')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="supplierName" :label="t('inventoryReplenishment.supplier')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.supplierName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="suggestedQty" :label="t('inventoryReplenishment.suggestedQty')" width="140" align="right">
          <template #default="{ row }">{{ formatNumber(row.suggestedQty) }}</template>
        </el-table-column>
        <el-table-column prop="shortageQtySnapshot" :label="t('inventoryReplenishment.shortageSnapshot')" width="140" align="right">
          <template #default="{ row }">{{ formatNumber(row.shortageQtySnapshot) }}</template>
        </el-table-column>
        <el-table-column prop="expectedArrivalDate" :label="t('inventoryReplenishment.expectedArrival')" width="140">
          <template #default="{ row }">{{ row.expectedArrivalDate || '-' }}</template>
        </el-table-column>
        <el-table-column prop="purchaseOrderNo" :label="t('inventoryReplenishment.purchaseOrder')" width="170">
          <template #default="{ row }">
            <el-button
              v-if="row.purchaseOrderNo"
              link
              type="primary"
              @click="goPurchaseOrder(row.purchaseOrderNo)"
            >
              {{ row.purchaseOrderNo }}
            </el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" :label="t('inventoryReplenishment.createdAt')" width="190">
          <template #default="{ row }">{{ formatDateTime(row.createdTime) }}</template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('inventoryReplenishment.remark')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('inventoryReplenishment.actions')" width="260" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'inventory:replenishment:update'"
              link
              type="primary"
              @click="handleEdit(row)"
            >
              {{ t('inventoryReplenishment.edit') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'inventory:replenishment:convert'"
              link
              type="success"
              @click="handleConvert(row)"
            >
              {{ t('inventoryReplenishment.convert') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'inventory:replenishment:cancel'"
              link
              type="danger"
              @click="handleCancel(row)"
            >
              {{ t('inventoryReplenishment.cancel') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.pageNo"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <el-dialog
      v-model="editDialogVisible"
      :title="t('inventoryReplenishment.editTitle')"
      width="560px"
      destroy-on-close
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="100px"
      >
        <el-form-item :label="t('inventoryReplenishment.suggestionNo')">
          <el-input v-model="editForm.suggestionNo" disabled />
        </el-form-item>
        <el-form-item :label="t('inventoryReplenishment.warehouse')">
          <el-input v-model="editForm.warehouseName" disabled />
        </el-form-item>
        <el-form-item :label="t('inventoryReplenishment.product')">
          <el-input v-model="editForm.productName" disabled />
        </el-form-item>
        <el-form-item :label="t('inventoryReplenishment.supplier')">
          <el-select
            v-model="editForm.supplierId"
            :placeholder="t('inventoryReplenishment.selectSupplier')"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="supplier in suppliers"
              :key="supplier.id"
              :label="`${supplier.code || supplier.supplierCode} - ${supplier.name || supplier.supplierName}`"
              :value="supplier.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('inventoryReplenishment.suggestedQty')" prop="suggestedQty">
          <el-input-number
            v-model="editForm.suggestedQty"
            :min="0.0001"
            :precision="4"
            :step="1"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('inventoryReplenishment.expectedArrival')">
          <el-date-picker
            v-model="editForm.expectedArrivalDate"
            type="date"
            value-format="YYYY-MM-DD"
            :placeholder="t('inventoryReplenishment.expectedArrivalPlaceholder')"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('inventoryReplenishment.remark')">
          <el-input
            v-model="editForm.remark"
            type="textarea"
            :rows="3"
            maxlength="512"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">{{ t('inventoryReplenishment.cancel') }}</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="submitEdit">
          {{ t('inventoryReplenishment.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  cancelInventoryReplenishmentSuggestion,
  convertInventoryReplenishmentSuggestion,
  getInventoryReplenishmentSuggestions,
  updateInventoryReplenishmentSuggestion
} from '@/api/inventory'
import {
  getProducts,
  getSuppliers,
  getWarehouses
} from '@/api/masterdata'
import { useInventoryReplenishmentPresentation } from '@/composables/useInventoryReplenishmentPresentation'
import { useInventoryReplenishmentList } from '@/composables/useInventoryReplenishmentList'
import { useInventoryReplenishmentForm } from '@/composables/useInventoryReplenishmentForm'

const router = useRouter()
const { t } = useI18n()
const editFormRef = ref<FormInstance>()

const notify = {
  onError: (message: string) => ElMessage.error(message),
  onSuccess: (message: string) => ElMessage.success(message),
  onWarning: (message: string) => ElMessage.warning(message)
}

const {
  createdRange,
  handleCancel,
  handleConvert,
  handleCreatedRangeChange,
  handlePageChange,
  handleQuery,
  handleReset,
  handleSizeChange,
  loadData,
  loadOptions,
  loading,
  products,
  queryParams,
  suppliers,
  tableData,
  total,
  warehouses
} = useInventoryReplenishmentList(t, {
  getSuggestions: getInventoryReplenishmentSuggestions,
  getWarehouses,
  getProducts,
  getSuppliers,
  cancelSuggestion: cancelInventoryReplenishmentSuggestion,
  convertSuggestion: convertInventoryReplenishmentSuggestion,
  confirm: (message, title, options) => ElMessageBox.confirm(message, title, options as any),
  prompt: (message, title, options) => ElMessageBox.prompt(message, title, options as any),
  ...notify
})

const {
  formatDateTime,
  formatNumber,
  fulfillmentStatusMeta
} = useInventoryReplenishmentPresentation(t)

const {
  editDialogVisible,
  editForm,
  editSubmitting,
  handleEdit,
  submitEdit: saveEdit
} = useInventoryReplenishmentForm(t, {
  updateSuggestion: updateInventoryReplenishmentSuggestion,
  onSubmitted: loadData,
  ...notify
})

const editRules = computed<FormRules>(() => ({
  suggestedQty: [
    { required: true, message: t('inventoryReplenishment.validation.quantityRequired'), trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (Number(value) <= 0) {
          callback(new Error(t('inventoryReplenishment.validation.quantityPositive')))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}))

const submitEdit = async () => {
  if (!editFormRef.value) return
  await editFormRef.value.validate()
  await saveEdit()
}

const goPurchaseOrder = (orderNo: string) => {
  router.push({
    path: '/purchase/orders',
    query: { keyword: orderNo }
  })
}

onMounted(() => {
  loadData()
  loadOptions()
})
</script>

<style scoped lang="scss">
.replenishment-suggestions-container {
  padding: 20px;

  .search-card,
  .table-card {
    margin-bottom: 20px;
  }

  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }
}
</style>
