import { computed, type Ref } from 'vue'

import type { BOMItem } from '@/api/production'
import type { Product } from '@/api/masterdata'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary' | ''

const STATUS_KEYS: Record<string, string> = {
  ACTIVE: 'productionBom.status.active',
  DISABLED: 'productionBom.status.disabled'
}

const STATUS_TAG_TYPES: Record<string, TagType> = {
  ACTIVE: 'success',
  DISABLED: 'danger'
}

/** Display helpers for BOM product/material labels and status tags. */
export const useProductionBomPresentation = (
  t: Translate,
  products: Ref<Product[]>
) => {
  const productMap = computed(
    () => new Map(products.value.map((product) => [String(product.id), product]))
  )

  const productLabel = (product: Product) => {
    const code = product.productCode || product.code || ''
    const name = product.productName || product.name || ''
    return code && name
      ? `${code} - ${name}`
      : name || code || t('productionBom.productFallback', { id: product.id })
  }

  const productById = (id?: string | number | null) => {
    if (id == null || id === '') return undefined
    return productMap.value.get(String(id))
  }

  const productLabelById = (id?: string | number | null) => {
    const product = productById(id)
    return product ? productLabel(product) : id || '-'
  }

  /** Prefer the live product option, then fall back to denormalized row fields. */
  const materialLabel = (row: BOMItem) => {
    const product = productById(row.materialId ?? row.materialProductId)
    if (product) return productLabel(product)
    const code = row.materialCode || ''
    const name = row.materialName || ''
    return code && name
      ? `${code} - ${name}`
      : name || code || row.materialId || '-'
  }

  const materialUnit = (row: BOMItem) => {
    const product = productById(row.materialId ?? row.materialProductId)
    return product?.unitName || product?.unit || row.unit || ''
  }

  const getStatusLabel = (status?: string) => {
    if (!status) return ''
    const key = STATUS_KEYS[status]
    return key ? t(key) : status
  }

  const getStatusType = (status?: string): TagType =>
    (status && STATUS_TAG_TYPES[status]) || ''

  return {
    getStatusLabel,
    getStatusType,
    materialLabel,
    materialUnit,
    productById,
    productLabel,
    productLabelById
  }
}
