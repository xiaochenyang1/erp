import { reactive } from 'vue'

import type { InventoryStockQuery } from '@/api/inventory'

export interface InventoryStockScope {
  warehouseId?: string
  productId?: string
  locationId?: string
}

export const useInventoryStockQueries = (initialScope: InventoryStockScope = {}) => {
  const queryParams = reactive<InventoryStockQuery>({
    pageNo: 1,
    pageSize: 20,
    warehouseId: initialScope.warehouseId,
    productId: initialScope.productId,
    locationId: initialScope.locationId
  })

  return {
    queryParams
  }
}
