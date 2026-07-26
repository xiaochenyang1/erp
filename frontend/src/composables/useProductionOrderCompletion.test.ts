import { describe, expect, it, vi } from 'vitest'

import type { ProductionOrder } from '@/api/production'
import { useProductionOrderCompletion } from './useProductionOrderCompletion'

const t = (key: string) => key

describe('production order completion', () => {
  it('opens completion dialog with remaining quantity and product controls', async () => {
    const productControlFromOptions = vi.fn(() => ({
      lotControlled: true,
      serialControlled: false,
      productCode: 'FG-1',
      productName: 'Finished good'
    }))
    const resolveProductControls = vi.fn(async () => ({
      lotControlled: true,
      serialControlled: true,
      productCode: 'FG-1',
      productName: 'Finished good'
    }))
    const loadFinishedLocations = vi.fn()
    const completion = useProductionOrderCompletion(t, {
      completeOrder: vi.fn(),
      reverseCompletion: vi.fn(),
      productControlFromOptions,
      resolveProductControls,
      loadFinishedLocations,
      formatBusinessDate: () => '2026-07-26'
    })

    await completion.handleComplete({
      id: 'mo-1',
      productId: 'p-1',
      planQuantity: 10,
      completedQuantity: 4,
      finishedWarehouseId: 'w-2',
      warehouseId: 'w-1'
    } as ProductionOrder)

    expect(completion.completeDialogVisible.value).toBe(true)
    expect(completion.completeForm.maxQuantity).toBe(6)
    expect(completion.completeForm.completedQuantity).toBe(6)
    expect(completion.completeForm.completionDate).toBe('2026-07-26')
    expect(loadFinishedLocations).toHaveBeenCalledWith('w-2')
    expect(resolveProductControls).toHaveBeenCalledWith('p-1')
    expect(completion.completeProductControls.serialControlled).toBe(true)
  })

  it('blocks completion when required serial capture is missing', async () => {
    const onWarning = vi.fn()
    const completeOrder = vi.fn()
    const completion = useProductionOrderCompletion(t, {
      completeOrder,
      reverseCompletion: vi.fn(),
      productControlFromOptions: () => ({ serialControlled: true, productCode: 'FG-1' }),
      resolveProductControls: async () => ({ serialControlled: true, productCode: 'FG-1' }),
      loadFinishedLocations: vi.fn(),
      formatBusinessDate: () => '2026-07-26',
      onWarning
    })
    completion.completeProductId.value = 'p-1'
    completion.completeForm.orderId = 'mo-2'
    completion.completeForm.completedQuantity = 2
    completion.completeForm.serialNos = ''

    await completion.handleConfirmComplete()

    expect(onWarning).toHaveBeenCalled()
    expect(completeOrder).not.toHaveBeenCalled()
  })

  it('submits completion payload and closes dialog on success', async () => {
    const completeOrder = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const onCompleted = vi.fn()
    const completion = useProductionOrderCompletion(t, {
      completeOrder,
      reverseCompletion: vi.fn(),
      productControlFromOptions: () => ({}),
      resolveProductControls: async () => ({}),
      loadFinishedLocations: vi.fn(),
      formatBusinessDate: () => '2026-07-26',
      onSuccess,
      onCompleted
    })
    completion.completeDialogVisible.value = true
    completion.completeProductId.value = 'p-9'
    completion.completeForm.orderId = 'mo-3'
    completion.completeForm.completedQuantity = 3
    completion.completeForm.completionDate = '2026-07-26'
    completion.completeForm.lotNo = 'L1'
    completion.completeForm.serialNos = 'S1,S2,S3'

    await completion.handleConfirmComplete()

    expect(completeOrder).toHaveBeenCalledWith('mo-3', expect.objectContaining({
      completedQuantity: 3,
      completionDate: '2026-07-26',
      lotNo: 'L1',
      serialNos: 'S1,S2,S3'
    }))
    expect(onSuccess).toHaveBeenCalledWith('productionOrder.message.completed')
    expect(completion.completeDialogVisible.value).toBe(false)
    expect(onCompleted).toHaveBeenCalled()
  })

  it('opens reverse dialog with completed quantity and posts reversal', async () => {
    const reverseCompletion = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const completion = useProductionOrderCompletion(t, {
      completeOrder: vi.fn(),
      reverseCompletion,
      productControlFromOptions: () => ({}),
      resolveProductControls: async () => ({}),
      loadFinishedLocations: vi.fn(),
      formatBusinessDate: () => '2026-07-26',
      onSuccess
    })

    completion.handleReverseCompletion({
      id: 'mo-4',
      completedQuantity: 5,
      actualEndDate: '2026-07-20',
      status: 'COMPLETED'
    } as ProductionOrder)
    expect(completion.reverseDialogVisible.value).toBe(true)
    expect(completion.reverseForm.reversedQty).toBe(5)

    await completion.handleConfirmReverseCompletion()
    expect(reverseCompletion).toHaveBeenCalledWith('mo-4', expect.objectContaining({
      reversedQty: 5,
      reversalDate: '2026-07-20'
    }))
    expect(onSuccess).toHaveBeenCalledWith('productionOrder.message.reversed')
  })
})
