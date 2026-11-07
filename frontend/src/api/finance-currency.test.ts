import { beforeEach, describe, expect, it, vi } from 'vitest'

const { get } = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('@/utils/request', () => ({ request: { get } }))

import { getBaseCurrency, getExchangeRates } from './finance'

describe('finance currency API', () => {
  beforeEach(() => get.mockReset())

  it('loads the configured base currency', async () => {
    get.mockResolvedValue('USD')
    await expect(getBaseCurrency()).resolves.toBe('USD')
    expect(get).toHaveBeenCalledWith('/finance/currencies/base')
  })

  it('passes currency pair filters to rate lookup', async () => {
    get.mockResolvedValue([])
    await getExchangeRates({ from: 'USD', to: 'CNY' })
    expect(get).toHaveBeenCalledWith('/finance/currencies/rates', { params: { from: 'USD', to: 'CNY' } })
  })
})
