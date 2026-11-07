import { reactive, ref } from 'vue'

import type {
  ProductionCostEntry,
  ProductionCostEntryCorrectionRequest,
  ProductionCostEntryRequest,
  ProductionCostEntryReversalRequest,
  ProductionOrder,
  ProductionOrderOperation
} from '@/api/production'

type Translate = (key: string, params?: Record<string, unknown>) => string

interface CostOptions {
  loadCosts: (orderId: string | number) => Promise<ProductionCostEntry[]>
  postCost: (orderId: string | number, payload: ProductionCostEntryRequest) => Promise<unknown>
  reverseCost: (
    orderId: string | number,
    costEntryId: string | number,
    payload: ProductionCostEntryReversalRequest
  ) => Promise<unknown>
  correctCost: (
    orderId: string | number,
    costEntryId: string | number,
    payload: ProductionCostEntryCorrectionRequest
  ) => Promise<unknown>
  loadOperations: (orderId: string | number) => Promise<ProductionOrderOperation[]>
  formatBusinessDate: () => string
  onError?: (message: string) => void
  onSuccess?: (message: string) => void
  onWarning?: (message: string) => void
  onCompleted?: () => void | Promise<void>
}

export const useProductionOrderCosts = (t: Translate, options: CostOptions) => {
  const costDialogVisible = ref(false)
  const costLoading = ref(false)
  const costSubmitLoading = ref(false)
  const costActionDialogVisible = ref(false)
  const costActionLoading = ref(false)
  const costActionMode = ref<'REVERSE' | 'CORRECT'>('REVERSE')
  const costActionEntry = ref<ProductionCostEntry | null>(null)
  const costOrderId = ref<string | number>('')
  const costOrderNo = ref('')
  const costOrderStatus = ref('')
  const costEntries = ref<ProductionCostEntry[]>([])
  const costOperations = ref<ProductionOrderOperation[]>([])
  const costOrder = ref<ProductionOrder | null>(null)
  const costForm = reactive<ProductionCostEntryRequest>({
    costType: 'LABOR',
    businessDate: options.formatBusinessDate(),
    amount: 0,
    remark: ''
  })
  const costActionForm = reactive<ProductionCostEntryCorrectionRequest>({
    costType: 'LABOR',
    businessDate: options.formatBusinessDate(),
    amount: 0,
    reason: '',
    remark: ''
  })

  const resetCostForm = () => {
    costForm.costType = 'LABOR'
    costForm.operationId = undefined
    costForm.businessDate = options.formatBusinessDate()
    costForm.amount = 0
    costForm.remark = ''
  }

  const loadCosts = async () => {
    if (!costOrderId.value) return
    costLoading.value = true
    try {
      const [entries, operations] = await Promise.all([
        options.loadCosts(costOrderId.value),
        options.loadOperations(costOrderId.value)
      ])
      costEntries.value = entries
      costOperations.value = operations
    } catch {
      options.onError?.(t('productionOrder.message.costLoadFailed'))
    } finally {
      costLoading.value = false
    }
  }

  const openCosts = async (order: ProductionOrder) => {
    costOrderId.value = order.id
    costOrderNo.value = order.orderNo
    costOrderStatus.value = order.status
    costOrder.value = order
    resetCostForm()
    costDialogVisible.value = true
    await loadCosts()
  }

  const submitCost = async () => {
    if (!costOrderId.value || !costForm.businessDate || Number(costForm.amount) <= 0) {
      options.onWarning?.(t('productionOrder.validation.costAmount'))
      return
    }
    costSubmitLoading.value = true
    try {
      await options.postCost(costOrderId.value, {
        costType: costForm.costType,
        operationId: costForm.operationId || undefined,
        businessDate: costForm.businessDate,
        amount: Number(costForm.amount),
        remark: costForm.remark?.trim() || undefined
      })
      options.onSuccess?.(t('productionOrder.message.costPosted'))
      resetCostForm()
      await loadCosts()
      await options.onCompleted?.()
    } catch {
      options.onError?.(t('productionOrder.message.costPostFailed'))
    } finally {
      costSubmitLoading.value = false
    }
  }

  const openCostAction = (entry: ProductionCostEntry, mode: 'REVERSE' | 'CORRECT') => {
    costActionEntry.value = entry
    costActionMode.value = mode
    costActionForm.costType = entry.costType
    costActionForm.operationId = entry.operationId
    costActionForm.businessDate = options.formatBusinessDate()
    costActionForm.amount = Math.abs(Number(entry.amount || 0))
    costActionForm.reason = ''
    costActionForm.remark = entry.remark || ''
    costActionDialogVisible.value = true
  }

  const openReverseCost = (entry: ProductionCostEntry) => openCostAction(entry, 'REVERSE')
  const openCorrectCost = (entry: ProductionCostEntry) => openCostAction(entry, 'CORRECT')

  const canReverseCost = (entry: ProductionCostEntry) =>
    entry.status === 'POSTED'
    && entry.entryKind !== 'REVERSAL'
    && Number(entry.amount) > 0
    && !entry.reversalEntryId

  const submitCostAction = async () => {
    const entry = costActionEntry.value
    const reason = costActionForm.reason?.trim()
    if (!costOrderId.value || !entry || !costActionForm.businessDate || !reason) {
      options.onWarning?.(t('productionOrder.costActionRequired'))
      return
    }
    if (costActionMode.value === 'CORRECT' && Number(costActionForm.amount) <= 0) {
      options.onWarning?.(t('productionOrder.validation.costAmount'))
      return
    }
    costActionLoading.value = true
    try {
      if (costActionMode.value === 'REVERSE') {
        await options.reverseCost(costOrderId.value, entry.id, {
          businessDate: costActionForm.businessDate,
          reason
        })
        options.onSuccess?.(t('productionOrder.costReversedMessage'))
      } else {
        await options.correctCost(costOrderId.value, entry.id, {
          costType: costActionForm.costType,
          operationId: costActionForm.operationId || undefined,
          businessDate: costActionForm.businessDate,
          amount: Number(costActionForm.amount),
          reason,
          remark: costActionForm.remark?.trim() || undefined
        })
        options.onSuccess?.(t('productionOrder.costCorrectedMessage'))
      }
      costActionDialogVisible.value = false
      await loadCosts()
      await options.onCompleted?.()
    } catch {
      options.onError?.(t(
        costActionMode.value === 'REVERSE'
          ? 'productionOrder.costReverseFailedMessage'
          : 'productionOrder.costCorrectFailedMessage'
      ))
    } finally {
      costActionLoading.value = false
    }
  }

  const costTypeLabel = (type?: string) =>
    type === 'OVERHEAD'
      ? t('productionOrder.costTypeValue.overhead')
      : t('productionOrder.costTypeValue.labor')

  const operationLabel = (operationId?: string | number) => {
    if (operationId == null) return t('productionOrder.orderLevelCost')
    const operation = costOperations.value.find((item) => String(item.id) === String(operationId))
    return operation ? `${operation.operationCode} ${operation.operationName}` : String(operationId)
  }

  const entryKindLabel = (entry: ProductionCostEntry) =>
    t(`productionOrder.costEntryKind.${(entry.entryKind || 'ORIGINAL').toLowerCase()}`)

  const entryStatusLabel = (status?: string) =>
    t(`productionOrder.costEntryStatus.${(status || 'POSTED').toLowerCase()}`)

  return {
    costDialogVisible,
    costActionDialogVisible,
    costActionEntry,
    costActionForm,
    costActionLoading,
    costActionMode,
    costEntries,
    costForm,
    costLoading,
    costOperations,
    costOrder,
    costOrderId,
    costOrderNo,
    costOrderStatus,
    costSubmitLoading,
    costTypeLabel,
    canReverseCost,
    entryKindLabel,
    entryStatusLabel,
    loadCosts,
    openCorrectCost,
    openCosts,
    openReverseCost,
    operationLabel,
    submitCost,
    submitCostAction
  }
}
