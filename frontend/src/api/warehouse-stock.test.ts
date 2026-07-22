import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
vi.mock('@/utils/request', () => ({ request: { get: (...args: unknown[]) => get(...args) } }))

import { getWarehouseStockSummary } from './masterdata'

describe('warehouse stock summary API', () => {
  beforeEach(() => get.mockReset())

  it('loads stock and normalizes the warehouse snowflake ID', async () => {
    get.mockResolvedValue({ warehouseId: '9007199254740993', skuCount: 2, qtyOnHand: 15, qtyAvailable: 11 })

    await expect(getWarehouseStockSummary('9007199254740993')).resolves.toMatchObject({
      warehouseId: '9007199254740993', skuCount: 2, qtyAvailable: 11
    })
    expect(get).toHaveBeenCalledWith('/masterdata/warehouses/9007199254740993/stock-summary')
  })
})
