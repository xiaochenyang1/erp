import { describe, expect, it } from 'vitest'

import { usePurchasePricePresentation } from './usePurchasePricePresentation'

const t = (key: string) => key

describe('purchase price presentation', () => {
  it('maps scope and status labels', () => {
    const presentation = usePurchasePricePresentation(t)
    expect(presentation.scopeLabel('s1')).toBe('purchasePrice.supplierSpecific')
    expect(presentation.scopeLabel(null)).toBe('purchasePrice.productGeneral')
    expect(presentation.scopeTagType('s1')).toBe('warning')
    expect(presentation.statusLabel('ACTIVE')).toBe('purchasePrice.active')
    expect(presentation.scopeDetail({ supplierId: '2', supplierName: 'Vendor' })).toBe('Vendor')
    expect(presentation.scopeDetail({})).toBe('purchasePrice.allSuppliers')
  })
})
