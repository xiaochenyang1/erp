import { computed, reactive, ref } from 'vue'

import type {
  FinanceInvoice,
  FinanceInvoiceSaveRequest
} from '@/api/finance'
import { formatBusinessDate } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

/**
 * Finance invoice create/edit form. Element Plus field rules stay on the page;
 * this composable owns payload build, detail load and submit.
 */
export const useFinanceInvoiceForm = (
  t: Translate,
  options: {
    getFinanceInvoice: (id: string | number) => Promise<FinanceInvoice>
    createFinanceInvoice: (data: FinanceInvoiceSaveRequest) => Promise<unknown>
    updateFinanceInvoice: (
      id: string | number,
      data: FinanceInvoiceSaveRequest
    ) => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const dialogVisible = ref(false)
  const dialogMode = ref<'create' | 'edit'>('create')
  const submitLoading = ref(false)

  const formData = reactive({
    id: '' as string | number,
    invoiceType: 'INPUT',
    partnerName: '',
    invoiceDate: '',
    amount: 0,
    taxAmount: 0,
    relatedBizType: '',
    relatedBizId: '',
    remark: ''
  })

  const dialogTitle = computed(() =>
    dialogMode.value === 'edit'
      ? t('financeReportPages.invoices.dialog.edit')
      : t('financeReportPages.invoices.dialog.create')
  )

  const resetForm = () => {
    Object.assign(formData, {
      id: '',
      invoiceType: 'INPUT',
      partnerName: '',
      invoiceDate: '',
      amount: 0,
      taxAmount: 0,
      relatedBizType: '',
      relatedBizId: '',
      remark: ''
    })
  }

  const handleAdd = () => {
    resetForm()
    dialogMode.value = 'create'
    formData.invoiceDate = formatBusinessDate()
    dialogVisible.value = true
  }

  const handleEdit = async (row: Pick<FinanceInvoice, 'id'>) => {
    try {
      const invoice = await options.getFinanceInvoice(row.id)
      Object.assign(formData, {
        id: invoice.id,
        invoiceType: invoice.invoiceType,
        partnerName: invoice.partnerName,
        invoiceDate: invoice.invoiceDate,
        amount: Number(invoice.amount || 0),
        taxAmount: Number(invoice.taxAmount || 0),
        relatedBizType: invoice.relatedBizType || '',
        relatedBizId: invoice.relatedBizId || '',
        remark: invoice.remark || ''
      })
      dialogMode.value = 'edit'
      dialogVisible.value = true
      return true
    } catch {
      options.onError?.(t('financeReportPages.invoices.message.detailLoadFailed'))
      return false
    }
  }

  const buildPayload = (): FinanceInvoiceSaveRequest => ({
    invoiceType: formData.invoiceType,
    partnerName: formData.partnerName,
    invoiceDate: formData.invoiceDate,
    amount: formData.amount,
    taxAmount: formData.taxAmount,
    relatedBizType: formData.relatedBizType || undefined,
    relatedBizId: formData.relatedBizId || undefined,
    remark: formData.remark || undefined
  })

  const submitSave = async () => {
    submitLoading.value = true
    try {
      const payload = buildPayload()
      if (formData.id) {
        await options.updateFinanceInvoice(formData.id, payload)
      } else {
        await options.createFinanceInvoice(payload)
      }
      options.onSuccess?.(t('financeReportPages.invoices.message.saved'))
      dialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('financeReportPages.invoices.message.saveFailed'))
      return false
    } finally {
      submitLoading.value = false
    }
  }

  const handleDialogClose = () => {
    resetForm()
  }

  return {
    buildPayload,
    dialogMode,
    dialogTitle,
    dialogVisible,
    formData,
    handleAdd,
    handleDialogClose,
    handleEdit,
    resetForm,
    submitLoading,
    submitSave
  }
}
