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
            <h1 class="page-title">{{ t('purchaseOrder.title') }}</h1>
            <p class="page-subtitle">{{ t('purchaseOrder.subtitle') }}</p>
          </div>
        </div>
        <div class="header-stats">
          <div class="stat-card">
            <span class="stat-label">{{ t('purchaseOrder.totalOrders') }}</span>
            <span class="stat-value">{{ total }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">{{ t('purchaseOrder.pendingApproval') }}</span>
            <span class="stat-value pending">{{ pendingCount }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">{{ t('purchaseOrder.approved') }}</span>
            <span class="stat-value approved">{{ approvedCount }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 搜索栏 -->
    <search-bar v-model="queryForm" @search="handleQuery" @reset="handleReset">
      <el-form-item :label="t('purchaseOrder.orderNo')" prop="orderNo">
        <el-input
          v-model="queryForm.orderNo"
          :placeholder="t('purchaseOrder.orderNoPlaceholder')"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item :label="t('purchaseOrder.supplier')" prop="supplierId">
        <el-select v-model="queryForm.supplierId" :placeholder="t('purchaseOrder.selectSupplier')" clearable>
          <el-option :label="t('purchaseOrder.allSuppliers')" value="" />
          <el-option
            v-for="supplier in suppliers"
            :key="supplier.id"
            :label="supplier.name || supplier.supplierName"
            :value="supplier.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('purchaseOrder.orderStatus')" prop="status">
        <el-select v-model="queryForm.status" :placeholder="t('purchaseOrder.selectStatus')" clearable>
          <el-option :label="t('purchaseOrder.status.draft')" value="DRAFT" />
          <el-option :label="t('purchaseOrder.status.submitted')" value="SUBMITTED" />
          <el-option :label="t('purchaseOrder.status.approved')" value="APPROVED" />
          <el-option :label="t('purchaseOrder.status.rejected')" value="REJECTED" />
          <el-option :label="t('purchaseOrder.status.closed')" value="CLOSED" />
          <el-option :label="t('purchaseOrder.status.cancelled')" value="CANCELLED" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('purchaseOrder.orderDate')" prop="dateRange">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          :range-separator="t('purchaseOrder.dateRangeSeparator')"
          :start-placeholder="t('purchaseOrder.startDate')"
          :end-placeholder="t('purchaseOrder.endDate')"
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
      :create-text="t('purchaseOrder.create')"
      :show-create="canCreate"
      @create="handleAdd"
      @export="handleExport"
      @refresh="handleQuery"
      @page-change="handlePageChange"
      class="purchase-table"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column prop="orderNo" :label="t('purchaseOrder.orderNo')" width="160" fixed>
        <template #default="{ row }">
          <span class="order-no">{{ row.orderNo }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="supplierName" :label="t('purchaseOrder.supplier')" width="160" show-overflow-tooltip />
      <el-table-column prop="orderDate" :label="t('purchaseOrder.orderDate')" width="120" align="center" />
      <el-table-column prop="expectedDate" :label="t('purchaseOrder.expectedArrival')" width="120" align="center" />
      <el-table-column prop="totalAmount" :label="t('purchaseOrder.orderAmount')" width="140" align="right">
        <template #default="{ row }">
          <span class="amount-value">¥{{ formatMoney(row.totalAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" :label="t('purchaseOrder.orderStatus')" width="110" align="center">
        <template #default="{ row }">
          <status-tag :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="createdBy" :label="t('purchaseOrder.createdBy')" width="100" />
      <el-table-column prop="createdAt" :label="t('purchaseOrder.createdAt')" width="190">
        <template #default="{ row }">{{ formatLocalizedDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('purchaseOrder.actions')" width="280" fixed="right" align="center">
        <template #default="{ row }">
          <div class="action-buttons">
            <el-button link type="primary" size="small" @click="handleView(row)">
              <el-icon><View /></el-icon>
              {{ t('purchaseOrder.view') }}
            </el-button>
            <el-button link type="primary" size="small" @click="handlePrint(row)">
              {{ t('purchaseOrder.print') }}
            </el-button>
            <el-button v-permission="'purchase:order:create'" link type="primary" size="small" @click="handleCopy(row)">
              {{ t('purchaseOrder.copy') }}
            </el-button>
            <el-button v-if="row.status === 'DRAFT'" v-permission="'purchase:order:update'" link type="primary" size="small" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              {{ t('purchaseOrder.edit') }}
            </el-button>
            <el-button v-if="row.status === 'DRAFT'" v-permission="'purchase:order:submit'" link type="success" size="small" @click="handleSubmit(row)">
              <el-icon><Check /></el-icon>
              {{ t('purchaseOrder.submit') }}
            </el-button>
            <el-button v-if="row.status === 'SUBMITTED' || row.status === 'PENDING' || row.approvalStatus === 'IN_APPROVAL'" v-permission="'purchase:order:approve'" link type="success" size="small" @click="handleApprove(row)">
              <el-icon><CircleCheck /></el-icon>
              {{ t('purchaseOrder.approve') }}
            </el-button>
            <el-button v-if="row.status === 'SUBMITTED' || row.status === 'PENDING' || row.approvalStatus === 'IN_APPROVAL'" v-permission="'purchase:order:reject'" link type="warning" size="small" @click="handleReject(row)">
              <el-icon><CircleClose /></el-icon>
              {{ t('purchaseOrder.reject') }}
            </el-button>
            <el-button v-if="canUnapproveOrder(row)" v-permission="'purchase:order:unapprove'" link type="warning" size="small" @click="handleUnapprove(row)">
              <el-icon><RefreshLeft /></el-icon>
              {{ t('purchaseOrder.unapprove') }}
            </el-button>
            <el-button v-if="canCloseOrder(row)" v-permission="'purchase:order:close'" link type="warning" size="small" @click="handleCloseOrder(row)">
              <el-icon><CircleClose /></el-icon>
              {{ t('purchaseOrder.close') }}
            </el-button>
            <el-button v-if="canCancelOrder(row)" v-permission="'purchase:order:cancel'" link type="danger" size="small" @click="handleCancelOrder(row)">
              <el-icon><Delete /></el-icon>
              {{ t('purchaseOrder.cancel') }}
            </el-button>
            <el-button link type="primary" size="small" @click="handleTraceOrder(row)">
              <el-icon><List /></el-icon>
              {{ t('purchaseOrder.trace') }}
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
          <div class="section-title">{{ t('purchaseOrder.basicInfo') }}</div>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="t('purchaseOrder.supplier')" prop="supplierId">
                <el-select v-model="form.supplierId" :placeholder="t('purchaseOrder.selectSupplier')" style="width: 100%">
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
              <el-form-item :label="t('purchaseOrder.orderDate')" prop="orderDate">
                <el-date-picker
                  v-model="form.orderDate"
                  type="date"
                  :placeholder="t('purchaseOrder.selectDate')"
                  style="width: 100%"
                  value-format="YYYY-MM-DD"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('purchaseOrder.expectedArrivalDate')" prop="expectedDate">
                <el-date-picker
                  v-model="form.expectedDate"
                  type="date"
                  :placeholder="t('purchaseOrder.selectDate')"
                  style="width: 100%"
                  value-format="YYYY-MM-DD"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <div class="form-section">
          <div class="section-title">
            {{ t('purchaseOrder.details') }}
            <el-button type="primary" size="small" :icon="Plus" @click="handleAddItem" style="margin-left: 12px">
              {{ t('purchaseOrder.addProduct') }}
            </el-button>
          </div>
          <el-table :data="form.items" border class="items-table">
            <el-table-column :label="t('purchaseOrder.sequence')" type="index" width="60" align="center" />
            <el-table-column :label="t('purchaseOrder.productName')" width="200">
              <template #default="{ row, $index }">
                <el-select v-model="row.productId" :placeholder="t('purchaseOrder.selectProduct')" @change="handleProductChange($index)">
                  <el-option
                    v-for="product in products"
                    :key="product.id"
                    :label="`${product.productCode} - ${product.productName}`"
                    :value="product.id"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="t('purchaseOrder.quantity')" width="140">
              <template #default="{ row }">
                <el-input-number v-model="row.quantity" :min="1" :controls="false" style="width: 100%" @change="calculateAmount(row)" />
              </template>
            </el-table-column>
            <el-table-column :label="t('purchaseOrder.unitPriceCny')" width="140">
              <template #default="{ row }">
                <el-input-number v-model="row.price" :min="0" :precision="2" :controls="false" style="width: 100%" @change="calculateAmount(row)" />
              </template>
            </el-table-column>
            <el-table-column :label="t('purchaseOrder.amountCny')" width="140" align="right">
              <template #default="{ row }">
                <span class="item-amount">{{ formatMoney(row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('purchaseOrder.remark')">
              <template #default="{ row }">
                <el-input v-model="row.remark" :placeholder="t('purchaseOrder.optional')" />
              </template>
            </el-table-column>
            <el-table-column :label="t('purchaseOrder.actions')" width="80" align="center">
              <template #default="{ $index }">
                <el-button link type="danger" @click="handleRemoveItem($index)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="total-row">
            <span class="total-label">{{ t('purchaseOrder.totalAmount') }}</span>
            <span class="total-amount">¥{{ formatMoney(orderTotal) }}</span>
          </div>
        </div>

        <el-form-item :label="t('purchaseOrder.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="3" :placeholder="t('purchaseOrder.remarkPlaceholder')" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('purchaseOrder.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmitForm">
          {{ t('purchaseOrder.confirm') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情查看对话框 -->
    <el-dialog
      v-model="detailVisible"
      :title="t('purchaseOrder.detailTitle')"
      width="900px"
      class="elegant-dialog purchase-dialog"
    >
      <detail-card v-if="currentRow">
        <div class="detail-section">
          <div class="section-title">
            <el-icon><Document /></el-icon>
            {{ t('purchaseOrder.orderInfo') }}
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">{{ t('purchaseOrder.orderNo') }}</div>
              <div class="detail-value">{{ currentRow.orderNo }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ t('purchaseOrder.supplier') }}</div>
              <div class="detail-value">{{ currentRow.supplierName }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ t('purchaseOrder.orderDate') }}</div>
              <div class="detail-value">{{ currentRow.orderDate }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ t('purchaseOrder.expectedArrival') }}</div>
              <div class="detail-value">{{ currentRow.expectedDate || '-' }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ t('purchaseOrder.orderStatus') }}</div>
              <div class="detail-value">
                <status-tag :status="currentRow.status" />
              </div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ t('purchaseOrder.orderAmount') }}</div>
              <div class="detail-value amount">¥{{ formatMoney(currentRow.totalAmount) }}</div>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">
            <el-icon><List /></el-icon>
            {{ t('purchaseOrder.details') }}
          </div>
          <el-table :data="currentRow.items" border class="detail-table">
            <el-table-column :label="t('purchaseOrder.sequence')" type="index" width="60" align="center" />
            <el-table-column prop="productName" :label="t('purchaseOrder.productName')" min-width="180" />
            <el-table-column prop="quantity" :label="t('purchaseOrder.quantity')" width="100" align="center" />
            <el-table-column prop="price" :label="t('purchaseOrder.unitPrice')" width="120" align="right">
              <template #default="{ row }">
                ¥{{ formatMoney(row.price) }}
              </template>
            </el-table-column>
            <el-table-column prop="amount" :label="t('purchaseOrder.amount')" width="140" align="right">
              <template #default="{ row }">
                <span class="item-amount">¥{{ formatMoney(row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="remark" :label="t('purchaseOrder.remark')" min-width="120" />
          </el-table>
        </div>

        <div class="detail-section">
          <div class="section-title">
            <el-icon><Clock /></el-icon>
            {{ t('purchaseOrder.otherInfo') }}
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">{{ t('purchaseOrder.createdBy') }}</div>
              <div class="detail-value">{{ currentRow.createdBy }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ t('purchaseOrder.createdAt') }}</div>
              <div class="detail-value">{{ formatLocalizedDateTime(currentRow.createdAt) }}</div>
            </div>
            <div class="detail-item" style="grid-column: 1 / -1">
              <div class="detail-label">{{ t('purchaseOrder.remark') }}</div>
              <div class="detail-value">{{ currentRow.remark || '-' }}</div>
            </div>
          </div>
        </div>
      </detail-card>
    </el-dialog>

    <el-dialog
      v-model="traceVisible"
      :title="t('purchaseOrder.traceTitle')"
      width="980px"
      class="elegant-dialog purchase-dialog"
    >
      <template v-if="purchaseTrace">
        <el-descriptions :column="4" border class="trace-summary">
          <el-descriptions-item :label="t('purchaseOrder.orderNo')">{{ purchaseTrace.order.orderNo }}</el-descriptions-item>
          <el-descriptions-item :label="t('purchaseOrder.orderStatus')">
            <status-tag :status="purchaseTrace.order.status" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('purchaseOrder.orderedQuantity')">{{ purchaseTrace.executionInfo.orderedQty }}</el-descriptions-item>
          <el-descriptions-item :label="t('purchaseOrder.receivedQuantity')">{{ purchaseTrace.executionInfo.receivedQty }}</el-descriptions-item>
          <el-descriptions-item :label="t('purchaseOrder.remainingQuantity')">{{ purchaseTrace.executionInfo.remainingReceiptQty }}</el-descriptions-item>
          <el-descriptions-item :label="t('purchaseOrder.receiptStatus')">{{ purchaseTrace.executionInfo.receiptStatus || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('purchaseOrder.orderAmount')">¥{{ formatMoney(purchaseTrace.order.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item :label="t('purchaseOrder.supplier')">{{ purchaseTrace.order.supplierName }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-section" v-for="section in traceDocSections" :key="section.key">
          <div class="section-title">{{ section.title }}</div>
          <el-table :data="purchaseTrace.relatedDocs[section.key]" border stripe>
            <el-table-column prop="documentNo" :label="t('purchaseOrder.documentNo')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="documentType" :label="t('purchaseOrder.type')" width="140" />
            <el-table-column prop="documentDate" :label="t('purchaseOrder.date')" width="120">
              <template #default="{ row }">{{ row.documentDate || '-' }}</template>
            </el-table-column>
            <el-table-column prop="status" :label="t('purchaseOrder.statusLabel')" width="110" />
            <el-table-column prop="amount" :label="t('purchaseOrder.amount')" width="140" align="right">
              <template #default="{ row }">
                ¥{{ formatMoney(row.amount) }}
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
import { useI18n } from 'vue-i18n'
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
import { formatBusinessDate, formatLocalizedDateTime, formatLocalizedNumber } from '@/utils/locale'

const userStore = useUserStore()
const { t } = useI18n()
const canCreate = computed(() => userStore.hasPermission('purchase:order:create'))
const formatMoney = (value?: number) => formatLocalizedNumber(Number(value ?? 0), {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})

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
const dialogTitle = computed(() => (editId.value ? t('purchaseOrder.dialog.edit') : t('purchaseOrder.dialog.create')))
const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const detailVisible = ref(false)
const currentRow = ref<PurchaseOrder>()
const traceVisible = ref(false)
const purchaseTrace = ref<PurchaseOrderTrace>()

const traceDocSections = computed<Array<{ key: keyof PurchaseOrderRelatedDocs; title: string }>>(() => [
  { key: 'receipts', title: t('purchaseOrder.traceSections.receipts') },
  { key: 'returns', title: t('purchaseOrder.traceSections.returns') },
  { key: 'payables', title: t('purchaseOrder.traceSections.payables') },
  { key: 'payments', title: t('purchaseOrder.traceSections.payments') },
  { key: 'vouchers', title: t('purchaseOrder.traceSections.vouchers') }
])

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
const formRules = computed<FormRules>(() => ({
  supplierId: [{ required: true, message: t('purchaseOrder.validation.supplier'), trigger: 'change' }],
  orderDate: [{ required: true, message: t('purchaseOrder.validation.orderDate'), trigger: 'change' }]
}))

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
    ElMessage.error(t('purchaseOrder.message.loadFailed'))
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
      orderDate: formatBusinessDate(),
      expectedDate: detail.expectedDate || detail.deliveryDate || '',
      remark: t('purchaseOrder.dialog.copiedFrom', { orderNo: detail.orderNo }) + (detail.remark ? `; ${detail.remark}` : ''),
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
    ElMessage.error(t('purchaseOrder.message.copyFailed'))
  }
}

const handlePrint = async (row: PurchaseOrder) => {
  try {
    const detail = await getPurchaseOrder(row.id)
    printPurchaseOrder(detail)
  } catch {
    ElMessage.error(t('purchaseOrder.message.printLoadFailed'))
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
      t('purchaseOrder.message.cancelConfirm', { orderNo: row.orderNo }),
      t('purchaseOrder.message.prompt'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'warning'
      }
    )

    await cancelPurchaseOrder(row.id)
    ElMessage.success(t('purchaseOrder.message.cancelled'))
    handleQuery()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(t('purchaseOrder.message.cancelFailed'))
    }
  }
}

// 关闭
const handleCloseOrder = async (row: PurchaseOrder) => {
  try {
    await ElMessageBox.confirm(
      t('purchaseOrder.message.closeConfirm', { orderNo: row.orderNo }),
      t('purchaseOrder.message.prompt'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'warning'
      }
    )

    await closePurchaseOrder(row.id)
    ElMessage.success(t('purchaseOrder.message.closed'))
    handleQuery()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(t('purchaseOrder.message.closeFailed'))
    }
  }
}

// 追踪
const handleTraceOrder = async (row: PurchaseOrder) => {
  try {
    purchaseTrace.value = await tracePurchaseOrder(row.id)
    traceVisible.value = true
  } catch (error) {
    ElMessage.error(t('purchaseOrder.message.traceLoadFailed'))
  }
}

// 提交审批
const handleSubmit = async (row: PurchaseOrder) => {
  try {
    await ElMessageBox.confirm(
      t('purchaseOrder.message.submitConfirm', { orderNo: row.orderNo }),
      t('purchaseOrder.message.prompt'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'info'
      }
    )

    await submitPurchaseOrder(row.id)
    ElMessage.success(t('purchaseOrder.message.submitted'))
    handleQuery()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(t('purchaseOrder.message.submitFailed'))
    }
  }
}

// 审核通过
const handleApprove = async (row: PurchaseOrder) => {
  try {
    await ElMessageBox.confirm(
      t('purchaseOrder.message.approveConfirm', { orderNo: row.orderNo }),
      t('purchaseOrder.message.prompt'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'success'
      }
    )

    await approvePurchaseOrder(row.id)
    ElMessage.success(t('purchaseOrder.message.approved'))
    handleQuery()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(t('purchaseOrder.message.approveFailed'))
    }
  }
}

// 反审核
const handleUnapprove = async (row: PurchaseOrder) => {
  try {
    await ElMessageBox.confirm(
      t('purchaseOrder.message.unapproveConfirm', { orderNo: row.orderNo }),
      t('purchaseOrder.message.prompt'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'warning'
      }
    )

    await unapprovePurchaseOrder(row.id)
    ElMessage.success(t('purchaseOrder.message.unapproved'))
    handleQuery()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(t('purchaseOrder.message.unapproveFailed'))
    }
  }
}

// 驳回
const handleReject = async (row: PurchaseOrder) => {
  try {
    const { value: reason } = await ElMessageBox.prompt(
      t('purchaseOrder.validation.rejectReason'),
      t('purchaseOrder.message.rejectTitle'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        inputPattern: /.+/,
        inputErrorMessage: t('purchaseOrder.validation.rejectReason')
      }
    )

    await rejectPurchaseOrder(row.id, reason)
    ElMessage.success(t('purchaseOrder.message.rejected'))
    handleQuery()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(t('purchaseOrder.message.actionFailed'))
    }
  }
}

// 导出
const handleExport = async () => {
  try {
    const blob = await exportPurchaseOrders(queryForm)
    downloadBlob(blob, t('purchaseOrder.message.exportFile', { timestamp: Date.now() }))
    ElMessage.success(t('purchaseOrder.message.exported'))
  } catch (error) {
    ElMessage.error(t('purchaseOrder.message.exportFailed'))
  }
}

// 提交表单
const handleSubmitForm = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    if (form.items.length === 0) {
      ElMessage.warning(t('purchaseOrder.validation.details'))
      return
    }

    submitLoading.value = true
    try {
      if (editId.value) {
        await updatePurchaseOrder(editId.value, form)
        ElMessage.success(t('purchaseOrder.message.updated'))
      } else {
        await createPurchaseOrder(form)
        ElMessage.success(t('purchaseOrder.message.created'))
      }
      dialogVisible.value = false
      handleQuery()
    } catch (error) {
      ElMessage.error(editId.value ? t('purchaseOrder.message.updateFailed') : t('purchaseOrder.message.createFailed'))
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
  loadSuppliers().catch(() => ElMessage.error(t('purchaseOrder.message.suppliersLoadFailed')))
  loadProducts().catch(() => ElMessage.error(t('purchaseOrder.message.productsLoadFailed')))
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
