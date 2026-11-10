import { beforeEach, describe, expect, it, vi } from 'vitest'

const { get } = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('@/utils/request', () => ({ request: { get } }))

import { getPartnerStatement } from './finance'

const params = {
  partnerType: 'CUSTOMER' as const,
  partnerId: '2072561615605100546',
  dateFrom: '2026-09-01',
  dateTo: '2026-09-30'
}

describe('partner statement API', () => {
  beforeEach(() => get.mockReset())

  it('preserves the base currency and adjustment rows when normalizing amounts', async () => {
    get.mockResolvedValue({
      ...params,
      partnerName: 'Acme',
      baseCurrencyCode: 'EUR',
      openingBalance: '0.00',
      totalIncrease: '720.00',
      totalDecrease: '720.00',
      closingBalance: '0.00',
      lines: [{
        bizDate: '2026-09-02', docNo: 'RC-001', docType: 'FX_ADJUSTMENT',
        direction: 'INCREASE', amount: '20.00', balance: '0.00'
      }]
    })

    const statement = await getPartnerStatement(params)

    expect(get).toHaveBeenCalledWith('/finance/statements', { params })
    expect(statement).toMatchObject({
      partnerId: params.partnerId,
      baseCurrencyCode: 'EUR',
      closingBalance: 0,
      totalIncrease: 720,
      totalDecrease: 720,
      lines: [{ docType: 'FX_ADJUSTMENT', direction: 'INCREASE', amount: 20, balance: 0 }]
    })
  })

  it('keeps legacy statements readable with the CNY currency fallback', async () => {
    get.mockResolvedValue({ ...params })

    const statement = await getPartnerStatement(params)

    expect(statement.baseCurrencyCode).toBe('CNY')
    expect(statement.lines).toEqual([])
    expect(statement.closingBalance).toBe(0)
  })
})
