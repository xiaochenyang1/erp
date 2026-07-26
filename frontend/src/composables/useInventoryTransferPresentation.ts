import { computed, type Ref } from 'vue'

import type { InventoryTransfer } from '@/api/inventory'
import type { Location, Product, Warehouse } from '@/api/masterdata'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info' | 'warning' | 'danger'

const STATUS_KEYS: Record<string, string> = {
  DRAFT: 'inventoryTransfers.status.draft',
  COMPLETED: 'inventoryTransfers.status.completed',
  CANCELLED: 'inventoryTransfers.status.cancelled'
}

const STATUS_TAG_TYPES: Record<string, TagType> = {
  DRAFT: 'info',
  COMPLETED: 'success',
  CANCELLED: 'danger'
}

export const useInventoryTransferPresentation = (
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
    || t('inventoryTransfers.warehouseFallback', { id: warehouse.id })

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
    return name || code || t('inventoryTransfers.productFallback', { id: product.id })
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

  /** Backfill both warehouse names so the list renders without a second lookup. */
  const withWarehouseNames = (rows: InventoryTransfer[]) =>
    rows.map((transfer) => ({
      ...transfer,
      fromWarehouseName: transfer.fromWarehouseName || warehouseNameById(transfer.fromWarehouseId),
      toWarehouseName: transfer.toWarehouseName || warehouseNameById(transfer.toWarehouseId)
    }))

  return {
    findProduct,
    locationLabel,
    locationsForWarehouse,
    productLabel,
    statusLabel,
    statusTagType,
    warehouseLabel,
    warehouseNameById,
    withWarehouseNames
  }
}

export const useInventoryTransferFormPresentation = (
  t: Translate,
  resources: {
    warehouses: Ref<Warehouse[]>
    products: Ref<Product[]>
    locations: Ref<Location[]>
  },
  selected: {
    fromWarehouseId: Ref<string | number | undefined>
    toWarehouseId: Ref<string | number | undefined>
  }
) => {
  const presentation = useInventoryTransferPresentation(t, resources)

  const locationsForFromWarehouse = computed(() =>
    presentation.locationsForWarehouse(selected.fromWarehouseId.value)
  )
  const locationsForToWarehouse = computed(() =>
    presentation.locationsForWarehouse(selected.toWarehouseId.value)
  )

  return {
    ...presentation,
    locationsForFromWarehouse,
    locationsForToWarehouse
  }
}
