import { ref } from 'vue'

import {
  getInventoryReservation,
  getInventoryReservationSource,
  getInventoryStock,
  type InventoryReservation,
  type InventoryReservationDetail,
  type InventoryReservationSource,
  type InventoryStock
} from '@/api/inventory'

export interface InventoryStockDetailDependencies {
  getStock: typeof getInventoryStock
  getReservation: typeof getInventoryReservation
  getReservationSource: typeof getInventoryReservationSource
}

const defaultDependencies: InventoryStockDetailDependencies = {
  getStock: getInventoryStock,
  getReservation: getInventoryReservation,
  getReservationSource: getInventoryReservationSource
}

export const useInventoryStockDetails = (
  onError: (messageKey: string) => void,
  dependencies: InventoryStockDetailDependencies = defaultDependencies
) => {
  const detailLoading = ref(false)
  const reservationSourceLoading = ref(false)
  const stockDetailVisible = ref(false)
  const reservationDetailVisible = ref(false)
  const reservationSourceVisible = ref(false)
  const selectedStock = ref<InventoryStock>()
  const reservationDetail = ref<InventoryReservationDetail>()
  const reservationSourceDetail = ref<InventoryReservationSource>()

  const handleViewStock = async (row: InventoryStock) => {
    stockDetailVisible.value = true
    selectedStock.value = undefined
    detailLoading.value = true
    try {
      selectedStock.value = await dependencies.getStock(row.id)
    } catch {
      onError('inventoryStocks.message.stockDetailLoadFailed')
      stockDetailVisible.value = false
    } finally {
      detailLoading.value = false
    }
  }

  const handleViewReservation = async (row: InventoryReservation) => {
    reservationDetail.value = undefined
    try {
      reservationDetail.value = await dependencies.getReservation(row.id)
      reservationDetailVisible.value = true
    } catch {
      onError('inventoryStocks.message.reservationDetailLoadFailed')
      reservationDetailVisible.value = false
    }
  }

  const handleViewReservationSource = async (row: InventoryReservation) => {
    reservationSourceVisible.value = true
    reservationSourceDetail.value = undefined
    reservationSourceLoading.value = true
    try {
      reservationSourceDetail.value = await dependencies.getReservationSource({
        sourceType: row.sourceType,
        sourceId: row.sourceId,
        sourceNo: row.sourceNo
      })
    } catch {
      onError('inventoryStocks.message.sourceReservationLoadFailed')
      reservationSourceVisible.value = false
    } finally {
      reservationSourceLoading.value = false
    }
  }

  return {
    detailLoading,
    handleViewReservation,
    handleViewReservationSource,
    handleViewStock,
    reservationDetail,
    reservationDetailVisible,
    reservationSourceDetail,
    reservationSourceLoading,
    reservationSourceVisible,
    selectedStock,
    stockDetailVisible
  }
}
