<template>
  <div class="sales-orders-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="queryParams.keyword"
            placeholder="订单号"
            clearable
            style="width: 220px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="客户">
          <el-select v-model="queryParams.customerId" placeholder="请选择客户" clearable filterable style="width: 220px">
            <el-option v-for="customer in customers" :key="customer.id" :label="customer.name" :value="customer.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 150px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="审批中" value="SUBMITTED" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="审批状态">
          <el-select v-model="queryParams.approvalStatus" placeholder="请选择审批状态" clearable style="width: 150px">
            <el-option label="未提交" value="NOT_SUBMITTED" />
            <el-option label="审批中" value="IN_APPROVAL" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>销售订单</span>
          <el-button v-permission="'sales:order:create'" type="primary" :icon="Plus" @click="handleCreate">新增订单</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="orderNo" label="订单号" min-width="170" fixed />
        <el-table-column prop="customerName" label="客户" min-width="160" />
        <el-table-column prop="orderDate" label="订单日期" width="120" />
        <el-table-column prop="deliveryDate" label="交付日期" width="120" />
        <el-table-column prop="totalQuantity" label="数量" width="110" align="right">
          <template #default="{ row }">{{ formatNumber(row.totalQuantity) }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="金额" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="订单状态" width="110">
          <template #default="{ row }">
            <el-tag>{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="approvalStatus" label="审批状态" width="110">
          <template #default="{ row }">
            <el-tag :type="approvalTagType(row.approvalStatus)">{{ approvalText(row.approvalStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deliveryStatus" label="发货状态" width="110">
          <template #default="{ row }">{{ deliveryText(row.deliveryStatus) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="310" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="handleView(row)">查看</el-button>
            <el-button link type="primary" @click="handlePrint(row)">打印</el-button>
            <el-button v-permission="'sales:order:create'" link type="primary" @click="handleCopy(row)">复制</el-button>
            <el-button v-if="canEdit(row)" v-permission="'sales:order:update'" link type="primary" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="canSubmit(row)" v-permission="'sales:order:submit'" link type="success" @click="handleSubmitOrder(row)">提交</el-button>
            <el-button v-if="canApprove(row)" v-permission="'sales:order:approve'" link type="success" @click="handleApprove(row)">通过</el-button>
            <el-button v-if="canApprove(row)" v-permission="'sales:order:reject'" link type="warning" @click="handleReject(row)">驳回</el-button>
            <el-button v-if="canUnapprove(row)" v-permission="'sales:order:unapprove'" link type="warning" @click="handleUnapprove(row)">反审核</el-button>
            <el-button v-if="canCancel(row)" v-permission="'sales:order:cancel'" link type="danger" @click="handleCancel(row)">取消</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="980px" @close="resetForm">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="客户" prop="customerId">
              <el-select
                v-model="formData.customerId"
                placeholder="请选择客户"
                filterable
                style="width: 100%"
                :disabled="isView"
                @change="onCustomerOrDateChange"
              >
                <el-option v-for="customer in customers" :key="customer.id" :label="customer.name" :value="customer.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="发货仓库" prop="warehouseId">
              <el-select v-model="formData.warehouseId" placeholder="请选择仓库" filterable style="width: 100%" :disabled="isView">
                <el-option v-for="warehouse in warehouses" :key="warehouse.id" :label="warehouse.name" :value="warehouse.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="订单日期" prop="orderDate">
              <el-date-picker
                v-model="formData.orderDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%"
                :disabled="isView"
                @change="onCustomerOrDateChange"
              />
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="交付日期">
              <el-date-picker v-model="formData.deliveryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" :disabled="isView" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="请输入备注" :disabled="isView" />
        </el-form-item>

        <div v-if="formData.customerId && !isView" v-loading="creditPreviewLoading" class="credit-preview-card">
          <div class="credit-preview-header">
            <div>
              <div class="credit-preview-title">客户授信预览</div>
              <div class="credit-preview-subtitle">未结应收 + 已审批未发货订单 + 本单含税金额</div>
            </div>
            <el-tag v-if="creditPreview" :type="creditPreview.exceeded ? 'danger' : 'success'">
              {{ creditPreview.unlimited ? '不限额客户' : creditPreview.exceeded ? '提交后超限' : '额度充足' }}
            </el-tag>
          </div>

          <template v-if="creditPreview">
            <div class="credit-preview-grid">
              <div class="credit-preview-item">
                <div class="preview-label">信用额度</div>
                <div class="preview-value" :class="{ quiet: creditPreview.unlimited }">
                  {{ creditPreview.unlimited ? '不限额' : formatMoney(creditPreview.creditLimit) }}
                </div>
              </div>
              <div class="credit-preview-item">
                <div class="preview-label">未结应收</div>
                <div class="preview-value">{{ formatMoney(creditPreview.outstandingReceivable) }}</div>
              </div>
              <div class="credit-preview-item">
                <div class="preview-label">在途订单敞口</div>
                <div class="preview-value">{{ formatMoney(creditPreview.openOrderExposure) }}</div>
              </div>
              <div class="credit-preview-item">
                <div class="preview-label">当前敞口</div>
                <div class="preview-value">{{ formatMoney(creditPreview.currentExposure) }}</div>
              </div>
              <div class="credit-preview-item">
                <div class="preview-label">本单含税金额</div>
                <div class="preview-value">{{ formatMoney(creditPreview.orderAmount) }}</div>
              </div>
              <div class="credit-preview-item">
                <div class="preview-label">提交后可用额度</div>
                <div class="preview-value" :class="{ danger: !creditPreview.unlimited && Number(creditPreview.projectedAvailableCredit ?? 0) < 0 }">
                  {{ creditPreview.unlimited ? '不限额' : formatMoney(creditPreview.projectedAvailableCredit) }}
                </div>
              </div>
            </div>

            <el-alert
              v-if="creditPreview.exceeded"
              type="error"
              :closable="false"
              show-icon
              :title="`预计超限 ${formatMoney(creditExceededAmount)}`"
              :description="`当前敞口 ${formatMoney(creditPreview.currentExposure)} + 本单 ${formatMoney(creditPreview.orderAmount)} = ${formatMoney(creditPreview.projectedExposure)}，已超过信用额度 ${formatMoney(creditPreview.creditLimit)}`"
            />
            <el-alert
              v-else-if="creditPreview.unlimited"
              type="info"
              :closable="false"
              show-icon
              title="该客户未设置授信额度"
              :description="`当前敞口 ${formatMoney(creditPreview.currentExposure)}，本单可继续提交审批`"
            />
            <el-alert
              v-else
              type="success"
              :closable="false"
              show-icon
              :title="`提交后敞口 ${formatMoney(creditPreview.projectedExposure)}`"
              :description="`提交后仍有可用额度 ${formatMoney(creditPreview.projectedAvailableCredit)}`"
            />
          </template>
        </div>

        <div class="line-toolbar">
          <span>订单明细</span>
          <el-button v-if="!isView" type="primary" :icon="Plus" @click="addLine">添加明细</el-button>
        </div>

        <el-table :data="formData.items" border>
          <el-table-column label="产品" min-width="240">
            <template #default="{ row }">
              <el-select v-model="row.productId" placeholder="请选择产品" filterable style="width: 100%" :disabled="isView" @change="onProductChange(row)">
                <el-option
                  v-for="product in products"
                  :key="product.id"
                  :label="`${product.code || product.productCode || product.id} - ${product.name || product.productName || '-'}`"
                  :value="product.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="150">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" :precision="2" :disabled="isView" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="单价" width="150">
            <template #default="{ row }">
              <el-input-number v-model="row.price" :min="0" :precision="2" :disabled="isView" style="width: 100%" />
              <div v-if="row.minPrice != null" class="price-hint">
                最低 {{ formatMoney(row.minPrice) }}
                <span v-if="row.priceLevel">· {{ row.priceLevel === 'CUSTOMER' ? '客户价' : '通用价' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="税率" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.taxRate" :min="0" :max="1" :step="0.01" :precision="4" :disabled="isView" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="金额" width="130" align="right">
            <template #default="{ row }">{{ formatMoney(lineAmount(row)) }}</template>
          </el-table-column>
          <el-table-column label="备注" min-width="150">
            <template #default="{ row }">
              <el-input v-model="row.remark" placeholder="备注" :disabled="isView" />
            </template>
          </el-table-column>
          <el-table-column v-if="!isView" label="操作" width="90" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" :icon="Delete" @click="removeLine($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button v-if="!isView" type="primary" :loading="submitLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Edit, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import {
  approveSalesOrder,
  unapproveSalesOrder,
  cancelSalesOrder,
  createSalesOrder,
  getSalesOrder,
  getSalesOrders,
  previewSalesOrderCredit,
  rejectSalesOrder,
  resolveSalesPrice,
  submitSalesOrder,
  updateSalesOrder,
  type SalesOrderCreditPreview,
  type SalesOrder,
  type SalesOrderItem,
  type SalesOrderQuery,
  type SalesOrderSaveRequest
} from '@/api/sales'
import { getCustomers, getProducts, getWarehouses, type Customer, type Product, type Warehouse } from '@/api/masterdata'
import { printSalesOrder } from '@/utils/bizPrint'
import { formatLocalizedNumber } from '@/utils/locale'

type PricedSalesOrderItem = SalesOrderItem & {
  minPrice?: number | null
  priceLevel?: string | null
}

type SalesOrderForm = SalesOrderSaveRequest & {
  id?: string
  items: PricedSalesOrderItem[]
}

const route = useRoute()
const readQueryString = (key: string) => {
  const value = route.query[key]
  return Array.isArray(value) ? value[0] || '' : typeof value === 'string' ? value : ''
}

const queryParams = reactive<SalesOrderQuery>({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  customerId: undefined,
  status: '',
  approvalStatus: ''
})
queryParams.keyword = readQueryString('keyword')

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref<SalesOrder[]>([])
const total = ref(0)
const customers = ref<Customer[]>([])
const warehouses = ref<Warehouse[]>([])
const products = ref<Product[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isView = ref(false)
const formRef = ref<FormInstance>()
const creditPreviewLoading = ref(false)
const creditPreview = ref<SalesOrderCreditPreview>()
let creditPreviewTimer: ReturnType<typeof setTimeout> | undefined
let creditPreviewRequestId = 0

const formData = reactive<SalesOrderForm>({
  customerId: '',
  warehouseId: '',
  orderDate: '',
  deliveryDate: '',
  remark: '',
  items: []
})

const formRules: FormRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择发货仓库', trigger: 'change' }],
  orderDate: [{ required: true, message: '请选择订单日期', trigger: 'change' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const page = await getSalesOrders(queryParams)
    tableData.value = page.records
    total.value = page.total
  } catch (error) {
    console.error('加载销售订单失败:', error)
    ElMessage.error('加载销售订单失败')
  } finally {
    loading.value = false
  }
}

const loadOptions = async () => {
  const [customerPage, warehousePage, productPage] = await Promise.all([
    getCustomers({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' }),
    getWarehouses({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' }),
    getProducts({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
  ])
  customers.value = customerPage.records
  warehouses.value = warehousePage.records
  products.value = productPage.records
}

const handleQuery = () => {
  queryParams.pageNo = 1
  loadData()
}

const handleReset = () => {
  queryParams.keyword = ''
  queryParams.customerId = undefined
  queryParams.status = ''
  queryParams.approvalStatus = ''
  queryParams.pageNo = 1
  loadData()
}

const handleCreate = () => {
  resetForm()
  dialogTitle.value = '新增销售订单'
  isView.value = false
  formData.orderDate = new Date().toISOString().slice(0, 10)
  addLine()
  dialogVisible.value = true
}

const handleEdit = async (row: SalesOrder) => {
  dialogTitle.value = '编辑销售订单'
  isView.value = false
  await fillForm(row.id)
  dialogVisible.value = true
}

const handleCopy = async (row: SalesOrder) => {
  dialogTitle.value = '复制销售订单'
  isView.value = false
  await fillForm(row.id)
  formData.id = undefined as any
  formData.orderDate = new Date().toISOString().slice(0, 10)
  formData.remark = (formData.remark ? formData.remark + ' ' : '') + `(复制自 ${row.orderNo})`
  dialogVisible.value = true
}

const handlePrint = async (row: SalesOrder) => {
  try {
    const order = await getSalesOrder(row.id)
    printSalesOrder(order)
  } catch {
    ElMessage.error('加载打印数据失败')
  }
}

const handleView = async (row: SalesOrder) => {
  dialogTitle.value = '销售订单详情'
  isView.value = true
  await fillForm(row.id)
  dialogVisible.value = true
}

const fillForm = async (id: string) => {
  const order = await getSalesOrder(id)
  Object.assign(formData, {
    id: order.id,
    customerId: order.customerId,
    warehouseId: order.warehouseId || '',
    orderDate: order.orderDate,
    deliveryDate: order.deliveryDate || '',
    remark: order.remark || '',
    items: order.items.map((item) => ({
      ...item,
      quantity: item.quantity ?? item.qty ?? 0,
      price: item.price ?? 0,
      taxRate: item.taxRate ?? 0
    }))
  })
}

const buildCreditPreviewItems = (): SalesOrderItem[] => (
  formData.items
    .filter((item) => item.productId && Number(item.quantity) > 0)
    .map((item) => ({
      productId: item.productId,
      quantity: Number(item.quantity ?? 0),
      price: Number(item.price ?? 0),
      taxRate: Number(item.taxRate ?? 0),
      amount: lineAmount(item),
      remark: item.remark
    }))
)

const clearCreditPreviewTimer = () => {
  if (!creditPreviewTimer) return
  clearTimeout(creditPreviewTimer)
  creditPreviewTimer = undefined
}

const loadCreditPreview = async () => {
  if (!dialogVisible.value || isView.value || !formData.customerId) {
    creditPreviewLoading.value = false
    creditPreview.value = undefined
    return
  }

  const requestId = ++creditPreviewRequestId
  creditPreviewLoading.value = true
  try {
    creditPreview.value = await previewSalesOrderCredit(formData.customerId, buildCreditPreviewItems())
  } catch {
    if (requestId === creditPreviewRequestId) {
      creditPreview.value = undefined
    }
  } finally {
    if (requestId === creditPreviewRequestId) {
      creditPreviewLoading.value = false
    }
  }
}

const scheduleCreditPreviewReload = () => {
  clearCreditPreviewTimer()
  if (!dialogVisible.value || isView.value || !formData.customerId) {
    creditPreviewLoading.value = false
    creditPreview.value = undefined
    return
  }
  creditPreviewTimer = setTimeout(() => {
    void loadCreditPreview()
  }, 250)
}

const handleSave = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    const validLines = formData.items.filter((item) => item.productId && item.quantity > 0)
    if (validLines.length === 0) {
      ElMessage.warning('请至少维护一条有效订单明细')
      return
    }
    for (let i = 0; i < validLines.length; i++) {
      const line = validLines[i] as PricedSalesOrderItem
      if (line.minPrice != null && Number(line.price) < Number(line.minPrice)) {
        ElMessage.warning(`第 ${i + 1} 行单价低于最低价 ${formatMoney(line.minPrice)}`)
        return
      }
    }
    submitLoading.value = true
    try {
      const payload = { ...formData, items: validLines }
      if (formData.id) {
        await updateSalesOrder(formData.id, payload)
        ElMessage.success('更新成功')
      } else {
        await createSalesOrder(payload)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch (error) {
      if (!(error instanceof Error)) {
        ElMessage.error('保存销售订单失败')
      }
    } finally {
      submitLoading.value = false
    }
  })
}

const handleSubmitOrder = async (row: SalesOrder) => {
  await runOrderAction(row, '确定提交该销售订单吗？', () => submitSalesOrder(row.id), '提交成功')
}

const handleApprove = async (row: SalesOrder) => {
  await runOrderAction(row, '确定审批通过该销售订单吗？', () => approveSalesOrder(row.id), '审批成功')
}

const handleUnapprove = async (row: SalesOrder) => {
  await runOrderAction(row, '确定反审核该销售订单吗？', () => unapproveSalesOrder(row.id), '反审核成功')
}

const handleReject = async (row: SalesOrder) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回销售订单', {
      inputPlaceholder: '驳回原因',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    await rejectSalesOrder(row.id, value)
    ElMessage.success('驳回成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel' && !(error instanceof Error)) {
      ElMessage.error('驳回失败')
    }
  }
}

const handleCancel = async (row: SalesOrder) => {
  await runOrderAction(row, '确定取消该销售订单吗？', () => cancelSalesOrder(row.id), '取消成功')
}

const runOrderAction = async (
  row: SalesOrder,
  message: string,
  action: () => Promise<SalesOrder>,
  successMessage: string
) => {
  try {
    await ElMessageBox.confirm(`${message}\n${row.orderNo}`, '提示', { type: 'warning' })
    await action()
    ElMessage.success(successMessage)
    loadData()
  } catch (error) {
    if (error !== 'cancel' && !(error instanceof Error)) {
      ElMessage.error('操作失败')
    }
  }
}

const addLine = () => {
  formData.items.push({
    productId: '',
    quantity: 1,
    price: 0,
    taxRate: 0,
    amount: 0,
    minPrice: null,
    priceLevel: null
  } as PricedSalesOrderItem)
}

const removeLine = (index: number) => {
  formData.items.splice(index, 1)
}

const applyResolvedPrice = async (line: PricedSalesOrderItem) => {
  if (!line.productId) {
    line.minPrice = null
    line.priceLevel = null
    return
  }
  const product = products.value.find((item) => String(item.id) === String(line.productId))
  const fallback = Number(product?.salePrice ?? product?.unitPrice ?? 0)

  try {
    const resolved = await resolveSalesPrice({
      productId: line.productId,
      customerId: formData.customerId || undefined,
      bizDate: formData.orderDate || undefined
    })
    if (resolved.matched) {
      line.price = Number(resolved.listPrice ?? fallback)
      line.minPrice = resolved.minPrice != null ? Number(resolved.minPrice) : null
      line.priceLevel = resolved.matchLevel || null
      return
    }
  } catch {
    // 取价失败时回退商品销售价
  }
  line.price = fallback
  line.minPrice = null
  line.priceLevel = null
}

const onProductChange = async (line: PricedSalesOrderItem) => {
  await applyResolvedPrice(line)
}

const onCustomerOrDateChange = async () => {
  if (isView.value) return
  await Promise.all(
    formData.items
      .filter((item) => item.productId)
      .map((item) => applyResolvedPrice(item as PricedSalesOrderItem))
  )
}

const resetForm = () => {
  clearCreditPreviewTimer()
  creditPreviewRequestId += 1
  creditPreviewLoading.value = false
  creditPreview.value = undefined
  formRef.value?.clearValidate()
  Object.assign(formData, {
    id: undefined,
    customerId: '',
    warehouseId: '',
    orderDate: '',
    deliveryDate: '',
    remark: '',
    items: []
  })
}

const canEdit = (row: SalesOrder) => row.approvalStatus === 'DRAFT' || row.status === 'DRAFT'
const canSubmit = (row: SalesOrder) => row.approvalStatus === 'DRAFT' || row.status === 'DRAFT'
const canApprove = (row: SalesOrder) => row.approvalStatus === 'IN_APPROVAL' || row.approvalStatus === 'PENDING'
const canUnapprove = (row: SalesOrder) => row.status === 'APPROVED' && row.approvalStatus === 'APPROVED' && row.deliveryStatus === 'NOT_DELIVERED'
const canCancel = (row: SalesOrder) => row.status !== 'CANCELLED' && row.status !== 'CLOSED'
const lineAmount = (row: SalesOrderItem) => Number(row.quantity ?? 0) * Number(row.price ?? 0)
const formatNumber = (value?: number) => formatLocalizedNumber(Number(value ?? 0), { maximumFractionDigits: 4 })
const formatMoney = (value?: number) => formatLocalizedNumber(Number(value ?? 0), {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})
const statusText = (status?: string) => ({
  DRAFT: '草稿',
  SUBMITTED: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CONFIRMED: '已确认',
  CANCELLED: '已取消',
  CLOSED: '已关闭'
}[status || ''] || status || '-')
const approvalText = (status?: string) => ({
  DRAFT: '草稿',
  NOT_SUBMITTED: '未提交',
  IN_APPROVAL: '审批中',
  PENDING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回'
}[status || ''] || status || '-')
const deliveryText = (status?: string) => ({
  NOT_DELIVERED: '未发货',
  PARTIAL: '部分发货',
  COMPLETED: '已发货'
}[status || ''] || status || '-')
const approvalTagType = (status?: string) => {
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  if (status === 'IN_APPROVAL' || status === 'PENDING') return 'warning'
  return 'info'
}

const creditExceededAmount = computed(() => (
  creditPreview.value?.projectedAvailableCredit != null && creditPreview.value.projectedAvailableCredit < 0
    ? Math.abs(creditPreview.value.projectedAvailableCredit)
    : 0
))

const creditPreviewSignature = computed(() => JSON.stringify({
  dialogVisible: dialogVisible.value,
  isView: isView.value,
  customerId: formData.customerId || '',
  items: formData.items.map((item) => ({
    productId: item.productId || '',
    quantity: Number(item.quantity ?? 0),
    price: Number(item.price ?? 0),
    taxRate: Number(item.taxRate ?? 0),
    remark: item.remark || ''
  }))
}))

watch(creditPreviewSignature, () => {
  scheduleCreditPreviewReload()
})

onBeforeUnmount(() => {
  clearCreditPreviewTimer()
})

onMounted(async () => {
  try {
    await loadOptions()
  } catch (error) {
    console.error('加载销售订单选项失败:', error)
  }
  loadData()
})
</script>

<style scoped lang="scss">
.sales-orders-container {
  padding: 20px;

  .search-card,
  .table-card {
    margin-bottom: 20px;
  }

  .card-header,
  .line-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .line-toolbar {
    margin: 12px 0;
    font-weight: 600;
  }

  .credit-preview-card {
    margin-bottom: 16px;
    padding: 16px;
    border: 1px solid #ebeef5;
    border-radius: 12px;
    background: linear-gradient(135deg, #f8fbff 0%, #fdfefe 100%);
  }

  .credit-preview-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 14px;
  }

  .credit-preview-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }

  .credit-preview-subtitle {
    margin-top: 4px;
    color: #909399;
    font-size: 12px;
  }

  .credit-preview-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
    margin-bottom: 14px;
  }

  .credit-preview-item {
    padding: 12px;
    border-radius: 10px;
    background: #fff;
    border: 1px solid #edf2f7;
  }

  .preview-label {
    color: #909399;
    font-size: 12px;
    margin-bottom: 6px;
  }

  .preview-value {
    color: #303133;
    font-size: 18px;
    font-weight: 600;

    &.danger {
      color: #f56c6c;
    }

    &.quiet {
      color: #67c23a;
    }
  }

  .price-hint {
    margin-top: 2px;
    color: #909399;
    font-size: 12px;
    line-height: 1.3;
  }

  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }

  @media (max-width: 900px) {
    .credit-preview-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }

  @media (max-width: 640px) {
    .credit-preview-header {
      flex-direction: column;
      align-items: stretch;
    }

    .credit-preview-grid {
      grid-template-columns: 1fr;
    }
  }
}
</style>
