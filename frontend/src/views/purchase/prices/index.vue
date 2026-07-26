<template>
  <div class="purchase-price-page">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item :label="$t('purchasePrice.keyword')">
          <el-input
            v-model="searchForm.keyword"
            :placeholder="$t('purchasePrice.keywordPlaceholder')"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="$t('purchasePrice.supplier')">
          <el-select
            v-model="searchForm.supplierId"
            clearable
            filterable
            :placeholder="$t('purchasePrice.allWithGeneral')"
            style="width: 200px"
          >
            <el-option
              v-for="supplier in suppliers"
              :key="supplier.id"
              :label="supplier.supplierName || supplier.name"
              :value="String(supplier.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('purchasePrice.status')">
          <el-select v-model="searchForm.status" clearable :placeholder="$t('purchasePrice.all')" style="width: 140px">
            <el-option :label="$t('purchasePrice.active')" value="ACTIVE" />
            <el-option :label="$t('purchasePrice.inactive')" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('purchasePrice.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('purchasePrice.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <el-button v-permission="'purchase:price:manage'" type="primary" :icon="Plus" @click="handleCreate">
          {{ $t('purchasePrice.create') }}
        </el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column :label="$t('purchasePrice.scope')" min-width="160">
          <template #default="{ row }">
            <el-tag v-if="row.supplierId" type="warning" size="small">{{ $t('purchasePrice.supplierSpecific') }}</el-tag>
            <el-tag v-else type="info" size="small">{{ $t('purchasePrice.productGeneral') }}</el-tag>
            <div class="sub">{{ row.supplierId ? row.supplierName || row.supplierId : $t('purchasePrice.allSuppliers') }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="$t('purchasePrice.product')" min-width="180">
          <template #default="{ row }">
            <div>{{ row.productCode }} {{ row.productName }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="listPrice" :label="$t('purchasePrice.listPrice')" width="130" align="right">
          <template #default="{ row }">{{ formatLocalizedCurrency(row.listPrice) }}</template>
        </el-table-column>
        <el-table-column prop="maxPrice" :label="$t('purchasePrice.maxPrice')" width="130" align="right">
          <template #default="{ row }">{{ formatLocalizedCurrency(row.maxPrice) }}</template>
        </el-table-column>
        <el-table-column :label="$t('purchasePrice.effectivePeriod')" min-width="220">
          <template #default="{ row }">
            {{ formatLocalizedDate(row.effectiveFrom) }} ~ {{ row.effectiveTo ? formatLocalizedDate(row.effectiveTo) : $t('purchasePrice.longTerm') }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('purchasePrice.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? $t('purchasePrice.active') : $t('purchasePrice.inactive') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="$t('purchasePrice.remark')" min-width="120" show-overflow-tooltip />
        <el-table-column :label="$t('purchasePrice.actions')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handlePrint(row)">
              {{ $t('purchasePrice.print') }}
            </el-button>
            <el-button v-permission="'purchase:price:manage'" link type="primary" @click="handleEdit(row)">
              {{ $t('purchasePrice.edit') }}
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'purchase:price:manage'"
              link
              type="warning"
              @click="handleDisable(row)"
            >
              {{ $t('purchasePrice.disable') }}
            </el-button>
            <el-button
              v-else
              v-permission="'purchase:price:manage'"
              link
              type="success"
              @click="handleEnable(row)"
            >
              {{ $t('purchasePrice.enable') }}
            </el-button>
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

    <el-dialog v-model="dialogVisible" :title="editingId ? $t('purchasePrice.editTitle') : $t('purchasePrice.createTitle')" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item :label="$t('purchasePrice.scope')">
          <el-radio-group v-model="scopeType">
            <el-radio-button value="PRODUCT">{{ $t('purchasePrice.productGeneral') }}</el-radio-button>
            <el-radio-button value="SUPPLIER">{{ $t('purchasePrice.supplierSpecific') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="scopeType === 'SUPPLIER'" :label="$t('purchasePrice.supplier')" prop="supplierId">
          <el-select v-model="form.supplierId" filterable :placeholder="$t('purchasePrice.selectSupplier')" style="width: 100%">
            <el-option
              v-for="supplier in suppliers"
              :key="supplier.id"
              :label="supplier.supplierName || supplier.name"
              :value="String(supplier.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('purchasePrice.product')" prop="productId">
          <el-select v-model="form.productId" filterable :placeholder="$t('purchasePrice.selectProduct')" style="width: 100%">
            <el-option
              v-for="product in products"
              :key="product.id"
              :label="`${product.productCode || ''} ${product.productName || ''}`.trim()"
              :value="String(product.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('purchasePrice.listPrice')" prop="listPrice">
          <el-input-number v-model="form.listPrice" :min="0" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('purchasePrice.maxPrice')" prop="maxPrice">
          <el-input-number v-model="form.maxPrice" :min="0" :precision="2" :controls="false" style="width: 100%" />
          <div class="form-tip">{{ $t('purchasePrice.maxPriceTip') }}</div>
        </el-form-item>
        <el-form-item :label="$t('purchasePrice.effectiveFrom')" prop="effectiveFrom">
          <el-date-picker v-model="form.effectiveFrom" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('purchasePrice.effectiveTo')">
          <el-date-picker
            v-model="form.effectiveTo"
            type="date"
            value-format="YYYY-MM-DD"
            clearable
            :placeholder="$t('purchasePrice.noExpiry')"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('purchasePrice.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('purchasePrice.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmSave">{{ $t('purchasePrice.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  createPurchasePrice,
  disablePurchasePrice,
  enablePurchasePrice,
  getPurchasePrice,
  getPurchasePrices,
  updatePurchasePrice,
  type PurchasePrice,
  type PurchasePriceQuery
} from '@/api/purchase'
import { getSuppliers, getProducts, type Supplier, type Product } from '@/api/masterdata'
import { formatBusinessDate, formatLocalizedCurrency, formatLocalizedDate } from '@/utils/locale'
import { printPurchasePrice } from '@/utils/bizPrint'

const { t } = useI18n()

const loading = ref(false)
const submitting = ref(false)
const tableData = ref<PurchasePrice[]>([])
const total = ref(0)
const suppliers = ref<Supplier[]>([])
const products = ref<Product[]>([])
const dialogVisible = ref(false)
const editingId = ref<string | number | null>(null)
const scopeType = ref<'PRODUCT' | 'SUPPLIER'>('PRODUCT')
const formRef = ref<FormInstance>()

const searchForm = reactive<PurchasePriceQuery>({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  supplierId: '',
  status: ''
})

const form = reactive({
  supplierId: '' as string,
  productId: '' as string,
  listPrice: 0,
  maxPrice: 0,
  effectiveFrom: '',
  effectiveTo: '' as string,
  remark: ''
})

const rules = computed<FormRules>(() => ({
  supplierId: [{ required: true, message: t('purchasePrice.validation.supplier'), trigger: 'change' }],
  productId: [{ required: true, message: t('purchasePrice.validation.product'), trigger: 'change' }],
  listPrice: [{ required: true, message: t('purchasePrice.validation.listPrice'), trigger: 'blur' }],
  maxPrice: [{ required: true, message: t('purchasePrice.validation.maxPrice'), trigger: 'blur' }],
  effectiveFrom: [{ required: true, message: t('purchasePrice.validation.effectiveFrom'), trigger: 'change' }]
}))

watch(scopeType, (value) => {
  if (value === 'PRODUCT') {
    form.supplierId = ''
  }
})

const today = () => formatBusinessDate()

const loadOptions = async () => {
  const [supplierPage, productPage] = await Promise.all([
    getSuppliers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' }),
    getProducts({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
  ])
  suppliers.value = supplierPage.records || []
  products.value = productPage.records || []
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getPurchasePrices({
      ...searchForm,
      supplierId: searchForm.supplierId || undefined,
      status: searchForm.status || undefined,
      keyword: searchForm.keyword || undefined
    })
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch {
    // The shared request interceptor already surfaces the error.
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
  searchForm.supplierId = ''
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

const resetForm = () => {
  editingId.value = null
  scopeType.value = 'PRODUCT'
  form.supplierId = ''
  form.productId = ''
  form.listPrice = 0
  form.maxPrice = 0
  form.effectiveFrom = today()
  form.effectiveTo = ''
  form.remark = ''
}

const handleCreate = async () => {
  await loadOptions()
  resetForm()
  dialogVisible.value = true
}

const handleEdit = async (row: PurchasePrice) => {
  await loadOptions()
  editingId.value = row.id
  scopeType.value = row.supplierId ? 'SUPPLIER' : 'PRODUCT'
  form.supplierId = row.supplierId ? String(row.supplierId) : ''
  form.productId = String(row.productId)
  form.listPrice = Number(row.listPrice || 0)
  form.maxPrice = Number(row.maxPrice || 0)
  form.effectiveFrom = row.effectiveFrom
  form.effectiveTo = row.effectiveTo || ''
  form.remark = row.remark || ''
  dialogVisible.value = true
}

const handlePrint = async (row: PurchasePrice) => {
  try {
    const detail = await getPurchasePrice(row.id)
    printPurchasePrice(detail)
  } catch {
    ElMessage.error(t('purchasePrice.message.printLoadFailed'))
  }
}

const confirmSave = async () => {
  if (!formRef.value) return
  if (scopeType.value === 'SUPPLIER' && !form.supplierId) {
    ElMessage.warning(t('purchasePrice.validation.supplier'))
    return
  }
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (form.maxPrice < form.listPrice) {
      ElMessage.warning(t('purchasePrice.validation.maxBelowList'))
      return
    }
    submitting.value = true
    try {
      const payload = {
        supplierId: scopeType.value === 'SUPPLIER' ? form.supplierId : null,
        productId: form.productId,
        listPrice: form.listPrice,
        maxPrice: form.maxPrice,
        effectiveFrom: form.effectiveFrom,
        effectiveTo: form.effectiveTo || null,
        remark: form.remark || undefined
      }
      if (editingId.value) {
        await updatePurchasePrice(editingId.value, payload)
        ElMessage.success(t('purchasePrice.message.saved'))
      } else {
        await createPurchasePrice(payload)
        ElMessage.success(t('purchasePrice.message.created'))
      }
      dialogVisible.value = false
      loadData()
    } catch {
      // The shared request interceptor already surfaces the error.
    } finally {
      submitting.value = false
    }
  })
}

const handleEnable = async (row: PurchasePrice) => {
  try {
    await enablePurchasePrice(row.id)
    ElMessage.success(t('purchasePrice.message.enabled'))
    loadData()
  } catch {
    // The shared request interceptor already surfaces the error.
  }
}

const handleDisable = async (row: PurchasePrice) => {
  try {
    await ElMessageBox.confirm(t('purchasePrice.message.disableConfirm'), t('purchasePrice.message.prompt'), { type: 'warning' })
    await disablePurchasePrice(row.id)
    ElMessage.success(t('purchasePrice.message.disabled'))
    loadData()
  } catch {
    // Cancelled by the user or handled by the shared interceptor.
  }
}

onMounted(async () => {
  await loadOptions()
  await loadData()
})
</script>

<style scoped>
.purchase-price-page {
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
.sub {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}
.form-tip {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
}
</style>
