import { ref } from 'vue'
import { describe, expect, it } from 'vitest'

import type { Product } from '@/api/masterdata'
import { useProductPresentation } from './useProductPresentation'

describe('product presentation', () => {
  const texts = ref({
    active: 'Active',
    inactive: 'Inactive',
    physicalProduct: 'Physical',
    goodsProduct: 'Goods',
    serviceProduct: 'Service',
    unitPiece: 'Piece',
    unitMachine: 'Machine',
    unitItem: 'Item',
    unitBox: 'Box'
  })

  it('formats values and maps unit/product type options', () => {
    const presentation = useProductPresentation(texts)

    expect(presentation.interpolate('Hello {name}', { name: 'ERP' })).toBe('Hello ERP')
    expect(presentation.joinNames(['A', 'B'], 'en-US')).toBe('A, B')
    expect(presentation.joinNames(['A', 'B'], 'zh-CN')).toBe('A、B')
    expect(presentation.labelWithCount('Selected', 3)).toBe('Selected (3)')
    expect(presentation.labelWithCount('Selected', 0)).toBe('Selected')
    expect(presentation.productTypeOptions.value.map((item) => item.value)).toEqual([
      'PHYSICAL',
      'GOODS',
      'SERVICE'
    ])
    expect(presentation.formatUnit('BOX')).toBe('Box')
    expect(presentation.formatUnit('unknown')).toBe('unknown')
  })

  it('counts active products and builds product labels', () => {
    const presentation = useProductPresentation(texts)
    const rows = [
      { id: '1', status: 'ACTIVE', name: 'A' },
      { id: '2', status: 'DISABLED', productName: 'B' },
      { id: '3', status: 'ACTIVE', productCode: 'C' }
    ] as Product[]

    expect(presentation.activeCount(rows)).toBe(2)
    expect(presentation.productLabel(rows[0])).toBe('A')
    expect(presentation.productLabel(rows[2])).toBe('C')
  })
})
