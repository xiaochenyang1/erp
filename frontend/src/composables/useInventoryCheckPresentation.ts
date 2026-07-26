import { computed, type Ref } from 'vue'

import type { InventoryCheck } from '@/api/inventory'
import type { Location, Product, Warehouse } from '@/api/masterdata'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info' | 'warning' | 'danger'

const STATUS_KEYS: Record<string, string> = {
  COUNTED: 'inventoryChecks.status.counted',
  ADJUSTED: 'inventoryChecks.status.adjusted',
  CANCELLED: 'inventoryChecks.status.cancelled'
}

const STATUS_TAG_TYPES: Record<string, TagType> = {
  COUNTED: 'warning',
  ADJUSTED: 'success',
  CANCELLED: 'danger'
}

export const useInventoryCheckPresentation = (
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
    || t('inventoryChecks.warehouseFallback', { id: warehouse.id })

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
    return name || code || t('inventoryChecks.productFallback', { id: product.id })
  }

  const findProduct = (productId?: string | number | null) => {
    if (productId == null || productId === '') return undefined
    return resources.products.value.find((item) => String(item.id) === String(productId))
  }

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

  const differenceClass = (difference?: number | null) => ({
    'text-danger': (difference ?? 0) < 0,
    'text-success': (difference ?? 0) > 0
  })

  const withWarehouseName = (rows: InventoryCheck[]) =>
    rows.map((check) => ({
      ...check,
      warehouseName: check.warehouseName || warehouseNameById(check.warehouseId)
    }))

  return {
    differenceClass,
    findProduct,
    locationLabel,
    locationsForWarehouse,
    productLabel,
    statusLabel,
    statusTagType,
    warehouseLabel,
    warehouseNameById,
    withWarehouseName
  }
}

export const useInventoryCheckFormPresentation = (
  t: Translate,
  resources: {
    warehouses: Ref<Warehouse[]>
    products: Ref<Product[]>
    locations: Ref<Location[]>
  },
  selectedWarehouseId: Ref<string | number | undefined>
) => {
  const presentation = useInventoryCheckPresentation(t, resources)
  const locationsForSelectedWarehouse = computed(() =>
    presentation.locationsForWarehouse(selectedWarehouseId.value)
  )

  return {
    ...presentation,
    locationsForSelectedWarehouse
  }
}
