import { reactive, ref } from 'vue'

import type { ContractAlertRecord, ContractQuery, ContractRecord } from '@/api/contracts'
import type { Customer, Product, Supplier } from '@/api/masterdata'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title: string,
  options?: { type?: string }
) => Promise<unknown>

interface OptionQuery {
  pageNo: number
  pageSize: number
  status?: string
}

/** Pickers load one page of active master data, like the other document pages. */
const OPTION_QUERY: OptionQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }

/**
 * Contract register query, alert badges, master-data options and status actions.
 *
 * Every backend call is injected so the list stays unit-testable without a
 * running API, matching the other document list composables.
 */
export const useContractList = (
  t: Translate,
  options: {
    getContracts: (params: ContractQuery) => Promise<PageResponse<ContractRecord>>
    getContractAlerts: () => Promise<ContractAlertRecord[]>
    exportContracts: (params: ContractQuery) => Promise<Blob>
    getCustomers: (params: OptionQuery) => Promise<PageResponse<Customer>>
    getSuppliers: (params: OptionQuery) => Promise<PageResponse<Supplier>>
    getProducts: (params: OptionQuery) => Promise<PageResponse<Product>>
    downloadBlob: (blob: Blob, filename: string) => void
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const query = reactive<ContractQuery & { pageNo: number; pageSize: number }>({
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    contractType: '',
    status: ''
  })
  const rows = ref<ContractRecord[]>([])
  const total = ref(0)
  const loading = ref(false)
  const alerts = ref<ContractAlertRecord[]>([])
  const customers = ref<Customer[]>([])
  const suppliers = ref<Supplier[]>([])
  const products = ref<Product[]>([])

  const loadData = async () => {
    loading.value = true
    try {
      const page = await options.getContracts(query)
      rows.value = page.records || []
      total.value = page.total || 0
      return true
    } catch {
      options.onError?.(t('contractPage.message.loadFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  const loadOptions = async () => {
    try {
      const [customerPage, supplierPage, productPage] = await Promise.all([
        options.getCustomers(OPTION_QUERY),
        options.getSuppliers(OPTION_QUERY),
        options.getProducts(OPTION_QUERY)
      ])
      customers.value = customerPage.records || []
      suppliers.value = supplierPage.records || []
      products.value = productPage.records || []
      return true
    } catch {
      options.onError?.(t('contractPage.message.optionsFailed'))
      return false
    }
  }

  /** Alerts only decorate the table, so a failure degrades to no badges. */
  const loadAlerts = async () => {
    try {
      alerts.value = await options.getContractAlerts()
      return true
    } catch {
      alerts.value = []
      return false
    }
  }
  const resetQuery = async () => {
    Object.assign(query, { pageNo: 1, keyword: '', contractType: '', status: '' })
    return loadData()
  }

  const handlePageChange = async (page: number) => {
    query.pageNo = page
    return loadData()
  }

  const handleSizeChange = async (size: number) => {
    query.pageSize = size
    query.pageNo = 1
    return loadData()
  }

  const handleExport = async () => {
    try {
      const blob = await options.exportContracts(query)
      options.downloadBlob(blob, t('contractPage.fileName'))
      options.onSuccess?.(t('contractPage.message.exported'))
      return true
    } catch {
      options.onError?.(t('contractPage.message.actionFailed'))
      return false
    }
  }

  /** Dismissing the confirmation is silent; only a failed call reports an error. */
  const runAction = async (
    action: (id: string) => Promise<unknown>,
    row: ContractRecord,
    actionLabelKey: string
  ) => {
    try {
      await options.confirm(
        t('contractPage.message.confirmAction', {
          action: t(actionLabelKey),
          name: row.contractName
        }),
        t('contractPage.message.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    try {
      await action(row.id)
      options.onSuccess?.(t('contractPage.message.actionDone'))
      await loadData()
      return true
    } catch {
      options.onError?.(t('contractPage.message.actionFailed'))
      return false
    }
  }

  return {
    alerts,
    customers,
    handleExport,
    handlePageChange,
    handleSizeChange,
    loadAlerts,
    loadData,
    loadOptions,
    loading,
    products,
    query,
    resetQuery,
    rows,
    runAction,
    suppliers,
    total
  }
}
