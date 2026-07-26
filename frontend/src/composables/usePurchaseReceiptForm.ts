import { computed, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

import type {
  PurchaseOrder,
  PurchaseReceipt,
  PurchaseReceiptCreateRequest,
  PurchaseReceiptItem
} from '@/api/purchase'
import type { PageResponse } from '@/types/common'
import { incrementScannedLine } from '@/utils/barcode'
import {
  hydrateProductLineLabels,
  validateProductControlLines
} from '@/utils/productLines'

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
type LoadProduct = (id: string | number) => Promise<{
  id?: string | number
  productCode?: string
  productName?: string
  lotControlled?: boolean
  shelfLifeControlled?: boolean
  serialControlled?: boolean
  [key: string]: unknown
}>
type LoadProductByBarcode = (barcode: string) => Promise<{
  id: string | number
  productCode?: string
  productName?: string
}>

export const usePurchaseReceiptForm = (
  t: Translate,
  options: {
    getApprovedOrders: (params: {
      pageNo: number
      pageSize: number
      status: string
    }) => Promise<PageResponse<PurchaseOrder>>
    getOrder: (id: string | number) => Promise<PurchaseOrder>
    getReceipt: (id: string | number) => Promise<PurchaseReceipt>
    createReceipt: (payload: PurchaseReceiptCreateRequest) => Promise<unknown>
    updateReceipt: (
      id: string | number,
      payload: PurchaseReceiptCreateRequest
    ) => Promise<unknown>
    loadProduct: LoadProduct
    loadProductByBarcode: LoadProductByBarcode
    loadLocations: (warehouseId?: string | number) => void | Promise<void>
    confirm: Confirm
    onSuccess?: Notify
    onError?: Notify
    onWarning?: Notify
    onCompleted?: () => void
  }
) => {
  const dialogVisible = ref(false)
  const editingId = ref<string | number>('')
  const formRef = ref<FormInstance>()
  const submitLoading = ref(false)
  const scanLoading = ref(false)
  const scanFeedback = ref('')
  const availableOrders = ref<PurchaseOrder[]>([])
  const form = reactive<PurchaseReceiptCreateRequest>({
    orderId: '',
    warehouseId: '',
    receiptDate: '',
    items: [],
    remark: ''
  })

  const formRules = computed<FormRules>(() => ({
    orderId: [{ required: true, message: t('purchaseReceipt.validation.order'), trigger: 'change' }],
    warehouseId: [{ required: true, message: t('purchaseReceipt.validation.warehouse'), trigger: 'change' }],
    receiptDate: [{ required: true, message: t('purchaseReceipt.validation.date'), trigger: 'change' }]
  }))

  const receiptQuantityTotal = computed(() => form.items.reduce(
    (total, item) => total + Number(item.quantity || 0),
    0
  ))

  const resetForm = () => {
    editingId.value = ''
    form.orderId = ''
    form.warehouseId = ''
    form.receiptDate = ''
    form.items = []
    form.remark = ''
    scanFeedback.value = ''
    formRef.value?.resetFields()
  }

  const handleAdd = async () => {
    try {
      const res = await options.getApprovedOrders({
        pageNo: 1,
        pageSize: 100,
        status: 'APPROVED'
      })
      availableOrders.value = res.records || []

      if (availableOrders.value.length === 0) {
        options.onWarning?.(t('purchaseReceipt.message.noAvailableOrders'))
        return
      }

      resetForm()
      dialogVisible.value = true
    } catch {
      options.onError?.(t('purchaseReceipt.message.ordersLoadFailed'))
    }
  }

  const handleEdit = async (row: PurchaseReceipt) => {
    try {
      const detail = await options.getReceipt(row.id)
      editingId.value = detail.id
      availableOrders.value = [{
        id: detail.orderId,
        orderNo: detail.orderNo,
        supplierName: detail.supplierName
      } as PurchaseOrder]
      form.orderId = detail.orderId
      form.warehouseId = detail.warehouseId
      form.receiptDate = detail.receiptDate
      form.remark = detail.remark || ''
      const receiptItems = (detail.items || detail.lines || []).map((item) => ({
        orderItemId: item.orderItemId,
        orderLineId: item.orderLineId ?? item.orderItemId,
        productId: item.productId,
        productCode: item.productCode,
        productName: item.productName,
        orderedQuantity: item.orderedQuantity ?? item.quantity,
        receivedQuantity: 0,
        quantity: item.quantity,
        qty: item.qty ?? item.quantity,
        locationId: item.locationId ?? undefined,
        serialNos: item.serialNos || '',
        lotNo: item.lotNo || '',
        productionDate: item.productionDate || '',
        expiryDate: item.expiryDate || '',
        remark: item.remark || ''
      }))
      form.items = await hydrateProductLineLabels(receiptItems, options.loadProduct)
      await options.loadLocations(form.warehouseId)
      dialogVisible.value = true
    } catch {
      options.onError?.(t('purchaseReceipt.message.receiptLoadFailed'))
    }
  }

  const handleOrderChange = async (orderId: string | number) => {
    const summary = availableOrders.value.find((order) => String(order.id) === String(orderId))
    const order = summary?.items?.length ? summary : await options.getOrder(orderId)
    if (!order) return

    const orderItems = (order.items || []).map((item) => ({
      orderItemId: item.id,
      orderLineId: item.id,
      productId: item.productId,
      productCode: item.productCode,
      productName: item.productName,
      orderedQuantity: item.quantity,
      receivedQuantity: item.receivedQty || 0,
      quantity: Math.max(0, item.quantity - (item.receivedQty || 0)),
      auxUnitName: item.auxUnitName || '',
      conversionFactor: item.conversionFactor != null ? Number(item.conversionFactor) : undefined,
      locationId: undefined,
      serialNos: '',
      lotNo: '',
      productionDate: '',
      expiryDate: '',
      remark: ''
    }))
    form.items = await hydrateProductLineLabels(orderItems, options.loadProduct)
  }

  const getReceiptMaximum = (item: PurchaseReceiptItem) => Math.max(
    0,
    Number(item.orderedQuantity || 0) - Number(item.receivedQuantity || 0)
  )

  const resetScanQuantities = async () => {
    try {
      await options.confirm(
        t('purchaseReceipt.scan.resetConfirm'),
        t('purchaseReceipt.scan.title'),
        {
          confirmButtonText: t('purchaseReceipt.scan.reset'),
          cancelButtonText: t('common.cancel'),
          type: 'warning'
        }
      )
      form.items.forEach((item) => {
        item.quantity = 0
        item.qty = 0
      })
      scanFeedback.value = t('purchaseReceipt.scan.resetDone')
    } catch (error: any) {
      if (error !== 'cancel' && error?.action !== 'cancel') {
        options.onError?.(t('purchaseReceipt.scan.resetFailed'))
      }
    }
  }

  const handleBarcodeScan = async (barcode: string) => {
    if (!form.orderId || form.items.length === 0) {
      options.onWarning?.(t('purchaseReceipt.scan.selectOrderFirst'))
      return
    }

    scanLoading.value = true
    try {
      const product = await options.loadProductByBarcode(barcode)
      const result = incrementScannedLine(form.items, product.id, getReceiptMaximum)
      if (result.status === 'not-found') {
        options.onWarning?.(t('purchaseReceipt.scan.notInOrder', { code: product.productCode }))
        return
      }
      if (result.status === 'at-maximum') {
        options.onWarning?.(t('purchaseReceipt.scan.atMaximum', { code: product.productCode }))
        return
      }
      form.items[result.index].qty = result.quantity
      scanFeedback.value = `${product.productCode} · ${result.quantity}`
    } catch (error) {
      options.onWarning?.(
        error instanceof Error ? error.message : t('purchaseReceipt.scan.lookupFailed')
      )
    } finally {
      scanLoading.value = false
    }
  }

  const handleSubmitForm = async () => {
    if (!formRef.value) return

    await formRef.value.validate(async (valid) => {
      if (!valid) return

      if (form.items.length === 0) {
        options.onWarning?.(t('purchaseReceipt.validation.order'))
        return
      }

      const controlIssues = validateProductControlLines(form.items)
      if (controlIssues.length > 0) {
        const issue = controlIssues[0]
        const product = issue.productCode || issue.productName || String(issue.productId)
        options.onWarning?.(t(`purchaseReceipt.validation.${issue.messageKey}`, {
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
          await options.updateReceipt(editingId.value, form)
          options.onSuccess?.(t('purchaseReceipt.message.updated'))
        } else {
          await options.createReceipt(form)
          options.onSuccess?.(t('purchaseReceipt.message.created'))
        }
        dialogVisible.value = false
        options.onCompleted?.()
      } catch {
        options.onError?.(
          editingId.value
            ? t('purchaseReceipt.message.updateFailed')
            : t('purchaseReceipt.message.createFailed')
        )
      } finally {
        submitLoading.value = false
      }
    })
  }

  const handleWarehouseChange = async (warehouseId?: string | number) => {
    form.items.forEach((item) => {
      item.locationId = undefined
    })
    await options.loadLocations(warehouseId)
  }

  return {
    availableOrders,
    dialogVisible,
    editingId,
    form,
    formRef,
    formRules,
    getReceiptMaximum,
    handleAdd,
    handleBarcodeScan,
    handleEdit,
    handleOrderChange,
    handleSubmitForm,
    handleWarehouseChange,
    receiptQuantityTotal,
    resetForm,
    resetScanQuantities,
    scanFeedback,
    scanLoading,
    submitLoading
  }
}
