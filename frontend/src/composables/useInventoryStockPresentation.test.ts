import { ref } from 'vue'
import { describe, expect, it } from 'vitest'

import type { Product, Warehouse } from '@/api/masterdata'
import { useInventoryStockPresentation } from './useInventoryStockPresentation'

const messages: Record<string, string> = {
  'inventoryStocks.warehouseFallback': 'Warehouse {id}',
  'inventoryStocks.productFallback': 'Product {id}',
  'inventoryStocks.locationFallback': 'Location {id}',
  'inventoryStocks.sourceTypeValue.salesOrder': 'Sales order',
  'inventoryStocks.reservationStatus.active': 'Active',
  'inventoryStocks.reservationStatus.released': 'Released',
  'inventoryStocks.reservationStatus.cancelled': 'Cancelled',
  'inventoryStocks.reservationEvent.reserve': 'Reserve',
  'inventoryStocks.reservationEvent.release': 'Release',
  'inventoryStocks.reservationEvent.manualRelease': 'Manual release',
  'inventoryStocks.directionValue.inbound': 'Inbound',
  'inventoryStocks.directionValue.outbound': 'Outbound',
  'inventoryStocks.expiryStatus.expired': 'Expired',
  'inventoryStocks.expiryStatus.expiring': 'Expiring',
  'inventoryStocks.expiryStatus.normal': 'Normal',
  'inventoryStocks.severityValue.error': 'Error',
  'inventoryStocks.severityValue.warning': 'Warning'
}

const translate = (key: string, params?: Record<string, unknown>) => {
  const template = messages[key] || key
  return Object.entries(params || {}).reduce(
    (result, [name, value]) => result.replace(`{${name}}`, String(value)),
    template
  )
}

describe('inventory stock presentation', () => {
  it('reactively resolves warehouse and product names with localized fallbacks', () => {
    const warehouses = ref<Warehouse[]>([])
    const products = ref<Product[]>([])
    const presentation = useInventoryStockPresentation(warehouses, products, translate)

    expect(presentation.warehouseName('10')).toBe('Warehouse 10')
    expect(presentation.productName('20')).toBe('Product 20')
    expect(presentation.locationName('30')).toBe('Location 30')
    expect(presentation.locationName()).toBe('-')

    warehouses.value = [{ id: '10', name: 'Main warehouse' } as Warehouse]
    products.value = [{ id: '20', code: 'P-20', name: 'Widget' } as Product]

    expect(presentation.warehouseName('10')).toBe('Main warehouse')
    expect(presentation.productName('20')).toBe('P-20 - Widget')
  })

  it('maps inventory states and preserves unknown backend values', () => {
    const presentation = useInventoryStockPresentation(
      ref<Warehouse[]>([]),
      ref<Product[]>([]),
      translate
    )

    expect(presentation.reservationStatusLabel('ACTIVE')).toBe('Active')
    expect(presentation.reservationStatusType('ACTIVE')).toBe('success')
    expect(presentation.directionLabel('OUT')).toBe('Outbound')
    expect(presentation.expiryStatusLabel('EXPIRED')).toBe('Expired')
    expect(presentation.expiryStatusType('EXPIRED')).toBe('danger')
    expect(presentation.severityLabel('WARNING')).toBe('Warning')
    expect(presentation.sourceTypeLabel('FUTURE_SOURCE')).toBe('FUTURE_SOURCE')
    expect(presentation.sourceTypeLabel()).toBe('-')
  })

  it('formats required, optional, and monetary quantities consistently', () => {
    const presentation = useInventoryStockPresentation(
      ref<Warehouse[]>([]),
      ref<Product[]>([]),
      translate
    )

    expect(presentation.formatNumber(12.34567)).toMatch(/12[.,]3457/)
    expect(presentation.formatOptionalNumber()).toBe('-')
    expect(presentation.formatMoney(12)).toMatch(/12[.,]00/)
  })
})
