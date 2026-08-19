import { reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

import {
  checkInventoryReservations,
  getInventoryReservation,
  getInventoryReservations,
  getInventoryReservationSource,
  getInventoryReservationSummary,
  manualReleaseInventoryReservation,
  type InventoryReservation,
  type InventoryReservationCheckIssue,
  type InventoryReservationDetail,
  type InventoryReservationQuery,
  type InventoryReservationSource,
  type InventoryReservationSummary,
  type InventoryStock,
  type InventoryStockQuery
} from '@/api/inventory'

export interface InventoryStockReservationListDependencies {
  checkReservations: typeof checkInventoryReservations
  getReservation: typeof getInventoryReservation
  getReservations: typeof getInventoryReservations
  getReservationSource: typeof getInventoryReservationSource
  getReservationSummary: typeof getInventoryReservationSummary
  manualRelease: typeof manualReleaseInventoryReservation
}

export interface InventoryStockReservationListCallbacks {
  onError: (messageKey: string, error?: unknown) => void
  onSuccess: (messageKey: string) => void
  reloadStockList: () => Promise<void> | void
  t: (key: string) => string
}

const defaultDependencies: InventoryStockReservationListDependencies = {
  checkReservations: checkInventoryReservations,
  getReservation: getInventoryReservation,
  getReservations: getInventoryReservations,
  getReservationSource: getInventoryReservationSource,
  getReservationSummary: getInventoryReservationSummary,
  manualRelease: manualReleaseInventoryReservation
}

/** Reservation operations opened from inventory balances and the current stock filters. */
export const useInventoryStockReservationList = (
  stockQuery: InventoryStockQuery,
  callbacks: InventoryStockReservationListCallbacks,
  dependencies: InventoryStockReservationListDependencies = defaultDependencies
) => {
  const reservationQuery = reactive<InventoryReservationQuery>({
    pageNo: 1,
    pageSize: 10,
    warehouseId: undefined,
    productId: undefined,
    status: 'ACTIVE',
    sourceNo: undefined
  })

  const reservationDialogVisible = ref(false)
  const reservationDetailVisible = ref(false)
  const reservationSourceVisible = ref(false)
  const releaseDialogVisible = ref(false)
  const checkDialogVisible = ref(false)

  const reservationLoading = ref(false)
  const reservationSummaryLoading = ref(false)
  const reservationDetailLoading = ref(false)
  const reservationSourceLoading = ref(false)
  const releasing = ref(false)
  const checkLoading = ref(false)
  const checkFailed = ref(false)

  const reservationData = ref<InventoryReservation[]>([])
  const reservationTotal = ref(0)
  const reservationSummaryData = ref<InventoryReservationSummary[]>([])
  const reservationDetail = ref<InventoryReservationDetail>()
  const reservationSourceDetail = ref<InventoryReservationSource>()
  const selectedReservation = ref<InventoryReservation>()
  const checkIssues = ref<InventoryReservationCheckIssue[]>([])
  const releaseFormRef = ref<FormInstance>()

  const releaseForm = reactive({
    qty: 0,
    reason: ''
  })

  let reservationRequestId = 0
  let reservationSummaryRequestId = 0
  let reservationDetailRequestId = 0
  let reservationSourceRequestId = 0
  let reservationCheckRequestId = 0

  const validateReleaseQty = (_rule: unknown, value: number, callback: (error?: Error) => void) => {
    const qty = Number(value)
    if (!Number.isFinite(qty) || qty <= 0) {
      callback(new Error(callbacks.t('inventoryStocks.validation.releasePositive')))
      return
    }
    if (selectedReservation.value && qty > Number(selectedReservation.value.remainingQty)) {
      callback(new Error(callbacks.t('inventoryStocks.validation.releaseMaximum')))
      return
    }
    callback()
  }

  const releaseRules: FormRules = {
    qty: [{ validator: validateReleaseQty, trigger: 'blur' }],
    reason: [
      { required: true, message: callbacks.t('inventoryStocks.validation.releaseReason'), trigger: 'blur' },
      { max: 255, message: callbacks.t('inventoryStocks.validation.releaseReasonLength'), trigger: 'blur' }
    ]
  }

  const loadReservations = async () => {
    const requestId = ++reservationRequestId
    reservationLoading.value = true
    try {
      const page = await dependencies.getReservations(reservationQuery)
      if (requestId !== reservationRequestId) return
      reservationData.value = page.records || []
      reservationTotal.value = page.total || 0
    } catch (error) {
      if (requestId !== reservationRequestId) return
      callbacks.onError('inventoryStocks.message.reservationsLoadFailed', error)
    } finally {
      if (requestId === reservationRequestId) reservationLoading.value = false
    }
  }

  const loadReservationSummary = async () => {
    const requestId = ++reservationSummaryRequestId
    reservationSummaryLoading.value = true
    try {
      const data = await dependencies.getReservationSummary({
        warehouseId: reservationQuery.warehouseId,
        productId: reservationQuery.productId,
        status: reservationQuery.status
      })
      if (requestId !== reservationSummaryRequestId) return
      reservationSummaryData.value = data || []
    } catch (error) {
      if (requestId !== reservationSummaryRequestId) return
      callbacks.onError('inventoryStocks.message.reservationSummaryLoadFailed', error)
    } finally {
      if (requestId === reservationSummaryRequestId) reservationSummaryLoading.value = false
    }
  }

  const reloadReservationData = () => Promise.all([
    loadReservations(),
    loadReservationSummary()
  ])

  const handleOpenReservations = (row?: InventoryStock) => {
    Object.assign(reservationQuery, {
      pageNo: 1,
      warehouseId: row?.warehouseId ?? stockQuery.warehouseId,
      productId: row?.productId ?? stockQuery.productId,
      status: 'ACTIVE',
      sourceNo: undefined
    })
    reservationDialogVisible.value = true
    return reloadReservationData()
  }

  const handleReservationQuery = () => {
    reservationQuery.pageNo = 1
    return reloadReservationData()
  }

  const resetReservationQuery = () => {
    reservationQuery.status = undefined
    reservationQuery.sourceNo = undefined
    return handleReservationQuery()
  }

  const handleReservationPageChange = (page: number) => {
    reservationQuery.pageNo = page
    return loadReservations()
  }

  const handleReservationSizeChange = (pageSize: number) => {
    reservationQuery.pageSize = pageSize
    reservationQuery.pageNo = 1
    return loadReservations()
  }

  const handleViewReservation = async (row: InventoryReservation) => {
    const requestId = ++reservationDetailRequestId
    reservationDetailVisible.value = true
    reservationDetail.value = undefined
    reservationDetailLoading.value = true
    try {
      const detail = await dependencies.getReservation(row.id)
      if (requestId !== reservationDetailRequestId) return
      reservationDetail.value = detail
    } catch (error) {
      if (requestId !== reservationDetailRequestId) return
      callbacks.onError('inventoryStocks.message.reservationDetailLoadFailed', error)
      reservationDetailVisible.value = false
    } finally {
      if (requestId === reservationDetailRequestId) reservationDetailLoading.value = false
    }
  }

  const handleViewReservationSource = async (row: InventoryReservation) => {
    const requestId = ++reservationSourceRequestId
    reservationSourceVisible.value = true
    reservationSourceDetail.value = undefined
    reservationSourceLoading.value = true
    try {
      const detail = await dependencies.getReservationSource({
        sourceType: row.sourceType,
        sourceId: row.sourceId,
        sourceNo: row.sourceNo
      })
      if (requestId !== reservationSourceRequestId) return
      reservationSourceDetail.value = detail
    } catch (error) {
      if (requestId !== reservationSourceRequestId) return
      callbacks.onError('inventoryStocks.message.sourceReservationLoadFailed', error)
      reservationSourceVisible.value = false
    } finally {
      if (requestId === reservationSourceRequestId) reservationSourceLoading.value = false
    }
  }

  const openReleaseDialog = (row: InventoryReservation) => {
    selectedReservation.value = row
    releaseForm.qty = Number(row.remainingQty)
    releaseForm.reason = ''
    releaseFormRef.value?.clearValidate()
    releaseDialogVisible.value = true
  }

  const submitManualRelease = async () => {
    if (!releaseFormRef.value || !selectedReservation.value || releasing.value) return
    const form = releaseFormRef.value
    const detailRequestIdAtSubmit = reservationDetailRequestId
    const releaseRequest = {
      id: selectedReservation.value.id,
      qty: releaseForm.qty,
      reason: releaseForm.reason.trim()
    }
    try {
      const valid = await form.validate()
      if (!valid || releasing.value) return
    } catch {
      return
    }

    releasing.value = true
    try {
      const detail = await dependencies.manualRelease(releaseRequest.id, {
        qty: releaseRequest.qty,
        reason: releaseRequest.reason
      })
      if (detailRequestIdAtSubmit === reservationDetailRequestId) {
        ++reservationDetailRequestId
        reservationDetail.value = detail
        reservationDetailLoading.value = false
      }
      releaseDialogVisible.value = false
      callbacks.onSuccess('inventoryStocks.message.released')
      await reloadReservationData()
      void callbacks.reloadStockList()
    } catch (error) {
      callbacks.onError('inventoryStocks.message.releaseFailed', error)
    } finally {
      releasing.value = false
    }
  }

  const handleReservationCheck = async () => {
    const requestId = ++reservationCheckRequestId
    checkDialogVisible.value = true
    checkFailed.value = false
    checkLoading.value = true
    try {
      const issues = await dependencies.checkReservations({
        warehouseId: stockQuery.warehouseId,
        productId: stockQuery.productId
      })
      if (requestId !== reservationCheckRequestId) return
      checkIssues.value = issues || []
    } catch (error) {
      if (requestId !== reservationCheckRequestId) return
      callbacks.onError('inventoryStocks.message.reservationCheckFailed', error)
      checkFailed.value = true
    } finally {
      if (requestId === reservationCheckRequestId) checkLoading.value = false
    }
  }

  return {
    checkDialogVisible,
    checkFailed,
    checkIssues,
    checkLoading,
    handleOpenReservations,
    handleReservationCheck,
    handleReservationPageChange,
    handleReservationQuery,
    handleReservationSizeChange,
    handleViewReservation,
    handleViewReservationSource,
    loadReservations,
    loadReservationSummary,
    openReleaseDialog,
    releaseDialogVisible,
    releaseForm,
    releaseFormRef,
    releaseRules,
    releasing,
    reservationData,
    reservationDetail,
    reservationDetailLoading,
    reservationDetailVisible,
    reservationDialogVisible,
    reservationLoading,
    reservationQuery,
    reservationSourceDetail,
    reservationSourceLoading,
    reservationSourceVisible,
    reservationSummaryData,
    reservationSummaryLoading,
    reservationTotal,
    resetReservationQuery,
    selectedReservation,
    submitManualRelease
  }
}
