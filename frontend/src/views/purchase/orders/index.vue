<template>
  <div class="purchase-order-management">
    <!-- 页面标题 - 使用深蓝色专业主题 -->
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <div class="icon-frame">
            <el-icon class="header-icon">
              <ShoppingCartFull />
            </el-icon>
            <div class="icon-waves"></div>
          </div>
          <div class="header-text">
            <h1 class="page-title">采购订单管理</h1>
            <p class="page-subtitle">管理采购订单全生命周期，优化供应链效率</p>
          </div>
        </div>
        <div class="header-stats">
          <div class="stat-card">
            <span class="stat-label">订单总数</span>
            <span class="stat-value">{{ total }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">待审核</span>
            <span class="stat-value pending">{{ pendingCount }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">已审核</span>
            <span class="stat-value approved">{{ approvedCount }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 搜索栏 -->
    <search-bar v-model="queryForm" @search="handleQuery" @reset="handleReset">
      <el-form-item label="订单编号" prop="orderNo">
        <el-input
          v-model="queryForm.orderNo"
          placeholder="请输入订单编号"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="供应商" prop="supplierId">
        <el-select v-model="queryForm.supplierId" placeholder="请选择供应商" clearable>
          <el-option label="全部供应商" value="" />
          <el-option
            v-for="supplier in suppliers"
            :key="supplier.id"
            :label="supplier.name || supplier.supplierName"
            :value="supplier.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="订单状态" prop="status">
        <el-select v-model="queryForm.status" placeholder="请选择状态" clearable>
          <el-option label="草稿" value="DRAFT" />
          <el-option label="审批中" value="SUBMITTED" />
          <el-option label="已审核" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
          <el-option label="已关闭" value="CLOSED" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
      </el-form-item>
      <el-form-item label="订单日期" prop="dateRange">
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
      create-text="新增订单"
      :show-create="canCreate"
      @create="handleAdd"
      @export="handleExport"
      @refresh="handleQuery"
      @page-change="handlePageChange"
      class="purchase-table"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column prop="orderNo" label="订单编号" width="160" fixed>
        <template #default="{ row }">
          <span class="order-no">{{ row.orderNo }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="supplierName" label="供应商" width="160" show-overflow-tooltip />
      <el-table-column prop="orderDate" label="订单日期" width="120" align="center" />
      <el-table-column prop="expectedDate" label="预计到货" width="120" align="center" />
      <el-table-column prop="totalAmount" label="订单金额" width="140" align="right">
        <template #default="{ row }">
          <span class="amount-value">¥{{ row.totalAmount?.toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="订单状态" width="110" align="center">
        <template #default="{ row }">
          <status-tag :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="createdBy" label="创建人" width="100" />
      <el-table-column prop="createdAt" label="创建时间" width="160" />
      <el-table-column label="操作" width="280" fixed="right" align="center">
        <template #default="{ row }">
          <div class="action-buttons">
            <el-button link type="primary" size="small" @click="handleView(row)">
              <el-icon><View /></el-icon>
              查看
            </el-button>
            <el-button link type="primary" size="small" @click="handlePrint(row)">
              打印
            </el-button>
            <el-button v-permission="'purchase:order:create'" link type="primary" size="small" @click="handleCopy(row)">
              复制
            </el-button>
            <el-button v-if="row.status === 'DRAFT'" v-permission="'purchase:order:update'" link type="primary" size="small" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button v-if="row.status === 'DRAFT'" v-permission="'purchase:order:submit'" link type="success" size="small" @click="handleSubmit(row)">
              <el-icon><Check /></el-icon>
              提交
            </el-button>
            <el-button v-if="row.status === 'SUBMITTED' || row.status === 'PENDING' || row.approvalStatus === 'IN_APPROVAL'" v-permission="'purchase:order:approve'" link type="success" size="small" @click="handleApprove(row)">
              <el-icon><CircleCheck /></el-icon>
              审核
            </el-button>
            <el-button v-if="row.status === 'SUBMITTED' || row.status === 'PENDING' || row.approvalStatus === 'IN_APPROVAL'" v-permission="'purchase:order:reject'" link type="warning" size="small" @click="handleReject(row)">
              <el-icon><CircleClose /></el-icon>
              驳回
            </el-button>
            <el-button v-if="canUnapproveOrder(row)" v-permission="'purchase:order:unapprove'" link type="warning" size="small" @click="handleUnapprove(row)">
              <el-icon><RefreshLeft /></el-icon>
              反审核
            </el-button>
            <el-button v-if="canCloseOrder(row)" v-permission="'purchase:order:close'" link type="warning" size="small" @click="handleCloseOrder(row)">
              <el-icon><CircleClose /></el-icon>
              关闭
            </el-button>
            <el-button v-if="canCancelOrder(row)" v-permission="'purchase:order:cancel'" link type="danger" size="small" @click="handleCancelOrder(row)">
              <el-icon><Delete /></el-icon>
              取消
            </el-button>
            <el-button link type="primary" size="small" @click="handleTraceOrder(row)">
              <el-icon><List /></el-icon>
              追踪
            </el-button>
          </div>
        </template>
      </el-table-column>
    </page-table>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="1000px"
      :close-on-click-modal="false"
      class="elegant-dialog purchase-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
        <div class="form-section">
          <div class="section-title">基本信息</div>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="供应商" prop="supplierId">
                <el-select v-model="form.supplierId" placeholder="请选择供应商" style="width: 100%">
                  <el-option
                    v-for="supplier in suppliers"
                    :key="supplier.id"
                    :label="supplier.name || supplier.supplierName"
                    :value="supplier.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="订单日期" prop="orderDate">
                <el-date-picker
                  v-model="form.orderDate"
                  type="date"
                  placeholder="选择日期"
                  style="width: 100%"
                  value-format="YYYY-MM-DD"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="预计到货日期" prop="expectedDate">
                <el-date-picker
                  v-model="form.expectedDate"
                  type="date"
                  placeholder="选择日期"
                  style="width: 100%"
                  value-format="YYYY-MM-DD"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <div class="form-section">
          <div class="section-title">
            订单明细
            <el-button type="primary" size="small" :icon="Plus" @click="handleAddItem" style="margin-left: 12px">
              添加商品
            </el-button>
          </div>
          <el-table :data="form.items" border class="items-table">
            <el-table-column label="序号" type="index" width="60" align="center" />
            <el-table-column label="商品名称" width="200">
              <template #default="{ row, $index }">
                <el-select v-model="row.productId" placeholder="请选择商品" @change="handleProductChange($index)">
                  <el-option
                    v-for="product in products"
                    :key="product.id"
                    :label="`${product.productCode} - ${product.productName}`"
                    :value="product.id"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="140">
              <template #default="{ row }">
                <el-input-number v-model="row.quantity" :min="1" :controls="false" style="width: 100%" @change="calculateAmount(row)" />
              </template>
            </el-table-column>
            <el-table-column label="单价（元）" width="140">
              <template #default="{ row }">
                <el-input-number v-model="row.price" :min="0" :precision="2" :controls="false" style="width: 100%" @change="calculateAmount(row)" />
              </template>
            </el-table-column>
            <el-table-column label="金额（元）" width="140" align="right">
              <template #default="{ row }">
                <span class="item-amount">{{ row.amount?.toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="备注">
              <template #default="{ row }">
                <el-input v-model="row.remark" placeholder="选填" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ $index }">
                <el-button link type="danger" @click="handleRemoveItem($index)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="total-row">
            <span class="total-label">订单总金额：</span>
            <span class="total-amount">¥{{ orderTotal.toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</span>
          </div>
        </div>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注信息（选填）" maxlength="500" show-word-limit />
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
      title="采购订单详情"
      width="900px"
      class="elegant-dialog purchase-dialog"
    >
      <detail-card v-if="currentRow">
        <div class="detail-section">
          <div class="section-title">
            <el-icon><Document /></el-icon>
            订单信息
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">订单编号</div>
              <div class="detail-value">{{ currentRow.orderNo }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">供应商</div>
              <div class="detail-value">{{ currentRow.supplierName }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">订单日期</div>
              <div class="detail-value">{{ currentRow.orderDate }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">预计到货</div>
              <div class="detail-value">{{ currentRow.expectedDate || '-' }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">订单状态</div>
              <div class="detail-value">
                <status-tag :status="currentRow.status" />
              </div>
            </div>
            <div class="detail-item">
              <div class="detail-label">订单金额</div>
              <div class="detail-value amount">¥{{ currentRow.totalAmount?.toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</div>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">
            <el-icon><List /></el-icon>
            订单明细
          </div>
          <el-table :data="currentRow.items" border class="detail-table">
            <el-table-column label="序号" type="index" width="60" align="center" />
            <el-table-column prop="productName" label="商品名称" min-width="180" />
            <el-table-column prop="quantity" label="数量" width="100" align="center" />
            <el-table-column prop="price" label="单价" width="120" align="right">
              <template #default="{ row }">
                ¥{{ row.price?.toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}
              </template>
            </el-table-column>
            <el-table-column prop="amount" label="金额" width="140" align="right">
              <template #default="{ row }">
                <span class="item-amount">¥{{ row.amount?.toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</span>
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

    <el-dialog
      v-model="traceVisible"
      title="采购订单追踪"
      width="980px"
      class="elegant-dialog purchase-dialog"
    >
      <template v-if="purchaseTrace">
        <el-descriptions :column="4" border class="trace-summary">
          <el-descriptions-item label="订单编号">{{ purchaseTrace.order.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="订单状态">
            <status-tag :status="purchaseTrace.order.status" />
          </el-descriptions-item>
          <el-descriptions-item label="订购数量">{{ purchaseTrace.executionInfo.orderedQty }}</el-descriptions-item>
          <el-descriptions-item label="已收数量">{{ purchaseTrace.executionInfo.receivedQty }}</el-descriptions-item>
          <el-descriptions-item label="待收数量">{{ purchaseTrace.executionInfo.remainingReceiptQty }}</el-descriptions-item>
          <el-descriptions-item label="收货状态">{{ purchaseTrace.executionInfo.receiptStatus || '-' }}</el-descriptions-item>
          <el-descriptions-item label="订单金额">¥{{ purchaseTrace.order.totalAmount?.toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ purchaseTrace.order.supplierName }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-section" v-for="section in traceDocSections" :key="section.key">
          <div class="section-title">{{ section.title }}</div>
          <el-table :data="purchaseTrace.relatedDocs[section.key]" border stripe>
            <el-table-column prop="documentNo" label="单据编号" min-width="180" show-overflow-tooltip />
            <el-table-column prop="documentType" label="类型" width="140" />
            <el-table-column prop="documentDate" label="日期" width="120">
              <template #default="{ row }">{{ row.documentDate || '-' }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column prop="amount" label="金额" width="140" align="right">
              <template #default="{ row }">
                ¥{{ row.amount?.toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}
              </template>
            </el-table-column>
          </el-table>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  ShoppingCartFull,
  Plus,
  View,
  Edit,
  Delete,
  Check,
  CircleCheck,
  CircleClose,
  Document,
  List,
  Clock,
  RefreshLeft
} from '@element-plus/icons-vue'
import {
  getPurchaseOrders,
  getPurchaseOrder,
  createPurchaseOrder,
  updatePurchaseOrder,
  cancelPurchaseOrder,
  closePurchaseOrder,
  submitPurchaseOrder,
  approvePurchaseOrder,
  unapprovePurchaseOrder,
  rejectPurchaseOrder,
  tracePurchaseOrder,
  exportPurchaseOrders,
  type PurchaseOrder,
  type PurchaseOrderQuery,
  type PurchaseOrderSaveRequest,
  type PurchaseOrderItem,
  type PurchaseOrderTrace,
  type PurchaseOrderRelatedDocs
} from '@/api/purchase'
import { printPurchaseOrder } from '@/utils/bizPrint'
import { getProducts, getSuppliers, type Product, type Supplier } from '@/api/masterdata'
import { PageTable, SearchBar, StatusTag, DetailCard } from '@/components/common'
import { downloadBlob } from '@/utils/download'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
const canCreate = computed(() => userStore.hasPermission('purchase:order:create'))

const route = useRoute()
const readQueryString = (key: string) => {
  const value = route.query[key]
  return Array.isArray(value) ? value[0] || '' : typeof value === 'string' ? value : ''
}

// 查询表单
const queryForm = reactive<PurchaseOrderQuery>({
  pageNo: 1,
  pageSize: 20,
  orderNo: '',
  supplierId: undefined,
  status: '',
  startDate: '',
  endDate: ''
})
queryForm.orderNo = readQueryString('keyword')

// 日期范围
const dateRange = ref<[string, string]>()

// 表格数据
const tableData = ref<PurchaseOrder[]>([])
const total = ref(0)
const loading = ref(false)
const suppliers = ref<Supplier[]>([])
const products = ref<Product[]>([])
const pendingCount = computed(() => tableData.value.filter(item => item.status === 'SUBMITTED' || item.status === 'PENDING' || item.approvalStatus === 'IN_APPROVAL').length)
const approvedCount = computed(() => tableData.value.filter(item => item.status === 'APPROVED').length)

// 对话框
const dialogVisible = ref(false)
const dialogTitle = computed(() => (editId.value ? '编辑采购订单' : '新增采购订单'))
const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const detailVisible = ref(false)
const currentRow = ref<PurchaseOrder>()
const traceVisible = ref(false)
const purchaseTrace = ref<PurchaseOrderTrace>()

const traceDocSections: Array<{ key: keyof PurchaseOrderRelatedDocs; title: string }> = [
  { key: 'receipts', title: '采购收货' },
  { key: 'returns', title: '采购退货' },
  { key: 'payables', title: '应付账款' },
  { key: 'payments', title: '付款记录' },
  { key: 'vouchers', title: '财务凭证' }
]

// 表单数据
const form = reactive<PurchaseOrderSaveRequest>({
  supplierId: '',
  orderDate: '',
  expectedDate: '',
  items: [],
  remark: ''
})

// 当前编辑ID
const editId = ref<string | number>()

// 表单验证规则
const formRules: FormRules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  orderDate: [{ required: true, message: '请选择订单日期', trigger: 'change' }]
}

// 订单总金额
const orderTotal = computed(() => {
  return form.items.reduce((sum, item) => sum + (item.amount || 0), 0)
})

// 查询数据
const handleQuery = async () => {
  loading.value = true
  try {
    const res = await getPurchaseOrders(queryForm)
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
  queryForm.orderNo = ''
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
const handleAdd = () => {
  editId.value = undefined
  resetForm()
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row: PurchaseOrder) => {
  editId.value = row.id
  Object.assign(form, {
    supplierId: row.supplierId,
    orderDate: row.orderDate,
    expectedDate: row.expectedDate,
    items: row.items.map(item => ({ ...item })),
    remark: row.remark
  })
  dialogVisible.value = true
}

// 查看
const handleCopy = async (row: PurchaseOrder) => {
  try {
    const detail = await getPurchaseOrder(row.id)
    editId.value = undefined
    Object.assign(form, {
      supplierId: detail.supplierId,
      orderDate: new Date().toISOString().slice(0, 10),
      expectedDate: detail.expectedDate || detail.deliveryDate || '',
      remark: `复制自 ${detail.orderNo}` + (detail.remark ? `；${detail.remark}` : ''),
      items: (detail.items || detail.lines || []).map((item: any) => ({
        productId: item.productId,
        quantity: Number(item.quantity ?? item.qty ?? 0),
        qty: Number(item.quantity ?? item.qty ?? 0),
        price: Number(item.price ?? 0),
        taxRate: Number(item.taxRate ?? 0),
        amount: Number(item.amount ?? 0),
        remark: item.remark || ''
      }))
    })
    dialogVisible.value = true
  } catch {
    ElMessage.error('复制失败')
  }
}

const handlePrint = async (row: PurchaseOrder) => {
  try {
    const detail = await getPurchaseOrder(row.id)
    printPurchaseOrder(detail)
  } catch {
    ElMessage.error('加载打印数据失败')
  }
}

const handleView = (row: PurchaseOrder) => {
  currentRow.value = row
  detailVisible.value = true
}

const canCancelOrder = (row: PurchaseOrder) => {
  return ['DRAFT', 'REJECTED', 'SUBMITTED'].includes(row.status)
}

const canCloseOrder = (row: PurchaseOrder) => {
  return row.status === 'APPROVED' && row.receiptStatus !== 'RECEIVED'
}

// 与后端 PurchaseOrderService.unapprove 对齐：已审核且尚未入库
const canUnapproveOrder = (row: PurchaseOrder) => {
  return row.status === 'APPROVED'
    && (row.approvalStatus === 'APPROVED' || !row.approvalStatus)
    && (row.receiptStatus === 'NOT_RECEIVED' || !row.receiptStatus)
}

// 取消
const handleCancelOrder = async (row: PurchaseOrder) => {
  try {
    await ElMessageBox.confirm(
      `确认取消订单"${row.orderNo}"吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await cancelPurchaseOrder(row.id)
    ElMessage.success('取消成功')
    handleQuery()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('取消失败')
    }
  }
}

// 关闭
const handleCloseOrder = async (row: PurchaseOrder) => {
  try {
    await ElMessageBox.confirm(
      `确认关闭订单"${row.orderNo}"吗？关闭后不能继续收货。`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await closePurchaseOrder(row.id)
    ElMessage.success('关闭成功')
    handleQuery()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('关闭失败')
    }
  }
}

// 追踪
const handleTraceOrder = async (row: PurchaseOrder) => {
  try {
    purchaseTrace.value = await tracePurchaseOrder(row.id)
    traceVisible.value = true
  } catch (error) {
    ElMessage.error('加载采购订单追踪失败')
  }
}

// 提交审批
const handleSubmit = async (row: PurchaseOrder) => {
  try {
    await ElMessageBox.confirm(
      `确认提交订单"${row.orderNo}"审批吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }
    )

    await submitPurchaseOrder(row.id)
    ElMessage.success('提交成功')
    handleQuery()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('提交失败')
    }
  }
}

// 审核通过
const handleApprove = async (row: PurchaseOrder) => {
  try {
    await ElMessageBox.confirm(
      `确认审核通过订单"${row.orderNo}"吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'success'
      }
    )

    await approvePurchaseOrder(row.id)
    ElMessage.success('审核成功')
    handleQuery()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('审核失败')
    }
  }
}

// 反审核
const handleUnapprove = async (row: PurchaseOrder) => {
  try {
    await ElMessageBox.confirm(
      `确认反审核订单"${row.orderNo}"吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await unapprovePurchaseOrder(row.id)
    ElMessage.success('反审核成功')
    handleQuery()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('反审核失败')
    }
  }
}

// 驳回
const handleReject = async (row: PurchaseOrder) => {
  try {
    const { value: reason } = await ElMessageBox.prompt(
      '请输入驳回原因',
      '驳回订单',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPattern: /.+/,
        inputErrorMessage: '请输入驳回原因'
      }
    )

    await rejectPurchaseOrder(row.id, reason)
    ElMessage.success('已驳回')
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
    const blob = await exportPurchaseOrders(queryForm)
    downloadBlob(blob, `采购订单_${Date.now()}.csv`)
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
      ElMessage.warning('请添加订单明细')
      return
    }

    submitLoading.value = true
    try {
      if (editId.value) {
        await updatePurchaseOrder(editId.value, form)
        ElMessage.success('更新成功')
      } else {
        await createPurchaseOrder(form)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      handleQuery()
    } catch (error) {
      ElMessage.error(editId.value ? '更新失败' : '创建失败')
    } finally {
      submitLoading.value = false
    }
  })
}

// 添加明细
const handleAddItem = () => {
  form.items.push({
    productId: '',
    quantity: 1,
    price: 0,
    amount: 0,
    taxRate: 0
  })
}

// 移除明细
const handleRemoveItem = (index: number) => {
  form.items.splice(index, 1)
}

// 商品变更
const handleProductChange = (index: number) => {
  const item = form.items[index]
  const product = products.value.find(product => String(product.id) === String(item.productId))
  item.productCode = product?.productCode
  item.productName = product?.productName
  item.price = Number(product?.purchasePrice ?? 0)
  item.taxRate = Number(product?.taxRate ?? 0) > 1 ? Number(product?.taxRate ?? 0) / 100 : Number(product?.taxRate ?? 0)
  calculateAmount(item)
}

// 计算金额
const calculateAmount = (item: PurchaseOrderItem) => {
  item.amount = item.quantity * item.price
}

// 重置表单
const resetForm = () => {
  form.supplierId = ''
  form.orderDate = ''
  form.expectedDate = ''
  form.items = []
  form.remark = ''
  formRef.value?.resetFields()
}

const loadSuppliers = async () => {
  const response = await getSuppliers({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
  suppliers.value = response.records
}

const loadProducts = async () => {
  const response = await getProducts({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
  products.value = response.records
}

// 初始化
onMounted(() => {
  handleQuery()
  loadSuppliers().catch(() => ElMessage.error('加载供应商失败'))
  loadProducts().catch(() => ElMessage.error('加载商品失败'))
})
</script>

<style scoped>
.purchase-order-management {
  padding: 24px;
  min-height: 100vh;
  background: linear-gradient(135deg, #e8f4f8 0%, #e0ebf5 100%);
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
  background: linear-gradient(135deg, #1e40af 0%, #1e3a8a 100%);
  border-radius: 16px;
  padding: 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 8px 32px rgba(30, 64, 175, 0.3);
  position: relative;
  overflow: hidden;
}

.header-content::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -10%;
  width: 350px;
  height: 350px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.08) 0%, transparent 70%);
  animation: rotate 20s linear infinite;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
  position: relative;
  z-index: 1;
}

.icon-frame {
  position: relative;
  width: 72px;
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 20px;
  border: 2px solid rgba(255, 255, 255, 0.2);
}

.header-icon {
  font-size: 46px;
  color: #ffffff;
  animation: iconSwing 2.5s ease-in-out infinite;
}

@keyframes iconSwing {
  0%, 100% {
    transform: rotate(-3deg);
  }
  50% {
    transform: rotate(3deg);
  }
}

.icon-waves {
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 20px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  animation: waves 3s ease-out infinite;
}

@keyframes waves {
  0% {
    transform: scale(1);
    opacity: 1;
  }
  100% {
    transform: scale(1.5);
    opacity: 0;
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

.stat-value.pending {
  color: #fbbf24;
  text-shadow: 0 0 10px rgba(251, 191, 36, 0.6);
}

.stat-value.approved {
  color: #86efac;
  text-shadow: 0 0 10px rgba(134, 239, 172, 0.6);
}

.purchase-table {
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

.order-no {
  font-family: 'Courier New', monospace;
  font-weight: 600;
  color: #1e40af;
  font-size: 13px;
}

.amount-value {
  font-weight: 600;
  color: #1e40af;
  font-size: 14px;
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

.purchase-dialog :deep(.el-dialog__header) {
  background: linear-gradient(to right, #e8f4f8, #e0ebf5);
  padding: 24px 32px;
  border-bottom: 1px solid #cbd5e1;
}

.purchase-dialog :deep(.el-dialog__title) {
  font-size: 18px;
  font-weight: 600;
  color: #2c3e50;
  letter-spacing: 0.3px;
}

.purchase-dialog :deep(.el-dialog__body) {
  padding: 24px 32px;
}

.form-section {
  margin-bottom: 24px;
  padding: 20px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.form-section .section-title {
  font-size: 15px;
  font-weight: 600;
  color: #1e40af;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 2px solid #1e40af;
  display: flex;
  align-items: center;
}

.items-table {
  margin-top: 12px;
}

.items-table :deep(.el-input-number) {
  width: 100%;
}

.item-amount {
  font-weight: 600;
  color: #1e40af;
}

.total-row {
  margin-top: 16px;
  padding: 12px 16px;
  background: #ffffff;
  border-radius: 8px;
  border: 2px solid #1e40af;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
}

.total-label {
  font-size: 15px;
  font-weight: 600;
  color: #475569;
}

.total-amount {
  font-pageSize: 20px;
  font-weight: 700;
  color: #1e40af;
}

.detail-table {
  margin-top: 12px;
}

.detail-value.amount {
  font-size: 18px;
  font-weight: 700;
  color: #1e40af;
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
  border-bottom: 2px solid #1e40af;
  letter-spacing: 0.3px;
}

.detail-section .section-title .el-icon {
  color: #1e40af;
  font-size: 16px;
}
</style>
