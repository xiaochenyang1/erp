<template>
  <div class="finance-accounts-container">
    <el-tabs v-model="activeTab">
      <el-tab-pane v-if="canViewReceivables" :label="t('financeAccount.tabs.receivables')" name="receivables">
        <el-card class="search-card" shadow="never">
          <el-form :model="receivableQuery" inline>
            <el-form-item :label="t('financeAccount.receivableNo')">
              <el-input v-model="receivableQuery.receivableNo" :placeholder="t('financeAccount.receivableNoPlaceholder')" clearable style="width: 200px" />
            </el-form-item>
            <el-form-item v-if="canLoadReceivableOptions" :label="t('financeAccount.customer')">
              <el-select v-model="receivableQuery.customerId" :placeholder="t('financeAccount.selectCustomer')" clearable filterable style="width: 200px">
                <el-option v-for="customer in customers" :key="customer.id" :label="customer.name" :value="customer.id" />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('financeAccount.statusLabel')">
              <el-select v-model="receivableQuery.status" :placeholder="t('financeAccount.selectStatus')" clearable style="width: 150px">
                <el-option :label="t('financeAccount.status.unsettled')" value="UNSETTLED" />
                <el-option :label="t('financeAccount.status.partiallySettled')" value="PARTIALLY_SETTLED" />
                <el-option :label="t('financeAccount.status.settled')" value="SETTLED" />
                <el-option :label="t('financeAccount.status.offset')" value="OFFSET" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadReceivables"><el-icon><Search /></el-icon>{{ t('financeAccount.search') }}</el-button>
              <el-button v-permission="'finance:receivable:view'" :icon="Download" @click="handleExportReceivables">{{ t('financeAccount.export') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="table-card" shadow="never">
          <el-table v-loading="receivableLoading" :data="receivableData" border stripe>
            <el-table-column prop="receivableNo" :label="t('financeAccount.receivableNo')" width="180" />
            <el-table-column prop="customerName" :label="t('financeAccount.customer')" width="150" />
            <el-table-column prop="sourceNo" :label="t('financeAccount.sourceNo')" width="180" />
            <el-table-column prop="receivableAmount" :label="t('financeAccount.receivableAmount')" width="120" align="right">
              <template #default="{ row }">{{ formatCurrency(row.receivableAmount) }}</template>
            </el-table-column>
            <el-table-column prop="receivedAmount" :label="t('financeAccount.receivedAmount')" width="120" align="right">
              <template #default="{ row }">{{ formatCurrency(row.receivedAmount) }}</template>
            </el-table-column>
            <el-table-column prop="remainingAmount" :label="t('financeAccount.unreceivedAmount')" width="120" align="right">
              <template #default="{ row }">
                <span :class="{ 'text-danger': row.remainingAmount > 0 }">
                  {{ formatCurrency(row.remainingAmount) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="bizDate" :label="t('financeAccount.bizDate')" width="120">
              <template #default="{ row }">{{ formatDate(row.bizDate) }}</template>
            </el-table-column>
            <el-table-column prop="dueDate" :label="t('financeAccount.dueDate')" width="120">
              <template #default="{ row }">{{ formatDate(row.dueDate) }}</template>
            </el-table-column>
            <el-table-column prop="status" :label="t('financeAccount.statusLabel')" width="100">
              <template #default="{ row }">
                <el-tag :type="accountStatusType(row.status)">{{ accountStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdTime" :label="t('financeAccount.createdTime')" width="180">
              <template #default="{ row }">{{ formatDateTime(row.createdTime) }}</template>
            </el-table-column>
            <el-table-column :label="t('financeAccount.actions')" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="handleViewReceivable(row)">{{ t('financeAccount.view') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="receivableQuery.pageNo"
            v-model:page-size="receivableQuery.pageSize"
            :total="receivableTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadReceivables"
            @current-change="loadReceivables"
          />
        </el-card>
      </el-tab-pane>

      <el-tab-pane v-if="canViewPayables" :label="t('financeAccount.tabs.payables')" name="payables">
        <el-card class="search-card" shadow="never">
          <el-form :model="payableQuery" inline>
            <el-form-item :label="t('financeAccount.payableNo')">
              <el-input v-model="payableQuery.payableNo" :placeholder="t('financeAccount.payableNoPlaceholder')" clearable style="width: 200px" />
            </el-form-item>
            <el-form-item v-if="canLoadPayableOptions" :label="t('financeAccount.supplier')">
              <el-select v-model="payableQuery.supplierId" :placeholder="t('financeAccount.selectSupplier')" clearable filterable style="width: 200px">
                <el-option v-for="supplier in suppliers" :key="supplier.id" :label="supplier.name" :value="supplier.id" />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('financeAccount.statusLabel')">
              <el-select v-model="payableQuery.status" :placeholder="t('financeAccount.selectStatus')" clearable style="width: 150px">
                <el-option :label="t('financeAccount.status.unsettled')" value="UNSETTLED" />
                <el-option :label="t('financeAccount.status.partiallySettled')" value="PARTIALLY_SETTLED" />
                <el-option :label="t('financeAccount.status.settled')" value="SETTLED" />
                <el-option :label="t('financeAccount.status.offset')" value="OFFSET" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadPayables"><el-icon><Search /></el-icon>{{ t('financeAccount.search') }}</el-button>
              <el-button v-permission="'finance:payable:view'" :icon="Download" @click="handleExportPayables">{{ t('financeAccount.export') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="table-card" shadow="never">
          <el-table v-loading="payableLoading" :data="payableData" border stripe>
            <el-table-column prop="payableNo" :label="t('financeAccount.payableNo')" width="180" />
            <el-table-column prop="supplierName" :label="t('financeAccount.supplier')" width="150" />
            <el-table-column prop="sourceNo" :label="t('financeAccount.sourceNo')" width="180" />
            <el-table-column prop="payableAmount" :label="t('financeAccount.payableAmount')" width="120" align="right">
              <template #default="{ row }">{{ formatCurrency(row.payableAmount) }}</template>
            </el-table-column>
            <el-table-column prop="paidAmount" :label="t('financeAccount.paidAmount')" width="120" align="right">
              <template #default="{ row }">{{ formatCurrency(row.paidAmount) }}</template>
            </el-table-column>
            <el-table-column prop="remainingAmount" :label="t('financeAccount.unpaidAmount')" width="120" align="right">
              <template #default="{ row }">
                <span :class="{ 'text-danger': row.remainingAmount > 0 }">
                  {{ formatCurrency(row.remainingAmount) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="bizDate" :label="t('financeAccount.bizDate')" width="120">
              <template #default="{ row }">{{ formatDate(row.bizDate) }}</template>
            </el-table-column>
            <el-table-column prop="dueDate" :label="t('financeAccount.dueDate')" width="120">
              <template #default="{ row }">{{ formatDate(row.dueDate) }}</template>
            </el-table-column>
            <el-table-column prop="status" :label="t('financeAccount.statusLabel')" width="100">
              <template #default="{ row }">
                <el-tag :type="accountStatusType(row.status)">{{ accountStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdTime" :label="t('financeAccount.createdTime')" width="180">
              <template #default="{ row }">{{ formatDateTime(row.createdTime) }}</template>
            </el-table-column>
            <el-table-column :label="t('financeAccount.actions')" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="handleViewPayable(row)">{{ t('financeAccount.view') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="payableQuery.pageNo"
            v-model:page-size="payableQuery.pageSize"
            :total="payableTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadPayables"
            @current-change="loadPayables"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="receivableDetailVisible" :title="t('financeAccount.dialog.receivable')" width="720px">
      <el-skeleton v-if="detailLoading" :rows="5" animated />
      <el-descriptions v-else-if="selectedReceivable" :column="2" border>
        <el-descriptions-item :label="t('financeAccount.receivableNo')">{{ selectedReceivable.receivableNo }}</el-descriptions-item>
        <el-descriptions-item :label="t('financeAccount.statusLabel')">{{ accountStatusLabel(selectedReceivable.status) }}</el-descriptions-item>
        <el-descriptions-item :label="t('financeAccount.customer')">{{ selectedReceivable.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('financeAccount.sourceNo')">{{ selectedReceivable.sourceNo || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('financeAccount.receivableAmount')">{{ formatCurrency(selectedReceivable.receivableAmount) }}</el-descriptions-item>
        <el-descriptions-item :label="t('financeAccount.receivedAmount')">{{ formatCurrency(selectedReceivable.receivedAmount) }}</el-descriptions-item>
        <el-descriptions-item :label="t('financeAccount.unreceivedAmount')">{{ formatCurrency(selectedReceivable.remainingAmount) }}</el-descriptions-item>
        <el-descriptions-item :label="t('financeAccount.bizDate')">{{ formatDate(selectedReceivable.bizDate) }}</el-descriptions-item>
        <el-descriptions-item :label="t('financeAccount.dueDate')">{{ formatDate(selectedReceivable.dueDate) }}</el-descriptions-item>
        <el-descriptions-item :label="t('financeAccount.createdTime')">{{ formatDateTime(selectedReceivable.createdTime) }}</el-descriptions-item>
        <el-descriptions-item :label="t('financeAccount.updatedTime')">{{ formatDateTime(selectedReceivable.updatedTime) }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="payableDetailVisible" :title="t('financeAccount.dialog.payable')" width="720px">
      <el-skeleton v-if="detailLoading" :rows="5" animated />
      <el-descriptions v-else-if="selectedPayable" :column="2" border>
        <el-descriptions-item :label="t('financeAccount.payableNo')">{{ selectedPayable.payableNo }}</el-descriptions-item>
        <el-descriptions-item :label="t('financeAccount.statusLabel')">{{ accountStatusLabel(selectedPayable.status) }}</el-descriptions-item>
        <el-descriptions-item :label="t('financeAccount.supplier')">{{ selectedPayable.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('financeAccount.sourceNo')">{{ selectedPayable.sourceNo || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('financeAccount.payableAmount')">{{ formatCurrency(selectedPayable.payableAmount) }}</el-descriptions-item>
        <el-descriptions-item :label="t('financeAccount.paidAmount')">{{ formatCurrency(selectedPayable.paidAmount) }}</el-descriptions-item>
        <el-descriptions-item :label="t('financeAccount.unpaidAmount')">{{ formatCurrency(selectedPayable.remainingAmount) }}</el-descriptions-item>
        <el-descriptions-item :label="t('financeAccount.bizDate')">{{ formatDate(selectedPayable.bizDate) }}</el-descriptions-item>
        <el-descriptions-item :label="t('financeAccount.dueDate')">{{ formatDate(selectedPayable.dueDate) }}</el-descriptions-item>
        <el-descriptions-item :label="t('financeAccount.createdTime')">{{ formatDateTime(selectedPayable.createdTime) }}</el-descriptions-item>
        <el-descriptions-item :label="t('financeAccount.updatedTime')">{{ formatDateTime(selectedPayable.updatedTime) }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import {
  exportPayables,
  exportReceivables,
  getPayable,
  getPayables,
  getReceivable,
  getReceivables,
  type Payable,
  type PayableQuery,
  type Receivable,
  type ReceivableQuery
} from '@/api/finance'
import { getCustomers, getSuppliers, type Customer, type Supplier } from '@/api/masterdata'
import { downloadBlob } from '@/utils/download'
import { formatLocalizedCurrency, formatLocalizedDate, formatLocalizedDateTime } from '@/utils/locale'
import { useUserStore } from '@/store/modules/user'
import {
  canLoadFinanceAccountOptions,
  canViewFinanceAccountTab,
  resolveFinanceAccountTab,
  type FinanceAccountTab
} from './account-access'

const props = defineProps<{
  defaultTab: FinanceAccountTab
}>()

const route = useRoute()
const userStore = useUserStore()
const { t } = useI18n()
const readQueryString = (key: string) => {
  const value = route.query[key]
  return Array.isArray(value) ? value[0] || '' : typeof value === 'string' ? value : ''
}

const canViewReceivables = computed(() => canViewFinanceAccountTab('receivables', userStore.hasPermission))
const canViewPayables = computed(() => canViewFinanceAccountTab('payables', userStore.hasPermission))
const canLoadReceivableOptions = computed(() => canLoadFinanceAccountOptions('receivables', userStore.hasPermission))
const canLoadPayableOptions = computed(() => canLoadFinanceAccountOptions('payables', userStore.hasPermission))
const preferredPath = props.defaultTab === 'payables' ? '/finance/payables' : '/finance/receivables'
const activeTab = ref<FinanceAccountTab | null>(resolveFinanceAccountTab(preferredPath, userStore.hasPermission))

// Receivables
const receivableQuery = reactive<ReceivableQuery>({ pageNo: 1, pageSize: 20, receivableNo: '', customerId: undefined, status: '' })
receivableQuery.receivableNo = readQueryString('keyword')
const receivableLoading = ref(false)
const receivableData = ref<Receivable[]>([])
const receivableTotal = ref(0)
const receivableDetailVisible = ref(false)
const selectedReceivable = ref<Receivable>()

// Payables
const payableQuery = reactive<PayableQuery>({ pageNo: 1, pageSize: 20, payableNo: '', supplierId: undefined, status: '' })
payableQuery.payableNo = readQueryString('keyword')
const payableLoading = ref(false)
const payableData = ref<Payable[]>([])
const payableTotal = ref(0)
const payableDetailVisible = ref(false)
const selectedPayable = ref<Payable>()
const detailLoading = ref(false)

// Customer and supplier options
const customers = ref<Customer[]>([])
const suppliers = ref<Supplier[]>([])

const loadReceivables = async () => {
  if (!canViewReceivables.value) return
  receivableLoading.value = true
  try {
    const response = await getReceivables(receivableQuery)
    receivableData.value = response.records
    receivableTotal.value = response.total
  } catch (error) {
    ElMessage.error(t('financeAccount.message.receivablesLoadFailed'))
  } finally {
    receivableLoading.value = false
  }
}

const loadPayables = async () => {
  if (!canViewPayables.value) return
  payableLoading.value = true
  try {
    const response = await getPayables(payableQuery)
    payableData.value = response.records
    payableTotal.value = response.total
  } catch (error) {
    ElMessage.error(t('financeAccount.message.payablesLoadFailed'))
  } finally {
    payableLoading.value = false
  }
}

const handleExportReceivables = async () => {
  if (!canViewReceivables.value) return
  try {
    const blob = await exportReceivables(receivableQuery)
    downloadBlob(blob, t('financeAccount.file.receivables', { timestamp: Date.now() }))
    ElMessage.success(t('financeAccount.message.exported'))
  } catch (error) {
    ElMessage.error(t('financeAccount.message.exportFailed'))
  }
}

const handleExportPayables = async () => {
  if (!canViewPayables.value) return
  try {
    const blob = await exportPayables(payableQuery)
    downloadBlob(blob, t('financeAccount.file.payables', { timestamp: Date.now() }))
    ElMessage.success(t('financeAccount.message.exported'))
  } catch (error) {
    ElMessage.error(t('financeAccount.message.exportFailed'))
  }
}

const handleViewReceivable = async (row: Receivable) => {
  if (!canViewReceivables.value) return
  receivableDetailVisible.value = true
  selectedReceivable.value = undefined
  detailLoading.value = true
  try {
    selectedReceivable.value = await getReceivable(row.id)
  } catch (error) {
    ElMessage.error(t('financeAccount.message.receivableDetailLoadFailed'))
    receivableDetailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const handleViewPayable = async (row: Payable) => {
  if (!canViewPayables.value) return
  payableDetailVisible.value = true
  selectedPayable.value = undefined
  detailLoading.value = true
  try {
    selectedPayable.value = await getPayable(row.id)
  } catch (error) {
    ElMessage.error(t('financeAccount.message.payableDetailLoadFailed'))
    payableDetailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const loadCustomers = async () => {
  if (!canLoadFinanceAccountOptions('receivables', userStore.hasPermission)) return
  try {
    const response = await getCustomers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    customers.value = response.records
  } catch (error) {
    console.error(t('financeAccount.message.customersLoadFailed'), error)
  }
}

const loadSuppliers = async () => {
  if (!canLoadFinanceAccountOptions('payables', userStore.hasPermission)) return
  try {
    const response = await getSuppliers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    suppliers.value = response.records
  } catch (error) {
    console.error(t('financeAccount.message.suppliersLoadFailed'), error)
  }
}

const formatCurrency = (value?: number) => formatLocalizedCurrency(Number(value ?? 0))
const formatDate = (value?: string) => formatLocalizedDate(value) || '-'
const formatDateTime = (value?: string) => formatLocalizedDateTime(value) || '-'
type AccountStatus = Receivable['status'] | Payable['status']
const accountStatusLabel = (status: AccountStatus) => {
  const keyMap: Record<AccountStatus, string> = {
    UNSETTLED: 'financeAccount.status.unsettled',
    PARTIALLY_SETTLED: 'financeAccount.status.partiallySettled',
    SETTLED: 'financeAccount.status.settled',
    OFFSET: 'financeAccount.status.offset'
  }
  return t(keyMap[status])
}
const accountStatusType = (status: AccountStatus) => {
  const typeMap: Record<AccountStatus, 'danger' | 'warning' | 'success' | 'info'> = {
    UNSETTLED: 'danger',
    PARTIALLY_SETTLED: 'warning',
    SETTLED: 'success',
    OFFSET: 'info'
  }
  return typeMap[status]
}

watch(activeTab, (newTab) => {
  if (newTab === 'receivables') {
    loadReceivables()
  } else if (newTab === 'payables') {
    loadPayables()
  }
})

onMounted(() => {
  if (activeTab.value === 'payables') {
    loadPayables()
  } else {
    loadReceivables()
  }
  if (canLoadFinanceAccountOptions('receivables', userStore.hasPermission)) loadCustomers()
  if (canLoadFinanceAccountOptions('payables', userStore.hasPermission)) loadSuppliers()
})
</script>

<style scoped lang="scss">
.finance-accounts-container {
  padding: 20px;

  .search-card, .table-card {
    margin-bottom: 20px;
  }

  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }

  .text-danger {
    color: #f56c6c;
    font-weight: bold;
  }
}
</style>
