import { reactive, ref, type Ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

import type {
  SalesDelivery,
  SalesReturn,
  SalesReturnCreateRequest
} from '@/api/sales'
import type { Product } from '@/api/masterdata'
import {
  hydrateProductLineLabels,
  validateProductControlLines
} from '@/utils/productLines'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export const useSalesReturnForm = (
  t: Translate,
  options: {
    products: Ref<Product[]>
    deliveries: Ref<SalesDelivery[]>
    getDelivery: (id: string | number) => Promise<SalesDelivery>
    getReturn: (id: string | number) => Promise<SalesReturn>
    createReturn: (payload: SalesReturnCreateRequest) => Promise<unknown>
    updateReturn: (id: string | number, payload: SalesReturnCreateRequest) => Promise<unknown>
    formatBusinessDate: () => string
    onSuccess?: Notify
    onError?: Notify
    onWarning?: Notify
    onCompleted?: () => void
  }
) => {
  const dialogVisible = ref(false)
  const submitLoading = ref(false)
  const dialogTitle = ref('')
  const isView = ref(false)
  const editingId = ref<string | number>('')
  const formRef = ref<FormInstance>()
  const formData = reactive<SalesReturnCreateRequest>({
    deliveryId: '',
    returnDate: '',
    items: [],
    remark: ''
  })

  const formRules: FormRules = {
    deliveryId: [{ required: true, message: t('salesReturnOps.validation.salesDelivery'), trigger: 'change' }],
    returnDate: [{ required: true, message: t('salesReturnOps.validation.returnDate'), trigger: 'change' }]
  }

  const totalQuantity = () =>
    formData.items.reduce((sum, item) => sum + Number(item.quantity || 0), 0)

  const totalAmount = () =>
    formData.items.reduce((sum, item) => sum + Number(item.amount || 0), 0)

  const productInfoById = (productId: string | number) => {
    const product = options.products.value.find((item) => String(item.id) === String(productId))
    return {
      productCode: product?.code || product?.productCode || '',
      productName: product?.name || product?.productName || ''
    }
  }

  const resetForm = () => {
    editingId.value = ''
    isView.value = false
    formData.deliveryId = ''
    formData.returnDate = options.formatBusinessDate()
    formData.items = []
    formData.remark = ''
    formRef.value?.clearValidate()
  }

  const handleCreate = () => {
    resetForm()
    dialogTitle.value = t('salesReturnOps.dialog.create')
    dialogVisible.value = true
  }

  const handleView = async (row: SalesReturn) => {
    try {
      const data = await options.getReturn(row.id)
      dialogTitle.value = t('salesReturnOps.dialog.view')
      isView.value = true
      editingId.value = ''
      Object.assign(formData, data)
      dialogVisible.value = true
    } catch {
      options.onError?.(t('salesReturnOps.message.detailLoadFailed'))
    }
  }

  const handleEdit = async (row: SalesReturn) => {
    try {
      const detail = await options.getReturn(row.id)
      dialogTitle.value = t('salesReturnOps.dialog.edit')
      isView.value = false
      editingId.value = detail.id
      const existing = options.deliveries.value.find((d) => String(d.id) === String(detail.deliveryId))
      if (!existing) {
        options.deliveries.value = [{
          id: detail.deliveryId,
          deliveryNo: detail.deliveryNo,
          customerName: detail.customerName,
          warehouseName: detail.warehouseName
        } as SalesDelivery, ...options.deliveries.value]
      }
      formData.deliveryId = detail.deliveryId
      formData.returnDate = detail.returnDate
      formData.remark = detail.remark || ''
      formData.items = (detail.items || detail.lines || []).map((item) => ({
        deliveryLineId: item.deliveryLineId,
        orderLineId: item.orderLineId,
        productId: item.productId,
        productCode: item.productCode,
        productName: item.productName,
        quantity: Number(item.quantity ?? item.qty ?? 0),
        price: Number(item.price ?? 0),
        taxRate: Number(item.taxRate ?? 0),
        amount: Number(item.amount ?? 0),
        taxAmount: Number(item.taxAmount ?? 0),
        locationId: item.locationId ?? undefined,
        serialNos: item.serialNos || '',
        lotNo: item.lotNo || '',
        productionDate: item.productionDate || '',
        expiryDate: item.expiryDate || '',
        reason: item.reason || item.remark || ''
      }))
      formData.items = await hydrateProductLineLabels(formData.items, async (productId) => {
        const product = options.products.value.find((item) => String(item.id) === String(productId))
        return product || {}
      })
      dialogVisible.value = true
    } catch {
      options.onError?.(t('salesReturnOps.message.returnLoadFailed'))
    }
  }

  const handleDeliveryChange = async () => {
    if (!formData.deliveryId) {
      formData.items = []
      return
    }
    try {
      const delivery = await options.getDelivery(formData.deliveryId)
      formData.items = (delivery.items || []).map((item) => ({
        ...productInfoById(item.productId),
        deliveryLineId: item.id,
        orderLineId: item.orderLineId,
        productId: item.productId,
        productCode: item.productCode || productInfoById(item.productId).productCode,
        productName: item.productName || productInfoById(item.productId).productName,
        quantity: item.quantity - (item.returnedQty || 0),
        price: item.price || 0,
        taxRate: item.taxRate || 0,
        amount: (item.quantity - (item.returnedQty || 0)) * (item.price || 0),
        taxAmount: 0,
        locationId: item.locationId ?? undefined,
        serialNos: item.serialNos || '',
        lotNo: item.lotNo || '',
        productionDate: item.productionDate || '',
        expiryDate: item.expiryDate || '',
        reason: ''
      })).filter((item) => item.quantity > 0)
      formData.items = await hydrateProductLineLabels(formData.items, async (productId) => {
        const product = options.products.value.find((item) => String(item.id) === String(productId))
        return product || {}
      })
    } catch {
      options.onError?.(t('salesReturnOps.message.deliveryDetailLoadFailed'))
    }
  }

  const handleDeleteItem = (index: number) => {
    formData.items.splice(index, 1)
  }

  const handleQuantityChange = (index: number) => {
    const item = formData.items[index]
    item.amount = (item.quantity || 0) * (item.price || 0)
  }

  const handleSubmit = async () => {
    if (!formRef.value) return
    await formRef.value.validate(async (valid) => {
      if (!valid) return
      if (formData.items.length === 0) {
        options.onWarning?.(t('salesReturnOps.validation.itemRequired'))
        return
      }
      const hasQuantity = formData.items.some((item) => item.quantity > 0)
      if (!hasQuantity) {
        options.onWarning?.(t('salesReturnOps.validation.quantityRequired'))
        return
      }

      formData.items = await hydrateProductLineLabels(formData.items, async (productId) => {
        const product = options.products.value.find((item) => String(item.id) === String(productId))
        return product || {}
      })
      const controlIssues = validateProductControlLines(formData.items)
      if (controlIssues.length > 0) {
        const issue = controlIssues[0]
        const product = issue.productCode || issue.productName || String(issue.productId)
        options.onWarning?.(t(`salesReturnOps.validation.${issue.messageKey}`, {
          line: issue.index + 1,
          product,
          expected: issue.expectedSerialCount,
          actual: issue.actualSerialCount
        }))
        return
      }

      submitLoading.value = true
      try {
        if (editingId.value) {
          await options.updateReturn(editingId.value, formData)
        } else {
          await options.createReturn(formData)
        }
        options.onSuccess?.(t('salesReturnOps.message.success'))
        dialogVisible.value = false
        options.onCompleted?.()
      } catch {
        options.onError?.(
          t(editingId.value ? 'salesReturnOps.message.updateFailed' : 'salesReturnOps.message.failed')
        )
      } finally {
        submitLoading.value = false
      }
    })
  }

  return {
    dialogTitle,
    dialogVisible,
    editingId,
    formData,
    formRef,
    formRules,
    handleCreate,
    handleDeleteItem,
    handleDeliveryChange,
    handleEdit,
    handleQuantityChange,
    handleSubmit,
    handleView,
    isView,
    resetForm,
    submitLoading,
    totalAmount,
    totalQuantity
  }
}
