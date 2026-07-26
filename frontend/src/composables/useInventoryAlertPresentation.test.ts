import { describe, expect, it } from 'vitest'

import type { InventoryAlert } from '@/api/inventory'
import { useInventoryAlertPresentation } from './useInventoryAlertPresentation'

const t = (key: string) => key

describe('inventory alert presentation', () => {
  it('maps labels/tags and builds statistics', () => {
    const presentation = useInventoryAlertPresentation(t)
    expect(presentation.alertTypeText('OUT_OF_STOCK')).toBe('inventoryAlerts.alertType.outOfStock')
    expect(presentation.alertTypeTag('LOW_STOCK')).toBe('warning')
    expect(presentation.statusText('RESOLVED')).toBe('inventoryAlerts.status.resolved')
    expect(presentation.statusTag('ACTIVE')).toBe('warning')

    const stats = presentation.buildStatistics([
      { alertType: 'OUT_OF_STOCK', shortageQty: 2 },
      { alertType: 'LOW_STOCK', shortageQty: 3 },
      { alertType: 'LOW_STOCK', shortageQty: 1 }
    ] as InventoryAlert[], 10)
    expect(stats.total).toBe(10)
    expect(stats.outOfStock).toBe(1)
    expect(stats.lowStock).toBe(2)
    expect(stats.shortageQty).toBe(6)
  })
})
