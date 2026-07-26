import { describe, expect, it } from 'vitest'

import { useInventoryReplenishmentPresentation } from './useInventoryReplenishmentPresentation'

const t = (key: string) => key

describe('inventory replenishment presentation', () => {
  it('maps fulfillment status labels and falls back for unknowns', () => {
    const presentation = useInventoryReplenishmentPresentation(t)
    expect(presentation.fulfillmentStatusMeta('SUGGESTED')).toEqual({
      label: 'inventoryReplenishment.fulfillment.suggested',
      type: 'warning'
    })
    expect(presentation.fulfillmentStatusMeta('REPLENISHED').type).toBe('success')
    expect(presentation.fulfillmentStatusMeta('OTHER')).toEqual({
      label: 'OTHER',
      type: 'info'
    })
    expect(presentation.fulfillmentStatusMeta(undefined).label).toBe('-')
  })
})
