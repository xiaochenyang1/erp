import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
vi.mock('@/utils/request', () => ({ request: { get: (...args: unknown[]) => get(...args) } }))

import { getProductStockSummary } from './masterdata'

describe('product stock summary API', () => {
  beforeEach(() => get.mockReset())

  it('loads totals and preserves the product snowflake ID', async () => {
    get.mockResolvedValue({ productId: '9007199254740993', warehouseCount: 2, qtyOnHand: 15, qtyReserved: 4, qtyAvailable: 11, amountOnHand: 160 })

    await expect(getProductStockSummary('9007199254740993')).resolves.toMatchObject({
      productId: '9007199254740993', qtyAvailable: 11, amountOnHand: 160
    })
    expect(get).toHaveBeenCalledWith('/masterdata/products/9007199254740993/stock-summary')
  })
})
