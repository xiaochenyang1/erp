import { reactive, ref, type Ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

import {
  checkInventoryReservations,
  manualReleaseInventoryReservation,
  type InventoryReservation,
  type InventoryReservationCheckIssue,
  type InventoryReservationDetail,
  type InventoryReservationQuery,
  type InventoryStock,
  type InventoryStockQuery
} from '@/api/inventory'

export interface InventoryStockActionQueries {
  queryParams: InventoryStockQuery
  reservationQuery: InventoryReservationQuery
}

export interface InventoryStockActionLoaders {
  loadData: () => Promise<void> | void
  loadReservations: () => Promise<void> | void
  loadReservationSummary: () => Promise<void> | void
}

export interface InventoryStockActionDependencies {
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
    handleOpenReservations,
    handleReservationCheck,
    handleReservationQuery,
    openReleaseDialog,
    releaseDialogVisible,
    releaseForm,
    releaseFormRef,
    releaseRules,
    releasing,
    reservationDialogVisible,
    resetReservationQuery,
    selectedReservation,
    submitManualRelease
  }
}
