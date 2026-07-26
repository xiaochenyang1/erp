import { describe, expect, it, vi } from 'vitest'

import type { ProductionOrderOperation } from '@/api/production'
import { useProductionOrderOperations } from './useProductionOrderOperations'

const t = (key: string) => key

describe('production order operations', () => {
  it('opens operations dialog and loads operation list for the selected order', async () => {
    const operations: ProductionOrderOperation[] = [{
      id: 'op-1',
      operationCode: 'OP10',
      operationName: 'Assembly',
      plannedQty: 10,
      reportedQty: 2,
      status: 'IN_PROGRESS'
    } as ProductionOrderOperation]
    const loadOperations = vi.fn(async () => operations)
    const reportOperation = vi.fn()
    const ops = useProductionOrderOperations(t, {
      loadOperations,
      reportOperation
    })

    await ops.openOperations({ id: 'order-1', orderNo: 'MO001' })

    expect(ops.opsDialogVisible.value).toBe(true)
    expect(ops.opsOrderId.value).toBe('order-1')
    expect(ops.opsOrderNo.value).toBe('MO001')
    expect(loadOperations).toHaveBeenCalledWith('order-1')
    expect(ops.operations.value).toEqual(operations)
  })

  it('prepares remaining quantity when opening a report dialog', () => {
    const ops = useProductionOrderOperations(t, {
      loadOperations: vi.fn(async () => []),
      reportOperation: vi.fn()
    })

    ops.openReport({
      id: 'op-2',
      operationCode: 'OP20',
      operationName: 'Packing',
      plannedQty: 5,
      reportedQty: 2,
      status: 'IN_PROGRESS'
    } as ProductionOrderOperation)

    expect(ops.reportDialogVisible.value).toBe(true)
    expect(ops.reportForm.operationId).toBe('op-2')
    expect(ops.reportForm.operationName).toBe('OP20 Packing')
    expect(ops.reportForm.reportQty).toBe(3)
    expect(ops.reportForm.qualifiedQty).toBe(3)
  })

  it('rejects reports where qualified quantity exceeds reported quantity', async () => {
    const onWarning = vi.fn()
    const reportOperation = vi.fn()
    const ops = useProductionOrderOperations(t, {
      loadOperations: vi.fn(async () => []),
      reportOperation,
      onWarning
    })
    ops.opsOrderId.value = 'order-2'
    ops.reportForm.operationId = 'op-3'
    ops.reportForm.reportQty = 1
    ops.reportForm.qualifiedQty = 2

    await ops.submitReport()

    expect(onWarning).toHaveBeenCalledWith('productionOrder.validation.qualifiedExceedsReported')
    expect(reportOperation).not.toHaveBeenCalled()
  })

  it('submits a valid operation report and reloads operations', async () => {
    const loadOperations = vi.fn(async () => [])
    const reportOperation = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const ops = useProductionOrderOperations(t, {
      loadOperations,
      reportOperation,
      onSuccess
    })
    ops.opsOrderId.value = 'order-3'
    ops.reportForm.operationId = 'op-4'
    ops.reportForm.reportQty = 4
    ops.reportForm.qualifiedQty = 3
    ops.reportForm.scrapQty = 1
    ops.reportForm.remark = 'ok'
    ops.reportDialogVisible.value = true

    await ops.submitReport()

    expect(reportOperation).toHaveBeenCalledWith('order-3', 'op-4', {
      reportQty: 4,
      qualifiedQty: 3,
      scrapQty: 1,
      remark: 'ok'
    })
    expect(onSuccess).toHaveBeenCalledWith('productionOrder.message.reported')
    expect(ops.reportDialogVisible.value).toBe(false)
    expect(loadOperations).toHaveBeenCalledWith('order-3')
  })
})
