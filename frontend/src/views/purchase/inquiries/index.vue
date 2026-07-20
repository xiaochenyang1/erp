<template>
  <div class="purchase-inquiry-page">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="searchForm.keyword"
            placeholder="询价单号/标题"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 150px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已提交" value="SUBMITTED" />
            <el-option label="已关闭" value="CLOSED" />
            <el-option label="已作废" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <el-button v-permission="'purchase:inquiry:manage'" type="primary" :icon="Plus" @click="handleCreate">
          新建询价单
        </el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column prop="inquiryNo" label="询价单号" min-width="170" />
        <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
        <el-table-column prop="inquiryDate" label="询价日期" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="中标供应商" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ supplierLabel(row.selectedSupplierId) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="420" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">详情</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'purchase:inquiry:manage'"
              link
              type="primary"
              @click="handleEdit(row)"
            >编辑</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'purchase:inquiry:manage'"
              link
              type="primary"
              @click="handleSubmit(row)"
            >提交</el-button>
            <el-button
              v-if="row.status === 'SUBMITTED'"
              v-permission="'purchase:inquiry:manage'"
              link
              type="success"
              @click="handleAddQuote(row)"
            >录入报价</el-button>
            <el-button
              v-if="row.status === 'SUBMITTED'"
              v-permission="'purchase:inquiry:manage'"
              link
              type="warning"
              @click="handleSelectQuote(row)"
            >选定中标</el-button>
            <el-button
              v-if="row.status === 'CLOSED'"
              v-permission="'purchase:order:create'"
              link
              type="primary"
              @click="handleCreatePo(row)"
            >生成采购订单</el-button>
            <el-button
              v-if="row.status === 'CLOSED'"
              v-permission="'purchase:inquiry:view'"
              link
              type="info"
              @click="handlePoPrefill(row)"
            >查看预填</el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'SUBMITTED'"
              v-permission="'purchase:inquiry:manage'"
              link
              type="danger"
              @click="handleCancel(row)"
            >作废</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="searchForm.pageNo"
        :page-size="searchForm.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>

    <!-- 新建/编辑 -->
    <el-dialog v-model="formVisible" :title="editingId ? '编辑询价单' : '新建询价单'" width="820px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="询价日期" required>
          <el-date-picker v-model="form.inquiryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="form.title" maxlength="128" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <div class="dialog-sub">
        明细
        <el-button link type="primary" @click="addLine">添加行</el-button>
      </div>
      <el-table :data="form.lines" border size="small">
        <el-table-column label="商品" min-width="240">
          <template #default="{ row }">
            <el-select
              v-model="row.productId"
              filterable
              clearable
              placeholder="选择商品"
              style="width: 100%"
              :loading="optionsLoading"
            >
              <el-option
                v-for="product in products"
                :key="product.id"
                :label="productLabel(product)"
                :value="String(product.id)"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.qty" :min="0.0001" :precision="4" :controls="false" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.remark" maxlength="255" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeLine($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 录入报价 -->
    <el-dialog v-model="quoteVisible" title="录入供应商报价" width="520px">
      <el-form :model="quoteForm" label-width="100px">
        <el-form-item label="供应商" required>
          <el-select
            v-model="quoteForm.supplierId"
            filterable
            clearable
            placeholder="选择供应商"
            style="width: 100%"
            :loading="optionsLoading"
          >
            <el-option
              v-for="supplier in suppliers"
              :key="supplier.id"
              :label="supplierLabelByEntity(supplier)"
              :value="String(supplier.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="单价" required>
          <el-input-number v-model="quoteForm.unitPrice" :min="0" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="税率(%)">
          <el-input-number v-model="quoteForm.taxRate" :min="0" :precision="4" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="quoteForm.remark" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quoteVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmQuote">确定</el-button>
      </template>
    </el-dialog>

    <!-- 选定中标 -->
    <el-dialog v-model="selectVisible" title="选定中标报价" width="760px">
      <el-table :data="selectQuotes" border size="small" highlight-current-row @current-change="onSelectQuoteRow">
        <el-table-column prop="id" label="报价ID" min-width="150" />
        <el-table-column label="供应商" min-width="160">
          <template #default="{ row }">{{ supplierLabel(row.supplierId) }}</template>
        </el-table-column>
        <el-table-column prop="unitPrice" label="单价" width="100" align="right" />
        <el-table-column prop="taxRate" label="税率%" width="100" align="right" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="remark" label="备注" min-width="120" />
      </el-table>
      <template #footer>
        <el-button @click="selectVisible = false">取消</el-button>
        <el-button type="warning" :loading="submitting" @click="confirmSelectQuote">确认选定</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="询价单详情" width="860px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="询价单号">{{ current?.inquiryNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="current" :type="statusType(current.status)">{{ statusText(current.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="询价日期">{{ current?.inquiryDate }}</el-descriptions-item>
        <el-descriptions-item label="标题">{{ current?.title || '-' }}</el-descriptions-item>
        <el-descriptions-item label="中标供应商">{{ supplierLabel(current?.selectedSupplierId) }}</el-descriptions-item>
        <el-descriptions-item label="中标报价">{{ current?.selectedQuoteId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ current?.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="dialog-sub">明细</div>
      <el-table :data="current?.lines || []" border size="small">
        <el-table-column prop="lineNo" label="行" width="60" />
        <el-table-column label="商品" min-width="200">
          <template #default="{ row }">{{ productLabelById(row.productId) }}</template>
        </el-table-column>
        <el-table-column prop="qty" label="数量" width="120" align="right" />
        <el-table-column prop="remark" label="备注" min-width="140" />
      </el-table>
      <div class="dialog-sub">报价</div>
      <el-table :data="current?.quotes || []" border size="small">
        <el-table-column prop="id" label="报价ID" min-width="150" />
        <el-table-column label="供应商" min-width="160">
          <template #default="{ row }">{{ supplierLabel(row.supplierId) }}</template>
        </el-table-column>
        <el-table-column prop="unitPrice" label="单价" width="100" align="right" />
        <el-table-column prop="taxRate" label="税率%" width="100" align="right" />
        <el-table-column prop="status" label="状态" width="100" />
      </el-table>
    </el-dialog>

    <!-- PO 预填 -->
    <el-dialog v-model="prefillVisible" title="采购订单预填数据" width="760px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="可一键创建采购订单草稿，或复制 JSON 到其它系统。"
        style="margin-bottom: 12px"
      />
      <el-descriptions v-if="prefill" :column="2" border>
        <el-descriptions-item label="询价单号">{{ prefill.inquiryNo }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ supplierLabel(prefill.supplierId) }}</el-descriptions-item>
        <el-descriptions-item label="订单日期">{{ prefill.orderDate }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ prefill.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="prefill?.lines || []" border size="small" style="margin-top: 12px">
        <el-table-column label="商品" min-width="180">
          <template #default="{ row }">{{ productLabelById(row.productId) }}</template>
        </el-table-column>
        <el-table-column prop="qty" label="数量" width="110" align="right" />
        <el-table-column prop="price" label="单价" width="110" align="right" />
        <el-table-column prop="taxRate" label="税率%" width="110" align="right" />
        <el-table-column prop="remark" label="备注" min-width="120" />
      </el-table>
      <template #footer>
        <el-button @click="copyPrefill">复制 JSON</el-button>
        <el-button
          v-permission="'purchase:order:create'"
          type="primary"
          :loading="creatingPo"
          @click="confirmCreatePoFromPrefill"
        >
          创建采购订单草稿
        </el-button>
        <el-button @click="prefillVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  getPurchaseInquiries,
  getPurchaseInquiry,
  createPurchaseInquiry,
  updatePurchaseInquiry,
  submitPurchaseInquiry,
  addPurchaseInquiryQuote,
  selectPurchaseInquiryQuote,
  getPurchaseInquiryPoPrefill,
  cancelPurchaseInquiry,
  createPurchaseOrder,
  type PurchaseInquiry,
  type PurchaseInquiryQuery,
  type PurchaseInquiryQuote,
  type PurchaseInquiryPoPrefill
} from '@/api/purchase'
import { getProducts, getSuppliers, type Product, type Supplier } from '@/api/masterdata'

const loading = ref(false)
const submitting = ref(false)
const creatingPo = ref(false)
const optionsLoading = ref(false)
const tableData = ref<PurchaseInquiry[]>([])
const total = ref(0)
const current = ref<PurchaseInquiry>()
const products = ref<Product[]>([])
const suppliers = ref<Supplier[]>([])

const searchForm = reactive<PurchaseInquiryQuery>({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  status: ''
})

const statusText = (status: string) =>
  ({ DRAFT: '草稿', SUBMITTED: '已提交', CLOSED: '已关闭', CANCELLED: '已作废' }[status] || status)

const statusType = (status: string) =>
  ({ DRAFT: 'info', SUBMITTED: 'warning', CLOSED: 'success', CANCELLED: 'danger' }[status] || 'info') as
    | 'info'
    | 'warning'
    | 'success'
    | 'danger'

const productLabel = (product: Product) =>
  `${product.productCode || ''} ${product.productName || ''}`.trim() || String(product.id)

const productLabelById = (productId?: string | number | null) => {
  if (productId == null || productId === '') return '-'
  const found = products.value.find((item) => String(item.id) === String(productId))
  return found ? productLabel(found) : String(productId)
}

const supplierLabelByEntity = (supplier: Supplier) =>
  supplier.supplierName || supplier.name || String(supplier.id)

const supplierLabel = (supplierId?: string | number | null) => {
  if (supplierId == null || supplierId === '') return '-'
  const found = suppliers.value.find((item) => String(item.id) === String(supplierId))
  return found ? supplierLabelByEntity(found) : String(supplierId)
}

const loadOptions = async () => {
  optionsLoading.value = true
  try {
    const [productPage, supplierPage] = await Promise.all([
      getProducts({ pageNo: 1, pageSize: 200, status: 'ACTIVE' }),
      getSuppliers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    ])
    products.value = productPage.records || []
    suppliers.value = supplierPage.records || []
  } catch {
    // 拦截器已提示
  } finally {
    optionsLoading.value = false
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getPurchaseInquiries(searchForm)
    tableData.value = res.records
    total.value = res.total
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  searchForm.pageNo = 1
  loadData()
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.status = ''
  searchForm.pageNo = 1
  loadData()
}

const handlePageChange = (page: number) => {
  searchForm.pageNo = page
  loadData()
}

const handleSizeChange = (size: number) => {
  searchForm.pageSize = size
  searchForm.pageNo = 1
  loadData()
}

const today = () => {
  const d = new Date()
  const m = `${d.getMonth() + 1}`.padStart(2, '0')
  const day = `${d.getDate()}`.padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

// ---- 新建/编辑 ----
const formVisible = ref(false)
const editingId = ref<string | number | null>(null)
const form = reactive<{
  inquiryDate: string
  title: string
  remark: string
  lines: Array<{ productId: string; qty: number; remark: string }>
}>({
  inquiryDate: '',
  title: '',
  remark: '',
  lines: []
})

const resetForm = () => {
  editingId.value = null
  form.inquiryDate = today()
  form.title = ''
  form.remark = ''
  form.lines = [{ productId: '', qty: 1, remark: '' }]
}

const addLine = () => {
  form.lines.push({ productId: '', qty: 1, remark: '' })
}

const removeLine = (index: number) => {
  if (form.lines.length <= 1) {
    ElMessage.warning('至少保留一行明细')
    return
  }
  form.lines.splice(index, 1)
}

const handleCreate = async () => {
  await loadOptions()
  resetForm()
  formVisible.value = true
}

const handleEdit = async (row: PurchaseInquiry) => {
  try {
    await loadOptions()
    const detail = await getPurchaseInquiry(row.id)
    editingId.value = detail.id
    form.inquiryDate = detail.inquiryDate
    form.title = detail.title || ''
    form.remark = detail.remark || ''
    form.lines = (detail.lines || []).map((line) => ({
      productId: String(line.productId),
      qty: Number(line.qty || 0),
      remark: line.remark || ''
    }))
    if (!form.lines.length) {
      form.lines = [{ productId: '', qty: 1, remark: '' }]
    }
    formVisible.value = true
  } catch {
    // 拦截器已提示
  }
}

const confirmSave = async () => {
  if (!form.inquiryDate) {
    ElMessage.warning('请选择询价日期')
    return
  }
  const lines = form.lines
    .filter((line) => String(line.productId || '').trim())
    .map((line) => ({
      productId: line.productId,
      qty: Number(line.qty),
      remark: line.remark || undefined
    }))
  if (!lines.length) {
    ElMessage.warning('请至少选择一行商品明细')
    return
  }
  if (lines.some((line) => !line.qty || line.qty <= 0)) {
    ElMessage.warning('数量必须大于 0')
    return
  }
  submitting.value = true
  try {
    const payload = {
      inquiryDate: form.inquiryDate,
      title: form.title || undefined,
      remark: form.remark || undefined,
      lines
    }
    if (editingId.value) {
      await updatePurchaseInquiry(editingId.value, payload)
      ElMessage.success('保存成功')
    } else {
      await createPurchaseInquiry(payload)
      ElMessage.success('创建成功')
    }
    formVisible.value = false
    loadData()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

// ---- 提交/作废 ----
const handleSubmit = async (row: PurchaseInquiry) => {
  try {
    await ElMessageBox.confirm(`确认提交询价单「${row.inquiryNo}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await submitPurchaseInquiry(row.id)
    ElMessage.success('已提交')
    loadData()
  } catch {
    // 拦截器已提示
  }
}

const handleCancel = async (row: PurchaseInquiry) => {
  try {
    await ElMessageBox.confirm(`确认作废询价单「${row.inquiryNo}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await cancelPurchaseInquiry(row.id)
    ElMessage.success('已作废')
    loadData()
  } catch {
    // 拦截器已提示
  }
}

// ---- 报价 ----
const quoteVisible = ref(false)
const quoteInquiryId = ref<string | number | null>(null)
const quoteForm = reactive({
  supplierId: '',
  unitPrice: 0,
  taxRate: 13,
  remark: ''
})

const handleAddQuote = async (row: PurchaseInquiry) => {
  await loadOptions()
  quoteInquiryId.value = row.id
  quoteForm.supplierId = ''
  quoteForm.unitPrice = 0
  quoteForm.taxRate = 13
  quoteForm.remark = ''
  quoteVisible.value = true
}

const confirmQuote = async () => {
  if (!quoteInquiryId.value) return
  if (!String(quoteForm.supplierId || '').trim()) {
    ElMessage.warning('请选择供应商')
    return
  }
  if (quoteForm.unitPrice == null || quoteForm.unitPrice < 0) {
    ElMessage.warning('请填写单价')
    return
  }
  submitting.value = true
  try {
    await addPurchaseInquiryQuote(quoteInquiryId.value, {
      supplierId: quoteForm.supplierId,
      unitPrice: quoteForm.unitPrice,
      taxRate: quoteForm.taxRate,
      remark: quoteForm.remark || undefined
    })
    ElMessage.success('报价已录入')
    quoteVisible.value = false
    loadData()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

// ---- 选定中标 ----
const selectVisible = ref(false)
const selectInquiryId = ref<string | number | null>(null)
const selectQuotes = ref<PurchaseInquiryQuote[]>([])
const selectedQuoteId = ref<string | number | null>(null)

const handleSelectQuote = async (row: PurchaseInquiry) => {
  try {
    await loadOptions()
    const detail = await getPurchaseInquiry(row.id)
    selectInquiryId.value = detail.id
    selectQuotes.value = (detail.quotes || []).filter((q) => q.status === 'PENDING')
    selectedQuoteId.value = null
    if (!selectQuotes.value.length) {
      ElMessage.warning('暂无待选报价，请先录入')
      return
    }
    selectVisible.value = true
  } catch {
    // 拦截器已提示
  }
}

const onSelectQuoteRow = (row: PurchaseInquiryQuote | undefined) => {
  selectedQuoteId.value = row?.id ?? null
}

const confirmSelectQuote = async () => {
  if (!selectInquiryId.value || !selectedQuoteId.value) {
    ElMessage.warning('请先点选一条报价')
    return
  }
  submitting.value = true
  try {
    await selectPurchaseInquiryQuote(selectInquiryId.value, selectedQuoteId.value)
    ElMessage.success('已选定中标报价')
    selectVisible.value = false
    loadData()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

// ---- 详情 / PO 预填 / 创建 PO ----
const detailVisible = ref(false)
const handleView = async (row: PurchaseInquiry) => {
  try {
    await loadOptions()
    current.value = await getPurchaseInquiry(row.id)
    detailVisible.value = true
  } catch {
    // 拦截器已提示
  }
}

const prefillVisible = ref(false)
const prefill = ref<PurchaseInquiryPoPrefill>()
const prefillInquiryId = ref<string | number | null>(null)

const loadPrefill = async (inquiryId: string | number) => {
  prefillInquiryId.value = inquiryId
  prefill.value = await getPurchaseInquiryPoPrefill(inquiryId)
}

const handlePoPrefill = async (row: PurchaseInquiry) => {
  try {
    await loadOptions()
    await loadPrefill(row.id)
    prefillVisible.value = true
  } catch {
    // 拦截器已提示
  }
}

const buildPoPayloadFromPrefill = (source: PurchaseInquiryPoPrefill) => ({
  supplierId: source.supplierId,
  orderDate: source.orderDate,
  remark: source.remark,
  items: (source.lines || []).map((line) => ({
    productId: line.productId,
    quantity: Number(line.qty || 0),
    qty: Number(line.qty || 0),
    price: Number(line.price || 0),
    taxRate: Number(line.taxRate || 0),
    amount: Number(line.qty || 0) * Number(line.price || 0),
    remark: line.remark
  }))
})

const confirmCreatePoFromPrefill = async () => {
  if (!prefill.value) return
  creatingPo.value = true
  try {
    const order = await createPurchaseOrder(buildPoPayloadFromPrefill(prefill.value))
    ElMessage.success(`已创建采购订单草稿 ${order.orderNo}`)
    prefillVisible.value = false
  } catch {
    // 拦截器已提示
  } finally {
    creatingPo.value = false
  }
}

const handleCreatePo = async (row: PurchaseInquiry) => {
  try {
    await ElMessageBox.confirm(
      `确认根据询价单「${row.inquiryNo}」创建采购订单草稿吗？`,
      '生成采购订单',
      { type: 'warning' }
    )
  } catch {
    return
  }
  creatingPo.value = true
  try {
    await loadOptions()
    const source = await getPurchaseInquiryPoPrefill(row.id)
    const order = await createPurchaseOrder(buildPoPayloadFromPrefill(source))
    ElMessage.success(`已创建采购订单草稿 ${order.orderNo}`)
    loadData()
  } catch {
    // 拦截器已提示
  } finally {
    creatingPo.value = false
  }
}

const copyPrefill = async () => {
  if (!prefill.value) return
  const payload = buildPoPayloadFromPrefill(prefill.value)
  try {
    await navigator.clipboard.writeText(JSON.stringify(payload, null, 2))
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动选择文本')
  }
}

onMounted(async () => {
  await loadOptions()
  await loadData()
})
</script>

<style scoped>
.purchase-inquiry-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.search-card :deep(.el-card__body),
.table-card :deep(.el-card__body) {
  padding: 16px;
}
.toolbar {
  margin-bottom: 12px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.dialog-sub {
  margin: 12px 0 8px;
  font-weight: 600;
}
</style>
