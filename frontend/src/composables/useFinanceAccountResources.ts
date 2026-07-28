import { ref } from 'vue'

import type { Customer, Supplier } from '@/api/masterdata'
import type { PageResponse } from '@/types/common'

type Translate = (key: string) => string
type ReportError = (message: string, error: unknown) => void

/** Permission-gated customer and supplier filter resources. */
export const useFinanceAccountResources = (
  t: Translate,
  options: {
    canLoadCustomers: () => boolean
    canLoadSuppliers: () => boolean
    getCustomers: (params: {
      pageNo: number
      pageSize: number
      status: string
    }) => Promise<PageResponse<Customer>>
    getSuppliers: (params: {
      pageNo: number
      pageSize: number
      status: string
    }) => Promise<PageResponse<Supplier>>
    reportError?: ReportError
  }
) => {
  const customers = ref<Customer[]>([])
  const suppliers = ref<Supplier[]>([])

  const loadCustomers = async () => {
    if (!options.canLoadCustomers()) return false
    try {
      const response = await options.getCustomers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
      customers.value = response.records || []
      return true
    } catch (error) {
      options.reportError?.(t('financeAccount.message.customersLoadFailed'), error)
      return false
    }
  }

  const loadSuppliers = async () => {
    if (!options.canLoadSuppliers()) return false
    try {
      const response = await options.getSuppliers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
      suppliers.value = response.records || []
      return true
    } catch (error) {
      options.reportError?.(t('financeAccount.message.suppliersLoadFailed'), error)
      return false
    }
  }

  return {
    customers,
    loadCustomers,
    loadSuppliers,
    suppliers
  }
}
