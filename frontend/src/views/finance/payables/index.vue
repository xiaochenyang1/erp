<template>
  <div class="receivables-container">
    <el-tabs v-model="activeTab">
      <!-- 应收账款 -->
      <el-tab-pane label="应收账款" name="receivables">
        <el-card class="search-card" shadow="never">
          <el-form :model="receivableQuery" inline>
            <el-form-item label="应收单号">
              <el-input v-model="receivableQuery.receivableNo" placeholder="请输入应收单号" clearable style="width: 200px" />
            </el-form-item>
            <el-form-item label="客户">
              <el-select v-model="receivableQuery.customerId" placeholder="请选择客户" clearable filterable style="width: 200px">
                <el-option v-for="customer in customers" :key="customer.id" :label="customer.name" :value="customer.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="receivableQuery.status" placeholder="请选择状态" clearable style="width: 150px">
                <el-option label="未付" value="UNPAID" />
                <el-option label="部分付款" value="PARTIAL" />
                <el-option label="已付" value="PAID" />
                <el-option label="逾期" value="OVERDUE" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadReceivables"><el-icon><Search /></el-icon>查询</el-button>
              <el-button v-permission="'finance:receivable:view'" :icon="Download" @click="handleExportReceivables">导出</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="table-card" shadow="never">
          <el-table v-loading="receivableLoading" :data="receivableData" border stripe>
            <el-table-column prop="receivableNo" label="应收单号" width="180" />
            <el-table-column prop="customerName" label="客户" width="150" />
            <el-table-column prop="orderNo" label="关联订单" width="180" />
            <el-table-column prop="receivableAmount" label="应收金额" width="120" align="right">
              <template #default="{ row }">¥{{ row.receivableAmount?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="receivedAmount" label="已收金额" width="120" align="right">
              <template #default="{ row }">¥{{ row.receivedAmount?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="remainingAmount" label="未收金额" width="120" align="right">
              <template #default="{ row }">
                <span :class="{ 'text-danger': row.remainingAmount > 0 }">
                  ¥{{ row.remainingAmount?.toFixed(2) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="dueDate" label="到期日期" width="120" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.status === 'UNPAID'" type="danger">未付</el-tag>
                <el-tag v-else-if="row.status === 'PARTIAL'" type="warning">部分付款</el-tag>
                <el-tag v-else-if="row.status === 'PAID'" type="success">已付</el-tag>
                <el-tag v-else-if="row.status === 'OVERDUE'" type="danger">逾期</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="160" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="handleViewReceivable(row)">查看</el-button>
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

      <!-- 应付账款 -->
      <el-tab-pane label="应付账款" name="payables">
        <el-card class="search-card" shadow="never">
          <el-form :model="payableQuery" inline>
            <el-form-item label="应付单号">
              <el-input v-model="payableQuery.payableNo" placeholder="请输入应付单号" clearable style="width: 200px" />
            </el-form-item>
            <el-form-item label="供应商">
              <el-select v-model="payableQuery.supplierId" placeholder="请选择供应商" clearable filterable style="width: 200px">
                <el-option v-for="supplier in suppliers" :key="supplier.id" :label="supplier.name" :value="supplier.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="payableQuery.status" placeholder="请选择状态" clearable style="width: 150px">
                <el-option label="未付" value="UNPAID" />
                <el-option label="部分付款" value="PARTIAL" />
                <el-option label="已付" value="PAID" />
                <el-option label="逾期" value="OVERDUE" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadPayables"><el-icon><Search /></el-icon>查询</el-button>
              <el-button v-permission="'finance:payable:view'" :icon="Download" @click="handleExportPayables">导出</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="table-card" shadow="never">
          <el-table v-loading="payableLoading" :data="payableData" border stripe>
            <el-table-column prop="payableNo" label="应付单号" width="180" />
            <el-table-column prop="supplierName" label="供应商" width="150" />
            <el-table-column prop="orderNo" label="关联订单" width="180" />
            <el-table-column prop="payableAmount" label="应付金额" width="120" align="right">
              <template #default="{ row }">¥{{ row.payableAmount?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="paidAmount" label="已付金额" width="120" align="right">
              <template #default="{ row }">¥{{ row.paidAmount?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="remainingAmount" label="未付金额" width="120" align="right">
              <template #default="{ row }">
                <span :class="{ 'text-danger': row.remainingAmount > 0 }">
                  ¥{{ row.remainingAmount?.toFixed(2) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="dueDate" label="到期日期" width="120" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.status === 'UNPAID'" type="danger">未付</el-tag>
                <el-tag v-else-if="row.status === 'PARTIAL'" type="warning">部分付款</el-tag>
                <el-tag v-else-if="row.status === 'PAID'" type="success">已付</el-tag>
                <el-tag v-else-if="row.status === 'OVERDUE'" type="danger">逾期</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="160" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="handleViewPayable(row)">查看</el-button>
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

    <el-dialog v-model="receivableDetailVisible" title="应收账款详情" width="720px">
      <el-skeleton v-if="detailLoading" :rows="5" animated />
      <el-descriptions v-else-if="selectedReceivable" :column="2" border>
        <el-descriptions-item label="应收单号">{{ selectedReceivable.receivableNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ receivableStatusLabel(selectedReceivable.status) }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ selectedReceivable.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联订单">{{ selectedReceivable.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="应收金额">¥{{ formatMoney(selectedReceivable.receivableAmount) }}</el-descriptions-item>
        <el-descriptions-item label="已收金额">¥{{ formatMoney(selectedReceivable.receivedAmount) }}</el-descriptions-item>
        <el-descriptions-item label="未收金额">¥{{ formatMoney(selectedReceivable.remainingAmount) }}</el-descriptions-item>
        <el-descriptions-item label="到期日期">{{ selectedReceivable.dueDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ selectedReceivable.createdAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ selectedReceivable.updatedAt || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="payableDetailVisible" title="应付账款详情" width="720px">
      <el-skeleton v-if="detailLoading" :rows="5" animated />
      <el-descriptions v-else-if="selectedPayable" :column="2" border>
        <el-descriptions-item label="应付单号">{{ selectedPayable.payableNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ payableStatusLabel(selectedPayable.status) }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ selectedPayable.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联订单">{{ selectedPayable.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="应付金额">¥{{ formatMoney(selectedPayable.payableAmount) }}</el-descriptions-item>
        <el-descriptions-item label="已付金额">¥{{ formatMoney(selectedPayable.paidAmount) }}</el-descriptions-item>
        <el-descriptions-item label="未付金额">¥{{ formatMoney(selectedPayable.remainingAmount) }}</el-descriptions-item>
        <el-descriptions-item label="到期日期">{{ selectedPayable.dueDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ selectedPayable.createdAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ selectedPayable.updatedAt || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
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

const route = useRoute()
const readQueryString = (key: string) => {
  const value = route.query[key]
  return Array.isArray(value) ? value[0] || '' : typeof value === 'string' ? value : ''
}

const activeTab = ref(route.path.includes('/finance/payables') ? 'payables' : 'receivables')

// 应收账款
const receivableQuery = reactive<ReceivableQuery>({ pageNo: 1, pageSize: 20, receivableNo: '', customerId: undefined, status: '' })
receivableQuery.receivableNo = readQueryString('keyword')
const receivableLoading = ref(false)
const receivableData = ref<Receivable[]>([])
const receivableTotal = ref(0)
const receivableDetailVisible = ref(false)
const selectedReceivable = ref<Receivable>()

// 应付账款
const payableQuery = reactive<PayableQuery>({ pageNo: 1, pageSize: 20, payableNo: '', supplierId: undefined, status: '' })
payableQuery.payableNo = readQueryString('keyword')
const payableLoading = ref(false)
const payableData = ref<Payable[]>([])
const payableTotal = ref(0)
const payableDetailVisible = ref(false)
const selectedPayable = ref<Payable>()
const detailLoading = ref(false)

// 客户和供应商
const customers = ref<Customer[]>([])
const suppliers = ref<Supplier[]>([])

const loadReceivables = async () => {
  receivableLoading.value = true
  try {
    const response = await getReceivables(receivableQuery)
    receivableData.value = response.records
    receivableTotal.value = response.total
  } catch (error) {
    ElMessage.error('加载应收账款失败')
  } finally {
    receivableLoading.value = false
  }
}

const loadPayables = async () => {
  payableLoading.value = true
  try {
    const response = await getPayables(payableQuery)
    payableData.value = response.records
    payableTotal.value = response.total
  } catch (error) {
    ElMessage.error('加载应付账款失败')
  } finally {
    payableLoading.value = false
  }
}

const handleExportReceivables = async () => {
  try {
    const blob = await exportReceivables(receivableQuery)
    downloadBlob(blob, `应收账款_${Date.now()}.csv`)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

const handleExportPayables = async () => {
  try {
    const blob = await exportPayables(payableQuery)
    downloadBlob(blob, `应付账款_${Date.now()}.csv`)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

const handleViewReceivable = async (row: Receivable) => {
  receivableDetailVisible.value = true
  selectedReceivable.value = undefined
  detailLoading.value = true
  try {
    selectedReceivable.value = await getReceivable(row.id)
  } catch (error) {
    ElMessage.error('加载应收账款详情失败')
    receivableDetailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const handleViewPayable = async (row: Payable) => {
  payableDetailVisible.value = true
  selectedPayable.value = undefined
  detailLoading.value = true
  try {
    selectedPayable.value = await getPayable(row.id)
  } catch (error) {
    ElMessage.error('加载应付账款详情失败')
    payableDetailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const loadCustomers = async () => {
  try {
    const response = await getCustomers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    customers.value = response.records
  } catch (error) {
    console.error('加载客户列表失败', error)
  }
}

const loadSuppliers = async () => {
  try {
    const response = await getSuppliers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    suppliers.value = response.records
  } catch (error) {
    console.error('加载供应商列表失败', error)
  }
}

const formatMoney = (value?: number) => Number(value ?? 0).toFixed(2)
const receivableStatusLabel = (status: Receivable['status']) => {
  const map: Record<Receivable['status'], string> = {
    UNPAID: '未付',
    PARTIAL: '部分付款',
    PAID: '已付',
    OVERDUE: '逾期'
  }
  return map[status] || status
}
const payableStatusLabel = (status: Payable['status']) => {
  const map: Record<Payable['status'], string> = {
    UNPAID: '未付',
    PARTIAL: '部分付款',
    PAID: '已付',
    OVERDUE: '逾期'
  }
  return map[status] || status
}

watch(activeTab, (newTab) => {
  if (newTab === 'receivables') {
    loadReceivables()
  } else {
    loadPayables()
  }
})

onMounted(() => {
  if (activeTab.value === 'payables') {
    loadPayables()
  } else {
    loadReceivables()
  }
  loadCustomers()
  loadSuppliers()
})
</script>

<style scoped lang="scss">
.receivables-container {
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
