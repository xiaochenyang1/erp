import { reactive, ref } from 'vue'

import type { Customer, Product } from '@/api/masterdata'
import type {
  SalesQuote,
  SalesQuoteSaveRequest
} from '@/api/sales'
import type { PageResponse } from '@/types/common'
import { formatBusinessDate } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export type SalesQuoteLineForm = {
  productId: string
  qty: number
  price: number
  taxRate: number
}

const emptyLine = (): SalesQuoteLineForm => ({
  productId: '',
  qty: 1,
  price: 0,
  taxRate: 0.13
})

/**
 * Sales quote create/edit form: options, line editing, payload build and save.
 */
export const useSalesQuoteForm = (
  t: Translate,
  options: {
    getSalesQuote: (id: string | number) => Promise<SalesQuote>
    createSalesQuote: (data: SalesQuoteSaveRequest) => Promise<unknown>
    updateSalesQuote: (id: string | number, data: SalesQuoteSaveRequest) => Promise<unknown>
    getCustomers: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<Customer>>
    getProducts: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<Product>>
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
    onSaved?: () => void | Promise<void>
  }
) => {
  const formVisible = ref(false)
  const saving = ref(false)
  const editingId = ref<string | number | null>(null)
  const customers = ref<Customer[]>([])
  const products = ref<Product[]>([])

  const form = reactive({
    customerId: '',
    quoteDate: '',
    validUntil: '',
    remark: '',
    lines: [] as SalesQuoteLineForm[]
  })

  const today = () => formatBusinessDate()

  const loadOptions = async () => {
    try {
      const [customerPage, productPage] = await Promise.all([
        options.getCustomers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' }),
        options.getProducts({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
      ])
      customers.value = customerPage.records || []
      products.value = productPage.records || []
      return true
    } catch {
      customers.value = []
      products.value = []
      options.onError?.(t('salesQuote.message.optionsLoadFailed'))
      return false
    }
  }

  const resetForm = () => {
    editingId.value = null
    form.customerId = ''
    form.quoteDate = today()
    form.validUntil = ''
    form.remark = ''
    form.lines = [emptyLine()]
  }

  const addLine = () => {
    form.lines.push(emptyLine())
  }

  const removeLine = (index: number) => {
    form.lines.splice(index, 1)
  }

  const openCreate = async () => {
    await loadOptions()
    resetForm()
    formVisible.value = true
  }

  const openEdit = async (row: Pick<SalesQuote, 'id'>) => {
    await loadOptions()
    try {
      const detail = await options.getSalesQuote(row.id)
      editingId.value = detail.id
      form.customerId = String(detail.customerId)
      form.quoteDate = detail.quoteDate
      form.validUntil = detail.validUntil || ''
      form.remark = detail.remark || ''
      form.lines = (detail.lines || []).map((line) => ({
        productId: String(line.productId),
        qty: Number(line.qty),
        price: Number(line.price),
        taxRate: Number(line.taxRate || 0)
      }))
      if (form.lines.length === 0) {
        form.lines = [emptyLine()]
      }
      formVisible.value = true
      return true
    } catch {
      options.onError?.(t('salesQuote.message.detailLoadFailed'))
      return false
    }
  }

  const validateForm = () => {
    if (!form.customerId || !form.quoteDate || !form.lines.some((line) => line.productId)) {
      options.onWarning?.(t('salesQuote.message.completeForm'))
      return false
    }
    return true
  }

  const buildPayload = (): SalesQuoteSaveRequest => ({
    customerId: form.customerId,
    quoteDate: form.quoteDate,
    validUntil: form.validUntil || undefined,
    remark: form.remark || undefined,
    lines: form.lines
      .filter((line) => line.productId)
      .map((line) => ({
        productId: line.productId,
        qty: line.qty,
        price: line.price,
        taxRate: line.taxRate
      }))
  })

  const save = async () => {
    if (!validateForm()) return false
    saving.value = true
    try {
      const payload = buildPayload()
      if (editingId.value) {
        await options.updateSalesQuote(editingId.value, payload)
      } else {
        await options.createSalesQuote(payload)
      }
      options.onSuccess?.(t('salesQuote.message.saved'))
      formVisible.value = false
      await options.onSaved?.()
      return true
    } catch {
      options.onError?.(t('salesQuote.message.saveFailed'))
      return false
    } finally {
      saving.value = false
    }
  }

  return {
    addLine,
    buildPayload,
    customers,
    editingId,
    form,
    formVisible,
    loadOptions,
    openCreate,
    openEdit,
    products,
    removeLine,
    resetForm,
    save,
    saving,
    validateForm
  }
}
