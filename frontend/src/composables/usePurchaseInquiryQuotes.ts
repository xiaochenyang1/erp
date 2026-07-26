import { reactive, ref } from 'vue'

import type {
  PurchaseInquiry,
  PurchaseInquiryLine,
  PurchaseInquiryQuote,
  PurchaseInquiryQuoteRequest
} from '@/api/purchase'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export const usePurchaseInquiryQuotes = (
  t: Translate,
  options: {
    loadOptions: () => Promise<void>
    getInquiry: (id: string | number) => Promise<PurchaseInquiry>
    addQuote: (id: string | number, payload: PurchaseInquiryQuoteRequest) => Promise<unknown>
    selectQuote: (id: string | number, quoteId: string | number) => Promise<unknown>
    onSuccess?: Notify
    onWarning?: Notify
    onError?: Notify
    onCompleted?: () => void | Promise<void>
  }
) => {
  const submitting = ref(false)

  const quoteVisible = ref(false)
  const quoteInquiryId = ref<string | number | null>(null)
  const quoteForm = reactive<{
    supplierId: string
    remark: string
    lines: Array<{
      inquiryLineId: string
      productId: string
      qty: number
      unitPrice: number
      taxRate: number
    }>
  }>({
    supplierId: '',
    remark: '',
    lines: []
  })

  const selectVisible = ref(false)
  const selectInquiryId = ref<string | number | null>(null)
  const selectQuotes = ref<PurchaseInquiryQuote[]>([])
  const selectInquiryLines = ref<PurchaseInquiryLine[]>([])
  const selectedQuoteId = ref<string | number | null>(null)

  const handleAddQuote = async (row: PurchaseInquiry) => {
    try {
      await options.loadOptions()
      const detail = await options.getInquiry(row.id)
      if ((detail.lines || []).some((line) => line.id == null)) {
        options.onError?.(t('purchaseInquiryOps.validation.lineIdMissing'))
        return
      }
      quoteInquiryId.value = detail.id
      quoteForm.supplierId = ''
      quoteForm.remark = ''
      quoteForm.lines = (detail.lines || []).map((line) => ({
        inquiryLineId: String(line.id),
        productId: String(line.productId),
        qty: Number(line.qty ?? 0),
        unitPrice: 0,
        taxRate: 13
      }))
      if (!quoteForm.lines.length) {
        options.onWarning?.(t('purchaseInquiryOps.validation.noQuotableLines'))
        return
      }
      quoteVisible.value = true
    } catch {
      // Shared request interceptor surfaces the error.
    }
  }

  const confirmQuote = async () => {
    if (!quoteInquiryId.value) return
    if (!String(quoteForm.supplierId || '').trim()) {
      options.onWarning?.(t('purchaseInquiryOps.validation.supplier'))
      return
    }
    if (!quoteForm.lines.length) {
      options.onWarning?.(t('purchaseInquiryOps.validation.noQuotableLines'))
      return
    }
    if (quoteForm.lines.some((line) => !Number.isFinite(Number(line.unitPrice)) || Number(line.unitPrice) < 0)) {
      options.onWarning?.(t('purchaseInquiryOps.validation.lineUnitPrice'))
      return
    }
    if (quoteForm.lines.some((line) => !Number.isFinite(Number(line.taxRate)) || Number(line.taxRate) < 0)) {
      options.onWarning?.(t('purchaseInquiryOps.validation.taxRate'))
      return
    }

    submitting.value = true
    try {
      await options.addQuote(quoteInquiryId.value, {
        supplierId: quoteForm.supplierId,
        lines: quoteForm.lines.map((line) => ({
          inquiryLineId: line.inquiryLineId,
          unitPrice: Number(line.unitPrice),
          taxRate: Number(line.taxRate)
        })),
        remark: quoteForm.remark || undefined
      })
      options.onSuccess?.(t('purchaseInquiryOps.message.quoteAdded'))
      quoteVisible.value = false
      await options.onCompleted?.()
    } catch {
      // Shared request interceptor surfaces the error.
    } finally {
      submitting.value = false
    }
  }

  const handleSelectQuote = async (row: PurchaseInquiry) => {
    try {
      await options.loadOptions()
      const detail = await options.getInquiry(row.id)
      selectInquiryId.value = detail.id
      selectInquiryLines.value = detail.lines || []
      selectQuotes.value = (detail.quotes || []).filter((q) => q.status === 'PENDING')
      selectedQuoteId.value = null
      if (!selectQuotes.value.length) {
        options.onWarning?.(t('purchaseInquiryOps.validation.noPendingQuotes'))
        return
      }
      selectVisible.value = true
    } catch {
      // Shared request interceptor surfaces the error.
    }
  }

  const onSelectQuoteRow = (row: PurchaseInquiryQuote | undefined) => {
    selectedQuoteId.value = row?.id ?? null
  }

  const confirmSelectQuote = async () => {
    if (!selectInquiryId.value || !selectedQuoteId.value) {
      options.onWarning?.(t('purchaseInquiryOps.validation.selectQuote'))
      return
    }
    submitting.value = true
    try {
      await options.selectQuote(selectInquiryId.value, selectedQuoteId.value)
      options.onSuccess?.(t('purchaseInquiryOps.message.winnerSelected'))
      selectVisible.value = false
      await options.onCompleted?.()
    } catch {
      // Shared request interceptor surfaces the error.
    } finally {
      submitting.value = false
    }
  }

  return {
    confirmQuote,
    confirmSelectQuote,
    handleAddQuote,
    handleSelectQuote,
    onSelectQuoteRow,
    quoteForm,
    quoteInquiryId,
    quoteVisible,
    selectInquiryId,
    selectInquiryLines,
    selectQuotes,
    selectVisible,
    selectedQuoteId,
    submitting
  }
}
