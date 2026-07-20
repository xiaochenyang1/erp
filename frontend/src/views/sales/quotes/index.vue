<template>
  <div class="page">
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" clearable style="width: 160px" @keyup.enter="loadData" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable style="width: 140px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已确认" value="CONFIRMED" />
            <el-option label="已转单" value="CONVERTED" />
            <el-option label="已作废" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button v-permission="'sales:quote:manage'" type="success" @click="openCreate">新建报价</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="quoteNo" label="报价单号" min-width="160" />
        <el-table-column prop="customerName" label="客户" min-width="140" />
        <el-table-column prop="quoteDate" label="报价日期" width="120" />
        <el-table-column prop="validUntil" label="有效期至" width="120" />
        <el-table-column prop="totalAmount" label="金额" width="120" align="right" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openView(row)">详情</el-button>
            <el-button v-if="row.status === 'DRAFT'" v-permission="'sales:quote:manage'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" v-permission="'sales:quote:manage'" link type="success" @click="confirm(row)">确认</el-button>
            <el-button v-if="row.status === 'CONFIRMED'" v-permission="'sales:quote:manage'" link type="warning" @click="openConvert(row)">转订单</el-button>
            <el-button v-if="row.status === 'DRAFT' || row.status === 'CONFIRMED'" v-permission="'sales:quote:manage'" link type="danger" @click="cancel(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        background
        layout="total, prev, pager, next"
        :total="total"
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        @current-change="loadData"
      />
    </el-card>

    <el-dialog v-model="formVisible" :title="editingId ? '编辑报价' : '新建报价'" width="860px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="客户" required>
          <el-select v-model="form.customerId" filterable style="width: 100%">
            <el-option v-for="c in customers" :key="c.id" :label="c.customerName || c.name" :value="String(c.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="报价日期" required>
          <el-date-picker v-model="form.quoteDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="有效期至">
          <el-date-picker v-model="form.validUntil" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <div class="line-bar">
          <b>明细</b>
          <el-button link type="primary" @click="addLine">加行</el-button>
        </div>
        <el-table :data="form.lines" border size="small">
          <el-table-column label="商品" min-width="220">
            <template #default="{ row }">
              <el-select v-model="row.productId" filterable style="width: 100%">
                <el-option
                  v-for="p in products"
                  :key="p.id"
                  :label="`${p.productCode || ''} ${p.productName || ''}`.trim()"
                  :value="String(p.id)"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.qty" :min="0.0001" :precision="4" :controls="false" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="单价" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.price" :min="0" :precision="2" :controls="false" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="税率" width="110">
            <template #default="{ row }">
              <el-input-number v-model="row.taxRate" :min="0" :precision="4" :controls="false" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ $index }">
              <el-button link type="danger" @click="form.lines.splice($index, 1)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="convertVisible" title="转销售订单" width="420px">
      <el-form label-width="100px">
        <el-form-item label="发货仓库" required>
          <el-select v-model="convertWarehouseId" filterable style="width: 100%">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName || w.name" :value="String(w.id)" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="convertVisible = false">取消</el-button>
        <el-button type="primary" :loading="converting" @click="doConvert">确定转单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelSalesQuote,
  confirmSalesQuote,
  convertSalesQuoteToOrder,
  createSalesQuote,
  getSalesQuote,
  getSalesQuotes,
  updateSalesQuote,
  type SalesQuote
} from '@/api/sales'
import { getCustomers, getProducts, getWarehouses } from '@/api/masterdata'

const loading = ref(false)
const saving = ref(false)
const converting = ref(false)
const rows = ref<SalesQuote[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 20, keyword: '', status: '' })
const customers = ref<any[]>([])
const products = ref<any[]>([])
const warehouses = ref<any[]>([])
const formVisible = ref(false)
const editingId = ref<string | number | null>(null)
const form = reactive({
  customerId: '',
  quoteDate: '',
  validUntil: '',
  remark: '',
  lines: [] as Array<{ productId: string; qty: number; price: number; taxRate: number }>
})
const convertVisible = ref(false)
const convertQuoteId = ref<string | number>('')
const convertWarehouseId = ref('')

const today = () => new Date().toISOString().slice(0, 10)

const loadOptions = async () => {
  const [c, p, w] = await Promise.all([
    getCustomers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' }),
    getProducts({ pageNo: 1, pageSize: 200, status: 'ACTIVE' }),
    getWarehouses({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
  ])
  customers.value = c.records || []
  products.value = p.records || []
  warehouses.value = w.records || []
}

const loadData = async () => {
  loading.value = true
  try {
    const page = await getSalesQuotes(query)
    rows.value = page.records || []
    total.value = page.total || 0
  } catch {
    ElMessage.error('加载报价失败')
  } finally {
    loading.value = false
  }
}

const addLine = () => form.lines.push({ productId: '', qty: 1, price: 0, taxRate: 0.13 })

const openCreate = async () => {
  await loadOptions()
  editingId.value = null
  form.customerId = ''
  form.quoteDate = today()
  form.validUntil = ''
  form.remark = ''
  form.lines = [{ productId: '', qty: 1, price: 0, taxRate: 0.13 }]
  formVisible.value = true
}

const openEdit = async (row: SalesQuote) => {
  await loadOptions()
  const detail = await getSalesQuote(row.id)
  editingId.value = detail.id
  form.customerId = String(detail.customerId)
  form.quoteDate = detail.quoteDate
  form.validUntil = detail.validUntil || ''
  form.remark = detail.remark || ''
  form.lines = (detail.lines || []).map((l) => ({
    productId: String(l.productId),
    qty: Number(l.qty),
    price: Number(l.price),
    taxRate: Number(l.taxRate || 0)
  }))
  formVisible.value = true
}

const openView = async (row: SalesQuote) => {
  const detail = await getSalesQuote(row.id)
  ElMessageBox.alert(
    `单号 ${detail.quoteNo}\n客户 ${detail.customerName}\n金额 ${detail.totalAmount}\n状态 ${detail.status}\n明细 ${detail.lines?.length || 0} 行`,
    '报价详情'
  )
}

const save = async () => {
  if (!form.customerId || !form.quoteDate || !form.lines.some((l) => l.productId)) {
    ElMessage.warning('请完善客户、日期与明细')
    return
  }
  saving.value = true
  try {
    const payload = {
      customerId: form.customerId,
      quoteDate: form.quoteDate,
      validUntil: form.validUntil || undefined,
      remark: form.remark || undefined,
      lines: form.lines
        .filter((l) => l.productId)
        .map((l) => ({ productId: l.productId, qty: l.qty, price: l.price, taxRate: l.taxRate }))
    }
    if (editingId.value) await updateSalesQuote(editingId.value, payload)
    else await createSalesQuote(payload)
    ElMessage.success('保存成功')
    formVisible.value = false
    loadData()
  } catch {
    // interceptor
  } finally {
    saving.value = false
  }
}

const confirm = async (row: SalesQuote) => {
  await confirmSalesQuote(row.id)
  ElMessage.success('已确认')
  loadData()
}

const cancel = async (row: SalesQuote) => {
  await ElMessageBox.confirm(`作废报价 ${row.quoteNo}？`, '提示', { type: 'warning' })
  await cancelSalesQuote(row.id)
  ElMessage.success('已作废')
  loadData()
}

const openConvert = async (row: SalesQuote) => {
  await loadOptions()
  convertQuoteId.value = row.id
  convertWarehouseId.value = ''
  convertVisible.value = true
}

const doConvert = async () => {
  if (!convertWarehouseId.value) {
    ElMessage.warning('请选择仓库')
    return
  }
  converting.value = true
  try {
    const order = await convertSalesQuoteToOrder(convertQuoteId.value, convertWarehouseId.value)
    ElMessage.success(`已转销售订单 ${order.orderNo}`)
    convertVisible.value = false
    loadData()
  } catch {
    // interceptor
  } finally {
    converting.value = false
  }
}

onMounted(async () => {
  await loadOptions()
  await loadData()
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.pager { margin-top: 12px; justify-content: flex-end; }
.line-bar { display: flex; justify-content: space-between; margin: 8px 0; }
</style>
