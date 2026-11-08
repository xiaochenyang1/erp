import { describe, expect, it, vi } from 'vitest'

import type { ProductionOrder } from '@/api/production'
import { useProductionOrderCosts } from './useProductionOrderCosts'

const t = (key: string) => key
const order = {
  id: 'order-1',
  orderNo: 'MO001',
  status: 'MATERIAL_ISSUED'
} as ProductionOrder

describe('production order costs', () => {
  it('loads cost entries and operation dimensions when opening the dialog', async () => {
    const loadCosts = vi.fn(async () => [{
      id: 'cost-1', costNo: 'PCE1', orderId: 'order-1', costType: 'LABOR' as const,
      businessDate: '2026-11-07', amount: 120, status: 'POSTED', entryKind: 'ORIGINAL' as const
    }])
    const loadOperations = vi.fn(async () => [{
      id: 'op-1', orderId: 'order-1', lineNo: 1, operationCode: 'OP10', operationName: 'Assembly',
      standardMinutes: 30, plannedQty: 1, reportedQty: 0, qualifiedQty: 0, scrapQty: 0, status: 'PENDING'
    }])
    const costs = useProductionOrderCosts(t, {
      loadCosts,
      loadOperations,
      postCost: vi.fn(),
      reverseCost: vi.fn(),
      correctCost: vi.fn(),
      loadCostCloses: vi.fn(async () => []),
      closeCost: vi.fn(),
      reopenCost: vi.fn(),
      formatBusinessDate: () => '2026-11-07'
    })

    await costs.openCosts(order)

    expect(costs.costDialogVisible.value).toBe(true)
    expect(loadCosts).toHaveBeenCalledWith('order-1')
    expect(loadOperations).toHaveBeenCalledWith('order-1')
    expect(costs.costEntries.value).toHaveLength(1)
    expect(costs.operationLabel('op-1')).toBe('OP10 Assembly')
  })

  it('validates and posts a cost entry before refreshing the list', async () => {
    const loadCosts = vi.fn(async () => [])
    const loadOperations = vi.fn(async () => [])
    const postCost = vi.fn(async () => ({}))
    const refreshedOrder = { ...order, laborAmount: 88.5 } as ProductionOrder
    const loadOrder = vi.fn(async () => refreshedOrder)
    const onSuccess = vi.fn()
    const costs = useProductionOrderCosts(t, {
      loadCosts,
      loadOperations,
      postCost,
      reverseCost: vi.fn(),
      correctCost: vi.fn(),
      loadCostCloses: vi.fn(async () => []),
      loadOrder,
      closeCost: vi.fn(),
      reopenCost: vi.fn(),
      formatBusinessDate: () => '2026-11-07',
      onSuccess
    })
    await costs.openCosts(order)
    costs.costForm.costType = 'OVERHEAD'
    costs.costForm.amount = 88.5
    costs.costForm.remark = 'allocation'

    await costs.submitCost()

    expect(postCost).toHaveBeenCalledWith('order-1', {
      costType: 'OVERHEAD',
      operationId: undefined,
      businessDate: '2026-11-07',
      amount: 88.5,
      remark: 'allocation'
    })
    expect(onSuccess).toHaveBeenCalledWith('productionOrder.message.costPosted')
    expect(loadCosts).toHaveBeenCalledTimes(2)
    expect(loadOrder).toHaveBeenCalledWith('order-1')
    expect(costs.costOrder.value).toEqual(refreshedOrder)
  })

  it('rejects a zero amount locally', async () => {
    const postCost = vi.fn()
    const onWarning = vi.fn()
    const costs = useProductionOrderCosts(t, {
      loadCosts: vi.fn(async () => []),
      loadOperations: vi.fn(async () => []),
      postCost,
      reverseCost: vi.fn(),
      correctCost: vi.fn(),
      loadCostCloses: vi.fn(async () => []),
      closeCost: vi.fn(),
      reopenCost: vi.fn(),
      formatBusinessDate: () => '2026-11-07',
      onWarning
    })
    costs.costOrderId.value = 'order-1'

    await costs.submitCost()

    expect(postCost).not.toHaveBeenCalled()
    expect(onWarning).toHaveBeenCalledWith('productionOrder.validation.costAmount')
  })

  it('refreshes the displayed variance after posting a late completed-order cost', async () => {
    const completedOrder = {
      ...order,
      status: 'COMPLETED',
      issuedAmount: 50,
      laborAmount: 30,
      finishedMaterialAmount: 50,
      finishedLaborAmount: 30,
      settledVarianceAmount: 0,
      costCloseStatus: 'OPEN'
    } as ProductionOrder
    const refreshedOrder = { ...completedOrder, laborAmount: 35 } as ProductionOrder
    const costs = useProductionOrderCosts(t, {
      loadCosts: vi.fn(async () => []),
      loadOperations: vi.fn(async () => []),
      postCost: vi.fn(async () => ({})),
      reverseCost: vi.fn(),
      correctCost: vi.fn(),
      loadCostCloses: vi.fn(async () => []),
      loadOrder: vi.fn(async () => refreshedOrder),
      closeCost: vi.fn(),
      reopenCost: vi.fn(),
      formatBusinessDate: () => '2026-11-07'
    })
    await costs.openCosts(completedOrder)
    expect(costs.costUnsettledVariance.value).toBe(0)
    costs.costForm.amount = 5

    await costs.submitCost()

    expect(costs.costUnsettledVariance.value).toBe(5)
  })

  it('reverses a posted cost and refreshes the audit chain', async () => {
    const reverseCost = vi.fn(async () => ({}))
    const loadCosts = vi.fn(async () => [])
    const onSuccess = vi.fn()
    const costs = useProductionOrderCosts(t, {
      loadCosts,
      loadOperations: vi.fn(async () => []),
      postCost: vi.fn(),
      reverseCost,
      correctCost: vi.fn(),
      loadCostCloses: vi.fn(async () => []),
      closeCost: vi.fn(),
      reopenCost: vi.fn(),
      formatBusinessDate: () => '2026-11-08',
      onSuccess
    })
    await costs.openCosts(order)
    const entry = {
      id: 'cost-1', costNo: 'PCE1', orderId: 'order-1', costType: 'LABOR' as const,
      businessDate: '2026-11-07', amount: 120, status: 'POSTED', entryKind: 'ORIGINAL' as const
    }

    costs.openReverseCost(entry)
    costs.costActionForm.reason = 'duplicate payroll'
    await costs.submitCostAction()

    expect(reverseCost).toHaveBeenCalledWith('order-1', 'cost-1', {
      businessDate: '2026-11-08', reason: 'duplicate payroll'
    })
    expect(onSuccess).toHaveBeenCalledWith('productionOrder.costReversedMessage')
    expect(costs.costActionDialogVisible.value).toBe(false)
  })

  it('corrects an entry with a linked replacement', async () => {
    const correctCost = vi.fn(async () => ({}))
    const costs = useProductionOrderCosts(t, {
      loadCosts: vi.fn(async () => []),
      loadOperations: vi.fn(async () => []),
      postCost: vi.fn(),
      reverseCost: vi.fn(),
      correctCost,
      loadCostCloses: vi.fn(async () => []),
      closeCost: vi.fn(),
      reopenCost: vi.fn(),
      formatBusinessDate: () => '2026-11-08'
    })
    await costs.openCosts(order)
    const entry = {
      id: 'cost-2', costNo: 'PCE2', orderId: 'order-1', costType: 'LABOR' as const,
      businessDate: '2026-11-07', amount: 120, status: 'POSTED', entryKind: 'ORIGINAL' as const,
      remark: 'old amount'
    }

    costs.openCorrectCost(entry)
    costs.costActionForm.costType = 'OVERHEAD'
    costs.costActionForm.amount = 88
    costs.costActionForm.reason = 'wrong category'
    await costs.submitCostAction()

    expect(correctCost).toHaveBeenCalledWith('order-1', 'cost-2', {
      costType: 'OVERHEAD',
      operationId: undefined,
      businessDate: '2026-11-08',
      amount: 88,
      reason: 'wrong category',
      remark: 'old amount'
    })
  })

  it('closes and reopens a completed order while refreshing the audit chain', async () => {
    const completedOrder = {
      ...order,
      status: 'COMPLETED',
      issuedAmount: 50,
      laborAmount: 30,
      finishedMaterialAmount: 50,
      finishedLaborAmount: 25,
      settledVarianceAmount: 0,
      costCloseStatus: 'OPEN'
    } as ProductionOrder
    const closeCost = vi.fn(async () => ({
      id: 'close-1', closeNo: 'PCC1', orderId: 'order-1', businessDate: '2026-11-08',
      amount: 5, status: 'POSTED', entryKind: 'CLOSE' as const
    }))
    const reopenCost = vi.fn(async () => ({
      id: 'close-2', closeNo: 'PCC2', orderId: 'order-1', businessDate: '2026-11-09',
      amount: -5, status: 'POSTED', entryKind: 'REVERSAL' as const, reversalOfCloseId: 'close-1'
    }))
    const loadCostCloses = vi.fn(async () => [])
    const costs = useProductionOrderCosts(t, {
      loadCosts: vi.fn(async () => []),
      loadOperations: vi.fn(async () => []),
      postCost: vi.fn(),
      reverseCost: vi.fn(),
      correctCost: vi.fn(),
      loadCostCloses,
      closeCost,
      reopenCost,
      formatBusinessDate: () => '2026-11-08'
    })
    await costs.openCosts(completedOrder)
    expect(costs.costUnsettledVariance.value).toBe(5)

    costs.openCostCloseAction('CLOSE')
    await costs.submitCostCloseAction()

    expect(closeCost).toHaveBeenCalledWith('order-1', {
      businessDate: '2026-11-08', remark: undefined
    })
    expect(costs.costCloseStatus.value).toBe('CLOSED')
    expect(costs.costUnsettledVariance.value).toBe(0)

    costs.openCostCloseAction('REOPEN')
    costs.costCloseActionForm.businessDate = '2026-11-09'
    costs.costCloseActionForm.reason = 'late payroll correction'
    await costs.submitCostCloseAction()

    expect(reopenCost).toHaveBeenCalledWith('order-1', {
      businessDate: '2026-11-09', reason: 'late payroll correction'
    })
    expect(costs.costCloseStatus.value).toBe('OPEN')
    expect(costs.costUnsettledVariance.value).toBe(5)
    expect(loadCostCloses).toHaveBeenCalledTimes(3)
  })
})
