import { computed, type ComputedRef, type Ref } from 'vue'

import type { Supplier } from '@/api/masterdata'
import { formatLocalizedCurrency, formatLocalizedDateTime, type DisplayPreferences } from '@/utils/locale'

type DisplayPreferencesLike = { locale: string; timeZone?: string }

export type SupplierPageTexts = {
  creditPeriodValue: string
  cashSettlement: string
  bankTransfer: string
  cash: string
  monthly: string
  cod: string
  [key: string]: string
}

export const useSupplierPresentation = (
  texts: ComputedRef<SupplierPageTexts> | Ref<SupplierPageTexts>,
  displayPreferences: ComputedRef<DisplayPreferencesLike> | Ref<DisplayPreferencesLike>
) => {
  const interpolate = (template: string, params: Record<string, string | number>) =>
    template.replace(/\{(\w+)\}/g, (_, key) => String(params[key] ?? ''))

  const joinNames = (items: string[], locale: string) => (
    locale === 'en-US' ? items.join(', ') : items.join('、')
  )

  const labelWithCount = (label: string, count: number) => (
    count > 0 ? `${label} (${count})` : label
  )

  const formatCurrency = (value?: number | string | null) => {
    const amount = Number(value)
    if (!Number.isFinite(amount)) return '-'
    return formatLocalizedCurrency(amount, {}, displayPreferences.value as DisplayPreferences)
  }

  const formatDateTime = (value?: string | null) => (
    value ? formatLocalizedDateTime(value, {}, displayPreferences.value as DisplayPreferences) || '-' : '-'
  )

  const hasCreditPeriod = (value?: number | string | null) => Number(value) > 0

  const formatCreditPeriod = (value?: number | string | null) => (
    hasCreditPeriod(value)
      ? interpolate(texts.value.creditPeriodValue, { days: Number(value) })
      : texts.value.cashSettlement
  )

  const settlementMethodOptions = computed(() => ([
    { label: texts.value.bankTransfer, value: 'BANK_TRANSFER' },
    { label: texts.value.cash, value: 'CASH' },
    { label: texts.value.monthly, value: 'MONTHLY' },
    { label: texts.value.cod, value: 'COD' }
  ]))

  const settlementMethodLabel = (value?: string | null) => (
    settlementMethodOptions.value.find((item) => item.value === value)?.label || value || '-'
  )

  const activeCount = (rows: Supplier[]) =>
    rows.filter((item) => item.status === 'ACTIVE').length

  const supplierLabel = (row: Supplier) =>
    row.name || row.supplierName || row.code || row.supplierCode || String(row.id)

  return {
    activeCount,
    formatCreditPeriod,
    formatCurrency,
    formatDateTime,
    hasCreditPeriod,
    interpolate,
    joinNames,
    labelWithCount,
    settlementMethodLabel,
    settlementMethodOptions,
    supplierLabel
  }
}
