import { ref } from 'vue'

import type {
  Payment,
  PaymentAllocation,
  Receipt,
  ReceiptAllocation
} from '@/api/finance'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export interface SettlementDetailItem {
  label: string
  value: string
}

/**
 * One detail dialog serves both tabs. Whichever side is loaded owns the
 * allocation grid, so the other side is cleared before every load to keep a
 * stale grid from rendering under the new title.
 */
export const useSettlementDetail = (
  t: Translate,
  options: {
    getReceipt: (id: string | number) => Promise<Receipt>
    getPayment: (id: string | number) => Promise<Payment>
    buildReceiptItems: (doc: Receipt) => SettlementDetailItem[]
    buildPaymentItems: (doc: Payment) => SettlementDetailItem[]
    onError?: Notify
  }
) => {
  const detailVisible = ref(false)
  const detailLoading = ref(false)
  const detailTitle = ref('')
  const detailItems = ref<SettlementDetailItem[]>([])
  const selectedReceipt = ref<Receipt>()
  const selectedPayment = ref<Payment>()
  const receiptAllocations = ref<ReceiptAllocation[]>([])
  const paymentAllocations = ref<PaymentAllocation[]>([])

  const resetDetail = () => {
    selectedReceipt.value = undefined
    selectedPayment.value = undefined
    receiptAllocations.value = []
    paymentAllocations.value = []
    detailItems.value = []
  }

  const viewReceipt = async (row: Receipt) => {
    detailTitle.value = t('financeReportPages.payments.receiptTitle', { no: row.receiptNo })
    detailVisible.value = true
    detailLoading.value = true
    resetDetail()
    try {
      const detail = await options.getReceipt(row.id)
      selectedReceipt.value = detail
      receiptAllocations.value = detail.allocations || []
      detailItems.value = options.buildReceiptItems(detail)
      return detail
    } catch {
      options.onError?.(t('financeReportPages.payments.message.receiptDetailLoadFailed'))
      detailVisible.value = false
      return undefined
    } finally {
      detailLoading.value = false
    }
  }

  const viewPayment = async (row: Payment) => {
    detailTitle.value = t('financeReportPages.payments.paymentTitle', { no: row.paymentNo })
    detailVisible.value = true
    detailLoading.value = true
    resetDetail()
    try {
      const detail = await options.getPayment(row.id)
      selectedPayment.value = detail
      paymentAllocations.value = detail.allocations || []
      detailItems.value = options.buildPaymentItems(detail)
      return detail
    } catch {
      options.onError?.(t('financeReportPages.payments.message.paymentDetailLoadFailed'))
      detailVisible.value = false
      return undefined
    } finally {
      detailLoading.value = false
    }
  }

  return {
    detailItems,
    detailLoading,
    detailTitle,
    detailVisible,
    paymentAllocations,
    receiptAllocations,
    resetDetail,
    selectedPayment,
    selectedReceipt,
    viewPayment,
    viewReceipt
  }
}
