<template>
  <div class="purchase-inquiry-page">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item :label="$t('purchaseInquiryOps.keyword')">
          <el-input
            v-model="searchForm.keyword"
            :placeholder="$t('purchaseInquiryOps.placeholder.keyword')"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="$t('purchaseInquiryOps.statusLabel')">
          <el-select v-model="searchForm.status" :placeholder="$t('purchaseInquiryOps.placeholder.all')" clearable style="width: 150px">
            <el-option :label="$t('purchaseInquiryOps.status.draft')" value="DRAFT" />
            <el-option :label="$t('purchaseInquiryOps.status.submitted')" value="SUBMITTED" />
            <el-option :label="$t('purchaseInquiryOps.status.closed')" value="CLOSED" />
            <el-option :label="$t('purchaseInquiryOps.status.converted')" value="CONVERTED" />
            <el-option :label="$t('purchaseInquiryOps.status.cancelled')" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('purchaseInquiryOps.action.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('purchaseInquiryOps.action.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <el-button v-permission="'purchase:inquiry:manage'" type="primary" :icon="Plus" @click="handleCreate">
          {{ $t('purchaseInquiryOps.action.create') }}
        </el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column prop="inquiryNo" :label="$t('purchaseInquiryOps.inquiryNo')" min-width="170" />
        <el-table-column prop="title" :label="$t('purchaseInquiryOps.title')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="inquiryDate" :label="$t('purchaseInquiryOps.inquiryDate')" width="120" />
        <el-table-column :label="$t('purchaseInquiryOps.statusLabel')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('purchaseInquiryOps.winningSupplier')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ supplierLabel(row.selectedSupplierId) }}
          </template>
        </el-table-column>
        <el-table-column prop="convertedOrderNo" :label="$t('purchaseInquiryOps.purchaseOrder')" min-width="160">
          <template #default="{ row }">{{ row.convertedOrderNo || '-' }}</template>
        </el-table-column>
        <el-table-column :label="$t('purchaseInquiryOps.actions')" width="480" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">{{ $t('purchaseInquiryOps.action.view') }}</el-button>
            <el-button link type="primary" @click="handlePrint(row)">{{ $t('purchaseInquiryOps.action.print') }}</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'purchase:inquiry:manage'"
              link
              type="primary"
              @click="handleEdit(row)"
            >{{ $t('purchaseInquiryOps.action.edit') }}</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'purchase:inquiry:manage'"
              link
              type="primary"
              @click="handleSubmit(row)"
            >{{ $t('purchaseInquiryOps.action.submit') }}</el-button>
            <el-button
              v-if="row.status === 'SUBMITTED'"
              v-permission="'purchase:inquiry:manage'"
              link
              type="success"
              @click="handleAddQuote(row)"
            >{{ $t('purchaseInquiryOps.action.addQuote') }}</el-button>
            <el-button
              v-if="row.status === 'SUBMITTED'"
              v-permission="'purchase:inquiry:manage'"
              link
              type="warning"
              @click="handleSelectQuote(row)"
            >{{ $t('purchaseInquiryOps.action.selectWinner') }}</el-button>
            <el-button
              v-if="row.status === 'CLOSED' && canConvertToPurchaseOrder"
              link
              type="primary"
              @click="handleCreatePo(row)"
            >{{ $t('purchaseInquiryOps.action.createPurchaseOrder') }}</el-button>
            <el-button
              v-if="row.status === 'CLOSED' || row.status === 'CONVERTED'"
              v-permission="'purchase:inquiry:view'"
              link
              type="info"
              @click="handlePoPrefill(row)"
            >{{ $t('purchaseInquiryOps.action.viewPrefill') }}</el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'SUBMITTED'"
              v-permission="'purchase:inquiry:manage'"
              link
              type="danger"
              @click="handleCancel(row)"
            >{{ $t('purchaseInquiryOps.action.void') }}</el-button>
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
    <el-dialog v-model="formVisible" :title="$t(editingId ? 'purchaseInquiryOps.dialog.edit' : 'purchaseInquiryOps.dialog.create')" width="820px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item :label="$t('purchaseInquiryOps.inquiryDate')" required>
          <el-date-picker v-model="form.inquiryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('purchaseInquiryOps.title')">
          <el-input v-model="form.title" maxlength="128" />
        </el-form-item>
        <el-form-item :label="$t('purchaseInquiryOps.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <div class="dialog-sub">
        {{ $t('purchaseInquiryOps.details') }}
        <el-button link type="primary" @click="addLine">{{ $t('purchaseInquiryOps.action.addLine') }}</el-button>
      </div>
      <el-table :data="form.lines" border size="small">
        <el-table-column :label="$t('purchaseInquiryOps.product')" min-width="240">
          <template #default="{ row }">
            <el-select
              v-model="row.productId"
              filterable
              clearable
              :placeholder="$t('purchaseInquiryOps.placeholder.product')"
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
        <el-table-column :label="$t('purchaseInquiryOps.quantity')" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.qty" :min="0.0001" :precision="4" :controls="false" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column :label="$t('purchaseInquiryOps.remark')" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.remark" maxlength="255" />
          </template>
        </el-table-column>
        <el-table-column :label="$t('purchaseInquiryOps.actions')" width="80">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeLine($index)">{{ $t('purchaseInquiryOps.action.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="formVisible = false">{{ $t('purchaseInquiryOps.action.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmSave">{{ $t('purchaseInquiryOps.action.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 录入报价 -->
    <el-dialog v-model="quoteVisible" :title="$t('purchaseInquiryOps.dialog.addQuote')" width="860px">
      <el-form :model="quoteForm" label-width="100px">
        <el-form-item :label="$t('purchaseInquiryOps.supplier')" required>
          <el-select
            v-model="quoteForm.supplierId"
            filterable
            clearable
            :placeholder="$t('purchaseInquiryOps.placeholder.supplier')"
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
        <el-form-item :label="$t('purchaseInquiryOps.remark')">
          <el-input v-model="quoteForm.remark" maxlength="255" />
        </el-form-item>
      </el-form>
      <div class="dialog-sub">{{ $t('purchaseInquiryOps.lineQuotes') }}</div>
      <el-table :data="quoteForm.lines" border size="small">
        <el-table-column :label="$t('purchaseInquiryOps.product')" min-width="220">
          <template #default="{ row }">{{ productLabelById(row.productId) }}</template>
        </el-table-column>
        <el-table-column prop="qty" :label="$t('purchaseInquiryOps.inquiryQuantity')" width="110" align="right" />
        <el-table-column :label="$t('purchaseInquiryOps.unitPrice')" width="160">
          <template #default="{ row }">
            <el-input-number v-model="row.unitPrice" :min="0" :precision="2" :controls="false" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column :label="$t('purchaseInquiryOps.taxRatePercent')" width="160">
          <template #default="{ row }">
            <el-input-number v-model="row.taxRate" :min="0" :precision="4" :controls="false" style="width: 100%" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="quoteVisible = false">{{ $t('purchaseInquiryOps.action.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmQuote">{{ $t('purchaseInquiryOps.action.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 选定中标 -->
    <el-dialog v-model="selectVisible" :title="$t('purchaseInquiryOps.dialog.selectQuote')" width="760px">
      <el-table :data="selectQuotes" border size="small" highlight-current-row @current-change="onSelectQuoteRow">
        <el-table-column prop="id" :label="$t('purchaseInquiryOps.quoteId')" min-width="150" />
        <el-table-column :label="$t('purchaseInquiryOps.supplier')" min-width="160">
          <template #default="{ row }">{{ supplierLabel(row.supplierId) }}</template>
        </el-table-column>
        <el-table-column :label="$t('purchaseInquiryOps.lineQuotes')" min-width="360">
          <template #default="{ row }">
            <div v-if="row.lines?.length" class="quote-line-list">
              <div v-for="line in row.lines" :key="line.id" class="quote-line-row">
                <span>{{ inquiryLineProductLabel(line.inquiryLineId, selectInquiryLines) }}</span>
                <span class="quote-line-meta">{{ $t('purchaseInquiryOps.lineQuoteSummary', { price: line.unitPrice, rate: line.taxRate ?? 0 }) }}</span>
              </div>
            </div>
            <div v-else class="quote-line-meta">
              {{ $t('purchaseInquiryOps.legacyQuoteSummary', { price: row.unitPrice ?? '-', rate: row.taxRate ?? 0 }) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('purchaseInquiryOps.statusLabel')" width="100">
          <template #default="{ row }">{{ quoteStatusText(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="remark" :label="$t('purchaseInquiryOps.remark')" min-width="120" />
      </el-table>
      <template #footer>
        <el-button @click="selectVisible = false">{{ $t('purchaseInquiryOps.action.cancel') }}</el-button>
        <el-button type="warning" :loading="submitting" @click="confirmSelectQuote">{{ $t('purchaseInquiryOps.action.confirmSelection') }}</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" :title="$t('purchaseInquiryOps.dialog.detail')" width="860px">
      <el-descriptions :column="2" border>
        <el-descriptions-item :label="$t('purchaseInquiryOps.inquiryNo')">{{ current?.inquiryNo }}</el-descriptions-item>
        <el-descriptions-item :label="$t('purchaseInquiryOps.statusLabel')">
          <el-tag v-if="current" :type="statusType(current.status)">{{ statusText(current.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('purchaseInquiryOps.inquiryDate')">{{ current?.inquiryDate }}</el-descriptions-item>
        <el-descriptions-item :label="$t('purchaseInquiryOps.title')">{{ current?.title || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('purchaseInquiryOps.winningSupplier')">{{ supplierLabel(current?.selectedSupplierId) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('purchaseInquiryOps.winningQuote')">{{ current?.selectedQuoteId || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('purchaseInquiryOps.purchaseOrder')">{{ current?.convertedOrderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('purchaseInquiryOps.convertedTime')">{{ current?.convertedTime || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('purchaseInquiryOps.remark')" :span="2">{{ current?.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="dialog-sub">{{ $t('purchaseInquiryOps.details') }}</div>
      <el-table :data="current?.lines || []" border size="small">
        <el-table-column prop="lineNo" :label="$t('purchaseInquiryOps.lineNo')" width="60" />
        <el-table-column :label="$t('purchaseInquiryOps.product')" min-width="200">
          <template #default="{ row }">{{ productLabelById(row.productId) }}</template>
        </el-table-column>
        <el-table-column prop="qty" :label="$t('purchaseInquiryOps.quantity')" width="120" align="right" />
        <el-table-column prop="remark" :label="$t('purchaseInquiryOps.remark')" min-width="140" />
      </el-table>
      <div class="dialog-sub">{{ $t('purchaseInquiryOps.quotes') }}</div>
      <el-table :data="current?.quotes || []" border size="small">
        <el-table-column prop="id" :label="$t('purchaseInquiryOps.quoteId')" min-width="150" />
        <el-table-column :label="$t('purchaseInquiryOps.supplier')" min-width="160">
          <template #default="{ row }">{{ supplierLabel(row.supplierId) }}</template>
        </el-table-column>
        <el-table-column :label="$t('purchaseInquiryOps.lineQuotes')" min-width="360">
          <template #default="{ row }">
            <div v-if="row.lines?.length" class="quote-line-list">
              <div v-for="line in row.lines" :key="line.id" class="quote-line-row">
                <span>{{ inquiryLineProductLabel(line.inquiryLineId, current?.lines || []) }}</span>
                <span class="quote-line-meta">{{ $t('purchaseInquiryOps.lineQuoteSummary', { price: line.unitPrice, rate: line.taxRate ?? 0 }) }}</span>
              </div>
            </div>
            <div v-else class="quote-line-meta">
              {{ $t('purchaseInquiryOps.legacyQuoteSummary', { price: row.unitPrice ?? '-', rate: row.taxRate ?? 0 }) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('purchaseInquiryOps.statusLabel')" width="100">
          <template #default="{ row }">{{ quoteStatusText(row.status) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- PO 预填 -->
    <el-dialog v-model="prefillVisible" :title="$t('purchaseInquiryOps.dialog.prefill')" width="760px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        :title="$t('purchaseInquiryOps.prefillNotice')"
        style="margin-bottom: 12px"
      />
      <el-descriptions v-if="prefill" :column="2" border>
        <el-descriptions-item :label="$t('purchaseInquiryOps.inquiryNo')">{{ prefill.inquiryNo }}</el-descriptions-item>
        <el-descriptions-item :label="$t('purchaseInquiryOps.supplier')">{{ supplierLabel(prefill.supplierId) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('purchaseInquiryOps.orderDate')">{{ prefill.orderDate }}</el-descriptions-item>
        <el-descriptions-item :label="$t('purchaseInquiryOps.remark')">{{ prefill.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="prefill?.lines || []" border size="small" style="margin-top: 12px">
        <el-table-column :label="$t('purchaseInquiryOps.product')" min-width="180">
          <template #default="{ row }">{{ productLabelById(row.productId) }}</template>
        </el-table-column>
        <el-table-column prop="qty" :label="$t('purchaseInquiryOps.quantity')" width="110" align="right" />
        <el-table-column prop="price" :label="$t('purchaseInquiryOps.unitPrice')" width="110" align="right" />
        <el-table-column prop="taxRate" :label="$t('purchaseInquiryOps.taxRatePercent')" width="110" align="right" />
        <el-table-column prop="remark" :label="$t('purchaseInquiryOps.remark')" min-width="120" />
      </el-table>
      <template #footer>
        <el-button @click="copyPrefill">{{ $t('purchaseInquiryOps.action.copyJson') }}</el-button>
        <el-button
          v-if="canConvertToPurchaseOrder && prefillConversionAvailable"
          type="primary"
          :loading="creatingPo"
          @click="confirmCreatePoFromPrefill"
        >
          {{ $t('purchaseInquiryOps.action.createOrderDraft') }}
        </el-button>
        <el-button @click="prefillVisible = false">{{ $t('purchaseInquiryOps.action.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
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
  convertPurchaseInquiryToPurchaseOrder,
  cancelPurchaseInquiry,
  type PurchaseInquiry,
  type PurchaseInquiryPoPrefill
} from '@/api/purchase'
import { getProducts, getSuppliers } from '@/api/masterdata'
import { useUserStore } from '@/store/modules/user'
import { printPurchaseInquiry } from '@/utils/bizPrint'
import { usePurchaseInquiryPresentation } from '@/composables/usePurchaseInquiryPresentation'
import { usePurchaseInquiryList } from '@/composables/usePurchaseInquiryList'
import { usePurchaseInquiryForm } from '@/composables/usePurchaseInquiryForm'
import { usePurchaseInquiryQuotes } from '@/composables/usePurchaseInquiryQuotes'

const { t } = useI18n()
const userStore = useUserStore()
const canConvertToPurchaseOrder = computed(() =>
  userStore.hasPermission('purchase:inquiry:manage')
  && userStore.hasPermission('purchase:order:create'))

const {
  handleCancel,
  handlePageChange,
  handlePrint,
  handleReset,
  handleSearch,
  handleSizeChange,
  handleSubmit,
  loadData,
  loadOptions,
  loading,
  optionsLoading,
  products,
  searchForm,
  suppliers,
  tableData,
  total
} = usePurchaseInquiryList(t, {
  getInquiries: getPurchaseInquiries,
  getInquiry: getPurchaseInquiry,
  submitInquiry: submitPurchaseInquiry,
  cancelInquiry: cancelPurchaseInquiry,
  getProducts,
  getSuppliers,
  printInquiry: printPurchaseInquiry,
  confirm: (message, title, opts) => ElMessageBox.confirm(message, title, opts as any),
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message)
})

const {
  inquiryLineProductLabel,
  productLabel,
  productLabelById,
  quoteStatusText,
  statusText,
  statusType,
  supplierLabel,
  supplierLabelByEntity
} = usePurchaseInquiryPresentation(t, products, suppliers)

const creatingPo = ref(false)
const current = ref<PurchaseInquiry>()

const {
  addLine,
  confirmSave,
  editingId,
  form,
  formVisible,
  handleCreate,
  handleEdit,
  removeLine,
  submitting: formSubmitting
} = usePurchaseInquiryForm(t, {
  loadOptions,
  getInquiry: getPurchaseInquiry,
  createInquiry: createPurchaseInquiry,
  updateInquiry: updatePurchaseInquiry,
  onSuccess: (message) => ElMessage.success(message),
  onWarning: (message) => ElMessage.warning(message),
  onCompleted: () => loadData()
})

const {
  confirmQuote,
  confirmSelectQuote,
  handleAddQuote,
  handleSelectQuote,
  onSelectQuoteRow,
  quoteForm,
  quoteVisible,
  selectInquiryLines,
  selectQuotes,
  selectVisible,
  submitting: quoteSubmitting
} = usePurchaseInquiryQuotes(t, {
  loadOptions,
  getInquiry: getPurchaseInquiry,
  addQuote: addPurchaseInquiryQuote,
  selectQuote: selectPurchaseInquiryQuote,
  onSuccess: (message) => ElMessage.success(message),
  onWarning: (message) => ElMessage.warning(message),
  onError: (message) => ElMessage.error(message),
  onCompleted: () => loadData()
})

const submitting = computed(() => formSubmitting.value || quoteSubmitting.value)

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
const prefillConversionAvailable = ref(false)

const loadPrefill = async (inquiryId: string | number) => {
  prefillInquiryId.value = inquiryId
  prefill.value = await getPurchaseInquiryPoPrefill(inquiryId)
}

const handlePoPrefill = async (row: PurchaseInquiry) => {
  try {
    await loadOptions()
    prefillConversionAvailable.value = row.status === 'CLOSED'
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
  if (!prefill.value || !prefillInquiryId.value) return
  creatingPo.value = true
  try {
    const order = await convertPurchaseInquiryToPurchaseOrder(prefillInquiryId.value)
    ElMessage.success(t('purchaseInquiryOps.message.orderCreated', { no: order.orderNo }))
    prefillVisible.value = false
    await loadData()
  } catch {
    // 拦截器已提示
  } finally {
    creatingPo.value = false
  }
}

const handleCreatePo = async (row: PurchaseInquiry) => {
  try {
    await ElMessageBox.confirm(
      t('purchaseInquiryOps.message.createOrderConfirm', { no: row.inquiryNo }),
      t('purchaseInquiryOps.dialog.createPurchaseOrder'),
      {
        type: 'warning',
        confirmButtonText: t('purchaseInquiryOps.action.confirm'),
        cancelButtonText: t('purchaseInquiryOps.action.cancel')
      }
    )
  } catch {
    return
  }
  creatingPo.value = true
  try {
    const order = await convertPurchaseInquiryToPurchaseOrder(row.id)
    ElMessage.success(t('purchaseInquiryOps.message.orderCreated', { no: order.orderNo }))
    await loadData()
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
    ElMessage.success(t('purchaseInquiryOps.message.copied'))
  } catch {
    ElMessage.error(t('purchaseInquiryOps.message.copyFailed'))
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
.quote-line-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.quote-line-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.quote-line-meta {
  color: #606266;
  white-space: nowrap;
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
