<template>
  <div class="payments-container">
    <el-tabs v-model="activeTab">
      <el-tab-pane :label="$t('financeReportPages.payments.tabs.receipts')" name="receipts">
        <el-card class="search-card" shadow="never">
          <el-form :model="receiptQuery" inline>
            <el-form-item :label="$t('financeReportPages.payments.customer')">
              <el-select v-model="receiptQuery.customerId" :placeholder="$t('financeReportPages.payments.selectCustomer')" clearable filterable style="width: 200px">
                <el-option v-for="customer in customers" :key="customer.id" :label="customer.name" :value="customer.id" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('financeReportPages.common.status')">
              <el-select v-model="receiptQuery.status" :placeholder="$t('financeReportPages.common.statusPlaceholder')" clearable style="width: 150px">
                <el-option :label="$t('financeReportPages.payments.status.draft')" value="DRAFT" />
                <el-option :label="$t('financeReportPages.payments.status.posted')" value="POSTED" />
                <el-option :label="$t('financeReportPages.payments.status.cancelled')" value="CANCELLED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="searchReceipts"><el-icon><Search /></el-icon>{{ $t('financeReportPages.common.search') }}</el-button>
              <el-button v-permission="'finance:receipt:create'" type="primary" @click="handleCreateReceipt"><el-icon><Plus /></el-icon>{{ $t('financeReportPages.payments.newReceipt') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="table-card" shadow="never">
          <el-table v-loading="receiptLoading" :data="receiptData" border stripe>
            <el-table-column prop="receiptNo" :label="$t('financeReportPages.payments.receiptNo')" width="180" />
            <el-table-column prop="customerId" :label="$t('financeReportPages.payments.customer')" width="150">
              <template #default="{ row }">{{ customerName(row.customerId) }}</template>
            </el-table-column>
            <el-table-column prop="receiptDate" :label="$t('financeReportPages.payments.receiptDate')" width="120" />
            <el-table-column prop="receiptAmount" :label="$t('financeReportPages.payments.receiptAmount')" width="120" align="right">
              <template #default="{ row }">{{ formatCurrency(row.receiptAmount) }}</template>
            </el-table-column>
            <el-table-column prop="allocatedAmount" :label="$t('financeReportPages.payments.allocated')" width="120" align="right">
              <template #default="{ row }">{{ formatCurrency(row.allocatedAmount) }}</template>
            </el-table-column>
            <el-table-column prop="status" :label="$t('financeReportPages.common.status')" width="100">
              <template #default="{ row }">
                <el-tag :type="paymentStatusTagType(row.status)">{{ paymentStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" :label="$t('financeReportPages.common.remark')" min-width="160" show-overflow-tooltip />
            <el-table-column :label="$t('financeReportPages.common.actions')" width="210" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="viewReceipt(row)">{{ $t('financeReportPages.common.view') }}</el-button>
                <el-button link type="primary" @click="printReceiptRow(row)">{{ $t('financeReportPages.common.print') }}</el-button>
                <el-button v-if="row.status === 'DRAFT'" v-permission="'finance:receipt:cancel'" link type="danger" @click="cancelReceipt(row)">{{ $t('financeReportPages.common.cancel') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="receiptQuery.pageNo"
            v-model:page-size="receiptQuery.pageSize"
            :total="receiptTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @size-change="searchReceipts"
            @current-change="loadReceipts"
          />
        </el-card>
      </el-tab-pane>

      <el-tab-pane :label="$t('financeReportPages.payments.tabs.payments')" name="payments">
        <el-card class="search-card" shadow="never">
          <el-form :model="paymentQuery" inline>
            <el-form-item :label="$t('financeReportPages.payments.supplier')">
              <el-select v-model="paymentQuery.supplierId" :placeholder="$t('financeReportPages.payments.selectSupplier')" clearable filterable style="width: 200px">
                <el-option v-for="supplier in suppliers" :key="supplier.id" :label="supplier.name" :value="supplier.id" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('financeReportPages.common.status')">
              <el-select v-model="paymentQuery.status" :placeholder="$t('financeReportPages.common.statusPlaceholder')" clearable style="width: 150px">
                <el-option :label="$t('financeReportPages.payments.status.draft')" value="DRAFT" />
                <el-option :label="$t('financeReportPages.payments.status.posted')" value="POSTED" />
                <el-option :label="$t('financeReportPages.payments.status.cancelled')" value="CANCELLED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="searchPayments"><el-icon><Search /></el-icon>{{ $t('financeReportPages.common.search') }}</el-button>
              <el-button v-permission="'finance:payment:create'" type="primary" @click="handleCreatePayment"><el-icon><Plus /></el-icon>{{ $t('financeReportPages.payments.newPayment') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="table-card" shadow="never">
          <el-table v-loading="paymentLoading" :data="paymentData" border stripe>
            <el-table-column prop="paymentNo" :label="$t('financeReportPages.payments.paymentNo')" width="180" />
            <el-table-column prop="supplierId" :label="$t('financeReportPages.payments.supplier')" width="150">
              <template #default="{ row }">{{ supplierName(row.supplierId) }}</template>
            </el-table-column>
            <el-table-column prop="paymentDate" :label="$t('financeReportPages.payments.paymentDate')" width="120" />
            <el-table-column prop="paymentAmount" :label="$t('financeReportPages.payments.paymentAmount')" width="120" align="right">
              <template #default="{ row }">{{ formatCurrency(row.paymentAmount) }}</template>
            </el-table-column>
            <el-table-column prop="allocatedAmount" :label="$t('financeReportPages.payments.allocated')" width="120" align="right">
              <template #default="{ row }">{{ formatCurrency(row.allocatedAmount) }}</template>
            </el-table-column>
            <el-table-column prop="status" :label="$t('financeReportPages.common.status')" width="100">
              <template #default="{ row }">
                <el-tag :type="paymentStatusTagType(row.status)">{{ paymentStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" :label="$t('financeReportPages.common.remark')" min-width="160" show-overflow-tooltip />
            <el-table-column :label="$t('financeReportPages.common.actions')" width="210" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="viewPayment(row)">{{ $t('financeReportPages.common.view') }}</el-button>
                <el-button link type="primary" @click="printPaymentRow(row)">{{ $t('financeReportPages.common.print') }}</el-button>
                <el-button v-if="row.status === 'DRAFT'" v-permission="'finance:payment:cancel'" link type="danger" @click="cancelPayment(row)">{{ $t('financeReportPages.common.cancel') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="paymentQuery.pageNo"
            v-model:page-size="paymentQuery.pageSize"
            :total="paymentTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @size-change="searchPayments"
            @current-change="loadPayments"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="receiptDialogVisible" :title="$t('financeReportPages.payments.newReceipt')" width="820px" @close="resetReceiptForm">
      <el-form ref="receiptFormRef" :model="receiptForm" :rules="receiptRules" label-width="110px">
        <el-form-item :label="$t('financeReportPages.payments.customer')" prop="partyId">
          <el-select v-model="receiptForm.partyId" :placeholder="$t('financeReportPages.payments.selectCustomer')" filterable style="width: 100%" @change="loadOpenReceivables">
            <el-option v-for="customer in customers" :key="customer.id" :label="customer.name" :value="customer.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.payments.pendingReceivables')" prop="selectedIds">
          <el-select
            v-model="receiptForm.selectedIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            :placeholder="$t('financeReportPages.payments.receivablesPlaceholder')"
            style="width: 100%"
            @change="onReceivableSelectionChange"
          >
            <el-option
              v-for="item in openReceivables"
              :key="item.id"
              :label="$t('financeReportPages.payments.remainingOption', { no: item.receivableNo, amount: formatMoney(item.remainingAmount) })"
              :value="String(item.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.payments.receiptDate')" prop="documentDate">
          <el-date-picker v-model="receiptForm.documentDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.payments.receiptAmount')" prop="amount">
          <el-input-number v-model="receiptForm.amount" :min="0.01" :precision="2" :controls="false" style="width: 100%" @change="rebalanceReceiptAllocations" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.payments.allocationDetails')">
          <div style="width: 100%">
            <div class="alloc-toolbar">
              <el-button size="small" @click="autoAllocateReceipt">{{ $t('financeReportPages.payments.autoAllocate') }}</el-button>
              <span class="alloc-tip">
                {{ $t('financeReportPages.payments.allocatedSummary', { amount: formatMoney(receiptAllocatedTotal) }) }} /
                {{ $t('financeReportPages.payments.receiptSummary', { amount: formatMoney(receiptForm.amount) }) }}
                <template v-if="receiptUnallocated > 0.0001"> · {{ $t('financeReportPages.payments.unallocated', { amount: formatMoney(receiptUnallocated) }) }}</template>
                <template v-else-if="receiptUnallocated < -0.0001"> · {{ $t('financeReportPages.payments.exceeded', { amount: formatMoney(-receiptUnallocated) }) }}</template>
              </span>
            </div>
            <el-table :data="receiptAllocationRows" border size="small" max-height="260">
              <el-table-column prop="documentNo" :label="$t('financeReportPages.payments.receivableNo')" min-width="150" />
              <el-table-column prop="remainingAmount" :label="$t('financeReportPages.payments.remaining')" width="110" align="right">
                <template #default="{ row }">{{ formatMoney(row.remainingAmount) }}</template>
              </el-table-column>
              <el-table-column :label="$t('financeReportPages.payments.currentAllocation')" width="150">
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
        <el-form-item :label="$t('financeReportPages.common.remark')">
          <el-input v-model="receiptForm.remark" type="textarea" :rows="2" :placeholder="$t('financeReportPages.payments.remarkPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="receiptDialogVisible = false">{{ $t('financeReportPages.common.cancel') }}</el-button>
        <el-button type="primary" :loading="receiptSubmitting" @click="submitReceipt">{{ $t('financeReportPages.common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="paymentDialogVisible" :title="$t('financeReportPages.payments.newPayment')" width="820px" @close="resetPaymentForm">
      <el-form ref="paymentFormRef" :model="paymentForm" :rules="paymentRules" label-width="110px">
        <el-form-item :label="$t('financeReportPages.payments.supplier')" prop="partyId">
          <el-select v-model="paymentForm.partyId" :placeholder="$t('financeReportPages.payments.selectSupplier')" filterable style="width: 100%" @change="loadOpenPayables">
            <el-option v-for="supplier in suppliers" :key="supplier.id" :label="supplier.name" :value="supplier.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.payments.pendingPayables')" prop="selectedIds">
          <el-select
            v-model="paymentForm.selectedIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            :placeholder="$t('financeReportPages.payments.payablesPlaceholder')"
            style="width: 100%"
            @change="onPayableSelectionChange"
          >
            <el-option
              v-for="item in openPayables"
              :key="item.id"
              :label="$t('financeReportPages.payments.remainingOption', { no: item.payableNo, amount: formatMoney(item.remainingAmount) })"
              :value="String(item.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.payments.paymentDate')" prop="documentDate">
          <el-date-picker v-model="paymentForm.documentDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.payments.paymentAmount')" prop="amount">
          <el-input-number v-model="paymentForm.amount" :min="0.01" :precision="2" :controls="false" style="width: 100%" @change="rebalancePaymentAllocations" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.payments.allocationDetails')">
          <div style="width: 100%">
            <div class="alloc-toolbar">
              <el-button size="small" @click="autoAllocatePayment">{{ $t('financeReportPages.payments.autoAllocate') }}</el-button>
              <span class="alloc-tip">
                {{ $t('financeReportPages.payments.allocatedSummary', { amount: formatMoney(paymentAllocatedTotal) }) }} /
                {{ $t('financeReportPages.payments.paymentSummary', { amount: formatMoney(paymentForm.amount) }) }}
                <template v-if="paymentUnallocated > 0.0001"> · {{ $t('financeReportPages.payments.unallocated', { amount: formatMoney(paymentUnallocated) }) }}</template>
                <template v-else-if="paymentUnallocated < -0.0001"> · {{ $t('financeReportPages.payments.exceeded', { amount: formatMoney(-paymentUnallocated) }) }}</template>
              </span>
            </div>
            <el-table :data="paymentAllocationRows" border size="small" max-height="260">
              <el-table-column prop="documentNo" :label="$t('financeReportPages.payments.payableNo')" min-width="150" />
              <el-table-column prop="remainingAmount" :label="$t('financeReportPages.payments.remaining')" width="110" align="right">
                <template #default="{ row }">{{ formatMoney(row.remainingAmount) }}</template>
              </el-table-column>
              <el-table-column :label="$t('financeReportPages.payments.currentAllocation')" width="150">
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
        <el-form-item :label="$t('financeReportPages.common.remark')">
          <el-input v-model="paymentForm.remark" type="textarea" :rows="2" :placeholder="$t('financeReportPages.payments.remarkPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="paymentDialogVisible = false">{{ $t('financeReportPages.common.cancel') }}</el-button>
        <el-button type="primary" :loading="paymentSubmitting" @click="submitPayment">{{ $t('financeReportPages.common.confirm') }}</el-button>
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
        <el-table-column prop="receivableNo" :label="$t('financeReportPages.payments.receivableNo')" min-width="180">
          <template #default="{ row }">{{ row.receivableNo || row.receivableId }}</template>
        </el-table-column>
        <el-table-column prop="receivableId" :label="$t('financeReportPages.payments.receivableId')" min-width="180" />
        <el-table-column prop="allocatedAmount" :label="$t('financeReportPages.payments.allocatedAmount')" width="140" align="right">
          <template #default="{ row }">{{ formatCurrency(row.allocatedAmount) }}</template>
        </el-table-column>
      </el-table>

      <el-table v-if="selectedPayment" :data="paymentAllocations" border stripe class="detail-table">
        <el-table-column prop="payableNo" :label="$t('financeReportPages.payments.payableNo')" min-width="180">
          <template #default="{ row }">{{ row.payableNo || row.payableId }}</template>
        </el-table-column>
        <el-table-column prop="payableId" :label="$t('financeReportPages.payments.payableId')" min-width="180" />
        <el-table-column prop="allocatedAmount" :label="$t('financeReportPages.payments.allocatedAmount')" width="140" align="right">
          <template #default="{ row }">{{ formatCurrency(row.allocatedAmount) }}</template>
        </el-table-column>
      </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'
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
  type PaymentQuery,
  type Receipt,
  type ReceiptQuery,
  type Receivable
} from '@/api/finance'
import { getCustomers, getSuppliers, type Customer, type Supplier } from '@/api/masterdata'
import { printPayment, printReceipt } from '@/utils/bizPrint'
import { useSettlementPresentation } from '@/composables/useSettlementPresentation'
import { useSettlementList } from '@/composables/useSettlementList'
import { useSettlementForm } from '@/composables/useSettlementForm'
import { useSettlementDetail } from '@/composables/useSettlementDetail'

const { t } = useI18n()
const activeTab = ref('receipts')

const customers = ref<Customer[]>([])
const suppliers = ref<Supplier[]>([])

const receiptFormRef = ref<FormInstance>()
const paymentFormRef = ref<FormInstance>()

const {
  customerName,
  formatCurrency,
  formatMoney,
  paymentStatusLabel,
  paymentStatusTagType,
  supplierName
} = useSettlementPresentation(t, { customers, suppliers })

const notify = {
  onError: (message: string) => ElMessage.error(message),
  onSuccess: (message: string) => ElMessage.success(message),
  onWarning: (message: string) => ElMessage.warning(message)
}
const confirm = (message: string, title: string, options?: { type?: string }) =>
  ElMessageBox.confirm(message, title, options as any)

const {
  handleCancel: cancelReceipt,
  handlePrint: printReceiptRow,
  handleSearch: searchReceipts,
  loadData: loadReceipts,
  loading: receiptLoading,
  query: receiptQuery,
  tableData: receiptData,
  total: receiptTotal
} = useSettlementList<Receipt, ReceiptQuery>(t, {
  partyKey: 'customerId',
  documentNoKey: 'receiptNo',
  listFailedKey: 'financeReportPages.payments.message.receiptsLoadFailed',
  cancelConfirmKey: 'financeReportPages.payments.message.receiptCancelConfirm',
  getList: getReceipts,
  getDetail: getReceipt,
  cancelDoc: apiCancelReceipt,
  printDoc: printReceipt,
  decoratePrint: (doc) => ({
    ...doc,
    customerName: doc.customerName || customerName(doc.customerId)
  }),
  confirm,
  ...notify
})

const {
  handleCancel: cancelPayment,
  handlePrint: printPaymentRow,
  handleSearch: searchPayments,
  loadData: loadPayments,
  loading: paymentLoading,
  query: paymentQuery,
  tableData: paymentData,
  total: paymentTotal
} = useSettlementList<Payment, PaymentQuery>(t, {
  partyKey: 'supplierId',
  documentNoKey: 'paymentNo',
  listFailedKey: 'financeReportPages.payments.message.paymentsLoadFailed',
  cancelConfirmKey: 'financeReportPages.payments.message.paymentCancelConfirm',
  getList: getPayments,
  getDetail: getPayment,
  cancelDoc: apiCancelPayment,
  printDoc: printPayment,
  decoratePrint: (doc) => ({
    ...doc,
    supplierName: doc.supplierName || supplierName(doc.supplierId)
  }),
  confirm,
  ...notify
})

const receiptFormState = useSettlementForm<Receivable>(t, {
  partyKey: 'customerId',
  dateKey: 'receiptDate',
  documentNoKey: 'receivableNo',
  allocationIdKey: 'receivableId',
  amountKey: 'receiptAmount',
  methodKey: 'receiptMethod',
  method: 'BANK_TRANSFER',
  allocationExceededKey: 'financeReportPages.payments.validation.receiptAllocationExceeded',
  createdKey: 'financeReportPages.payments.message.receiptCreated',
  createFailedKey: 'financeReportPages.payments.message.receiptCreateFailed',
  getOpenItems: (params) => getReceivables(params as any),
  createDoc: (data) => createReceipt(data as any),
  onSubmitted: loadReceipts,
  ...notify
})

const paymentFormState = useSettlementForm<Payable>(t, {
  partyKey: 'supplierId',
  dateKey: 'paymentDate',
  documentNoKey: 'payableNo',
  allocationIdKey: 'payableId',
  amountKey: 'paymentAmount',
  methodKey: 'paymentMethod',
  method: 'BANK_TRANSFER',
  allocationExceededKey: 'financeReportPages.payments.validation.paymentAllocationExceeded',
  createdKey: 'financeReportPages.payments.message.paymentCreated',
  createFailedKey: 'financeReportPages.payments.message.paymentCreateFailed',
  getOpenItems: (params) => getPayables(params as any),
  createDoc: (data) => createPayment(data as any),
  onSubmitted: loadPayments,
  ...notify
})

const receiptForm = receiptFormState.form
const receiptDialogVisible = receiptFormState.dialogVisible
const receiptSubmitting = receiptFormState.submitting
const openReceivables = receiptFormState.openItems
const receiptAllocationRows = receiptFormState.allocationRows
const receiptAllocatedTotal = receiptFormState.allocatedTotal
const receiptUnallocated = receiptFormState.unallocated
const handleCreateReceipt = receiptFormState.handleCreate
const loadOpenReceivables = receiptFormState.loadOpenItems
const onReceivableSelectionChange = receiptFormState.onSelectionChange
const autoAllocateReceipt = receiptFormState.autoAllocate
const rebalanceReceiptAllocations = receiptFormState.rebalance

const paymentForm = paymentFormState.form
const paymentDialogVisible = paymentFormState.dialogVisible
const paymentSubmitting = paymentFormState.submitting
const openPayables = paymentFormState.openItems
const paymentAllocationRows = paymentFormState.allocationRows
const paymentAllocatedTotal = paymentFormState.allocatedTotal
const paymentUnallocated = paymentFormState.unallocated
const handleCreatePayment = paymentFormState.handleCreate
const loadOpenPayables = paymentFormState.loadOpenItems
const onPayableSelectionChange = paymentFormState.onSelectionChange
const autoAllocatePayment = paymentFormState.autoAllocate
const rebalancePaymentAllocations = paymentFormState.rebalance

const resetReceiptForm = () => {
  receiptFormRef.value?.clearValidate()
  receiptFormState.resetForm()
}

const resetPaymentForm = () => {
  paymentFormRef.value?.clearValidate()
  paymentFormState.resetForm()
}

const {
  detailItems,
  detailLoading,
  detailTitle,
  detailVisible,
  paymentAllocations,
  receiptAllocations,
  selectedPayment,
  selectedReceipt,
  viewPayment,
  viewReceipt
} = useSettlementDetail(t, {
  getReceipt,
  getPayment,
  buildReceiptItems: (detail) => [
    { label: t('financeReportPages.payments.customer'), value: detail.customerName || customerName(detail.customerId) },
    { label: t('financeReportPages.payments.receiptDate'), value: detail.receiptDate },
    { label: t('financeReportPages.payments.receiptAmount'), value: formatCurrency(detail.receiptAmount) },
    { label: t('financeReportPages.payments.allocated'), value: formatCurrency(detail.allocatedAmount) },
    { label: t('financeReportPages.common.status'), value: paymentStatusLabel(detail.status) },
    { label: t('financeReportPages.common.remark'), value: detail.remark || '-' }
  ],
  buildPaymentItems: (detail) => [
    { label: t('financeReportPages.payments.supplier'), value: detail.supplierName || supplierName(detail.supplierId) },
    { label: t('financeReportPages.payments.paymentDate'), value: detail.paymentDate },
    { label: t('financeReportPages.payments.paymentAmount'), value: formatCurrency(detail.paymentAmount) },
    { label: t('financeReportPages.payments.allocated'), value: formatCurrency(detail.allocatedAmount) },
    { label: t('financeReportPages.common.status'), value: paymentStatusLabel(detail.status) },
    { label: t('financeReportPages.common.remark'), value: detail.remark || '-' }
  ],
  onError: notify.onError
})

const receiptRules = computed<FormRules>(() => ({
  partyId: [{ required: true, message: t('financeReportPages.payments.validation.customer'), trigger: 'change' }],
  selectedIds: [{ type: 'array', required: true, message: t('financeReportPages.payments.validation.receivable'), trigger: 'change' }],
  documentDate: [{ required: true, message: t('financeReportPages.payments.validation.receiptDate'), trigger: 'change' }],
  amount: [{ required: true, message: t('financeReportPages.payments.validation.receiptAmount'), trigger: 'blur' }]
}))

const paymentRules = computed<FormRules>(() => ({
  partyId: [{ required: true, message: t('financeReportPages.payments.validation.supplier'), trigger: 'change' }],
  selectedIds: [{ type: 'array', required: true, message: t('financeReportPages.payments.validation.payable'), trigger: 'change' }],
  documentDate: [{ required: true, message: t('financeReportPages.payments.validation.paymentDate'), trigger: 'change' }],
  amount: [{ required: true, message: t('financeReportPages.payments.validation.paymentAmount'), trigger: 'blur' }]
}))

const submitReceipt = async () => {
  if (!receiptFormRef.value) return
  await receiptFormRef.value.validate(async (valid) => {
    if (!valid) return
    await receiptFormState.submit()
  })
}

const submitPayment = async () => {
  if (!paymentFormRef.value) return
  await paymentFormRef.value.validate(async (valid) => {
    if (!valid) return
    await paymentFormState.submit()
  })
}

const loadCustomers = async () => {
  const response = await getCustomers({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
  customers.value = response.records
}

const loadSuppliers = async () => {
  const response = await getSuppliers({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
  suppliers.value = response.records
}

watch(activeTab, (newTab) => {
  if (newTab === 'receipts') {
    loadReceipts()
  } else {
    loadPayments()
  }
})

onMounted(() => {
  loadReceipts()
  loadCustomers().catch(() => ElMessage.error(t('financeReportPages.payments.message.customersLoadFailed')))
  loadSuppliers().catch(() => ElMessage.error(t('financeReportPages.payments.message.suppliersLoadFailed')))
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
