import { computed, reactive, ref } from 'vue'

import type {
  ProductionCostEntry,
  ProductionCostCloseEntry,
  ProductionCostCloseRequest,
  ProductionCostReopenRequest,
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
  loadCostCloses: (orderId: string | number) => Promise<ProductionCostCloseEntry[]>
  loadOrder?: (orderId: string | number) => Promise<ProductionOrder>
  closeCost: (orderId: string | number, payload: ProductionCostCloseRequest) => Promise<ProductionCostCloseEntry>
  reopenCost: (orderId: string | number, payload: ProductionCostReopenRequest) => Promise<ProductionCostCloseEntry>
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
  const costCloseEntries = ref<ProductionCostCloseEntry[]>([])
  const costCloseActionDialogVisible = ref(false)
  const costCloseActionLoading = ref(false)
  const costCloseActionMode = ref<'CLOSE' | 'REOPEN'>('CLOSE')
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
  const costCloseActionForm = reactive({
    businessDate: options.formatBusinessDate(),
    reason: '',
    remark: ''
  })
  const costCloseStatus = computed(() => costOrder.value?.costCloseStatus || 'OPEN')
  const costUnsettledVariance = computed(() => {
    const order = costOrder.value
    if (!order) return 0
    const input = Number(order.issuedAmount || 0) + Number(order.laborAmount || 0) + Number(order.overheadAmount || 0)
    const finished = Number(order.finishedMaterialAmount || 0)
      + Number(order.finishedLaborAmount || 0)
      + Number(order.finishedOverheadAmount || 0)
    return Number((input - finished - Number(order.settledVarianceAmount || 0)).toFixed(2))
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
      const [entries, operations, closeEntries] = await Promise.all([
        options.loadCosts(costOrderId.value),
        options.loadOperations(costOrderId.value),
        options.loadCostCloses(costOrderId.value)
      ])
      costEntries.value = entries
      costOperations.value = operations
      costCloseEntries.value = closeEntries
    } catch {
      options.onError?.(t('productionOrder.message.costLoadFailed'))
    } finally {
      costLoading.value = false
    }
  }

  const refreshCostOrder = async () => {
    if (!costOrderId.value || !options.loadOrder) return
    try {
      costOrder.value = await options.loadOrder(costOrderId.value)
      costOrderStatus.value = costOrder.value.status
    } catch {
      options.onError?.(t('productionOrder.message.costLoadFailed'))
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
      await refreshCostOrder()
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
    costCloseStatus.value !== 'CLOSED'
    && entry.status === 'POSTED'
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
      await refreshCostOrder()
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

  const openCostCloseAction = (mode: 'CLOSE' | 'REOPEN') => {
    costCloseActionMode.value = mode
    costCloseActionForm.businessDate = options.formatBusinessDate()
    costCloseActionForm.reason = ''
    costCloseActionForm.remark = ''
    costCloseActionDialogVisible.value = true
  }

  const canCloseCost = computed(() =>
    costOrder.value?.status === 'COMPLETED' && costCloseStatus.value !== 'CLOSED'
  )
  const canReopenCost = computed(() => costCloseStatus.value === 'CLOSED')

  const submitCostCloseAction = async () => {
    if (!costOrderId.value || !costCloseActionForm.businessDate) {
      options.onWarning?.(t('productionOrder.costCloseDateRequired'))
      return
    }
    const reason = costCloseActionForm.reason.trim()
    if (costCloseActionMode.value === 'REOPEN' && !reason) {
      options.onWarning?.(t('productionOrder.costCloseReasonRequired'))
      return
    }
    costCloseActionLoading.value = true
    try {
      const result = costCloseActionMode.value === 'CLOSE'
        ? await options.closeCost(costOrderId.value, {
          businessDate: costCloseActionForm.businessDate,
          remark: costCloseActionForm.remark.trim() || undefined
        })
        : await options.reopenCost(costOrderId.value, {
          businessDate: costCloseActionForm.businessDate,
          reason
        })
      if (costOrder.value) {
        costOrder.value.settledVarianceAmount = Number(
          (Number(costOrder.value.settledVarianceAmount || 0) + Number(result.amount || 0)).toFixed(2)
        )
        costOrder.value.costCloseStatus = costCloseActionMode.value === 'CLOSE' ? 'CLOSED' : 'OPEN'
        costOrder.value.costCloseEntryId = costCloseActionMode.value === 'CLOSE' ? String(result.id) : undefined
        costOrder.value.costClosedDate = costCloseActionMode.value === 'CLOSE' ? result.businessDate : undefined
      }
      options.onSuccess?.(t(
        costCloseActionMode.value === 'CLOSE'
          ? 'productionOrder.costClosedMessage'
          : 'productionOrder.costReopenedMessage'
      ))
      costCloseActionDialogVisible.value = false
      await loadCosts()
      await refreshCostOrder()
      await options.onCompleted?.()
    } catch {
      options.onError?.(t(
        costCloseActionMode.value === 'CLOSE'
          ? 'productionOrder.costCloseFailedMessage'
          : 'productionOrder.costReopenFailedMessage'
      ))
    } finally {
      costCloseActionLoading.value = false
    }
  }

  const closeEntryKindLabel = (entry: ProductionCostCloseEntry) =>
    t(`productionOrder.costCloseEntryKind.${(entry.entryKind || 'CLOSE').toLowerCase()}`)

  return {
    costDialogVisible,
    costActionDialogVisible,
    costActionEntry,
    costActionForm,
    costActionLoading,
    costActionMode,
    costCloseActionDialogVisible,
    costCloseActionForm,
    costCloseActionLoading,
    costCloseActionMode,
    costCloseEntries,
    costCloseStatus,
    costUnsettledVariance,
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
    canCloseCost,
    canReopenCost,
    canReverseCost,
    closeEntryKindLabel,
    entryKindLabel,
    entryStatusLabel,
    loadCosts,
    openCorrectCost,
    openCostCloseAction,
    openCosts,
    openReverseCost,
    operationLabel,
    submitCost,
    submitCostAction,
    submitCostCloseAction
  }
}
