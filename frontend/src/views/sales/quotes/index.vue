<template>
  <div class="page">
    <el-card shadow="never">
      <el-form inline>
        <el-form-item :label="$t('salesQuote.keyword')">
          <el-input v-model="query.keyword" clearable style="width: 160px" @keyup.enter="loadData" />
        </el-form-item>
        <el-form-item :label="$t('salesQuote.status')">
          <el-select v-model="query.status" clearable style="width: 140px">
            <el-option :label="$t('salesQuote.statusValue.draft')" value="DRAFT" />
            <el-option :label="$t('salesQuote.statusValue.confirmed')" value="CONFIRMED" />
            <el-option :label="$t('salesQuote.statusValue.converted')" value="CONVERTED" />
            <el-option :label="$t('salesQuote.statusValue.cancelled')" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">{{ $t('salesQuote.search') }}</el-button>
          <el-button v-permission="'sales:quote:manage'" type="success" @click="openCreate">{{ $t('salesQuote.create') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="quoteNo" :label="$t('salesQuote.quoteNo')" min-width="160" />
        <el-table-column prop="customerName" :label="$t('salesQuote.customer')" min-width="140" />
        <el-table-column prop="quoteDate" :label="$t('salesQuote.quoteDate')" width="130">
          <template #default="{ row }">{{ formatLocalizedDate(row.quoteDate) }}</template>
        </el-table-column>
        <el-table-column prop="validUntil" :label="$t('salesQuote.validUntil')" width="130">
          <template #default="{ row }">{{ formatLocalizedDate(row.validUntil) || '-' }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" :label="$t('salesQuote.amount')" width="140" align="right">
          <template #default="{ row }">{{ formatLocalizedCurrency(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('salesQuote.status')" width="110">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column :label="$t('salesQuote.actions')" width="360" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openView(row)">{{ $t('salesQuote.detail') }}</el-button>
            <el-button v-if="row.status === 'DRAFT'" v-permission="'sales:quote:manage'" link type="primary" @click="openEdit(row)">{{ $t('salesQuote.edit') }}</el-button>
            <el-button v-if="row.status === 'DRAFT'" v-permission="'sales:quote:manage'" link type="success" @click="confirm(row)">{{ $t('salesQuote.confirm') }}</el-button>
            <el-button v-if="row.status === 'CONFIRMED'" v-permission="'sales:quote:manage'" link type="warning" @click="openConvert(row)">{{ $t('salesQuote.convert') }}</el-button>
            <el-button v-if="row.status === 'DRAFT' || row.status === 'CONFIRMED'" v-permission="'sales:quote:manage'" link type="danger" @click="cancel(row)">{{ $t('salesQuote.cancelQuote') }}</el-button>
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

    <el-dialog v-model="formVisible" :title="editingId ? $t('salesQuote.editTitle') : $t('salesQuote.createTitle')" width="860px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item :label="$t('salesQuote.customer')" required>
          <el-select v-model="form.customerId" filterable style="width: 100%">
            <el-option v-for="c in customers" :key="c.id" :label="c.customerName || c.name" :value="String(c.id)" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('salesQuote.quoteDate')" required>
          <el-date-picker v-model="form.quoteDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('salesQuote.validUntil')">
          <el-date-picker v-model="form.validUntil" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('salesQuote.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <div class="line-bar">
          <b>{{ $t('salesQuote.lines') }}</b>
          <el-button link type="primary" @click="addLine">{{ $t('salesQuote.addLine') }}</el-button>
        </div>
        <el-table :data="form.lines" border size="small">
          <el-table-column :label="$t('salesQuote.product')" min-width="220">
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
          <el-table-column :label="$t('salesQuote.quantity')" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.qty" :min="0.0001" :precision="4" :controls="false" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('salesQuote.unitPrice')" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.price" :min="0" :precision="2" :controls="false" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('salesQuote.taxRate')" width="110">
            <template #default="{ row }">
              <el-input-number v-model="row.taxRate" :min="0" :precision="4" :controls="false" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('salesQuote.actions')" width="90">
            <template #default="{ $index }">
              <el-button link type="danger" @click="form.lines.splice($index, 1)">{{ $t('salesQuote.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">{{ $t('salesQuote.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ $t('salesQuote.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="convertVisible" :title="$t('salesQuote.convertTitle')" width="420px">
      <el-form label-width="100px">
        <el-form-item :label="$t('salesQuote.deliveryWarehouse')" required>
          <el-select v-model="convertWarehouseId" filterable style="width: 100%">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName || w.name" :value="String(w.id)" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="convertVisible = false">{{ $t('salesQuote.cancel') }}</el-button>
        <el-button type="primary" :loading="converting" @click="doConvert">{{ $t('salesQuote.confirmConvert') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
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
import { formatBusinessDate, formatLocalizedCurrency, formatLocalizedDate } from '@/utils/locale'

const { t } = useI18n()

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

const today = () => formatBusinessDate()

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: t('salesQuote.statusValue.draft'),
    CONFIRMED: t('salesQuote.statusValue.confirmed'),
    CONVERTED: t('salesQuote.statusValue.converted'),
    CANCELLED: t('salesQuote.statusValue.cancelled')
  }
  return map[status] || status
}

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
    ElMessage.error(t('salesQuote.message.loadFailed'))
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
    t('salesQuote.detailContent', {
      quoteNo: detail.quoteNo,
      customer: detail.customerName,
      amount: formatLocalizedCurrency(detail.totalAmount),
      status: statusLabel(detail.status),
      count: detail.lines?.length || 0
    }),
    t('salesQuote.detailTitle')
  )
}

const save = async () => {
  if (!form.customerId || !form.quoteDate || !form.lines.some((l) => l.productId)) {
    ElMessage.warning(t('salesQuote.message.completeForm'))
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
    ElMessage.success(t('salesQuote.message.saved'))
    formVisible.value = false
    loadData()
  } catch {
    // The shared request interceptor already surfaces the error.
  } finally {
    saving.value = false
  }
}

const confirm = async (row: SalesQuote) => {
  await confirmSalesQuote(row.id)
  ElMessage.success(t('salesQuote.message.confirmed'))
  loadData()
}

const cancel = async (row: SalesQuote) => {
  await ElMessageBox.confirm(
    t('salesQuote.message.cancelConfirm', { quoteNo: row.quoteNo }),
    t('salesQuote.message.prompt'),
    { type: 'warning' }
  )
  await cancelSalesQuote(row.id)
  ElMessage.success(t('salesQuote.message.cancelled'))
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
    ElMessage.warning(t('salesQuote.message.selectWarehouse'))
    return
  }
  converting.value = true
  try {
    const order = await convertSalesQuoteToOrder(convertQuoteId.value, convertWarehouseId.value)
    ElMessage.success(t('salesQuote.message.converted', { orderNo: order.orderNo }))
    convertVisible.value = false
    loadData()
  } catch {
    // The shared request interceptor already surfaces the error.
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
