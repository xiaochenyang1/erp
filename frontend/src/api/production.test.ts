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
  batchCloseProductionCosts,
  closeProductionOrderCost,
  correctProductionOrderCost,
  getProductionOrderCostCloses,
  getProductionOrderCosts,
  getProductionCostPeriods,
  getProductionCostPeriodCloseEvidence,
  getProductionCostPeriodClosePreview,
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

  it('normalizes period preview, partial batch results, and audit evidence', async () => {
    get.mockResolvedValue([{
      id: '9007199254740993', periodYear: 2026, periodMonth: '2026-11',
      startDate: '2026-11-01', endDate: '2026-11-30', status: 'OPEN'
    }])
    const periods = await getProductionCostPeriods()
    expect(get).toHaveBeenCalledWith('/production/cost-close-periods')
    expect(periods[0]).toMatchObject({ id: '9007199254740993', periodMonth: '2026-11', status: 'OPEN' })

    get.mockResolvedValue({
      periodId: '9007199254740993', periodMonth: '2026-11', startDate: '2026-11-01', endDate: '2026-11-30',
      periodStatus: 'OPEN', candidateCount: 1, positiveVarianceAmount: '5.00', negativeVarianceAmount: '0.00',
      netVarianceAmount: '5.00', items: [{
        orderId: '9007199254740995', orderNo: 'MO1', productId: '9007199254740997',
        completionDate: '2026-11-20', latestActivityDate: '2026-11-25', issuedAmount: '50.00',
        laborAmount: '5.00', overheadAmount: '0.00', finishedAmount: '50.00', unsettledVarianceAmount: '5.00'
      }]
    })
    const preview = await getProductionCostPeriodClosePreview('9007199254740993')
    expect(get).toHaveBeenCalledWith('/production/cost-close-periods/9007199254740993/preview')
    expect(preview.items[0]).toMatchObject({
      orderId: '9007199254740995', productId: '9007199254740997', unsettledVarianceAmount: 5
    })

    post.mockResolvedValue({
      periodId: '9007199254740993', periodMonth: '2026-11', businessDate: '2026-11-30',
      requestedCount: 2, successCount: 1, failureCount: 1, closedVarianceAmount: '5.00',
      operatorId: '9007199254740999', executedTime: '2026-11-30T18:00:00', results: [
        { orderId: '9007199254740995', orderNo: 'MO1', success: true, closeId: '9007199254741001', closeNo: 'PCC1', varianceAmount: '5.00' },
        { orderId: '8', success: false, errorCode: 'NOT_IN_PERIOD', errorMessage: 'not in period' }
      ]
    })
    const batch = await batchCloseProductionCosts('9007199254740993', {
      orderIds: ['9007199254740995', '8'], remark: 'month close'
    })
    expect(post).toHaveBeenCalledWith('/production/cost-close-periods/9007199254740993/batch-close', {
      orderIds: ['9007199254740995', '8'], remark: 'month close'
    })
    expect(batch.results[0]).toMatchObject({ closeId: '9007199254741001', varianceAmount: 5 })

    get.mockResolvedValue({ records: [{
      id: '9007199254741003', operatorId: '9007199254740999', operatorName: 'finance',
      auditTime: '2026-11-30T18:00:00', message: 'batch close', batch
    }], total: 1, pageNo: 1, pageSize: 20 })
    const evidence = await getProductionCostPeriodCloseEvidence('9007199254740993', { pageNo: 1 })
    expect(get).toHaveBeenCalledWith('/production/cost-close-periods/9007199254740993/evidence', {
      params: { pageNo: 1 }
    })
    expect(evidence.records[0].batch.results[0].orderId).toBe('9007199254740995')
  })
})
