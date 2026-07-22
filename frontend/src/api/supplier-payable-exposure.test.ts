import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
vi.mock('@/utils/request', () => ({ request: { get: (...args: unknown[]) => get(...args) } }))

import { getSupplierPayableExposure } from './masterdata'

describe('supplier payable exposure API', () => {
  beforeEach(() => get.mockReset())

  it('loads exposure and normalizes the supplier snowflake ID', async () => {
    get.mockResolvedValue({
      supplierId: '9007199254740993', outstandingPayable: 600,
      openPurchaseOrderAmount: 440, totalExposure: 1040
    })

    await expect(getSupplierPayableExposure('9007199254740993')).resolves.toMatchObject({
      supplierId: '9007199254740993', totalExposure: 1040
    })
    expect(get).toHaveBeenCalledWith('/masterdata/suppliers/9007199254740993/payable-exposure')
  })
})
