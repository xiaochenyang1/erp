<template>
  <div class="sales-deliveries-container">
    <!-- 查询表单 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="发货单号">
          <el-input
            v-model="queryParams.deliveryNo"
            placeholder="请输入发货单号"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="销售订单">
          <el-input
            v-model="queryParams.orderId"
            placeholder="请输入订单ID"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="客户">
          <el-select
            v-model="queryParams.customerId"
            placeholder="请选择客户"
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
        <el-form-item label="状态">
          <el-select
            v-model="queryParams.status"
            placeholder="请选择状态"
            clearable
            style="width: 150px"
          >
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已过账" value="POSTED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮 -->
    <el-card class="toolbar-card" shadow="never">
      <el-button v-permission="'sales:delivery:create'" type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        新增发货
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
        <el-table-column prop="deliveryNo" label="发货单号" width="180" />
        <el-table-column prop="orderNo" label="销售订单号" width="180" />
        <el-table-column prop="customerName" label="客户" width="150" />
        <el-table-column prop="warehouseName" label="发货仓库" width="140" />
        <el-table-column prop="deliveryDate" label="发货日期" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'DRAFT'" type="info">草稿</el-tag>
            <el-tag v-else-if="row.status === 'POSTED' || row.status === 'COMPLETED'" type="success">已过账</el-tag>
            <el-tag v-else-if="row.status === 'CANCELLED'" type="danger">已取消</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column prop="createdBy" label="创建人" width="120" />
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">
              查看
            </el-button>
            <el-button link type="primary" @click="handlePrint(row)">
              打印
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'sales:delivery:update'"
              link
              type="primary"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'sales:delivery:post'"
              link
              type="success"
              @click="handlePost(row)"
            >
              过账
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'sales:delivery:cancel'"
              link
              type="danger"
              @click="handleCancel(row)"
            >
              取消
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
            <el-form-item label="销售订单" prop="orderId">
              <el-select
                v-model="formData.orderId"
                placeholder="请选择销售订单"
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
            <el-form-item label="发货仓库" prop="warehouseId">
              <el-select
                v-model="formData.warehouseId"
                placeholder="请选择发货仓库"
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
            <el-form-item label="发货日期" prop="deliveryDate">
              <el-date-picker
                v-model="formData.deliveryDate"
                type="date"
                placeholder="请选择发货日期"
                style="width: 100%"
                value-format="YYYY-MM-DD"
                :disabled="isView"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="2"
            placeholder="请输入备注"
            :disabled="isView"
          />
        </el-form-item>

        <!-- 发货明细 -->
        <el-divider content-position="left">发货明细</el-divider>
        <div v-if="!isView && formData.items.length > 0" class="scan-toolbar">
          <BarcodeScanField :disabled="scanLoading" @scan="handleBarcodeScan" />
          <el-button class="scan-toolbar__reset" :disabled="scanLoading" @click="resetScanQuantities">
            <el-icon><RefreshLeft /></el-icon>
            清零数量
          </el-button>
          <div class="scan-toolbar__summary" aria-live="polite">
            <span>本次数量 <strong>{{ deliveryQuantityTotal }}</strong></span>
            <span v-if="scanFeedback" class="scan-toolbar__feedback">{{ scanFeedback }}</span>
          </div>
        </div>
        <el-table :data="formData.items" border max-height="400">
          <el-table-column label="产品编码" prop="productCode" width="150" />
          <el-table-column label="产品名称" prop="productName" width="180" />
          <el-table-column label="订单数量" prop="orderedQuantity" width="120" align="right">
            <template #default="{ row }">
              {{ row.orderedQuantity || 0 }}
            </template>
          </el-table-column>
          <el-table-column label="已发货数量" prop="deliveredQuantity" width="130" align="right">
            <template #default="{ row }">
              {{ row.deliveredQuantity || 0 }}
            </template>
          </el-table-column>
          <el-table-column label="本次发货数量" prop="quantity" width="150">
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
          <el-table-column label="备注" prop="remark">
            <template #default="{ row }">
              <el-input
                v-model="row.remark"
                placeholder="请输入备注"
                :disabled="isView"
              />
            </template>
          </el-table-column>
        </el-table>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="!isView" type="primary" :loading="submitLoading" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { RefreshLeft } from '@element-plus/icons-vue'
import {
  getSalesDeliveries,
  getSalesDelivery,
  createSalesDelivery,
  updateSalesDelivery,
  cancelSalesDelivery,
  postSalesDelivery,
  type SalesDeliveryQuery,
  type SalesDeliveryCreateRequest,
  type SalesDelivery,
  type SalesDeliveryItem
} from '@/api/sales'
import { printSalesDelivery } from '@/utils/bizPrint'
import { getSalesOrders, getSalesOrder, type SalesOrder } from '@/api/sales'
import { getCustomers, getProduct, getProductByBarcode, type Customer } from '@/api/masterdata'
import { getWarehouses, type Warehouse } from '@/api/masterdata'
import { BarcodeScanField } from '@/components/common'
import { incrementScannedLine } from '@/utils/barcode'
import { hydrateProductLineLabels } from '@/utils/productLines'

const route = useRoute()
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
  remark: ''
})
const deliveryQuantityTotal = computed(() => formData.items.reduce(
  (total, item) => total + Number(item.quantity || 0),
  0
))

// 表单验证规则
const formRules: FormRules = {
  orderId: [{ required: true, message: '请选择销售订单', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择发货仓库', trigger: 'change' }],
  deliveryDate: [{ required: true, message: '请选择发货日期', trigger: 'change' }]
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const response = await getSalesDeliveries(queryParams)
    tableData.value = response.records
    total.value = response.total
  } catch (error) {
    ElMessage.error('加载数据失败')
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
    ElMessage.error('加载客户列表失败')
  }
}

// 加载仓库列表
const loadWarehouses = async () => {
  try {
    const response = await getWarehouses({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
    warehouses.value = response.records
  } catch (error) {
    ElMessage.error('加载仓库列表失败')
  }
}

// 加载订单列表
const loadOrders = async () => {
  try {
    const response = await getSalesOrders({ pageNo: 1, pageSize: 1000, status: 'APPROVED' })
    orders.value = response.records
  } catch (error) {
    ElMessage.error('加载订单列表失败')
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
  dialogTitle.value = '新增销售发货'
  dialogVisible.value = true
}

// 查看
const handlePrint = async (row: any) => {
  try {
    const detail = await getSalesDelivery(row.id)
    printSalesDelivery(detail)
  } catch {
    ElMessage.error('加载打印数据失败')
  }
}

const handleView = async (row: SalesDelivery) => {
  try {
    const data = await getSalesDelivery(row.id)
    dialogTitle.value = '查看销售发货'
    isView.value = true
    editingId.value = ''
    Object.assign(formData, data)
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载详情失败')
  }
}

// 编辑草稿
const handleEdit = async (row: SalesDelivery) => {
  try {
    const detail = await getSalesDelivery(row.id)
    dialogTitle.value = '编辑销售发货'
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
        remark: item.remark || ''
      }
    })
    formData.items = await hydrateProductLineLabels(deliveryItems, getProduct)
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载发货单失败')
  }
}

// 取消
const handleCancel = async (row: SalesDelivery) => {
  try {
    await ElMessageBox.confirm('确认取消此发货单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await cancelSalesDelivery(row.id)
    ElMessage.success('操作成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

// 过账
const handlePost = async (row: SalesDelivery) => {
  try {
    await ElMessageBox.confirm('确定过账该销售发货单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await postSalesDelivery(row.id)
    ElMessage.success('过账成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('过账失败')
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
      remark: ''
    }))
    formData.items = await hydrateProductLineLabels(orderItems, getProduct)
  } catch (error) {
    ElMessage.error('加载订单详情失败')
  }
}

const getDeliveryMaximum = (item: SalesDeliveryItem) => Math.max(
  Number(item.quantity || 0),
  Number(item.orderedQuantity || 0) - Number(item.deliveredQuantity || 0),
  0
)

const resetScanQuantities = async () => {
  try {
    await ElMessageBox.confirm('确认清零当前发货数量吗？', '扫码计数', {
      confirmButtonText: '清零',
      cancelButtonText: '取消',
      type: 'warning'
    })
    formData.items.forEach((item) => {
      item.quantity = 0
      item.qty = 0
    })
    scanFeedback.value = '数量已清零'
  } catch (error: any) {
    if (error !== 'cancel' && error?.action !== 'cancel') {
      ElMessage.error('清零数量失败')
    }
  }
}

const handleBarcodeScan = async (barcode: string) => {
  if (!formData.orderId || formData.items.length === 0) {
    ElMessage.warning('请先选择销售订单')
    return
  }

  scanLoading.value = true
  try {
    const product = await getProductByBarcode(barcode)
    const result = incrementScannedLine(formData.items, product.id, getDeliveryMaximum)
    if (result.status === 'not-found') {
      ElMessage.warning(`商品 ${product.productCode} 不在当前销售订单中`)
      return
    }
    if (result.status === 'at-maximum') {
      ElMessage.warning(`商品 ${product.productCode} 已达到可发货数量`)
      return
    }
    formData.items[result.index].qty = result.quantity
    scanFeedback.value = `${product.productCode} · ${result.quantity}`
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '条码查询失败')
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
        ElMessage.warning('请至少添加一条发货明细')
        return
      }

      // 检查发货数量
      const hasQuantity = formData.items.some(item => item.quantity > 0)
      if (!hasQuantity) {
        ElMessage.warning('请输入发货数量')
        return
      }

      submitLoading.value = true
      try {
        if (editingId.value) {
          await updateSalesDelivery(editingId.value, formData)
          ElMessage.success('更新成功')
        } else {
          await createSalesDelivery(formData)
          ElMessage.success('操作成功')
        }
        dialogVisible.value = false
        loadData()
      } catch (error) {
        ElMessage.error(editingId.value ? '更新失败' : '操作失败')
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
  formData.deliveryDate = new Date().toISOString().split('T')[0]
  formData.items = []
  formData.remark = ''
  scanFeedback.value = ''
  formRef.value?.clearValidate()
}

onMounted(() => {
  loadData()
  loadCustomers()
  loadWarehouses()
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
