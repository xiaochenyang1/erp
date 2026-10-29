<template>
  <div class="page">
    <el-card shadow="never">
      <el-form inline>
        <el-form-item :label="$t('salesQuote.keyword')">
          <el-input v-model="query.keyword" clearable style="width: 160px" @keyup.enter="handleSearch" />
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
          <el-button type="primary" @click="handleSearch">{{ $t('salesQuote.search') }}</el-button>
          <el-button v-permission="'sales:quote:manage'" type="success" @click="openCreate">{{ $t('salesQuote.create') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="quoteNo" :label="$t('salesQuote.quoteNo')" min-width="160" />
        <el-table-column prop="customerName" :label="$t('salesQuote.customer')" min-width="140" />
        <el-table-column prop="quoteDate" :label="$t('salesQuote.quoteDate')" width="130">
          <template #default="{ row }">{{ formatDate(row.quoteDate) }}</template>
        </el-table-column>
        <el-table-column prop="validUntil" :label="$t('salesQuote.validUntil')" width="130">
          <template #default="{ row }">{{ formatDate(row.validUntil) }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" :label="$t('salesQuote.amount')" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('salesQuote.status')" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('salesQuote.actions')" width="420" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openView(row)">{{ $t('salesQuote.detail') }}</el-button>
            <el-button link type="primary" @click="handlePrint(row)">{{ $t('salesQuote.print') }}</el-button>
            <el-button v-if="row.status === 'DRAFT'" v-permission="'sales:quote:manage'" link type="primary" @click="openEdit(row)">{{ $t('salesQuote.edit') }}</el-button>
            <el-button v-if="row.status === 'DRAFT'" v-permission="'sales:quote:manage'" link type="success" @click="confirmQuote(row)">{{ $t('salesQuote.confirm') }}</el-button>
            <el-button v-if="row.status === 'CONFIRMED'" v-permission="'sales:quote:manage'" link type="warning" @click="openConvert(row)">{{ $t('salesQuote.convert') }}</el-button>
            <el-button v-if="row.status === 'DRAFT' || row.status === 'CONFIRMED'" v-permission="'sales:quote:manage'" link type="danger" @click="cancelQuote(row)">{{ $t('salesQuote.cancelQuote') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="query.pageNo"
        :page-size="query.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
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
              <el-button link type="danger" @click="removeLine($index)">{{ $t('salesQuote.delete') }}</el-button>
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
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelSalesQuote,
  confirmSalesQuote,
  convertSalesQuoteToOrder,
  createSalesQuote,
  getSalesQuote,
  getSalesQuotes,
  updateSalesQuote
} from '@/api/sales'
import { getCustomers, getProducts, getWarehouses } from '@/api/masterdata'
import { useSalesQuoteForm } from '@/composables/useSalesQuoteForm'
import { useSalesQuoteList } from '@/composables/useSalesQuoteList'
import { useSalesQuotePresentation } from '@/composables/useSalesQuotePresentation'
import { printSalesQuote } from '@/utils/bizPrint'

const { t } = useI18n()

const {
  detailContent,
  formatDate,
  formatMoney,
  statusLabel,
  statusTagType
} = useSalesQuotePresentation(t)

const {
  addLine,
  customers,
  editingId,
  form,
  formVisible,
  loadOptions,
  openCreate,
  openEdit,
  products,
  removeLine,
  save,
  saving
} = useSalesQuoteForm(t, {
  getSalesQuote,
  createSalesQuote,
  updateSalesQuote,
  getCustomers,
  getProducts,
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message),
  onWarning: (message) => ElMessage.warning(message),
  onSaved: async () => { await loadData() }
})

const {
  cancelQuote,
  confirmQuote,
  convertVisible,
  convertWarehouseId,
  converting,
  doConvert,
  handlePageChange,
  handlePrint,
  handleSearch,
  handleSizeChange,
  loadData,
  loading,
  openConvert,
  openView,
  query,
  rows,
  total,
  warehouses
} = useSalesQuoteList(t, {
  getSalesQuotes,
  getSalesQuote,
  confirmSalesQuote,
  cancelSalesQuote,
  convertSalesQuoteToOrder,
  getWarehouses,
  getProducts,
  printSalesQuote,
  detailContent,
  confirm: (message, title, options) => ElMessageBox.confirm(message, title, options as any),
  alert: (message, title) => ElMessageBox.alert(message, title),
  products,
  loadProducts: loadOptions,
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message),
  onWarning: (message) => ElMessage.warning(message)
})

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
