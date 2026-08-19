import { ref } from 'vue'

import {
  getInventoryLotBalances,
  getInventoryLotTrace,
  getInventoryReservationSummary,
  getInventoryReservations,
  type InventoryLotBalance,
  type InventoryLotBalanceQuery,
  type InventoryLotTrace,
  type InventoryLotTraceQuery,
  type InventoryReservation,
  type InventoryReservationQuery,
  type InventoryReservationSummary
} from '@/api/inventory'

export interface InventoryStockResourceQueries {
  reservations: InventoryReservationQuery
  lotBalances: InventoryLotBalanceQuery
  lotTrace: InventoryLotTraceQuery
}

export interface InventoryStockResourceDependencies {
  getReservations: typeof getInventoryReservations
  getReservationSummary: typeof getInventoryReservationSummary
  getLotBalances: typeof getInventoryLotBalances
  getLotTrace: typeof getInventoryLotTrace
}

const defaultDependencies: InventoryStockResourceDependencies = {
  getReservations: getInventoryReservations,
  getReservationSummary: getInventoryReservationSummary,
  getLotBalances: getInventoryLotBalances,
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
  const lotTraceLoading = ref(false)
  const reservationData = ref<InventoryReservation[]>([])
  const reservationSummaryData = ref<InventoryReservationSummary[]>([])
  const reservationTotal = ref(0)
  const lotBalanceData = ref<InventoryLotBalance[]>([])
  const lotBalanceTotal = ref(0)
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
    loadLotBalances,
    loadLotTrace,
    loadReservations,
    loadReservationSummary,
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
