import { reactive } from 'vue'

import type {
  InventoryLotBalanceQuery,
  InventoryLotExpiryAlertQuery,
  InventoryLotTraceQuery,
  InventoryReservationQuery,
  InventoryStock,
  InventoryStockQuery,
  InventoryTransactionQuery
} from '@/api/inventory'

export interface InventoryStockScope {
  warehouseId?: string
  productId?: string
}

type ScopedQuery = {
  warehouseId?: string | number
  productId?: string | number
  pageNo?: number
}

export const useInventoryStockQueries = (initialScope: InventoryStockScope = {}) => {
  const queryParams = reactive<InventoryStockQuery>({
    pageNo: 1,
    pageSize: 20,
    warehouseId: initialScope.warehouseId,
    productId: initialScope.productId
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

  const transactionQuery = reactive<InventoryTransactionQuery>({
    pageNo: 1,
    pageSize: 10,
    warehouseId: undefined,
    productId: undefined,
    bizNo: undefined,
    direction: undefined
  })

  const lotAlertQuery = reactive<InventoryLotExpiryAlertQuery>({
    pageNo: 1,
    pageSize: 10,
    warehouseId: undefined,
    productId: undefined,
    lotNo: undefined,
    warningDays: 30,
    status: undefined
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
    target.pageNo = 1
  }

  return {
    applyStockScope,
    lotAlertQuery,
    lotBalanceQuery,
    lotTraceQuery,
    queryParams,
    reservationQuery,
    transactionQuery
  }
}
