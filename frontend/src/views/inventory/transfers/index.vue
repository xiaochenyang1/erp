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
            <el-tag v-if="row.status === 'DRAFT'" type="info">{{ $t('inventoryTransfers.status.draft') }}</el-tag>
            <el-tag v-else-if="row.status === 'COMPLETED'" type="success">{{ $t('inventoryTransfers.status.completed') }}</el-tag>
            <el-tag v-else-if="row.status === 'CANCELLED'" type="danger">{{ $t('inventoryTransfers.status.cancelled') }}</el-tag>
            <el-tag v-else type="info">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="$t('inventoryTransfers.remark')" show-overflow-tooltip />
        <el-table-column prop="createdBy" :label="$t('inventoryTransfers.createdBy')" width="120" />
        <el-table-column prop="createdAt" :label="$t('inventoryTransfers.createdTime')" width="160" />
        <el-table-column :label="$t('inventoryTransfers.actions')" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">
              {{ $t('inventoryTransfers.action.view') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'inventory:transfer:post'"
              link
              type="success"
              @click="handleShip(row)"
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
        @current-change="handleQuery"
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
                  :label="`${location.locationCode} ${location.locationName}`"
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
                  :label="`${location.locationCode} ${location.locationName}`"
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
        <el-button v-if="!isView" type="primary" :loading="submitLoading" @click="handleSubmit">
          {{ $t('inventoryTransfers.action.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getInventoryTransfers,
  getInventoryTransfer,
  createInventoryTransfer,
  shipInventoryTransfer,
  cancelInventoryTransfer,
  type InventoryTransferQuery,
  type InventoryTransferCreateRequest,
  type InventoryTransfer
} from '@/api/inventory'
import { getLocations, getWarehouses, type Location, type Product, type Warehouse } from '@/api/masterdata'
import { getProducts } from '@/api/masterdata'
import {
  hydrateProductLineLabels,
  serialCaptureProgress,
  validateProductControlLines
} from '@/utils/productLines'
import { formatBusinessDate } from '@/utils/locale'

const { t } = useI18n()

// 查询参数
const queryParams = reactive<InventoryTransferQuery>({
  pageNo: 1,
  pageSize: 10,
  transferNo: '',
  fromWarehouseId: undefined,
  toWarehouseId: undefined,
  status: '',
  startDate: '',
  endDate: ''
})

// 日期范围
const dateRange = ref<[string, string] | null>(null)

// 表格数据
const loading = ref(false)
const tableData = ref<InventoryTransfer[]>([])
const total = ref(0)

// 仓库列表
const warehouses = ref<Warehouse[]>([])
const locations = ref<Location[]>([])

// 产品列表
const products = ref<Product[]>([])

// 对话框
const dialogVisible = ref(false)
const submitLoading = ref(false)
const dialogTitle = ref('')
const isView = ref(false)
const formRef = ref<FormInstance>()

// 表单数据
const formData = reactive<InventoryTransferCreateRequest>({
  fromWarehouseId: 0,
  toWarehouseId: 0,
  transferDate: '',
  items: [],
  remark: ''
})
const locationsForFromWarehouse = computed(() => {
  if (!formData.fromWarehouseId) return locations.value
  return locations.value.filter((location) => String(location.warehouseId) === String(formData.fromWarehouseId))
})
const locationsForToWarehouse = computed(() => {
  if (!formData.toWarehouseId) return locations.value
  return locations.value.filter((location) => String(location.warehouseId) === String(formData.toWarehouseId))
})

// 表单验证规则
const formRules: FormRules = {
  fromWarehouseId: [{ required: true, message: t('inventoryTransfers.validation.fromWarehouse'), trigger: 'change' }],
  toWarehouseId: [{ required: true, message: t('inventoryTransfers.validation.toWarehouse'), trigger: 'change' }],
  transferDate: [{ required: true, message: t('inventoryTransfers.validation.transferDate'), trigger: 'change' }]
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const response = await getInventoryTransfers(queryParams)
    tableData.value = response.records.map((transfer) => ({
      ...transfer,
      fromWarehouseName: transfer.fromWarehouseName || warehouseNameById(transfer.fromWarehouseId),
      toWarehouseName: transfer.toWarehouseName || warehouseNameById(transfer.toWarehouseId)
    }))
    total.value = response.total
  } catch (error) {
    ElMessage.error(t('inventoryTransfers.message.loadFailed'))
  } finally {
    loading.value = false
  }
}

// 加载仓库列表
const loadWarehouses = async () => {
  try {
    const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }
    const response = await getWarehouses(optionPageQuery)
    warehouses.value = response.records
  } catch (error) {
    ElMessage.error(t('inventoryTransfers.message.warehousesLoadFailed'))
  }
}

const loadLocations = async () => {
  try {
    const page = await getLocations({ pageNo: 1, pageSize: 500, status: 'ACTIVE' })
    locations.value = page.records || []
  } catch {
    locations.value = []
  }
}

// 加载产品列表
const loadProducts = async () => {
  try {
    const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }
    const response = await getProducts(optionPageQuery)
    products.value = response.records
  } catch (error) {
    ElMessage.error(t('inventoryTransfers.message.productsLoadFailed'))
  }
}

// 查询
const handleQuery = () => {
  if (dateRange.value) {
    queryParams.startDate = dateRange.value[0]
    queryParams.endDate = dateRange.value[1]
  } else {
    queryParams.startDate = ''
    queryParams.endDate = ''
  }
  queryParams.pageNo = 1
  loadData()
}

// 重置
const handleReset = () => {
  queryParams.transferNo = ''
  queryParams.fromWarehouseId = undefined
  queryParams.toWarehouseId = undefined
  queryParams.status = ''
  dateRange.value = null
  queryParams.startDate = ''
  queryParams.endDate = ''
  handleQuery()
}

// 新增
const handleCreate = () => {
  dialogTitle.value = t('inventoryTransfers.dialog.create')
  isView.value = false
  resetForm()
  dialogVisible.value = true
}

// 查看
const handleView = async (row: InventoryTransfer) => {
  dialogTitle.value = t('inventoryTransfers.dialog.view')
  isView.value = true
  try {
    const data = await getInventoryTransfer(row.id)
    Object.assign(formData, data)
    formData.items = await hydrateProductLineLabels(formData.items || [], async (productId) => {
      const product = products.value.find((item) => String(item.id) === String(productId))
      return product || {}
    })
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(t('inventoryTransfers.message.detailLoadFailed'))
  }
}

// 过账
const handleShip = async (row: InventoryTransfer) => {
  try {
    await ElMessageBox.confirm(t('inventoryTransfers.message.postConfirm'), t('inventoryTransfers.prompt'), {
      confirmButtonText: t('inventoryTransfers.action.confirm'),
      cancelButtonText: t('inventoryTransfers.action.cancel'),
      type: 'warning'
    })
    await shipInventoryTransfer(row.id)
    ElMessage.success(t('inventoryTransfers.message.success'))
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(t('inventoryTransfers.message.failed'))
    }
  }
}

// 取消
const handleCancel = async (row: InventoryTransfer) => {
  try {
    await ElMessageBox.confirm(t('inventoryTransfers.message.cancelConfirm'), t('inventoryTransfers.prompt'), {
      confirmButtonText: t('inventoryTransfers.action.confirm'),
      cancelButtonText: t('inventoryTransfers.action.cancel'),
      type: 'warning'
    })
    await cancelInventoryTransfer(row.id)
    ElMessage.success(t('inventoryTransfers.message.success'))
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(t('inventoryTransfers.message.failed'))
    }
  }
}

// 添加明细
const handleAddItem = () => {
  formData.items.push({
    productId: 0,
    productCode: '',
    productName: '',
    quantity: 0,
    unitCost: 0,
    fromLocationId: undefined,
    toLocationId: undefined,
    serialNos: '',
    lotNo: '',
    productionDate: '',
    expiryDate: '',
    lotControlled: undefined,
    shelfLifeControlled: undefined,
    serialControlled: undefined,
    remark: ''
  })
}

// 删除明细
const handleDeleteItem = (index: number) => {
  formData.items.splice(index, 1)
}

// 产品变化
const handleProductChange = async (index: number) => {
  const item = formData.items[index]
  const product = products.value.find(p => String(p.id) === String(item.productId))
  if (product) {
    item.productCode = product.code || product.productCode || ''
    item.productName = product.name || product.productName || ''
    item.unitCost = product.purchasePrice ?? item.unitCost ?? 0
    item.lotControlled = Boolean(product.lotControlled)
    item.shelfLifeControlled = Boolean(product.shelfLifeControlled)
    item.serialControlled = Boolean(product.serialControlled)
  }
  if (item.productId) {
    const [hydrated] = await hydrateProductLineLabels([item], async () => product || {})
    Object.assign(item, hydrated)
  }
}

const warehouseLabel = (warehouse: Warehouse) => warehouse.name || warehouse.warehouseName || t('inventoryTransfers.warehouseFallback', { id: warehouse.id })

const warehouseNameById = (warehouseId: string | number) => {
  const warehouse = warehouses.value.find(item => String(item.id) === String(warehouseId))
  return warehouse ? warehouseLabel(warehouse) : ''
}

const productLabel = (product: Product) => {
  const code = product.code || product.productCode || ''
  const name = product.name || product.productName || ''
  return code && name ? `${code} - ${name}` : name || code || t('inventoryTransfers.productFallback', { id: product.id })
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      if (formData.fromWarehouseId === formData.toWarehouseId) {
        ElMessage.warning(t('inventoryTransfers.validation.warehousesDifferent'))
        return
      }

      if (formData.items.length === 0) {
        ElMessage.warning(t('inventoryTransfers.validation.itemRequired'))
        return
      }

      formData.items = await hydrateProductLineLabels(formData.items, async (productId) => {
        const product = products.value.find((item) => String(item.id) === String(productId))
        return product || {}
      })
      const controlIssues = validateProductControlLines(formData.items)
      if (controlIssues.length > 0) {
        const issue = controlIssues[0]
        const product = issue.productCode || issue.productName || String(issue.productId)
        ElMessage.warning(t(`inventoryTransfers.validation.${issue.messageKey}`, {
          line: issue.index + 1,
          product,
          expected: issue.expectedSerialCount,
          actual: issue.actualSerialCount
        }))
        return
      }

      submitLoading.value = true
      try {
        await createInventoryTransfer(formData)
        ElMessage.success(t('inventoryTransfers.message.success'))
        dialogVisible.value = false
        loadData()
      } catch (error) {
        ElMessage.error(t('inventoryTransfers.message.failed'))
      } finally {
        submitLoading.value = false
      }
    }
  })
}

// 重置表单
const resetForm = () => {
  formData.fromWarehouseId = 0
  formData.toWarehouseId = 0
  formData.transferDate = formatBusinessDate()
  formData.items = []
  formData.remark = ''
  formRef.value?.clearValidate()
}

onMounted(async () => {
  await Promise.all([loadWarehouses(), loadProducts(), loadLocations()])
  loadData()
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
