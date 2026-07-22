import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
vi.mock('@/utils/request', () => ({ request: { get: (...args: unknown[]) => get(...args) } }))

import { getCustomerCreditExposure } from './masterdata'

describe('customer credit exposure API', () => {
  beforeEach(() => get.mockReset())

  it('loads exposure and normalizes the customer snowflake ID', async () => {
    get.mockResolvedValue({ customerId: '9007199254740993', creditLimit: 2000, totalExposure: 1300, availableCredit: 700 })

    await expect(getCustomerCreditExposure('9007199254740993')).resolves.toMatchObject({
      customerId: '9007199254740993', totalExposure: 1300, availableCredit: 700
    })
    expect(get).toHaveBeenCalledWith('/masterdata/customers/9007199254740993/credit-exposure')
  })
})
