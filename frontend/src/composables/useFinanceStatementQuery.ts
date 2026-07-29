import { ref } from 'vue'

import type { PartnerStatement } from '@/api/finance'
import type {
  Customer,
  CustomerQuery,
  Supplier,
  SupplierQuery
} from '@/api/masterdata'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export type FinanceStatementPartnerType = 'CUSTOMER' | 'SUPPLIER'

export type FinanceStatementQueryParams = {
  partnerType: FinanceStatementPartnerType
  partnerId: string | number
  dateFrom: string
  dateTo: string
}

type StatementPartner = Customer | Supplier

/** Partner resources, business-month defaults and statement query state. */
export const useFinanceStatementQuery = (
  t: Translate,
  options: {
    getCustomers: (params: CustomerQuery) => Promise<PageResponse<Customer>>
    getSuppliers: (params: SupplierQuery) => Promise<PageResponse<Supplier>>
    getPartnerStatement: (params: FinanceStatementQueryParams) => Promise<PartnerStatement>
    getBusinessMonthDateRange: () => [string, string]
    onError?: Notify
    onWarning?: Notify
  }
) => {
  const partnerType = ref<FinanceStatementPartnerType>('CUSTOMER')
  const partnerId = ref('')
  const range = ref<string[]>([...options.getBusinessMonthDateRange()])
  const partners = ref<StatementPartner[]>([])
  const statement = ref<PartnerStatement>()
  const loading = ref(false)
  let partnerRequestId = 0
  let statementRequestId = 0

  const loadPartners = async () => {
    const requestId = ++partnerRequestId
    const requestedType = partnerType.value
    try {
      if (requestedType === 'CUSTOMER') {
        const page = await options.getCustomers({
          pageNo: 1,
          pageSize: 200,
          status: 'ACTIVE'
        })
        if (requestId !== partnerRequestId || partnerType.value !== requestedType) return false
        partners.value = (page.records || []).map((customer) => ({
          ...customer,
          name: customer.customerName || customer.name
        }))
      } else {
        const page = await options.getSuppliers({
          pageNo: 1,
          pageSize: 200,
          status: 'ACTIVE'
        })
        if (requestId !== partnerRequestId || partnerType.value !== requestedType) return false
        partners.value = (page.records || []).map((supplier) => ({
          ...supplier,
          name: supplier.supplierName || supplier.name
        }))
      }
      return true
    } catch {
      if (requestId !== partnerRequestId || partnerType.value !== requestedType) return false
      partners.value = []
      options.onError?.(t('financeStatement.message.optionsLoadFailed'))
      return false
    }
  }

  const handlePartnerTypeChange = (type: FinanceStatementPartnerType) => {
    partnerRequestId += 1
    partnerType.value = type
    partnerId.value = ''
    partners.value = []
    clearStatement()
    return loadPartners()
  }

  const clearStatement = () => {
    statementRequestId += 1
    statement.value = undefined
    loading.value = false
  }

  const loadData = async () => {
    const selectedRange = range.value || []
    const [dateFrom, dateTo] = selectedRange
    if (
      !partnerId.value
      || !dateFrom
      || !dateTo
      || selectedRange.length !== 2
      || dateFrom > dateTo
    ) {
      options.onWarning?.(t('financeStatement.message.selectPartnerAndRange'))
      return false
    }

    const requestId = ++statementRequestId
    const requestedType = partnerType.value
    const requestedPartnerId = partnerId.value
    loading.value = true
    const isCurrentRequest = () => (
      requestId === statementRequestId
      && partnerType.value === requestedType
      && partnerId.value === requestedPartnerId
      && range.value?.[0] === dateFrom
      && range.value?.[1] === dateTo
    )
    try {
      const result = await options.getPartnerStatement({
        partnerType: requestedType,
        partnerId: requestedPartnerId,
        dateFrom,
        dateTo
      })
      if (!isCurrentRequest()) return false
      statement.value = result
      return true
    } catch {
      if (!isCurrentRequest()) return false
      options.onError?.(t('financeStatement.message.loadFailed'))
      return false
    } finally {
      if (requestId === statementRequestId) loading.value = false
    }
  }

  return {
    clearStatement,
    handlePartnerTypeChange,
    loadData,
    loadPartners,
    loading,
    partnerId,
    partners,
    partnerType,
    range,
    statement
  }
}
