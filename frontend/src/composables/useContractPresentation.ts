import { computed, type Ref } from 'vue'

import type { ContractAlertRecord, ContractLine, ContractRecord } from '@/api/contracts'
import type { Customer, Product, Supplier } from '@/api/masterdata'
import {
  formatLocalizedCurrency,
  formatLocalizedDate,
  formatLocalizedDateTime
} from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

/** Enough of a line to derive quantities; contract and version lines both qualify. */
interface QuantityLine {
  quantity?: number
  committedQuantity?: number
  fulfilledQuantity?: number
}

const STATUS_TAG_TYPES: Record<string, TagType> = {
  ACTIVE: 'success',
  CANCELLED: 'info',
  REJECTED: 'danger',
  SUBMITTED: 'warning'
}

/** The only typed alert; everything else the backend reports is a low-execution alert. */
const EXPIRING_ALERT = 'CONTRACT_EXPIRING'

/** Master-data options read as "code name", tolerating both API field aliases. */
const codeNameLabel = (code?: string, name?: string) => `${code || ''} ${name || ''}`.trim()

export const useContractPresentation = (
  t: Translate,
  resources: {
    alerts: Ref<ContractAlertRecord[]>
    products: Ref<Product[]>
  }
) => {
  const productMap = computed(
    () => new Map(resources.products.value.map((product) => [String(product.id), product]))
  )

  const alertMap = computed(() => new Map(
    resources.alerts.value.map((alert) => [String(alert.contractId), alert.alertTypes || []])
  ))

  const formatDate = (value?: string) => formatLocalizedDate(value) || '-'
  const formatDateTime = (value?: string) => (value ? formatLocalizedDateTime(value) : '-')
  const formatMoney = (value?: number) => formatLocalizedCurrency(Number(value || 0))
  /** An open-ended contract keeps a dash where the end date would be. */
  const effectivePeriodText = (from?: string, to?: string) =>
    `${formatDate(from)} ~ ${to ? formatDate(to) : '-'}`

  const statusText = (status?: string) =>
    (status ? t(`contractPage.statusValue.${status.toLowerCase()}`) : '-')

  const statusType = (status?: string): TagType =>
    (status && STATUS_TAG_TYPES[status]) || 'primary'

  const contractTypeText = (contractType?: string) =>
    t(contractType === 'SALES' ? 'contractPage.sales' : 'contractPage.purchase')

  /** A contract has either a customer or a supplier, never both. */
  const partnerName = (record: Pick<ContractRecord, 'customerName' | 'supplierName'>) =>
    record.customerName || record.supplierName || '-'

  const versionEventText = (eventType?: string) =>
    (eventType ? t(`contractPage.versionEventValue.${eventType.toLowerCase()}`) : '-')

  const changedFieldsText = (fields?: string[]) => (fields?.length
    ? fields
      .map((field) => t(`contractPage.changedFieldValue.${field.toLowerCase()}`))
      .join(t('contractPage.listSeparator'))
    : '-')

  const customerLabel = (customer: Customer) =>
    codeNameLabel(customer.customerCode, customer.customerName)

  const supplierLabel = (supplier: Supplier) =>
    codeNameLabel(supplier.supplierCode, supplier.supplierName)

  const productLabel = (product: Product) =>
    codeNameLabel(product.productCode || product.code, product.productName || product.name)

  /** Version snapshots only store product ids, so labels come from the loaded options. */
  const productLabelById = (productId: string) => {
    const product = productMap.value.get(String(productId))
    return product ? productLabel(product) : String(productId)
  }

  /** Live contract lines carry their own code and name; the id is the last resort. */
  const lineProductLabel = (
    line: Pick<ContractLine, 'productId' | 'productCode' | 'productName'>
  ) => codeNameLabel(line.productCode, line.productName) || String(line.productId)

  const lineAmount = (line: { quantity?: number; unitPrice?: number }) =>
    formatMoney(Number(line.quantity || 0) * Number(line.unitPrice || 0))
  const availableOrderQuantity = (line: QuantityLine) =>
    Math.max(0, Number(line.quantity || 0) - Number(line.committedQuantity || 0))

  const availableFulfillmentQuantity = (line: QuantityLine) =>
    Math.max(0, Number(line.quantity || 0) - Number(line.fulfilledQuantity || 0))

  /** Clamped to 100 so an over-fulfilled line still renders a valid progress bar. */
  const fulfillmentPercentage = (line: QuantityLine) => {
    const quantity = Number(line.quantity || 0)
    return quantity <= 0
      ? 0
      : Math.min(100, Math.round(Number(line.fulfilledQuantity || 0) / quantity * 100))
  }

  const alertsFor = (contractId: string) => alertMap.value.get(String(contractId)) || []

  const alertText = (alertType: string) => t(alertType === EXPIRING_ALERT
    ? 'contractPage.expiringAlert'
    : 'contractPage.lowExecutionAlert')

  return {
    alertText,
    alertsFor,
    availableFulfillmentQuantity,
    availableOrderQuantity,
    changedFieldsText,
    contractTypeText,
    customerLabel,
    effectivePeriodText,
    formatDate,
    formatDateTime,
    formatMoney,
    fulfillmentPercentage,
    lineAmount,
    lineProductLabel,
    partnerName,
    productLabel,
    productLabelById,
    statusText,
    statusType,
    supplierLabel,
    versionEventText
  }
}
