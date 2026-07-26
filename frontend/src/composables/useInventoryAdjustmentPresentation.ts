import { computed, type Ref } from 'vue'

import type { InventoryAdjustment } from '@/api/inventory'
import type { Location, Product, Warehouse } from '@/api/masterdata'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info' | 'warning' | 'danger'

const TYPE_KEYS: Record<string, string> = {
  GAIN: 'inventoryAdjustments.type.gain',
  LOSS: 'inventoryAdjustments.type.loss',
  OTHER: 'inventoryAdjustments.type.other'
}

const TYPE_TAG_TYPES: Record<string, TagType> = {
  GAIN: 'success',
  LOSS: 'danger',
  OTHER: 'info'
}

const STATUS_KEYS: Record<string, string> = {
  DRAFT: 'inventoryAdjustments.status.draft',
  COMPLETED: 'inventoryAdjustments.status.completed',
  CANCELLED: 'inventoryAdjustments.status.cancelled'
}

const STATUS_TAG_TYPES: Record<string, TagType> = {
  DRAFT: 'info',
  COMPLETED: 'success',
  CANCELLED: 'danger'
}

export const useInventoryAdjustmentPresentation = (
  t: Translate,
  resources: {
    warehouses: Ref<Warehouse[]>
    products: Ref<Product[]>
    locations: Ref<Location[]>
  }
) => {
  const warehouseLabel = (warehouse: Warehouse) =>
    warehouse.name
    || warehouse.warehouseName
    || t('inventoryAdjustments.warehouseFallback', { id: warehouse.id })

  const warehouseNameById = (warehouseId?: string | number | null) => {
    if (warehouseId == null || warehouseId === '') return ''
    const warehouse = resources.warehouses.value.find(
      (item) => String(item.id) === String(warehouseId)
    )
    return warehouse ? warehouseLabel(warehouse) : ''
  }

  const productLabel = (product: Product) => {
    const code = product.code || product.productCode || ''
    const name = product.name || product.productName || ''
    if (code && name) return `${code} - ${name}`
    return name || code || t('inventoryAdjustments.productFallback', { id: product.id })
  }

  const findProduct = (productId?: string | number | null) => {
    if (productId == null || productId === '') return undefined
    return resources.products.value.find((item) => String(item.id) === String(productId))
  }

  /** Unknown types fall back to the neutral "other" wording rather than a raw enum. */
  const typeLabel = (type?: string) => t(TYPE_KEYS[type || ''] || TYPE_KEYS.OTHER)

  const typeTagType = (type?: string): TagType =>
    (type && TYPE_TAG_TYPES[type]) || 'info'

  const statusLabel = (status?: string) => {
    if (!status) return ''
    const key = STATUS_KEYS[status]
    return key ? t(key) : status
  }

  const statusTagType = (status?: string): TagType =>
    (status && STATUS_TAG_TYPES[status]) || 'info'

  const locationsForWarehouse = (warehouseId?: string | number | null) => {
    if (warehouseId == null || warehouseId === '' || warehouseId === 0) {
      return resources.locations.value
    }
    return resources.locations.value.filter(
      (location) => String(location.warehouseId) === String(warehouseId)
    )
  }

  const locationLabel = (location: Location) =>
    `${location.locationCode ?? ''} ${location.locationName ?? ''}`.trim()

  /** The list response may omit the warehouse name; fill it from loaded options. */
  const withWarehouseName = (rows: InventoryAdjustment[]) =>
    rows.map((adjustment) => ({
      ...adjustment,
      warehouseName: adjustment.warehouseName || warehouseNameById(adjustment.warehouseId)
    }))

  return {
    findProduct,
    locationLabel,
    locationsForWarehouse,
    productLabel,
    statusLabel,
    statusTagType,
    typeLabel,
    typeTagType,
    warehouseLabel,
    warehouseNameById,
    withWarehouseName
  }
}

export const useInventoryAdjustmentFormPresentation = (
  t: Translate,
  resources: {
    warehouses: Ref<Warehouse[]>
    products: Ref<Product[]>
    locations: Ref<Location[]>
  },
  selectedWarehouseId: Ref<string | number | undefined>
) => {
  const presentation = useInventoryAdjustmentPresentation(t, resources)
  const locationsForSelectedWarehouse = computed(() =>
    presentation.locationsForWarehouse(selectedWarehouseId.value)
  )

  return {
    ...presentation,
    locationsForSelectedWarehouse
  }
}
