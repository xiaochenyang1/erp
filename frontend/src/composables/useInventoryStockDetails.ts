import { ref } from 'vue'

import {
  getInventoryStock,
  type InventoryStock
} from '@/api/inventory'

export interface InventoryStockDetailDependencies {
  getStock: typeof getInventoryStock
}

const defaultDependencies: InventoryStockDetailDependencies = {
  getStock: getInventoryStock
}

export const useInventoryStockDetails = (
  onError: (messageKey: string) => void,
  dependencies: InventoryStockDetailDependencies = defaultDependencies
) => {
  const detailLoading = ref(false)
  const stockDetailVisible = ref(false)
  const selectedStock = ref<InventoryStock>()

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

  return {
    detailLoading,
    handleViewStock,
    selectedStock,
    stockDetailVisible
  }
}
