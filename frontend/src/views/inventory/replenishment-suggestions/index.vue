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
        @size-change="handleQuery"
        @current-change="loadData"
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
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { formatLocalizedDateTime, formatLocalizedNumber } from '@/utils/locale'
import {
  cancelInventoryReplenishmentSuggestion,
  convertInventoryReplenishmentSuggestion,
  getInventoryReplenishmentSuggestions,
  updateInventoryReplenishmentSuggestion,
  type InventoryReplenishmentSuggestion,
  type InventoryReplenishmentSuggestionQuery
} from '@/api/inventory'
import {
  getProducts,
  getSuppliers,
  getWarehouses,
  type Product,
  type Supplier,
  type Warehouse
} from '@/api/masterdata'

const router = useRouter()
const { t } = useI18n()
const loading = ref(false)
const tableData = ref<InventoryReplenishmentSuggestion[]>([])
const total = ref(0)
const warehouses = ref<Warehouse[]>([])
const products = ref<Product[]>([])
const suppliers = ref<Supplier[]>([])
const createdRange = ref<[string, string]>()
const editDialogVisible = ref(false)
const editSubmitting = ref(false)
const editFormRef = ref<FormInstance>()

const editForm = reactive({
  id: '',
  suggestionNo: '',
  warehouseName: '',
  productName: '',
  supplierId: undefined as string | number | undefined,
  suggestedQty: 1,
  expectedArrivalDate: '',
  remark: ''
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

const fulfillmentStatusMap = computed<Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'info' | 'danger' }>>(() => ({
  SUGGESTED: { label: t('inventoryReplenishment.fulfillment.suggested'), type: 'warning' },
  PURCHASE_CREATED: { label: t('inventoryReplenishment.fulfillment.purchaseCreated'), type: 'primary' },
  PARTIAL_RECEIVED: { label: t('inventoryReplenishment.fulfillment.partialReceived'), type: 'warning' },
  REPLENISHED: { label: t('inventoryReplenishment.fulfillment.replenished'), type: 'success' },
  PURCHASE_CLOSED: { label: t('inventoryReplenishment.fulfillment.purchaseClosed'), type: 'info' },
  CANCELLED: { label: t('inventoryReplenishment.fulfillment.cancelled'), type: 'info' }
}))

const queryParams = reactive<InventoryReplenishmentSuggestionQuery>({
  pageNo: 1,
  pageSize: 20,
  suggestionNo: '',
  status: '',
  warehouseId: undefined,
  productId: undefined,
  supplierId: undefined,
  createdTimeFrom: undefined,
  createdTimeTo: undefined
})

const fulfillmentStatusMeta = (status?: string) => fulfillmentStatusMap.value[status || ''] || {
  label: status || '-',
  type: 'info' as const
}

const loadData = async () => {
  loading.value = true
  try {
    const response = await getInventoryReplenishmentSuggestions(queryParams)
    tableData.value = response.records
    total.value = response.total
  } catch (error) {
    ElMessage.error(t('inventoryReplenishment.message.loadFailed'))
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  loadData()
}

const handleReset = () => {
  Object.assign(queryParams, {
    pageNo: 1,
    pageSize: 20,
    suggestionNo: '',
    status: '',
    warehouseId: undefined,
    productId: undefined,
    supplierId: undefined,
    createdTimeFrom: undefined,
    createdTimeTo: undefined
  })
  createdRange.value = undefined
  loadData()
}

const handleCreatedRangeChange = (value: [string, string] | null) => {
  queryParams.createdTimeFrom = value?.[0]
  queryParams.createdTimeTo = value?.[1]
}

const handleEdit = (row: InventoryReplenishmentSuggestion) => {
  Object.assign(editForm, {
    id: row.id,
    suggestionNo: row.suggestionNo,
    warehouseName: row.warehouseName || '-',
    productName: `${row.productCode || ''} ${row.productName || ''}`.trim() || '-',
    supplierId: row.supplierId,
    suggestedQty: Number(row.suggestedQty || 0),
    expectedArrivalDate: row.expectedArrivalDate || '',
    remark: row.remark || ''
  })
  editDialogVisible.value = true
}

const submitEdit = async () => {
  if (!editFormRef.value) return
  await editFormRef.value.validate()
  editSubmitting.value = true
  try {
    await updateInventoryReplenishmentSuggestion(editForm.id, {
      supplierId: editForm.supplierId,
      suggestedQty: editForm.suggestedQty,
      expectedArrivalDate: editForm.expectedArrivalDate || undefined,
      remark: editForm.remark || undefined
    })
    ElMessage.success(t('inventoryReplenishment.message.updated'))
    editDialogVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error(t('inventoryReplenishment.message.saveFailed'))
  } finally {
    editSubmitting.value = false
  }
}

const handleCancel = async (row: InventoryReplenishmentSuggestion) => {
  try {
    const { value } = await ElMessageBox.prompt(
      t('inventoryReplenishment.message.cancelConfirm', { no: row.suggestionNo }),
      t('inventoryReplenishment.message.cancelTitle'),
      {
        confirmButtonText: t('inventoryReplenishment.message.confirm'),
        cancelButtonText: t('inventoryReplenishment.cancel'),
        inputType: 'textarea',
        inputPlaceholder: t('inventoryReplenishment.message.cancelReason')
      }
    )
    await cancelInventoryReplenishmentSuggestion(row.id, value || undefined)
    ElMessage.success(t('inventoryReplenishment.message.cancelled'))
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('inventoryReplenishment.message.cancelFailed'))
    }
  }
}

const handleConvert = async (row: InventoryReplenishmentSuggestion) => {
  if (!row.supplierId) {
    ElMessage.warning(t('inventoryReplenishment.message.supplierRequired'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('inventoryReplenishment.message.convertConfirm', { no: row.suggestionNo }),
      t('inventoryReplenishment.message.convertTitle'),
      {
        confirmButtonText: t('inventoryReplenishment.message.confirm'),
        cancelButtonText: t('inventoryReplenishment.cancel'),
        type: 'warning'
      }
    )
    const response = await convertInventoryReplenishmentSuggestion(row.id)
    ElMessage.success(t('inventoryReplenishment.message.converted', { no: response.purchaseOrderNo }))
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('inventoryReplenishment.message.convertFailed'))
    }
  }
}

const goPurchaseOrder = (orderNo: string) => {
  router.push({
    path: '/purchase/orders',
    query: { keyword: orderNo }
  })
}

const loadOptions = async () => {
  try {
    const [warehousePage, productPage, supplierPage] = await Promise.all([
      getWarehouses({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' }),
      getProducts({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' }),
      getSuppliers({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
    ])
    warehouses.value = warehousePage.records
    products.value = productPage.records
    suppliers.value = supplierPage.records
  } catch (error) {
    ElMessage.warning(t('inventoryReplenishment.message.optionsLoadFailed'))
  }
}

const formatNumber = (value?: number) => formatLocalizedNumber(Number(value ?? 0), {
  maximumFractionDigits: 4
})

const formatDateTime = (value?: string) => {
  return formatLocalizedDateTime(value) || '-'
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
