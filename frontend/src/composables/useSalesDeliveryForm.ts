import { computed, reactive, ref, type Ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

import type {
  SalesDelivery,
  SalesDeliveryCreateRequest,
  SalesDeliveryItem,
  SalesOrder
} from '@/api/sales'
import {
  hydrateProductLineLabels,
  validateProductControlLines
} from '@/utils/productLines'
import { incrementScannedLine } from '@/utils/barcode'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title: string,
  options?: {
    confirmButtonText?: string
    cancelButtonText?: string
    type?: string
  }
) => Promise<unknown>
type LoadProduct = (id: string | number) => Promise<any>
type LoadProductByBarcode = (barcode: string) => Promise<{
  id: string | number
  productCode?: string
}>

export const useSalesDeliveryForm = (
  t: Translate,
  options: {
    orders: Ref<SalesOrder[]>
    getDelivery: (id: string | number) => Promise<SalesDelivery>
    getOrder: (id: string | number) => Promise<SalesOrder>
    createDelivery: (payload: SalesDeliveryCreateRequest) => Promise<unknown>
    updateDelivery: (id: string | number, payload: SalesDeliveryCreateRequest) => Promise<unknown>
    loadProduct: LoadProduct
    loadProductByBarcode: LoadProductByBarcode
    loadLocations: (warehouseId?: string | number) => void | Promise<void>
    formatBusinessDate: () => string
    confirm: Confirm
    onSuccess?: Notify
    onError?: Notify
    onWarning?: Notify
    onCompleted?: () => void
  }
) => {
  const dialogVisible = ref(false)
  const submitLoading = ref(false)
  const scanLoading = ref(false)
  const scanFeedback = ref('')
  const dialogTitle = ref('')
  const isView = ref(false)
  const editingId = ref<string | number>('')
  const formRef = ref<FormInstance>()
  const formData = reactive<SalesDeliveryCreateRequest>({
    orderId: 0,
    warehouseId: 0,
    deliveryDate: '',
    items: [],
    remark: '',
    carrierName: '',
    trackingNo: '',
    logisticsStatus: 'PENDING_SHIP'
  })

  const formRules = computed<FormRules>(() => ({
    orderId: [{ required: true, message: t('salesDelivery.validation.order'), trigger: 'change' }],
    warehouseId: [{ required: true, message: t('salesDelivery.validation.warehouse'), trigger: 'change' }],
    deliveryDate: [{ required: true, message: t('salesDelivery.validation.date'), trigger: 'change' }]
  }))

  const deliveryQuantityTotal = computed(() => formData.items.reduce(
    (total, item) => total + Number(item.quantity || 0),
    0
  ))

  const resetForm = () => {
    editingId.value = ''
    isView.value = false
    formData.orderId = 0
    formData.warehouseId = 0
    formData.deliveryDate = options.formatBusinessDate()
    formData.items = []
    formData.remark = ''
    formData.carrierName = ''
    formData.trackingNo = ''
    formData.logisticsStatus = 'PENDING_SHIP'
    scanFeedback.value = ''
    formRef.value?.resetFields()
  }

  const handleWarehouseChange = async (warehouseId?: string | number) => {
    formData.items.forEach((item) => {
      item.locationId = undefined
    })
    await options.loadLocations(warehouseId)
  }

  const handleCreate = () => {
    resetForm()
    dialogTitle.value = t('salesDelivery.dialog.create')
    dialogVisible.value = true
  }

  const handleView = async (row: SalesDelivery) => {
    try {
      const data = await options.getDelivery(row.id)
      dialogTitle.value = t('salesDelivery.dialog.view')
      isView.value = true
      editingId.value = ''
      Object.assign(formData, data)
      dialogVisible.value = true
    } catch {
      options.onError?.(t('salesDelivery.message.detailLoadFailed'))
    }
  }

  const handleEdit = async (row: SalesDelivery) => {
    try {
      const detail = await options.getDelivery(row.id)
      dialogTitle.value = t('salesDelivery.dialog.edit')
      isView.value = false
      editingId.value = detail.id
      let order = options.orders.value.find((o) => String(o.id) === String(detail.orderId))
      if (!order) {
        options.orders.value = [{
          id: detail.orderId,
          orderNo: detail.orderNo,
          customerName: detail.customerName
        } as SalesOrder, ...options.orders.value]
      }
      let orderItems: SalesOrder['items'] = []
      try {
        const orderDetail = await options.getOrder(detail.orderId)
        orderItems = orderDetail.items || []
        order = orderDetail
        const exists = options.orders.value.some((o) => String(o.id) === String(orderDetail.id))
        if (!exists) {
          options.orders.value = [orderDetail, ...options.orders.value]
        }
      } catch {
        // Keep opening the edit dialog even if order detail fails.
      }
      formData.orderId = detail.orderId
      formData.warehouseId = detail.warehouseId
      formData.deliveryDate = detail.deliveryDate
      formData.remark = detail.remark || ''
      formData.carrierName = detail.carrierName || ''
      formData.trackingNo = detail.trackingNo || ''
      formData.logisticsStatus = detail.logisticsStatus || 'PENDING_SHIP'
      const deliveryItems = (detail.items || detail.lines || []).map((item) => {
        const orderLineId = item.orderLineId ?? item.orderItemId
        const orderItem = orderItems.find((oi) => String(oi.id) === String(orderLineId))
        const qty = Number(item.quantity ?? item.qty ?? 0)
        return {
          orderItemId: item.orderItemId ?? item.orderLineId,
          orderLineId,
          productId: item.productId,
          productCode: item.productCode || orderItem?.productCode,
          productName: item.productName || orderItem?.productName,
          orderedQuantity: Number(orderItem?.quantity ?? qty),
          deliveredQuantity: Math.max(0, Number(orderItem?.deliveredQuantity ?? 0)),
          quantity: qty,
          locationId: item.locationId ?? undefined,
          serialNos: item.serialNos || '',
          lotNo: item.lotNo || '',
          productionDate: item.productionDate || '',
          expiryDate: item.expiryDate || '',
          remark: item.remark || ''
        }
      })
      formData.items = await hydrateProductLineLabels(deliveryItems, options.loadProduct)
      await options.loadLocations(formData.warehouseId)
      dialogVisible.value = true
    } catch {
      options.onError?.(t('salesDelivery.message.deliveryLoadFailed'))
    }
  }

  const handleOrderChange = async () => {
    if (!formData.orderId) return
    try {
      const order = await options.getOrder(formData.orderId)
      const orderItems = (order.items || []).map((item) => ({
        orderItemId: item.id,
        productId: item.productId,
        productCode: item.productCode,
        productName: item.productName,
        orderedQuantity: item.quantity,
        deliveredQuantity: item.deliveredQuantity || 0,
        quantity: Math.max(0, item.quantity - (item.deliveredQuantity || 0)),
        auxUnitName: item.auxUnitName || '',
        conversionFactor: item.conversionFactor != null ? Number(item.conversionFactor) : undefined,
        locationId: undefined,
        serialNos: '',
        lotNo: '',
        productionDate: '',
        expiryDate: '',
        remark: ''
      }))
      formData.items = await hydrateProductLineLabels(orderItems, options.loadProduct)
    } catch {
      options.onError?.(t('salesDelivery.message.orderDetailLoadFailed'))
    }
  }

  const getDeliveryMaximum = (item: SalesDeliveryItem) => Math.max(
    0,
    Number(item.orderedQuantity || 0) - Number(item.deliveredQuantity || 0)
  )

  const resetScanQuantities = async () => {
    try {
      await options.confirm(
        t('salesDelivery.scan.resetConfirm'),
        t('salesDelivery.scan.title'),
        {
          confirmButtonText: t('salesDelivery.scan.reset'),
          cancelButtonText: t('common.cancel'),
          type: 'warning'
        }
      )
      formData.items.forEach((item) => {
        item.quantity = 0
      })
      scanFeedback.value = t('salesDelivery.scan.resetDone')
    } catch (error: any) {
      if (error !== 'cancel' && error?.action !== 'cancel') {
        options.onError?.(t('salesDelivery.scan.resetFailed'))
      }
    }
  }

  const handleBarcodeScan = async (barcode: string) => {
    if (!formData.orderId || formData.items.length === 0) {
      options.onWarning?.(t('salesDelivery.scan.selectOrderFirst'))
      return
    }
    scanLoading.value = true
    try {
      const product = await options.loadProductByBarcode(barcode)
      const result = incrementScannedLine(formData.items, product.id, getDeliveryMaximum)
      if (result.status === 'not-found') {
        options.onWarning?.(t('salesDelivery.scan.notInOrder', { code: product.productCode }))
        return
      }
      if (result.status === 'at-maximum') {
        options.onWarning?.(t('salesDelivery.scan.atMaximum', { code: product.productCode }))
        return
      }
      scanFeedback.value = `${product.productCode} · ${result.quantity}`
    } catch (error) {
      options.onWarning?.(
        error instanceof Error ? error.message : t('salesDelivery.scan.lookupFailed')
      )
    } finally {
      scanLoading.value = false
    }
  }

  const handleSubmit = async () => {
    if (!formRef.value) return
    await formRef.value.validate(async (valid) => {
      if (!valid) return
      if (!formData.items.length) {
        options.onWarning?.(t('salesDelivery.validation.order'))
        return
      }

      formData.items = await hydrateProductLineLabels(formData.items, options.loadProduct)
      const controlIssues = validateProductControlLines(formData.items)
      if (controlIssues.length > 0) {
        const issue = controlIssues[0]
        const product = issue.productCode || issue.productName || String(issue.productId)
        options.onWarning?.(t(`salesDelivery.validation.${issue.messageKey}`, {
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
          await options.updateDelivery(editingId.value, formData)
          options.onSuccess?.(t('salesDelivery.message.updated'))
        } else {
          await options.createDelivery(formData)
          options.onSuccess?.(t('salesDelivery.message.created'))
        }
        dialogVisible.value = false
        options.onCompleted?.()
      } catch {
        options.onError?.(
          editingId.value
            ? t('salesDelivery.message.updateFailed')
            : t('salesDelivery.message.createFailed')
        )
      } finally {
        submitLoading.value = false
      }
    })
  }

  return {
    deliveryQuantityTotal,
    dialogTitle,
    dialogVisible,
    editingId,
    formData,
    formRef,
    formRules,
    getDeliveryMaximum,
    handleBarcodeScan,
    handleCreate,
    handleEdit,
    handleOrderChange,
    handleSubmit,
    handleView,
    handleWarehouseChange,
    isView,
    resetForm,
    resetScanQuantities,
    scanFeedback,
    scanLoading,
    submitLoading
  }
}
