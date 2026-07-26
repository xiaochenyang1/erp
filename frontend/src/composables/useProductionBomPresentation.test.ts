import { describe, expect, it } from 'vitest'
import { ref } from 'vue'

import type { BOMItem } from '@/api/production'
import type { Product } from '@/api/masterdata'
import { useProductionBomPresentation } from './useProductionBomPresentation'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.id != null ? `${key}:${params.id}` : key

const products = (): Product[] => [
  {
    id: 'p1',
    productCode: 'FG-01',
    productName: '成品A',
    unitName: '件'
  } as Product,
  {
    id: 'm1',
    code: 'RM-01',
    name: '原料B',
    unit: 'kg'
  } as Product
]

describe('production BOM presentation', () => {
  it('formats product labels from code/name variants and fallbacks', () => {
    const presentation = useProductionBomPresentation(t, ref(products()))

    expect(presentation.productLabel(products()[0])).toBe('FG-01 - 成品A')
    expect(presentation.productLabel(products()[1])).toBe('RM-01 - 原料B')
    expect(presentation.productLabel({ id: 'x' } as Product))
      .toBe('productionBom.productFallback:x')
    expect(presentation.productLabelById('p1')).toBe('FG-01 - 成品A')
    expect(presentation.productLabelById('missing')).toBe('missing')
    expect(presentation.productLabelById(undefined)).toBe('-')
  })

  it('resolves material labels and units from options then row fields', () => {
    const presentation = useProductionBomPresentation(t, ref(products()))

    expect(presentation.materialLabel({ materialId: 'm1' } as BOMItem)).toBe('RM-01 - 原料B')
    expect(presentation.materialUnit({ materialId: 'm1' } as BOMItem)).toBe('kg')
    expect(presentation.materialLabel({
      materialId: 'gone',
      materialCode: 'X',
      materialName: '遗留物料'
    } as BOMItem)).toBe('X - 遗留物料')
    expect(presentation.materialUnit({
      materialId: 'gone',
      unit: '箱'
    } as BOMItem)).toBe('箱')
  })

  it('maps status labels and tag types, leaving unknowns raw', () => {
    const presentation = useProductionBomPresentation(t, ref([]))

    expect(presentation.getStatusLabel('ACTIVE')).toBe('productionBom.status.active')
    expect(presentation.getStatusType('ACTIVE')).toBe('success')
    expect(presentation.getStatusLabel('DISABLED')).toBe('productionBom.status.disabled')
    expect(presentation.getStatusType('DISABLED')).toBe('danger')
    expect(presentation.getStatusLabel('OTHER')).toBe('OTHER')
    expect(presentation.getStatusType('OTHER')).toBe('')
    expect(presentation.getStatusLabel(undefined)).toBe('')
  })
})
