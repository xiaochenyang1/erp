import type { Location, Product, Warehouse } from '@/api/masterdata'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary'

/** Display helpers for inventory serial ledger. */
export const useInventorySerialPresentation = (
  t: Translate,
  resources: {
    warehouses: { value: Warehouse[] }
    locations: { value: Location[] }
  }
) => {
  const warehouseLabel = (warehouseId?: string | number | null) => {
    if (warehouseId == null || warehouseId === '') return '-'
    const warehouse = resources.warehouses.value.find(
      (item) => String(item.id) === String(warehouseId)
    )
    return warehouse
      ? warehouse.name || warehouse.warehouseName || String(warehouseId)
      : String(warehouseId)
  }

  const locationLabel = (locationId?: string | number | null) => {
    if (locationId == null || locationId === '') return '-'
    const location = resources.locations.value.find(
      (item) => String(item.id) === String(locationId)
    )
    return location
      ? `${location.locationCode} ${location.locationName}`
      : String(locationId)
  }

  const productLabel = (product: Product) => {
    const code = product.code || product.productCode || ''
    const name = product.name || product.productName || ''
    return [code, name].filter(Boolean).join(' ') || String(product.id)
  }

  const statusLabel = (status?: string) => {
    const map: Record<string, string> = {
      IN_STOCK: t('inventorySerial.statusValue.inStock'),
      ISSUED: t('inventorySerial.statusValue.issued'),
      SCRAPPED: t('inventorySerial.statusValue.scrapped')
    }
    return map[String(status || '')] || status || '-'
  }

  const statusType = (status?: string): TagType => {
    const map: Record<string, TagType> = {
      IN_STOCK: 'success',
      ISSUED: 'info',
      SCRAPPED: 'warning'
    }
    return map[String(status || '')] || 'info'
  }

  const locationsForWarehouse = (
    warehouseId?: string | number | null,
    all: Location[] = resources.locations.value
  ) => {
    if (!warehouseId) return all
    return all.filter((location) => String(location.warehouseId) === String(warehouseId))
  }

  return {
    locationLabel,
    locationsForWarehouse,
    productLabel,
    statusLabel,
    statusType,
    warehouseLabel
  }
}
