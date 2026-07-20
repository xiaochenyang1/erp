<template>
  <div class="payments-container">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="收款管理" name="receipts">
        <el-card class="search-card" shadow="never">
          <el-form :model="receiptQuery" inline>
            <el-form-item label="客户">
              <el-select v-model="receiptQuery.customerId" placeholder="请选择客户" clearable filterable style="width: 200px">
                <el-option v-for="customer in customers" :key="customer.id" :label="customer.name" :value="customer.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="receiptQuery.status" placeholder="请选择状态" clearable style="width: 150px">
                <el-option label="草稿" value="DRAFT" />
                <el-option label="已过账" value="POSTED" />
                <el-option label="已取消" value="CANCELLED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadReceipts"><el-icon><Search /></el-icon>查询</el-button>
              <el-button v-permission="'finance:receipt:create'" type="primary" @click="handleCreateReceipt"><el-icon><Plus /></el-icon>新增收款</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="table-card" shadow="never">
          <el-table v-loading="receiptLoading" :data="receiptData" border stripe>
            <el-table-column prop="receiptNo" label="收款单号" width="180" />
            <el-table-column prop="customerId" label="客户" width="150">
              <template #default="{ row }">{{ customerName(row.customerId) }}</template>
            </el-table-column>
            <el-table-column prop="receiptDate" label="收款日期" width="120" />
            <el-table-column prop="receiptAmount" label="收款金额" width="120" align="right">
              <template #default="{ row }">¥{{ formatMoney(row.receiptAmount) }}</template>
            </el-table-column>
            <el-table-column prop="allocatedAmount" label="已核销" width="120" align="right">
              <template #default="{ row }">¥{{ formatMoney(row.allocatedAmount) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.status === 'DRAFT'" type="info">草稿</el-tag>
                <el-tag v-else-if="row.status === 'POSTED' || row.status === 'COMPLETED'" type="success">已过账</el-tag>
                <el-tag v-else type="danger">已取消</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="viewReceipt(row)">查看</el-button>
                <el-button v-if="row.status === 'DRAFT'" v-permission="'finance:receipt:cancel'" link type="danger" @click="cancelReceipt(row)">取消</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="receiptQuery.pageNo"
            v-model:page-size="receiptQuery.pageSize"
            :total="receiptTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @size-change="loadReceipts"
            @current-change="loadReceipts"
          />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="付款管理" name="payments">
        <el-card class="search-card" shadow="never">
          <el-form :model="paymentQuery" inline>
            <el-form-item label="供应商">
              <el-select v-model="paymentQuery.supplierId" placeholder="请选择供应商" clearable filterable style="width: 200px">
                <el-option v-for="supplier in suppliers" :key="supplier.id" :label="supplier.name" :value="supplier.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="paymentQuery.status" placeholder="请选择状态" clearable style="width: 150px">
                <el-option label="草稿" value="DRAFT" />
                <el-option label="已过账" value="POSTED" />
                <el-option label="已取消" value="CANCELLED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadPayments"><el-icon><Search /></el-icon>查询</el-button>
              <el-button v-permission="'finance:payment:create'" type="primary" @click="handleCreatePayment"><el-icon><Plus /></el-icon>新增付款</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="table-card" shadow="never">
          <el-table v-loading="paymentLoading" :data="paymentData" border stripe>
            <el-table-column prop="paymentNo" label="付款单号" width="180" />
            <el-table-column prop="supplierId" label="供应商" width="150">
              <template #default="{ row }">{{ supplierName(row.supplierId) }}</template>
            </el-table-column>
            <el-table-column prop="paymentDate" label="付款日期" width="120" />
            <el-table-column prop="paymentAmount" label="付款金额" width="120" align="right">
              <template #default="{ row }">¥{{ formatMoney(row.paymentAmount) }}</template>
            </el-table-column>
            <el-table-column prop="allocatedAmount" label="已核销" width="120" align="right">
              <template #default="{ row }">¥{{ formatMoney(row.allocatedAmount) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.status === 'DRAFT'" type="info">草稿</el-tag>
                <el-tag v-else-if="row.status === 'POSTED' || row.status === 'COMPLETED'" type="success">已过账</el-tag>
                <el-tag v-else type="danger">已取消</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="viewPayment(row)">查看</el-button>
                <el-button v-if="row.status === 'DRAFT'" v-permission="'finance:payment:cancel'" link type="danger" @click="cancelPayment(row)">取消</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="paymentQuery.pageNo"
            v-model:page-size="paymentQuery.pageSize"
            :total="paymentTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @size-change="loadPayments"
            @current-change="loadPayments"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="receiptDialogVisible" title="新增收款" width="820px" @close="resetReceiptForm">
      <el-form ref="receiptFormRef" :model="receiptForm" :rules="receiptRules" label-width="110px">
        <el-form-item label="客户" prop="customerId">
          <el-select v-model="receiptForm.customerId" placeholder="请选择客户" filterable style="width: 100%" @change="loadOpenReceivables">
            <el-option v-for="customer in customers" :key="customer.id" :label="customer.name" :value="customer.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="待核销应收" prop="receivableIds">
          <el-select
            v-model="receiptForm.receivableIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="可多选未结应收"
            style="width: 100%"
            @change="onReceivableSelectionChange"
          >
            <el-option
              v-for="item in openReceivables"
              :key="item.id"
              :label="`${item.receivableNo} / 剩余 ${formatMoney(item.remainingAmount)}`"
              :value="String(item.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="收款日期" prop="receiptDate">
          <el-date-picker v-model="receiptForm.receiptDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="收款金额" prop="amount">
          <el-input-number v-model="receiptForm.amount" :min="0.01" :precision="2" :controls="false" style="width: 100%" @change="rebalanceReceiptAllocations" />
        </el-form-item>
        <el-form-item label="核销明细">
          <div style="width: 100%">
            <div class="alloc-toolbar">
              <el-button size="small" @click="autoAllocateReceipt">按剩余自动分摊</el-button>
              <span class="alloc-tip">
                已分摊 {{ formatMoney(receiptAllocatedTotal) }} /
                收款 {{ formatMoney(receiptForm.amount) }}
                <template v-if="receiptUnallocated > 0.0001"> · 未核销 {{ formatMoney(receiptUnallocated) }}</template>
                <template v-else-if="receiptUnallocated < -0.0001"> · 超出 {{ formatMoney(-receiptUnallocated) }}</template>
              </span>
            </div>
            <el-table :data="receiptAllocationRows" border size="small" max-height="260">
              <el-table-column prop="receivableNo" label="应收单号" min-width="150" />
              <el-table-column prop="remainingAmount" label="剩余" width="110" align="right">
                <template #default="{ row }">{{ formatMoney(row.remainingAmount) }}</template>
              </el-table-column>
              <el-table-column label="本次核销" width="150">
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.allocatedAmount"
                    :min="0"
                    :max="Number(row.remainingAmount)"
                    :precision="2"
                    :controls="false"
                    style="width: 100%"
                  />
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="receiptForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="receiptDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="receiptSubmitting" @click="submitReceipt">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="paymentDialogVisible" title="新增付款" width="820px" @close="resetPaymentForm">
      <el-form ref="paymentFormRef" :model="paymentForm" :rules="paymentRules" label-width="110px">
        <el-form-item label="供应商" prop="supplierId">
          <el-select v-model="paymentForm.supplierId" placeholder="请选择供应商" filterable style="width: 100%" @change="loadOpenPayables">
            <el-option v-for="supplier in suppliers" :key="supplier.id" :label="supplier.name" :value="supplier.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="待核销应付" prop="payableIds">
          <el-select
            v-model="paymentForm.payableIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="可多选未结应付"
            style="width: 100%"
            @change="onPayableSelectionChange"
          >
            <el-option
              v-for="item in openPayables"
              :key="item.id"
              :label="`${item.payableNo} / 剩余 ${formatMoney(item.remainingAmount)}`"
              :value="String(item.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="付款日期" prop="paymentDate">
          <el-date-picker v-model="paymentForm.paymentDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="付款金额" prop="amount">
          <el-input-number v-model="paymentForm.amount" :min="0.01" :precision="2" :controls="false" style="width: 100%" @change="rebalancePaymentAllocations" />
        </el-form-item>
        <el-form-item label="核销明细">
          <div style="width: 100%">
            <div class="alloc-toolbar">
              <el-button size="small" @click="autoAllocatePayment">按剩余自动分摊</el-button>
              <span class="alloc-tip">
                已分摊 {{ formatMoney(paymentAllocatedTotal) }} /
                付款 {{ formatMoney(paymentForm.amount) }}
                <template v-if="paymentUnallocated > 0.0001"> · 未核销 {{ formatMoney(paymentUnallocated) }}</template>
                <template v-else-if="paymentUnallocated < -0.0001"> · 超出 {{ formatMoney(-paymentUnallocated) }}</template>
              </span>
            </div>
            <el-table :data="paymentAllocationRows" border size="small" max-height="260">
              <el-table-column prop="payableNo" label="应付单号" min-width="150" />
              <el-table-column prop="remainingAmount" label="剩余" width="110" align="right">
                <template #default="{ row }">{{ formatMoney(row.remainingAmount) }}</template>
              </el-table-column>
              <el-table-column label="本次核销" width="150">
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.allocatedAmount"
                    :min="0"
                    :max="Number(row.remainingAmount)"
                    :precision="2"
                    :controls="false"
                    style="width: 100%"
                  />
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="paymentForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="paymentDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="paymentSubmitting" @click="submitPayment">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" :title="detailTitle" width="760px">
      <el-skeleton v-if="detailLoading" :rows="5" animated />
      <template v-else>
      <el-descriptions v-if="detailItems.length" :column="2" border>
        <el-descriptions-item v-for="item in detailItems" :key="item.label" :label="item.label">
          {{ item.value }}
        </el-descriptions-item>
      </el-descriptions>

      <el-table v-if="selectedReceipt" :data="receiptAllocations" border stripe class="detail-table">
        <el-table-column prop="receivableNo" label="应收单号" min-width="180">
          <template #default="{ row }">{{ row.receivableNo || row.receivableId }}</template>
        </el-table-column>
        <el-table-column prop="receivableId" label="应收ID" min-width="180" />
        <el-table-column prop="allocatedAmount" label="核销金额" width="140" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.allocatedAmount) }}</template>
        </el-table-column>
      </el-table>

      <el-table v-if="selectedPayment" :data="paymentAllocations" border stripe class="detail-table">
        <el-table-column prop="payableNo" label="应付单号" min-width="180">
          <template #default="{ row }">{{ row.payableNo || row.payableId }}</template>
        </el-table-column>
        <el-table-column prop="payableId" label="应付ID" min-width="180" />
        <el-table-column prop="allocatedAmount" label="核销金额" width="140" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.allocatedAmount) }}</template>
        </el-table-column>
      </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import {
  cancelPayment as apiCancelPayment,
  cancelReceipt as apiCancelReceipt,
  createPayment,
  createReceipt,
  getPayment,
  getPayables,
  getPayments,
  getReceipt,
  getReceivables,
  getReceipts,
  type Payable,
  type Payment,
  type PaymentAllocation,
  type PaymentQuery,
  type Receipt,
  type ReceiptAllocation,
  type ReceiptQuery,
  type Receivable
} from '@/api/finance'
import { getCustomers, getSuppliers, type Customer, type Supplier } from '@/api/masterdata'

const activeTab = ref('receipts')

const receiptQuery = reactive<ReceiptQuery>({ pageNo: 1, pageSize: 10, customerId: undefined, status: '' })
const paymentQuery = reactive<PaymentQuery>({ pageNo: 1, pageSize: 10, supplierId: undefined, status: '' })

const receiptLoading = ref(false)
const paymentLoading = ref(false)
const receiptData = ref<Receipt[]>([])
const paymentData = ref<Payment[]>([])
const receiptTotal = ref(0)
const paymentTotal = ref(0)

const customers = ref<Customer[]>([])
const suppliers = ref<Supplier[]>([])
const openReceivables = ref<Receivable[]>([])
const openPayables = ref<Payable[]>([])

const receiptDialogVisible = ref(false)
const paymentDialogVisible = ref(false)
const receiptSubmitting = ref(false)
const paymentSubmitting = ref(false)
const receiptFormRef = ref<FormInstance>()
const paymentFormRef = ref<FormInstance>()

const receiptForm = reactive({
  customerId: '' as string | number,
  receivableIds: [] as string[],
  receiptDate: '',
  amount: 0,
  remark: ''
})

const paymentForm = reactive({
  supplierId: '' as string | number,
  payableIds: [] as string[],
  paymentDate: '',
  amount: 0,
  remark: ''
})

type AllocRow = {
  id: string
  receivableNo?: string
  payableNo?: string
  remainingAmount: number
  allocatedAmount: number
}

const receiptAllocationRows = ref<AllocRow[]>([])
const paymentAllocationRows = ref<AllocRow[]>([])

const receiptRules: FormRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  receivableIds: [{ type: 'array', required: true, message: '请至少选择一笔应收账款', trigger: 'change' }],
  receiptDate: [{ required: true, message: '请选择收款日期', trigger: 'change' }],
  amount: [{ required: true, message: '请输入收款金额', trigger: 'blur' }]
}

const paymentRules: FormRules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  payableIds: [{ type: 'array', required: true, message: '请至少选择一笔应付账款', trigger: 'change' }],
  paymentDate: [{ required: true, message: '请选择付款日期', trigger: 'change' }],
  amount: [{ required: true, message: '请输入付款金额', trigger: 'blur' }]
}

const receiptAllocatedTotal = computed(() =>
  receiptAllocationRows.value.reduce((sum, row) => sum + Number(row.allocatedAmount || 0), 0)
)
const paymentAllocatedTotal = computed(() =>
  paymentAllocationRows.value.reduce((sum, row) => sum + Number(row.allocatedAmount || 0), 0)
)
const receiptUnallocated = computed(() => Number(receiptForm.amount || 0) - receiptAllocatedTotal.value)
const paymentUnallocated = computed(() => Number(paymentForm.amount || 0) - paymentAllocatedTotal.value)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailTitle = ref('')
const detailItems = ref<Array<{ label: string; value: string }>>([])
const selectedReceipt = ref<Receipt>()
const selectedPayment = ref<Payment>()
const receiptAllocations = ref<ReceiptAllocation[]>([])
const paymentAllocations = ref<PaymentAllocation[]>([])

const customerMap = computed(() => new Map(customers.value.map((item) => [String(item.id), item.name])))
const supplierMap = computed(() => new Map(suppliers.value.map((item) => [String(item.id), item.name])))

const loadReceipts = async () => {
  receiptLoading.value = true
  try {
    const response = await getReceipts(receiptQuery)
    receiptData.value = response.records
    receiptTotal.value = response.total
  } catch (error) {
    ElMessage.error('加载收款数据失败')
  } finally {
    receiptLoading.value = false
  }
}

const loadPayments = async () => {
  paymentLoading.value = true
  try {
    const response = await getPayments(paymentQuery)
    paymentData.value = response.records
    paymentTotal.value = response.total
  } catch (error) {
    ElMessage.error('加载付款数据失败')
  } finally {
    paymentLoading.value = false
  }
}

const handleCreateReceipt = () => {
  resetReceiptForm()
  receiptForm.receiptDate = today()
  receiptDialogVisible.value = true
}

const handleCreatePayment = () => {
  resetPaymentForm()
  paymentForm.paymentDate = today()
  paymentDialogVisible.value = true
}

const loadOpenReceivables = async () => {
  receiptForm.receivableIds = []
  receiptAllocationRows.value = []
  receiptForm.amount = 0
  if (!receiptForm.customerId) {
    openReceivables.value = []
    return
  }
  const page = await getReceivables({ pageNo: 1, pageSize: 1000, customerId: receiptForm.customerId })
  openReceivables.value = page.records.filter((item) => Number(item.remainingAmount) > 0)
}

const loadOpenPayables = async () => {
  paymentForm.payableIds = []
  paymentAllocationRows.value = []
  paymentForm.amount = 0
  if (!paymentForm.supplierId) {
    openPayables.value = []
    return
  }
  const page = await getPayables({ pageNo: 1, pageSize: 1000, supplierId: paymentForm.supplierId })
  openPayables.value = page.records.filter((item) => Number(item.remainingAmount) > 0)
}

const onReceivableSelectionChange = () => {
  const selected = new Set(receiptForm.receivableIds.map(String))
  receiptAllocationRows.value = openReceivables.value
    .filter((item) => selected.has(String(item.id)))
    .map((item) => ({
      id: String(item.id),
      receivableNo: item.receivableNo,
      remainingAmount: Number(item.remainingAmount || 0),
      allocatedAmount: 0
    }))
  autoAllocateReceipt()
}

const onPayableSelectionChange = () => {
  const selected = new Set(paymentForm.payableIds.map(String))
  paymentAllocationRows.value = openPayables.value
    .filter((item) => selected.has(String(item.id)))
    .map((item) => ({
      id: String(item.id),
      payableNo: item.payableNo,
      remainingAmount: Number(item.remainingAmount || 0),
      allocatedAmount: 0
    }))
  autoAllocatePayment()
}

const autoAllocate = (rows: AllocRow[], totalAmount: number) => {
  let left = Number(totalAmount || 0)
  for (const row of rows) {
    const take = Math.min(left, Number(row.remainingAmount || 0))
    row.allocatedAmount = Number(take.toFixed(2))
    left = Number((left - take).toFixed(2))
  }
}

const autoAllocateReceipt = () => {
  if (!receiptForm.amount || receiptForm.amount <= 0) {
    const sum = receiptAllocationRows.value.reduce((s, r) => s + Number(r.remainingAmount || 0), 0)
    receiptForm.amount = Number(sum.toFixed(2))
  }
  autoAllocate(receiptAllocationRows.value, receiptForm.amount)
}

const autoAllocatePayment = () => {
  if (!paymentForm.amount || paymentForm.amount <= 0) {
    const sum = paymentAllocationRows.value.reduce((s, r) => s + Number(r.remainingAmount || 0), 0)
    paymentForm.amount = Number(sum.toFixed(2))
  }
  autoAllocate(paymentAllocationRows.value, paymentForm.amount)
}

const rebalanceReceiptAllocations = () => autoAllocate(receiptAllocationRows.value, receiptForm.amount)
const rebalancePaymentAllocations = () => autoAllocate(paymentAllocationRows.value, paymentForm.amount)

const submitReceipt = async () => {
  if (!receiptFormRef.value) return
  await receiptFormRef.value.validate(async (valid) => {
    if (!valid) return
    const allocations = receiptAllocationRows.value
      .filter((row) => Number(row.allocatedAmount) > 0)
      .map((row) => ({ receivableId: row.id, allocatedAmount: Number(row.allocatedAmount) }))
    if (!allocations.length) {
      ElMessage.warning('请至少填写一笔核销金额')
      return
    }
    const allocated = allocations.reduce((s, a) => s + a.allocatedAmount, 0)
    if (allocated - Number(receiptForm.amount) > 0.0001) {
      ElMessage.warning('核销合计不能超过收款金额')
      return
    }
    receiptSubmitting.value = true
    try {
      await createReceipt({
        customerId: receiptForm.customerId,
        receiptDate: receiptForm.receiptDate,
        receiptAmount: receiptForm.amount,
        receiptMethod: 'BANK_TRANSFER',
        allocations,
        remark: receiptForm.remark
      })
      ElMessage.success('收款创建成功')
      receiptDialogVisible.value = false
      loadReceipts()
    } catch (error) {
      ElMessage.error('创建收款失败')
    } finally {
      receiptSubmitting.value = false
    }
  })
}

const submitPayment = async () => {
  if (!paymentFormRef.value) return
  await paymentFormRef.value.validate(async (valid) => {
    if (!valid) return
    const allocations = paymentAllocationRows.value
      .filter((row) => Number(row.allocatedAmount) > 0)
      .map((row) => ({ payableId: row.id, allocatedAmount: Number(row.allocatedAmount) }))
    if (!allocations.length) {
      ElMessage.warning('请至少填写一笔核销金额')
      return
    }
    const allocated = allocations.reduce((s, a) => s + a.allocatedAmount, 0)
    if (allocated - Number(paymentForm.amount) > 0.0001) {
      ElMessage.warning('核销合计不能超过付款金额')
      return
    }
    paymentSubmitting.value = true
    try {
      await createPayment({
        supplierId: paymentForm.supplierId,
        paymentDate: paymentForm.paymentDate,
        paymentAmount: paymentForm.amount,
        paymentMethod: 'BANK_TRANSFER',
        allocations,
        remark: paymentForm.remark
      })
      ElMessage.success('付款创建成功')
      paymentDialogVisible.value = false
      loadPayments()
    } catch (error) {
      ElMessage.error('创建付款失败')
    } finally {
      paymentSubmitting.value = false
    }
  })
}

const viewReceipt = async (row: Receipt) => {
  detailTitle.value = `收款单 ${row.receiptNo}`
  detailVisible.value = true
  detailLoading.value = true
  selectedReceipt.value = undefined
  selectedPayment.value = undefined
  receiptAllocations.value = []
  paymentAllocations.value = []
  try {
    const detail = await getReceipt(row.id)
    selectedReceipt.value = detail
    receiptAllocations.value = detail.allocations || []
  detailItems.value = [
      { label: '客户', value: detail.customerName || customerName(detail.customerId) },
      { label: '收款日期', value: detail.receiptDate },
      { label: '收款金额', value: formatMoney(detail.receiptAmount) },
      { label: '已核销', value: formatMoney(detail.allocatedAmount) },
      { label: '状态', value: detail.status },
      { label: '备注', value: detail.remark || '-' }
  ]
  } catch (error) {
    ElMessage.error('加载收款详情失败')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const viewPayment = async (row: Payment) => {
  detailTitle.value = `付款单 ${row.paymentNo}`
  detailVisible.value = true
  detailLoading.value = true
  selectedReceipt.value = undefined
  selectedPayment.value = undefined
  receiptAllocations.value = []
  paymentAllocations.value = []
  try {
    const detail = await getPayment(row.id)
    selectedPayment.value = detail
    paymentAllocations.value = detail.allocations || []
  detailItems.value = [
      { label: '供应商', value: detail.supplierName || supplierName(detail.supplierId) },
      { label: '付款日期', value: detail.paymentDate },
      { label: '付款金额', value: formatMoney(detail.paymentAmount) },
      { label: '已核销', value: formatMoney(detail.allocatedAmount) },
      { label: '状态', value: detail.status },
      { label: '备注', value: detail.remark || '-' }
  ]
  } catch (error) {
    ElMessage.error('加载付款详情失败')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const cancelReceipt = async (row: Receipt) => {
  try {
    await ElMessageBox.confirm(`确定取消收款单"${row.receiptNo}"吗？`, '提示', { type: 'warning' })
    await apiCancelReceipt(row.id)
    ElMessage.success('取消成功')
    loadReceipts()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('取消失败')
  }
}

const cancelPayment = async (row: Payment) => {
  try {
    await ElMessageBox.confirm(`确定取消付款单"${row.paymentNo}"吗？`, '提示', { type: 'warning' })
    await apiCancelPayment(row.id)
    ElMessage.success('取消成功')
    loadPayments()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('取消失败')
  }
}

const loadCustomers = async () => {
  const response = await getCustomers({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
  customers.value = response.records
}

const loadSuppliers = async () => {
  const response = await getSuppliers({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
  suppliers.value = response.records
}

const resetReceiptForm = () => {
  receiptFormRef.value?.clearValidate()
  Object.assign(receiptForm, { customerId: '', receivableIds: [], receiptDate: '', amount: 0, remark: '' })
  receiptAllocationRows.value = []
  openReceivables.value = []
}

const resetPaymentForm = () => {
  paymentFormRef.value?.clearValidate()
  Object.assign(paymentForm, { supplierId: '', payableIds: [], paymentDate: '', amount: 0, remark: '' })
  paymentAllocationRows.value = []
  openPayables.value = []
}

const customerName = (id: string | number) => customerMap.value.get(String(id)) || `客户 ${id}`
const supplierName = (id: string | number) => supplierMap.value.get(String(id)) || `供应商 ${id}`
const formatMoney = (value?: number) => Number(value ?? 0).toFixed(2)
const today = () => new Date().toISOString().slice(0, 10)

watch(activeTab, (newTab) => {
  if (newTab === 'receipts') {
    loadReceipts()
  } else {
    loadPayments()
  }
})

onMounted(() => {
  loadReceipts()
  loadCustomers().catch(() => ElMessage.error('加载客户列表失败'))
  loadSuppliers().catch(() => ElMessage.error('加载供应商列表失败'))
})
</script>

<style scoped lang="scss">
.payments-container {
  padding: 20px;

  .search-card,
  .table-card {
    margin-bottom: 20px;
  }

  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }

  .detail-table {
    margin-top: 16px;
  }
}

.alloc-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  gap: 12px;
}
.alloc-tip {
  color: #6b7280;
  font-size: 12px;
}
</style>
