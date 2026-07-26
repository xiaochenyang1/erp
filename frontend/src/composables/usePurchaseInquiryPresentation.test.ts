import { describe, expect, it } from 'vitest'
import { ref } from 'vue'

import type { Product, Supplier } from '@/api/masterdata'
import { usePurchaseInquiryPresentation } from './usePurchaseInquiryPresentation'

const t = (key: string, params?: Record<string, unknown>) => {
  if (params?.id != null) return `${key}:${params.id}`
  return key
}

describe('purchase inquiry presentation', () => {
  it('maps status labels and resolves product/supplier labels', () => {
    const products = ref<Product[]>([
      { id: 'p1', productCode: 'RM-1', productName: 'Steel' } as Product
    ])
    const suppliers = ref<Supplier[]>([
      { id: 's1', supplierName: 'Acme' } as Supplier
    ])
    const presentation = usePurchaseInquiryPresentation(t, products, suppliers)

    expect(presentation.statusText('SUBMITTED')).toBe('purchaseInquiryOps.status.submitted')
    expect(presentation.statusType('CANCELLED')).toBe('danger')
    expect(presentation.productLabelById('p1')).toContain('RM-1')
    expect(presentation.supplierLabel('s1')).toBe('Acme')
    expect(presentation.inquiryLineProductLabel('line-1', [
      { id: 'line-1', productId: 'p1' } as any
    ])).toContain('RM-1')
  })
})
