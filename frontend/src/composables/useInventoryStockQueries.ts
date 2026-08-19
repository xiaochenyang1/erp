import { reactive } from 'vue'

import type {
  InventoryReservationQuery,
  InventoryStockQuery
} from '@/api/inventory'

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

  const reservationQuery = reactive<InventoryReservationQuery>({
    pageNo: 1,
    pageSize: 10,
    warehouseId: undefined,
    productId: undefined,
    status: 'ACTIVE',
    sourceNo: undefined
  })

  return {
    queryParams,
    reservationQuery
  }
}
