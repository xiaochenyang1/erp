import { reactive, ref } from 'vue'

import {
  getInventoryTransaction,
  getInventoryTransactions,
  type InventoryStock,
  type InventoryStockQuery,
  type InventoryTransaction,
  type InventoryTransactionQuery
} from '@/api/inventory'

export interface InventoryStockTransactionListDependencies {
  getTransaction: typeof getInventoryTransaction
  getTransactions: typeof getInventoryTransactions
}

export interface InventoryStockTransactionListCallbacks {
  onDetailError: (messageKey: string) => void
  onListError: (messageKey: string, error: unknown) => void
}

const defaultDependencies: InventoryStockTransactionListDependencies = {
  getTransaction: getInventoryTransaction,
  getTransactions: getInventoryTransactions
}

/** Inventory transaction dialog list, pagination, filters, and row detail. */
export const useInventoryStockTransactionList = (
  stockQuery: InventoryStockQuery,
  callbacks: InventoryStockTransactionListCallbacks,
  dependencies: InventoryStockTransactionListDependencies = defaultDependencies
) => {
  const transactionQuery = reactive<InventoryTransactionQuery>({
    pageNo: 1,
    pageSize: 10,
    warehouseId: undefined,
    productId: undefined,
    bizNo: undefined,
    direction: undefined
  })
  const transactionDialogVisible = ref(false)
  const transactionLoading = ref(false)
  const transactionData = ref<InventoryTransaction[]>([])
  const transactionTotal = ref(0)
  const transactionDetailVisible = ref(false)
  const transactionDetailLoading = ref(false)
  const selectedTransaction = ref<InventoryTransaction>()
  let transactionRequestId = 0
  let transactionDetailRequestId = 0

  const loadTransactions = async () => {
    const requestId = ++transactionRequestId
    transactionLoading.value = true
    try {
      const page = await dependencies.getTransactions(transactionQuery)
      if (requestId !== transactionRequestId) return
      transactionData.value = page.records || []
      transactionTotal.value = page.total || 0
    } catch (error) {
      if (requestId !== transactionRequestId) return
      callbacks.onListError('inventoryStocks.message.transactionsLoadFailed', error)
    } finally {
      if (requestId === transactionRequestId) transactionLoading.value = false
    }
  }

  const handleOpenTransactions = (row?: InventoryStock) => {
    transactionQuery.warehouseId = row?.warehouseId || stockQuery.warehouseId
    transactionQuery.productId = row?.productId || stockQuery.productId
    transactionQuery.pageNo = 1
    transactionQuery.bizNo = undefined
    transactionQuery.direction = undefined
    transactionDialogVisible.value = true
    return loadTransactions()
  }

  const handleTransactionQuery = () => {
    transactionQuery.pageNo = 1
    return loadTransactions()
  }

  const resetTransactionQuery = () => {
    transactionQuery.bizNo = undefined
    transactionQuery.direction = undefined
    return handleTransactionQuery()
  }

  const handleTransactionPageChange = (page: number) => {
    transactionQuery.pageNo = page
    return loadTransactions()
  }

  const handleTransactionSizeChange = (pageSize: number) => {
    transactionQuery.pageSize = pageSize
    transactionQuery.pageNo = 1
    return loadTransactions()
  }

  const handleViewTransaction = async (row: InventoryTransaction) => {
    const requestId = ++transactionDetailRequestId
    transactionDetailVisible.value = true
    selectedTransaction.value = undefined
    transactionDetailLoading.value = true
    try {
      const detail = await dependencies.getTransaction(row.id)
      if (requestId !== transactionDetailRequestId) return
      selectedTransaction.value = detail
    } catch {
      if (requestId !== transactionDetailRequestId) return
      callbacks.onDetailError('inventoryStocks.message.transactionDetailLoadFailed')
      transactionDetailVisible.value = false
    } finally {
      if (requestId === transactionDetailRequestId) transactionDetailLoading.value = false
    }
  }

  return {
    handleOpenTransactions,
    handleTransactionPageChange,
    handleTransactionQuery,
    handleTransactionSizeChange,
    handleViewTransaction,
    loadTransactions,
    resetTransactionQuery,
    selectedTransaction,
    transactionData,
    transactionDetailLoading,
    transactionDetailVisible,
    transactionDialogVisible,
    transactionLoading,
    transactionQuery,
    transactionTotal
  }
}
