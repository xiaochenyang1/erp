import { computed, reactive, ref, type Ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

import type {
  PurchaseOrder,
  PurchaseOrderItem,
  PurchaseOrderSaveRequest
} from '@/api/purchase'
import type { Product } from '@/api/masterdata'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type PriceLine = PurchaseOrderItem & {
  maxPrice?: number | null
  priceLevel?: string | null
  auxQty?: number
  auxUnitName?: string
  conversionFactor?: number
}
type ResolvePrice = (params: {
  productId: string | number
  supplierId?: string | number
  bizDate?: string
}) => Promise<{
  matched?: boolean
  listPrice?: number | null
  maxPrice?: number | null
  matchLevel?: string | null
}>

export const usePurchaseOrderForm = (
  t: Translate,
  options: {
    products: Ref<Product[]>
    getOrder: (id: string | number) => Promise<PurchaseOrder>
    createOrder: (payload: PurchaseOrderSaveRequest) => Promise<unknown>
    updateOrder: (id: string | number, payload: PurchaseOrderSaveRequest) => Promise<unknown>
    resolvePrice: ResolvePrice
    formatBusinessDate: () => string
    onSuccess?: Notify
    onError?: Notify
    onWarning?: Notify
    onCompleted?: () => void
  }
) => {
  const dialogVisible = ref(false)
  const editId = ref<string | number>()
  const formRef = ref<FormInstance>()
  const submitLoading = ref(false)
  const form = reactive<PurchaseOrderSaveRequest>({
    supplierId: '',
    orderDate: '',
    expectedDate: '',
    items: [],
    remark: ''
  })

  const dialogTitle = computed(() => (
    editId.value ? t('purchaseOrder.dialog.edit') : t('purchaseOrder.dialog.create')
  ))

  const formRules = computed<FormRules>(() => ({
    supplierId: [{ required: true, message: t('purchaseOrder.validation.supplier'), trigger: 'change' }],
    orderDate: [{ required: true, message: t('purchaseOrder.validation.orderDate'), trigger: 'change' }]
  }))

  const orderTotal = computed(() =>
    form.items.reduce((sum, item) => sum + Number(item.amount || 0), 0)
  )

  const calculateAmount = (item: PurchaseOrderItem) => {
    item.amount = Number(item.quantity || 0) * Number(item.price || 0)
  }

  const resetForm = () => {
    form.supplierId = ''
    form.orderDate = ''
    form.expectedDate = ''
    form.items = []
    form.remark = ''
    formRef.value?.resetFields()
  }

  const handleAdd = () => {
    editId.value = undefined
    resetForm()
    dialogVisible.value = true
  }

  const handleEdit = (row: PurchaseOrder) => {
    editId.value = row.id
    Object.assign(form, {
      supplierId: row.supplierId,
      orderDate: row.orderDate,
      expectedDate: row.expectedDate,
      items: (row.items || []).map((item) => ({ ...item })),
      remark: row.remark
    })
    dialogVisible.value = true
  }

  const handleCopy = async (row: PurchaseOrder) => {
    try {
      const detail = await options.getOrder(row.id)
      editId.value = undefined
      Object.assign(form, {
        supplierId: detail.supplierId,
        orderDate: options.formatBusinessDate(),
        expectedDate: detail.expectedDate || detail.deliveryDate || '',
        remark: t('purchaseOrder.dialog.copiedFrom', { orderNo: detail.orderNo })
          + (detail.remark ? `; ${detail.remark}` : ''),
        items: (detail.items || detail.lines || []).map((item: any) => ({
          productId: item.productId,
          auxQty: item.auxQty != null ? Number(item.auxQty) : undefined,
          auxUnitName: item.auxUnitName || '',
          conversionFactor: item.conversionFactor != null ? Number(item.conversionFactor) : undefined,
          quantity: Number(item.quantity ?? item.qty ?? 0),
          qty: Number(item.quantity ?? item.qty ?? 0),
          price: Number(item.price ?? 0),
          taxRate: Number(item.taxRate ?? 0),
          amount: Number(item.amount ?? 0),
          remark: item.remark || ''
        }))
      })
      dialogVisible.value = true
    } catch {
      options.onError?.(t('purchaseOrder.message.copyFailed'))
    }
  }

  const handleAddItem = () => {
    form.items.push({
      productId: '',
      auxQty: undefined,
      auxUnitName: '',
      conversionFactor: undefined,
      quantity: 1,
      price: 0,
      amount: 0,
      taxRate: 0
    } as PurchaseOrderItem)
  }

  const handleRemoveItem = (index: number) => {
    form.items.splice(index, 1)
  }

  const handleAuxQtyChange = (index: number) => {
    const item = form.items[index] as PriceLine
    if (!item?.auxUnitName || item.conversionFactor == null || item.auxQty == null) return
    item.quantity = Number((Number(item.auxQty) * Number(item.conversionFactor)).toFixed(4))
    item.qty = item.quantity
    calculateAmount(item)
  }

  const applyResolvedPrice = async (item: PriceLine) => {
    if (!item.productId) {
      item.maxPrice = null
      item.priceLevel = null
      return
    }
    try {
      const resolved = await options.resolvePrice({
        productId: item.productId,
        supplierId: form.supplierId || undefined,
        bizDate: form.orderDate || undefined
      })
      if (resolved.matched) {
        if (resolved.listPrice != null) {
          item.price = Number(resolved.listPrice)
        }
        item.maxPrice = resolved.maxPrice != null ? Number(resolved.maxPrice) : null
        item.priceLevel = resolved.matchLevel || null
        calculateAmount(item)
        return
      }
    } catch {
      // Keep the product master price when resolve fails.
    }
    item.maxPrice = null
    item.priceLevel = null
  }

  const handleProductChange = async (index: number) => {
    const item = form.items[index] as PriceLine
    const product = options.products.value.find(
      (candidate) => String(candidate.id) === String(item.productId)
    )
    item.productCode = product?.productCode
    item.productName = product?.productName
    item.price = Number(product?.purchasePrice ?? 0)
    item.taxRate = Number(product?.taxRate ?? 0) > 1
      ? Number(product?.taxRate ?? 0) / 100
      : Number(product?.taxRate ?? 0)
    calculateAmount(item)
    await applyResolvedPrice(item)
  }

  const handleSubmitForm = async () => {
    if (!formRef.value) return

    await formRef.value.validate(async (valid) => {
      if (!valid) return

      if (form.items.length === 0) {
        options.onWarning?.(t('purchaseOrder.validation.details'))
        return
      }

      submitLoading.value = true
      try {
        if (editId.value) {
          await options.updateOrder(editId.value, form)
          options.onSuccess?.(t('purchaseOrder.message.updated'))
        } else {
          await options.createOrder(form)
          options.onSuccess?.(t('purchaseOrder.message.created'))
        }
        dialogVisible.value = false
        options.onCompleted?.()
      } catch {
        options.onError?.(
          editId.value
            ? t('purchaseOrder.message.updateFailed')
            : t('purchaseOrder.message.createFailed')
        )
      } finally {
        submitLoading.value = false
      }
    })
  }

  return {
    applyResolvedPrice,
    calculateAmount,
    dialogTitle,
    dialogVisible,
    editId,
    form,
    formRef,
    formRules,
    handleAdd,
    handleAddItem,
    handleAuxQtyChange,
    handleCopy,
    handleEdit,
    handleProductChange,
    handleRemoveItem,
    handleSubmitForm,
    orderTotal,
    resetForm,
    submitLoading
  }
}
