import { describe, it, expect, vi, beforeEach } from 'vitest'

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock('@/utils/request', () => ({
  request: {
    get: (...args: unknown[]) => get(...args),
    post: (...args: unknown[]) => post(...args),
    put: (...args: unknown[]) => put(...args),
    delete: vi.fn()
  }
}))

import {
  closeProductionOrderCost,
  correctProductionOrderCost,
  getProductionOrderCostCloses,
  getProductionOrderCosts,
  getWorkCenters,
  getRouting,
  getRoutings,
  postProductionOrderCost,
  reopenProductionOrderCost,
  reverseProductionOrderCost
} from '@/api/production'

beforeEach(() => {
  get.mockReset()
  post.mockReset()
  put.mockReset()
})

describe('production API 归一化', () => {
  it('getWorkCenters 归一化分页 records 的 id 为字符串', async () => {
    // 后端 Long ID 以 JSON 字符串下发,归一化须原样保留精度。
    get.mockResolvedValue({
      records: [{ id: '9007199254740993', workCenterCode: 'WC01', workCenterName: '装配', status: 'ACTIVE' }],
      total: 1,
      pageNo: 1,
      pageSize: 20
    })
    const page = await getWorkCenters({ pageNo: 1, pageSize: 20 })
    expect(page.records[0].id).toBe('9007199254740993')
    expect(typeof page.records[0].id).toBe('string')
  })

  it('getRouting 归一化 id/bomId 及工序 workCenterId 为字符串、standardMinutes 为数值', async () => {
    get.mockResolvedValue({
      id: '9007199254740993',
      routingCode: 'RT01',
      routingName: '标准工艺',
      bomId: '9007199254740995',
      status: 'ACTIVE',
      operations: [
        { id: '1', lineNo: 1, operationCode: 'OP1', operationName: '切割', workCenterId: '9007199254740997', standardMinutes: '12.50' }
      ]
    })
    const routing = await getRouting('9007199254740993')
    expect(routing.id).toBe('9007199254740993')
    expect(routing.bomId).toBe('9007199254740995')
    expect(routing.operations[0].workCenterId).toBe('9007199254740997')
    expect(routing.operations[0].standardMinutes).toBe(12.5)
    expect(typeof routing.operations[0].standardMinutes).toBe('number')
  })

  it('getRoutings 对空 operations 归一化为数组', async () => {
    get.mockResolvedValue({
      records: [{ id: 1, routingCode: 'RT', routingName: 'x', bomId: 2, status: 'ACTIVE' }],
      total: 1,
      pageNo: 1,
      pageSize: 20
    })
    const page = await getRoutings({ pageNo: 1, pageSize: 20 })
    expect(page.records[0].operations).toEqual([])
  })

  it('生产成本归集 API 保留 Long 精度并归一化金额', async () => {
    get.mockResolvedValue([{
      id: '9007199254740993',
      costNo: 'PCE202611070001',
      orderId: '9007199254740995',
      operationId: '9007199254740997',
      costType: 'LABOR',
      businessDate: '2026-11-07',
      amount: '123.45',
      status: 'REVERSED',
      entryKind: 'ORIGINAL',
      reversalEntryId: '9007199254740999'
    }])
    const entries = await getProductionOrderCosts('9007199254740995')
    expect(entries[0]).toMatchObject({
      id: '9007199254740993',
      orderId: '9007199254740995',
      operationId: '9007199254740997',
      amount: 123.45,
      reversalEntryId: '9007199254740999'
    })

    post.mockResolvedValue({
      id: 1, costNo: 'PCE1', orderId: 2, costType: 'OVERHEAD',
      businessDate: '2026-11-07', amount: 20, status: 'POSTED'
    })
    const posted = await postProductionOrderCost(2, {
      costType: 'OVERHEAD', businessDate: '2026-11-07', amount: 20
    })
    expect(post).toHaveBeenCalledWith('/production/orders/2/costs', {
      costType: 'OVERHEAD', businessDate: '2026-11-07', amount: 20
    })
    expect(posted.orderId).toBe('2')

    post.mockResolvedValue({
      id: 3, costNo: 'PCE3', orderId: 2, costType: 'OVERHEAD',
      businessDate: '2026-11-08', amount: -20, status: 'POSTED', entryKind: 'REVERSAL',
      reversalOfEntryId: 1
    })
    const reversed = await reverseProductionOrderCost(2, 1, {
      businessDate: '2026-11-08', reason: 'duplicate entry'
    })
    expect(post).toHaveBeenCalledWith('/production/orders/2/costs/1/reverse', {
      businessDate: '2026-11-08', reason: 'duplicate entry'
    })
    expect(reversed.reversalOfEntryId).toBe('1')
    expect(reversed.amount).toBe(-20)

    post.mockResolvedValue({
      id: 4, costNo: 'PCE4', orderId: 2, costType: 'LABOR',
      businessDate: '2026-11-08', amount: 25, status: 'POSTED', entryKind: 'CORRECTION',
      correctionOfEntryId: 1
    })
    const corrected = await correctProductionOrderCost(2, 1, {
      costType: 'LABOR', businessDate: '2026-11-08', amount: 25,
      reason: 'wrong amount', remark: 'replacement'
    })
    expect(post).toHaveBeenCalledWith('/production/orders/2/costs/1/correct', {
      costType: 'LABOR', businessDate: '2026-11-08', amount: 25,
      reason: 'wrong amount', remark: 'replacement'
    })
    expect(corrected.correctionOfEntryId).toBe('1')
  })

  it('normalizes and posts production cost close audit records', async () => {
    get.mockResolvedValue([{
      id: '9007199254740993', closeNo: 'PCC1', orderId: '9007199254740995',
      businessDate: '2026-11-08', amount: '5.25', status: 'POSTED', entryKind: 'CLOSE'
    }])
    const records = await getProductionOrderCostCloses('9007199254740995')
    expect(records[0]).toMatchObject({
      id: '9007199254740993', orderId: '9007199254740995', amount: 5.25
    })

    post.mockResolvedValue({
      id: 5, closeNo: 'PCC2', orderId: 2, businessDate: '2026-11-08',
      amount: 10, status: 'POSTED', entryKind: 'CLOSE'
    })
    await closeProductionOrderCost(2, { businessDate: '2026-11-08', remark: 'month close' })
    expect(post).toHaveBeenCalledWith('/production/orders/2/cost-close', {
      businessDate: '2026-11-08', remark: 'month close'
    })

    post.mockResolvedValue({
      id: 6, closeNo: 'PCC3', orderId: 2, businessDate: '2026-11-09',
      amount: -10, status: 'POSTED', entryKind: 'REVERSAL', reversalOfCloseId: 5
    })
    const reopened = await reopenProductionOrderCost(2, {
      businessDate: '2026-11-09', reason: 'late payroll'
    })
    expect(post).toHaveBeenCalledWith('/production/orders/2/cost-reopen', {
      businessDate: '2026-11-09', reason: 'late payroll'
    })
    expect(reopened.reversalOfCloseId).toBe('5')
  })
})
