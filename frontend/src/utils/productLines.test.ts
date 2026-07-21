import { describe, expect, it, vi } from 'vitest'

import { hydrateProductLineLabels } from '@/utils/productLines'

describe('hydrateProductLineLabels', () => {
  it('fills missing product labels and reuses one lookup per product', async () => {
    const loadProduct = vi.fn().mockResolvedValue({
      productCode: 'P-001',
      productName: '测试商品'
    })
    const lines = [
      { productId: '9007199254740993', quantity: 1 },
      { productId: '9007199254740993', quantity: 2 }
    ]

    const result = await hydrateProductLineLabels(lines, loadProduct)

    expect(result).toEqual([
      { productId: '9007199254740993', quantity: 1, productCode: 'P-001', productName: '测试商品' },
      { productId: '9007199254740993', quantity: 2, productCode: 'P-001', productName: '测试商品' }
    ])
    expect(loadProduct).toHaveBeenCalledOnce()
    expect(loadProduct).toHaveBeenCalledWith('9007199254740993')
  })

  it('preserves existing labels and leaves a line usable when lookup fails', async () => {
    const loadProduct = vi.fn().mockRejectedValue(new Error('offline'))
    const lines = [
      { productId: '1', productCode: 'KNOWN', productName: '已有名称' },
      { productId: '2', quantity: 3 }
    ]

    const result = await hydrateProductLineLabels(lines, loadProduct)

    expect(result).toEqual(lines)
    expect(loadProduct).toHaveBeenCalledOnce()
    expect(loadProduct).toHaveBeenCalledWith('2')
  })
})
