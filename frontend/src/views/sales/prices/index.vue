<template>
  <div class="sales-price-page">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="searchForm.keyword"
            placeholder="商品编码/名称"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="客户">
          <el-select
            v-model="searchForm.customerId"
            clearable
            filterable
            placeholder="全部（含通用价）"
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
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" clearable placeholder="全部" style="width: 140px">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
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
        <el-button v-permission="'sales:price:manage'" type="primary" :icon="Plus" @click="handleCreate">
          新建价目
        </el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column label="适用范围" min-width="160">
          <template #default="{ row }">
            <el-tag v-if="row.customerId" type="warning" size="small">客户专价</el-tag>
            <el-tag v-else type="info" size="small">商品通用</el-tag>
            <div class="sub">{{ row.customerId ? row.customerName || row.customerId : '全部客户' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="商品" min-width="180">
          <template #default="{ row }">
            <div>{{ row.productCode }} {{ row.productName }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="listPrice" label="标准价" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.listPrice) }}</template>
        </el-table-column>
        <el-table-column prop="minPrice" label="最低价" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.minPrice) }}</template>
        </el-table-column>
        <el-table-column label="生效区间" min-width="200">
          <template #default="{ row }">
            {{ row.effectiveFrom }} ~ {{ row.effectiveTo || '长期' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'sales:price:manage'" link type="primary" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'sales:price:manage'"
              link
              type="warning"
              @click="handleDisable(row)"
            >
              停用
            </el-button>
            <el-button
              v-else
              v-permission="'sales:price:manage'"
              link
              type="success"
              @click="handleEnable(row)"
            >
              启用
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑价目' : '新建价目'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="适用范围">
          <el-radio-group v-model="scopeType">
            <el-radio-button value="PRODUCT">商品通用</el-radio-button>
            <el-radio-button value="CUSTOMER">客户专价</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="scopeType === 'CUSTOMER'" label="客户" prop="customerId">
          <el-select v-model="form.customerId" filterable placeholder="选择客户" style="width: 100%">
            <el-option
              v-for="customer in customers"
              :key="customer.id"
              :label="customer.customerName || customer.name"
              :value="String(customer.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="商品" prop="productId">
          <el-select v-model="form.productId" filterable placeholder="选择商品" style="width: 100%">
            <el-option
              v-for="product in products"
              :key="product.id"
              :label="`${product.productCode || ''} ${product.productName || ''}`.trim()"
              :value="String(product.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标准价" prop="listPrice">
          <el-input-number v-model="form.listPrice" :min="0" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="最低价" prop="minPrice">
          <el-input-number v-model="form.minPrice" :min="0" :precision="2" :controls="false" style="width: 100%" />
          <div class="form-tip">销售订单单价不得低于最低价；无匹配价目时不拦截</div>
        </el-form-item>
        <el-form-item label="生效日期" prop="effectiveFrom">
          <el-date-picker v-model="form.effectiveFrom" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="失效日期">
          <el-date-picker
            v-model="form.effectiveTo"
            type="date"
            value-format="YYYY-MM-DD"
            clearable
            placeholder="留空=长期有效"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
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

const rules: FormRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  productId: [{ required: true, message: '请选择商品', trigger: 'change' }],
  listPrice: [{ required: true, message: '请输入标准价', trigger: 'blur' }],
  minPrice: [{ required: true, message: '请输入最低价', trigger: 'blur' }],
  effectiveFrom: [{ required: true, message: '请选择生效日期', trigger: 'change' }]
}

watch(scopeType, (value) => {
  if (value === 'PRODUCT') {
    form.customerId = ''
  }
})

const today = () => new Date().toISOString().slice(0, 10)

const formatMoney = (value: number) =>
  Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

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
    // interceptor
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
    ElMessage.warning('请选择客户')
    return
  }
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (form.minPrice > form.listPrice) {
      ElMessage.warning('最低价不能高于标准价')
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
        ElMessage.success('保存成功')
      } else {
        await createSalesPrice(payload)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch {
      // interceptor
    } finally {
      submitting.value = false
    }
  })
}

const handleEnable = async (row: SalesPrice) => {
  try {
    await enableSalesPrice(row.id)
    ElMessage.success('已启用')
    loadData()
  } catch {
    // interceptor
  }
}

const handleDisable = async (row: SalesPrice) => {
  try {
    await ElMessageBox.confirm(`确认停用该价目吗？`, '提示', { type: 'warning' })
    await disableSalesPrice(row.id)
    ElMessage.success('已停用')
    loadData()
  } catch {
    // cancel or interceptor
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
