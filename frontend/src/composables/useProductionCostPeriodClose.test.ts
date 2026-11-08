import { describe, expect, it, vi } from 'vitest'

import { useProductionCostPeriodClose } from './useProductionCostPeriodClose'

const t = (key: string) => key
const periods = [
  { id: '2', periodYear: 2026, periodMonth: '2026-12', startDate: '2026-12-01', endDate: '2026-12-31', status: 'OPEN' },
  { id: '1', periodYear: 2026, periodMonth: '2026-11', startDate: '2026-11-01', endDate: '2026-11-30', status: 'OPEN' }
]
const preview = {
  periodId: '1', periodMonth: '2026-11', startDate: '2026-11-01', endDate: '2026-11-30', periodStatus: 'OPEN',
  candidateCount: 2, positiveVarianceAmount: 5, negativeVarianceAmount: -2, netVarianceAmount: 3,
  items: [
    { orderId: '10', orderNo: 'MO10', productId: '100', completionDate: '2026-11-20', latestActivityDate: '2026-11-25', issuedAmount: 50, laborAmount: 5, overheadAmount: 0, finishedAmount: 50, unsettledVarianceAmount: 5 },
    { orderId: '11', orderNo: 'MO11', productId: '101', completionDate: '2026-11-21', latestActivityDate: '2026-11-26', issuedAmount: 48, laborAmount: 0, overheadAmount: 0, finishedAmount: 50, unsettledVarianceAmount: -2 }
  ]
}

describe('production cost period close', () => {
  it('selects the earliest open period and loads preview plus evidence', async () => {
    const loadPreview = vi.fn(async () => preview)
    const loadEvidence = vi.fn(async () => ({ records: [], total: 0, pageNo: 1, pageSize: 20 }))
    const state = useProductionCostPeriodClose(t, {
      loadPeriods: vi.fn(async () => periods),
      loadPreview,
      loadEvidence,
      closeBatch: vi.fn()
    })

    await state.open()

    expect(state.periodId.value).toBe('1')
    expect(state.preview.value?.candidateCount).toBe(2)
    expect(loadPreview).toHaveBeenCalledWith('1')
    expect(loadEvidence).toHaveBeenCalledWith('1', { pageNo: 1, pageSize: 20 })
  })

  it('returns partial results and refreshes preview and audit evidence', async () => {
    const loadPreview = vi.fn(async () => preview)
    const loadEvidence = vi.fn(async () => ({ records: [], total: 0, pageNo: 1, pageSize: 20 }))
    const closeBatch = vi.fn(async () => ({
      periodId: '1', periodMonth: '2026-11', businessDate: '2026-11-30', requestedCount: 2,
      successCount: 1, failureCount: 1, closedVarianceAmount: 5, operatorId: '7', executedTime: '2026-11-30T18:00:00',
      results: [
        { orderId: '10', orderNo: 'MO10', success: true, closeId: '90', closeNo: 'PCC90', varianceAmount: 5 },
        { orderId: '11', orderNo: 'MO11', success: false, errorCode: 'CONFLICT', errorMessage: 'already closed' }
      ]
    }))
    const onWarning = vi.fn()
    const state = useProductionCostPeriodClose(t, {
      loadPeriods: vi.fn(async () => periods),
      loadPreview,
      loadEvidence,
      closeBatch,
      confirm: vi.fn(async () => undefined),
      onWarning
    })
    await state.open()
    state.handleSelectionChange(preview.items)
    state.remark.value = ' month close '

    await state.submit()

    expect(closeBatch).toHaveBeenCalledWith('1', { orderIds: ['10', '11'], remark: 'month close' })
    expect(state.batchResult.value?.failureCount).toBe(1)
    expect(onWarning).toHaveBeenCalledWith('productionOrder.periodClosePartial')
    expect(loadPreview).toHaveBeenCalledTimes(2)
    expect(loadEvidence).toHaveBeenCalledTimes(2)
  })
})
