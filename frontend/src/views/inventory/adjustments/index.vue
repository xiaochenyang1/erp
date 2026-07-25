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
              :label="warehouse.name"
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
            <el-tag v-if="row.type === 'GAIN'" type="success">{{ $t('inventoryAdjustments.type.gain') }}</el-tag>
            <el-tag v-else-if="row.type === 'LOSS'" type="danger">{{ $t('inventoryAdjustments.type.loss') }}</el-tag>
            <el-tag v-else type="info">{{ $t('inventoryAdjustments.type.other') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('inventoryAdjustments.statusLabel')" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'DRAFT'" type="info">{{ $t('inventoryAdjustments.status.draft') }}</el-tag>
            <el-tag v-else-if="row.status === 'COMPLETED'" type="success">{{ $t('inventoryAdjustments.status.completed') }}</el-tag>
            <el-tag v-else-if="row.status === 'CANCELLED'" type="danger">{{ $t('inventoryAdjustments.status.cancelled') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="$t('inventoryAdjustments.remark')" show-overflow-tooltip />
        <el-table-column prop="createdBy" :label="$t('inventoryAdjustments.createdBy')" width="120" />
        <el-table-column prop="createdAt" :label="$t('inventoryAdjustments.createdTime')" width="160" />
        <el-table-column :label="$t('inventoryAdjustments.actions')" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">
              {{ $t('inventoryAdjustments.action.view') }}
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
        @current-change="handleQuery"
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
                  :label="warehouse.name"
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
                  v-for="location in locationsForWarehouse"
                  :key="location.id"
                  :label="`${location.locationCode} ${location.locationName}`"
                  :value="location.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$t('inventoryAdjustments.serialNos')" min-width="160">
            <template #default="{ row }">
              <el-input
                v-model="row.serialNos"
                :placeholder="$t('inventoryAdjustments.placeholder.serialNos')"
                :disabled="isView"
              />
            </template>
          </el-table-column>
          <el-table-column :label="$t('inventoryAdjustments.lotNo')" width="130">
            <template #default="{ row }">
              <el-input v-model="row.lotNo" :placeholder="$t('inventoryAdjustments.placeholder.lotNo')" :disabled="isView" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('inventoryAdjustments.productionDate')" width="150">
            <template #default="{ row }">
              <el-date-picker v-model="row.productionDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('inventoryAdjustments.placeholder.productionDate')" :disabled="isView" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('inventoryAdjustments.expiryDate')" width="150">
            <template #default="{ row }">
              <el-date-picker v-model="row.expiryDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('inventoryAdjustments.placeholder.expiryDate')" :disabled="isView" style="width: 100%" />
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
          <el-button v-if="!isView" type="primary" :loading="submitLoading" @click="handleSubmit">
            {{ $t('inventoryAdjustments.action.confirm') }}
          </el-button>
        </div>
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
  getInventoryAdjustments,
  getInventoryAdjustment,
  createInventoryAdjustment,
  completeInventoryAdjustment,
  cancelInventoryAdjustment,
  type InventoryAdjustmentQuery,
  type InventoryAdjustmentCreateRequest,
  type InventoryAdjustment
} from '@/api/inventory'
import { getLocations, getWarehouses, type Location, type Product, type Warehouse } from '@/api/masterdata'
import { getProducts } from '@/api/masterdata'
import { formatBusinessDate } from '@/utils/locale'

const { t } = useI18n()

// 查询参数
const queryParams = reactive<InventoryAdjustmentQuery>({
  pageNo: 1,
  pageSize: 10,
  adjustmentNo: '',
  warehouseId: undefined,
  type: '',
  status: '',
  startDate: '',
  endDate: ''
})

// 日期范围
const dateRange = ref<[string, string] | null>(null)

// 表格数据
const loading = ref(false)
const tableData = ref<InventoryAdjustment[]>([])
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
const formData = reactive<InventoryAdjustmentCreateRequest>({
  warehouseId: 0,
  adjustmentDate: '',
  type: 'GAIN',
  items: [],
  remark: ''
})
const locationsForWarehouse = computed(() => {
  if (!formData.warehouseId) return locations.value
  return locations.value.filter((location) => String(location.warehouseId) === String(formData.warehouseId))
})

// 表单验证规则
const formRules: FormRules = {
  warehouseId: [{ required: true, message: t('inventoryAdjustments.validation.warehouse'), trigger: 'change' }],
  adjustmentDate: [{ required: true, message: t('inventoryAdjustments.validation.adjustmentDate'), trigger: 'change' }],
  type: [{ required: true, message: t('inventoryAdjustments.validation.adjustmentType'), trigger: 'change' }]
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const response = await getInventoryAdjustments(queryParams)
    tableData.value = response.records
    total.value = response.total
  } catch (error) {
    ElMessage.error(t('inventoryAdjustments.message.loadFailed'))
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
    ElMessage.error(t('inventoryAdjustments.message.warehousesLoadFailed'))
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
    ElMessage.error(t('inventoryAdjustments.message.productsLoadFailed'))
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
  queryParams.adjustmentNo = ''
  queryParams.warehouseId = undefined
  queryParams.type = ''
  queryParams.status = ''
  dateRange.value = null
  queryParams.startDate = ''
  queryParams.endDate = ''
  handleQuery()
}

// 新增
const handleCreate = () => {
  dialogTitle.value = t('inventoryAdjustments.dialog.create')
  isView.value = false
  resetForm()
  dialogVisible.value = true
}

// 查看
const handleView = async (row: InventoryAdjustment) => {
  dialogTitle.value = t('inventoryAdjustments.dialog.view')
  isView.value = true
  try {
    const data = await getInventoryAdjustment(row.id)
    Object.assign(formData, data)
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(t('inventoryAdjustments.message.detailLoadFailed'))
  }
}

// 过账
const handleComplete = async (row: InventoryAdjustment) => {
  try {
    await ElMessageBox.confirm(t('inventoryAdjustments.message.postConfirm'), t('inventoryAdjustments.prompt'), {
      confirmButtonText: t('inventoryAdjustments.action.confirm'),
      cancelButtonText: t('inventoryAdjustments.action.cancel'),
      type: 'warning'
    })
    await completeInventoryAdjustment(row.id)
    ElMessage.success(t('inventoryAdjustments.message.success'))
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(t('inventoryAdjustments.message.failed'))
    }
  }
}

// 取消
const handleCancel = async (row: InventoryAdjustment) => {
  try {
    await ElMessageBox.confirm(t('inventoryAdjustments.message.cancelConfirm'), t('inventoryAdjustments.prompt'), {
      confirmButtonText: t('inventoryAdjustments.action.confirm'),
      cancelButtonText: t('inventoryAdjustments.action.cancel'),
      type: 'warning'
    })
    await cancelInventoryAdjustment(row.id)
    ElMessage.success(t('inventoryAdjustments.message.success'))
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(t('inventoryAdjustments.message.failed'))
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
    locationId: undefined,
    serialNos: '',
    lotNo: '',
    productionDate: '',
    expiryDate: '',
    reason: ''
  })
}

// 删除明细
const handleDeleteItem = (index: number) => {
  formData.items.splice(index, 1)
}

// 产品变化
const handleProductChange = (index: number) => {
  const item = formData.items[index]
  const product = products.value.find(p => String(p.id) === String(item.productId))
  if (product) {
    item.productCode = product.code || product.productCode || ''
    item.productName = product.name || product.productName || ''
    item.unitCost = product.purchasePrice ?? item.unitCost ?? 0
  }
}

const productLabel = (product: Product) => {
  const code = product.code || product.productCode || ''
  const name = product.name || product.productName || ''
  return code && name ? `${code} - ${name}` : name || code || t('inventoryAdjustments.productFallback', { id: product.id })
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      if (formData.items.length === 0) {
        ElMessage.warning(t('inventoryAdjustments.validation.itemRequired'))
        return
      }

      submitLoading.value = true
      try {
        await createInventoryAdjustment(formData)
        ElMessage.success(t('inventoryAdjustments.message.success'))
        dialogVisible.value = false
        loadData()
      } catch (error) {
        ElMessage.error(t('inventoryAdjustments.message.failed'))
      } finally {
        submitLoading.value = false
      }
    }
  })
}

// 重置表单
const resetForm = () => {
  formData.warehouseId = 0
  formData.adjustmentDate = formatBusinessDate()
  formData.type = 'GAIN'
  formData.items = []
  formData.remark = ''
  formRef.value?.clearValidate()
}

onMounted(() => {
  loadData()
  loadWarehouses()
  loadProducts()
  loadLocations()
})
</script>
