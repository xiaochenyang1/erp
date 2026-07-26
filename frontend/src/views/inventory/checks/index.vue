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
            <el-tag v-if="row.status === 'COUNTED'" type="warning">{{ $t('inventoryChecks.status.counted') }}</el-tag>
            <el-tag v-else-if="row.status === 'ADJUSTED'" type="success">{{ $t('inventoryChecks.status.adjusted') }}</el-tag>
            <el-tag v-else-if="row.status === 'CANCELLED'" type="danger">{{ $t('inventoryChecks.status.cancelled') }}</el-tag>
            <el-tag v-else type="info">{{ row.status }}</el-tag>
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
        @size-change="handleQuery"
        @current-change="handleQuery"
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
                  v-for="location in locationsForWarehouse"
                  :key="location.id"
                  :label="`${location.locationCode} ${location.locationName}`"
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
              <span :class="{ 'text-danger': row.difference < 0, 'text-success': row.difference > 0 }">
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
        <el-button v-if="!isView" type="primary" :loading="submitLoading" @click="handleSubmit">
          {{ $t('inventoryChecks.action.confirm') }}
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
  getInventoryChecks,
  getInventoryCheck,
  createInventoryCheck,
  updateInventoryCheck,
  completeInventoryCheck,
  cancelInventoryCheck,
  type InventoryCheckQuery,
  type InventoryCheckCreateRequest,
  type InventoryCheckUpdateRequest,
  type InventoryCheck,
  type InventoryCheckItem
} from '@/api/inventory'
import { getLocations, getWarehouses, type Location, type Product, type Warehouse } from '@/api/masterdata'
import { getProducts } from '@/api/masterdata'
import { getInventoryStocks, type InventoryStockQuery } from '@/api/inventory'
import { formatBusinessDate } from '@/utils/locale'
import { printInventoryCheck } from '@/utils/bizPrint'
import {
  hydrateProductLineLabels,
  serialCaptureProgress,
  validateProductControlLines
} from '@/utils/productLines'

const { t } = useI18n()

// 查询参数
const queryParams = reactive<InventoryCheckQuery>({
  pageNo: 1,
  pageSize: 10,
  checkNo: '',
  warehouseId: undefined,
  status: '',
  startDate: '',
  endDate: ''
})

// 日期范围
const dateRange = ref<[string, string] | null>(null)

// 表格数据
const loading = ref(false)
const tableData = ref<InventoryCheck[]>([])
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
const isEdit = ref(false)
const currentId = ref<string | number>('')
const formRef = ref<FormInstance>()

// 表单数据
const formData = reactive<InventoryCheckCreateRequest & { items: InventoryCheckItem[] }>({
  warehouseId: 0,
  checkDate: '',
  items: [],
  remark: ''
})
const locationsForWarehouse = computed(() => {
  if (!formData.warehouseId) return locations.value
  return locations.value.filter((location) => String(location.warehouseId) === String(formData.warehouseId))
})

// 表单验证规则
const formRules: FormRules = {
  warehouseId: [{ required: true, message: t('inventoryChecks.validation.warehouse'), trigger: 'change' }],
  checkDate: [{ required: true, message: t('inventoryChecks.validation.checkDate'), trigger: 'change' }]
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const response = await getInventoryChecks(queryParams)
    tableData.value = response.records.map((check) => ({
      ...check,
      warehouseName: check.warehouseName || warehouseNameById(check.warehouseId)
    }))
    total.value = response.total
  } catch (error) {
    ElMessage.error(t('inventoryChecks.message.loadFailed'))
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
    ElMessage.error(t('inventoryChecks.message.warehousesLoadFailed'))
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
    ElMessage.error(t('inventoryChecks.message.productsLoadFailed'))
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
  queryParams.checkNo = ''
  queryParams.warehouseId = undefined
  queryParams.status = ''
  dateRange.value = null
  queryParams.startDate = ''
  queryParams.endDate = ''
  handleQuery()
}

// 新增
const handleCreate = () => {
  dialogTitle.value = t('inventoryChecks.dialog.create')
  isView.value = false
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

// 编辑
const handleEdit = async (row: InventoryCheck) => {
  dialogTitle.value = t('inventoryChecks.dialog.edit')
  isView.value = false
  isEdit.value = true
  currentId.value = row.id
  try {
    const data = await getInventoryCheck(row.id)
    Object.assign(formData, data)
    formData.items = await hydrateProductLineLabels(formData.items || [], async (productId) => {
      const product = products.value.find((item) => String(item.id) === String(productId))
      return product || {}
    })
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(t('inventoryChecks.message.detailLoadFailed'))
  }
}

// 查看
const handleView = async (row: InventoryCheck) => {
  dialogTitle.value = t('inventoryChecks.dialog.view')
  isView.value = true
  isEdit.value = false
  try {
    const data = await getInventoryCheck(row.id)
    Object.assign(formData, data)
    formData.items = await hydrateProductLineLabels(formData.items || [], async (productId) => {
      const product = products.value.find((item) => String(item.id) === String(productId))
      return product || {}
    })
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(t('inventoryChecks.message.detailLoadFailed'))
  }
}

const handlePrint = async (row: InventoryCheck) => {
  try {
    const detail = await getInventoryCheck(row.id)
    printInventoryCheck(detail)
  } catch {
    ElMessage.error(t('inventoryChecks.message.printLoadFailed'))
  }
}

// 调整
const handleComplete = async (row: InventoryCheck) => {
  try {
    await ElMessageBox.confirm(t('inventoryChecks.message.adjustConfirm'), t('inventoryChecks.prompt'), {
      confirmButtonText: t('inventoryChecks.action.confirm'),
      cancelButtonText: t('inventoryChecks.action.cancel'),
      type: 'warning'
    })
    await completeInventoryCheck(row.id)
    ElMessage.success(t('inventoryChecks.message.success'))
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(t('inventoryChecks.message.failed'))
    }
  }
}

// 取消
const handleCancel = async (row: InventoryCheck) => {
  try {
    await ElMessageBox.confirm(t('inventoryChecks.message.cancelConfirm'), t('inventoryChecks.prompt'), {
      confirmButtonText: t('inventoryChecks.action.confirm'),
      cancelButtonText: t('inventoryChecks.action.cancel'),
      type: 'warning'
    })
    await cancelInventoryCheck(row.id)
    ElMessage.success(t('inventoryChecks.message.success'))
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(t('inventoryChecks.message.failed'))
    }
  }
}

// 仓库变化 - 加载库存数据
const handleWarehouseChange = async () => {
  if (!formData.warehouseId) return

  try {
    const stockQuery: InventoryStockQuery = {
      pageNo: 1,
      pageSize: 1000,
      warehouseId: formData.warehouseId
    }
    const response = await getInventoryStocks(stockQuery)

    // 自动填充盘点明细
    formData.items = await hydrateProductLineLabels(response.records.map(stock => ({
      productId: stock.productId,
      productCode: stock.productCode,
      productName: stock.productName,
      locationId: stock.locationId ?? undefined,
      lotNo: '',
      productionDate: '',
      expiryDate: '',
      serialNos: '',
      lotControlled: undefined,
      shelfLifeControlled: undefined,
      serialControlled: undefined,
      bookQuantity: stock.quantity,
      actualQuantity: undefined,
      difference: undefined,
      remark: ''
    })), async (productId) => {
      const product = products.value.find((item) => String(item.id) === String(productId))
      return product || {}
    })
  } catch (error) {
    ElMessage.error(t('inventoryChecks.message.stockLoadFailed'))
  }
}

// 添加明细
const handleAddItem = () => {
  formData.items.push({
    productId: 0,
    productCode: '',
    productName: '',
    locationId: undefined,
    lotNo: '',
    productionDate: '',
    expiryDate: '',
    serialNos: '',
    lotControlled: undefined,
    shelfLifeControlled: undefined,
    serialControlled: undefined,
    bookQuantity: 0,
    actualQuantity: undefined,
    difference: undefined,
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
    item.productCode = product.code || product.productCode
    item.productName = product.name || product.productName
    item.bookQuantity = 0 // 需要从库存查询实际账面数量
    item.lotControlled = Boolean(product.lotControlled)
    item.shelfLifeControlled = Boolean(product.shelfLifeControlled)
    item.serialControlled = Boolean(product.serialControlled)
  }
  if (item.productId) {
    const [hydrated] = await hydrateProductLineLabels([item], async () => product || {})
    Object.assign(item, hydrated)
  }
}

const warehouseLabel = (warehouse: Warehouse) => warehouse.name || warehouse.warehouseName || t('inventoryChecks.warehouseFallback', { id: warehouse.id })

const warehouseNameById = (warehouseId: string | number) => {
  const warehouse = warehouses.value.find(item => String(item.id) === String(warehouseId))
  return warehouse ? warehouseLabel(warehouse) : ''
}

const productLabel = (product: Product) => {
  const code = product.code || product.productCode || ''
  const name = product.name || product.productName || ''
  return code && name ? `${code} - ${name}` : name || code || t('inventoryChecks.productFallback', { id: product.id })
}

// 数量变化
const handleQuantityChange = (item: InventoryCheckItem) => {
  if (item.actualQuantity !== undefined) {
    item.difference = item.actualQuantity - (item.bookQuantity || 0)
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      if (formData.items.length === 0) {
        ElMessage.warning(t('inventoryChecks.validation.itemRequired'))
        return
      }

      formData.items = await hydrateProductLineLabels(formData.items, async (productId) => {
        const product = products.value.find((item) => String(item.id) === String(productId))
        return product || {}
      })
      const controlIssues = validateProductControlLines(formData.items.map((item) => ({
        ...item,
        quantity: item.actualQuantity
      })))
      if (controlIssues.length > 0) {
        const issue = controlIssues[0]
        const product = issue.productCode || issue.productName || String(issue.productId)
        ElMessage.warning(t(`inventoryChecks.validation.${issue.messageKey}`, {
          line: issue.index + 1,
          product,
          expected: issue.expectedSerialCount,
          actual: issue.actualSerialCount
        }))
        return
      }

      submitLoading.value = true
      try {
        if (isEdit.value) {
          // 编辑模式
          const updateData: InventoryCheckUpdateRequest = {
            items: formData.items
          }
          await updateInventoryCheck(currentId.value, updateData)
        } else {
          // 新增模式
          await createInventoryCheck(formData)
        }
        ElMessage.success(t('inventoryChecks.message.success'))
        dialogVisible.value = false
        loadData()
      } catch (error) {
        ElMessage.error(t('inventoryChecks.message.failed'))
      } finally {
        submitLoading.value = false
      }
    }
  })
}

// 重置表单
const resetForm = () => {
  formData.warehouseId = 0
  formData.checkDate = formatBusinessDate()
  formData.items = []
  formData.remark = ''
  currentId.value = ''
  formRef.value?.clearValidate()
}

onMounted(async () => {
  await Promise.all([
    loadWarehouses(),
    loadProducts(),
    loadLocations()
  ])
  loadData()
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
