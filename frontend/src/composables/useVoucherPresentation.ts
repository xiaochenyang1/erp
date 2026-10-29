import type { Voucher } from '@/api/finance'
import { formatLocalizedCurrency, formatLocalizedDate } from '@/utils/locale'

type Translate = (key: string) => string
type SourceTagType = 'success' | 'warning' | 'info'
type StatusTagType = 'info' | 'warning' | 'success' | 'danger'

/**
 * Registry of automatic voucher sources; the order here is the order of the filter dropdown.
 * Keep it in sync whenever the backend FinanceVoucherPostingService / ExpensePostingService /
 * ManualVoucherPostingService gains a source type, otherwise the voucher page falls back to
 * rendering the raw enum name.
 */
const voucherSourceTypes: ReadonlyArray<{ value: string; messageKey: string; tag: SourceTagType }> = [
  { value: 'RECEIPT', messageKey: 'receipt', tag: 'success' },
  { value: 'RECEIPT_REVERSAL', messageKey: 'receiptReversal', tag: 'warning' },
  { value: 'PAYMENT', messageKey: 'payment', tag: 'success' },
  { value: 'PAYMENT_REVERSAL', messageKey: 'paymentReversal', tag: 'warning' },
  { value: 'EXPENSE', messageKey: 'expense', tag: 'success' },
  { value: 'EXPENSE_REVERSAL', messageKey: 'expenseReversal', tag: 'warning' },
  { value: 'PURCHASE_RECEIPT', messageKey: 'purchaseReceipt', tag: 'info' },
  { value: 'PURCHASE_RETURN', messageKey: 'purchaseReturn', tag: 'warning' },
  { value: 'SALES_DELIVERY', messageKey: 'salesDelivery', tag: 'info' },
  { value: 'SALES_RETURN', messageKey: 'salesReturn', tag: 'warning' },
  { value: 'INVENTORY_ADJUSTMENT', messageKey: 'inventoryAdjustment', tag: 'info' },
  { value: 'PRODUCTION_ISSUE', messageKey: 'productionIssue', tag: 'info' },
  { value: 'PRODUCTION_COMPLETION', messageKey: 'productionCompletion', tag: 'info' },
  { value: 'PRODUCTION_COMPLETION_REVERSAL', messageKey: 'productionCompletionReversal', tag: 'warning' },
  { value: 'PRODUCTION_RETURN', messageKey: 'productionReturn', tag: 'warning' },
  { value: 'MANUAL', messageKey: 'manual', tag: 'info' },
  { value: 'MANUAL_REVERSAL', messageKey: 'manualReversal', tag: 'warning' },
  { value: 'FX_REVALUATION_AR', messageKey: 'fxRevaluationAr', tag: 'info' },
  { value: 'FX_REVALUATION_AP', messageKey: 'fxRevaluationAp', tag: 'info' },
  { value: 'FX_REVALUATION_AR_REVERSAL', messageKey: 'fxRevaluationArReversal', tag: 'warning' },
  { value: 'FX_REVALUATION_AP_REVERSAL', messageKey: 'fxRevaluationApReversal', tag: 'warning' },
  { value: 'FX_REVALUATION_AR_CANCEL', messageKey: 'fxRevaluationArCancel', tag: 'warning' },
  { value: 'FX_REVALUATION_AP_CANCEL', messageKey: 'fxRevaluationApCancel', tag: 'warning' },
  { value: 'FX_REVALUATION_AR_REVERSAL_CANCEL', messageKey: 'fxRevaluationArReversalCancel', tag: 'warning' },
  { value: 'FX_REVALUATION_AP_REVERSAL_CANCEL', messageKey: 'fxRevaluationApReversalCancel', tag: 'warning' }
]

const sourceMessageKey = (messageKey: string) => `financeReportPages.vouchers.sourceValue.${messageKey}`

/** Localized values and tag mappings for read-only finance vouchers. */
export const useVoucherPresentation = (t: Translate) => {
  const formatMoney = (amount?: number) => formatLocalizedCurrency(Number(amount || 0))
  const formatDate = (value?: string) => formatLocalizedDate(value)

  const sourceTypeOptions = () =>
    voucherSourceTypes.map((source) => ({ value: source.value, label: t(sourceMessageKey(source.messageKey)) }))

  const sourceTypeLabel = (sourceType?: string) => {
    if (!sourceType) return '-'
    const source = voucherSourceTypes.find((item) => item.value === sourceType)
    return source ? t(sourceMessageKey(source.messageKey)) : sourceType
  }

  const sourceTypeTag = (sourceType?: string): SourceTagType =>
    voucherSourceTypes.find((item) => item.value === sourceType)?.tag || 'info'

  const statusLabel = (status: string) => {
    const labels: Record<string, string> = {
      DRAFT: t('financeReportPages.vouchers.status.draft'),
      APPROVED: t('financeReportPages.vouchers.status.approved'),
      POSTED: t('financeReportPages.vouchers.status.posted'),
      CANCELLED: t('financeReportPages.vouchers.status.cancelled')
    }
    return labels[status] || status
  }

  const statusType = (status: string): StatusTagType => {
    const types: Record<string, StatusTagType> = {
      DRAFT: 'info',
      APPROVED: 'warning',
      POSTED: 'success',
      CANCELLED: 'danger'
    }
    return types[status] || 'info'
  }

  const toVoucherRow = (row: unknown) => row as Voucher

  return {
    formatDate,
    formatMoney,
    sourceTypeLabel,
    sourceTypeOptions,
    sourceTypeTag,
    statusLabel,
    statusType,
    toVoucherRow
  }
}
