import { computed, type Ref } from 'vue'

import type { Location } from '@/api/masterdata'
import type { SalesDeliveryItem } from '@/api/sales'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary'

export const useSalesDeliveryPresentation = (
  t: Translate,
  locations: Ref<Location[]>,
  warehouseId?: Ref<string | number | undefined | null> | (() => string | number | undefined | null)
) => {
  const logisticsStatusLabel = (status?: string) => {
    const key = {
      PENDING_SHIP: 'pendingShip',
      PICKED_UP: 'pickedUp',
      IN_TRANSIT: 'inTransit',
      DELIVERED: 'delivered'
    }[String(status || 'PENDING_SHIP')]
    return key ? t(`salesDelivery.logistics.${key}`) : (status || '-')
  }

  const logisticsStatusType = (status?: string): TagType => {
    return ({
      PENDING_SHIP: 'info',
      PICKED_UP: 'warning',
      IN_TRANSIT: 'primary',
      DELIVERED: 'success'
    }[String(status || 'PENDING_SHIP')] || 'info') as TagType
  }

  const resolveWarehouseId = () => {
    if (!warehouseId) return undefined
    return typeof warehouseId === 'function' ? warehouseId() : warehouseId.value
  }

  const locationsForWarehouse = computed(() => {
    const currentWarehouseId = resolveWarehouseId()
    if (currentWarehouseId == null || currentWarehouseId === '') return locations.value
    return locations.value.filter(
      (location) => String(location.warehouseId) === String(currentWarehouseId)
    )
  })

  const deliveryQuantityTotal = (items: Array<Pick<SalesDeliveryItem, 'quantity'>>) =>
    items.reduce((total, item) => total + Number(item.quantity || 0), 0)

  return {
    deliveryQuantityTotal,
    locationsForWarehouse,
    logisticsStatusLabel,
    logisticsStatusType
  }
}
