import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
vi.mock('@/utils/request', () => ({
  request: {
    get: (...args: unknown[]) => get(...args),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

import { getPayables, getReceivables } from './finance'

beforeEach(() => get.mockReset())

describe('finance account API contract', () => {
  it('maps receivable date filters and preserves real settlement/source/time fields', async () => {
    get.mockResolvedValue({
      pageNo: 1,
      pageSize: 20,
      total: 1,
      records: [{
        id: '9007199254740993',
        receivableNo: 'AR-1',
        customerId: '9007199254740994',
        bizDate: '2026-07-23',
        sourceType: 'SALES_DELIVERY',
        sourceId: '9007199254740995',
        sourceNo: 'SD-1',
        originalAmount: '100.00',
        settledAmount: '40.00',
        remainingAmount: '60.00',
        status: 'UNSETTLED',
        createdTime: '2026-07-23T09:00:00',
        updatedTime: '2026-07-23T10:00:00'
      }]
    })

    const page = await getReceivables({
      pageNo: 1,
      pageSize: 20,
      status: 'UNSETTLED',
      startDate: '2026-07-01',
      endDate: '2026-07-31'
    })

    expect(get).toHaveBeenCalledWith('/finance/receivables', {
      params: {
        pageNo: 1,
        pageSize: 20,
        status: 'UNSETTLED',
        bizDateFrom: '2026-07-01',
        bizDateTo: '2026-07-31'
      }
    })
    expect(page.records[0]).toMatchObject({
      id: '9007199254740993',
      sourceId: '9007199254740995',
      sourceNo: 'SD-1',
      bizDate: '2026-07-23',
      status: 'UNSETTLED',
      createdTime: '2026-07-23T09:00:00',
      updatedTime: '2026-07-23T10:00:00'
    })
    expect(page.records[0]).not.toHaveProperty('orderNo')
    expect(page.records[0]).not.toHaveProperty('dueDate')
    expect(page.records[0]).not.toHaveProperty('createdAt')
    expect(page.records[0]).not.toHaveProperty('updatedAt')
  })

  it('uses the same real status and date-filter contract for payables', async () => {
    get.mockResolvedValue({
      pageNo: 1,
      pageSize: 20,
      total: 1,
      records: [{
        id: '9007199254740996',
        payableNo: 'AP-1',
        supplierId: '9007199254740997',
        bizDate: '2026-07-22',
        sourceType: 'PURCHASE_RECEIPT',
        sourceId: '9007199254740998',
        sourceNo: 'PR-1',
        originalAmount: 80,
        settledAmount: 80,
        remainingAmount: 0,
        status: 'OFFSET',
        createdTime: '2026-07-22T09:00:00',
        updatedTime: '2026-07-22T11:00:00'
      }]
    })

    const page = await getPayables({
      pageNo: 1,
      pageSize: 20,
      status: 'OFFSET',
      startDate: '2026-07-01',
      endDate: '2026-07-31'
    })

    expect(get).toHaveBeenCalledWith('/finance/payables', {
      params: {
        pageNo: 1,
        pageSize: 20,
        status: 'OFFSET',
        bizDateFrom: '2026-07-01',
        bizDateTo: '2026-07-31'
      }
    })
    expect(page.records[0]).toMatchObject({
      sourceNo: 'PR-1',
      status: 'OFFSET',
      createdTime: '2026-07-22T09:00:00',
      updatedTime: '2026-07-22T11:00:00'
    })
    expect(page.records[0]).not.toHaveProperty('createdAt')
    expect(page.records[0]).not.toHaveProperty('updatedAt')
  })
})
