import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type { Product } from '@/api/masterdata'
import { useProductionOrderProductControls } from './useProductionOrderProductControls'

describe('production order product controls', () => {
  it('resolves control flags from cached product options without network calls', async () => {
    const products = ref<Product[]>([{
      id: 'p1',
      code: 'M-1',
      name: 'Material A',
      lotControlled: true,
      shelfLifeControlled: false,
      serialControlled: true
    } as Product])
    const loadProduct = vi.fn()
    const controls = useProductionOrderProductControls(products, loadProduct)

    const resolved = await controls.resolveProductControls('p1')

    expect(resolved).toEqual({
      lotControlled: true,
      shelfLifeControlled: false,
      serialControlled: true,
      productCode: 'M-1',
      productName: 'Material A'
    })
    expect(loadProduct).not.toHaveBeenCalled()
  })

  it('hydrates missing control flags and material labels via product API', async () => {
    const products = ref<Product[]>([])
    const loadProduct = vi.fn(async (productId: string | number) => ({
      productCode: `CODE-${productId}`,
      productName: `Name-${productId}`,
      lotControlled: true,
      shelfLifeControlled: true,
      serialControlled: false
    }))
    const controls = useProductionOrderProductControls(products, loadProduct)

    const materials = await controls.hydrateMaterialControls([
      {
        materialProductId: '88',
        materialCode: 'RAW-88',
        materialName: 'Raw material'
      }
    ])

    expect(loadProduct).toHaveBeenCalledWith('88')
    expect(materials[0]).toMatchObject({
      materialProductId: '88',
      productCode: 'RAW-88',
      productName: 'Raw material',
      lotControlled: true,
      shelfLifeControlled: true,
      serialControlled: false
    })
  })

  it('falls back to cached option values when product API fails', async () => {
    const products = ref<Product[]>([{
      id: 'p2',
      productCode: 'P-2',
      productName: 'Partial product'
    } as Product])
    const loadProduct = vi.fn(async () => {
      throw new Error('network')
    })
    const controls = useProductionOrderProductControls(products, loadProduct)

    const resolved = await controls.resolveProductControls('p2')

    expect(resolved.productCode).toBe('P-2')
    expect(resolved.productName).toBe('Partial product')
    expect(resolved.lotControlled).toBe(false)
    expect(resolved.serialControlled).toBe(false)
  })
})
