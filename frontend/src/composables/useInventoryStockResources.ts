import { ref } from 'vue'

import {
  getInventoryLotBalances,
  getInventoryLotExpiryAlerts,
  getInventoryLotTrace,
  getInventoryReservationSummary,
  getInventoryReservations,
  type InventoryLotBalance,
  type InventoryLotBalanceQuery,
  type InventoryLotExpiryAlert,
  type InventoryLotExpiryAlertQuery,
  type InventoryLotTrace,
  type InventoryLotTraceQuery,
  type InventoryReservation,
  type InventoryReservationQuery,
  type InventoryReservationSummary
} from '@/api/inventory'

export interface InventoryStockResourceQueries {
  reservations: InventoryReservationQuery
  lotBalances: InventoryLotBalanceQuery
  lotAlerts: InventoryLotExpiryAlertQuery
  lotTrace: InventoryLotTraceQuery
}

export interface InventoryStockResourceDependencies {
  getReservations: typeof getInventoryReservations
  getReservationSummary: typeof getInventoryReservationSummary
  getLotBalances: typeof getInventoryLotBalances
  getLotAlerts: typeof getInventoryLotExpiryAlerts
  getLotTrace: typeof getInventoryLotTrace
}

const defaultDependencies: InventoryStockResourceDependencies = {
  getReservations: getInventoryReservations,
  getReservationSummary: getInventoryReservationSummary,
  getLotBalances: getInventoryLotBalances,
  getLotAlerts: getInventoryLotExpiryAlerts,
  getLotTrace: getInventoryLotTrace
}

export const useInventoryStockResources = (
  queries: InventoryStockResourceQueries,
  onError: (messageKey: string, error: unknown) => void,
  dependencies: InventoryStockResourceDependencies = defaultDependencies
) => {
  const reservationLoading = ref(false)
  const reservationSummaryLoading = ref(false)
  const lotBalanceLoading = ref(false)
  const lotAlertLoading = ref(false)
  const lotTraceLoading = ref(false)
  const reservationData = ref<InventoryReservation[]>([])
  const reservationSummaryData = ref<InventoryReservationSummary[]>([])
  const reservationTotal = ref(0)
  const lotBalanceData = ref<InventoryLotBalance[]>([])
  const lotBalanceTotal = ref(0)
  const lotAlertData = ref<InventoryLotExpiryAlert[]>([])
  const lotAlertTotal = ref(0)
  const lotTraceData = ref<InventoryLotTrace[]>([])
  const lotTraceTotal = ref(0)

  const loadReservations = async () => {
    reservationLoading.value = true
    try {
      const page = await dependencies.getReservations(queries.reservations)
      reservationData.value = page.records
      reservationTotal.value = page.total
    } catch (error) {
      onError('inventoryStocks.message.reservationsLoadFailed', error)
    } finally {
      reservationLoading.value = false
    }
  }

  const loadReservationSummary = async () => {
    reservationSummaryLoading.value = true
    try {
      reservationSummaryData.value = await dependencies.getReservationSummary({
        warehouseId: queries.reservations.warehouseId,
        productId: queries.reservations.productId,
        status: queries.reservations.status
      })
    } catch (error) {
      onError('inventoryStocks.message.reservationSummaryLoadFailed', error)
    } finally {
      reservationSummaryLoading.value = false
    }
  }

  const loadLotBalances = async () => {
    lotBalanceLoading.value = true
    try {
      const page = await dependencies.getLotBalances(queries.lotBalances)
      lotBalanceData.value = page.records
      lotBalanceTotal.value = page.total
    } catch (error) {
      onError('inventoryStocks.message.lotStockLoadFailed', error)
    } finally {
      lotBalanceLoading.value = false
    }
  }

  const loadLotAlerts = async () => {
    lotAlertLoading.value = true
    try {
      const page = await dependencies.getLotAlerts(queries.lotAlerts)
      lotAlertData.value = page.records
      lotAlertTotal.value = page.total
    } catch (error) {
      onError('inventoryStocks.message.expiryAlertsLoadFailed', error)
    } finally {
      lotAlertLoading.value = false
    }
  }

  const loadLotTrace = async () => {
    lotTraceLoading.value = true
    try {
      const page = await dependencies.getLotTrace(queries.lotTrace)
      lotTraceData.value = page.records
      lotTraceTotal.value = page.total
    } catch (error) {
      onError('inventoryStocks.message.lotTraceLoadFailed', error)
    } finally {
      lotTraceLoading.value = false
    }
  }

  return {
    loadLotAlerts,
    loadLotBalances,
    loadLotTrace,
    loadReservations,
    loadReservationSummary,
    lotAlertData,
    lotAlertLoading,
    lotAlertTotal,
    lotBalanceData,
    lotBalanceLoading,
    lotBalanceTotal,
    lotTraceData,
    lotTraceLoading,
    lotTraceTotal,
    reservationData,
    reservationLoading,
    reservationSummaryData,
    reservationSummaryLoading,
    reservationTotal
  }
}
