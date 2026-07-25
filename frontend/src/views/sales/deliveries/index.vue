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
        <el-table-column prop="logisticsStatus" :label="t('salesDelivery.logisticsStatus')" width="120" />
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
            <el-button v-if=\"row.status==='POSTED'\" v-permission=\"'sales:delivery:update'\" link type=\"primary\" @click=\"advanceLogistics(row)\">{{ t('salesDelivery.advanceLogistics') }}</el-button>
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
          <el-input v-model="formData.carrierName" :placeholder="t('salesDelivery.carrierPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('salesDelivery.trackingNo')">
          <el-input v-model="formData.trackingNo" :placeholder="t('salesDelivery.trackingPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('salesDelivery.logisticsStatus')">
          <el-select v-model="formData.logisticsStatus" style="width:100%">
            <el-option :label="t('salesDelivery.logistics.pendingShip')" value="PENDING_SHIP" />
            <el-option :label="t('salesDelivery.logistics.pickedUp')" value="PICKED_UP" />
            <el-option :label="t('salesDelivery.logistics.inTransit')" value="IN_TRANSIT" />
            <el-option :label="t('salesDelivery.logistics.delivered')" value="DELIVERED" />
          </el-select>
        </el-form-item>
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
          <el-table-column :label="t('salesDelivery.serialNos')" min-width="180">
            <template #default="{ row }">
              <el-input
                v-model="row.serialNos"
                :placeholder="t('salesDelivery.serialNosPlaceholder')"
                :disabled="isView"
              />
            </template>
          </el-table-column>
          <el-table-column :label="t('salesDelivery.lotNo')" width="140">
            <template #default="{ row }">
              <el-input
                v-model="row.lotNo"
                :placeholder="t('salesDelivery.lotNoPlaceholder')"
                :disabled="isView"
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
                :disabled="isView"
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
                :placeholder="t('salesDelivery.expiryDatePlaceholder')"
                :disabled="isView"
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { RefreshLeft } from '@element-plus/icons-vue'
import {
  getSalesDeliveries,
  getSalesDelivery,
  createSalesDelivery,
  updateSalesDelivery,
  cancelSalesDelivery,
  postSalesDelivery, updateSalesDeliveryLogistics,
  type SalesDeliveryQuery,
  type SalesDeliveryCreateRequest,
  type SalesDelivery,
  type SalesDeliveryItem
} from '@/api/sales'
import { printSalesDelivery } from '@/utils/bizPrint'
import { getSalesOrders, getSalesOrder, type SalesOrder } from '@/api/sales'
import {
  getCustomers,
  getLocations,
  getProduct,
  getProductByBarcode,
  getWarehouses,
  type Customer,
  type Location,
  type Warehouse
} from '@/api/masterdata'
import { BarcodeScanField } from '@/components/common'
import { incrementScannedLine } from '@/utils/barcode'
import { hydrateProductLineLabels } from '@/utils/productLines'
import { formatBusinessDate, formatLocalizedDateTime } from '@/utils/locale'

const route = useRoute()
const { t } = useI18n()
const readQueryString = (key: string) => {
  const value = route.query[key]
  return Array.isArray(value) ? value[0] || '' : typeof value === 'string' ? value : ''
}

// 查询参数
const queryParams = reactive<SalesDeliveryQuery>({
  pageNo: 1,
  pageSize: 10,
  deliveryNo: '',
  orderId: undefined,
  customerId: undefined,
  status: '',
  startDate: '',
  endDate: ''
})
queryParams.deliveryNo = readQueryString('keyword')

// 日期范围
const dateRange = ref<[string, string] | null>(null)

// 表格数据
const loading = ref(false)
const tableData = ref<SalesDelivery[]>([])
const total = ref(0)

// 客户列表
const customers = ref<Customer[]>([])

// 仓库列表
const warehouses = ref<Warehouse[]>([])
const locations = ref<Location[]>([])

// 订单列表（已审批的订单）
const orders = ref<SalesOrder[]>([])

// 对话框
const dialogVisible = ref(false)
const submitLoading = ref(false)
const scanLoading = ref(false)
const scanFeedback = ref('')
const dialogTitle = ref('')
const isView = ref(false)
const editingId = ref<string | number>('')
const formRef = ref<FormInstance>()

// 表单数据
const formData = reactive<SalesDeliveryCreateRequest>({
  orderId: 0,
  warehouseId: 0,
  deliveryDate: '',
  items: [],
  remark: '',
  carrierName: '',
  trackingNo: '',
  logisticsStatus: 'PENDING_SHIP'
})
const deliveryQuantityTotal = computed(() => formData.items.reduce(
  (total, item) => total + Number(item.quantity || 0),
  0
))
const locationsForWarehouse = computed(() => {
  if (!formData.warehouseId) return locations.value
  return locations.value.filter((location) => String(location.warehouseId) === String(formData.warehouseId))
})

// 表单验证规则
const formRules = computed<FormRules>(() => ({
  orderId: [{ required: true, message: t('salesDelivery.validation.order'), trigger: 'change' }],
  warehouseId: [{ required: true, message: t('salesDelivery.validation.warehouse'), trigger: 'change' }],
  deliveryDate: [{ required: true, message: t('salesDelivery.validation.date'), trigger: 'change' }]
}))

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const response = await getSalesDeliveries(queryParams)
    tableData.value = response.records
    total.value = response.total
  } catch (error) {
    ElMessage.error(t('salesDelivery.message.loadFailed'))
  } finally {
    loading.value = false
  }
}

// 加载客户列表
const loadCustomers = async () => {
  try {
    const response = await getCustomers({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
    customers.value = response.records
  } catch (error) {
    ElMessage.error(t('salesDelivery.message.customersLoadFailed'))
  }
}

// 加载仓库列表
const loadWarehouses = async () => {
  try {
    const response = await getWarehouses({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
    warehouses.value = response.records
  } catch (error) {
    ElMessage.error(t('salesDelivery.message.warehousesLoadFailed'))
  }
}

const loadLocations = async (warehouseId?: string | number) => {
  try {
    const page = await getLocations({
      pageNo: 1,
      pageSize: 500,
      status: 'ACTIVE',
      warehouseId: warehouseId || undefined
    })
    locations.value = page.records || []
  } catch {
    locations.value = []
  }
}

const handleWarehouseChange = async (warehouseId?: string | number) => {
  formData.items.forEach((item) => {
    item.locationId = undefined
  })
  await loadLocations(warehouseId)
}

// 加载订单列表
const loadOrders = async () => {
  try {
    const response = await getSalesOrders({ pageNo: 1, pageSize: 1000, status: 'APPROVED' })
    orders.value = response.records
  } catch (error) {
    ElMessage.error(t('salesDelivery.message.ordersLoadFailed'))
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
  queryParams.deliveryNo = ''
  queryParams.orderId = undefined
  queryParams.customerId = undefined
  queryParams.status = ''
  dateRange.value = null
  queryParams.startDate = ''
  queryParams.endDate = ''
  handleQuery()
}

// 新增
const handleCreate = () => {
  resetForm()
  dialogTitle.value = t('salesDelivery.dialog.create')
  dialogVisible.value = true
}

// 查看
const handlePrint = async (row: any) => {
  try {
    const detail = await getSalesDelivery(row.id)
    printSalesDelivery(detail)
  } catch {
    ElMessage.error(t('salesDelivery.message.printLoadFailed'))
  }
}

const handleView = async (row: SalesDelivery) => {
  try {
    const data = await getSalesDelivery(row.id)
    dialogTitle.value = t('salesDelivery.dialog.view')
    isView.value = true
    editingId.value = ''
    Object.assign(formData, data)
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(t('salesDelivery.message.detailLoadFailed'))
  }
}

// 编辑草稿
const handleEdit = async (row: SalesDelivery) => {
  try {
    const detail = await getSalesDelivery(row.id)
    dialogTitle.value = t('salesDelivery.dialog.edit')
    isView.value = false
    editingId.value = detail.id
    // 载入所属订单，供只读展示（草稿不允许改订单）
    let order = orders.value.find(o => String(o.id) === String(detail.orderId))
    if (!order) {
      orders.value = [{
        id: detail.orderId,
        orderNo: detail.orderNo,
        customerName: detail.customerName
      } as SalesOrder, ...orders.value]
    }
    // 后端发货明细不含 productCode/Name 与订单数量；编辑时补齐，
    // 否则 el-input-number 的 max=(ordered-delivered) 会把数量钳成 0 导致无法保存。
    let orderItems: SalesOrder['items'] = []
    try {
      const orderDetail = await getSalesOrder(detail.orderId)
      orderItems = orderDetail.items || []
      order = orderDetail
      const exists = orders.value.some(o => String(o.id) === String(orderDetail.id))
      if (!exists) {
        orders.value = [orderDetail, ...orders.value]
      }
    } catch {
      // 订单详情失败时仍尽量打开编辑弹窗
    }
    formData.orderId = detail.orderId
    formData.warehouseId = detail.warehouseId
    formData.deliveryDate = detail.deliveryDate
    formData.remark = detail.remark || ''
    formData.carrierName = detail.carrierName || ''
    formData.trackingNo = detail.trackingNo || ''
    formData.logisticsStatus = detail.logisticsStatus || 'PENDING_SHIP'
    const deliveryItems = (detail.items || detail.lines || []).map(item => {
      const orderLineId = item.orderLineId ?? item.orderItemId
      const orderItem = orderItems.find(oi => String(oi.id) === String(orderLineId))
      const qty = Number(item.quantity ?? item.qty ?? 0)
      return {
        orderItemId: item.orderItemId ?? item.orderLineId,
        orderLineId,
        productId: item.productId,
        productCode: item.productCode || orderItem?.productCode,
        productName: item.productName || orderItem?.productName,
        orderedQuantity: Number(orderItem?.quantity ?? qty),
        // 草稿未过账，不计入已发货；用订单 delivered 但至少保证 max >= 当前编辑数量
        deliveredQuantity: Math.max(
          0,
          Number(orderItem?.deliveredQuantity ?? 0)
        ),
        quantity: qty,
        locationId: item.locationId ?? undefined,
        serialNos: item.serialNos || '',
        lotNo: item.lotNo || '',
        productionDate: item.productionDate || '',
        expiryDate: item.expiryDate || '',
        remark: item.remark || ''
      }
    })
    formData.items = await hydrateProductLineLabels(deliveryItems, getProduct)
    await loadLocations(formData.warehouseId)
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(t('salesDelivery.message.deliveryLoadFailed'))
  }
}

// 取消
const handleCancel = async (row: SalesDelivery) => {
  try {
    await ElMessageBox.confirm(t('salesDelivery.message.cancelConfirm'), t('salesDelivery.message.prompt'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    })
    await cancelSalesDelivery(row.id)
    ElMessage.success(t('salesDelivery.message.cancelled'))
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(t('salesDelivery.message.cancelFailed'))
    }
  }
}

// 过账
const advanceLogistics = async (row: any) => {
  const order = ['PENDING_SHIP','PICKED_UP','IN_TRANSIT','DELIVERED']
  const current = row.logisticsStatus || 'PENDING_SHIP'
  const idx = order.indexOf(current)
  const next = order[Math.min(idx+1, order.length-1)]
  if (next === current) {
    ElMessage.info(t('salesDelivery.message.logisticsDone'))
    return
  }
  await updateSalesDeliveryLogistics(row.id, { logisticsStatus: next, carrierName: row.carrierName, trackingNo: row.trackingNo })
  ElMessage.success(t('salesDelivery.message.logisticsUpdated'))
  handleQuery()
}

const handlePost = async (row: SalesDelivery) => {
  try {
    await ElMessageBox.confirm(t('salesDelivery.message.postConfirm'), t('salesDelivery.message.prompt'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    })
    await postSalesDelivery(row.id)
    ElMessage.success(t('salesDelivery.message.posted'))
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(t('salesDelivery.message.postFailed'))
    }
  }
}

// 订单变化
const handleOrderChange = async () => {
  if (!formData.orderId) return

  try {
    const order = await getSalesOrder(formData.orderId)

    // 填充发货明细
    const orderItems = order.items.map(item => ({
      orderItemId: item.id,
      productId: item.productId,
      productCode: item.productCode,
      productName: item.productName,
      orderedQuantity: item.quantity,
      deliveredQuantity: item.deliveredQuantity || 0,
      quantity: Math.max(0, item.quantity - (item.deliveredQuantity || 0)),
      locationId: undefined,
      serialNos: '',
      lotNo: '',
      productionDate: '',
      expiryDate: '',
      remark: ''
    }))
    formData.items = await hydrateProductLineLabels(orderItems, getProduct)
    if (order.warehouseId) {
      formData.warehouseId = order.warehouseId
      await loadLocations(order.warehouseId)
    }
  } catch (error) {
    ElMessage.error(t('salesDelivery.message.orderDetailLoadFailed'))
  }
}

const getDeliveryMaximum = (item: SalesDeliveryItem) => Math.max(
  Number(item.quantity || 0),
  Number(item.orderedQuantity || 0) - Number(item.deliveredQuantity || 0),
  0
)

const resetScanQuantities = async () => {
  try {
    await ElMessageBox.confirm(t('salesDelivery.scan.resetConfirm'), t('salesDelivery.scan.title'), {
      confirmButtonText: t('salesDelivery.scan.reset'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    })
    formData.items.forEach((item) => {
      item.quantity = 0
      item.qty = 0
    })
    scanFeedback.value = t('salesDelivery.scan.resetDone')
  } catch (error: any) {
    if (error !== 'cancel' && error?.action !== 'cancel') {
      ElMessage.error(t('salesDelivery.scan.resetFailed'))
    }
  }
}

const handleBarcodeScan = async (barcode: string) => {
  if (!formData.orderId || formData.items.length === 0) {
    ElMessage.warning(t('salesDelivery.scan.selectOrderFirst'))
    return
  }

  scanLoading.value = true
  try {
    const product = await getProductByBarcode(barcode)
    const result = incrementScannedLine(formData.items, product.id, getDeliveryMaximum)
    if (result.status === 'not-found') {
      ElMessage.warning(t('salesDelivery.scan.notInOrder', { code: product.productCode }))
      return
    }
    if (result.status === 'at-maximum') {
      ElMessage.warning(t('salesDelivery.scan.atMaximum', { code: product.productCode }))
      return
    }
    formData.items[result.index].qty = result.quantity
    scanFeedback.value = `${product.productCode} · ${result.quantity}`
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : t('salesDelivery.scan.lookupFailed'))
  } finally {
    scanLoading.value = false
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      if (formData.items.length === 0) {
        ElMessage.warning(t('salesDelivery.validation.lineRequired'))
        return
      }

      // 检查发货数量
      const hasQuantity = formData.items.some(item => item.quantity > 0)
      if (!hasQuantity) {
        ElMessage.warning(t('salesDelivery.validation.quantityRequired'))
        return
      }

      submitLoading.value = true
      try {
        if (editingId.value) {
          await updateSalesDelivery(editingId.value, formData)
          ElMessage.success(t('salesDelivery.message.updated'))
        } else {
          await createSalesDelivery(formData)
          ElMessage.success(t('salesDelivery.message.created'))
        }
        dialogVisible.value = false
        loadData()
      } catch (error) {
        ElMessage.error(editingId.value ? t('salesDelivery.message.updateFailed') : t('salesDelivery.message.createFailed'))
      } finally {
        submitLoading.value = false
      }
    }
  })
}

// 重置表单（含编辑态，避免取消后下次新建误走 PUT）
const resetForm = () => {
  editingId.value = ''
  isView.value = false
  formData.orderId = 0
  formData.warehouseId = 0
  formData.deliveryDate = formatBusinessDate()
  formData.items = []
  formData.remark = ''
  formData.carrierName = ''
  formData.trackingNo = ''
  formData.logisticsStatus = 'PENDING_SHIP'
  scanFeedback.value = ''
  formRef.value?.clearValidate()
}

onMounted(() => {
  loadData()
  loadCustomers()
  loadWarehouses()
  loadLocations()
  loadOrders()
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
</style>
