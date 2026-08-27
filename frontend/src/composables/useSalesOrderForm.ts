import { computed, getCurrentInstance, reactive, ref, watch, onBeforeUnmount, type Ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

import type {
  SalesOrder,
  SalesOrderCreditPreview,
  SalesOrderItem,
  SalesOrderSaveRequest
} from '@/api/sales'
import type { Product } from '@/api/masterdata'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type PricedSalesOrderItem = SalesOrderItem & {
  minPrice?: number | null
  priceLevel?: string | null
}
type SalesOrderForm = SalesOrderSaveRequest & {
  id?: string
  items: PricedSalesOrderItem[]
}

export const useSalesOrderForm = (
  t: Translate,
  options: {
    products: Ref<Product[]>
    getOrder: (id: string | number) => Promise<SalesOrder>
    createOrder: (payload: SalesOrderSaveRequest) => Promise<unknown>
    updateOrder: (id: string | number, payload: SalesOrderSaveRequest) => Promise<unknown>
    previewCredit: (customerId: string | number, items: SalesOrderItem[]) => Promise<SalesOrderCreditPreview>
    resolvePrice: (params: {
      productId: string | number
      customerId?: string | number
      bizDate?: string
    }) => Promise<{
      matched?: boolean
      listPrice?: number | null
      minPrice?: number | null
      matchLevel?: string | null
    }>
    formatBusinessDate: () => string
    formatMoney: (value?: number) => string
    lineAmount: (row: SalesOrderItem) => number
    onSuccess?: Notify
    onError?: Notify
    onWarning?: Notify
    onCompleted?: () => void
  }
) => {
  const submitLoading = ref(false)
  const dialogVisible = ref(false)
  const dialogTitle = ref('')
  const isView = ref(false)
  const formRef = ref<FormInstance>()
  const creditPreviewLoading = ref(false)
  const creditPreview = ref<SalesOrderCreditPreview>()
  let creditPreviewTimer: ReturnType<typeof setTimeout> | undefined
  let creditPreviewRequestId = 0

  const formData = reactive<SalesOrderForm>({
    customerId: '',
    warehouseId: '',
    orderDate: '',
    deliveryDate: '',
    contractId: undefined,
    remark: '',
    items: []
  })

  const formRules = computed<FormRules>(() => ({
    customerId: [{ required: true, message: t('salesOrder.validation.customer'), trigger: 'change' }],
    warehouseId: [{ required: true, message: t('salesOrder.validation.warehouse'), trigger: 'change' }],
    orderDate: [{ required: true, message: t('salesOrder.validation.orderDate'), trigger: 'change' }]
  }))

  const clearCreditPreviewTimer = () => {
    if (!creditPreviewTimer) return
    clearTimeout(creditPreviewTimer)
    creditPreviewTimer = undefined
  }

  const resetForm = () => {
    clearCreditPreviewTimer()
    creditPreviewRequestId += 1
    creditPreviewLoading.value = false
    creditPreview.value = undefined
    formRef.value?.clearValidate()
    Object.assign(formData, {
      id: undefined,
      customerId: '',
      warehouseId: '',
      orderDate: '',
      deliveryDate: '',
      contractId: undefined,
      remark: '',
      items: []
    })
  }

  const addLine = () => {
    formData.items.push({
      productId: '',
      quantity: 1,
      price: 0,
      taxRate: 0,
      amount: 0,
      minPrice: null,
      priceLevel: null
    } as PricedSalesOrderItem)
  }

  const removeLine = (index: number) => {
    formData.items.splice(index, 1)
  }

  const fillForm = async (id: string | number) => {
    const order = await options.getOrder(id)
    Object.assign(formData, {
      id: order.id,
      customerId: order.customerId,
      warehouseId: order.warehouseId || '',
      orderDate: order.orderDate,
      deliveryDate: order.deliveryDate || '',
      contractId: order.contractId,
      remark: order.remark || '',
      items: order.items.map((item) => ({
        ...item,
        quantity: item.quantity ?? item.qty ?? 0,
        price: item.price ?? 0,
        taxRate: item.taxRate ?? 0
      }))
    })
  }

  const handleCreate = () => {
    resetForm()
    dialogTitle.value = t('salesOrder.dialog.create')
    isView.value = false
    formData.orderDate = options.formatBusinessDate()
    addLine()
    dialogVisible.value = true
  }

  const handleEdit = async (row: SalesOrder) => {
    dialogTitle.value = t('salesOrder.dialog.edit')
    isView.value = false
    await fillForm(row.id)
    dialogVisible.value = true
  }

  const handleCopy = async (row: SalesOrder) => {
    dialogTitle.value = t('salesOrder.dialog.copy')
    isView.value = false
    await fillForm(row.id)
    formData.id = undefined as any
    formData.orderDate = options.formatBusinessDate()
    formData.remark = (formData.remark ? formData.remark + ' ' : '')
      + `(${t('salesOrder.dialog.copiedFrom', { orderNo: row.orderNo })})`
    dialogVisible.value = true
  }

  const handleView = async (row: SalesOrder) => {
    dialogTitle.value = t('salesOrder.dialog.view')
    isView.value = true
    await fillForm(row.id)
    dialogVisible.value = true
  }

  const buildCreditPreviewItems = (): SalesOrderItem[] => (
    formData.items
      .filter((item) => item.productId && Number(item.quantity) > 0)
      .map((item) => ({
        productId: item.productId,
        quantity: Number(item.quantity ?? 0),
        price: Number(item.price ?? 0),
        taxRate: Number(item.taxRate ?? 0),
        amount: options.lineAmount(item),
        remark: item.remark
      }))
  )

  const loadCreditPreview = async () => {
    if (!dialogVisible.value || isView.value || !formData.customerId) {
      creditPreviewLoading.value = false
      creditPreview.value = undefined
      return
    }
    const requestId = ++creditPreviewRequestId
    creditPreviewLoading.value = true
    try {
      creditPreview.value = await options.previewCredit(formData.customerId, buildCreditPreviewItems())
    } catch {
      if (requestId === creditPreviewRequestId) {
        creditPreview.value = undefined
      }
    } finally {
      if (requestId === creditPreviewRequestId) {
        creditPreviewLoading.value = false
      }
    }
  }

  const scheduleCreditPreviewReload = () => {
    clearCreditPreviewTimer()
    if (!dialogVisible.value || isView.value || !formData.customerId) {
      creditPreviewLoading.value = false
      creditPreview.value = undefined
      return
    }
    creditPreviewTimer = setTimeout(() => {
      void loadCreditPreview()
    }, 250)
  }

  const applyResolvedPrice = async (line: PricedSalesOrderItem) => {
    if (!line.productId) {
      line.minPrice = null
      line.priceLevel = null
      return
    }
    const product = options.products.value.find((item) => String(item.id) === String(line.productId))
    const fallback = Number(product?.salePrice ?? product?.unitPrice ?? 0)
    try {
      const resolved = await options.resolvePrice({
        productId: line.productId,
        customerId: formData.customerId || undefined,
        bizDate: formData.orderDate || undefined
      })
      if (resolved.matched) {
        line.price = Number(resolved.listPrice ?? fallback)
        line.minPrice = resolved.minPrice != null ? Number(resolved.minPrice) : null
        line.priceLevel = resolved.matchLevel || null
        return
      }
    } catch {
      // Fallback to product sale price when resolve fails.
    }
    line.price = fallback
    line.minPrice = null
    line.priceLevel = null
  }

  const handleAuxQtyChange = (index: number) => {
    const item: any = formData.items[index]
    if (!item?.auxUnitName || item.conversionFactor == null || item.auxQty == null) return
    item.quantity = Number((Number(item.auxQty) * Number(item.conversionFactor)).toFixed(4))
    item.qty = item.quantity
    if (item.price != null) {
      item.amount = Number((Number(item.quantity) * Number(item.price)).toFixed(2))
    }
  }

  const onProductChange = async (line: PricedSalesOrderItem) => {
    await applyResolvedPrice(line)
  }

  const onCustomerOrDateChange = async () => {
    if (isView.value) return
    await Promise.all(
      formData.items
        .filter((item) => item.productId)
        .map((item) => applyResolvedPrice(item as PricedSalesOrderItem))
    )
  }

  const handleSave = async () => {
    if (!formRef.value) return
    await formRef.value.validate(async (valid) => {
      if (!valid) return
      const validLines = formData.items.filter((item) => item.productId && item.quantity > 0)
      if (validLines.length === 0) {
        options.onWarning?.(t('salesOrder.validation.lineRequired'))
        return
      }
      for (let i = 0; i < validLines.length; i++) {
        const line = validLines[i] as PricedSalesOrderItem
        if (line.minPrice != null && Number(line.price) < Number(line.minPrice)) {
          options.onWarning?.(t('salesOrder.validation.belowMinimum', {
            line: i + 1,
            amount: options.formatMoney(line.minPrice)
          }))
          return
        }
      }
      submitLoading.value = true
      try {
        const payload = { ...formData, items: validLines }
        if (formData.id) {
          await options.updateOrder(formData.id, payload)
          options.onSuccess?.(t('salesOrder.message.updated'))
        } else {
          await options.createOrder(payload)
          options.onSuccess?.(t('salesOrder.message.created'))
        }
        dialogVisible.value = false
        options.onCompleted?.()
      } catch (error) {
        if (!(error instanceof Error)) {
          options.onError?.(t('salesOrder.message.saveFailed'))
        }
      } finally {
        submitLoading.value = false
      }
    })
  }

  const creditExceededAmount = computed(() => (
    creditPreview.value?.projectedAvailableCredit != null && creditPreview.value.projectedAvailableCredit < 0
      ? Math.abs(creditPreview.value.projectedAvailableCredit)
      : 0
  ))

  const creditPreviewSignature = computed(() => JSON.stringify({
    dialogVisible: dialogVisible.value,
    isView: isView.value,
    customerId: formData.customerId || '',
    items: formData.items.map((item) => ({
      productId: item.productId || '',
      quantity: Number(item.quantity ?? 0),
      price: Number(item.price ?? 0),
      taxRate: Number(item.taxRate ?? 0),
      remark: item.remark || ''
    }))
  }))

  watch(creditPreviewSignature, () => {
    scheduleCreditPreviewReload()
  })

  if (getCurrentInstance()) {
    onBeforeUnmount(() => {
      clearCreditPreviewTimer()
    })
  }

  return {
    addLine,
    applyResolvedPrice,
    clearCreditPreviewTimer,
    creditExceededAmount,
    creditPreview,
    creditPreviewLoading,
    dialogTitle,
    dialogVisible,
    formData,
    formRef,
    formRules,
    handleAuxQtyChange,
    handleCopy,
    handleCreate,
    handleEdit,
    handleSave,
    handleView,
    isView,
    onCustomerOrDateChange,
    onProductChange,
    removeLine,
    resetForm,
    submitLoading
  }
}
