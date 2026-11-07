import { reactive, ref } from 'vue'

import {
  getInventoryLotExpiryAlerts,
  type InventoryLotExpiryAlert,
  type InventoryLotExpiryAlertQuery,
  type InventoryStock,
  type InventoryStockQuery
} from '@/api/inventory'

export interface InventoryStockExpiryAlertListDependencies {
  getLotAlerts: typeof getInventoryLotExpiryAlerts
}

export interface InventoryStockExpiryAlertListCallbacks {
  onError: (messageKey: string, error: unknown) => void
}

const defaultDependencies: InventoryStockExpiryAlertListDependencies = {
  getLotAlerts: getInventoryLotExpiryAlerts
}

/** Expiry-alert dialog filters, pagination, loading, and scoped opening. */
export const useInventoryStockExpiryAlertList = (
  stockQuery: InventoryStockQuery,
  callbacks: InventoryStockExpiryAlertListCallbacks,
  dependencies: InventoryStockExpiryAlertListDependencies = defaultDependencies
) => {
  const lotAlertQuery = reactive<InventoryLotExpiryAlertQuery>({
    pageNo: 1,
    pageSize: 10,
    warehouseId: undefined,
    productId: undefined,
    lotNo: undefined,
    warningDays: 30,
    status: undefined
  })
  const lotAlertDialogVisible = ref(false)
  const lotAlertLoading = ref(false)
  const lotAlertData = ref<InventoryLotExpiryAlert[]>([])
  const lotAlertTotal = ref(0)
  let requestId = 0

  const loadLotAlerts = async () => {
    const currentRequestId = ++requestId
    lotAlertLoading.value = true
    try {
      const page = await dependencies.getLotAlerts(lotAlertQuery)
      if (currentRequestId !== requestId) return
      lotAlertData.value = page.records || []
      lotAlertTotal.value = page.total || 0
    } catch (error) {
      if (currentRequestId !== requestId) return
      callbacks.onError('inventoryStocks.message.expiryAlertsLoadFailed', error)
    } finally {
      if (currentRequestId === requestId) lotAlertLoading.value = false
    }
  }

  const handleOpenLotAlerts = (row?: InventoryStock) => {
    lotAlertQuery.warehouseId = row?.warehouseId || stockQuery.warehouseId
    lotAlertQuery.productId = row?.productId || stockQuery.productId
    lotAlertQuery.pageNo = 1
    lotAlertQuery.lotNo = undefined
    lotAlertDialogVisible.value = true
    return loadLotAlerts()
  }

  const handleLotAlertQuery = () => {
    lotAlertQuery.pageNo = 1
    return loadLotAlerts()
  }

  const resetLotAlertQuery = () => {
    lotAlertQuery.lotNo = undefined
    lotAlertQuery.warningDays = 30
    lotAlertQuery.status = undefined
    return handleLotAlertQuery()
  }

  const handleLotAlertPageChange = (page: number) => {
    lotAlertQuery.pageNo = page
    return loadLotAlerts()
  }

  const handleLotAlertSizeChange = (pageSize: number) => {
    lotAlertQuery.pageSize = pageSize
    lotAlertQuery.pageNo = 1
    return loadLotAlerts()
  }

  return {
    handleLotAlertPageChange,
    handleLotAlertQuery,
    handleLotAlertSizeChange,
    handleOpenLotAlerts,
    loadLotAlerts,
    lotAlertData,
    lotAlertDialogVisible,
    lotAlertLoading,
    lotAlertQuery,
    lotAlertTotal,
    resetLotAlertQuery
  }
}
