import { type ComputedRef, type Ref } from 'vue'

import type { Customer } from '@/api/masterdata'
import { formatLocalizedCurrency, formatLocalizedDateTime } from '@/utils/locale'

export type CustomerPageTexts = {
  creditPeriodValue: string
  cashSettlement: string
  noLimit: string
  [key: string]: string
}

export const useCustomerPresentation = (
  texts: ComputedRef<CustomerPageTexts> | Ref<CustomerPageTexts>,
  displayPreferences: ComputedRef<{ locale: string; timeZone?: string }> | Ref<{ locale: string; timeZone?: string }>
) => {
  const interpolate = (template: string, params: Record<string, string | number>) =>
    template.replace(/\{(\w+)\}/g, (_, key) => String(params[key] ?? ''))

  const formatCurrency = (value?: number | string | null) => {
    const amount = Number(value)
    if (!Number.isFinite(amount)) return '-'
    return formatLocalizedCurrency(amount, {}, displayPreferences.value)
  }

  const formatDateTime = (value?: string | null) => (
    value ? formatLocalizedDateTime(value, {}, displayPreferences.value) || '-' : '-'
  )

  const hasCreditPeriod = (value?: number | null) => value != null && Number(value) > 0

  const formatCreditPeriod = (value?: number | null) => (
    hasCreditPeriod(value)
      ? interpolate(texts.value.creditPeriodValue, { days: Number(value) })
      : texts.value.cashSettlement
  )

  const formatCreditLimit = (value?: number | null) => (
    value ? formatCurrency(value) : texts.value.noLimit
  )

  const companyCount = (rows: Customer[]) =>
    rows.filter((item) => item.type === 'COMPANY').length

  const individualCount = (rows: Customer[]) =>
    rows.filter((item) => item.type === 'INDIVIDUAL').length

  const customerLabel = (row: Customer) =>
    row.name || row.customerName || row.code || row.customerCode || String(row.id)

  return {
    companyCount,
    customerLabel,
    formatCreditLimit,
    formatCreditPeriod,
    formatCurrency,
    formatDateTime,
    hasCreditPeriod,
    individualCount,
    interpolate
  }
}
