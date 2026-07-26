import { reactive, ref } from 'vue'

import type { ProductionOrder } from '@/api/production'
import {
  validateProductControlLines,
  type ProductControlValidationIssue
} from '@/utils/productLines'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type ProductControls = {
  lotControlled?: boolean
  shelfLifeControlled?: boolean
  serialControlled?: boolean
  productCode?: string
  productName?: string
}
type CompletePayload = {
  completedQuantity: number
  scrapQuantity?: number
  completionDate?: string
  lotNo?: string
  productionDate?: string
  expiryDate?: string
  locationId?: string | number
  serialNos?: string
  remark?: string
}
type ReversePayload = {
  reversedQty: number
  reversalDate?: string
  remark?: string
}

export const useProductionOrderCompletion = (
  t: Translate,
  options: {
    completeOrder: (orderId: string | number, payload: CompletePayload) => Promise<unknown>
    reverseCompletion: (orderId: string | number, payload: ReversePayload) => Promise<unknown>
    productControlFromOptions: (productId?: string | number) => ProductControls
    resolveProductControls: (productId?: string | number) => Promise<ProductControls>
    loadFinishedLocations: (warehouseId?: string | number) => void | Promise<void>
    formatBusinessDate: () => string
    onSuccess?: Notify
    onError?: Notify
    onWarning?: Notify
    onCompleted?: () => void
  }
) => {
  const completeDialogVisible = ref(false)
  const reverseDialogVisible = ref(false)
  const submitLoading = ref(false)
  const completeProductId = ref<string | number | undefined>()
  const completeProductControls = reactive<ProductControls>({
    lotControlled: undefined,
    shelfLifeControlled: undefined,
    serialControlled: undefined,
    productCode: undefined,
    productName: undefined
  })
  const completeForm = reactive({
    orderId: '' as string | number,
    completedQuantity: 0,
    scrapQuantity: 0,
    completionDate: '',
    lotNo: '',
    productionDate: '',
    expiryDate: '',
    locationId: undefined as string | number | undefined,
    serialNos: '',
    maxQuantity: 0,
    remark: ''
  })
  const reverseForm = reactive({
    orderId: '' as string | number,
    reversedQty: 0,
    reversalDate: '',
    maxQuantity: 0,
    remark: ''
  })

  const canReverseCompletion = (row: ProductionOrder) => {
    return row.status !== 'CANCELLED' && Number(row.completedQuantity || 0) > 0
  }

  const handleComplete = async (row: ProductionOrder) => {
    completeForm.orderId = row.id
    completeForm.maxQuantity = Number(row.planQuantity || 0) - Number(row.completedQuantity || 0)
    completeForm.completedQuantity = completeForm.maxQuantity
    completeForm.scrapQuantity = 0
    completeForm.completionDate = options.formatBusinessDate()
    completeForm.lotNo = ''
    completeForm.productionDate = ''
    completeForm.expiryDate = ''
    completeForm.locationId = undefined
    completeForm.serialNos = ''
    completeForm.remark = ''
    completeProductId.value = row.productId
    Object.assign(completeProductControls, options.productControlFromOptions(row.productId))
    completeDialogVisible.value = true
    void options.loadFinishedLocations(row.finishedWarehouseId || row.warehouseId)
    Object.assign(completeProductControls, await options.resolveProductControls(row.productId))
  }

  const warnControlIssue = (issue: ProductControlValidationIssue, line = 1) => {
    const product = issue.productCode || issue.productName || String(issue.productId)
    options.onWarning?.(t(`productionOrder.validation.${issue.messageKey}`, {
      line,
      product,
      expected: issue.expectedSerialCount,
      actual: issue.actualSerialCount
    }))
  }

  const handleConfirmComplete = async () => {
    if (!completeForm.completedQuantity) {
      options.onWarning?.(t('productionOrder.validation.completedQuantity'))
      return
    }

    Object.assign(completeProductControls, await options.resolveProductControls(completeProductId.value))
    const controlIssues = validateProductControlLines([{
      productId: completeProductId.value || completeForm.orderId,
      productCode: completeProductControls.productCode,
      productName: completeProductControls.productName,
      quantity: completeForm.completedQuantity,
      lotNo: completeForm.lotNo,
      expiryDate: completeForm.expiryDate,
      serialNos: completeForm.serialNos,
      lotControlled: completeProductControls.lotControlled,
      shelfLifeControlled: completeProductControls.shelfLifeControlled,
      serialControlled: completeProductControls.serialControlled
    }])
    if (controlIssues.length > 0) {
      warnControlIssue(controlIssues[0], 1)
      return
    }

    submitLoading.value = true
    try {
      await options.completeOrder(completeForm.orderId, {
        completedQuantity: completeForm.completedQuantity,
        scrapQuantity: completeForm.scrapQuantity,
        completionDate: completeForm.completionDate,
        lotNo: completeForm.lotNo || undefined,
        productionDate: completeForm.productionDate || undefined,
        expiryDate: completeForm.expiryDate || undefined,
        locationId: completeForm.locationId || undefined,
        serialNos: completeForm.serialNos || undefined,
        remark: completeForm.remark
      })
      options.onSuccess?.(t('productionOrder.message.completed'))
      completeDialogVisible.value = false
      options.onCompleted?.()
    } catch {
      options.onError?.(t('productionOrder.message.completeFailed'))
    } finally {
      submitLoading.value = false
    }
  }

  const handleReverseCompletion = (row: ProductionOrder) => {
    const completedQuantity = Number(row.completedQuantity || 0)
    reverseForm.orderId = row.id
    reverseForm.maxQuantity = completedQuantity
    reverseForm.reversedQty = completedQuantity
    reverseForm.reversalDate = row.actualEndDate || row.planEndDate || options.formatBusinessDate()
    reverseForm.remark = ''
    reverseDialogVisible.value = true
  }

  const handleConfirmReverseCompletion = async () => {
    if (!reverseForm.reversedQty) {
      options.onWarning?.(t('productionOrder.validation.reversalQuantity'))
      return
    }

    submitLoading.value = true
    try {
      await options.reverseCompletion(reverseForm.orderId, {
        reversedQty: reverseForm.reversedQty,
        reversalDate: reverseForm.reversalDate,
        remark: reverseForm.remark
      })
      options.onSuccess?.(t('productionOrder.message.reversed'))
      reverseDialogVisible.value = false
      options.onCompleted?.()
    } catch {
      options.onError?.(t('productionOrder.message.reverseFailed'))
    } finally {
      submitLoading.value = false
    }
  }

  return {
    canReverseCompletion,
    completeDialogVisible,
    completeForm,
    completeProductControls,
    completeProductId,
    handleComplete,
    handleConfirmComplete,
    handleConfirmReverseCompletion,
    handleReverseCompletion,
    reverseDialogVisible,
    reverseForm,
    submitLoading
  }
}
