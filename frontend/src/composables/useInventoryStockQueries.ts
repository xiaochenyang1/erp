import { reactive } from 'vue'

import type {
  InventoryLotBalanceQuery,
  InventoryLotTraceQuery,
  InventoryReservationQuery,
  InventoryStock,
  InventoryStockQuery
} from '@/api/inventory'

export interface InventoryStockScope {
  warehouseId?: string
  productId?: string
  locationId?: string
}

type ScopedQuery = {
  warehouseId?: string | number
  productId?: string | number
  locationId?: string | number
  pageNo?: number
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

  const lotBalanceQuery = reactive<InventoryLotBalanceQuery>({
    pageNo: 1,
    pageSize: 10,
    warehouseId: undefined,
    productId: undefined,
    lotNo: undefined,
    expiringWithinDays: undefined
  })

  const lotTraceQuery = reactive<InventoryLotTraceQuery>({
    pageNo: 1,
    pageSize: 10,
    warehouseId: undefined,
    productId: undefined,
    lotNo: undefined,
    direction: undefined
  })

  const applyStockScope = (target: ScopedQuery, row?: InventoryStock) => {
    target.warehouseId = row?.warehouseId || queryParams.warehouseId
    target.productId = row?.productId || queryParams.productId
    if ('locationId' in target) {
      target.locationId = row?.locationId || queryParams.locationId
    }
    target.pageNo = 1
  }

  return {
    applyStockScope,
    lotBalanceQuery,
    lotTraceQuery,
    queryParams,
    reservationQuery
  }
}
