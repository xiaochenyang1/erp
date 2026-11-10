import { describe, expect, it, vi } from 'vitest'

import { useInventoryLotGenealogyQuery } from './useInventoryLotGenealogyQuery'

const emptyResponse = {
  root: { productId: '1', lotNo: 'LOT-1', depth: 0, links: [] },
  upstream: null,
  downstream: null,
  limits: {
    maxDepth: 5,
    perLevelNodeLimit: 200,
    totalNodeLimit: 500,
    truncated: false,
    truncationReasons: [],
    scopeLimited: false
  }
}

describe('useInventoryLotGenealogyQuery', () => {
  it('validates and normalizes the query', async () => {
    const getInventoryLotGenealogy = vi.fn().mockResolvedValue(emptyResponse)
    const onError = vi.fn()
    const { form, load } = useInventoryLotGenealogyQuery((key) => key, { getInventoryLotGenealogy, onError })

    await load()
    expect(onError).toHaveBeenCalledWith('inventoryLotGenealogy.feedback.productAndLotRequired')

    form.productId = '7001'
    form.lotNo = ' LOT-1 '
    await load()
    expect(getInventoryLotGenealogy).toHaveBeenCalledWith({
      productId: '7001',
      lotNo: 'LOT-1',
      direction: 'BOTH',
      maxDepth: 5
    })
  })

  it('seeds the query from the route', () => {
    const { form, applyFromRoute } = useInventoryLotGenealogyQuery((key) => key, {
      getInventoryLotGenealogy: vi.fn()
    })

    applyFromRoute({ productId: '7001', lotNo: 'LOT-1' })

    expect(form.productId).toBe('7001')
    expect(form.lotNo).toBe('LOT-1')
  })
})
