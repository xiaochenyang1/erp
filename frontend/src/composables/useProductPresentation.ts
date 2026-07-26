import { computed, type ComputedRef, type Ref } from 'vue'

import type { Product } from '@/api/masterdata'
import {
  formatLocalizedCurrency,
  formatLocalizedDateTime,
  formatLocalizedNumber
} from '@/utils/locale'

export type ProductPageTexts = {
  active: string
  inactive: string
  physicalProduct: string
  goodsProduct: string
  serviceProduct: string
  unitPiece: string
  unitMachine: string
  unitItem: string
  unitBox: string
  [key: string]: string
}

export const useProductPresentation = (
  texts: ComputedRef<ProductPageTexts> | Ref<ProductPageTexts>
) => {
  const interpolate = (template: string, params: Record<string, string | number>) =>
    Object.entries(params).reduce(
      (result, [key, value]) => result.replace(`{${key}}`, String(value)),
      template
    )

  const joinNames = (items: string[], locale: string) => (
    locale === 'en-US' ? items.join(', ') : items.join('、')
  )

  const labelWithCount = (label: string, count: number) => (
    count > 0 ? `${label} (${count})` : label
  )

  const formatCurrency = (value?: number | string | null) => {
    const amount = Number(value)
    return Number.isFinite(amount) ? formatLocalizedCurrency(amount) : '-'
  }

  const formatDateTime = (value?: string | null) => (
    value ? formatLocalizedDateTime(value) : '-'
  )

  const formatNumber = (value?: number | string | null) => (
    value == null || value === '' ? '-' : formatLocalizedNumber(Number(value))
  )

  const productTypeOptions = computed(() => ([
    { label: texts.value.physicalProduct, value: 'PHYSICAL' },
    { label: texts.value.goodsProduct, value: 'GOODS' },
    { label: texts.value.serviceProduct, value: 'SERVICE' }
  ]))

  const unitOptions = computed(() => ([
    { label: texts.value.unitPiece, value: 'PCS' },
    { label: texts.value.unitMachine, value: 'SET' },
    { label: texts.value.unitItem, value: 'ITEM' },
    { label: texts.value.unitBox, value: 'BOX' }
  ]))

  const formatUnit = (value?: string | null) => {
    if (!value) return '-'
    const option = unitOptions.value.find((item) => item.value === value || item.label === value)
    return option?.label || value
  }

  const activeCount = (rows: Product[]) =>
    rows.filter((item) => item.status === 'ACTIVE').length

  const productLabel = (row: Product) =>
    row.name || row.productName || row.code || row.productCode || String(row.id)

  return {
    activeCount,
    formatCurrency,
    formatDateTime,
    formatNumber,
    formatUnit,
    interpolate,
    joinNames,
    labelWithCount,
    productLabel,
    productTypeOptions,
    unitOptions
  }
}
