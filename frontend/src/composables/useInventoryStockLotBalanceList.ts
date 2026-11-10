import { reactive, ref } from 'vue'

import {
  getInventoryLotBalance,
  getInventoryLotBalances,
  getInventoryLotTrace,
  type InventoryLotBalance,
  type InventoryLotBalanceQuery,
  type InventoryLotTrace,
  type InventoryLotTraceQuery,
  type InventoryStock,
  type InventoryStockQuery
} from '@/api/inventory'

export interface InventoryStockLotBalanceListDependencies {
  getLotBalance: typeof getInventoryLotBalance
  getLotBalances: typeof getInventoryLotBalances
  getLotTrace: typeof getInventoryLotTrace
}

export interface InventoryStockLotBalanceListCallbacks {
  onDetailError: (messageKey: string) => void
  onListError: (messageKey: string, error: unknown) => void
}

const defaultDependencies: InventoryStockLotBalanceListDependencies = {
  getLotBalance: getInventoryLotBalance,
  getLotBalances: getInventoryLotBalances,
  getLotTrace: getInventoryLotTrace
}

/** Lot-balance list/detail and the trace list opened from a lot row. */
export const useInventoryStockLotBalanceList = (
  stockQuery: InventoryStockQuery,
  callbacks: InventoryStockLotBalanceListCallbacks,
  dependencies: InventoryStockLotBalanceListDependencies = defaultDependencies
) => {
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
  const lotBalanceDialogVisible = ref(false)
  const lotBalanceDetailVisible = ref(false)
  const lotTraceDialogVisible = ref(false)
  const lotBalanceLoading = ref(false)
  const lotBalanceDetailLoading = ref(false)
  const lotTraceLoading = ref(false)
  const lotBalanceData = ref<InventoryLotBalance[]>([])
  const lotBalanceTotal = ref(0)
  const lotTraceData = ref<InventoryLotTrace[]>([])
  const lotTraceTotal = ref(0)
  const selectedLotBalance = ref<InventoryLotBalance>()
  let lotBalanceRequestId = 0
  let lotBalanceDetailRequestId = 0
  let lotTraceRequestId = 0

  const loadLotBalances = async () => {
    const requestId = ++lotBalanceRequestId
    lotBalanceLoading.value = true
    try {
      const page = await dependencies.getLotBalances(lotBalanceQuery)
      if (requestId !== lotBalanceRequestId) return
      lotBalanceData.value = page.records || []
      lotBalanceTotal.value = page.total || 0
    } catch (error) {
      if (requestId !== lotBalanceRequestId) return
      callbacks.onListError('inventoryStocks.message.lotStockLoadFailed', error)
    } finally {
      if (requestId === lotBalanceRequestId) lotBalanceLoading.value = false
    }
  }

  const handleOpenLotBalances = (row?: InventoryStock) => {
    lotBalanceQuery.warehouseId = row?.warehouseId || stockQuery.warehouseId
    lotBalanceQuery.productId = row?.productId || stockQuery.productId
    lotBalanceQuery.pageNo = 1
    lotBalanceQuery.lotNo = undefined
    lotBalanceDialogVisible.value = true
    return loadLotBalances()
  }

  const handleLotBalanceQuery = () => {
    lotBalanceQuery.pageNo = 1
    return loadLotBalances()
  }

  const resetLotBalanceQuery = () => {
    lotBalanceQuery.lotNo = undefined
    lotBalanceQuery.expiringWithinDays = undefined
    return handleLotBalanceQuery()
  }

  const handleLotBalancePageChange = (page: number) => {
    lotBalanceQuery.pageNo = page
    return loadLotBalances()
  }

  const handleLotBalanceSizeChange = (pageSize: number) => {
    lotBalanceQuery.pageSize = pageSize
    lotBalanceQuery.pageNo = 1
    return loadLotBalances()
  }

  const handleViewLotBalance = async (row: InventoryLotBalance) => {
    const requestId = ++lotBalanceDetailRequestId
    lotBalanceDetailVisible.value = true
    selectedLotBalance.value = undefined
    lotBalanceDetailLoading.value = true
    try {
      const detail = await dependencies.getLotBalance(row.id)
      if (requestId !== lotBalanceDetailRequestId) return
      selectedLotBalance.value = detail
    } catch {
      if (requestId !== lotBalanceDetailRequestId) return
      callbacks.onDetailError('inventoryStocks.message.lotStockDetailLoadFailed')
      lotBalanceDetailVisible.value = false
    } finally {
      if (requestId === lotBalanceDetailRequestId) lotBalanceDetailLoading.value = false
    }
  }

  const loadLotTrace = async () => {
    const requestId = ++lotTraceRequestId
    lotTraceLoading.value = true
    try {
      const page = await dependencies.getLotTrace(lotTraceQuery)
      if (requestId !== lotTraceRequestId) return
      lotTraceData.value = page.records || []
      lotTraceTotal.value = page.total || 0
    } catch (error) {
      if (requestId !== lotTraceRequestId) return
      callbacks.onListError('inventoryStocks.message.lotTraceLoadFailed', error)
    } finally {
      if (requestId === lotTraceRequestId) lotTraceLoading.value = false
    }
  }

  const handleOpenLotTrace = (row: InventoryLotBalance) => {
    Object.assign(lotTraceQuery, {
      pageNo: 1,
      warehouseId: row.warehouseId,
      productId: row.productId,
      lotNo: row.lotNo,
      direction: undefined
    })
    lotTraceDialogVisible.value = true
    return loadLotTrace()
  }

  const handleLotTracePageChange = (page: number) => {
    lotTraceQuery.pageNo = page
    return loadLotTrace()
  }

  const handleLotTraceSizeChange = (pageSize: number) => {
    lotTraceQuery.pageSize = pageSize
    lotTraceQuery.pageNo = 1
    return loadLotTrace()
  }

  return {
    handleLotBalancePageChange,
    handleLotBalanceQuery,
    handleLotBalanceSizeChange,
    handleLotTracePageChange,
    handleLotTraceSizeChange,
    handleOpenLotBalances,
    handleOpenLotTrace,
    handleViewLotBalance,
    loadLotBalances,
    loadLotTrace,
    lotBalanceData,
    lotBalanceDetailLoading,
    lotBalanceDetailVisible,
    lotBalanceDialogVisible,
    lotBalanceLoading,
    lotBalanceQuery,
    lotBalanceTotal,
    lotTraceData,
    lotTraceDialogVisible,
    lotTraceLoading,
    lotTraceQuery,
    lotTraceTotal,
    resetLotBalanceQuery,
    selectedLotBalance
  }
}
