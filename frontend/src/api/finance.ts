import { request } from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

// ==================== 应收账款 ====================

export type FinanceAccountStatus = 'UNSETTLED' | 'PARTIALLY_SETTLED' | 'SETTLED' | 'OFFSET'

export interface Receivable {
  id: string
  receivableNo: string
  customerId: string
  customerName?: string
  sourceType?: string
  sourceId?: string
  sourceNo?: string
  direction?: string
  receivableAmount: number
  receivedAmount: number
  remainingAmount: number
  originalAmount?: number
  settledAmount?: number
  bizDate?: string
  dueDate?: string
  status: FinanceAccountStatus
  createdTime?: string
  updatedTime?: string
  remark?: string
}

export interface ReceivableQuery extends PageQuery {
  receivableNo?: string
  customerId?: string | number
  status?: FinanceAccountStatus | ''
  startDate?: string
  endDate?: string
}

// 应收账款API
export const getReceivables = (params: ReceivableQuery) => {
  return request.get<PageResponse<Receivable>>('/finance/receivables', { params: normalizeFinanceAccountQuery(params) }).then((page) => ({
    ...page,
    records: page.records.map(normalizeReceivable)
  }))
}

export const getReceivable = (id: string | number) => {
  return request.get<Receivable>(`/finance/receivables/${id}`).then(normalizeReceivable)
}

export const exportReceivables = (params: ReceivableQuery) => {
  return request.get<Blob>('/finance/receivables/export', {
    params: normalizeFinanceAccountQuery(params),
    responseType: 'blob'
  })
}

// ==================== 应付账款 ====================

export interface Payable {
  id: string
  payableNo: string
  supplierId: string
  supplierName?: string
  sourceType?: string
  sourceId?: string
  sourceNo?: string
  direction?: string
  payableAmount: number
  paidAmount: number
  remainingAmount: number
  originalAmount?: number
  settledAmount?: number
  bizDate?: string
  dueDate?: string
  status: FinanceAccountStatus
  createdTime?: string
  updatedTime?: string
  remark?: string
}

export interface PayableQuery extends PageQuery {
  payableNo?: string
  supplierId?: string | number
  status?: FinanceAccountStatus | ''
  startDate?: string
  endDate?: string
}

// 应付账款API
export const getPayables = (params: PayableQuery) => {
  return request.get<PageResponse<Payable>>('/finance/payables', { params: normalizeFinanceAccountQuery(params) }).then((page) => ({
    ...page,
    records: page.records.map(normalizePayable)
  }))
}

export const getPayable = (id: string | number) => {
  return request.get<Payable>(`/finance/payables/${id}`).then(normalizePayable)
}

export const exportPayables = (params: PayableQuery) => {
  return request.get<Blob>('/finance/payables/export', {
    params: normalizeFinanceAccountQuery(params),
    responseType: 'blob'
  })
}

// ==================== 收款管理 ====================

export interface Receipt {
  id: string
  receiptNo: string
  customerId: string
  customerName?: string
  receiptDate: string
  receiptAmount: number
  amount?: number
  allocatedAmount?: number
  receiptMethod: 'CASH' | 'BANK_TRANSFER' | 'CHECK' | 'OTHER'
  bankAccount?: string
  allocations: ReceiptAllocation[]
  status: 'DRAFT' | 'POSTED' | 'COMPLETED' | 'CANCELLED'
  remark?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface ReceiptAllocation {
  receivableId: string | number
  receivableNo?: string
  allocatedAmount: number
  amount?: number
}

export interface ReceiptQuery extends PageQuery {
  receiptNo?: string
  customerId?: string | number
  status?: string
  startDate?: string
  endDate?: string
}

export interface ReceiptCreateRequest {
  customerId: string | number
  receiptDate: string
  receiptAmount: number
  receiptMethod: string
  bankAccount?: string
  allocations: ReceiptAllocation[]
  remark?: string
}

// 收款API
export const getReceipts = (params: ReceiptQuery) => {
  return request.get<PageResponse<Receipt>>('/finance/receipts', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeReceipt)
  }))
}

export const getReceipt = (id: string | number) => {
  return request.get<Receipt>(`/finance/receipts/${id}`).then(normalizeReceipt)
}

export const createReceipt = (data: ReceiptCreateRequest) => {
  return request.post<Receipt>('/finance/receipts', toReceiptPayload(data)).then(normalizeReceipt)
}

export const cancelReceipt = (id: string | number, reason = '前端作废') => {
  return request.post<Receipt>(`/finance/receipts/${id}/cancel`, { reason }).then(normalizeReceipt)
}

// ==================== 付款管理 ====================

export interface Payment {
  id: string
  paymentNo: string
  supplierId: string
  supplierName?: string
  paymentDate: string
  paymentAmount: number
  amount?: number
  allocatedAmount?: number
  paymentMethod: 'CASH' | 'BANK_TRANSFER' | 'CHECK' | 'OTHER'
  bankAccount?: string
  allocations: PaymentAllocation[]
  status: 'DRAFT' | 'POSTED' | 'COMPLETED' | 'CANCELLED'
  remark?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface PaymentAllocation {
  payableId: string | number
  payableNo?: string
  allocatedAmount: number
  amount?: number
}

export interface PaymentQuery extends PageQuery {
  paymentNo?: string
  supplierId?: string | number
  status?: string
  startDate?: string
  endDate?: string
}

export interface PaymentCreateRequest {
  supplierId: string | number
  paymentDate: string
  paymentAmount: number
  paymentMethod: string
  bankAccount?: string
  allocations: PaymentAllocation[]
  remark?: string
}

// 付款API
export const getPayments = (params: PaymentQuery) => {
  return request.get<PageResponse<Payment>>('/finance/payments', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizePayment)
  }))
}

export const getPayment = (id: string | number) => {
  return request.get<Payment>(`/finance/payments/${id}`).then(normalizePayment)
}

export const createPayment = (data: PaymentCreateRequest) => {
  return request.post<Payment>('/finance/payments', toPaymentPayload(data)).then(normalizePayment)
}

export const cancelPayment = (id: string | number, reason = '前端作废') => {
  return request.post<Payment>(`/finance/payments/${id}/cancel`, { reason }).then(normalizePayment)
}

const normalizeFinanceAccountQuery = (params: ReceivableQuery | PayableQuery) => {
  const { startDate, endDate, ...rest } = params
  return {
    ...rest,
    bizDateFrom: startDate || undefined,
    bizDateTo: endDate || undefined
  }
}

const normalizeReceivable = (item: Receivable): Receivable => ({
  ...item,
  id: String(item.id),
  customerId: String(item.customerId),
  sourceId: item.sourceId != null ? String(item.sourceId) : undefined,
  receivableAmount: item.receivableAmount ?? item.originalAmount ?? 0,
  receivedAmount: item.receivedAmount ?? item.settledAmount ?? 0,
  remainingAmount: item.remainingAmount ?? 0,
  createdTime: item.createdTime || '',
  updatedTime: item.updatedTime || ''
})

const normalizePayable = (item: Payable): Payable => ({
  ...item,
  id: String(item.id),
  supplierId: String(item.supplierId),
  sourceId: item.sourceId != null ? String(item.sourceId) : undefined,
  payableAmount: item.payableAmount ?? item.originalAmount ?? 0,
  paidAmount: item.paidAmount ?? item.settledAmount ?? 0,
  remainingAmount: item.remainingAmount ?? 0,
  createdTime: item.createdTime || '',
  updatedTime: item.updatedTime || ''
})

const normalizeReceipt = (item: Receipt): Receipt => ({
  ...item,
  id: String(item.id),
  customerId: String(item.customerId),
  customerName: item.customerName || '',
  receiptAmount: item.receiptAmount ?? item.amount ?? 0,
  receiptMethod: item.receiptMethod || 'BANK_TRANSFER',
  allocations: (item.allocations || []).map((allocation) => ({
    ...allocation,
    receivableId: String(allocation.receivableId),
    allocatedAmount: allocation.allocatedAmount ?? allocation.amount ?? 0
  })),
  createdAt: item.createdAt || '',
  updatedAt: item.updatedAt || ''
})

const normalizePayment = (item: Payment): Payment => ({
  ...item,
  id: String(item.id),
  supplierId: String(item.supplierId),
  supplierName: item.supplierName || '',
  paymentAmount: item.paymentAmount ?? item.amount ?? 0,
  paymentMethod: item.paymentMethod || 'BANK_TRANSFER',
  allocations: (item.allocations || []).map((allocation) => ({
    ...allocation,
    payableId: String(allocation.payableId),
    allocatedAmount: allocation.allocatedAmount ?? allocation.amount ?? 0
  })),
  createdAt: item.createdAt || '',
  updatedAt: item.updatedAt || ''
})

const toReceiptPayload = (data: ReceiptCreateRequest) => ({
  customerId: data.customerId,
  receiptDate: data.receiptDate,
  amount: data.receiptAmount,
  remark: data.remark,
  allocations: data.allocations.map((allocation) => ({
    receivableId: allocation.receivableId,
    amount: allocation.allocatedAmount
  }))
})

const toPaymentPayload = (data: PaymentCreateRequest) => ({
  supplierId: data.supplierId,
  paymentDate: data.paymentDate,
  amount: data.paymentAmount,
  remark: data.remark,
  allocations: data.allocations.map((allocation) => ({
    payableId: allocation.payableId,
    amount: allocation.allocatedAmount
  }))
})

// ==================== 财务凭证 ====================

export interface Voucher {
  id: string
  voucherNo: string
  sourceType: 'MANUAL' | 'EXPENSE' | string
  sourceId?: string
  sourceNo?: string
  bizDate: string
  amount: number
  status: 'DRAFT' | 'APPROVED' | 'POSTED' | 'CANCELLED'
  expenseSource?: {
    expenseId: string
    expenseNo: string
    expenseDate: string
    status: string
    amount: number
  } | null
  remark?: string

  // 页面兼容别名
  voucherType?: string
  voucherDate?: string
  totalDebit?: number
  totalCredit?: number
  entries?: VoucherEntry[]
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface VoucherEntry {
  id?: string
  voucherId?: string
  bizDate?: string
  lineNo?: number
  subjectId: string
  subjectCode: string
  subjectName: string
  debitAmount: number
  creditAmount: number
  summary?: string
}

export interface VoucherQuery extends PageQuery {
  voucherNo?: string
  voucherType?: string
  sourceType?: string
  status?: string
  startDate?: string
  endDate?: string
  dateFrom?: string
  dateTo?: string
}

// 凭证API
export const getVouchers = (params: VoucherQuery) => {
  return request.get<PageResponse<Voucher>>('/finance/vouchers', {
    params: toVoucherQueryParams(params)
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizeVoucher)
  }))
}

export const getVoucher = (id: string | number) => {
  return request.get<Voucher>(`/finance/vouchers/${id}`).then(normalizeVoucher)
}

export const getVoucherEntries = (id: string | number) => {
  return request.get<VoucherEntry[]>(`/finance/vouchers/${id}/entries`).then((entries) => entries.map(normalizeVoucherEntry))
}

const toVoucherQueryParams = (params: VoucherQuery) => {
  const { voucherType, startDate, endDate, ...rest } = params
  return {
    ...rest,
    sourceType: params.sourceType || voucherType || undefined,
    dateFrom: params.dateFrom || startDate || undefined,
    dateTo: params.dateTo || endDate || undefined
  }
}

const normalizeVoucher = (voucher: Voucher): Voucher => {
  return {
    ...voucher,
    id: String(voucher.id),
    sourceId: voucher.sourceId != null ? String(voucher.sourceId) : undefined,
    expenseSource: voucher.expenseSource
      ? {
          ...voucher.expenseSource,
          expenseId: String(voucher.expenseSource.expenseId)
        }
      : null,
    voucherType: voucher.voucherType || voucher.sourceType,
    voucherDate: voucher.voucherDate || voucher.bizDate,
    totalDebit: voucher.totalDebit ?? voucher.amount ?? 0,
    totalCredit: voucher.totalCredit ?? voucher.amount ?? 0,
    entries: (voucher.entries || []).map(normalizeVoucherEntry)
  }
}

const normalizeVoucherEntry = (entry: VoucherEntry): VoucherEntry => ({
  ...entry,
  id: entry.id != null ? String(entry.id) : undefined,
  voucherId: entry.voucherId != null ? String(entry.voucherId) : undefined,
  subjectId: String(entry.subjectId)
})

// ==================== 会计科目 ====================

export interface AccountSubject {
  id: string
  subjectCode?: string
  subjectName?: string
  subjectType?: 'ASSET' | 'LIABILITY' | 'EQUITY' | 'REVENUE' | 'EXPENSE' | string
  balanceDirection?: string
  code?: string
  name?: string
  category?: 'ASSET' | 'LIABILITY' | 'EQUITY' | 'REVENUE' | 'EXPENSE' | string
  parentId?: string
  parentName?: string
  level?: number
  isLeaf?: boolean
  status: 'ACTIVE' | 'INACTIVE' | 'DISABLED'
  remark?: string
  children?: AccountSubject[]
}

export interface AccountSubjectQuery extends PageQuery {
  subjectCode?: string
  subjectName?: string
  subjectType?: string
  code?: string
  name?: string
  category?: string
  status?: string
}

export interface AccountSubjectSaveRequest {
  subjectCode: string
  subjectName: string
  subjectType: string
  balanceDirection: string
  parentId?: string | number
  remark?: string
}

// 会计科目API
export const getAccountSubjects = (params: AccountSubjectQuery) => {
  return request.get<PageResponse<AccountSubject>>('/finance/account-subjects', {
    params: toAccountSubjectQueryParams(params)
  }).then((page) => ({
    ...page,
    records: page.records.map(normalizeAccountSubject)
  }))
}

export const getAccountSubjectTree = () => {
  return request.get<AccountSubject[]>('/finance/account-subjects/tree')
    .then((subjects) => subjects.map(normalizeAccountSubject))
}

export const getAccountSubject = (id: string | number) => {
  return request.get<AccountSubject>(`/finance/account-subjects/${id}`).then(normalizeAccountSubject)
}

export const createAccountSubject = (data: AccountSubjectSaveRequest) => {
  return request.post<AccountSubject>('/finance/account-subjects', toAccountSubjectPayload(data)).then(normalizeAccountSubject)
}

export const updateAccountSubject = (id: string | number, data: AccountSubjectSaveRequest) => {
  return request.put<AccountSubject>(`/finance/account-subjects/${id}`, toAccountSubjectPayload(data)).then(normalizeAccountSubject)
}

export const enableAccountSubject = (id: string | number) => {
  return request.post<AccountSubject>(`/finance/account-subjects/${id}/enable`).then(normalizeAccountSubject)
}

export const disableAccountSubject = (id: string | number) => {
  return request.post<AccountSubject>(`/finance/account-subjects/${id}/disable`).then(normalizeAccountSubject)
}

const toAccountSubjectQueryParams = (params: AccountSubjectQuery) => {
  const { code, name, category, ...rest } = params
  return {
    ...rest,
    subjectCode: params.subjectCode || code || undefined,
    subjectName: params.subjectName || name || undefined,
    subjectType: params.subjectType || category || undefined
  }
}

const toAccountSubjectPayload = (data: AccountSubjectSaveRequest) => ({
  subjectCode: data.subjectCode,
  subjectName: data.subjectName,
  parentId: data.parentId,
  subjectType: data.subjectType,
  balanceDirection: data.balanceDirection,
  remark: data.remark
})

const normalizeAccountSubject = (subject: AccountSubject): AccountSubject => {
  const children = subject.children?.map(normalizeAccountSubject)
  return {
    ...subject,
    id: String(subject.id),
    parentId: subject.parentId != null ? String(subject.parentId) : undefined,
    code: subject.code || subject.subjectCode || '',
    name: subject.name || subject.subjectName || '',
    category: subject.category || subject.subjectType || '',
    isLeaf: subject.isLeaf ?? !children?.length,
    children
  }
}

// ==================== 总账查询 ====================

export interface LedgerEntry {
  id: string
  voucherId: string
  bizDate: string
  lineNo: number
  subjectCode: string
  subjectName: string
  summary: string
  debitAmount: number
  creditAmount: number
}

export interface LedgerQuery extends PageQuery {
  subjectId?: string | number
  subjectCode?: string
  startDate?: string
  endDate?: string
  dateFrom?: string
  dateTo?: string
}

export interface LedgerSummary {
  subjectCode: string
  subjectName: string
  debitAmount: number
  creditAmount: number
}

// 总账API
export const getLedgerEntries = (params: LedgerQuery) => {
  return request.get<LedgerEntry[]>('/finance/ledger/detail', {
    params: toLedgerQueryParams(params)
  }).then((entries) => entries.map(normalizeLedgerEntry))
}

export const getLedgerSummary = (params: LedgerQuery) => {
  return request.get<LedgerSummary[]>('/finance/ledger/general', {
    params: toLedgerQueryParams(params)
  }).then((summaries) => summaries.map(normalizeLedgerSummary))
}

export const exportLedger = (params: LedgerQuery) => {
  return request.get<Blob>('/finance/ledger/export', {
    params: toLedgerQueryParams(params),
    responseType: 'blob'
  })
}

const toLedgerQueryParams = (params: LedgerQuery) => {
  const { startDate, endDate, ...rest } = params
  return {
    ...rest,
    dateFrom: params.dateFrom || startDate || undefined,
    dateTo: params.dateTo || endDate || undefined
  }
}

const normalizeLedgerEntry = (entry: LedgerEntry): LedgerEntry => ({
  ...entry,
  id: String(entry.id),
  voucherId: String(entry.voucherId)
})

const normalizeLedgerSummary = (summary: LedgerSummary): LedgerSummary => ({
  ...summary,
  debitAmount: summary.debitAmount ?? 0,
  creditAmount: summary.creditAmount ?? 0
})

// ==================== 会计期间 ====================

export type AccountPeriodStatus = 'OPEN' | 'LOCKED' | 'CLOSED'

export interface AccountPeriod {
  id: string
  periodYear: number
  periodMonth: string
  startDate: string
  endDate: string
  status: AccountPeriodStatus | string
  lockedBy?: string
  lockedTime?: string
  closedBy?: string
  closedTime?: string
  reopenedBy?: string
  reopenedTime?: string
  remark?: string
}

export interface AccountPeriodCloseIssue {
  type: string
  message: string
  amount: number
}

export interface AccountPeriodCloseCheckItem {
  code: string
  title: string
  category: string
  passed: boolean
  message: string
  metric: number
}

export interface AccountPeriodCloseCheck {
  periodId: string
  periodMonth: string
  passed: boolean
  issues: AccountPeriodCloseIssue[]
  checks?: AccountPeriodCloseCheckItem[]
}

export interface InventoryFinanceReconciliation {
  periodId: string
  periodMonth: string
  inventoryNetAmount: number
  financeInventoryNetAmount: number
  differenceAmount: number
  balanced: boolean
}

export interface InventoryFinanceDifference {
  sourceKey: string
  sourceType: string
  sourceNo: string
  inventoryAmount: number
  financeAmount: number
  differenceAmount: number
  differenceType: string
}

export interface InventoryFinanceInventoryTransaction {
  id: string
  bizType: string
  bizNo: string
  direction: string
  qty: number
  amount: number
  occurredTime: string
  remark?: string
}

export interface InventoryFinanceVoucherEntry {
  voucherId: string
  voucherNo: string
  sourceType: string
  sourceNo: string
  bizDate: string
  lineNo: number
  subjectCode: string
  subjectName: string
  debitAmount: number
  creditAmount: number
  summary?: string
}

export interface InventoryFinanceDifferenceDetail extends InventoryFinanceDifference {
  periodId: string
  periodMonth: string
  inventoryTransactions: InventoryFinanceInventoryTransaction[]
  voucherEntries: InventoryFinanceVoucherEntry[]
}

export interface InventoryFinanceDifferenceQuery {
  differenceType?: string
}

export const getAccountPeriods = (year?: number) => {
  return request.get<AccountPeriod[]>('/finance/periods', { params: { year } })
    .then((periods) => periods.map(normalizeAccountPeriod))
}

export const generateAccountPeriods = (year: number) => {
  return request.post<AccountPeriod[]>('/finance/periods/generate', { year })
    .then((periods) => periods.map(normalizeAccountPeriod))
}

export const lockAccountPeriod = (id: string | number) => {
  return request.post<AccountPeriod>(`/finance/periods/${id}/lock`).then(normalizeAccountPeriod)
}

export const checkAccountPeriodClose = (id: string | number) => {
  return request.get<AccountPeriodCloseCheck>(`/finance/periods/${id}/close-check`)
    .then(normalizeAccountPeriodCloseCheck)
}

export const closeAccountPeriod = (id: string | number) => {
  return request.post<AccountPeriod>(`/finance/periods/${id}/close`).then(normalizeAccountPeriod)
}

export const reopenAccountPeriod = (id: string | number) => {
  return request.post<AccountPeriod>(`/finance/periods/${id}/reopen`).then(normalizeAccountPeriod)
}

export const getInventoryFinanceReconciliation = (id: string | number) => {
  return request.get<InventoryFinanceReconciliation>(`/finance/periods/${id}/reconciliation`)
    .then(normalizeInventoryFinanceReconciliation)
}

export const getInventoryFinanceDifferences = (
  id: string | number,
  params?: InventoryFinanceDifferenceQuery
) => {
  return request.get<InventoryFinanceDifference[]>(`/finance/periods/${id}/reconciliation/differences`, { params })
}

export const getInventoryFinanceDifferenceDetail = (
  id: string | number,
  sourceType: string,
  sourceNo: string
) => {
  return request.get<InventoryFinanceDifferenceDetail>(`/finance/periods/${id}/reconciliation/differences/detail`, {
    params: { sourceType, sourceNo }
  }).then(normalizeInventoryFinanceDifferenceDetail)
}

const normalizeAccountPeriod = (period: AccountPeriod): AccountPeriod => ({
  ...period,
  id: String(period.id),
  lockedBy: period.lockedBy != null ? String(period.lockedBy) : undefined,
  closedBy: period.closedBy != null ? String(period.closedBy) : undefined,
  reopenedBy: period.reopenedBy != null ? String(period.reopenedBy) : undefined
})

const normalizeAccountPeriodCloseCheck = (result: AccountPeriodCloseCheck): AccountPeriodCloseCheck => ({
  ...result,
  periodId: String(result.periodId),
  issues: (result.issues || []).map((issue) => ({
    ...issue,
    amount: Number(issue.amount ?? 0)
  })),
  checks: (result.checks || []).map((item) => ({
    ...item,
    metric: Number(item.metric ?? 0)
  }))
})

const normalizeInventoryFinanceReconciliation = (
  result: InventoryFinanceReconciliation
): InventoryFinanceReconciliation => ({
  ...result,
  periodId: String(result.periodId)
})

const normalizeInventoryFinanceDifferenceDetail = (
  detail: InventoryFinanceDifferenceDetail
): InventoryFinanceDifferenceDetail => ({
  ...detail,
  periodId: String(detail.periodId),
  inventoryTransactions: (detail.inventoryTransactions || []).map((transaction) => ({
    ...transaction,
    id: String(transaction.id),
    qty: Number(transaction.qty ?? 0),
    amount: Number(transaction.amount ?? 0)
  })),
  voucherEntries: (detail.voucherEntries || []).map((entry) => ({
    ...entry,
    voucherId: String(entry.voucherId),
    lineNo: Number(entry.lineNo ?? 0),
    debitAmount: Number(entry.debitAmount ?? 0),
    creditAmount: Number(entry.creditAmount ?? 0)
  }))
})

// ==================== 费用管理 ====================

export interface Expense {
  id: string
  expenseNo: string
  expenseDate: string
  subjectId: string | number
  paymentSubjectId: string | number
  amount: number
  status: 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'POSTED' | 'CANCELLED' | string
  voucherId?: string
  voucherNo?: string
  voucherStatus?: string
  voucherAmount?: number
  voucherEntryCount?: number
  voucherBalanced?: boolean
  amountMatched?: boolean
  reversalVoucherId?: string
  reversalVoucherNo?: string
  reversalVoucherStatus?: string
  reversalVoucherAmount?: number
  reversalVoucherEntryCount?: number
  reversalVoucherBalanced?: boolean
  reversalAmountMatched?: boolean
  reversed?: boolean
  remark?: string
}

export interface ExpenseQuery extends PageQuery {
  status?: string
  dateFrom?: string
  dateTo?: string
}

export interface ExpenseCreateRequest {
  expenseDate: string
  subjectId: string | number
  paymentSubjectId: string | number
  amount: number
  remark?: string
}

export interface ExpenseReconciliation {
  expense: Expense
  voucher: Voucher | null
  entries: VoucherEntry[]
  reversalVoucher: Voucher | null
  reversalEntries: VoucherEntry[]
  debitTotal: number
  creditTotal: number
  reversalDebitTotal: number
  reversalCreditTotal: number
  voucherMissing: boolean
  entriesMissing: boolean
  voucherBalanced: boolean
  amountMatched: boolean
  voucherLinkedToExpense: boolean
  reversalVoucherBalanced: boolean
  reversalAmountMatched: boolean
  reversed: boolean
}

// 费用API
export const getExpenses = (params: ExpenseQuery) => {
  return request.get<PageResponse<Expense>>('/finance/expenses', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeExpense)
  }))
}

export const getExpense = (id: string | number) => {
  return request.get<Expense>(`/finance/expenses/${id}`).then(normalizeExpense)
}

export const getExpenseReconciliation = (id: string | number) => {
  return request.get<ExpenseReconciliation>(`/finance/expenses/${id}/reconciliation`).then(normalizeExpenseReconciliation)
}

export const createExpense = (data: ExpenseCreateRequest) => {
  return request.post<Expense>('/finance/expenses', toExpensePayload(data)).then(normalizeExpense)
}

export const updateExpense = (id: string | number, data: ExpenseCreateRequest) => {
  return request.put<Expense>(`/finance/expenses/${id}`, toExpensePayload(data)).then(normalizeExpense)
}

export const submitExpense = (id: string | number, remark?: string) => {
  return request.post<Expense>(`/finance/expenses/${id}/submit`, { remark }).then(normalizeExpense)
}

export const approveExpense = (id: string | number, remark?: string) => {
  return request.post<Expense>(`/finance/expenses/${id}/approve`, { remark }).then(normalizeExpense)
}

export const rejectExpense = (id: string | number, reason: string) => {
  return request.post<Expense>(`/finance/expenses/${id}/reject`, { reason }).then(normalizeExpense)
}

export const postExpense = (id: string | number) => {
  return request.post<Expense>(`/finance/expenses/${id}/post`).then(normalizeExpense)
}

export const reverseExpense = (id: string | number) => {
  return request.post<Expense>(`/finance/expenses/${id}/reverse`).then(normalizeExpense)
}

export const cancelExpense = (id: string | number) => {
  return request.post<Expense>(`/finance/expenses/${id}/cancel`).then(normalizeExpense)
}

const toExpensePayload = (data: ExpenseCreateRequest) => ({
  expenseDate: data.expenseDate,
  subjectId: data.subjectId,
  paymentSubjectId: data.paymentSubjectId,
  amount: data.amount,
  remark: data.remark
})

const normalizeExpense = (expense: Expense): Expense => ({
  ...expense,
  id: String(expense.id),
  subjectId: String(expense.subjectId),
  paymentSubjectId: String(expense.paymentSubjectId),
  voucherId: expense.voucherId != null ? String(expense.voucherId) : undefined,
  reversalVoucherId: expense.reversalVoucherId != null ? String(expense.reversalVoucherId) : undefined,
  amount: Number(expense.amount ?? 0),
  voucherAmount: expense.voucherAmount == null ? undefined : Number(expense.voucherAmount),
  voucherEntryCount: expense.voucherEntryCount == null ? undefined : Number(expense.voucherEntryCount),
  reversalVoucherAmount: expense.reversalVoucherAmount == null ? undefined : Number(expense.reversalVoucherAmount),
  reversalVoucherEntryCount: expense.reversalVoucherEntryCount == null ? undefined : Number(expense.reversalVoucherEntryCount)
})

const normalizeExpenseReconciliation = (result: ExpenseReconciliation): ExpenseReconciliation => ({
  ...result,
  expense: normalizeExpense(result.expense),
  voucher: result.voucher ? normalizeVoucher(result.voucher) : null,
  entries: (result.entries || []).map(normalizeVoucherEntry),
  reversalVoucher: result.reversalVoucher ? normalizeVoucher(result.reversalVoucher) : null,
  reversalEntries: (result.reversalEntries || []).map(normalizeVoucherEntry),
  debitTotal: Number(result.debitTotal ?? 0),
  creditTotal: Number(result.creditTotal ?? 0),
  reversalDebitTotal: Number(result.reversalDebitTotal ?? 0),
  reversalCreditTotal: Number(result.reversalCreditTotal ?? 0),
  voucherMissing: Boolean(result.voucherMissing),
  entriesMissing: Boolean(result.entriesMissing),
  voucherBalanced: Boolean(result.voucherBalanced),
  amountMatched: Boolean(result.amountMatched),
  voucherLinkedToExpense: Boolean(result.voucherLinkedToExpense),
  reversalVoucherBalanced: Boolean(result.reversalVoucherBalanced),
  reversalAmountMatched: Boolean(result.reversalAmountMatched),
  reversed: Boolean(result.reversed)
})

// ==================== 手工凭证 ====================
// 财务人员手工录入的记账凭证（计提/结转/更正）。状态机：
// DRAFT →(提交)→ PENDING →(审批)→ APPROVED →(过账)→ POSTED；PENDING →(驳回)→ DRAFT；POSTED →(作废)→ CANCELLED。

export type ManualVoucherStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'POSTED' | 'CANCELLED'

export interface ManualVoucherLine {
  id?: string
  lineNo: number
  subjectId: string
  subjectCode: string
  subjectName: string
  debitAmount: number
  creditAmount: number
  summary?: string
}

export interface ManualVoucher {
  id: string
  voucherNo: string
  bizDate: string
  amount: number
  status: ManualVoucherStatus
  remark?: string
  postedVoucherId?: string
  reversalVoucherId?: string
  cancelReason?: string
  rejectReason?: string
  submittedTime?: string
  approvedTime?: string
  postedTime?: string
  cancelledTime?: string
  createdTime?: string
  lines: ManualVoucherLine[]
}

export interface ManualVoucherLineRequest {
  subjectId: string | number
  debitAmount: number
  creditAmount: number
  summary?: string
}

export interface ManualVoucherSaveRequest {
  bizDate: string
  remark?: string
  lines: ManualVoucherLineRequest[]
}

export interface ManualVoucherQuery extends PageQuery {
  voucherNo?: string
  status?: string
  dateFrom?: string
  dateTo?: string
}

const normalizeManualVoucherLine = (line: ManualVoucherLine): ManualVoucherLine => ({
  ...line,
  id: line.id != null ? String(line.id) : undefined,
  lineNo: Number(line.lineNo ?? 0),
  subjectId: String(line.subjectId),
  debitAmount: Number(line.debitAmount ?? 0),
  creditAmount: Number(line.creditAmount ?? 0)
})

const normalizeManualVoucher = (voucher: ManualVoucher): ManualVoucher => ({
  ...voucher,
  id: String(voucher.id),
  postedVoucherId: voucher.postedVoucherId != null ? String(voucher.postedVoucherId) : undefined,
  reversalVoucherId: voucher.reversalVoucherId != null ? String(voucher.reversalVoucherId) : undefined,
  amount: Number(voucher.amount ?? 0),
  lines: (voucher.lines || []).map(normalizeManualVoucherLine)
})

export const getManualVouchers = (params: ManualVoucherQuery) => {
  return request.get<PageResponse<ManualVoucher>>('/finance/vouchers/manual', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeManualVoucher)
  }))
}

export const getManualVoucher = (id: string | number) => {
  return request.get<ManualVoucher>(`/finance/vouchers/manual/${id}`).then(normalizeManualVoucher)
}

export const createManualVoucher = (data: ManualVoucherSaveRequest) => {
  return request.post<ManualVoucher>('/finance/vouchers/manual', data).then(normalizeManualVoucher)
}

export const updateManualVoucher = (id: string | number, data: ManualVoucherSaveRequest) => {
  return request.put<ManualVoucher>(`/finance/vouchers/manual/${id}`, data).then(normalizeManualVoucher)
}

export const submitManualVoucher = (id: string | number) => {
  return request.post<void>(`/finance/vouchers/manual/${id}/submit`)
}

export const approveManualVoucher = (id: string | number) => {
  return request.post<void>(`/finance/vouchers/manual/${id}/approve`)
}

export const rejectManualVoucher = (id: string | number, reason: string) => {
  return request.post<void>(`/finance/vouchers/manual/${id}/reject`, { reason })
}

export const postManualVoucher = (id: string | number) => {
  return request.post<void>(`/finance/vouchers/manual/${id}/post`)
}

export const cancelManualVoucher = (id: string | number, reason: string) => {
  return request.post<void>(`/finance/vouchers/manual/${id}/cancel`, { reason })
}

export const deleteManualVoucher = (id: string | number) => {
  return request.delete<void>(`/finance/vouchers/manual/${id}`)
}

// ==================== 发票登记 ====================

export interface FinanceInvoice {
  id: string
  invoiceNo: string
  invoiceType: 'INPUT' | 'OUTPUT' | string
  partnerName: string
  amount: number
  taxAmount: number
  invoiceDate: string
  relatedBizType?: string
  relatedBizId?: string
  status: 'DRAFT' | 'POSTED' | 'CANCELLED' | string
  remark?: string
}

export interface FinanceInvoiceQuery extends PageQuery {
  status?: string
  invoiceType?: string
  partnerName?: string
  dateFrom?: string
  dateTo?: string
}

export interface FinanceInvoiceSaveRequest {
  invoiceType: string
  partnerName: string
  amount: number
  taxAmount: number
  invoiceDate: string
  relatedBizType?: string
  relatedBizId?: string | number
  remark?: string
}

export const getFinanceInvoices = (params: FinanceInvoiceQuery) => {
  return request.get<PageResponse<FinanceInvoice>>('/finance/invoices', { params }).then((page) => ({
    ...page,
    records: page.records.map(normalizeFinanceInvoice)
  }))
}

export const getFinanceInvoice = (id: string | number) => {
  return request.get<FinanceInvoice>(`/finance/invoices/${id}`).then(normalizeFinanceInvoice)
}

export const createFinanceInvoice = (data: FinanceInvoiceSaveRequest) => {
  return request.post<FinanceInvoice>('/finance/invoices', data).then(normalizeFinanceInvoice)
}

export const updateFinanceInvoice = (id: string | number, data: FinanceInvoiceSaveRequest) => {
  return request.put<FinanceInvoice>(`/finance/invoices/${id}`, data).then(normalizeFinanceInvoice)
}

export const postFinanceInvoice = (id: string | number) => {
  return request.post<FinanceInvoice>(`/finance/invoices/${id}/post`).then(normalizeFinanceInvoice)
}

export const cancelFinanceInvoice = (id: string | number) => {
  return request.post<FinanceInvoice>(`/finance/invoices/${id}/cancel`).then(normalizeFinanceInvoice)
}

const normalizeFinanceInvoice = (invoice: FinanceInvoice): FinanceInvoice => ({
  ...invoice,
  id: String(invoice.id),
  relatedBizId: invoice.relatedBizId != null ? String(invoice.relatedBizId) : undefined,
  amount: Number(invoice.amount ?? 0),
  taxAmount: Number(invoice.taxAmount ?? 0)
})

// ==================== 账龄分析 ====================

export interface FinanceAgingBucket {
  code: string
  label: string
  minDaysInclusive: number
  maxDaysInclusive?: number | null
  count: number
  amount: number
}

export interface FinanceAgingOpenItem {
  side: 'RECEIVABLE' | 'PAYABLE' | string
  id: string | number
  docNo: string
  partnerId?: string | number
  partnerName?: string
  bizDate?: string
  dueDate?: string
  agingDays: number
  bucketCode: string
  remainingAmount: number
  status?: string
}

export interface FinanceAgingSummary {
  asOfDate: string
  receivableTotal: number
  payableTotal: number
  receivableBuckets: FinanceAgingBucket[]
  payableBuckets: FinanceAgingBucket[]
  overdueReceivables: FinanceAgingOpenItem[]
  overduePayables: FinanceAgingOpenItem[]
}

export const getFinanceAgingSummary = (asOfDate?: string) => {
  return request
    .get<FinanceAgingSummary>('/finance/aging', { params: asOfDate ? { asOfDate } : undefined })
    .then((summary) => ({
      ...summary,
      receivableTotal: Number(summary.receivableTotal ?? 0),
      payableTotal: Number(summary.payableTotal ?? 0),
      receivableBuckets: (summary.receivableBuckets || []).map((b) => ({
        ...b,
        count: Number(b.count ?? 0),
        amount: Number(b.amount ?? 0)
      })),
      payableBuckets: (summary.payableBuckets || []).map((b) => ({
        ...b,
        count: Number(b.count ?? 0),
        amount: Number(b.amount ?? 0)
      })),
      overdueReceivables: (summary.overdueReceivables || []).map((item) => ({
        ...item,
        id: String(item.id),
        partnerId: item.partnerId != null ? String(item.partnerId) : item.partnerId,
        agingDays: Number(item.agingDays ?? 0),
        remainingAmount: Number(item.remainingAmount ?? 0)
      })),
      overduePayables: (summary.overduePayables || []).map((item) => ({
        ...item,
        id: String(item.id),
        partnerId: item.partnerId != null ? String(item.partnerId) : item.partnerId,
        agingDays: Number(item.agingDays ?? 0),
        remainingAmount: Number(item.remainingAmount ?? 0)
      }))
    }))
}

// ==================== 往来对账 / 毛利 ====================

export interface PartnerStatementLine {
  bizDate: string
  docType: string
  docNo: string
  direction: string
  amount: number
  balance: number
  remark?: string
}

export interface PartnerStatement {
  partnerType: string
  partnerId: string | number
  partnerName: string
  dateFrom: string
  dateTo: string
  openingBalance: number
  totalIncrease: number
  totalDecrease: number
  closingBalance: number
  lines: PartnerStatementLine[]
}

export const getPartnerStatement = (params: {
  partnerType: 'CUSTOMER' | 'SUPPLIER' | string
  partnerId: string | number
  dateFrom: string
  dateTo: string
}) => {
  return request.get<PartnerStatement>('/finance/statements', { params }).then((s) => ({
    ...s,
    partnerId: String(s.partnerId),
    openingBalance: Number(s.openingBalance ?? 0),
    totalIncrease: Number(s.totalIncrease ?? 0),
    totalDecrease: Number(s.totalDecrease ?? 0),
    closingBalance: Number(s.closingBalance ?? 0),
    lines: (s.lines || []).map((l) => ({
      ...l,
      amount: Number(l.amount ?? 0),
      balance: Number(l.balance ?? 0)
    }))
  }))
}

export interface GrossMarginLine {
  productId?: string | number
  productCode?: string
  productName?: string
  salesQty: number
  salesAmount: number
  costAmount: number
  grossMargin: number
  marginRate: number
}

export interface GrossMarginSummary {
  dateFrom: string
  dateTo: string
  salesAmount: number
  costAmount: number
  grossMargin: number
  marginRate: number
  lines: GrossMarginLine[]
}

export const getGrossMarginSummary = (dateFrom: string, dateTo: string) => {
  return request
    .get<GrossMarginSummary>('/finance/gross-margin', { params: { dateFrom, dateTo } })
    .then((s) => ({
      ...s,
      salesAmount: Number(s.salesAmount ?? 0),
      costAmount: Number(s.costAmount ?? 0),
      grossMargin: Number(s.grossMargin ?? 0),
      marginRate: Number(s.marginRate ?? 0),
      lines: (s.lines || []).map((l) => ({
        ...l,
        productId: l.productId != null ? String(l.productId) : l.productId,
        salesQty: Number(l.salesQty ?? 0),
        salesAmount: Number(l.salesAmount ?? 0),
        costAmount: Number(l.costAmount ?? 0),
        grossMargin: Number(l.grossMargin ?? 0),
        marginRate: Number(l.marginRate ?? 0)
      }))
    }))
}
