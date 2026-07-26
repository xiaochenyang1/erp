import { reactive, ref, type Ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

import {
  checkInventoryReservations,
  manualReleaseInventoryReservation,
  type InventoryLotBalance,
  type InventoryLotBalanceQuery,
  type InventoryLotExpiryAlertQuery,
  type InventoryLotTraceQuery,
  type InventoryReservation,
  type InventoryReservationCheckIssue,
  type InventoryReservationDetail,
  type InventoryReservationQuery,
  type InventoryStock,
  type InventoryStockQuery,
  type InventoryTransactionQuery
} from '@/api/inventory'

export interface InventoryStockActionQueries {
  queryParams: InventoryStockQuery
  reservationQuery: InventoryReservationQuery
  lotBalanceQuery: InventoryLotBalanceQuery
  transactionQuery: InventoryTransactionQuery
  lotAlertQuery: InventoryLotExpiryAlertQuery
  lotTraceQuery: InventoryLotTraceQuery
}

export interface InventoryStockActionLoaders {
  loadData: () => Promise<void> | void
  loadLotAlerts: () => Promise<void> | void
  loadLotBalances: () => Promise<void> | void
  loadLotTrace: () => Promise<void> | void
  loadReservations: () => Promise<void> | void
  loadReservationSummary: () => Promise<void> | void
  loadTransactions: () => Promise<void> | void
}

export interface InventoryStockActionDependencies {
  applyStockScope: (target: {
    warehouseId?: string | number
    productId?: string | number
    locationId?: string | number
    pageNo?: number
  }, row?: InventoryStock) => void
  checkReservations?: typeof checkInventoryReservations
  manualRelease?: typeof manualReleaseInventoryReservation
  onError: (messageKey: string, error?: unknown) => void
  onSuccess: (messageKey: string) => void
  reservationDetail: Ref<InventoryReservationDetail | undefined>
  t: (key: string) => string
}

const defaultDependencies = {
  checkReservations: checkInventoryReservations,
  manualRelease: manualReleaseInventoryReservation
}

/**
 * Dialog visibility, scoped open/query/reset handlers, and reservation release/check
 * flows for the inventory stocks page.
 */
export const useInventoryStockActions = (
  queries: InventoryStockActionQueries,
  loaders: InventoryStockActionLoaders,
  dependencies: InventoryStockActionDependencies
) => {
  const checkReservations = dependencies.checkReservations || defaultDependencies.checkReservations
  const manualRelease = dependencies.manualRelease || defaultDependencies.manualRelease

  const reservationDialogVisible = ref(false)
  const releaseDialogVisible = ref(false)
  const checkDialogVisible = ref(false)
  const lotBalanceDialogVisible = ref(false)
  const transactionDialogVisible = ref(false)
  const lotAlertDialogVisible = ref(false)
  const lotTraceDialogVisible = ref(false)
  const checkLoading = ref(false)
  const releasing = ref(false)
  const selectedReservation = ref<InventoryReservation>()
  const checkIssues = ref<InventoryReservationCheckIssue[]>([])
  const releaseFormRef = ref<FormInstance>()

  const releaseForm = reactive({
    qty: 0,
    reason: ''
  })

  const validateReleaseQty = (_rule: unknown, value: number, callback: (error?: Error) => void) => {
    const qty = Number(value)
    if (!Number.isFinite(qty) || qty <= 0) {
      callback(new Error(dependencies.t('inventoryStocks.validation.releasePositive')))
      return
    }
    if (selectedReservation.value && qty > Number(selectedReservation.value.remainingQty)) {
      callback(new Error(dependencies.t('inventoryStocks.validation.releaseMaximum')))
      return
    }
    callback()
  }

  const releaseRules: FormRules = {
    qty: [{ validator: validateReleaseQty, trigger: 'blur' }],
    reason: [
      { required: true, message: dependencies.t('inventoryStocks.validation.releaseReason'), trigger: 'blur' },
      { max: 255, message: dependencies.t('inventoryStocks.validation.releaseReasonLength'), trigger: 'blur' }
    ]
  }

  const handleOpenLotBalances = (row?: InventoryStock) => {
    dependencies.applyStockScope(queries.lotBalanceQuery, row)
    queries.lotBalanceQuery.lotNo = undefined
    lotBalanceDialogVisible.value = true
    void loaders.loadLotBalances()
  }

  const handleLotBalanceQuery = () => {
    queries.lotBalanceQuery.pageNo = 1
    void loaders.loadLotBalances()
  }

  const resetLotBalanceQuery = () => {
    queries.lotBalanceQuery.lotNo = undefined
    queries.lotBalanceQuery.expiringWithinDays = undefined
    handleLotBalanceQuery()
  }

  const handleOpenTransactions = (row?: InventoryStock) => {
    dependencies.applyStockScope(queries.transactionQuery, row)
    queries.transactionQuery.bizNo = undefined
    queries.transactionQuery.direction = undefined
    transactionDialogVisible.value = true
    void loaders.loadTransactions()
  }

  const handleTransactionQuery = () => {
    queries.transactionQuery.pageNo = 1
    void loaders.loadTransactions()
  }

  const resetTransactionQuery = () => {
    queries.transactionQuery.bizNo = undefined
    queries.transactionQuery.direction = undefined
    handleTransactionQuery()
  }

  const handleOpenLotAlerts = (row?: InventoryStock) => {
    dependencies.applyStockScope(queries.lotAlertQuery, row)
    queries.lotAlertQuery.lotNo = undefined
    lotAlertDialogVisible.value = true
    void loaders.loadLotAlerts()
  }

  const handleLotAlertQuery = () => {
    queries.lotAlertQuery.pageNo = 1
    void loaders.loadLotAlerts()
  }

  const resetLotAlertQuery = () => {
    queries.lotAlertQuery.lotNo = undefined
    queries.lotAlertQuery.warningDays = 30
    queries.lotAlertQuery.status = undefined
    handleLotAlertQuery()
  }

  const handleOpenLotTrace = (row: InventoryLotBalance) => {
    Object.assign(queries.lotTraceQuery, {
      pageNo: 1,
      pageSize: 10,
      warehouseId: row.warehouseId,
      productId: row.productId,
      lotNo: row.lotNo,
      direction: undefined
    })
    lotTraceDialogVisible.value = true
    void loaders.loadLotTrace()
  }

  const handleOpenReservations = (row: InventoryStock) => {
    Object.assign(queries.reservationQuery, {
      pageNo: 1,
      pageSize: 10,
      warehouseId: row.warehouseId,
      productId: row.productId,
      status: 'ACTIVE',
      sourceNo: undefined
    })
    reservationDialogVisible.value = true
    void loaders.loadReservationSummary()
    void loaders.loadReservations()
  }

  const handleReservationQuery = () => {
    queries.reservationQuery.pageNo = 1
    void loaders.loadReservationSummary()
    void loaders.loadReservations()
  }

  const resetReservationQuery = () => {
    queries.reservationQuery.pageNo = 1
    queries.reservationQuery.status = undefined
    queries.reservationQuery.sourceNo = undefined
    void loaders.loadReservationSummary()
    void loaders.loadReservations()
  }

  const openReleaseDialog = (row: InventoryReservation) => {
    selectedReservation.value = row
    releaseForm.qty = Number(row.remainingQty)
    releaseForm.reason = ''
    releaseFormRef.value?.clearValidate()
    releaseDialogVisible.value = true
  }

  const submitManualRelease = async () => {
    if (!releaseFormRef.value || !selectedReservation.value) return
    await releaseFormRef.value.validate()

    releasing.value = true
    try {
      const detail = await manualRelease(selectedReservation.value.id, {
        qty: releaseForm.qty,
        reason: releaseForm.reason.trim()
      })
      dependencies.reservationDetail.value = detail
      releaseDialogVisible.value = false
      dependencies.onSuccess('inventoryStocks.message.released')
      await loaders.loadReservations()
      void loaders.loadData()
    } catch (error) {
      dependencies.onError('inventoryStocks.message.releaseFailed', error)
    } finally {
      releasing.value = false
    }
  }

  const handleReservationCheck = async () => {
    checkDialogVisible.value = true
    checkLoading.value = true
    try {
      checkIssues.value = await checkReservations({
        warehouseId: queries.queryParams.warehouseId,
        productId: queries.queryParams.productId
      })
    } catch (error) {
      dependencies.onError('inventoryStocks.message.reservationCheckFailed', error)
    } finally {
      checkLoading.value = false
    }
  }

  return {
    checkDialogVisible,
    checkIssues,
    checkLoading,
    handleLotAlertQuery,
    handleLotBalanceQuery,
    handleOpenLotAlerts,
    handleOpenLotBalances,
    handleOpenLotTrace,
    handleOpenReservations,
    handleOpenTransactions,
    handleReservationCheck,
    handleReservationQuery,
    handleTransactionQuery,
    lotAlertDialogVisible,
    lotBalanceDialogVisible,
    lotTraceDialogVisible,
    openReleaseDialog,
    releaseDialogVisible,
    releaseForm,
    releaseFormRef,
    releaseRules,
    releasing,
    reservationDialogVisible,
    resetLotAlertQuery,
    resetLotBalanceQuery,
    resetReservationQuery,
    resetTransactionQuery,
    selectedReservation,
    submitManualRelease,
    transactionDialogVisible
  }
}
