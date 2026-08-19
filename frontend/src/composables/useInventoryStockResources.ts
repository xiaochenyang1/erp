import { ref } from 'vue'

import {
  getInventoryReservationSummary,
  getInventoryReservations,
  type InventoryReservation,
  type InventoryReservationQuery,
  type InventoryReservationSummary
} from '@/api/inventory'

export interface InventoryStockResourceQueries {
  reservations: InventoryReservationQuery
}

export interface InventoryStockResourceDependencies {
  getReservations: typeof getInventoryReservations
  getReservationSummary: typeof getInventoryReservationSummary
}

const defaultDependencies: InventoryStockResourceDependencies = {
  getReservations: getInventoryReservations,
  getReservationSummary: getInventoryReservationSummary
}

export const useInventoryStockResources = (
  queries: InventoryStockResourceQueries,
  onError: (messageKey: string, error: unknown) => void,
  dependencies: InventoryStockResourceDependencies = defaultDependencies
) => {
  const reservationLoading = ref(false)
  const reservationSummaryLoading = ref(false)
  const reservationData = ref<InventoryReservation[]>([])
  const reservationSummaryData = ref<InventoryReservationSummary[]>([])
  const reservationTotal = ref(0)

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

  return {
    loadReservations,
    loadReservationSummary,
    reservationData,
    reservationLoading,
    reservationSummaryData,
    reservationSummaryLoading,
    reservationTotal
  }
}
