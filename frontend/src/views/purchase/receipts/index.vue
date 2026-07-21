<template>
  <div class="purchase-receipt-management">
    <!-- 页面标题 - 使用青色主题 -->
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <div class="icon-cube">
            <el-icon class="header-icon">
              <Box />
            </el-icon>
            <div class="icon-particles"></div>
          </div>
          <div class="header-text">
            <h1 class="page-title">采购收货管理</h1>
            <p class="page-subtitle">管理采购收货入库，确保物料按时到位</p>
          </div>
        </div>
        <div class="header-stats">
          <div class="stat-card">
            <span class="stat-label">收货总数</span>
            <span class="stat-value">{{ total }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">待完成</span>
            <span class="stat-value draft">{{ draftCount }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">已完成</span>
            <span class="stat-value completed">{{ completedCount }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 搜索栏 -->
    <search-bar v-model="queryForm" @search="handleQuery" @reset="handleReset">
      <el-form-item label="收货单号" prop="receiptNo">
        <el-input
          v-model="queryForm.receiptNo"
          placeholder="请输入收货单号"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="采购订单" prop="orderId">
        <el-input
          v-model="queryForm.orderId"
          placeholder="请输入订单ID"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="供应商" prop="supplierId">
        <el-select v-model="queryForm.supplierId" placeholder="请选择供应商" clearable>
          <el-option label="全部供应商" value="" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryForm.status" placeholder="请选择状态" clearable>
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已过账" value="POSTED" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
      </el-form-item>
      <el-form-item label="收货日期" prop="dateRange">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          @change="handleDateChange"
        />
      </el-form-item>
    </search-bar>

    <!-- 数据表格 -->
    <page-table
      :data="tableData"
      :loading="loading"
      :total="total"
      :page="queryForm.pageNo"
      :page-size="queryForm.pageSize"
      create-text="新增收货"
      :show-create="canCreate"
      @create="handleAdd"
      @export="handleExport"
      @refresh="handleQuery"
      @page-change="handlePageChange"
      class="receipt-table"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column prop="receiptNo" label="收货单号" width="160" fixed>
        <template #default="{ row }">
          <span class="receipt-no">{{ row.receiptNo }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="orderNo" label="采购订单号" width="160">
        <template #default="{ row }">
          <el-link type="primary" @click="viewOrder(row.orderId)">{{ row.orderNo }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="supplierName" label="供应商" width="160" show-overflow-tooltip />
      <el-table-column prop="warehouseName" label="入库仓库" width="140" />
      <el-table-column prop="receiptDate" label="收货日期" width="120" align="center" />
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <status-tag :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="createdBy" label="创建人" width="100" />
      <el-table-column prop="createdAt" label="创建时间" width="160" />
      <el-table-column label="操作" width="220" fixed="right" align="center">
        <template #default="{ row }">
          <div class="action-buttons">
            <el-button link type="primary" size="small" @click="handleView(row)">
              <el-icon><View /></el-icon>
              查看
            </el-button>
            <el-button link type="primary" size="small" @click="handlePrint(row)">
              打印
            </el-button>
            <el-button v-if="row.status === 'DRAFT'" v-permission="'purchase:receipt:update'" link type="primary" size="small" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button v-if="row.status === 'DRAFT'" v-permission="'purchase:receipt:post'" link type="success" size="small" @click="handleComplete(row)">
              <el-icon><CircleCheck /></el-icon>
              过账
            </el-button>
            <el-button v-if="row.status === 'DRAFT'" v-permission="'purchase:receipt:cancel'" link type="danger" size="small" @click="handleCancel(row)">
              <el-icon><CircleClose /></el-icon>
              取消
            </el-button>
          </div>
        </template>
      </el-table-column>
    </page-table>

    <!-- 新增/编辑收货对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑采购收货' : '新增采购收货'"
      width="1000px"
      :close-on-click-modal="false"
      class="elegant-dialog receipt-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
        <div class="form-section">
          <div class="section-title">基本信息</div>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="采购订单" prop="orderId">
                <el-select
                  v-model="form.orderId"
                  placeholder="请选择采购订单"
                  style="width: 100%"
                  :disabled="!!editingId"
                  @change="handleOrderChange"
                >
                  <el-option
                    v-for="order in availableOrders"
                    :key="order.id"
                    :label="`${order.orderNo} - ${order.supplierName}`"
                    :value="order.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="收货日期" prop="receiptDate">
                <el-date-picker
                  v-model="form.receiptDate"
                  type="date"
                  placeholder="选择日期"
                  style="width: 100%"
                  value-format="YYYY-MM-DD"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="入库仓库" prop="warehouseId">
                <el-select v-model="form.warehouseId" placeholder="请选择仓库" style="width: 100%">
                  <el-option
                    v-for="warehouse in warehouses"
                    :key="warehouse.id"
                    :label="warehouse.name || warehouse.warehouseName"
                    :value="warehouse.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <div class="form-section" v-if="form.items.length > 0">
          <div class="section-title scan-section-title">
            <span>收货明细</span>
            <el-button :disabled="scanLoading" @click="resetScanQuantities">
              <el-icon><RefreshLeft /></el-icon>
              清零数量
            </el-button>
          </div>
          <div class="scan-toolbar">
            <BarcodeScanField :disabled="scanLoading" @scan="handleBarcodeScan" />
            <div class="scan-toolbar__summary" aria-live="polite">
              <span>本次数量 <strong>{{ receiptQuantityTotal }}</strong></span>
              <span v-if="scanFeedback" class="scan-toolbar__feedback">{{ scanFeedback }}</span>
            </div>
          </div>
          <el-table :data="form.items" border class="items-table">
            <el-table-column label="序号" type="index" width="60" align="center" />
            <el-table-column label="商品名称" prop="productName" min-width="180" />
            <el-table-column label="订单数量" prop="orderedQuantity" width="100" align="center" />
            <el-table-column label="实际收货" width="140">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.quantity"
                  :min="0"
                  :max="getReceiptMaximum(row)"
                  :controls="false"
                  style="width: 100%"
                />
              </template>
            </el-table-column>
            <el-table-column label="备注">
              <template #default="{ row }">
                <el-input v-model="row.remark" placeholder="选填" />
              </template>
            </el-table-column>
          </el-table>
        </div>

        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注信息（选填）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmitForm">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情查看对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="收货单详情"
      width="850px"
      class="elegant-dialog receipt-dialog"
    >
      <detail-card v-if="currentRow">
        <div class="detail-section">
          <div class="section-title">
            <el-icon><Document /></el-icon>
            收货信息
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">收货单号</div>
              <div class="detail-value">{{ currentRow.receiptNo }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">采购订单</div>
              <div class="detail-value">
                <el-link type="primary" @click="viewOrder(currentRow.orderId)">{{ currentRow.orderNo }}</el-link>
              </div>
            </div>
            <div class="detail-item">
              <div class="detail-label">供应商</div>
              <div class="detail-value">{{ currentRow.supplierName }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">入库仓库</div>
              <div class="detail-value">{{ currentRow.warehouseName }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">收货日期</div>
              <div class="detail-value">{{ currentRow.receiptDate }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">状态</div>
              <div class="detail-value">
                <status-tag :status="currentRow.status" />
              </div>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">
            <el-icon><List /></el-icon>
            收货明细
          </div>
          <el-table :data="currentRow.items" border class="detail-table">
            <el-table-column label="序号" type="index" width="60" align="center" />
            <el-table-column prop="productName" label="商品名称" min-width="180" />
            <el-table-column prop="orderedQuantity" label="订单数量" width="100" align="center" />
            <el-table-column prop="quantity" label="实收数量" width="100" align="center" />
            <el-table-column prop="remark" label="备注" min-width="120" />
          </el-table>
        </div>

        <div class="detail-section">
          <div class="section-title">
            <el-icon><Clock /></el-icon>
            其他信息
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">创建人</div>
              <div class="detail-value">{{ currentRow.createdBy }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">创建时间</div>
              <div class="detail-value">{{ currentRow.createdAt }}</div>
            </div>
            <div class="detail-item" style="grid-column: 1 / -1">
              <div class="detail-label">备注</div>
              <div class="detail-value">{{ currentRow.remark || '-' }}</div>
            </div>
          </div>
        </div>
      </detail-card>
    </el-dialog>

    <!-- 关联采购订单详情 -->
    <el-dialog
      v-model="linkedOrderVisible"
      title="采购订单详情"
      width="900px"
      class="elegant-dialog receipt-dialog"
    >
      <detail-card v-if="linkedOrder" v-loading="linkedOrderLoading">
        <div class="detail-section">
          <div class="section-title">
            <el-icon><Document /></el-icon>
            订单信息
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">订单编号</div>
              <div class="detail-value">{{ linkedOrder.orderNo }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">供应商</div>
              <div class="detail-value">{{ linkedOrder.supplierName }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">订单日期</div>
              <div class="detail-value">{{ linkedOrder.orderDate }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">预计到货</div>
              <div class="detail-value">{{ linkedOrder.expectedDate || '-' }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">状态</div>
              <div class="detail-value">
                <status-tag :status="linkedOrder.status" />
              </div>
            </div>
            <div class="detail-item">
              <div class="detail-label">订单金额</div>
              <div class="detail-value amount">
                ¥{{ linkedOrder.totalAmount?.toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}
              </div>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">
            <el-icon><List /></el-icon>
            订单明细
          </div>
          <el-table :data="linkedOrder.items" border class="detail-table">
            <el-table-column label="序号" type="index" width="60" align="center" />
            <el-table-column prop="productName" label="商品名称" min-width="180" />
            <el-table-column prop="quantity" label="订单数量" width="100" align="center" />
            <el-table-column prop="receivedQty" label="已收数量" width="100" align="center" />
            <el-table-column prop="price" label="单价" width="120" align="right">
              <template #default="{ row }">
                ¥{{ row.price?.toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}
              </template>
            </el-table-column>
            <el-table-column prop="amount" label="金额" width="140" align="right">
              <template #default="{ row }">
                ¥{{ row.amount?.toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="120" />
          </el-table>
        </div>

        <div class="detail-section">
          <div class="section-title">
            <el-icon><Clock /></el-icon>
            其他信息
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">创建人</div>
              <div class="detail-value">{{ linkedOrder.createdBy }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">创建时间</div>
              <div class="detail-value">{{ linkedOrder.createdAt }}</div>
            </div>
            <div class="detail-item" style="grid-column: 1 / -1">
              <div class="detail-label">备注</div>
              <div class="detail-value">{{ linkedOrder.remark || '-' }}</div>
            </div>
          </div>
        </div>
      </detail-card>
      <div v-else v-loading="linkedOrderLoading" class="linked-detail-loading">
        加载采购订单详情...
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  Box,
  View,
  Edit,
  CircleCheck,
  CircleClose,
  Document,
  List,
  Clock,
  RefreshLeft
} from '@element-plus/icons-vue'
import {
  getPurchaseReceipts,
  createPurchaseReceipt,
  updatePurchaseReceipt,
  getPurchaseReceipt,
  completePurchaseReceipt,
  cancelPurchaseReceipt,
  exportPurchaseReceipts,
  getPurchaseOrders,
  getPurchaseOrder,
  type PurchaseReceipt,
  type PurchaseReceiptQuery,
  type PurchaseReceiptCreateRequest,
  type PurchaseOrder,
  type PurchaseReceiptItem
} from '@/api/purchase'
import { printPurchaseReceipt } from '@/utils/bizPrint'
import { getProduct, getProductByBarcode, getWarehouses, type Warehouse } from '@/api/masterdata'
import { BarcodeScanField, PageTable, SearchBar, StatusTag, DetailCard } from '@/components/common'
import { incrementScannedLine } from '@/utils/barcode'
import { hydrateProductLineLabels } from '@/utils/productLines'
import { downloadBlob } from '@/utils/download'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
const canCreate = computed(() => userStore.hasPermission('purchase:receipt:create'))

const route = useRoute()
const readQueryString = (key: string) => {
  const value = route.query[key]
  return Array.isArray(value) ? value[0] || '' : typeof value === 'string' ? value : ''
}

// 查询表单
const queryForm = reactive<PurchaseReceiptQuery>({
  pageNo: 1,
  pageSize: 20,
  receiptNo: '',
  orderId: undefined,
  supplierId: undefined,
  status: '',
  startDate: '',
  endDate: ''
})
queryForm.receiptNo = readQueryString('keyword')

// 日期范围
const dateRange = ref<[string, string]>()

// 表格数据
const tableData = ref<PurchaseReceipt[]>([])
const total = ref(0)
const loading = ref(false)
const draftCount = computed(() => tableData.value.filter(item => item.status === 'DRAFT').length)
const completedCount = computed(() => tableData.value.filter(item => item.status === 'POSTED' || item.status === 'COMPLETED').length)

// 对话框
const dialogVisible = ref(false)
const editingId = ref<string | number>('')
const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const scanLoading = ref(false)
const scanFeedback = ref('')
const detailVisible = ref(false)
const currentRow = ref<PurchaseReceipt>()
const linkedOrderVisible = ref(false)
const linkedOrderLoading = ref(false)
const linkedOrder = ref<PurchaseOrder>()

// 可用订单列表
const availableOrders = ref<PurchaseOrder[]>([])
const warehouses = ref<Warehouse[]>([])

// 表单数据
const form = reactive<PurchaseReceiptCreateRequest>({
  orderId: '',
  warehouseId: '',
  receiptDate: '',
  items: [],
  remark: ''
})
const receiptQuantityTotal = computed(() => form.items.reduce(
  (total, item) => total + Number(item.quantity || 0),
  0
))

// 表单验证规则
const formRules: FormRules = {
  orderId: [{ required: true, message: '请选择采购订单', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择入库仓库', trigger: 'change' }],
  receiptDate: [{ required: true, message: '请选择收货日期', trigger: 'change' }]
}

// 查询数据
const handleQuery = async () => {
  loading.value = true
  try {
    const res = await getPurchaseReceipts(queryForm)
    tableData.value = res.records
    total.value = res.total
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

// 重置查询
const handleReset = () => {
  queryForm.receiptNo = ''
  queryForm.orderId = undefined
  queryForm.supplierId = undefined
  queryForm.status = ''
  queryForm.startDate = ''
  queryForm.endDate = ''
  queryForm.pageNo = 1
  dateRange.value = undefined
  handleQuery()
}

// 日期范围变化
const handleDateChange = (dates: [string, string] | null) => {
  if (dates) {
    queryForm.startDate = dates[0]
    queryForm.endDate = dates[1]
  } else {
    queryForm.startDate = ''
    queryForm.endDate = ''
  }
}

// 分页
const handlePageChange = (page: number, size: number) => {
  queryForm.pageNo = page
  queryForm.pageSize = size
  handleQuery()
}

// 新增
const handleAdd = async () => {
  // 加载已审核的采购订单
  try {
    const res = await getPurchaseOrders({
      pageNo: 1,
      pageSize: 100,
      status: 'APPROVED'
    })
    availableOrders.value = res.records

    if (availableOrders.value.length === 0) {
      ElMessage.warning('暂无可收货的采购订单')
      return
    }

    resetForm()
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载订单失败')
  }
}

// 编辑草稿
const handleEdit = async (row: PurchaseReceipt) => {
  try {
    const detail = await getPurchaseReceipt(row.id)
    editingId.value = detail.id
    // 载入所属订单，供只读展示（草稿不允许改订单）
    availableOrders.value = [{
      id: detail.orderId,
      orderNo: detail.orderNo,
      supplierName: detail.supplierName
    } as PurchaseOrder]
    form.orderId = detail.orderId
    form.warehouseId = detail.warehouseId
    form.receiptDate = detail.receiptDate
    form.remark = detail.remark || ''
    const receiptItems = (detail.items || detail.lines || []).map(item => ({
      orderItemId: item.orderItemId,
      orderLineId: item.orderLineId ?? item.orderItemId,
      productId: item.productId,
      productCode: item.productCode,
      productName: item.productName,
      orderedQuantity: item.orderedQuantity ?? item.quantity,
      receivedQuantity: 0,
      quantity: item.quantity,
      qty: item.qty ?? item.quantity,
      remark: item.remark || ''
    }))
    form.items = await hydrateProductLineLabels(receiptItems, getProduct)
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载收货单失败')
  }
}

// 订单变更
const handleOrderChange = async (orderId: string | number) => {
  const summary = availableOrders.value.find(o => String(o.id) === String(orderId))
  const order = summary?.items?.length ? summary : await getPurchaseOrder(orderId)
  if (order) {
    const orderItems = order.items.map(item => ({
      orderItemId: item.id,
      orderLineId: item.id,
      productId: item.productId,
      productCode: item.productCode,
      productName: item.productName,
      orderedQuantity: item.quantity,
      receivedQuantity: item.receivedQty || 0,
      quantity: Math.max(0, item.quantity - (item.receivedQty || 0)),
      remark: ''
    }))
    form.items = await hydrateProductLineLabels(orderItems, getProduct)
  }
}

const getReceiptMaximum = (item: PurchaseReceiptItem) => Math.max(
  0,
  Number(item.orderedQuantity || 0) - Number(item.receivedQuantity || 0)
)

const resetScanQuantities = async () => {
  try {
    await ElMessageBox.confirm('确认清零当前收货数量吗？', '扫码计数', {
      confirmButtonText: '清零',
      cancelButtonText: '取消',
      type: 'warning'
    })
    form.items.forEach((item) => {
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
  if (!form.orderId || form.items.length === 0) {
    ElMessage.warning('请先选择采购订单')
    return
  }

  scanLoading.value = true
  try {
    const product = await getProductByBarcode(barcode)
    const result = incrementScannedLine(form.items, product.id, getReceiptMaximum)
    if (result.status === 'not-found') {
      ElMessage.warning(`商品 ${product.productCode} 不在当前采购订单中`)
      return
    }
    if (result.status === 'at-maximum') {
      ElMessage.warning(`商品 ${product.productCode} 已达到可收货数量`)
      return
    }
    form.items[result.index].qty = result.quantity
    scanFeedback.value = `${product.productCode} · ${result.quantity}`
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '条码查询失败')
  } finally {
    scanLoading.value = false
  }
}

// 查看
const handlePrint = async (row: any) => {
  try {
    const detail = await getPurchaseReceipt(row.id)
    printPurchaseReceipt(detail)
  } catch {
    ElMessage.error('加载打印数据失败')
  }
}

const handleView = (row: PurchaseReceipt) => {
  currentRow.value = row
  detailVisible.value = true
}

// 查看订单
const viewOrder = async (orderId: string | number) => {
  if (!orderId) {
    ElMessage.warning('缺少采购订单ID')
    return
  }

  linkedOrderVisible.value = true
  linkedOrder.value = undefined
  linkedOrderLoading.value = true
  try {
    linkedOrder.value = await getPurchaseOrder(orderId)
  } catch (error) {
    ElMessage.error('加载采购订单详情失败')
    linkedOrderVisible.value = false
  } finally {
    linkedOrderLoading.value = false
  }
}

// 完成收货
const handleComplete = async (row: PurchaseReceipt) => {
  try {
    await ElMessageBox.confirm(
      `确认过账收货单"${row.receiptNo}"吗？过账后将入库并生成应付。`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'success'
      }
    )

    await completePurchaseReceipt(row.id)
    ElMessage.success('过账成功')
    handleQuery()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

// 取消收货
const handleCancel = async (row: PurchaseReceipt) => {
  try {
    await ElMessageBox.confirm(
      `确认取消收货单"${row.receiptNo}"吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await cancelPurchaseReceipt(row.id)
    ElMessage.success('已取消')
    handleQuery()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

// 导出
const handleExport = async () => {
  try {
    const blob = await exportPurchaseReceipts(queryForm)
    downloadBlob(blob, `采购收货_${Date.now()}.csv`)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

// 提交表单
const handleSubmitForm = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    if (form.items.length === 0) {
      ElMessage.warning('请选择采购订单')
      return
    }

    submitLoading.value = true
    try {
      if (editingId.value) {
        await updatePurchaseReceipt(editingId.value, form)
        ElMessage.success('更新成功')
      } else {
        await createPurchaseReceipt(form)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      handleQuery()
    } catch (error) {
      ElMessage.error(editingId.value ? '更新失败' : '创建失败')
    } finally {
      submitLoading.value = false
    }
  })
}

// 重置表单
const resetForm = () => {
  editingId.value = ''
  form.orderId = ''
  form.warehouseId = ''
  form.receiptDate = ''
  form.items = []
  form.remark = ''
  scanFeedback.value = ''
  formRef.value?.resetFields()
}

const loadWarehouses = async () => {
  const response = await getWarehouses({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
  warehouses.value = response.records
}

// 初始化
onMounted(() => {
  handleQuery()
  loadWarehouses().catch(() => ElMessage.error('加载仓库失败'))
})
</script>

<style scoped>
.purchase-receipt-management {
  padding: 24px;
  min-height: 100vh;
  background: linear-gradient(135deg, #ecfeff 0%, #e0f2fe 100%);
  font-family: 'Plus Jakarta Sans', 'Segoe UI', system-ui, -apple-system, sans-serif;
}

.pageNo-header {
  margin-bottom: 24px;
  animation: slideDown 0.4s ease-out;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.header-content {
  background: linear-gradient(135deg, #06b6d4 0%, #0891b2 100%);
  border-radius: 16px;
  padding: 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 8px 32px rgba(6, 182, 212, 0.3);
  position: relative;
  overflow: hidden;
}

.header-content::before {
  content: '';
  position: absolute;
  width: 100%;
  height: 100%;
  background: repeating-linear-gradient(
    45deg,
    transparent,
    transparent 10px,
    rgba(255, 255, 255, 0.03) 10px,
    rgba(255, 255, 255, 0.03) 20px
  );
}

.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
  position: relative;
  z-index: 1;
}

.icon-cube {
  position: relative;
  width: 70px;
  height: 70px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 18px;
  border: 2px solid rgba(255, 255, 255, 0.25);
  transform-style: preserve-3d;
  animation: cube3D 4s ease-in-out infinite;
}

@keyframes cube3D {
  0%, 100% {
    transform: rotateY(0deg) rotateX(0deg);
  }
  50% {
    transform: rotateY(15deg) rotateX(15deg);
  }
}

.header-icon {
  font-size: 44px;
  color: #ffffff;
}

.icon-particles {
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 18px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.3) 1px, transparent 1px);
  background-size: 8px 8px;
  animation: particles 3s linear infinite;
}

@keyframes particles {
  0% {
    background-position: 0 0;
    opacity: 0.3;
  }
  100% {
    background-position: 8px 8px;
    opacity: 0.1;
  }
}

.header-text {
  color: #ffffff;
}

.pageNo-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 8px 0;
  letter-spacing: 0.5px;
}

.pageNo-subtitle {
  font-size: 14px;
  margin: 0;
  opacity: 0.95;
  font-weight: 400;
}

.header-stats {
  display: flex;
  gap: 16px;
  position: relative;
  z-index: 1;
}

.stat-card {
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 12px;
  padding: 16px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  transition: all 0.3s ease;
  min-width: 100px;
}

.stat-card:hover {
  background: rgba(255, 255, 255, 0.25);
  transform: translateY(-3px);
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.2);
}

.stat-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.95);
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.stat-value {
  font-size: 24px;
  color: #ffffff;
  font-weight: 700;
}

.stat-value.draft {
  color: #fcd34d;
  text-shadow: 0 0 10px rgba(252, 211, 77, 0.6);
}

.stat-value.completed {
  color: #a7f3d0;
  text-shadow: 0 0 10px rgba(167, 243, 208, 0.6);
}

.receipt-table {
  animation: fadeIn 0.5s ease-out 0.1s both;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.receipt-no {
  font-family: 'Courier New', monospace;
  font-weight: 600;
  color: #06b6d4;
  font-size: 13px;
}

.action-buttons {
  display: flex;
  gap: 4px;
  justify-content: center;
  flex-wrap: wrap;
}

.action-buttons :deep(.el-button) {
  padding: 4px 8px;
  transition: all 0.2s ease;
}

.action-buttons :deep(.el-button:hover) {
  transform: translateY(-1px);
}

.receipt-dialog :deep(.el-dialog__header) {
  background: linear-gradient(to right, #ecfeff, #e0f2fe);
  padding: 24px 32px;
  border-bottom: 1px solid #a5f3fc;
}

.receipt-dialog :deep(.el-dialog__title) {
  font-size: 18px;
  font-weight: 600;
  color: #2c3e50;
  letter-spacing: 0.3px;
}

.receipt-dialog :deep(.el-dialog__body) {
  padding: 24px 32px;
}

.form-section {
  margin-bottom: 24px;
  padding: 20px;
  background: #f0fdfa;
  border-radius: 8px;
  border: 1px solid #ccfbf1;
}

.form-section .section-title {
  font-size: 15px;
  font-weight: 600;
  color: #06b6d4;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 2px solid #06b6d4;
}

.scan-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.scan-section-title :deep(.el-button) {
  min-height: 40px;
}

.scan-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 44px;
  margin-bottom: 14px;
}

.scan-toolbar__summary {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 132px;
  color: #475569;
  font-size: 13px;
  line-height: 1.4;
}

.scan-toolbar__summary strong,
.scan-toolbar__feedback {
  font-variant-numeric: tabular-nums;
}

.scan-toolbar__feedback {
  color: #047857;
}

.items-table {
  margin-top: 12px;
}

.items-table :deep(.el-input-number) {
  width: 100%;
}

.detail-table {
  margin-top: 12px;
}

.detail-section .section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #495057;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 2px solid #06b6d4;
  letter-spacing: 0.3px;
}

.detail-section .section-title .el-icon {
  color: #06b6d4;
  font-size: 16px;
}

.linked-detail-loading {
  min-height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
}

@media (max-width: 720px) {
  .scan-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
