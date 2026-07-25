<template>
  <div class="sales-price-page">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item :label="$t('salesPrice.keyword')">
          <el-input
            v-model="searchForm.keyword"
            :placeholder="$t('salesPrice.keywordPlaceholder')"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="$t('salesPrice.customer')">
          <el-select
            v-model="searchForm.customerId"
            clearable
            filterable
            :placeholder="$t('salesPrice.allWithGeneral')"
            style="width: 200px"
          >
            <el-option
              v-for="customer in customers"
              :key="customer.id"
              :label="customer.customerName || customer.name"
              :value="String(customer.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('salesPrice.status')">
          <el-select v-model="searchForm.status" clearable :placeholder="$t('salesPrice.all')" style="width: 140px">
            <el-option :label="$t('salesPrice.active')" value="ACTIVE" />
            <el-option :label="$t('salesPrice.inactive')" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('salesPrice.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('salesPrice.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <el-button v-permission="'sales:price:manage'" type="primary" :icon="Plus" @click="handleCreate">
          {{ $t('salesPrice.create') }}
        </el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column :label="$t('salesPrice.scope')" min-width="160">
          <template #default="{ row }">
            <el-tag v-if="row.customerId" type="warning" size="small">{{ $t('salesPrice.customerSpecific') }}</el-tag>
            <el-tag v-else type="info" size="small">{{ $t('salesPrice.productGeneral') }}</el-tag>
            <div class="sub">{{ row.customerId ? row.customerName || row.customerId : $t('salesPrice.allCustomers') }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="$t('salesPrice.product')" min-width="180">
          <template #default="{ row }">
            <div>{{ row.productCode }} {{ row.productName }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="listPrice" :label="$t('salesPrice.listPrice')" width="130" align="right">
          <template #default="{ row }">{{ formatLocalizedCurrency(row.listPrice) }}</template>
        </el-table-column>
        <el-table-column prop="minPrice" :label="$t('salesPrice.minPrice')" width="130" align="right">
          <template #default="{ row }">{{ formatLocalizedCurrency(row.minPrice) }}</template>
        </el-table-column>
        <el-table-column :label="$t('salesPrice.effectivePeriod')" min-width="220">
          <template #default="{ row }">
            {{ formatLocalizedDate(row.effectiveFrom) }} ~ {{ row.effectiveTo ? formatLocalizedDate(row.effectiveTo) : $t('salesPrice.longTerm') }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('salesPrice.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? $t('salesPrice.active') : $t('salesPrice.inactive') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="$t('salesPrice.remark')" min-width="120" show-overflow-tooltip />
        <el-table-column :label="$t('salesPrice.actions')" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'sales:price:manage'" link type="primary" @click="handleEdit(row)">
              {{ $t('salesPrice.edit') }}
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'sales:price:manage'"
              link
              type="warning"
              @click="handleDisable(row)"
            >
              {{ $t('salesPrice.disable') }}
            </el-button>
            <el-button
              v-else
              v-permission="'sales:price:manage'"
              link
              type="success"
              @click="handleEnable(row)"
            >
              {{ $t('salesPrice.enable') }}
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

    <el-dialog v-model="dialogVisible" :title="editingId ? $t('salesPrice.editTitle') : $t('salesPrice.createTitle')" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item :label="$t('salesPrice.scope')">
          <el-radio-group v-model="scopeType">
            <el-radio-button value="PRODUCT">{{ $t('salesPrice.productGeneral') }}</el-radio-button>
            <el-radio-button value="CUSTOMER">{{ $t('salesPrice.customerSpecific') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="scopeType === 'CUSTOMER'" :label="$t('salesPrice.customer')" prop="customerId">
          <el-select v-model="form.customerId" filterable :placeholder="$t('salesPrice.selectCustomer')" style="width: 100%">
            <el-option
              v-for="customer in customers"
              :key="customer.id"
              :label="customer.customerName || customer.name"
              :value="String(customer.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('salesPrice.product')" prop="productId">
          <el-select v-model="form.productId" filterable :placeholder="$t('salesPrice.selectProduct')" style="width: 100%">
            <el-option
              v-for="product in products"
              :key="product.id"
              :label="`${product.productCode || ''} ${product.productName || ''}`.trim()"
              :value="String(product.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('salesPrice.listPrice')" prop="listPrice">
          <el-input-number v-model="form.listPrice" :min="0" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('salesPrice.minPrice')" prop="minPrice">
          <el-input-number v-model="form.minPrice" :min="0" :precision="2" :controls="false" style="width: 100%" />
          <div class="form-tip">{{ $t('salesPrice.minPriceTip') }}</div>
        </el-form-item>
        <el-form-item :label="$t('salesPrice.effectiveFrom')" prop="effectiveFrom">
          <el-date-picker v-model="form.effectiveFrom" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('salesPrice.effectiveTo')">
          <el-date-picker
            v-model="form.effectiveTo"
            type="date"
            value-format="YYYY-MM-DD"
            clearable
            :placeholder="$t('salesPrice.noExpiry')"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('salesPrice.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('salesPrice.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmSave">{{ $t('salesPrice.save') }}</el-button>
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
  createSalesPrice,
  disableSalesPrice,
  enableSalesPrice,
  getSalesPrices,
  updateSalesPrice,
  type SalesPrice,
  type SalesPriceQuery
} from '@/api/sales'
import { getCustomers, getProducts, type Customer, type Product } from '@/api/masterdata'
import { formatBusinessDate, formatLocalizedCurrency, formatLocalizedDate } from '@/utils/locale'

const { t } = useI18n()

const loading = ref(false)
const submitting = ref(false)
const tableData = ref<SalesPrice[]>([])
const total = ref(0)
const customers = ref<Customer[]>([])
const products = ref<Product[]>([])
const dialogVisible = ref(false)
const editingId = ref<string | number | null>(null)
const scopeType = ref<'PRODUCT' | 'CUSTOMER'>('PRODUCT')
const formRef = ref<FormInstance>()

const searchForm = reactive<SalesPriceQuery>({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  customerId: '',
  status: ''
})

const form = reactive({
  customerId: '' as string,
  productId: '' as string,
  listPrice: 0,
  minPrice: 0,
  effectiveFrom: '',
  effectiveTo: '' as string,
  remark: ''
})

const rules = computed<FormRules>(() => ({
  customerId: [{ required: true, message: t('salesPrice.validation.customer'), trigger: 'change' }],
  productId: [{ required: true, message: t('salesPrice.validation.product'), trigger: 'change' }],
  listPrice: [{ required: true, message: t('salesPrice.validation.listPrice'), trigger: 'blur' }],
  minPrice: [{ required: true, message: t('salesPrice.validation.minPrice'), trigger: 'blur' }],
  effectiveFrom: [{ required: true, message: t('salesPrice.validation.effectiveFrom'), trigger: 'change' }]
}))

watch(scopeType, (value) => {
  if (value === 'PRODUCT') {
    form.customerId = ''
  }
})

const today = () => formatBusinessDate()

const loadOptions = async () => {
  const [customerPage, productPage] = await Promise.all([
    getCustomers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' }),
    getProducts({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
  ])
  customers.value = customerPage.records || []
  products.value = productPage.records || []
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getSalesPrices({
      ...searchForm,
      customerId: searchForm.customerId || undefined,
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
  searchForm.customerId = ''
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
  form.customerId = ''
  form.productId = ''
  form.listPrice = 0
  form.minPrice = 0
  form.effectiveFrom = today()
  form.effectiveTo = ''
  form.remark = ''
}

const handleCreate = async () => {
  await loadOptions()
  resetForm()
  dialogVisible.value = true
}

const handleEdit = async (row: SalesPrice) => {
  await loadOptions()
  editingId.value = row.id
  scopeType.value = row.customerId ? 'CUSTOMER' : 'PRODUCT'
  form.customerId = row.customerId ? String(row.customerId) : ''
  form.productId = String(row.productId)
  form.listPrice = Number(row.listPrice || 0)
  form.minPrice = Number(row.minPrice || 0)
  form.effectiveFrom = row.effectiveFrom
  form.effectiveTo = row.effectiveTo || ''
  form.remark = row.remark || ''
  dialogVisible.value = true
}

const confirmSave = async () => {
  if (!formRef.value) return
  if (scopeType.value === 'CUSTOMER' && !form.customerId) {
    ElMessage.warning(t('salesPrice.validation.customer'))
    return
  }
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (form.minPrice > form.listPrice) {
      ElMessage.warning(t('salesPrice.validation.minAboveList'))
      return
    }
    submitting.value = true
    try {
      const payload = {
        customerId: scopeType.value === 'CUSTOMER' ? form.customerId : null,
        productId: form.productId,
        listPrice: form.listPrice,
        minPrice: form.minPrice,
        effectiveFrom: form.effectiveFrom,
        effectiveTo: form.effectiveTo || null,
        remark: form.remark || undefined
      }
      if (editingId.value) {
        await updateSalesPrice(editingId.value, payload)
        ElMessage.success(t('salesPrice.message.saved'))
      } else {
        await createSalesPrice(payload)
        ElMessage.success(t('salesPrice.message.created'))
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

const handleEnable = async (row: SalesPrice) => {
  try {
    await enableSalesPrice(row.id)
    ElMessage.success(t('salesPrice.message.enabled'))
    loadData()
  } catch {
    // The shared request interceptor already surfaces the error.
  }
}

const handleDisable = async (row: SalesPrice) => {
  try {
    await ElMessageBox.confirm(t('salesPrice.message.disableConfirm'), t('salesPrice.message.prompt'), { type: 'warning' })
    await disableSalesPrice(row.id)
    ElMessage.success(t('salesPrice.message.disabled'))
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
.sales-price-page {
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
