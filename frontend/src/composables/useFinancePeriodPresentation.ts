import { computed, type Ref } from 'vue'

import type { AccountPeriod } from '@/api/finance'
import { formatLocalizedDateTime, formatLocalizedNumber } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary'

export const useFinancePeriodPresentation = (t: Translate) => {
  const formatMonth = (periodMonth?: string) => periodMonth || '-'

  const formatDateTime = (value?: string) => formatLocalizedDateTime(value) || '-'

  const formatAmount = (amount?: number | string) => {
    const value = Number(amount ?? 0)
    return Number.isFinite(value)
      ? formatLocalizedNumber(value, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
      : '0.00'
  }

  const formatQty = (qty?: number | string) => {
    const value = Number(qty ?? 0)
    return Number.isFinite(value)
      ? formatLocalizedNumber(value, { minimumFractionDigits: 4, maximumFractionDigits: 4 })
      : '0.0000'
  }

  const getStatusLabel = (status: string) => {
    const map: Record<string, string> = {
      OPEN: t('financeReportPages.periods.status.open'),
      LOCKED: t('financeReportPages.periods.status.locked'),
      CLOSED: t('financeReportPages.periods.status.closed')
    }
    return map[status] || status
  }

  const getStatusType = (status: string): TagType => {
    const map: Record<string, TagType> = {
      OPEN: 'primary',
      LOCKED: 'warning',
      CLOSED: 'success'
    }
    return map[status] || 'info'
  }

  const getIssueTypeLabel = (type: string) => {
    const map: Record<string, string> = {
      OPEN_DOCUMENTS: t('financeReportPages.periods.issue.openDocuments'),
      INVENTORY_FINANCE_RECONCILIATION: t('financeReportPages.periods.issue.inventoryFinanceReconciliation'),
      VOUCHER_ENTRY_MISSING: t('financeReportPages.periods.issue.voucherEntryMissing'),
      VOUCHER_UNBALANCED: t('financeReportPages.periods.issue.voucherUnbalanced'),
      PAYMENT_ALLOCATION_MISMATCH: t('financeReportPages.periods.issue.paymentAllocationMismatch'),
      RECEIPT_ALLOCATION_MISMATCH: t('financeReportPages.periods.issue.receiptAllocationMismatch'),
      SETTLEMENT_AMOUNT_INVALID: t('financeReportPages.periods.issue.settlementAmountInvalid'),
      BANK_STATEMENT_UNMATCHED: t('financeReportPages.periods.issue.bankStatementUnmatched'),
      INVENTORY_BALANCE_NEGATIVE: t('financeReportPages.periods.issue.inventoryBalanceNegative')
    }
    return map[type] || type
  }

  const getIssueSeverity = (type: string): TagType =>
    type === 'INVENTORY_FINANCE_RECONCILIATION' ? 'danger' : 'warning'

  const getDifferenceTypeLabel = (type: string) => {
    const map: Record<string, string> = {
      INVENTORY_ONLY: t('financeReportPages.periods.difference.inventoryOnly'),
      FINANCE_ONLY: t('financeReportPages.periods.difference.financeOnly'),
      AMOUNT_MISMATCH: t('financeReportPages.periods.difference.amountMismatch')
    }
    return map[type] || type
  }

  const getDifferenceTypeTag = (type: string): TagType => {
    const map: Record<string, TagType> = {
      INVENTORY_ONLY: 'warning',
      FINANCE_ONLY: 'info',
      AMOUNT_MISMATCH: 'danger'
    }
    return map[type] || 'info'
  }

  const getSourceTypeLabel = (type: string) => {
    const map: Record<string, string> = {
      PURCHASE_RECEIPT: t('financeReportPages.periods.source.purchaseReceipt'),
      PURCHASE_RETURN: t('financeReportPages.periods.source.purchaseReturn'),
      SALES_DELIVERY: t('financeReportPages.periods.source.salesDelivery'),
      SALES_RETURN: t('financeReportPages.periods.source.salesReturn'),
      INVENTORY_ADJUSTMENT: t('financeReportPages.periods.source.inventoryAdjustment'),
      INVENTORY_TRANSFER: t('financeReportPages.periods.source.inventoryTransfer')
    }
    return map[type] || type || '-'
  }

  const differenceTypeOptions = computed(() => [
    { label: t('financeReportPages.periods.difference.inventoryOnly'), value: 'INVENTORY_ONLY' },
    { label: t('financeReportPages.periods.difference.financeOnly'), value: 'FINANCE_ONLY' },
    { label: t('financeReportPages.periods.difference.amountMismatch'), value: 'AMOUNT_MISMATCH' }
  ])

  const buildStatusSummary = (rows: AccountPeriod[]) => {
    const count = (status: string) => rows.filter((item) => item.status === status).length
    return [
      { key: 'open', label: t('financeReportPages.periods.statusSummary.open'), value: count('OPEN') },
      { key: 'locked', label: t('financeReportPages.periods.statusSummary.locked'), value: count('LOCKED') },
      { key: 'closed', label: t('financeReportPages.periods.statusSummary.closed'), value: count('CLOSED') },
      { key: 'total', label: t('financeReportPages.periods.statusSummary.total'), value: rows.length }
    ]
  }

  const useStatusSummary = (tableData: Ref<AccountPeriod[]>) =>
    computed(() => buildStatusSummary(tableData.value))

  return {
    buildStatusSummary,
    differenceTypeOptions,
    formatAmount,
    formatDateTime,
    formatMonth,
    formatQty,
    getDifferenceTypeLabel,
    getDifferenceTypeTag,
    getIssueSeverity,
    getIssueTypeLabel,
    getSourceTypeLabel,
    getStatusLabel,
    getStatusType,
    useStatusSummary
  }
}
