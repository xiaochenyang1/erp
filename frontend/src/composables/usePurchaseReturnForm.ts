import { computed, reactive, ref, type Ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

import type {
  PurchaseReceipt,
  PurchaseReturn,
  PurchaseReturnCreateRequest
} from '@/api/purchase'
import type { Product } from '@/api/masterdata'
import {
  hydrateProductLineLabels,
  validateProductControlLines
} from '@/utils/productLines'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export const usePurchaseReturnForm = (
  t: Translate,
  options: {
    products: Ref<Product[]>
    availableReceipts: Ref<PurchaseReceipt[]>
    loadCreateOptions: () => Promise<PurchaseReceipt[]>
    getReceipt: (id: string | number) => Promise<PurchaseReceipt>
    getReturn: (id: string | number) => Promise<PurchaseReturn>
    createReturn: (payload: PurchaseReturnCreateRequest) => Promise<unknown>
    updateReturn: (id: string | number, payload: PurchaseReturnCreateRequest) => Promise<unknown>
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
  const selectedReceipt = ref<PurchaseReceipt>()
  const form = reactive<PurchaseReturnCreateRequest>({
    receiptId: '',
    returnDate: '',
    items: [],
    remark: ''
  })

  const formRules = computed<FormRules>(() => ({
    receiptId: [{ required: true, message: t('purchaseReturn.validation.receipt'), trigger: 'change' }],
    returnDate: [{ required: true, message: t('purchaseReturn.validation.date'), trigger: 'change' }]
  }))

  const productInfoById = (productId: string | number) => {
    const product = options.products.value.find((item) => String(item.id) === String(productId))
    return {
      productCode: product?.code || product?.productCode || '',
      productName: product?.name || product?.productName || ''
    }
  }

  const resetForm = () => {
    editingId.value = ''
    form.receiptId = ''
    form.returnDate = ''
    form.items = []
    form.remark = ''
    selectedReceipt.value = undefined
    formRef.value?.resetFields()
  }

  const handleAdd = async () => {
    try {
      const receipts = await options.loadCreateOptions()
      if (receipts.length === 0) {
        options.onWarning?.(t('purchaseReturn.message.noAvailableReceipts'))
        return
      }
      resetForm()
      dialogVisible.value = true
    } catch {
      options.onError?.(t('purchaseReturn.message.ordersLoadFailed'))
    }
  }

  const handleReceiptChange = async () => {
    const summary = options.availableReceipts.value.find(
      (receipt) => String(receipt.id) === String(form.receiptId)
    )
    const receipt = summary?.items?.length ? summary : await options.getReceipt(form.receiptId)
    if (!receipt) return

    selectedReceipt.value = receipt
    form.items = (receipt.items || []).map((item) => ({
      ...productInfoById(item.productId),
      receiptLineId: item.id,
      orderLineId: item.orderLineId || item.orderItemId,
      productId: item.productId,
      productCode: item.productCode || productInfoById(item.productId).productCode,
      productName: item.productName || productInfoById(item.productId).productName,
      receiptQty: item.quantity,
      returnedQty: item.returnedQty || 0,
      availableReturnQty: item.availableReturnQty ?? item.quantity - (item.returnedQty || 0),
      quantity: item.quantity - (item.returnedQty || 0),
      price: item.price || 0,
      taxRate: item.taxRate || 0,
      amount: item.amount || 0,
      taxAmount: item.taxAmount || 0,
      locationId: item.locationId ?? undefined,
      serialNos: item.serialNos || '',
      lotNo: item.lotNo || '',
      productionDate: item.productionDate || '',
      expiryDate: item.expiryDate || '',
      remark: ''
    }))
    form.items = await hydrateProductLineLabels(form.items, async (productId) => {
      const product = options.products.value.find((item) => String(item.id) === String(productId))
      return product || {}
    })
  }

  const handleEdit = async (row: PurchaseReturn) => {
    try {
      const detail = await options.getReturn(row.id)
      editingId.value = detail.id
      const receipt = await options.getReceipt(detail.receiptId)
      options.availableReceipts.value = [receipt]
      selectedReceipt.value = receipt
      form.receiptId = detail.receiptId
      form.returnDate = detail.returnDate
      form.remark = detail.remark || ''
      form.items = (detail.items || detail.lines || []).map((item) => ({
        receiptLineId: item.receiptLineId,
        orderLineId: item.orderLineId,
        productId: item.productId,
        productCode: item.productCode,
        productName: item.productName,
        receiptQty: item.receiptQty,
        returnedQty: item.returnedQty || 0,
        availableReturnQty: (item.availableReturnQty ?? 0) + Number(item.quantity ?? item.qty ?? 0),
        quantity: Number(item.quantity ?? item.qty ?? 0),
        price: item.price || 0,
        taxRate: item.taxRate || 0,
        amount: item.amount || 0,
        taxAmount: item.taxAmount || 0,
        lotNo: item.lotNo,
        productionDate: item.productionDate,
        expiryDate: item.expiryDate,
        locationId: item.locationId ?? undefined,
        serialNos: item.serialNos || '',
        remark: item.reason || item.remark || ''
      }))
      form.items = await hydrateProductLineLabels(form.items, async (productId) => {
        const product = options.products.value.find((item) => String(item.id) === String(productId))
        return product || {}
      })
      dialogVisible.value = true
    } catch {
      options.onError?.(t('purchaseReturn.message.returnLoadFailed'))
    }
  }

  const handleSubmitForm = async () => {
    if (!formRef.value) return

    await formRef.value.validate(async (valid) => {
      if (!valid) return

      if (form.items.length === 0) {
        options.onWarning?.(t('purchaseReturn.validation.receipt'))
        return
      }

      form.items = await hydrateProductLineLabels(form.items, async (productId) => {
        const product = options.products.value.find((item) => String(item.id) === String(productId))
        return product || {}
      })
      const controlIssues = validateProductControlLines(form.items)
      if (controlIssues.length > 0) {
        const issue = controlIssues[0]
        const product = issue.productCode || issue.productName || String(issue.productId)
        options.onWarning?.(t(`purchaseReturn.validation.${issue.messageKey}`, {
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
          await options.updateReturn(editingId.value, form)
          options.onSuccess?.(t('purchaseReturn.message.updated'))
        } else {
          await options.createReturn(form)
          options.onSuccess?.(t('purchaseReturn.message.created'))
        }
        dialogVisible.value = false
        options.onCompleted?.()
      } catch {
        options.onError?.(
          editingId.value
            ? t('purchaseReturn.message.updateFailed')
            : t('purchaseReturn.message.createFailed')
        )
      } finally {
        submitLoading.value = false
      }
    })
  }

  return {
    dialogVisible,
    editingId,
    form,
    formRef,
    formRules,
    handleAdd,
    handleEdit,
    handleReceiptChange,
    handleSubmitForm,
    resetForm,
    selectedReceipt,
    submitLoading
  }
}
