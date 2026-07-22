import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
vi.mock('@/utils/request', () => ({ request: { get: (...args: unknown[]) => get(...args) } }))

import { getOperationsDashboard } from './dashboard'

describe('operations dashboard API', () => {
  beforeEach(() => get.mockReset())

  it('normalizes TOP SKU product IDs without losing precision', async () => {
    get.mockResolvedValue({
      summary: {}, todos: [], lowStock: [], failedOperations: [],
      topSkus: [{ productId: 9007199254740993n, productCode: 'SKU-1', productName: 'Product', quantity: 12, amount: 3600 }]
    })

    const dashboard = await getOperationsDashboard()

    expect(get).toHaveBeenCalledWith('/dashboard/operations')
    expect(dashboard.topSkus[0].productId).toBe('9007199254740993')
  })
})
