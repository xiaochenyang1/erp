import { beforeEach, describe, expect, it, vi } from 'vitest'

const { get } = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('@/utils/request', () => ({ request: { get } }))
import { getContract, getContractVersion } from './contracts'
import { getFinanceInvoice } from './finance'
import { getFxGainLossReport } from './workflow'

beforeEach(() => get.mockReset())

describe('currency snapshots at API boundaries', () => {
  it('normalizes saved contract and version rates without replacing a zero base amount', async () => {
    const header = { totalAmount: '0.01', currencyCode: 'USD', exchangeRate: '0.01', baseTotalAmount: '0' }
    get.mockResolvedValueOnce({ id: '9007199254740993', ...header, lines: [] })
    expect(await getContract('9007199254740993')).toMatchObject({
      id: '9007199254740993', currencyCode: 'USD', exchangeRate: 0.01, baseTotalAmount: 0
    })
    get.mockResolvedValueOnce({ id: '3', contractId: '2', header, lines: [] })
    expect((await getContractVersion('2', '3')).header).toMatchObject({ exchangeRate: 0.01, baseTotalAmount: 0 })
  })

  it('keeps invoice snapshots numeric and does not invent snapshots for older API responses', async () => {
    get.mockResolvedValueOnce({ id: '1', amount: '100', taxAmount: '10', currencyCode: 'USD', exchangeRate: '7.2', baseAmount: '720', baseTaxAmount: '72' })
    expect(await getFinanceInvoice('1')).toMatchObject({ currencyCode: 'USD', exchangeRate: 7.2, baseAmount: 720, baseTaxAmount: 72 })
    get.mockResolvedValueOnce({ id: '2', amount: '100', taxAmount: '0' })
    expect(await getFinanceInvoice('2')).toMatchObject({ exchangeRate: undefined, baseAmount: undefined })
  })

  it('includes rounding-only report rows and reconstructs the same total as the export', async () => {
    get.mockResolvedValueOnce({ records: [
      { allocationId: '1', partnerId: '2', fxGainLossAmount: '0', roundingAmount: '-0.01', postingSnapshot: true },
      { allocationId: '3', partnerId: '2', fxGainLossAmount: '20' }
    ] })
    const report = await getFxGainLossReport({})
    expect(report.records[0]).toMatchObject({ fxGainLossAmount: 0, roundingAmount: -0.01, totalGainLossAmount: -0.01, postingSnapshot: true })
    expect(report.records[1]).toMatchObject({ roundingAmount: 0, totalGainLossAmount: 20, postingSnapshot: false })
  })
})
