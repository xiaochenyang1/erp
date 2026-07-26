import { reactive, ref } from 'vue'

import type { ProductionOrder, ProductionOrderOperation } from '@/api/production'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type LoadOperations = (orderId: string | number) => Promise<ProductionOrderOperation[]>
type ReportOperation = (
  orderId: string | number,
  operationId: string | number,
  payload: {
    reportQty: number
    qualifiedQty: number
    scrapQty: number
    remark?: string
  }
) => Promise<unknown>

export const useProductionOrderOperations = (
  t: Translate,
  options: {
    loadOperations: LoadOperations
    reportOperation: ReportOperation
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
  }
) => {
  const opsDialogVisible = ref(false)
  const opsLoading = ref(false)
  const opsOrderId = ref<string | number>('')
  const opsOrderNo = ref('')
  const operations = ref<ProductionOrderOperation[]>([])
  const reportDialogVisible = ref(false)
  const reportLoading = ref(false)
  const reportForm = reactive({
    operationId: '' as string | number,
    operationName: '',
    reportQty: 1,
    qualifiedQty: 1,
    scrapQty: 0,
    remark: ''
  })

  const loadOperations = async () => {
    if (!opsOrderId.value) return
    opsLoading.value = true
    try {
      operations.value = await options.loadOperations(opsOrderId.value)
    } catch {
      options.onError?.(t('productionOrder.message.operationsLoadFailed'))
    } finally {
      opsLoading.value = false
    }
  }

  const openOperations = async (row: Pick<ProductionOrder, 'id' | 'orderNo'>) => {
    opsOrderId.value = row.id
    opsOrderNo.value = row.orderNo
    opsDialogVisible.value = true
    await loadOperations()
  }

  const openReport = (row: ProductionOrderOperation) => {
    reportForm.operationId = row.id
    reportForm.operationName = `${row.operationCode} ${row.operationName}`
    const remain = Math.max(Number(row.plannedQty) - Number(row.reportedQty), 0.0001)
    reportForm.reportQty = remain
    reportForm.qualifiedQty = remain
    reportForm.scrapQty = 0
    reportForm.remark = ''
    reportDialogVisible.value = true
  }

  const submitReport = async () => {
    if (reportForm.qualifiedQty > reportForm.reportQty) {
      options.onWarning?.(t('productionOrder.validation.qualifiedExceedsReported'))
      return
    }
    reportLoading.value = true
    try {
      await options.reportOperation(opsOrderId.value, reportForm.operationId, {
        reportQty: reportForm.reportQty,
        qualifiedQty: reportForm.qualifiedQty,
        scrapQty: reportForm.scrapQty,
        remark: reportForm.remark || undefined
      })
      options.onSuccess?.(t('productionOrder.message.reported'))
      reportDialogVisible.value = false
      await loadOperations()
    } catch {
      // Shared request interceptor already surfaces the error.
    } finally {
      reportLoading.value = false
    }
  }

  return {
    loadOperations,
    openOperations,
    openReport,
    operations,
    opsDialogVisible,
    opsLoading,
    opsOrderId,
    opsOrderNo,
    reportDialogVisible,
    reportForm,
    reportLoading,
    submitReport
  }
}
