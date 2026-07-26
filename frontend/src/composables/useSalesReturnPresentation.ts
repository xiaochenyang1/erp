import { computed, type Ref } from 'vue'

import type { Location } from '@/api/masterdata'
import type { SalesDelivery } from '@/api/sales'
import { formatLocalizedCurrency } from '@/utils/locale'

export const useSalesReturnPresentation = (
  deliveries: Ref<SalesDelivery[]>,
  locations: Ref<Location[]>,
  selectedDeliveryId?: Ref<string | number | undefined | null> | (() => string | number | undefined | null)
) => {
  const formatMoney = (value?: number) => formatLocalizedCurrency(Number(value ?? 0))

  const deliveryById = (deliveryId?: string | number) =>
    deliveries.value.find((item) => String(item.id) === String(deliveryId))

  const deliveryLabel = (delivery: SalesDelivery) =>
    [delivery.deliveryNo, delivery.customerName, delivery.warehouseName].filter(Boolean).join(' - ')
    || String(delivery.id)

  const deliveryLabelById = (deliveryId?: string | number) => {
    const delivery = deliveryById(deliveryId)
    return delivery ? deliveryLabel(delivery) : ''
  }

  const deliveryNoById = (deliveryId?: string | number) =>
    deliveryById(deliveryId)?.deliveryNo || ''

  const deliveryCustomerNameById = (deliveryId?: string | number) =>
    deliveryById(deliveryId)?.customerName || ''

  const deliveryWarehouseNameById = (deliveryId?: string | number) =>
    deliveryById(deliveryId)?.warehouseName || ''

  const resolveSelectedDeliveryId = () => {
    if (!selectedDeliveryId) return undefined
    return typeof selectedDeliveryId === 'function' ? selectedDeliveryId() : selectedDeliveryId.value
  }

  const selectedDelivery = computed(() => deliveryById(resolveSelectedDeliveryId()))

  const locationsForSelectedDelivery = computed(() => {
    const warehouseId = selectedDelivery.value?.warehouseId
    if (!warehouseId) return locations.value
    return locations.value.filter((location) => String(location.warehouseId) === String(warehouseId))
  })

  return {
    deliveryById,
    deliveryCustomerNameById,
    deliveryLabel,
    deliveryLabelById,
    deliveryNoById,
    deliveryWarehouseNameById,
    formatMoney,
    locationsForSelectedDelivery,
    selectedDelivery
  }
}
