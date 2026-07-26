import { reactive, ref } from 'vue'

import type {
  PurchaseInquiry,
  PurchaseInquirySaveRequest
} from '@/api/purchase'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

type InquiryFormLine = {
  productId: string
  qty: number
  remark: string
}

export const usePurchaseInquiryForm = (
  t: Translate,
  options: {
    loadOptions: () => Promise<void>
    getInquiry: (id: string | number) => Promise<PurchaseInquiry>
    createInquiry: (payload: PurchaseInquirySaveRequest) => Promise<unknown>
    updateInquiry: (id: string | number, payload: PurchaseInquirySaveRequest) => Promise<unknown>
    formatBusinessDate?: () => string
    onSuccess?: Notify
    onWarning?: Notify
    onCompleted?: () => void | Promise<void>
  }
) => {
  const formVisible = ref(false)
  const editingId = ref<string | number | null>(null)
  const submitting = ref(false)
  const form = reactive<{
    inquiryDate: string
    title: string
    remark: string
    lines: InquiryFormLine[]
  }>({
    inquiryDate: '',
    title: '',
    remark: '',
    lines: []
  })

  const today = () => {
    if (options.formatBusinessDate) {
      return options.formatBusinessDate()
    }
    const d = new Date()
    const m = `${d.getMonth() + 1}`.padStart(2, '0')
    const day = `${d.getDate()}`.padStart(2, '0')
    return `${d.getFullYear()}-${m}-${day}`
  }

  const resetForm = () => {
    editingId.value = null
    form.inquiryDate = today()
    form.title = ''
    form.remark = ''
    form.lines = [{ productId: '', qty: 1, remark: '' }]
  }

  const addLine = () => {
    form.lines.push({ productId: '', qty: 1, remark: '' })
  }

  const removeLine = (index: number) => {
    if (form.lines.length <= 1) {
      options.onWarning?.(t('purchaseInquiryOps.validation.keepOneLine'))
      return
    }
    form.lines.splice(index, 1)
  }

  const handleCreate = async () => {
    await options.loadOptions()
    resetForm()
    formVisible.value = true
  }

  const handleEdit = async (row: PurchaseInquiry) => {
    try {
      await options.loadOptions()
      const detail = await options.getInquiry(row.id)
      editingId.value = detail.id
      form.inquiryDate = detail.inquiryDate
      form.title = detail.title || ''
      form.remark = detail.remark || ''
      form.lines = (detail.lines || []).map((line) => ({
        productId: String(line.productId),
        qty: Number(line.qty || 0),
        remark: line.remark || ''
      }))
      if (!form.lines.length) {
        form.lines = [{ productId: '', qty: 1, remark: '' }]
      }
      formVisible.value = true
    } catch {
      // Shared request interceptor surfaces the error.
    }
  }

  const confirmSave = async () => {
    if (!form.inquiryDate) {
      options.onWarning?.(t('purchaseInquiryOps.validation.inquiryDate'))
      return
    }
    const lines = form.lines
      .filter((line) => String(line.productId || '').trim())
      .map((line) => ({
        productId: line.productId,
        qty: Number(line.qty),
        remark: line.remark || undefined
      }))
    if (!lines.length) {
      options.onWarning?.(t('purchaseInquiryOps.validation.lineRequired'))
      return
    }
    if (lines.some((line) => !line.qty || line.qty <= 0)) {
      options.onWarning?.(t('purchaseInquiryOps.validation.quantityPositive'))
      return
    }

    submitting.value = true
    try {
      const payload: PurchaseInquirySaveRequest = {
        inquiryDate: form.inquiryDate,
        title: form.title || undefined,
        remark: form.remark || undefined,
        lines
      }
      if (editingId.value) {
        await options.updateInquiry(editingId.value, payload)
        options.onSuccess?.(t('purchaseInquiryOps.message.saved'))
      } else {
        await options.createInquiry(payload)
        options.onSuccess?.(t('purchaseInquiryOps.message.created'))
      }
      formVisible.value = false
      await options.onCompleted?.()
    } catch {
      // Shared request interceptor surfaces the error.
    } finally {
      submitting.value = false
    }
  }

  return {
    addLine,
    confirmSave,
    editingId,
    form,
    formVisible,
    handleCreate,
    handleEdit,
    removeLine,
    submitting
  }
}
